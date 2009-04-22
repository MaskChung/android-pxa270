/*
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

package com.android.internal.telephony;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.gsm.PdpConnection;
import com.android.internal.telephony.test.SimulatedRadioControl;

import java.util.List;


/**
 * (<em>Not for SDK use</em>)
 * A base implementation for the com.android.internal.telephony.Phone interface.
 *
 * Note that implementations of Phone.java are expected to be used
 * from a single application thread. This should be the same thread that
 * originally called PhoneFactory to obtain the interface.
 *
 *  {@hide}
 *
 */

public abstract class PhoneBase implements Phone {
    private static final String LOG_TAG = "PHONE";
    private static final boolean LOCAL_DEBUG = true;

    // Key used to read and write the saved network selection value
    public static final String NETWORK_SELECTION_KEY = "network_selection_key";

 // Key used to read/write "disable data connection on boot" pref (used for testing)
    public static final String DATA_DISABLED_ON_BOOT_KEY = "disabled_on_boot_key";

    //***** Event Constants
    protected static final int EVENT_RADIO_AVAILABLE             = 1;
    /** Supplementary Service Notification received. */
    protected static final int EVENT_SSN                         = 2;
    protected static final int EVENT_SIM_RECORDS_LOADED          = 3;
    protected static final int EVENT_MMI_DONE                    = 4;
    protected static final int EVENT_RADIO_ON                    = 5;
    protected static final int EVENT_GET_BASEBAND_VERSION_DONE   = 6;
    protected static final int EVENT_USSD                        = 7;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE  = 8;
    protected static final int EVENT_GET_IMEI_DONE               = 9;
    protected static final int EVENT_GET_IMEISV_DONE             = 10;
    protected static final int EVENT_GET_SIM_STATUS_DONE         = 11;
    protected static final int EVENT_SET_CALL_FORWARD_DONE       = 12;
    protected static final int EVENT_GET_CALL_FORWARD_DONE       = 13;
    protected static final int EVENT_CALL_RING                   = 14;
    // Used to intercept the carrier selection calls so that
    // we can save the values.
    protected static final int EVENT_SET_NETWORK_MANUAL_COMPLETE    = 15;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC_COMPLETE = 16;
    protected static final int EVENT_SET_CLIR_COMPLETE              = 17;
    protected static final int EVENT_REGISTERED_TO_NETWORK          = 18;
    // Events for CDMA support
    protected static final int EVENT_GET_DEVICE_IDENTITY_DONE       = 19;
    protected static final int EVENT_RUIM_RECORDS_LOADED            = 3;
    protected static final int EVENT_NV_READY                       = 20;
    protected static final int EVENT_SET_ENHANCED_VP = 21;

    // Key used to read/write current CLIR setting
    public static final String CLIR_KEY = "clir_key";


    //***** Instance Variables
    public CommandsInterface mCM;
    protected IccFileHandler mIccFileHandler;

    /**
     * Set a system property, unless we're in unit test mode
     */
    public void
    setSystemProperty(String property, String value) {
        if(getUnitTestMode()) {
            return;
        }
        SystemProperties.set(property, value);
    }


    protected final RegistrantList mPhoneStateRegistrants
            = new RegistrantList();

    protected final RegistrantList mNewRingingConnectionRegistrants
            = new RegistrantList();

    protected final RegistrantList mIncomingRingRegistrants
            = new RegistrantList();

    protected final RegistrantList mDisconnectRegistrants
            = new RegistrantList();

    protected final RegistrantList mServiceStateRegistrants
            = new RegistrantList();

    protected final RegistrantList mMmiCompleteRegistrants
            = new RegistrantList();

    protected final RegistrantList mMmiRegistrants
            = new RegistrantList();

    protected final RegistrantList mUnknownConnectionRegistrants
            = new RegistrantList();

    protected final RegistrantList mSuppServiceFailedRegistrants
            = new RegistrantList();

    protected Looper mLooper; /* to insure registrants are in correct thread*/

