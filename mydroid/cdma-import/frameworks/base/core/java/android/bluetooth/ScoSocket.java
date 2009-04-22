/*
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

package android.bluetooth;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import java.io.IOException;
import java.lang.Thread;


/**
 * The Android Bluetooth API is not finalized, and *will* change. Use at your
 * own risk.
 *
 * Simple SCO Socket.
 * Currently in Android, there is no support for sending data over a SCO
 * socket - this is managed by the hardware link to the Bluetooth Chip. This
 * class is instead intended for management of the SCO socket lifetime, 
 * and is tailored for use with the headset / handsfree profiles.
 * @hide
 */
public class ScoSocket {
    private static final String TAG = "ScoSocket";
    private static final boolean DBG = true;
    private static final boolean VDBG = false;  // even more logging

    public static final int STATE_READY = 1;    // Ready for use. No threads or sockets
    public static final int STATE_ACCEPT = 2;   // accept() thread running
    public static final int STATE_CONNECTING = 3;  // connect() thread running
    public static final int STATE_CONNECTED = 4;   // connected, waiting for close()
    public static final int STATE_CLOSED = 5;   // was connected, now closed.

    private int mState;
    private int mNativeData;
    private Handler mHandler;
    private int mAcceptedCode;
    private int mConnectedCode;
    private int mClosedCode;

    private WakeLock mWakeLock;  // held while STATE_CONNECTING or STATE_CONNECTED

    static {
        classInitNative();
    }
    private native static void classInitNative();

    public ScoSocket(PowerManager pm, Handler handler, int acceptedCode, int connectedCode,
                     int closedCode) {
        initNative();
        mState = STATE_READY;
        mHandler = handler;
        mAcceptedCode = acceptedCode;
        mConnectedCode = connectedCode;
        mClosedCode = closedCode;
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScoSocket");
        mWakeLock.setReferenceCounted(false);
        if (VDBG) log(this + " SCO OBJECT CTOR");
    }
    private native void initNative();

    protected void finalize() throws Throwable {
        try {
            if (VDBG) log(this + " SCO OBJECT DTOR");
            destroyNative();
            releaseWakeLock();
        } finally {
            super.finalize();
        }
    }
    private native void destroyNative();

    /** Connect this SCO socket to the given BT address.
     *  Does not block.
     */
    public synchronized boolean connect(String address) {
        if (VDBG) log("connect() " + this);
        if (mState != STATE_READY) {
            if (DBG) log("connect(): Bad state");
            return false;
        }
        acquireWakeLock();
        if (connectNative(address)) {
            mState = STATE_CONNECTING;
            return true;
        } else {
            mState = STATE_CLOSED;
            releaseWakeLock();
            return false;
        }
    }
    private native boolean connectNative(String address);

    /** Accept incoming SCO connections.
     *  Does not block.
     */
    public synchronized boolean accept() {
        if (VDBG) log("accept() " + this);
        if (mState != STATE_READY) {
            if (DBG) log("Bad state");
            return false;
        }
        if (acceptNative()) {
            mState = STATE_ACCEPT;
            return true;
        } else {
            mState = STATE_CLOSED;
            return false;
        }
    }
    private native boolean acceptNative();

    public synchronized void close() {
        if (DBG) log(this + " SCO OBJECT close() mState = " + mState);
        mState = STATE_CLOSED;
        closeNative();
        releaseWakeLock();
    }
    private native void closeNative();

    public synchronized int getState() {
        return mState;
    }

    private synchronized void onConnected(int result) {
        if (VDBG) log(this + " onConnected() mState = " + mState + " " + this);
        if (mState != STATE_CONNECTING) {
            if (DBG) log("Strange state, closing " + mState + " " + this);
            return;
        }
        if (result >= 0) {
            mState = STATE_CONNECTED;
        } else {
            mState = STATE_CLOSED;
        }
        mHandler.obtainMessage(mConnectedCode, mState, -1, this).sendToTarget();
        if (result < 0) {
            releaseWakeLock();
        }
    }

    private synchronized void onAccepted(int result) {
        if (VDBG) log("onAccepted() " + this);
        if (mState != STATE_ACCEPT) {
            if (DBG) log("Strange state" + this);
            return;
        }
        if (result >= 0) {
            acquireWakeLock();
            mState = STATE_CONNECTED;
        } else {
            mState = STATE_CLOSED;
        }
        mHandler.obtainMessage(mAcceptedCode, mState, -1, this).sendToTarget();
    }

    private synchronized void onClosed() {
        if (DBG) log("onClosed() " + this);
        if (mState != STATE_CLOSED) {
            mState = STATE_CLOSED;
            mHandler.obtainMessage(mClosedCode, mState, -1, this).sendToTarget();
            releaseWakeLock();
        }
    }

    private void acquireWakeLock() {
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
            if (VDBG) log("mWakeLock.acquire()" + this);
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock.isHeld()) {
            if (VDBG) log("mWakeLock.release()" + this);
            mWakeLock.release();
        }
    }

    private void log(String msg) {
        Log.d(TAG, msg);
    }
}
