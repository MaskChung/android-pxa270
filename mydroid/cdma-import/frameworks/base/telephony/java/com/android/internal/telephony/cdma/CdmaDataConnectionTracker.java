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

package com.android.internal.telephony.cdma;

import android.app.AlarmManager;
import android.app.IAlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.INetStatService;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.Checkin;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DataConnection;
import com.android.internal.telephony.DataConnection.FailCause;
import com.android.internal.telephony.DataConnectionTracker;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneProxy;

import java.util.ArrayList;

/**
 * {@hide}
 *
 */
public final class CdmaDataConnectionTracker extends DataConnectionTracker {
    private static final String LOG_TAG = "CDMA";
    private static final boolean DBG = true;

    //***** Instance Variables

    // Indicates baseband will not auto-attach
    private boolean noAutoAttach = false;
    long nextReconnectDelay = RECONNECT_DELAY_INITIAL_MILLIS;
    private IntentFilter filterS;
    private IntentFilter filter;

    //useful for debugging
    boolean failNextConnect = false;

    /**
     * dataConnectionList holds all the Data connection
     */
    private ArrayList<DataConnection> dataConnectionList;

    /** Currently active CdmaDataConnection */
    private CdmaDataConnection mActiveDataConnection;

    /** Defined cdma connection profiles */
    private static int EXTERNAL_NETWORK_DEFAULT_ID = 0;
    private static int EXTERNAL_NETWORK_NUM_TYPES  = 1;

    private boolean[] dataEnabled = new boolean[EXTERNAL_NETWORK_NUM_TYPES];

    //***** Constants

    /**
     * Pool size of CdmaDataConnection objects.
     */
    private static final int DATA_CONNECTION_POOL_SIZE = 1;

    private static final int POLL_CONNECTION_MILLIS = 5 * 1000;
    static final String INTENT_RECONNECT_ALARM = "com.android.internal.telephony.cdma-reconnect";

