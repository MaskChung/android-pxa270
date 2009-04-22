/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.telephony.SmsMessage;
import android.util.Log;

import com.google.android.mms.util.SqliteWrapper;

public class MessageStatusReceiver extends BroadcastReceiver {
    public static final String MESSAGE_STATUS_RECEIVED_ACTION =
            "com.android.mms.transaction.MessageStatusReceiver.MESSAGE_STATUS_RECEIVED";
    private static final String[] ID_PROJECTION = new String[] { Sms._ID };
    private static final String LOG_TAG = "MessageStatusReceiver";
    private static final Uri STATUS_URI =
            Uri.parse("content://sms/status");
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        if (MESSAGE_STATUS_RECEIVED_ACTION.equals(intent.getAction())) {

            Uri messageUri = intent.getData();
            byte[] pdu = (byte[]) intent.getExtra("pdu");

            updateMessageStatus(context, messageUri, pdu);
            MessagingNotification.updateNewMessageIndicator(context, true);
       }
    }

    private void updateMessageStatus(Context context, Uri messageUri, byte[] pdu) {
        // Create a "status/#" URL and use it to update the
        // message's status in the database.
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                            messageUri, ID_PROJECTION, null, null, null);
        if ((cursor != null) && cursor.moveToFirst()) {
            int messageId = cursor.getInt(0);

            cursor.close();

            Uri updateUri = ContentUris.withAppendedId(STATUS_URI, messageId);
            SmsMessage message = SmsMessage.createFromPdu(pdu);
            int status = message.getStatus();
            ContentValues contentValues = new ContentValues(1);

            contentValues.put(Sms.STATUS, status);
            SqliteWrapper.update(context, context.getContentResolver(),
                                updateUri, contentValues, null, null);
        } else {
            error("Can't find message for status update: " + messageUri);
        }
    }

    private void error(String message) {
        Log.e(LOG_TAG, "[MessageStatusReceiver] " + message);
    }
}
