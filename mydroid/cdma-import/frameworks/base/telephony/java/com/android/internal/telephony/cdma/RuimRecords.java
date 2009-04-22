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

package com.android.internal.telephony.cdma;

import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.util.Log;

import static com.android.internal.telephony.TelephonyProperties.*;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.AdnRecordCache;
import com.android.internal.telephony.AdnRecordLoader;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cdma.RuimCard;
import com.android.internal.telephony.gsm.MccTable;

// can't be used since VoiceMailConstants is not public
//import com.android.internal.telephony.gsm.VoiceMailConstants;
import com.android.internal.telephony.IccException;
import com.android.internal.telephony.IccRecords;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.PhoneProxy;


/**
 * {@hide}
 */
public final class RuimRecords extends IccRecords {
    static final String LOG_TAG = "CDMA";

    private static final boolean DBG = true;

    //***** Instance Variables
    String imsi_m;
    String mdn = null;  // My mobile number
    String h_sid;
    String h_nid;

    // is not initialized

    //***** Event Constants

    private static final int EVENT_RUIM_READY = 1;
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 2;
    private static final int EVENT_GET_DEVICE_IDENTITY_DONE = 4;
    private static final int EVENT_GET_ICCID_DONE = 5;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_DONE = 10;
    private static final int EVENT_UPDATE_DONE = 14;
    private static final int EVENT_GET_SST_DONE = 17;
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;

    private static final int EVENT_SMS_ON_RUIM = 21;
    private static final int EVENT_GET_SMS_DONE = 22;

    private static final int EVENT_RUIM_REFRESH = 31;

    //***** Constructor

    RuimRecords(CDMAPhone p) {
        super(p);

        adnCache = new AdnRecordCache(phone);

        recordsRequested = false;  // No load request is made till SIM ready

        // recordsToLoad is set to 0 because no requests are made yet
        recordsToLoad = 0;


        p.mCM.registerForRUIMReady(this, EVENT_RUIM_READY, null);
        p.mCM.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        // NOTE the EVENT_SMS_ON_RUIM is not registered
        p.mCM.setOnIccRefresh(this, EVENT_RUIM_REFRESH, null);

        // Start off by setting empty state
        onRadioOffOrNotAvailable();

    }

    public void dispose() {
        //Unregister for all events
        phone.mCM.unregisterForRUIMReady(this);
        phone.mCM.unregisterForOffOrNotAvailable( this);
        phone.mCM.unSetOnIccRefresh(this);
    }

    protected void finalize() {
        if(DBG) Log.d(LOG_TAG, "RuimRecords finalized");
    }

    protected void onRadioOffOrNotAvailable() {
        countVoiceMessages = 0;
        mncLength = 0;
        iccid = null;

        adnCache.reset();

        phone.setSystemProperty(PROPERTY_ICC_OPERATOR_NUMERIC, null);
        phone.setSystemProperty(PROPERTY_ICC_OPERATOR_ISO_COUNTRY, null);

        // recordsRequested is set to false indicating that the SIM
        // read requests made so far are not valid. This is set to
        // true only when fresh set of read requests are made.
        recordsRequested = false;
    }

    //***** Public Methods

    /** Returns null if RUIM is not yet ready */
    public String getIMSI_M() {
        return imsi_m;
    }

    public String getMdnNumber() {
        return mdn;
    }

    public void setVoiceMailNumber(String alphaTag, String voiceNumber, Message onComplete){
        // In CDMA this is Operator/OEM dependent
        AsyncResult.forMessage((onComplete)).exception =
                new IccException("setVoiceMailNumber not implemented");
        onComplete.sendToTarget();
        Log.e(LOG_TAG, "method setVoiceMailNumber is not implemented");
    }