    BroadcastReceiver screenOnOffReceiver = new BroadcastReceiver () {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) &&
                    phone.getState() == Phone.State.IDLE) {
                stopNetStatPoll();
                startNetStatPoll();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                stopNetStatPoll();
                startNetStatPoll();
            } else {
                Log.w(LOG_TAG, "DataConnectionTracker received unexpected Intent: "
                        + intent.getAction());
            }
        }
    };

    BroadcastReceiver alarmReceiver = new BroadcastReceiver () {

        public void onReceive(Context context, Intent intent) {
            Log.d(LOG_TAG, "Data reconnect alarm. Previous state was " + state);

            if (state == State.FAILED) {
                cleanUpConnection(false, null);
            }

            trySetupData(null);
        }
    };


    //***** Constructor

    CdmaDataConnectionTracker(CDMAPhone p) {
        super(p);

        p.mCM.registerForAvailable (this, EVENT_RADIO_AVAILABLE, null);
        p.mCM.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        p.mRuimRecords.registerForRecordsLoaded(this, EVENT_RECORDS_LOADED, null);
        p.mCM.registerForNVReady(this, EVENT_NV_READY, null);
        p.mCM.registerForDataStateChanged (this, EVENT_DATA_STATE_CHANGED, null);
        p.mCT.registerForVoiceCallEnded (this, EVENT_VOICE_CALL_ENDED, null);
        p.mCT.registerForVoiceCallStarted (this, EVENT_VOICE_CALL_STARTED, null);
        p.mSST.registerForCdmaDataConnectionAttached(this, EVENT_TRY_SETUP_DATA, null);
        p.mSST.registerForCdmaDataConnectionDetached(this, EVENT_CDMA_DATA_DETACHED, null);
        p.mSST.registerForRoamingOn(this, EVENT_ROAMING_ON, null);
        p.mSST.registerForRoamingOff(this, EVENT_ROAMING_OFF, null);

        this.netstat = INetStatService.Stub.asInterface(ServiceManager.getService("netstat"));

        filter = new IntentFilter();
        filter.addAction(INTENT_RECONNECT_ALARM);
        phone.getContext().registerReceiver(
                alarmReceiver, filter, null, phone.getHandler());

        filterS = new IntentFilter();
        filterS.addAction(Intent.ACTION_SCREEN_ON);
        filterS.addAction(Intent.ACTION_SCREEN_OFF);
        p.getContext().registerReceiver(screenOnOffReceiver, filterS);

        mDataConnectionTracker = this;

        createAllDataConnectionList();

        // This preference tells us 1) initial condition for "dataEnabled",
        // and 2) whether the RIL will setup the baseband to auto-PS attach.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(phone.getContext());

        dataEnabled[EXTERNAL_NETWORK_DEFAULT_ID] =
                !sp.getBoolean(CDMAPhone.DATA_DISABLED_ON_BOOT_KEY, false);
        noAutoAttach = !dataEnabled[EXTERNAL_NETWORK_DEFAULT_ID];
    }

    public void dispose() {
        //Unregister from all events
        phone.mCM.unregisterForAvailable(this);
        phone.mCM.unregisterForOffOrNotAvailable(this);
        ((CDMAPhone) phone).mRuimRecords.unregisterForRecordsLoaded(this);
        phone.mCM.unregisterForNVReady(this);
        phone.mCM.unregisterForDataStateChanged(this);
        ((CDMAPhone) phone).mCT.unregisterForVoiceCallEnded(this);
        ((CDMAPhone) phone).mCT.unregisterForVoiceCallStarted(this);
        ((CDMAPhone) phone).mSST.unregisterForCdmaDataConnectionAttached(this);
        ((CDMAPhone) phone).mSST.unregisterForCdmaDataConnectionDetached(this);
        ((CDMAPhone) phone).mSST.unregisterForRoamingOn(this);
        ((CDMAPhone) phone).mSST.unregisterForRoamingOff(this);

        phone.getContext().unregisterReceiver(this.alarmReceiver);
        phone.getContext().unregisterReceiver(this.screenOnOffReceiver);
        destroyAllDataConnectionList();
    }

    protected void finalize() {
        if(DBG) Log.d(LOG_TAG, "CdmaDataConnectionTracker finalized");
    }

    void setState(State s) {
        if (state != s) {
            if (s == State.INITING) { // request Data connection context
                Checkin.updateStats(phone.getContext().getContentResolver(),
                        Checkin.Stats.Tag.PHONE_CDMA_DATA_ATTEMPTED, 1, 0.0);
            }

            if (s == State.CONNECTED) { // pppd is up
                Checkin.updateStats(phone.getContext().getContentResolver(),
                        Checkin.Stats.Tag.PHONE_CDMA_DATA_CONNECTED, 1, 0.0);
            }
        }

        state = s;
    }

    public int enableApnType(String type) {
        // This request is mainly used to enable MMS APN
        // In CDMA there is no need to enable/disable a different APN for MMS
        Log.d(LOG_TAG, "Request to enableApnType("+type+")");
        if (TextUtils.equals(type, Phone.APN_TYPE_MMS)) {
            return Phone.APN_ALREADY_ACTIVE;
        } else {
            return Phone.APN_REQUEST_FAILED;
        }
    }

    public int disableApnType(String type) {
        // This request is mainly used to disable MMS APN
        // In CDMA there is no need to enable/disable a different APN for MMS
        Log.d(LOG_TAG, "Request to disableApnType("+type+")");
        if (TextUtils.equals(type, Phone.APN_TYPE_MMS)) {
            return Phone.APN_REQUEST_STARTED;
        } else {
            return Phone.APN_REQUEST_FAILED;
        }
    }

    private boolean isEnabled(int cdmaDataProfile) {
        return dataEnabled[cdmaDataProfile];
    }

    private void setEnabled(int cdmaDataProfile, boolean enable) {
        Log.d(LOG_TAG, "setEnabled("  + cdmaDataProfile + ", " + enable + ')');
        dataEnabled[cdmaDataProfile] = enable;
        Log.d(LOG_TAG, "dataEnabled[DEFAULT_PROFILE]=" + dataEnabled[EXTERNAL_NETWORK_DEFAULT_ID]);
    }

    /**
     * Prevent mobile data connections from being established,
     * or once again allow mobile data connections. If the state
     * toggles, then either tear down or set up data, as
     * appropriate to match the new state.
     * <p>This operation only affects the default connection
     * @param enable indicates whether to enable ({@code true}) or disable ({@code false}) data
     * @return {@code true} if the operation succeeded
     */
    public boolean setDataEnabled(boolean enable) {

        boolean isEnabled = isEnabled(EXTERNAL_NETWORK_DEFAULT_ID);

        Log.d(LOG_TAG, "setDataEnabled("+enable+") isEnabled=" + isEnabled);
        if (!isEnabled && enable) {
            setEnabled(EXTERNAL_NETWORK_DEFAULT_ID, true);
            return trySetupData(Phone.REASON_DATA_ENABLED);
        } else if (!enable) {
            setEnabled(EXTERNAL_NETWORK_DEFAULT_ID, false);
            return false;
        } else // isEnabled && enable

        return true;
    }

    /**
     * Report the current state of data connectivity (enabled or disabled)
     * @return {@code false} if data connectivity has been explicitly disabled,
     * {@code true} otherwise.
     */
    public boolean getDataEnabled() {
        return dataEnabled[EXTERNAL_NETWORK_DEFAULT_ID];
    }

    /**
     * Report on whether data connectivity is enabled
     * @return {@code false} if data connectivity has been explicitly disabled,
     * {@code true} otherwise.
     */
    public boolean getAnyDataEnabled() {
        for (int i=0; i < EXTERNAL_NETWORK_NUM_TYPES; i++) {
            if (isEnabled(i)) return true;
        }
        return false;
    }

    //Retrieve the data roaming setting from the shared preferences.
    public boolean getDataOnRoamingEnabled() {
        try {
            return Settings.System.getInt(phone.getContext().getContentResolver(),
                    Settings.System.DATA_ROAMING) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    private boolean isDataAllowed() {
        boolean roaming = phone.getServiceState().getRoaming();
        return getAnyDataEnabled() && (!roaming || getDataOnRoamingEnabled());
    }

    private boolean trySetupData(String reason) {
        if (DBG) log("***trySetupData due to " + (reason == null ? "(unspecified)" : reason));

        if (phone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            setState(State.CONNECTED);
            phone.notifyDataConnection(reason);

            Log.i(LOG_TAG, "(fix?) We're on the simulator; assuming data is connected");
            return true;
        }

        int psState = ((CDMAPhone) phone).mSST.getCurrentCdmaDataConnectionState();
        boolean roaming = phone.getServiceState().getRoaming();

        if ((state == State.IDLE || state == State.SCANNING)
                && (psState == ServiceState.RADIO_TECHNOLOGY_1xRTT ||
                    psState == ServiceState.RADIO_TECHNOLOGY_EVDO_0 ||
                    psState == ServiceState.RADIO_TECHNOLOGY_EVDO_A)
                && ((phone.mCM.getRadioState() == CommandsInterface.RadioState.NV_READY) ||
                     ((CDMAPhone) phone).mRuimRecords.getRecordsLoaded())
                && ( ((CDMAPhone) phone).mSST.isConcurrentVoiceAndData() ||
                     phone.getState() == Phone.State.IDLE )
                && isDataAllowed()) {

            return setupData(reason);

        } else {
            if (DBG) {
                    log("trySetupData: Not ready for data: " +
                    " dataState=" + state +
                    " PS state=" + psState +
                    " radio state=" + phone.mCM.getRadioState() +
                    " ruim=" + ((CDMAPhone) phone).mRuimRecords.getRecordsLoaded() +
                    " concurrentVoice&Data=" + ((CDMAPhone) phone).mSST.isConcurrentVoiceAndData() +
                    " phoneState=" + phone.getState() +
                    " dataEnabled=" + getAnyDataEnabled() +
                    " roaming=" + roaming +
                    " dataOnRoamingEnable=" + getDataOnRoamingEnabled());
            }
            return false;
        }
    }

    /**
     * If tearDown is true, this only tears down a CONNECTED session. Presently,
     * there is no mechanism for abandoning an INITING/CONNECTING session,
     * but would likely involve cancelling pending async requests or
     * setting a flag or new state to ignore them when they came in
     * @param tearDown true if the underlying DataConnection should be
     * disconnected.
     * @param reason reason for the clean up.
     */
    private void cleanUpConnection(boolean tearDown, String reason) {
        if (DBG) log("Clean up connection due to " + reason);

        for (DataConnection connBase : dataConnectionList) {
            CdmaDataConnection conn = (CdmaDataConnection) connBase;

            if(conn != null) {
                if (tearDown) {
                    Message msg = obtainMessage(EVENT_DISCONNECT_DONE);
                    conn.disconnect(msg);
                } else {
                    conn.clearSettings();
                }
            }
        }

        stopNetStatPoll();
        setState(State.IDLE);
        phone.notifyDataConnection(reason);
    }

    private CdmaDataConnection findFreeDataConnection() {
        for (DataConnection connBase : dataConnectionList) {
            CdmaDataConnection conn = (CdmaDataConnection) connBase;
            if (conn.getState() == DataConnection.State.INACTIVE) {
                return conn;
            }
        }
        return null;
    }

    private boolean setupData(String reason) {

        CdmaDataConnection conn = findFreeDataConnection();

        if (conn == null) {
            if (DBG) log("setupData: No free CdmaDataConnectionfound!");
            return false;
        }

        mActiveDataConnection = conn;

        Message msg = obtainMessage();
        msg.what = EVENT_DATA_SETUP_COMPLETE;

        conn.connect(msg);

        setState(State.INITING);
        phone.notifyDataConnection(reason);
        return true;
    }

    private void notifyDefaultData() {
        setState(State.CONNECTED);
        phone.notifyDataConnection(null);
        startNetStatPoll();
        // reset reconnect timer
        nextReconnectDelay = RECONNECT_DELAY_INITIAL_MILLIS;
    }

    private void resetPollStats() {
        txPkts = -1;
        rxPkts = -1;
        sentSinceLastRecv = 0;
        netStatPollPeriod = POLL_NETSTAT_MILLIS;
        mNoRecvPollCount = 0;
    }

    protected void startNetStatPoll() {
        if (state == State.CONNECTED) {
            Log.d(LOG_TAG, "[DataConnection] Start poll NetStat");
            resetPollStats();
            netStatPollEnabled = true;
            mPollNetStat.run();
        }
    }

    protected void stopNetStatPoll() {
        netStatPollEnabled = false;
        removeCallbacks(mPollNetStat);
        Log.d(LOG_TAG, "[DataConnection] Stop poll NetStat");
    }

    protected void restartRadio() {
        Log.d(LOG_TAG, "************TURN OFF RADIO**************");
        cleanUpConnection(true, Phone.REASON_RADIO_TURNED_OFF);
        phone.mCM.setRadioPower(false, null);
        /* Note: no need to call setRadioPower(true).  Assuming the desired
         * radio power state is still ON (as tracked by ServiceStateTracker),
         * ServiceStateTracker will call setRadioPower when it receives the
         * RADIO_STATE_CHANGED notification for the power off.  And if the
         * desired power state has changed in the interim, we don't want to
         * override it with an unconditional power on.
         */
    }

    /**
     * Returns true if the last fail cause is something that
     * seems like it deserves an error notification.
     * Transient errors are ignored
     */
    private boolean
    shouldPostNotification(FailCause cause) {
        return (cause != FailCause.UNKNOWN);
    }

    private void
    reconnectAfterFail(FailCause lastFailCauseCode) {
        if (state == State.FAILED) {
            Log.d(LOG_TAG, "Data Connection activate failed. Scheduling next attempt for "
                    + (nextReconnectDelay / 1000) + "s");

            try {
                IAlarmManager am = IAlarmManager.Stub.asInterface(
                        ServiceManager.getService(Context.ALARM_SERVICE));
                PendingIntent sender = PendingIntent.getBroadcast(
                        phone.getContext(), 0,
                        new Intent(INTENT_RECONNECT_ALARM), 0);
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + nextReconnectDelay,
                        sender);
            } catch (RemoteException ex) {
            }

            // double it for next time
            nextReconnectDelay *= 2;

            if (!shouldPostNotification(lastFailCauseCode)) {
                Log.d(LOG_TAG,"NOT Posting Data Connection Unavailable notification "
                                + "-- likely transient error");
            } else {
                notifyNoData(lastFailCauseCode);
            }
        }
    }

    private void notifyNoData(FailCause lastFailCauseCode) {
        setState(State.FAILED);
    }

    protected void onRecordsLoaded() {
        if (state == State.FAILED) {
            cleanUpConnection(false, null);
        }
        sendMessage(obtainMessage(EVENT_TRY_SETUP_DATA));
    }

    protected void onNVReady() {
        if (state == State.FAILED) {
            cleanUpConnection(false, null);
        }
        sendMessage(obtainMessage(EVENT_TRY_SETUP_DATA));
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onTrySetupData() {
        trySetupData(null);
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onRoamingOff() {
        trySetupData(Phone.REASON_ROAMING_OFF);
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onRoamingOn() {
        if (getDataOnRoamingEnabled()) {
            trySetupData(Phone.REASON_ROAMING_ON);
        } else {
            if (DBG) log("Tear down data connection on roaming.");
            cleanUpConnection(true, Phone.REASON_ROAMING_ON);
        }
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onRadioAvailable() {
        if (phone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            setState(State.CONNECTED);
            phone.notifyDataConnection(null);

            Log.i(LOG_TAG, "We're on the simulator; assuming data is connected");
        }

        if (state != State.IDLE) {
            cleanUpConnection(true, null);
        }
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onRadioOffOrNotAvailable() {
        // Make sure our reconnect delay starts at the initial value
        // next time the radio comes on
        nextReconnectDelay = RECONNECT_DELAY_INITIAL_MILLIS;

        if (phone.getSimulatedRadioControl() != null) {
            // Assume data is connected on the simulator
            // FIXME  this can be improved
            Log.i(LOG_TAG, "We're on the simulator; assuming radio off is meaningless");
        } else {
            if (DBG) log("Radio is off and clean up all connection");
            cleanUpConnection(false, Phone.REASON_RADIO_TURNED_OFF);
        }
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onDataSetupComplete(AsyncResult ar) {

        if (ar.exception == null) {
            // everything is setup
            notifyDefaultData();

        } else {
            FailCause cause = (FailCause) (ar.result);
            if(DBG) log("Data Connection setup failed " + cause);

            // No try for permanent failure
            if (cause.isPermanentFail()) {
                notifyNoData(cause);
            }

            if (tryAgain(cause)) {
                    trySetupData(null);
            } else {
                notifyNoData(cause);
                reconnectAfterFail(cause);
            }
        }

    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onDisconnectDone() {
        if(DBG) log("EVENT_DISCONNECT_DONE");
        trySetupData(null);
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onVoiceCallStarted() {
        if (state == State.CONNECTED && !((CDMAPhone) phone).mSST.isConcurrentVoiceAndData()) {
            stopNetStatPoll();
            phone.notifyDataConnection(Phone.REASON_VOICE_CALL_STARTED);
        }
    }

    /**
     * @override com.android.internal.telephony.DataConnectionTracker
     */
    protected void onVoiceCallEnded() {
         // in case data setup was attempted when we were on a voice call
        trySetupData(Phone.REASON_VOICE_CALL_ENDED);
        if (state == State.CONNECTED &&
                !((CDMAPhone) phone).mSST.isConcurrentVoiceAndData()) {
            startNetStatPoll();
            phone.notifyDataConnection(Phone.REASON_VOICE_CALL_ENDED);
        } else {
            // clean slate after call end.
            resetPollStats();
        }
    }

    private boolean tryAgain(FailCause cause) {
        return (cause != FailCause.RADIO_NOT_AVAILABLE)
            && (cause != FailCause.RADIO_OFF)
            && (cause != FailCause.RADIO_ERROR_RETRY)
            && (cause != FailCause.NO_SIGNAL)
            && (cause != FailCause.SIM_LOCKED);
    }

    private void createAllDataConnectionList() {
       dataConnectionList = new ArrayList<DataConnection>();
        CdmaDataConnection dataConn;

       for (int i = 0; i < DATA_CONNECTION_POOL_SIZE; i++) {
            dataConn = new CdmaDataConnection((CDMAPhone) phone);
            dataConnectionList.add(dataConn);
       }
   }

    private void destroyAllDataConnectionList() {
        if(dataConnectionList != null) {
            CdmaDataConnection pdp;
            dataConnectionList.removeAll(dataConnectionList);
        }
    }

    private void onCdmaDataAttached() {
        if (state == State.CONNECTED) {
            startNetStatPoll();
            phone.notifyDataConnection(Phone.REASON_CDMA_DATA_DETACHED);
        } else {
            trySetupData(Phone.REASON_CDMA_DATA_DETACHED);
        }
    }

    protected void onDataStateChanged (AsyncResult ar) {

        if (ar.exception != null) {
            // This is probably "radio not available" or something
            // of that sort. If so, the whole connection is going
            // to come down soon anyway
            return;
        }

        Log.i(LOG_TAG, "Data connection has changed.");
    }

    String getInterfaceName() {
        if (mActiveDataConnection != null) {
            return mActiveDataConnection.getInterface();
        }
        return null;
    }

    protected String getIpAddress() {
        if (mActiveDataConnection != null) {
            return mActiveDataConnection.getIpAddress();
        }
        return null;
    }

    String getGateway() {
        if (mActiveDataConnection != null) {
            return mActiveDataConnection.getGatewayAddress();
        }
        return null;
    }

    protected String[] getDnsServers() {
        if (mActiveDataConnection != null) {
            return mActiveDataConnection.getDnsServers();
        }
        return null;
    }

    public ArrayList<DataConnection> getAllDataConnections() {
        return dataConnectionList;
    }

    public void handleMessage (Message msg) {

        switch (msg.what) {
            case EVENT_RECORDS_LOADED:
                onRecordsLoaded();
                break;

            case EVENT_NV_READY:
                onNVReady();
                break;

            case EVENT_CDMA_DATA_DETACHED:
                onCdmaDataAttached();
                break;

            case EVENT_DATA_STATE_CHANGED:
                onDataStateChanged((AsyncResult) msg.obj);
                break;

            default:
                // handle the message in the super class DataConnectionTracker
                super.handleMessage(msg);
                break;
        }
    }

    protected void log(String s) {
        Log.d(LOG_TAG, "[CdmaDataConnectionTracker] " + s);
    }
}
