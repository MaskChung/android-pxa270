/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony.gsm;

import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.AdnRecordCache;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.PhoneProxy;

import java.util.ArrayList;
import java.util.List;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */


public class SimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "GSM";


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch(msg.what) {
                default:
                    mBaseHandler.handleMessage(msg);
                    break;
            }
        }
    };

    public SimPhoneBookInterfaceManager(GSMPhone phone) {
        super(phone);
        adnCache = phone.mSIMRecords.getAdnCache();
        //NOTE service "simphonebook" added by IccSmsInterfaceManagerProxy
    }

    public void dispose() {
        super.dispose();
    }

    protected void finalize() {
        if(DBG) Log.d(LOG_TAG, "SimPhoneBookInterfaceManager finalized");
    }

    public int[] getAdnRecordsSize(int efid) {
        if (DBG) logd("getAdnRecordsSize: efid=" + efid);
        synchronized(mLock) {
            checkThread();
            recordSize = new int[3];

            //Using mBaseHandler, no difference in EVENT_GET_SIZE_DONE handling
            Message response = mBaseHandler.obtainMessage(EVENT_GET_SIZE_DONE);

            phone.getIccFileHandler().getEFLinearRecordSize(efid, response);
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to load from the SIM");
            }
        }

        return recordSize;
    }

    protected void logd(String msg) {
        Log.d(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }

    protected void loge(String msg) {
        Log.e(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }
}