    /**
     * Called by CCAT Service when REFRESH is received.
     * @param fileChanged indicates whether any files changed
     * @param fileList if non-null, a list of EF files that changed
     */
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            // A future optimization would be to inspect fileList and
            // only reload those files that we care about.  For now,
            // just re-fetch all RUIM records that we cache.
            fetchRuimRecords();
        }
    }

    /** Returns the 5 or 6 digit MCC/MNC of the operator that
     *  provided the RUIM card. Returns null of RUIM is not yet ready
     */
    String getRUIMOperatorNumeric() {
        if (imsi_m == null) {
            return null;
        }

        if (mncLength != 0) {
            // Length = length of MCC + length of MNC
            // TODO: change spec name
            // length of mcc = 3 (3GPP2 C.S0005 - Section 2.3)
            return imsi_m.substring(0, 3 + mncLength);
        }

        // Guess the MNC length based on the MCC if we don't
        // have a valid value in ef[ad]

        int mcc;

        mcc = Integer.parseInt(imsi_m.substring(0,3));

        return imsi_m.substring(0, 3 + MccTable.smallestDigitsMccForMnc(mcc));
    }

    //***** Overridden from Handler
    public void handleMessage(Message msg) {
        AsyncResult ar;

        byte data[];

        boolean isRecordLoadResponse = false;

        try { switch (msg.what) {
            case EVENT_RUIM_READY:
                onRuimReady();
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOffOrNotAvailable();
            break;

            case EVENT_GET_DEVICE_IDENTITY_DONE:
                Log.d(LOG_TAG, "Event EVENT_GET_DEVICE_IDENTITY_DONE Received");
            break;

            /* IO events */

            case EVENT_GET_CDMA_SUBSCRIPTION_DONE:
                ar = (AsyncResult)msg.obj;
                String localTemp[] = (String[])ar.result;
                if (ar.exception != null) {
                    break;
                }

                mdn    = localTemp[0];
                h_sid  = localTemp[1];
                h_nid  = localTemp[2];

                Log.d(LOG_TAG, "MDN: " + mdn);

            break;

            case EVENT_GET_ICCID_DONE:
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;

                if (ar.exception != null) {
                    break;
                }

                iccid = IccUtils.bcdToString(data, 0, data.length);

                Log.d(LOG_TAG, "iccid: " + iccid);

            break;

            case EVENT_UPDATE_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception != null) {
                    Log.i(LOG_TAG, "RuimRecords update failed", ar.exception);
                }
            break;

            case EVENT_GET_ALL_SMS_DONE:
            case EVENT_MARK_SMS_READ_DONE:
            case EVENT_SMS_ON_RUIM:
            case EVENT_GET_SMS_DONE:
                Log.w(LOG_TAG, "Event not supported: " + msg.what);
                break;

            // TODO: probably EF_CST should be read instead
            case EVENT_GET_SST_DONE:
                Log.d(LOG_TAG, "Event EVENT_GET_SST_DONE Received");
            break;

            case EVENT_RUIM_REFRESH:
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    handleRuimRefresh((int[])(ar.result));
                }
                break;

        }}catch (RuntimeException exc) {
            // I don't want these exceptions to be fatal
            Log.w(LOG_TAG, "Exception parsing RUIM record", exc);
        } finally {
            // Count up record load responses even if they are fails
            if (isRecordLoadResponse) {
                onRecordLoaded();
            }
        }
    }

    protected void onRecordLoaded() {
        // One record loaded successfully or failed, In either case
        // we need to update the recordsToLoad count
        recordsToLoad -= 1;

        if (recordsToLoad == 0 && recordsRequested == true) {
            onAllRecordsLoaded();
        } else if (recordsToLoad < 0) {
            Log.e(LOG_TAG, "RuimRecords: recordsToLoad <0, programmer error suspected");
            recordsToLoad = 0;
        }
    }

    protected void onAllRecordsLoaded() {
        Log.d(LOG_TAG, "RuimRecords: record load complete");

        // Further records that can be inserted are Operator/OEM dependent

        recordsLoadedRegistrants.notifyRegistrants(
            new AsyncResult(null, null, null));
        ((CDMAPhone) phone).mRuimCard.broadcastRuimStateChangedIntent(
                RuimCard.INTENT_VALUE_ICC_LOADED, null);
    }


    //***** Private Methods

    private void onRuimReady() {
        /* broadcast intent ICC_READY here so that we can make sure
          READY is sent before IMSI ready
        */

        ((CDMAPhone) phone).mRuimCard.broadcastRuimStateChangedIntent(
                RuimCard.INTENT_VALUE_ICC_READY, null);

        fetchRuimRecords();

        phone.mCM.getCDMASubscription(obtainMessage(EVENT_GET_CDMA_SUBSCRIPTION_DONE));

    }

    private void fetchRuimRecords() {
        recordsRequested = true;

        Log.v(LOG_TAG, "RuimRecords:fetchRuimRecords " + recordsToLoad);

        phone.getIccFileHandler().loadEFTransparent(EF_ICCID,
                obtainMessage(EVENT_GET_ICCID_DONE));
        recordsToLoad++;

        // Further records that can be inserted are Operator/OEM dependent
    }

    @Override
    protected int getDisplayRule(String plmn) {
        // TODO together with spn
        return 0;
    }

    @Override
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        Log.i(LOG_TAG, "RuimRecords: setVoiceMessageWaiting not supported.");
    }

    private void handleRuimRefresh(int[] result) {
        if (result == null || result.length == 0) {
            return;
        }

        switch ((result[0])) {
            case CommandsInterface.SIM_REFRESH_FILE_UPDATED:
                adnCache.reset();
                fetchRuimRecords();
                break;
            case CommandsInterface.SIM_REFRESH_INIT:
                // need to reload all files (that we care about)
                fetchRuimRecords();
                break;
            case CommandsInterface.SIM_REFRESH_RESET:
                phone.mCM.setRadioPower(false, null);
                /* Note: no need to call setRadioPower(true).  Assuming the desired
                * radio power state is still ON (as tracked by ServiceStateTracker),
                * ServiceStateTracker will call setRadioPower when it receives the
                * RADIO_STATE_CHANGED notification for the power off.  And if the
                * desired power state has changed in the interim, we don't want to
                * override it with an unconditional power on.
                */
                break;
            default:
                // unknown refresh operation
                break;
        }
    }

    protected void log(String s) {
        Log.d(LOG_TAG, "[RuimRecords] " + s);
    }

}