    protected Context mContext;

    /**
     * PhoneNotifier is an abstraction for all system-wide
     * state change notification. DefaultPhoneNotifier is
     * used here unless running we're inside a unit test.
     */
    protected PhoneNotifier mNotifier;

    protected SimulatedRadioControl mSimulatedRadioControl;

    boolean mUnitTestMode;

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param context Context object from hosting application
     * @param notifier An instance of DefaultPhoneNotifier,
     * unless unit testing.
     */
    protected PhoneBase(PhoneNotifier notifier, Context context) {
        this(notifier, context, false);
    }

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param context Context object from hosting application
     * @param notifier An instance of DefaultPhoneNotifier,
     * unless unit testing.
     * @param unitTestMode when true, prevents notifications
     * of state change events
     */
    protected PhoneBase(PhoneNotifier notifier, Context context,
            boolean unitTestMode) {
        this.mNotifier = notifier;
        this.mContext = context;
        mLooper = Looper.myLooper();

        setUnitTestMode(unitTestMode);
    }

    // Inherited documentation suffices.
    public Context getContext() {
        return mContext;
    }

    // Inherited documentation suffices.
    public void registerForPhoneStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mPhoneStateRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForPhoneStateChanged(Handler h) {
        mPhoneStateRegistrants.remove(h);
    }

    /**
     * Notify registrants of a PhoneStateChanged.
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyCallStateChangedP() {
        AsyncResult ar = new AsyncResult(null, this, null);
        mPhoneStateRegistrants.notifyRegistrants(ar);
    }

    // Inherited documentation suffices.
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForUnknownConnection(Handler h) {
        mUnknownConnectionRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForNewRingingConnection(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForNewRingingConnection(Handler h) {
        mNewRingingConnectionRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj){
        mCM.registerForInCallVoicePrivacyOn(h,what,obj);
    }

    // Inherited documentation suffices.
    public void unregisterForInCallVoicePrivacyOn(Handler h){
        mCM.unregisterForInCallVoicePrivacyOn(h);
    }

    // Inherited documentation suffices.
    public void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj){
        mCM.registerForInCallVoicePrivacyOff(h,what,obj);
    }

    // Inherited documentation suffices.
    public void unregisterForInCallVoicePrivacyOff(Handler h){
        mCM.unregisterForInCallVoicePrivacyOff(h);
    }


    /**
     * Notifiy registrants of a new ringing Connection.
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyNewRingingConnectionP(Connection cn) {
        AsyncResult ar = new AsyncResult(null, cn, null);
        mNewRingingConnectionRegistrants.notifyRegistrants(ar);
    }

    // Inherited documentation suffices.
    public void registerForIncomingRing(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mIncomingRingRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForIncomingRing(Handler h) {
        mIncomingRingRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForDisconnect(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mDisconnectRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForDisconnect(Handler h) {
        mDisconnectRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForSuppServiceFailed(Handler h) {
        mSuppServiceFailedRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mMmiRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForMmiInitiate(Handler h) {
        mMmiRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForMmiComplete(Handler h) {
        checkCorrectThread(h);

        mMmiCompleteRegistrants.remove(h);
    }

    /**
     * Method to retrieve the saved operator id from the Shared Preferences
     */
    private String getSavedNetworkSelection() {
        // open the shared preferences and search with our key.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(NETWORK_SELECTION_KEY, "");
    }

    /**
     * Method to restore the previously saved operator id, or reset to
     * automatic selection, all depending upon the value in the shared
     * preferences.
     */
    public void restoreSavedNetworkSelection(Message response) {
        // retrieve the operator id
        String networkSelection = getSavedNetworkSelection();

        // set to auto if the id is empty, otherwise select the network.
        if (TextUtils.isEmpty(networkSelection)) {
            mCM.setNetworkSelectionModeAutomatic(response);
        } else {
            mCM.setNetworkSelectionModeManual(networkSelection, response);
        }
    }

