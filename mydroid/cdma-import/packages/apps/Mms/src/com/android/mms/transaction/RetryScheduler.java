/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.transaction;

import com.android.mms.util.DownloadManager;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.util.SqliteWrapper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.pim.DateFormat;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.util.Config;
import android.util.Log;

public class RetryScheduler implements Observer {
    private static final String TAG = "RetryScheduler";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private final Context mContext;
    private final ContentResolver mContentResolver;

    private RetryScheduler(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    private static RetryScheduler sInstance;
    public static RetryScheduler getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new RetryScheduler(context);
        }
        return sInstance;
    }

    private boolean isConnected() {
        ConnectivityManager mConnMgr = (ConnectivityManager)
                    mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = mConnMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return networkInfo.isConnected();
    }

    public void update(Observable observable) {
        try {
            Transaction t = (Transaction) observable;
            // We are only supposed to handle M-Notification.ind, M-Send.req
            // and M-ReadRec.ind.
            if ((t instanceof NotificationTransaction)
                    || (t instanceof RetrieveTransaction)
                    || (t instanceof ReadRecTransaction)
                    || (t instanceof SendTransaction)) {
                try {
                    TransactionState state = t.getState();
                    if (state.getState() == TransactionState.FAILED) {
                        Uri uri = state.getContentUri();
                        if (uri != null) {
                            scheduleRetry(uri);
                        }
                    }
                } finally {
                    t.detach(this);
                }
            }
        } finally {
            if (isConnected()) {
                setRetryAlarm(mContext);
            }
        }
    }

    private void scheduleRetry(Uri uri) {
        long msgId = ContentUris.parseId(uri);

        Uri.Builder uriBuilder = PendingMessages.CONTENT_URI.buildUpon();
        uriBuilder.appendQueryParameter("protocol", "mms");
        uriBuilder.appendQueryParameter("message", String.valueOf(msgId));

        Cursor cursor = SqliteWrapper.query(mContext, mContentResolver,
                uriBuilder.build(), null, null, null, null);

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    int msgType = cursor.getInt(cursor.getColumnIndexOrThrow(
                            PendingMessages.MSG_TYPE));

                    int direction;
                    switch (msgType) {
                        case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                            direction = AbstractRetryScheme.INCOMING;
                            break;
                        case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                        case PduHeaders.MESSAGE_TYPE_READ_REC_IND:
                            direction = AbstractRetryScheme.OUTGOING;
                            break;
                        default:
                            Log.w(TAG, "Bad message type found: " + msgType);
                            return;
                    }

                    int retryIndex = cursor.getInt(cursor.getColumnIndexOrThrow(
                            PendingMessages.RETRY_INDEX)) + 1; // Count this time.

                    // TODO Should exactly understand what was happened.
                    int errorType = MmsSms.ERR_TYPE_GENERIC;

                    DefaultRetryScheme scheme = new DefaultRetryScheme(
                            mContext, direction, retryIndex, errorType);

                    ContentValues values = new ContentValues(4);
                    long current = System.currentTimeMillis();
                    boolean isRetryDownloading =
                            (msgType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);
                    if (retryIndex < scheme.getRetryLimit()) {
                        long retryAt = current + scheme.getWaitingInterval();

                        if (LOCAL_LOGV) {
                            Log.v(TAG, "Retry for " + uri + " is scheduled to "
                                    + DateFormat.format("kk:mm:ss.", retryAt)
                                    + (retryAt % 1000));
                        }

                        values.put(PendingMessages.DUE_TIME, retryAt);

                        if (isRetryDownloading) {
                            // Downloading process is transiently failed.
                            DownloadManager.getInstance().markState(
                                    uri, DownloadManager.STATE_TRANSIENT_FAILURE);
                        }
                    } else {
                        errorType = MmsSms.ERR_TYPE_GENERIC_PERMANENT;
                        if (isRetryDownloading) {
                            Cursor c = SqliteWrapper.query(mContext, mContext.getContentResolver(), uri,
                                    new String[] { Mms.THREAD_ID }, null, null, null);
                            
                            long threadId = -1;
                            if (c != null) {
                                try {
                                    if (c.moveToFirst()) {
                                        threadId = c.getLong(0);
                                    }
                                } finally {
                                    c.close();
                                }
                            }

                            if (threadId != -1) {
                                // Downloading process is permanently failed.
                                MessagingNotification.notifyDownloadFailed(mContext, threadId);
                            }

                            DownloadManager.getInstance().markState(
                                    uri, DownloadManager.STATE_PERMANENT_FAILURE);
                        } else {
                            MessagingNotification.notifySendFailed(mContext, true);
                        }
                    }

                    values.put(PendingMessages.ERROR_TYPE,  errorType);
                    values.put(PendingMessages.RETRY_INDEX, retryIndex);
                    values.put(PendingMessages.LAST_TRY,    current);

                    int columnIndex = cursor.getColumnIndexOrThrow(
                            PendingMessages._ID);
                    long id = cursor.getLong(columnIndex);
                    SqliteWrapper.update(mContext, mContentResolver,
                            PendingMessages.CONTENT_URI,
                            values, PendingMessages._ID + "=" + id, null);
                } else if (LOCAL_LOGV) {
                    Log.v(TAG, "Cannot found correct pending status for: " + msgId);
                }
            } finally {
                cursor.close();
            }
        }
    }

    public static void setRetryAlarm(Context context) {
        Cursor cursor = PduPersister.getPduPersister(context).getPendingMessages(
                Long.MAX_VALUE);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    // The result of getPendingMessages() is order by due time.
                    long retryAt = cursor.getLong(cursor.getColumnIndexOrThrow(
                            PendingMessages.DUE_TIME));

                    Intent service = new Intent(TransactionService.ACTION_ONALARM,
                                        null, context, TransactionService.class);
                    PendingIntent operation = PendingIntent.getService(
                            context, 0, service, PendingIntent.FLAG_ONE_SHOT);
                    AlarmManager am = (AlarmManager) context.getSystemService(
                            Context.ALARM_SERVICE);
                    am.set(AlarmManager.RTC, retryAt, operation);

                    if (LOCAL_LOGV) {
                        Log.v(TAG, "Next retry is scheduled at: "
                                + DateFormat.format("kk:mm:ss.", retryAt)
                                + (retryAt % 1000));
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }
}
