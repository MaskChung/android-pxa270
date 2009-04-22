/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

import com.android.mms.util.RateController;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.PduComposer;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduParser;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.SendConf;
import com.google.android.mms.pdu.SendReq;
import com.google.android.mms.util.SqliteWrapper;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Sent;
import android.util.Config;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

/**
 * The SendTransaction is responsible for sending multimedia messages
 * (M-Send.req) to the MMSC server.  It:
 *
 * <ul>
 * <li>Loads the multimedia message from storage (Outbox).
 * <li>Packs M-Send.req and sends it.
 * <li>Retrieves confirmation data from the server  (M-Send.conf).
 * <li>Parses confirmation message and handles it.
 * <li>Moves sent multimedia message from Outbox to Sent.
 * <li>Notifies the TransactionService about successful completion.
 * </ul>
 */
public class SendTransaction extends Transaction implements Runnable {
    private static final String TAG = "SendTransaction";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private Thread mThread;
    private final Uri mSendReqURI;

    public SendTransaction(Context context,
            int transId, TransactionSettings connectionSettings, String uri) {
        super(context, transId, connectionSettings);
        mSendReqURI = Uri.parse(uri);
        mId = uri;

        // Attach the transaction to the instance of RetryScheduler.
        attach(RetryScheduler.getInstance(context));
    }

    /*
     * (non-Javadoc)
     * @see com.android.mms.Transaction#process()
     */
    @Override
    public void process() {
        mThread = new Thread(this);
        mThread.start();
    }

    public void run() {
        try {
            RateController rateCtlr = RateController.getInstance();
            if (rateCtlr.isLimitSurpassed() && !rateCtlr.isAllowedByUser()) {
                throw new MmsException("Sending rate limit surpassed.");
            }

            // Load M-Send.req from outbox
            PduPersister persister = PduPersister.getPduPersister(mContext);
            SendReq sendReq = (SendReq) persister.load(mSendReqURI);

            // Update the 'date' field of the PDU right before sending it.
            long date = System.currentTimeMillis() / 1000L;
            sendReq.setDate(date);

            // Persist the new date value into database.
            ContentValues values = new ContentValues(1);
            values.put(Mms.DATE, date);
            SqliteWrapper.update(mContext, mContext.getContentResolver(),
                                 mSendReqURI, values, null, null);

            // Pack M-Send.req, send it, retrieve confirmation data, and parse it
            long tokenKey = ContentUris.parseId(mSendReqURI);
            byte[] response = sendPdu(SendingProgressTokenManager.get(tokenKey),
                                      new PduComposer(mContext, sendReq).make());
            SendingProgressTokenManager.remove(tokenKey);

            SendConf conf = (SendConf) new PduParser(response).parse();
            if (conf == null) {
                throw new MmsException("None M-Send.conf received.");
            }

            // Check whether the responding Transaction-ID is consistent
            // with the sent one.
            if (!Arrays.equals(sendReq.getTransactionId(), conf.getTransactionId())) {
                throw new MmsException("Inconsistent Transaction-ID.");
            }

            // From now on, we won't save the whole M-Send.conf into
            // our database. Instead, we just save some interesting fields
            // into the related M-Send.req.
            values = new ContentValues(2);
            int respStatus = conf.getResponseStatus();
            values.put(Mms.RESPONSE_STATUS, respStatus);

            if (respStatus != PduHeaders.RESPONSE_STATUS_OK) {
                SqliteWrapper.update(mContext, mContext.getContentResolver(),
                                     mSendReqURI, values, null, null);
                throw new MmsException("Server returns an error code: " + respStatus);
            }

            String messageId = PduPersister.toIsoString(conf.getMessageId());
            values.put(Mms.MESSAGE_ID, messageId);
            SqliteWrapper.update(mContext, mContext.getContentResolver(),
                                 mSendReqURI, values, null, null);

            // Move M-Send.req from Outbox into Sent.
            Uri uri = persister.move(mSendReqURI, Sent.CONTENT_URI);

            mTransactionState.setState(TransactionState.SUCCESS);
            mTransactionState.setContentUri(uri);
        } catch (IOException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Unexpected IOException", e);
            }
        } catch (MmsException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Unexpected MmsException", e);
            }
        } catch (ClassCastException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Unexpected ClassCastException", e);
            }
        } catch (RuntimeException e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Unexpected RuntimeException", e);
            }
        } catch (Exception e) {
            if (LOCAL_LOGV) {
                Log.v(TAG, "Unexpected Exception.", e);
            }
        } finally {
            if (mTransactionState.getState() != TransactionState.SUCCESS) {
                mTransactionState.setState(TransactionState.FAILED);
                mTransactionState.setContentUri(mSendReqURI);
                Log.e(TAG, "Delivery failed.");
            }
            notifyObservers();
        }
    }

    @Override
    public int getType() {
        return SEND_TRANSACTION;
    }
}
