/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerInterface;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.Config;

import static com.android.internal.telephony.RILConstants.*;

import com.android.internal.os.HandlerThread;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.gsm.NetworkInfo;
import com.android.internal.telephony.gsm.PDPContextState;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.IccCardApplication;
import com.android.internal.telephony.IccCardStatus;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsResponse;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/**
 * {@hide}
 */
class RILRequest {
    static final String LOG_TAG = "RILJ";

    //***** Class Variables
    static int sNextSerial = 0;
    static Object sSerialMonitor = new Object();
    private static Object sPoolSync = new Object();
    private static RILRequest sPool = null;
    private static int sPoolSize = 0;
    private static final int MAX_POOL_SIZE = 4;

    //***** Instance Variables
    int mSerial;
    int mRequest;
    Message mResult;
    Parcel mp;
    RILRequest mNext;

    /**
     * Retrieves a new RILRequest instance from the pool.
     *
     * @param request RIL_REQUEST_*
     * @param result sent when operation completes
     * @return a RILRequest instance from the pool.
     */
    static RILRequest obtain(int request, Message result) {
        RILRequest rr = null;

        synchronized(sPoolSync) {
            if (sPool != null) {
                rr = sPool;
                sPool = rr.mNext;
                rr.mNext = null;
                sPoolSize--;
            }
        }

        if (rr == null) {
            rr = new RILRequest();
        }

        synchronized(sSerialMonitor) {
            rr.mSerial = sNextSerial++;
        }
        rr.mRequest = request;
        rr.mResult = result;
        rr.mp = Parcel.obtain();

        if (result != null && result.getTarget() == null) {
            throw new NullPointerException("Message target must not be null");
        }

        // first elements in any RIL Parcel
        rr.mp.writeInt(request);
        rr.mp.writeInt(rr.mSerial);

        return rr;
    }

    /**
     * Returns a RILRequest instance to the pool.
     *
     * Note: This should only be called once per use.
     */
    void release() {
        synchronized (sPoolSync) {
            if (sPoolSize < MAX_POOL_SIZE) {
                this.mNext = sPool;
                sPool = this;
                sPoolSize++;
            }
        }
    }

    private RILRequest() {
    }

    static void
    resetSerial() {
        synchronized(sSerialMonitor) {
            sNextSerial = 0;
        }
    }

    String
    serialString() {
        //Cheesy way to do %04d
        StringBuilder sb = new StringBuilder(8);
        String sn;

        sn = Integer.toString(mSerial);

        //sb.append("J[");
        sb.append('[');
        for (int i = 0, s = sn.length() ; i < 4 - s; i++) {
            sb.append('0');
        }

        sb.append(sn);
        sb.append(']');
        return sb.toString();
    }

    void
    onError(int error) {
        CommandException ex;

        ex = CommandException.fromRilErrno(error);

        if (RIL.RILJ_LOG) Log.d(LOG_TAG, serialString() + "< "
            + RIL.requestToString(mRequest)
            + " error: " + ex);

        if (mResult != null) {
            AsyncResult.forMessage(mResult, null, ex);
            mResult.sendToTarget();
        }

        if (mp != null) {
            mp.recycle();
            mp = null;
        }
    }
}


/**
 * RIL implementation of the CommandsInterface.
 * FIXME public only for testing
 *
 * {@hide}
 */
public final class RIL extends BaseCommands implements CommandsInterface {
    static final String LOG_TAG = "RILJ";
    private static final boolean DBG = true;
    static final boolean RILJ_LOG = true;
    static int WAKE_LOCK_TIMEOUT = 5000;

    //***** Instance Variables

    LocalSocket mSocket;
    HandlerThread mSenderThread;
    Handler mSenderH;
    RILSender mSender;
    Thread mReceiverThread;
    RILReceiver mReceiver;
    private Context mContext;
    WakeLock mWakeLock;
    int mRequestMessagesPending;

    // Is this the first radio state change?
    private boolean mInitialRadioStateChange = true;

    //I'd rather this be LinkedList or something
    ArrayList<RILRequest> mRequestsList = new ArrayList<RILRequest>();

    Object     mLastNITZTimeInfo;

    //***** Events

    static final int EVENT_SEND                 = 1;
    static final int EVENT_WAKE_LOCK_TIMEOUT    = 2;

    //***** Constants

    // match with constant in ril.cpp
    static final int RIL_MAX_COMMAND_BYTES = (8 * 1024);
    static final int RESPONSE_SOLICITED = 0;
    static final int RESPONSE_UNSOLICITED = 1;

    static final String SOCKET_NAME_RIL = "rild";

    static final int SOCKET_OPEN_RETRY_MILLIS = 4 * 1000;


    BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                sendScreenState(true);
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                sendScreenState(false);
            } else {
                Log.w(LOG_TAG, "RIL received unexpected Intent: " + intent.getAction());
            }
        }
    };

    class RILSender implements HandlerInterface,Runnable {
        // Only allocated once
        byte[] dataLength = new byte[4];

        //***** Runnable implementation
        public void
        run() {
            //setup if needed
        }


        //***** Handler implemementation

        public void
        handleMessage(Message msg) {
            RILRequest rr = (RILRequest)(msg.obj);
            RILRequest req = null;

            switch (msg.what) {
                case EVENT_SEND:
                    /**
                     * mRequestMessagePending++ already happened for every
                     * EVENT_SEND, thus we must make sure
                     * mRequestMessagePending-- happens once and only once
                     */
                    boolean alreadySubtracted = false;
                    try {
                        LocalSocket s;

                        s = mSocket;

                        if (s == null) {
                            rr.onError(RADIO_NOT_AVAILABLE);
                            rr.release();
                            mRequestMessagesPending--;
                            alreadySubtracted = true;
                            return;
                        }

                        synchronized (mRequestsList) {
                            mRequestsList.add(rr);
                        }

                        mRequestMessagesPending--;
                        alreadySubtracted = true;

                        byte[] data;

                        data = rr.mp.marshall();
                        rr.mp.recycle();
                        rr.mp = null;

                        if (data.length > RIL_MAX_COMMAND_BYTES) {
                            throw new RuntimeException(
                                    "Parcel larger than max bytes allowed! "
                                                          + data.length);
                        }

                        // parcel length in big endian
                        dataLength[0] = dataLength[1] = 0;
                        dataLength[2] = (byte)((data.length >> 8) & 0xff);
                        dataLength[3] = (byte)((data.length) & 0xff);

                        //Log.v(LOG_TAG, "writing packet: " + data.length + " bytes");

                        s.getOutputStream().write(dataLength);
                        s.getOutputStream().write(data);
                    } catch (IOException ex) {
                        Log.e(LOG_TAG, "IOException", ex);
                        req = findAndRemoveRequestFromList(rr.mSerial);
                        // make sure this request has not already been handled,
                        // eg, if RILReceiver cleared the list.
                        if (req != null || !alreadySubtracted) {
                            rr.onError(RADIO_NOT_AVAILABLE);
                            rr.release();
                        }
                    } catch (RuntimeException exc) {
                        Log.e(LOG_TAG, "Uncaught exception ", exc);
                        req = findAndRemoveRequestFromList(rr.mSerial);
                        // make sure this request has not already been handled,
                        // eg, if RILReceiver cleared the list.
                        if (req != null || !alreadySubtracted) {
                            rr.onError(GENERIC_FAILURE);
                            rr.release();
                        }
                    }

                    if (!alreadySubtracted) {
                        mRequestMessagesPending--;
                    }

                    break;

                case EVENT_WAKE_LOCK_TIMEOUT:
                    // Haven't heard back from the last request.  Assume we're
                    // not getting a response and  release the wake lock.
                    // TODO should we clean up mRequestList and mRequestPending
                    synchronized (mWakeLock) {
                        if (mWakeLock.isHeld()) {
                            if (DBG) {
                                synchronized (mRequestsList) {
                                    int count = mRequestsList.size();
                                    Log.d(LOG_TAG, "WAKE_LOCK_TIMEOUT " +
                                        " mReqPending=" + mRequestMessagesPending +
                                        " mRequestList=" + count);

                                    for (int i = 0; i < count; i++) {
                                        rr = mRequestsList.get(i);
                                        Log.d(LOG_TAG, i + ": [" + rr.mSerial + "] " +
                                            requestToString(rr.mRequest));

                                    }
                                }
                            }
                            mWakeLock.release();
                        }
                    }

                    break;
            }
        }
    }

    /**
     * Reads in a single RIL message off the wire. A RIL message consists
     * of a 4-byte little-endian length and a subsequent series of bytes.
     * The final message (length header omitted) is read into
     * <code>buffer</code> and the length of the final message (less header)
     * is returned. A return value of -1 indicates end-of-stream.
     *
     * @param is non-null; Stream to read from
     * @param buffer Buffer to fill in. Must be as large as maximum
     * message size, or an ArrayOutOfBounds exception will be thrown.
     * @return Length of message less header, or -1 on end of stream.
     * @throws IOException
     */
    private static int readRilMessage(InputStream is, byte[] buffer)
            throws IOException {
        int countRead;
        int offset;
        int remaining;
        int messageLength;

        // First, read in the length of the message
        offset = 0;
        remaining = 4;
        do {
            countRead = is.read(buffer, offset, remaining);

            if (countRead < 0 ) {
                Log.e(LOG_TAG, "Hit EOS reading message length");
                return -1;
            }

            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);

        messageLength = ((buffer[0] & 0xff) << 24)
                | ((buffer[1] & 0xff) << 16)
                | ((buffer[2] & 0xff) << 8)
                | (buffer[3] & 0xff);

        // Then, re-use the buffer and read in the message itself
        offset = 0;
        remaining = messageLength;
        do {
            countRead = is.read(buffer, offset, remaining);

            if (countRead < 0 ) {
                Log.e(LOG_TAG, "Hit EOS reading message.  messageLength=" + messageLength
                        + " remaining=" + remaining);
                return -1;
            }

            offset += countRead;
            remaining -= countRead;
        } while (remaining > 0);

        return messageLength;
    }

    class RILReceiver implements Runnable {
        byte[] buffer;

        RILReceiver() {
            buffer = new byte[RIL_MAX_COMMAND_BYTES];
        }

        public void
        run() {
            int retryCount = 0;

            try {for (;;) {
                LocalSocket s = null;
                LocalSocketAddress l;

                try {
                    s = new LocalSocket();
                    l = new LocalSocketAddress(SOCKET_NAME_RIL,
                            LocalSocketAddress.Namespace.RESERVED);
                    s.connect(l);
                } catch (IOException ex){
                    try {
                        if (s != null) {
                            s.close();
                        }
                    } catch (IOException ex2) {
                        //ignore failure to close after failure to connect
                    }

                    // don't print an error message after the the first time
                    // or after the 8th time

                    if (retryCount == 8) {
                        Log.e (LOG_TAG,
                            "Couldn't find '" + SOCKET_NAME_RIL
                            + "' socket after " + retryCount
                            + " times, continuing to retry silently");
                    } else if (retryCount > 0 && retryCount < 8) {
                        Log.i (LOG_TAG,
                            "Couldn't find '" + SOCKET_NAME_RIL
                            + "' socket; retrying after timeout");
                    }

                    try {
                        Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                    } catch (InterruptedException er) {
                    }

                    retryCount++;
                    continue;
                }

                retryCount = 0;

                mSocket = s;
                Log.i(LOG_TAG, "Connected to '" + SOCKET_NAME_RIL + "' socket");

                int length = 0;
                try {
                    InputStream is = mSocket.getInputStream();

                    for (;;) {
                        Parcel p;

                        length = readRilMessage(is, buffer);

                        if (length < 0) {
                            // End-of-stream reached
                            break;
                        }

                        p = Parcel.obtain();
                        p.unmarshall(buffer, 0, length);
                        p.setDataPosition(0);

                        //Log.v(LOG_TAG, "Read packet: " + length + " bytes");

                        processResponse(p);
                        p.recycle();
                    }
                } catch (java.io.IOException ex) {
                    Log.i(LOG_TAG, "'" + SOCKET_NAME_RIL + "' socket closed",
                          ex);
                } catch (Throwable tr) {
                    Log.e(LOG_TAG, "Uncaught exception read length=" + length, tr);
                }

                Log.i(LOG_TAG, "Disconnected from '" + SOCKET_NAME_RIL
                      + "' socket");

                setRadioState (RadioState.RADIO_UNAVAILABLE);

                try {
                    mSocket.close();
                } catch (IOException ex) {
                }

                mSocket = null;
                RILRequest.resetSerial();

                // Clear request list on close
                synchronized (mRequestsList) {
                    for (int i = 0, sz = mRequestsList.size() ; i < sz ; i++) {
                        RILRequest rr = mRequestsList.get(i);
                        rr.onError(RADIO_NOT_AVAILABLE);
                        rr.release();
                    }

                    mRequestsList.clear();
                }
            }} catch (Throwable tr) {
                Log.e(LOG_TAG,"Uncaught exception", tr);
            }
        }
    }



    //***** Constructors
    public
    RIL(Context context) {
        this(context, RILConstants.PREFERRED_NETWORK_MODE,
                RILConstants.PREFERRED_CDMA_SUBSCRIPTION);
    }

    public RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context);
        mCdmaSubscription  = cdmaSubscription;
        mNetworkMode = networkMode;
        //At startup mPhoneType is first set from networkMode
        switch(networkMode) {
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
            case RILConstants.NETWORK_MODE_GSM_ONLY:
            case RILConstants.NETWORK_MODE_WCDMA_ONLY:
            case RILConstants.NETWORK_MODE_GSM_UMTS:
                mPhoneType = RILConstants.GSM_PHONE;
                break;
            case RILConstants.NETWORK_MODE_CDMA:
            case RILConstants.NETWORK_MODE_CDMA_NO_EVDO:
            case RILConstants.NETWORK_MODE_EVDO_NO_CDMA:
                mPhoneType = RILConstants.CDMA_PHONE;
                break;
            case RILConstants.NETWORK_MODE_GLOBAL:
                mPhoneType = RILConstants.CDMA_PHONE;
                break;
            default:
                mPhoneType = RILConstants.CDMA_PHONE;
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG);
        mWakeLock.setReferenceCounted(false);
        mRequestMessagesPending = 0;

        mContext = context;
        mSender = new RILSender();
        mSenderThread = new HandlerThread(mSender, mSender, "RILSender");
        mSenderH = mSenderThread.getHandler();
        mReceiver = new RILReceiver();
        mReceiverThread = new Thread(mReceiver, "RILReceiver");
        mReceiverThread.start();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(mIntentReceiver, filter);
    }

    //***** CommandsInterface implementation

    @Override public void
    setOnNITZTime(Handler h, int what, Object obj) {
        super.setOnNITZTime(h, what, obj);

        // Send the last NITZ time if we have it
        if (mLastNITZTimeInfo != null) {
            mNITZTimeRegistrant
                .notifyRegistrant(
                    new AsyncResult (null, mLastNITZTimeInfo, null));
            mLastNITZTimeInfo = null;
        }
    }

    public void
    getIccStatus(Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_SIM_STATUS, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    supplyIccPin(String pin, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_SIM_PIN, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(1);
        rr.mp.writeString(pin);

        send(rr);
    }

    public void
    supplyIccPuk(String puk, String newPin, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_SIM_PUK, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(2);
        rr.mp.writeString(puk);
        rr.mp.writeString(newPin);

        send(rr);
    }

    public void
    supplyIccPin2(String pin, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_SIM_PIN2, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(1);
        rr.mp.writeString(pin);

        send(rr);
    }

    public void
    supplyIccPuk2(String puk, String newPin2, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_SIM_PUK2, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(2);
        rr.mp.writeString(puk);
        rr.mp.writeString(newPin2);

        send(rr);
    }

    public void
    changeIccPin(String oldPin, String newPin, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CHANGE_SIM_PIN, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(2);
        rr.mp.writeString(oldPin);
        rr.mp.writeString(newPin);

        send(rr);
    }

    public void
    changeIccPin2(String oldPin2, String newPin2, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CHANGE_SIM_PIN2, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(2);
        rr.mp.writeString(oldPin2);
        rr.mp.writeString(newPin2);

        send(rr);
    }

    public void
    changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CHANGE_BARRING_PASSWORD, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(3);
        rr.mp.writeString(facility);
        rr.mp.writeString(oldPwd);
        rr.mp.writeString(newPwd);

        send(rr);
    }

    public void
    supplyNetworkDepersonalization(String netpin, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(1);
        rr.mp.writeString(netpin);

        send(rr);
    }

    public void
    getCurrentCalls (Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_CURRENT_CALLS, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getPDPContextList(Message result) {
        getDataCallList(result);
    }

    public void
    getDataCallList(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DATA_CALL_LIST, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    dial (String address, int clirMode, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);

        rr.mp.writeString(address);
        rr.mp.writeInt(clirMode);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getIMSI(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_IMSI, result);

        if (RILJ_LOG) riljLog(rr.serialString() +
                              "> getIMSI:RIL_REQUEST_GET_IMSI " +
                              RIL_REQUEST_GET_IMSI +
                              " " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getIMEI(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_IMEI, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getIMEISV(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_IMEISV, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }


    public void
    hangupConnection (int gsmIndex, Message result) {
        if (RILJ_LOG) riljLog("hangupConnection: gsmIndex=" + gsmIndex);

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_HANGUP, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " +
                gsmIndex);

        rr.mp.writeInt(1);
        rr.mp.writeInt(gsmIndex);

        send(rr);
    }

    public void
    hangupWaitingOrBackground (Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND,
                                        result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    hangupForegroundResumeBackground (Message result) {
        RILRequest rr
                = RILRequest.obtain(
                        RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND,
                                        result);
        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    switchWaitingOrHoldingAndActive (Message result) {
        RILRequest rr
                = RILRequest.obtain(
                        RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE,
                                        result);
        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    conference (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_CONFERENCE, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }


    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE,
                result);

        rr.mp.writeInt(1);
        rr.mp.writeInt(enable ? 1:0);

        send(rr);
    }

    public void getPreferredVoicePrivacy(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE,
                result);
        send(rr);
    }

    public void
    separateConnection (int gsmIndex, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SEPARATE_CONNECTION, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                            + " " + gsmIndex);

        rr.mp.writeInt(1);
        rr.mp.writeInt(gsmIndex);

        send(rr);
    }

    public void
    acceptCall (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_ANSWER, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    rejectCall (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_UDUB, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    explicitCallTransfer (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_EXPLICIT_CALL_TRANSFER, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getLastCallFailCause (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_LAST_CALL_FAIL_CAUSE, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * @deprecated
     */
    public void
    getLastPdpFailCause (Message result) {
        getLastDataCallFailCause (result);
    }

    /**
     * The preferred new alternative to getLastPdpFailCause
     */
    public void
    getLastDataCallFailCause (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    setMute (boolean enableMute, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_MUTE, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                            + " " + enableMute);

        rr.mp.writeInt(1);
        rr.mp.writeInt(enableMute ? 1 : 0);

        send(rr);
    }

    public void
    getMute (Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_GET_MUTE, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getSignalStrength (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SIGNAL_STRENGTH, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getRegistrationState (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_REGISTRATION_STATE, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getGPRSRegistrationState (Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_GPRS_REGISTRATION_STATE, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getOperator(Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_OPERATOR, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    sendDtmf(char c, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_DTMF, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeString(Character.toString(c));

        send(rr);
    }

    public void
    startDtmf(char c, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_DTMF_START, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeString(Character.toString(c));

        send(rr);
    }

    public void
    stopDtmf(Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_DTMF_STOP, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }


    public void
    sendSMS (String smscPDU, String pdu, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SEND_SMS, result);

        rr.mp.writeInt(2);
        rr.mp.writeString(smscPDU);
        rr.mp.writeString(pdu);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    sendCdmaSms(byte[] pdu, Message result) {
        int address_nbr_of_digits;
        int subaddr_nbr_of_digits;
        int bearerDataLength;
        ByteArrayInputStream bais = new ByteArrayInputStream(pdu);
        DataInputStream dis = new DataInputStream(bais);

        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_CDMA_SEND_SMS, result);

        try {
            rr.mp.writeInt(dis.readInt()); //teleServiceId
            rr.mp.writeInt(dis.readInt()); //servicePresent
            rr.mp.writeInt(dis.readInt()); //serviceCategory
            rr.mp.writeInt(dis.read()); //address_digit_mode
            rr.mp.writeInt(dis.read()); //address_nbr_mode
            rr.mp.writeInt(dis.read()); //address_ton
            rr.mp.writeInt(dis.read()); //address_nbr_plan
            address_nbr_of_digits = (byte) dis.read();
            rr.mp.writeInt(address_nbr_of_digits);
            for(int i=0; i < address_nbr_of_digits; i++){
                rr.mp.writeInt(dis.readByte()); // address_orig_bytes[i]
            }
            rr.mp.writeInt(dis.read()); //subaddressType
            rr.mp.writeInt(dis.read()); //subaddr_odd
            subaddr_nbr_of_digits = (byte) dis.read();
            rr.mp.writeInt(subaddr_nbr_of_digits);
            for(int i=0; i < subaddr_nbr_of_digits; i++){
                rr.mp.writeInt(dis.readByte()); //subaddr_orig_bytes[i]
            }

            bearerDataLength = dis.read();
            rr.mp.writeInt(bearerDataLength);
            for(int i=0; i < bearerDataLength; i++){
                rr.mp.writeInt(dis.readByte()); //bearerData[i]
            }
        }catch (IOException ex){
            if (RILJ_LOG) riljLog("sendSmsCdma: conversion from input stream to object failed: "
                    + ex);
        }

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void deleteSmsOnSim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DELETE_SMS_ON_SIM,
                response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(index);

        if (Config.LOGD) {
            if (RILJ_LOG) riljLog(rr.serialString() + "> "
                    + requestToString(rr.mRequest)
                    + " " + index);
        }

        send(rr);
    }

    public void deleteSmsOnRuim(int index, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM,
                response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(index);

        if (Config.LOGD) {
            if (RILJ_LOG) riljLog(rr.serialString() + "> "
                    + requestToString(rr.mRequest)
                    + " " + index);
        }

        send(rr);
    }

    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        status = translateStatus(status);

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_WRITE_SMS_TO_SIM,
                response);

        rr.mp.writeInt(status);
        rr.mp.writeString(pdu);
        rr.mp.writeString(smsc);

        if (Config.LOGD) {
            if (RILJ_LOG) riljLog(rr.serialString() + "> "
                    + requestToString(rr.mRequest)
                    + " " + status);
        }

        send(rr);
    }

    public void writeSmsToRuim(int status, String pdu, Message response) {
        status = translateStatus(status);

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM,
                response);

        rr.mp.writeInt(status);
        rr.mp.writeString(pdu);

        if (Config.LOGD) {
            if (RILJ_LOG) riljLog(rr.serialString() + "> "
                    + requestToString(rr.mRequest)
                    + " " + status);
        }

        send(rr);
    }

    /**
     *  Translates EF_SMS status bits to a status value compatible with
     *  SMS AT commands.  See TS 27.005 3.1.
     */
    private int translateStatus(int status) {
        switch(status & 0x7) {
            case SmsManager.STATUS_ON_ICC_READ:
                return 1;
            case SmsManager.STATUS_ON_ICC_UNREAD:
                return 0;
            case SmsManager.STATUS_ON_ICC_SENT:
                return 3;
            case SmsManager.STATUS_ON_ICC_UNSENT:
                return 2;
        }

        // Default to READ.
        return 1;
    }

    /**
     * @deprecated
     */
    public void
    setupDefaultPDP(String apn, String user, String password, Message result) {
        String radioTechnology = "1"; //0 for CDMA, 1 for GSM/UMTS
        String profile = ""; //profile number, NULL for GSM/UMTS
        setupDataCall(radioTechnology, profile, apn, user,
                password, result);

    }

    /**
     * @deprecated
     */
    public void
    deactivateDefaultPDP(int cid, Message result) {
        deactivateDataCall(cid, result);
    }

    /**
     * The preferred new alternative to setupDefaultPDP that is
     * CDMA-compatible.
     *
     */
    public void
    setupDataCall(String radioTechnology, String profile, String apn,
            String user, String password, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SETUP_DATA_CALL, result);

        rr.mp.writeInt(5);

        rr.mp.writeString(radioTechnology);
        rr.mp.writeString(profile);
        rr.mp.writeString(apn);
        rr.mp.writeString(user);
        rr.mp.writeString(password);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " "
                + apn);

        send(rr);
    }

    public void
    deactivateDataCall(int cid, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_DEACTIVATE_DATA_CALL, result);

        rr.mp.writeInt(1);
        rr.mp.writeString(Integer.toString(cid));

        if (RILJ_LOG) riljLog(rr.serialString() + "> " +
                requestToString(rr.mRequest) + " " + cid);

        send(rr);
    }

    public void
    setRadioPower(boolean on, Message result) {
        //if radio is OFF set preferred NW type and cmda subscription
        if(mInitialRadioStateChange) {
            synchronized (mStateMonitor) {
                if (!mState.isOn()) {
                    RILRequest rrPnt = RILRequest.obtain(
                                   RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, null);

                    rrPnt.mp.writeInt(1);
                    rrPnt.mp.writeInt(mNetworkMode);
                    if (RILJ_LOG) riljLog(rrPnt.serialString() + "> "
                        + requestToString(rrPnt.mRequest) + " : " + mNetworkMode);

                    send(rrPnt);

                    RILRequest rrCs = RILRequest.obtain(
                                   RIL_REQUEST_CDMA_SET_SUBSCRIPTION, null);
                    rrCs.mp.writeInt(1);
                    rrCs.mp.writeInt(mCdmaSubscription);
                    if (RILJ_LOG) riljLog(rrCs.serialString() + "> "
                    + requestToString(rrCs.mRequest) + " : " + mCdmaSubscription);
                    send(rrCs);
                }
            }
        }
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);

        rr.mp.writeInt(1);
        rr.mp.writeInt(on ? 1 : 0);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    setSuppServiceNotifications(boolean enable, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION, result);

        rr.mp.writeInt(1);
        rr.mp.writeInt(enable ? 1 : 0);

        if (Config.LOGD) {
            if (RILJ_LOG) riljLog(rr.serialString() + "> "
                    + requestToString(rr.mRequest));
        }

        send(rr);
    }

    public void
    acknowledgeLastIncomingSMS(boolean success, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SMS_ACKNOWLEDGE, result);

        rr.mp.writeInt(1);
        rr.mp.writeInt(success ? 1 : 0);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    acknowledgeLastIncomingCdmaSms(boolean success, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE, result);

        rr.mp.writeInt(success ? 0 : 1); //RIL_CDMA_SMS_ErrorClass
        // cause code according to X.S004-550E
        rr.mp.writeInt(39); //39 means other terminal problem; is not interpreted for success.

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }


    public void
    iccIO (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SIM_IO, result);

        rr.mp.writeInt(command);
        rr.mp.writeInt(fileid);
        rr.mp.writeString(path);
        rr.mp.writeInt(p1);
        rr.mp.writeInt(p2);
        rr.mp.writeInt(p3);
        rr.mp.writeString(data);
        rr.mp.writeString(pin2);

        if (RILJ_LOG) riljLog(rr.serialString() + "> iccIO: " + requestToString(rr.mRequest)
                + " 0x" + Integer.toHexString(command)
                + " 0x" + Integer.toHexString(fileid) + " "
                + p1 + "," + p2 + "," + p3);

        send(rr);
    }

    public void
    getCLIR(Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_GET_CLIR, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    setCLIR(int clirMode, Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_CLIR, result);

        // count ints
        rr.mp.writeInt(1);

        rr.mp.writeInt(clirMode);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + clirMode);

        send(rr);
    }

    public void
    queryCallWaiting(int serviceClass, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_CALL_WAITING, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(serviceClass);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + serviceClass);

        send(rr);
    }

    public void
    setCallWaiting(boolean enable, int serviceClass, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_CALL_WAITING, response);

        rr.mp.writeInt(2);
        rr.mp.writeInt(enable ? 1 : 0);
        rr.mp.writeInt(serviceClass);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " " + enable + ", " + serviceClass);

        send(rr);
    }

    public void
    setNetworkSelectionModeAutomatic(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC,
                                    response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL,
                                    response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + operatorNumeric);

        rr.mp.writeString(operatorNumeric);

        send(rr);
    }

    public void
    getNetworkSelectionMode(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE,
                                    response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getAvailableNetworks(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_AVAILABLE_NETWORKS,
                                    response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    setCallForward(int action, int cfReason, int serviceClass,
                String number, int timeSeconds, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_CALL_FORWARD, response);

        rr.mp.writeInt(action);
        rr.mp.writeInt(cfReason);
        rr.mp.writeInt(serviceClass);
        rr.mp.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mp.writeString(number);
        rr.mp.writeInt (timeSeconds);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + action + " " + cfReason + " " + serviceClass
                    + timeSeconds);

        send(rr);
    }

    public void
    queryCallForwardStatus(int cfReason, int serviceClass,
                String number, Message response) {
        RILRequest rr
            = RILRequest.obtain(RIL_REQUEST_QUERY_CALL_FORWARD_STATUS, response);

        rr.mp.writeInt(2); // 2 is for query action, not in used anyway
        rr.mp.writeInt(cfReason);
        rr.mp.writeInt(serviceClass);
        rr.mp.writeInt(PhoneNumberUtils.toaFromString(number));
        rr.mp.writeString(number);
        rr.mp.writeInt (0);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " " + cfReason + " " + serviceClass);

        send(rr);
    }

    public void
    queryCLIP(Message response) {
        RILRequest rr
            = RILRequest.obtain(RIL_REQUEST_QUERY_CLIP, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }


    public void
    getBasebandVersion (Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_BASEBAND_VERSION, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    queryFacilityLock (String facility, String password, int serviceClass,
                            Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_QUERY_FACILITY_LOCK, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        // count strings
        rr.mp.writeInt(3);

        rr.mp.writeString(facility);
        rr.mp.writeString(password);

        rr.mp.writeString(Integer.toString(serviceClass));

        send(rr);
    }

    public void
    setFacilityLock (String facility, boolean lockState, String password,
                        int serviceClass, Message response) {
        String lockString;
         RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_FACILITY_LOCK, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        // count strings
        rr.mp.writeInt(4);

        rr.mp.writeString(facility);
        lockString = (lockState)?"1":"0";
        rr.mp.writeString(lockString);
        rr.mp.writeString(password);
        rr.mp.writeString(Integer.toString(serviceClass));

        send(rr);

    }

    public void
    sendUSSD (String ussdString, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SEND_USSD, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                            + " " + ussdString);

        rr.mp.writeString(ussdString);

        send(rr);
    }

    // inherited javadoc suffices
    public void cancelPendingUssd (Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_CANCEL_USSD, response);

        if (RILJ_LOG) riljLog(rr.serialString()
                + "> " + requestToString(rr.mRequest));

        send(rr);
    }


    public void resetRadio(Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_RESET_RADIO, result);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_OEM_HOOK_RAW, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
               + "[" + IccUtils.bytesToHexString(data) + "]");

        rr.mp.writeByteArray(data);

        send(rr);

    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_OEM_HOOK_STRINGS, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeStringArray(strings);

        send(rr);
    }

     /**
     * Assign a specified band for RF configuration.
     *
     * @param bandMode one of BM_*_BAND
     * @param response is callback message
     */
    public void setBandMode (int bandMode, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_BAND_MODE, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(bandMode);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                 + " " + bandMode);

        send(rr);
     }

    /**
     * Query the list of band mode supported by RF.
     *
     * @param response is callback message
     *        ((AsyncResult)response.obj).result  is an int[] with every
     *        element representing one avialable BM_*_BAND
     */
    public void queryAvailableBandMode (Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE,
                response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void sendTerminalResponse(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeString(contents);
        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void sendEnvelope(String contents, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeString(contents);
        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void handleCallSetupRequestFromSim(
            boolean accept, Message response) {

        RILRequest rr = RILRequest.obtain(
            RILConstants.RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM,
            response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        int[] param = new int[1];
        param[0] = accept ? 1 : 0;
        rr.mp.writeIntArray(param);
        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void setPreferredNetworkType(int networkType , Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(networkType);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + networkType);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void getPreferredNetworkType(Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void getNeighboringCids(Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_GET_NEIGHBORING_CELL_IDS, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void setLocationUpdates(boolean enable, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_LOCATION_UPDATES, response);
        rr.mp.writeInt(1);
        rr.mp.writeInt(enable ? 1 : 0);

        if (RILJ_LOG) riljLog(rr.serialString() + "> "
                + requestToString(rr.mRequest) + ": " + enable);

        send(rr);
    }

    //***** Private Methods

    private void sendScreenState(boolean on) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SCREEN_STATE, null);
        rr.mp.writeInt(1);
        rr.mp.writeInt(on ? 1 : 0);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + ": " + on);

        send(rr);
    }

    protected void
    onRadioAvailable() {
        // In case screen state was lost (due to process crash),
        // this ensures that the RIL knows the correct screen state.

        // TODO: Should query Power Manager and send the actual
        // screen state.  Just send true for now.
        sendScreenState(true);
   }

    private void setRadioStateFromRILInt(int state) {
        RadioState newState;

        /* RIL_RadioState ril.h */
        switch(state) {
            case 0: newState = RadioState.RADIO_OFF; break;
            case 1: newState = RadioState.RADIO_UNAVAILABLE; break;
            case 2: newState = RadioState.SIM_NOT_READY; break;
            case 3: newState = RadioState.SIM_LOCKED_OR_ABSENT; break;
            case 4: newState = RadioState.SIM_READY; break;
            case 5: newState = RadioState.RUIM_NOT_READY; break;
            case 6: newState = RadioState.RUIM_READY; break;
            case 7: newState = RadioState.RUIM_LOCKED_OR_ABSENT; break;
            case 8: newState = RadioState.NV_NOT_READY; break;
            case 9: newState = RadioState.NV_READY; break;

            default:
                throw new RuntimeException(
                            "Unrecognized RIL_RadioState: " +state);
        }

        if (mInitialRadioStateChange) {
            if (newState.isOn()) {
                /* If this is our first notification, make sure the radio
                 * is powered off.  This gets the radio into a known state,
                 * since it's possible for the phone proc to have restarted
                 * (eg, if it or the runtime crashed) without the RIL
                 * and/or radio knowing.
                 */
                if (DBG) Log.d(LOG_TAG, "Radio ON @ init; reset to OFF");
                    setRadioPower(false, null);
            } else {
                if (DBG) Log.d(LOG_TAG, "Radio OFF @ init");
                setRadioState(newState);
            }
            mInitialRadioStateChange = false;
        } else {
            setRadioState(newState);
        }
    }

    /**
     * Holds a PARTIAL_WAKE_LOCK whenever
     * a) There is outstanding RIL request sent to RIL deamon and no replied
     * b) There is a request waiting to be sent out.
     *
     * There is a WAKE_LOCK_TIMEOUT to release the lock, though it shouldn't
     * happen often.
     */

    private void
    acquireWakeLock() {
        synchronized (mWakeLock) {
            mWakeLock.acquire();
            mRequestMessagesPending++;

            mSenderH.removeMessages(EVENT_WAKE_LOCK_TIMEOUT);
            Message msg = mSenderH.obtainMessage(EVENT_WAKE_LOCK_TIMEOUT);
            mSenderH.sendMessageDelayed(msg, WAKE_LOCK_TIMEOUT);
        }
    }

    private void
    releaseWakeLockIfDone() {
        synchronized (mWakeLock) {
            if (mWakeLock.isHeld() &&
                (mRequestMessagesPending == 0) &&
                (mRequestsList.size() == 0)) {
                mSenderH.removeMessages(EVENT_WAKE_LOCK_TIMEOUT);
                mWakeLock.release();
            }
        }
    }

    private void
    send(RILRequest rr) {
        Message msg;

        msg = mSenderH.obtainMessage(EVENT_SEND, rr);

        acquireWakeLock();

        msg.sendToTarget();
    }

    private void
    processResponse (Parcel p) {
        int type;

        type = p.readInt();

        if (type == RESPONSE_UNSOLICITED) {
            processUnsolicited (p);
        } else if (type == RESPONSE_SOLICITED) {
            processSolicited (p);
        }

        releaseWakeLockIfDone();
    }

    private RILRequest findAndRemoveRequestFromList(int serial) {
        synchronized (mRequestsList) {
            for (int i = 0, s = mRequestsList.size() ; i < s ; i++) {
                RILRequest rr = mRequestsList.get(i);

                if (rr.mSerial == serial) {
                    mRequestsList.remove(i);
                    return rr;
                }
            }
        }

        return null;
    }

    private void
    processSolicited (Parcel p) {
        int serial, error;
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Log.w(LOG_TAG, "Unexpected solicited response! sn: "
                            + serial + " error: " + error);
            return;
        }

        if (error != 0) {
            rr.onError(error);
            rr.release();
            return;
        }

        Object ret;

        try {switch (rr.mRequest) {
/*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
*/
            case RIL_REQUEST_GET_SIM_STATUS: ret =  responseIccCardStatus(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN: ret =  responseVoid(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK: ret =  responseVoid(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN2: ret =  responseVoid(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK2: ret =  responseVoid(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN: ret =  responseVoid(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN2: ret =  responseVoid(p); break;
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_CURRENT_CALLS: ret =  responseCallList(p); break;
            case RIL_REQUEST_DIAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMSI: ret =  responseString(p); break;
            case RIL_REQUEST_HANGUP: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseInts(p); break;
            case RIL_REQUEST_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_GPRS_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
            case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
            case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseStrings(p); break;
            case RIL_REQUEST_SIM_IO: ret =  responseICC_IO(p); break;
            case RIL_REQUEST_SEND_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_CANCEL_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_CLIR: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CLIR: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: ret =  responseCallForward(p); break;
            case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_WAITING: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CALL_WAITING: ret =  responseVoid(p); break;
            case RIL_REQUEST_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMEI: ret =  responseString(p); break;
            case RIL_REQUEST_GET_IMEISV: ret =  responseString(p); break;
            case RIL_REQUEST_ANSWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseVoid(p); break;
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseNetworkInfos(p); break;
            case RIL_REQUEST_DTMF_START: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF_STOP: ret =  responseVoid(p); break;
            case RIL_REQUEST_BASEBAND_VERSION: ret =  responseString(p); break;
            case RIL_REQUEST_SEPARATE_CONNECTION: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_MUTE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_MUTE: ret =  responseInts(p); break;
            case RIL_REQUEST_QUERY_CLIP: ret =  responseInts(p); break;
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_DATA_CALL_LIST: ret =  responseDataCallList(p); break;
            case RIL_REQUEST_RESET_RADIO: ret =  responseVoid(p); break;
            case RIL_REQUEST_OEM_HOOK_RAW: ret =  responseRaw(p); break;
            case RIL_REQUEST_OEM_HOOK_STRINGS: ret =  responseStrings(p); break;
            case RIL_REQUEST_SCREEN_STATE: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_WRITE_SMS_TO_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_DELETE_SMS_ON_SIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_BAND_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_STK_GET_PROFILE: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SET_PROFILE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseInts(p); break;
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret =  responseStrings(p); break;
            case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_TTY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_TTY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_FLASH: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BURST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SEND_SMS: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_BROADCAST_CONFIG: ret =  responseBR_SMS_CNF(p); break;
            case RIL_REQUEST_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: ret =  responseCDMA_BR_CNF(p); break;
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_VALIDATE_AKEY: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SUBSCRIPTION: ret =  responseStrings(p); break;
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
            default:
                throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
            //break;
        }} catch (Throwable tr) {
            // Exceptions here usually mean invalid RIL responses

            Log.w(LOG_TAG, rr.serialString() + "< "
                    + requestToString(rr.mRequest)
                    + " exception, possible invalid RIL response", tr);

            if (rr.mResult != null) {
                AsyncResult.forMessage(rr.mResult, null, tr);
                rr.mResult.sendToTarget();
            }
            rr.release();
            return;
        }

        if (RILJ_LOG) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
            + " " + retToString(rr.mRequest, ret));

        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        rr.release();
    }

    private String
    retToString(int req, Object ret) {
        if (ret == null) return "";
        switch (req) {
            // Don't log these return values, for privacy's sake.
            case RIL_REQUEST_GET_IMSI:
            case RIL_REQUEST_GET_IMEI:
            case RIL_REQUEST_GET_IMEISV:
                return "";
        }

        StringBuilder sb;
        String s;
        int length;
        if (ret instanceof int[]){
            int[] intArray = (int[]) ret;
            length = intArray.length;
            sb = new StringBuilder("{");
            if (length > 0) {
                int i = 0;
                sb.append(intArray[i++]);
                while ( i < length) {
                    sb.append(", ").append(intArray[i++]);
                }
            }
            sb.append("}");
            s = sb.toString();
        } else if (ret instanceof String[]) {
            String[] strings = (String[]) ret;
            length = strings.length;
            sb = new StringBuilder("{");
            if (length > 0) {
                int i = 0;
                sb.append(strings[i++]);
                while ( i < length) {
                    sb.append(", ").append(strings[i++]);
                }
            }
            sb.append("}");
            s = sb.toString();
        }else if (req == RIL_REQUEST_GET_CURRENT_CALLS) {
            ArrayList<DriverCall> calls = (ArrayList<DriverCall>) ret;
            sb = new StringBuilder(" ");
            for (DriverCall dc : calls) {
                sb.append("[").append(dc).append("] ");
            }
            s = sb.toString();
        } else {
            s = ret.toString();
        }
        return s;
    }

    private void
    processUnsolicited (Parcel p) {
        int response;
        Object ret;

        response = p.readInt();

        try {switch(response) {
/*
 cat libs/telephony/ril_unsol_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: \2(rr, p); break;/'
*/

            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_NEW_SMS: ret =  responseString(p); break;
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: ret =  responseString(p); break;
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: ret =  responseInts(p); break;
            case RIL_UNSOL_ON_USSD: ret =  responseStrings(p); break;
            case RIL_UNSOL_NITZ_TIME_RECEIVED: ret =  responseString(p); break;
            case RIL_UNSOL_SIGNAL_STRENGTH: ret = responseInts(p); break;
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED: ret = responseDataCallList(p);break;
            case RIL_UNSOL_SUPP_SVC_NOTIFICATION: ret = responseSuppServiceNotification(p); break;
            case RIL_UNSOL_STK_SESSION_END: ret = responseVoid(p); break;
            case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;
            case RIL_UNSOL_STK_EVENT_NOTIFY: ret = responseString(p); break;
            case RIL_UNSOL_STK_CALL_SETUP: ret = responseInts(p); break;
            case RIL_UNSOL_SIM_SMS_STORAGE_FULL: ret =  responseVoid(p); break;
            case RIL_UNSOL_SIM_REFRESH: ret =  responseInts(p); break;
            case RIL_UNSOL_CALL_RING: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:  ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:  ret =  responseCdmaSms(p); break;
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =  responseString(p); break;
            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:  ret =  responseVoid(p); break;
            default:
                throw new RuntimeException("Unrecognized unsol response: " + response);
            //break; (implied)
        }} catch (Throwable tr) {
            Log.e(LOG_TAG, "Exception processing unsol response: "
                                    + response, tr);
            return;
        }

        switch(response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                /* has bonus radio state int */
                setRadioStateFromRILInt(p.readInt());

                if (RILJ_LOG) riljLog("[UNSL]< RADIO_STATE_CHANGED " +mState);
            break;
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED:
                if (RILJ_LOG) riljLog("[UNSL]< CALL_STATE_CHANGED");

                mCallStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
            case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED:
                if (RILJ_LOG) riljLog("[UNSL]< NETWORK_STATE_CHANGED");

                mNetworkStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
            case RIL_UNSOL_RESPONSE_NEW_SMS: {
                if (RILJ_LOG) riljLog("[UNSL]< NEW_SMS");

                // FIXME this should move up a layer
                String a[] = new String[2];

                a[1] = (String)ret;

                SmsMessage sms;

                sms = SmsMessage.newFromCMT(a);
                if (mSMSRegistrant != null) {
                    mSMSRegistrant
                        .notifyRegistrant(new AsyncResult(null, sms, null));
                }
            break;
            }
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< NEW_SMS_STATUS_REPORT " + ret);
                }

                if (mSmsStatusRegistrant != null) {
                    mSmsStatusRegistrant.notifyRegistrant(
                            new AsyncResult(null, ret, null));
                }
            break;
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM:
                if (RILJ_LOG) riljLog("[UNSL]< NEW_SMS_ON_SIM" + retToString(response, ret));

                int[] smsIndex = (int[])ret;

                if(smsIndex.length == 1) {
                    if (mSmsOnSimRegistrant != null) {
                        mSmsOnSimRegistrant.
                                notifyRegistrant(new AsyncResult(null, smsIndex, null));
                    }
                } else {
                    if (RILJ_LOG) riljLog(" NEW_SMS_ON_SIM ERROR with wrong length "
                            + smsIndex.length);
                }
            break;
            case RIL_UNSOL_ON_USSD:
                String[] resp = (String[])ret;

                if (resp.length < 2) {
                    resp = new String[2];
                    resp[0] = ((String[])ret)[0];
                    resp[1] = null;
                }
                if (RILJ_LOG) riljLog("[UNSL]< ON_USSD " + resp[0]);
                if (mUSSDRegistrant != null) {
                    mUSSDRegistrant.notifyRegistrant(
                        new AsyncResult (null, resp, null));
                }
            break;
            case RIL_UNSOL_NITZ_TIME_RECEIVED:
                if (RILJ_LOG) riljLog("[UNSL]< NITZ_TIME_RECEIVED " + retToString(response, ret));

                // has bonus int containing time_t that the NITZ
                // time was received
                int nitzReceiveTime = p.readInt();

                Object[] result = new Object[2];

                result[0] = ret;
                result[1] = Integer.valueOf(nitzReceiveTime);

                if (mNITZTimeRegistrant != null) {

                    mNITZTimeRegistrant
                        .notifyRegistrant(new AsyncResult (null, result, null));
                } else {
                    // in case NITZ time registrant isnt registered yet
                    mLastNITZTimeInfo = result;
                }
            break;

            case RIL_UNSOL_SIGNAL_STRENGTH:
                // Note this is set to "verbose" because it happens
                // frequently
                if (Config.LOGV) Log.v(LOG_TAG, "[UNSL]< SIGNAL_STRENGTH "
                        + retToString(response, ret));

                if (mSignalStrengthRegistrant != null) {
                    mSignalStrengthRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
            break;
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
                if (RILJ_LOG) riljLog("[UNSL]< DATA_CALL_LIST_CHANGED " 
                        + retToString(response, ret));

                mDataConnectionRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
            break;

            case RIL_UNSOL_SUPP_SVC_NOTIFICATION:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< SUPP_SVC_NOTIFICATION "
                            + retToString(response, ret));
                }

                if (mSsnRegistrant != null) {
                    mSsnRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_SESSION_END:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< STK_SESSION_END");
                }

                if (mStkSessionEndRegistrant != null) {
                    mStkSessionEndRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_PROACTIVE_COMMAND:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< STK_PROACTIVE_COMMAND "
                            + retToString(response, ret));
                }

                if (mStkProCmdRegistrant != null) {
                    mStkProCmdRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_EVENT_NOTIFY:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< STK_EVENT_NOTIFY "
                            + retToString(response, ret));
                }

                if (mStkEventRegistrant != null) {
                    mStkEventRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_CALL_SETUP:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< STK_CALL_SETUP "
                            + retToString(response, ret));
                }

                if (mStkCallSetUpRegistrant != null) {
                    mStkCallSetUpRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< SIM_SMS_STORAGE_FULL");
                }

                if (mIccSmsFullRegistrant != null) {
                    mIccSmsFullRegistrant.notifyRegistrant();
                }
                break;

            case RIL_UNSOL_SIM_REFRESH:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< SIM_REFRESH " + retToString(response, ret));
                }

                if (mIccRefreshRegistrant != null) {
                    mIccRefreshRegistrant.notifyRegistrant(
                            new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_CALL_RING:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< CALL_RING ");
                }

                if (mRingRegistrant != null) {
                    mRingRegistrant.notifyRegistrant();
                }
                break;

            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:
                if (mIccStatusChangedRegistrants != null) {
                    mIccStatusChangedRegistrants.notifyRegistrants();
                }
                break;

            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
                SmsMessage sms = (SmsMessage) ret;

                if (mSMSRegistrant != null) {
                    mSMSRegistrant
                        .notifyRegistrant(new AsyncResult(null, sms, null));
                }
                break;

            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
                // TODO T: waiting for SMS BC feature
                break;

            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:
                if (Config.LOGD) {
                    if (RILJ_LOG) riljLog("[UNSL]< RUIM_SMS_STORAGE_FULL");
                }

                if (mIccSmsFullRegistrant != null) {
                    mIccSmsFullRegistrant.notifyRegistrant();
                }
                break;
        }
    }

    private Object
    responseInts(Parcel p) {
        int numInts;
        int response[];

        numInts = p.readInt();

        response = new int[numInts];

        for (int i = 0 ; i < numInts ; i++) {
            response[i] = p.readInt();
        }

        return response;
    }


    private Object
    responseVoid(Parcel p) {
        return null;
    }

    private Object
    responseCallForward(Parcel p) {
        int numInfos;
        CallForwardInfo infos[];

        numInfos = p.readInt();

        infos = new CallForwardInfo[numInfos];

        for (int i = 0 ; i < numInfos ; i++) {
            infos[i] = new CallForwardInfo();

            infos[i].status = p.readInt();
            infos[i].reason = p.readInt();
            infos[i].serviceClass = p.readInt();
            infos[i].toa = p.readInt();
            infos[i].number = p.readString();
            infos[i].timeSeconds = p.readInt();
        }

        return infos;
    }

    private Object
    responseSuppServiceNotification(Parcel p) {
        SuppServiceNotification notification = new SuppServiceNotification();

        notification.notificationType = p.readInt();
        notification.code = p.readInt();
        notification.index = p.readInt();
        notification.type = p.readInt();
        notification.number = p.readString();

        return notification;
    }

    private Object
    responseCdmaSms(Parcel p) {
        SmsMessage sms;
        sms = SmsMessage.newFromParcel(p);

        return sms;
    }

    private Object
    responseString(Parcel p) {
        String response;

        response = p.readString();

        return response;
    }

    private Object
    responseStrings(Parcel p) {
        int num;
        String response[];

        response = p.readStringArray();

        if (false) {
            num = p.readInt();

            response = new String[num];
            for (int i = 0; i < num; i++) {
                response[i] = p.readString();
            }
        }

        return response;
    }

    private Object
    responseRaw(Parcel p) {
        int num;
        byte response[];

        response = p.createByteArray();

        return response;
    }

    private Object
    responseSMS(Parcel p) {
        int messageRef;
        String ackPDU;

        messageRef = p.readInt();
        ackPDU = p.readString();

        SmsResponse response = new SmsResponse(messageRef, ackPDU);

        return response;
    }


    private Object
     responseICC_IO(Parcel p) {
        int sw1, sw2;
        byte data[] = null;
        Message ret;

        sw1 = p.readInt();
        sw2 = p.readInt();

        String s = p.readString();

        return new IccIoResult(sw1, sw2, s);
    }

    private Object
    responseIccCardStatus(Parcel p) {
        RadioState currentRadioState;
        IccCardApplication ca;

        currentRadioState = getRadioState();

        IccCardStatus status = new IccCardStatus();
        status.card_state                      = status.CardStateFromRILInt(p.readInt());
        status.universal_pin_state             = status.PinStateFromRILInt(p.readInt());
        status.gsm_umts_subscription_app_index = p.readInt();
        status.cdma_subscription_app_index     = p.readInt();
        status.num_applications                = p.readInt();

        // limit to maximum allowed applications
        if (status.num_applications > IccCardStatus.CARD_MAX_APPS) {
            status.num_applications = IccCardStatus.CARD_MAX_APPS;
        }

        for (int i = 0 ; i < status.num_applications ; i++) {
            ca = new IccCardApplication();
            ca.app_type       = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state      = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            ca.aid            = p.readString();
            ca.app_label      = p.readString();
            ca.pin1_replaced  = p.readInt();
            ca.pin1           = p.readInt();
            ca.pin2           = p.readInt();
            status.application.add(ca);
        }

        // this is common for all radio technologies
        if (!status.card_state.isCardPresent()) {
            return IccStatus.ICC_ABSENT;
        }

        // check radio technology
        if( currentRadioState == RadioState.RADIO_OFF         ||
            currentRadioState == RadioState.RADIO_UNAVAILABLE ||
            currentRadioState == RadioState.SIM_NOT_READY     ||
            currentRadioState == RadioState.RUIM_NOT_READY    ||
            currentRadioState == RadioState.NV_NOT_READY      ||
            currentRadioState == RadioState.NV_READY            ) {
            return IccStatus.ICC_NOT_READY;
        }

        if( currentRadioState == RadioState.SIM_LOCKED_OR_ABSENT  ||
            currentRadioState == RadioState.SIM_READY             ||
            currentRadioState == RadioState.RUIM_LOCKED_OR_ABSENT ||
            currentRadioState == RadioState.RUIM_READY) {

            int index;

            // check for CDMA radio technology
            if (currentRadioState == RadioState.RUIM_LOCKED_OR_ABSENT ||
                currentRadioState == RadioState.RUIM_READY) {
                index = status.cdma_subscription_app_index;
            }
            else {
                index = status.gsm_umts_subscription_app_index;
            }

            // check if PIN required
            if (status.application.get(index).app_state.isPinRequired()) {
                return IccStatus.ICC_PIN;
            }
            if (status.application.get(index).app_state.isPukRequired()) {
                return IccStatus.ICC_PUK;
            }
            if (status.application.get(index).app_state.isSubscriptionPersoEnabled()) {
                return IccStatus.ICC_NETWORK_PERSONALIZATION;
            }
            if (status.application.get(index).app_state.isAppReady()) {
                return IccStatus.ICC_READY;
            }
            if (status.application.get(index).app_state.isAppNotReady()) {
                return IccStatus.ICC_NOT_READY;
            }
            return IccStatus.ICC_NOT_READY;
        }

        throw new RuntimeException ("Invalid RIL_REQUEST_GET_SIM_STATUS result");

    }

    private Object
    responseCallList(Parcel p) {
        int num;
        byte voiceSettings;
        ArrayList<DriverCall> response;
        DriverCall dc;

        num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        for (int i = 0 ; i < num ; i++) {
            dc = new DriverCall();

            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            dc.isMpty = (0 != p.readInt());
            dc.isMT = (0 != p.readInt());
            dc.als = p.readInt();
            voiceSettings = p.readByte();
            dc.isVoice = (0 == voiceSettings) ? false : true;
            dc.number = p.readString();

            // Make sure there's a leading + on addresses with a TOA
            // of 145

            dc.number = PhoneNumberUtils.stringFromStringAndTOA(
                                    dc.number, dc.TOA);

            response.add(dc);

            if ( RILConstants.CDMA_VOICE_PRIVACY == voiceSettings ) {
                mVoicePrivacyOnRegistrants.notifyRegistrants();
                Log.d(LOG_TAG, "InCall VoicePrivacy is enabled: " +
                        Integer.toString(voiceSettings));
            } else {
                mVoicePrivacyOffRegistrants.notifyRegistrants();
                Log.d(LOG_TAG, "InCall VoicePrivacy is disabled: " +
                        Integer.toString(voiceSettings));
            }
        }

        Collections.sort(response);

        return response;
    }

    private Object
    responseDataCallList(Parcel p) {
        int num;
        ArrayList<PDPContextState> response;

        num = p.readInt();
        response = new ArrayList<PDPContextState>(num);

        for (int i = 0; i < num; i++) {
            PDPContextState pdp = new PDPContextState();

            pdp.cid = p.readInt();
            pdp.active = p.readInt() == 0 ? false : true;
            pdp.type = p.readString();
            pdp.apn = p.readString();
            pdp.address = p.readString();

            response.add(pdp);
        }

        return response;
    }

    private Object
    responseNetworkInfos(Parcel p) {
        String strings[] = (String [])responseStrings(p);
        ArrayList<NetworkInfo> ret;

        if (strings.length % 4 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
                + strings.length + " strings, expected multible of 4");
        }

        ret = new ArrayList<NetworkInfo>(strings.length / 4);

        for (int i = 0 ; i < strings.length ; i += 4) {
            ret.add (
                new NetworkInfo(
                    strings[i+0],
                    strings[i+1],
                    strings[i+2],
                    strings[i+3]));
        }

        return ret;
    }

    private Object
    responseBR_SMS_CNF(Parcel p) {
        // TODO
        return null;
    }

    private Object
    responseCDMA_BR_CNF(Parcel p) {
        int numInts;
        int response[];

        numInts = p.readInt();

        response = new int[numInts];

        response[0] = numInts;
        for (int i = 1 ; i < numInts; i++) {
            response[i] = p.readInt();
        }

        return response;
    }

    static String
    requestToString(int request) {
/*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
*/
        switch(request) {
            case RIL_REQUEST_GET_SIM_STATUS: return "GET_SIM_STATUS";
            case RIL_REQUEST_ENTER_SIM_PIN: return "ENTER_SIM_PIN";
            case RIL_REQUEST_ENTER_SIM_PUK: return "ENTER_SIM_PUK";
            case RIL_REQUEST_ENTER_SIM_PIN2: return "ENTER_SIM_PIN2";
            case RIL_REQUEST_ENTER_SIM_PUK2: return "ENTER_SIM_PUK2";
            case RIL_REQUEST_CHANGE_SIM_PIN: return "CHANGE_SIM_PIN";
            case RIL_REQUEST_CHANGE_SIM_PIN2: return "CHANGE_SIM_PIN2";
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: return "ENTER_NETWORK_DEPERSONALIZATION";
            case RIL_REQUEST_GET_CURRENT_CALLS: return "GET_CURRENT_CALLS";
            case RIL_REQUEST_DIAL: return "DIAL";
            case RIL_REQUEST_GET_IMSI: return "GET_IMSI";
            case RIL_REQUEST_HANGUP: return "HANGUP";
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: return "HANGUP_WAITING_OR_BACKGROUND";
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: return "RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case RIL_REQUEST_CONFERENCE: return "CONFERENCE";
            case RIL_REQUEST_UDUB: return "UDUB";
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: return "LAST_CALL_FAIL_CAUSE";
            case RIL_REQUEST_SIGNAL_STRENGTH: return "SIGNAL_STRENGTH";
            case RIL_REQUEST_REGISTRATION_STATE: return "REGISTRATION_STATE";
            case RIL_REQUEST_GPRS_REGISTRATION_STATE: return "GPRS_REGISTRATION_STATE";
            case RIL_REQUEST_OPERATOR: return "OPERATOR";
            case RIL_REQUEST_RADIO_POWER: return "RADIO_POWER";
            case RIL_REQUEST_DTMF: return "DTMF";
            case RIL_REQUEST_SEND_SMS: return "SEND_SMS";
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: return "SEND_SMS_EXPECT_MORE";
            case RIL_REQUEST_SETUP_DATA_CALL: return "SETUP_DATA_CALL";
            case RIL_REQUEST_SIM_IO: return "SIM_IO";
            case RIL_REQUEST_SEND_USSD: return "SEND_USSD";
            case RIL_REQUEST_CANCEL_USSD: return "CANCEL_USSD";
            case RIL_REQUEST_GET_CLIR: return "GET_CLIR";
            case RIL_REQUEST_SET_CLIR: return "SET_CLIR";
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: return "QUERY_CALL_FORWARD_STATUS";
            case RIL_REQUEST_SET_CALL_FORWARD: return "SET_CALL_FORWARD";
            case RIL_REQUEST_QUERY_CALL_WAITING: return "QUERY_CALL_WAITING";
            case RIL_REQUEST_SET_CALL_WAITING: return "SET_CALL_WAITING";
            case RIL_REQUEST_SMS_ACKNOWLEDGE: return "SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GET_IMEI: return "GET_IMEI";
            case RIL_REQUEST_GET_IMEISV: return "GET_IMEISV";
            case RIL_REQUEST_ANSWER: return "ANSWER";
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: return "DEACTIVATE_DATA_CALL";
            case RIL_REQUEST_QUERY_FACILITY_LOCK: return "QUERY_FACILITY_LOCK";
            case RIL_REQUEST_SET_FACILITY_LOCK: return "SET_FACILITY_LOCK";
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: return "CHANGE_BARRING_PASSWORD";
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: return "QUERY_NETWORK_SELECTION_MODE";
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: return "SET_NETWORK_SELECTION_AUTOMATIC";
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: return "SET_NETWORK_SELECTION_MANUAL";
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : return "QUERY_AVAILABLE_NETWORKS ";
            case RIL_REQUEST_DTMF_START: return "DTMF_START";
            case RIL_REQUEST_DTMF_STOP: return "DTMF_STOP";
            case RIL_REQUEST_BASEBAND_VERSION: return "BASEBAND_VERSION";
            case RIL_REQUEST_SEPARATE_CONNECTION: return "SEPARATE_CONNECTION";
            case RIL_REQUEST_SET_MUTE: return "SET_MUTE";
            case RIL_REQUEST_GET_MUTE: return "GET_MUTE";
            case RIL_REQUEST_QUERY_CLIP: return "QUERY_CLIP";
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: return "LAST_DATA_CALL_FAIL_CAUSE";
            case RIL_REQUEST_DATA_CALL_LIST: return "DATA_CALL_LIST";
            case RIL_REQUEST_RESET_RADIO: return "RESET_RADIO";
            case RIL_REQUEST_OEM_HOOK_RAW: return "OEM_HOOK_RAW";
            case RIL_REQUEST_OEM_HOOK_STRINGS: return "OEM_HOOK_STRINGS";
            case RIL_REQUEST_SCREEN_STATE: return "SCREEN_STATE";
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: return "SET_SUPP_SVC_NOTIFICATION";
            case RIL_REQUEST_WRITE_SMS_TO_SIM: return "WRITE_SMS_TO_SIM";
            case RIL_REQUEST_DELETE_SMS_ON_SIM: return "DELETE_SMS_ON_SIM";
            case RIL_REQUEST_SET_BAND_MODE: return "SET_BAND_MODE";
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: return "QUERY_AVAILABLE_BAND_MODE";
            case RIL_REQUEST_STK_GET_PROFILE: return "RIL_REQUEST_STK_GET_PROFILE";
            case RIL_REQUEST_STK_SET_PROFILE: return "RIL_REQUEST_STK_SET_PROFILE";
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: return "RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: return "RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: return "RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: return "RIL_REQUEST_EXPLICIT_CALL_TRANSFER";
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: return "RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: return "RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: return "RIL_REQUEST_GET_NEIGHBORING_CELL_IDS";
            case RIL_REQUEST_SET_LOCATION_UPDATES: return "RIL_REQUEST_SET_LOCATION_UPDATES";
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION: return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION";
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RIL_REQUEST_SET_TTY_MODE: return "RIL_REQUEST_SET_TTY_MODE";
            case RIL_REQUEST_QUERY_TTY_MODE: return "RIL_REQUEST_QUERY_TTY_MODE";
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_FLASH: return "RIL_REQUEST_CDMA_FLASH";
            case RIL_REQUEST_CDMA_BURST_DTMF: return "RIL_REQUEST_CDMA_BURST_DTMF";
            case RIL_REQUEST_CDMA_SEND_SMS: return "RIL_REQUEST_CDMA_SEND_SMS";
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GET_BROADCAST_CONFIG: return "RIL_REQUEST_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_SET_BROADCAST_CONFIG: return "RIL_REQUEST_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_BROADCAST_ACTIVATION: return "RIL_REQUEST_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_VALIDATE_AKEY: return "RIL_REQUEST_CDMA_VALIDATE_AKEY";
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_SUBSCRIPTION: return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case RIL_REQUEST_DEVICE_IDENTITY: return "RIL_REQUEST_DEVICE_IDENTITY";
            default: return "<unknown request>";
        }
    }

    private void riljLog(String msg) {
        Log.d(LOG_TAG, msg);
    }

    // ***** Methods for CDMA support
    public void
    getDeviceIdentity(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DEVICE_IDENTITY, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    getCDMASubscription(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_SUBSCRIPTION, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void setPhoneType(int phoneType) { //Set by CDMAPhone and GSMPhone constructor
        mPhoneType = phoneType;
    }

    /**
     * {@inheritDoc}
     */
    public void queryCdmaRoamingPreference(Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE, response);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(cdmaRoamingType);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + cdmaRoamingType);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void setCdmaSubscription(int cdmaSubscription , Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_CDMA_SET_SUBSCRIPTION, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(cdmaSubscription);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + cdmaSubscription);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void queryTTYModeEnabled(Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_QUERY_TTY_MODE, response);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void setTTYModeEnabled(boolean enable, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_SET_TTY_MODE, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(enable ? 1 : 0);

        send(rr);
    }

    /**
     * {@inheritDoc}
     */
    public void
    sendCDMAFeatureCode(String FeatureCode, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_FLASH, response);

        rr.mp.writeInt(1);
        rr.mp.writeString(FeatureCode);

        if (RILJ_LOG) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + FeatureCode);

        send(rr);
    }

    public void getCdmaBroadcastConfig(Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG, response);

        send(rr);
    }

    public void setCdmaBroadcastConfig(int[] configValuesArray, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG, response);

        for(int i = 0; i < configValuesArray.length; i++) {
            rr.mp.writeInt(configValuesArray[i]);
        }

        send(rr);
    }

    public void activateCdmaBroadcastSms(int activate, Message response) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_CDMA_BROADCAST_ACTIVATION, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(activate);

        send(rr);
    }
}