    // Inherited documentation suffices.
    public void setUnitTestMode(boolean f) {
        mUnitTestMode = f;
    }

    // Inherited documentation suffices.
    public boolean getUnitTestMode() {
        return mUnitTestMode;
    }

    /**
     * To be invoked when a voice call Connection disconnects.
     *
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyDisconnectP(Connection cn) {
        AsyncResult ar = new AsyncResult(null, cn, null);
        mDisconnectRegistrants.notifyRegistrants(ar);
    }

    // Inherited documentation suffices.
    public void registerForServiceStateChanged(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mServiceStateRegistrants.add(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForServiceStateChanged(Handler h) {
        mServiceStateRegistrants.remove(h);
    }

    /**
     * Subclasses of Phone probably want to replace this with a
     * version scoped to their packages
     */
    protected void notifyServiceStateChangedP(ServiceState ss) {
        AsyncResult ar = new AsyncResult(null, ss, null);
        mServiceStateRegistrants.notifyRegistrants(ar);

        mNotifier.notifyServiceState(this);
    }

    // Inherited documentation suffices.
    public SimulatedRadioControl getSimulatedRadioControl() {
        return mSimulatedRadioControl;
    }

    /**
     * Verifies the current thread is the same as the thread originally
     * used in the initialization of this instance. Throws RuntimeException
     * if not.
     *
     * @exception RuntimeException if the current thread is not
     * the thread that originally obtained this PhoneBase instance.
     */
    private void checkCorrectThread(Handler h) {
        if (h.getLooper() != mLooper) {
            throw new RuntimeException(
                    "com.android.internal.telephony.Phone must be used from within one thread");
        }
    }

    /**
     * Retrieves the Handler of the Phone instance
     */
    public abstract Handler getHandler();

    /**
     * Retrieves the IccFileHandler of the Phone instance
     */
    public abstract IccFileHandler getIccFileHandler();


    /**
     *  Query the status of the CDMA roaming preference
     */
    public void queryCdmaRoamingPreference(Message response) {
        mCM.queryCdmaRoamingPreference(response);
    }

    /**
     *  Set the status of the CDMA roaming preference
     */
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        mCM.setCdmaRoamingPreference(cdmaRoamingType, response);
    }

    /**
     *  Set the status of the CDMA subscription mode
     */
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        mCM.setCdmaSubscription(cdmaSubscriptionType, response);
    }

    /**
     *  Set the preferred Network Type: Global, CDMA only or GSM/UMTS only
     */
    public void setPreferredNetworkType(int networkType, Message response) {
        mCM.setPreferredNetworkType(networkType, response);
    }

    /**
     *  Set the status of the preferred Network Type: Global, CDMA only or GSM/UMTS only
     */
    public void getPreferredNetworkType(Message response) {
        mCM.getPreferredNetworkType(response);
    }

    public void setTTYModeEnabled(boolean enable, Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void queryTTYModeEnabled(Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    /**
     * This should only be called in GSM mode.
     * Only here for some backward compatibility
     * issues concerning the GSMPhone class.
     * @deprecated
     */
    public List<PdpConnection> getCurrentPdpList() {
        return null;
    }

    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void getEnhancedVoicePrivacy(Message onComplete) {
        // This function should be overridden by the class CDMAPhone. Not implemented in GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, inactive CDMAPhone.");
    }

    public void setBandMode(int bandMode, Message response) {
        mCM.setBandMode(bandMode, response);
    }

    public void queryAvailableBandMode(Message response) {
        mCM.queryAvailableBandMode(response);
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        mCM.invokeOemRilRequestRaw(data, response);
    }

    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        mCM.invokeOemRilRequestStrings(strings, response);
    }

    public void notifyDataActivity() {
        mNotifier.notifyDataActivity(this);
    }

    public void notifyDataConnection(String reason) {
        mNotifier.notifyDataConnection(this, reason);
    }

    public abstract String getPhoneName();

}
