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

package com.android.internal.telephony.gsm;

import static com.android.internal.telephony.TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_OPERATOR_ALPHA;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_OPERATOR_ISMANUAL;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_OPERATOR_ISROAMING;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_OPERATOR_NUMERIC;
import com.android.internal.telephony.Phone;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Checkin;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Telephony.Intents;
import android.telephony.ServiceState;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;
import android.util.TimeUtils;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.TelephonyIntents;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * {@hide}
 */
final class GsmServiceStateTracker extends ServiceStateTracker {
    //***** Instance Variables
    GSMPhone phone;
    GsmCellLocation cellLoc;
    GsmCellLocation newCellLoc;
    int mPreferredNetworkType;

    int rssi = 99;     // signal strength 0-31, 99=unknown
                       // That's "received signal strength indication" fyi

    private int gprsState = ServiceState.STATE_OUT_OF_SERVICE;
    private int newGPRSState = ServiceState.STATE_OUT_OF_SERVICE;

    /**
     *  The access technology currently in use: DATA_ACCESS_
     */
    private int networkType = 0;
    private int newNetworkType = 0;
    /* gsm roaming status solely based on TS 27.007 7.2 CREG */
    private boolean mGsmRoaming = false;

    private RegistrantList gprsAttachedRegistrants = new RegistrantList();
    private RegistrantList gprsDetachedRegistrants = new RegistrantList();

    // Sometimes we get the NITZ time before we know what country we are in.
    // Keep the time zone information from the NITZ string so we can fix
    // the time zone once know the country.
    private boolean mNeedFixZone = false;
    private int mZoneOffset;
    private boolean mZoneDst;
    private long mZoneTime;
    private boolean mGotCountryCode = false;
    private ContentResolver cr;

    String mSavedTimeZone;
    long mSavedTime;
    long mSavedAtTime;

    // We can't register for SIM_RECORDS_LOADED immediately because the
    // SIMRecords object may not be instantiated yet.
    private boolean mNeedToRegForSimLoaded;

    // Keep track of SPN display rules, so we only broadcast intent if something changes.
    private String curSpn = null;
    private String curPlmn = null;
    private int curSpnRule = 0;

    //***** Constants

    static final boolean DBG = true;
    static final String LOG_TAG = "GSM";


    private ContentObserver mAutoTimeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Log.i("GsmServiceStateTracker", "Auto time state changed");
            revertToNitz();
        }
    };


    //***** Constructors

    public GsmServiceStateTracker(GSMPhone phone) {
        super();

        this.phone = phone;
        cm = phone.mCM;
        ss = new ServiceState();
        newSS = new ServiceState();
        cellLoc = new GsmCellLocation();
        newCellLoc = new GsmCellLocation();

        cm.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        cm.registerForRadioStateChanged(this, EVENT_RADIO_STATE_CHANGED, null);

        cm.registerForNetworkStateChanged(this, EVENT_NETWORK_STATE_CHANGED, null);
        cm.setOnNITZTime(this, EVENT_NITZ_TIME, null);
        cm.setOnSignalStrengthUpdate(this, EVENT_SIGNAL_STRENGTH_UPDATE, null);

        cm.registerForSIMReady(this, EVENT_SIM_READY, null);

        // system setting property AIRPLANE_MODE_ON is set in Settings.
        int airplaneMode = Settings.System.getInt(
                phone.getContext().getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0);
        mDesiredPowerState = ! (airplaneMode > 0);

        cr = phone.getContext().getContentResolver();
        cr.registerContentObserver(
                Settings.System.getUriFor(Settings.System.AUTO_TIME), true,
                mAutoTimeObserver);
        setRssiDefaultValues();
        mNeedToRegForSimLoaded = true;
    }

    public void dispose() {
        //Unregister for all events
        cm.unregisterForAvailable(this);
        cm.unregisterForRadioStateChanged(this);
        cm.unregisterForNetworkStateChanged(this);
        cm.unregisterForSIMReady(this);
        phone.mSIMRecords.unregisterForRecordsLoaded(this);
        cm.unSetOnSignalStrengthUpdate(this);
        cm.unSetOnNITZTime(this);
        cr.unregisterContentObserver(this.mAutoTimeObserver);
    }

    protected void finalize() {
        if(DBG) Log.d(LOG_TAG, "GsmServiceStateTracker finalized");
    }

    /**
     * Registration point for transition into GPRS attached.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    /*protected*/ void
    registerForGprsAttached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        gprsAttachedRegistrants.add(r);

        if (gprsState == ServiceState.STATE_IN_SERVICE) {
            r.notifyRegistrant();
        }
    }

    /*protected*/ void unregisterForGprsAttached(Handler h) {
        gprsAttachedRegistrants.remove(h);
    }

    void registerForNetworkAttach(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        networkAttachedRegistrants.add(r);

        if (ss.getState() == ServiceState.STATE_IN_SERVICE) {
            r.notifyRegistrant();
        }
    }
    void unregisterForNetworkAttach(Handler h) {
        networkAttachedRegistrants.remove(h);
    }
    /**
     * Registration point for transition into GPRS detached.
     * @param h handler to notify
     * @param what what code of message when delivered
     * @param obj placed in Message.obj
     */
    /*protected*/  void
    registerForGprsDetached(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        gprsDetachedRegistrants.add(r);

        if (gprsState == ServiceState.STATE_OUT_OF_SERVICE) {
            r.notifyRegistrant();
        }
    }

    /*protected*/  void unregisterForGprsDetached(Handler h) {
        gprsDetachedRegistrants.remove(h);
    }

    //***** Called from GSMPhone
    public void
    getLacAndCid(Message onComplete) {
        cm.getRegistrationState(obtainMessage(
                        EVENT_GET_LOC_DONE, onComplete));
    }


    //***** Overridden from ServiceStateTracker
    public void
    handleMessage (Message msg) {
        AsyncResult ar;
        int[] ints;
        String[] strings;
        Message message;

        switch (msg.what) {
            case EVENT_RADIO_AVAILABLE:
                //this is unnecessary
                //setPowerStateToDesired();
                break;

            case EVENT_SIM_READY:
                // The SIM is now ready i.e if it was locked
                // it has been unlocked. At this stage, the radio is already
                // powered on.
                if (mNeedToRegForSimLoaded) {
                    phone.mSIMRecords.registerForRecordsLoaded(this,
                            EVENT_SIM_RECORDS_LOADED, null);
                    mNeedToRegForSimLoaded = false;
                }
                // restore the previous network selection.
                phone.restoreSavedNetworkSelection(null);
                pollState();
                // Signal strength polling stops when radio is off
                queueNextSignalStrengthPoll();
                break;

            case EVENT_RADIO_STATE_CHANGED:
                // This will do nothing in the radio not
                // available case
                setPowerStateToDesired();
                pollState();
                break;

            case EVENT_NETWORK_STATE_CHANGED:
                pollState();
                break;

            case EVENT_GET_SIGNAL_STRENGTH:
                // This callback is called when signal strength is polled
                // all by itself

                if (!(cm.getRadioState().isOn()) || (cm.getRadioState().isCdma())) {
                    // Polling will continue when radio turns back on and not CDMA
                    return;
                }
                ar = (AsyncResult) msg.obj;
                onSignalStrengthResult(ar);
                queueNextSignalStrengthPoll();

                break;

            case EVENT_GET_LOC_DONE:
                ar = (AsyncResult) msg.obj;

                if (ar.exception == null) {
                    String states[] = (String[])ar.result;
                    int lac = -1;
                    int cid = -1;
                    if (states.length == 3) {
                        try {
                            if (states[1] != null && states[1].length() > 0) {
                                lac = Integer.parseInt(states[1], 16);
                            }
                            if (states[2] != null && states[2].length() > 0) {
                                cid = Integer.parseInt(states[2], 16);
                            }
                        } catch (NumberFormatException ex) {
                            Log.w(LOG_TAG, "error parsing location: " + ex);
                        }
                    }

                    // only update if lac or cid changed
                    if (cellLoc.getCid() != cid || cellLoc.getLac() != lac) {
                        cellLoc.setLacAndCid(lac, cid);
                        phone.notifyLocationChanged();
                    }
                }

                if (ar.userObj != null) {
                    AsyncResult.forMessage(((Message) ar.userObj)).exception
                            = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                }
                break;

            case EVENT_POLL_STATE_REGISTRATION:
            case EVENT_POLL_STATE_GPRS:
            case EVENT_POLL_STATE_OPERATOR:
            case EVENT_POLL_STATE_NETWORK_SELECTION_MODE:
                ar = (AsyncResult) msg.obj;

                handlePollStateResult(msg.what, ar);
                break;

            case EVENT_POLL_SIGNAL_STRENGTH:
                // Just poll signal strength...not part of pollState()

                cm.getSignalStrength(obtainMessage(EVENT_GET_SIGNAL_STRENGTH));
                break;

            case EVENT_NITZ_TIME:
                ar = (AsyncResult) msg.obj;

                String nitzString = (String)((Object[])ar.result)[0];
                int nitzReceiveTime = ((Integer)((Object[])ar.result)[1]).intValue();

                setTimeFromNITZString(nitzString, nitzReceiveTime);
                break;

            case EVENT_SIGNAL_STRENGTH_UPDATE:
                // This is a notification from
                // CommandsInterface.setOnSignalStrengthUpdate

                ar = (AsyncResult) msg.obj;

                // The radio is telling us about signal strength changes
                // we don't have to ask it
                dontPollSignalStrength = true;

                onSignalStrengthResult(ar);
                break;

            case EVENT_SIM_RECORDS_LOADED:
                updateSpnDisplay();
                break;

            case EVENT_LOCATION_UPDATES_ENABLED:
                ar = (AsyncResult) msg.obj;

                if (ar.exception == null) {
                    getLacAndCid(null);
                }
                break;

            case EVENT_SET_PREFERRED_NETWORK_TYPE:
                ar = (AsyncResult) msg.obj;
                // Don't care the result, only use for dereg network (COPS=2)
                message = obtainMessage(EVENT_RESET_PREFERRED_NETWORK_TYPE, ar.userObj);
                cm.setPreferredNetworkType(mPreferredNetworkType, message);
                break;

            case EVENT_RESET_PREFERRED_NETWORK_TYPE:
                ar = (AsyncResult) msg.obj;
                if (ar.userObj != null) {
                    AsyncResult.forMessage(((Message) ar.userObj)).exception
                            = ar.exception;
                    ((Message) ar.userObj).sendToTarget();
                }
                break;

            case EVENT_GET_PREFERRED_NETWORK_TYPE:
                ar = (AsyncResult) msg.obj;

                if (ar.exception == null) {
                    mPreferredNetworkType = ((int[])ar.result)[0];
                } else {
                    mPreferredNetworkType = RILConstants.NETWORK_MODE_GLOBAL;
                }

                message = obtainMessage(EVENT_SET_PREFERRED_NETWORK_TYPE, ar.userObj);
                int toggledNetworkType = RILConstants.NETWORK_MODE_GLOBAL;

                cm.setPreferredNetworkType(toggledNetworkType, message);
                break;
            default:
                Log.e(LOG_TAG, "Unhandled message with number: " + msg.what);
            break;
        }
    }

    //***** Private Instance Methods

    protected void updateSpnDisplay() {
        int rule = phone.mSIMRecords.getDisplayRule(ss.getOperatorNumeric());
        String spn = phone.mSIMRecords.getServiceProviderName();
        String plmn = ss.getOperatorAlphaLong();

        if (rule != curSpnRule
                || !TextUtils.equals(spn, curSpn)
                || !TextUtils.equals(plmn, curPlmn)) {
            boolean showSpn =
                (rule & SIMRecords.SPN_RULE_SHOW_SPN) == SIMRecords.SPN_RULE_SHOW_SPN;
            boolean showPlmn =
                (rule & SIMRecords.SPN_RULE_SHOW_PLMN) == SIMRecords.SPN_RULE_SHOW_PLMN;
            Intent intent = new Intent(Intents.SPN_STRINGS_UPDATED_ACTION);
            intent.putExtra(Intents.EXTRA_SHOW_SPN, showSpn);
            intent.putExtra(Intents.EXTRA_SPN, spn);
            intent.putExtra(Intents.EXTRA_SHOW_PLMN, showPlmn);
            intent.putExtra(Intents.EXTRA_PLMN, plmn);
            phone.getContext().sendStickyBroadcast(intent);
        }
        curSpnRule = rule;
        curSpn = spn;
        curPlmn = plmn;
    }

    /**
     * Handle the result of one of the pollState()-related requests
     */

    protected void
    handlePollStateResult (int what, AsyncResult ar) {
        int ints[];
        String states[];

        // Ignore stale requests from last poll
        if (ar.userObj != pollingContext) return;

        if (ar.exception != null) {
            CommandException.Error err=null;

            if (ar.exception instanceof CommandException) {
                err = ((CommandException)(ar.exception)).getCommandError();
            }

            if (err == CommandException.Error.RADIO_NOT_AVAILABLE) {
                // Radio has crashed or turned off
                cancelPollState();
                return;
            }

            if (!cm.getRadioState().isOn()) {
                // Radio has crashed or turned off
                cancelPollState();
                return;
            }

            if (err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW &&
                    err != CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW) {
                Log.e(LOG_TAG,
                        "RIL implementation has returned an error where it must succeed",
                        ar.exception);
            }
        } else try {
            switch (what) {
                case EVENT_POLL_STATE_REGISTRATION:
                    states = (String[])ar.result;
                    int lac = -1;
                    int cid = -1;
                    int regState = -1;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);
                            if (states.length == 3) {
                                if (states[1] != null && states[1].length() > 0) {
                                    lac = Integer.parseInt(states[1], 16);
                                }
                                if (states[2] != null && states[2].length() > 0) {
                                    cid = Integer.parseInt(states[2], 16);
                                }
                            }
                        } catch (NumberFormatException ex) {
                            Log.w(LOG_TAG, "error parsing RegistrationState: " + ex);
                        }
                    }

                    mGsmRoaming = regCodeIsRoaming(regState);
                    newSS.setState (regCodeToServiceState(regState));

                    // LAC and CID are -1 if not avail
                    newCellLoc.setLacAndCid(lac, cid);
                break;

                case EVENT_POLL_STATE_GPRS:
                    states = (String[])ar.result;

                    int type = 0;
                    regState = -1;
                    if (states.length > 0) {
                        try {
                            regState = Integer.parseInt(states[0]);

                            // states[3] (if present) is the current radio technology
                            if (states.length >= 4 && states[3] != null) {
                                type = Integer.parseInt(states[3]);
                            }
                        } catch (NumberFormatException ex) {
                            Log.w(LOG_TAG, "error parsing GprsRegistrationState: " + ex);
                        }
                    }
                    newGPRSState = regCodeToServiceState(regState);
                    newNetworkType = type;
                break;

                case EVENT_POLL_STATE_OPERATOR:
                    String opNames[] = (String[])ar.result;

                    if (opNames != null && opNames.length >= 3) {
                        newSS.setOperatorName (
                                opNames[0], opNames[1], opNames[2]);
                    }
                break;

                case EVENT_POLL_STATE_NETWORK_SELECTION_MODE:
                    ints = (int[])ar.result;
                    newSS.setIsManualSelection(ints[0] == 1);
                break;
            }

        } catch (RuntimeException ex) {
            Log.e(LOG_TAG, "Exception while polling service state. "
                            + "Probably malformed RIL response.", ex);
        }

        pollingContext[0]--;

        if (pollingContext[0] == 0) {
            newSS.setRoaming(isRoamingBetweenOperators(mGsmRoaming, newSS));
            pollStateDone();
        }

    }

    private void
    setRssiDefaultValues() {
        rssi = 99;
    }

    /**
     * A complete "service state" from our perspective is
     * composed of a handful of separate requests to the radio.
     *
     * We make all of these requests at once, but then abandon them
     * and start over again if the radio notifies us that some
     * event has changed
     */

    private void
    pollState() {
        pollingContext = new int[1];
        pollingContext[0] = 0;

        switch (cm.getRadioState()) {
            case RADIO_UNAVAILABLE:
                newSS.setStateOutOfService();
                newCellLoc.setStateInvalid();
                setRssiDefaultValues();
                mGotCountryCode = false;

                pollStateDone();
            break;

            case RADIO_OFF:
                newSS.setStateOff();
                newCellLoc.setStateInvalid();
                setRssiDefaultValues();
                mGotCountryCode = false;

                pollStateDone();
            break;

            case RUIM_NOT_READY:
            case RUIM_READY:
            case RUIM_LOCKED_OR_ABSENT:
            case NV_NOT_READY:
            case NV_READY:
                log("Radio Technology Change ongoing, setting SS to off");
                newSS.setStateOff();
                newCellLoc.setStateInvalid();
                setRssiDefaultValues();
                mGotCountryCode = false;

                pollStateDone();
                break;

            default:
                // Issue all poll-related commands at once
                // then count down the responses, which
                // are allowed to arrive out-of-order

                pollingContext[0]++;
                cm.getOperator(
                    obtainMessage(
                        EVENT_POLL_STATE_OPERATOR, pollingContext));

                pollingContext[0]++;
                cm.getGPRSRegistrationState(
                    obtainMessage(
                        EVENT_POLL_STATE_GPRS, pollingContext));

                pollingContext[0]++;
                cm.getRegistrationState(
                    obtainMessage(
                        EVENT_POLL_STATE_REGISTRATION, pollingContext));

                pollingContext[0]++;
                cm.getNetworkSelectionMode(
                    obtainMessage(
                        EVENT_POLL_STATE_NETWORK_SELECTION_MODE, pollingContext));
            break;
        }
    }

    private static String networkTypeToString(int type) {
        //Network Type from GPRS_REGISTRATION_STATE
        String ret = "unknown";

        switch (type) {
            case DATA_ACCESS_GPRS:
                ret = "GPRS";
                break;
            case DATA_ACCESS_EDGE:
                ret = "EDGE";
                break;
            case DATA_ACCESS_UMTS:
                ret = "UMTS";
                break;
            default:
                Log.e(LOG_TAG, "Wrong network type: " + Integer.toString(type));
                break;
        }

        return ret;
    }

    private void
    pollStateDone() {
        if (DBG) {
            Log.d(LOG_TAG, "Poll ServiceState done: " +
                " oldSS=[" + ss + "] newSS=[" + newSS +
                "] oldGprs=" + gprsState + " newGprs=" + newGPRSState +
                " oldType=" + networkTypeToString(networkType) +
                " newType=" + networkTypeToString(newNetworkType));
        }

        boolean hasRegistered =
            ss.getState() != ServiceState.STATE_IN_SERVICE
            && newSS.getState() == ServiceState.STATE_IN_SERVICE;

        boolean hasDeregistered =
            ss.getState() == ServiceState.STATE_IN_SERVICE
            && newSS.getState() != ServiceState.STATE_IN_SERVICE;

        boolean hasGprsAttached =
                gprsState != ServiceState.STATE_IN_SERVICE
                && newGPRSState == ServiceState.STATE_IN_SERVICE;

        boolean hasGprsDetached =
                gprsState == ServiceState.STATE_IN_SERVICE
                && newGPRSState != ServiceState.STATE_IN_SERVICE;

        boolean hasNetworkTypeChanged = networkType != newNetworkType;

        boolean hasChanged = !newSS.equals(ss);

        boolean hasRoamingOn = !ss.getRoaming() && newSS.getRoaming();

        boolean hasRoamingOff = ss.getRoaming() && !newSS.getRoaming();

        boolean hasLocationChanged = !newCellLoc.equals(cellLoc);

        ServiceState tss;
        tss = ss;
        ss = newSS;
        newSS = tss;
        // clean slate for next time
        newSS.setStateOutOfService();

        GsmCellLocation tcl = cellLoc;
        cellLoc = newCellLoc;
        newCellLoc = tcl;

        gprsState = newGPRSState;
        networkType = newNetworkType;

        newSS.setStateOutOfService(); // clean slate for next time

        if (hasNetworkTypeChanged) {
            phone.setSystemProperty(PROPERTY_DATA_NETWORK_TYPE,
                    networkTypeToString(networkType));
        }

        if (hasRegistered) {
            Checkin.updateStats(phone.getContext().getContentResolver(),
                    Checkin.Stats.Tag.PHONE_GSM_REGISTERED, 1, 0.0);
            networkAttachedRegistrants.notifyRegistrants();
        }

        if (hasChanged) {
            String operatorNumeric;

            phone.setSystemProperty(PROPERTY_OPERATOR_ALPHA,
                ss.getOperatorAlphaLong());

            operatorNumeric = ss.getOperatorNumeric();
            phone.setSystemProperty(PROPERTY_OPERATOR_NUMERIC, operatorNumeric);

            if (operatorNumeric == null) {
                phone.setSystemProperty(PROPERTY_OPERATOR_ISO_COUNTRY, "");
            } else {
                String iso = "";
                try{
                    iso = MccTable.countryCodeForMcc(Integer.parseInt(
                            operatorNumeric.substring(0,3)));
                } catch ( NumberFormatException ex){
                    Log.w(LOG_TAG, "countryCodeForMcc error" + ex);
                } catch ( StringIndexOutOfBoundsException ex) {
                    Log.w(LOG_TAG, "countryCodeForMcc error" + ex);
                }

                phone.setSystemProperty(PROPERTY_OPERATOR_ISO_COUNTRY, iso);
                mGotCountryCode = true;

                if (mNeedFixZone) {
                    TimeZone zone = null;
                    // If the offset is (0, false) and the timezone property
                    // is set, use the timezone property rather than
                    // GMT.
                    String zoneName = SystemProperties.get(TIMEZONE_PROPERTY);
                    if ((mZoneOffset == 0) && (mZoneDst == false) &&
                        (zoneName != null) && (zoneName.length() > 0) &&
                        (Arrays.binarySearch(GMT_COUNTRY_CODES, iso) < 0)) {
                        zone = TimeZone.getDefault();
                        // For NITZ string without timezone,
                        // need adjust time to reflect default timezone setting
                        long tzOffset;
                        tzOffset = zone.getOffset(System.currentTimeMillis());
                        SystemClock.setCurrentTimeMillis(
                                System.currentTimeMillis() - tzOffset);
                    } else if (iso.equals("")){
                        // Country code not found.  This is likely a test network.
                        // Get a TimeZone based only on the NITZ parameters (best guess).
                        zone = getNitzTimeZone(mZoneOffset, mZoneDst, mZoneTime);
                    } else {
                        zone = TimeUtils.getTimeZone(mZoneOffset,
                            mZoneDst, mZoneTime, iso);
                    }

                    mNeedFixZone = false;

                    if (zone != null) {
                        Context context = phone.getContext();
                        if (getAutoTime()) {
                            AlarmManager alarm =
                            (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                            alarm.setTimeZone(zone.getID());
                        }
                        saveNitzTimeZone(zone.getID());
                    }
                }
            }

            phone.setSystemProperty(PROPERTY_OPERATOR_ISROAMING,
                ss.getRoaming() ? "true" : "false");
            phone.setSystemProperty(PROPERTY_OPERATOR_ISMANUAL,
                ss.getIsManualSelection() ? "true" : "false");

            updateSpnDisplay();
            phone.notifyServiceStateChanged(ss);
        }

        if (hasGprsAttached) {
            gprsAttachedRegistrants.notifyRegistrants();
        }

        if (hasGprsDetached) {
            gprsDetachedRegistrants.notifyRegistrants();
        }

        if (hasNetworkTypeChanged) {
            phone.notifyDataConnection(null);
        }

        if (hasRoamingOn) {
            roamingOnRegistrants.notifyRegistrants();
        }

        if (hasRoamingOff) {
            roamingOffRegistrants.notifyRegistrants();
        }

        if (hasLocationChanged) {
            phone.notifyLocationChanged();
        }
    }

    /**
     * Returns a TimeZone object based only on parameters from the NITZ string.
     */
    private TimeZone getNitzTimeZone(int offset, boolean dst, long when) {
        TimeZone guess = findTimeZone(offset, dst, when);
        if (guess == null) {
            // Couldn't find a proper timezone.  Perhaps the DST data is wrong.
            guess = findTimeZone(offset, !dst, when);
        }
        if (DBG) {
            Log.d(LOG_TAG, "getNitzTimeZone returning "
                    + (guess == null ? guess : guess.getID()));
        }
        return guess;
    }

    private TimeZone findTimeZone(int offset, boolean dst, long when) {
        int rawOffset = offset;
        if (dst) {
            rawOffset -= 3600000;
        }
        String[] zones = TimeZone.getAvailableIDs(rawOffset);
        TimeZone guess = null;
        Date d = new Date(when);
        for (String zone : zones) {
            TimeZone tz = TimeZone.getTimeZone(zone);
            if (tz.getOffset(when) == offset &&
                tz.inDaylightTime(d) == dst) {
                guess = tz;
                break;
            }
        }

        return guess;
    }

    private void
    queueNextSignalStrengthPoll() {
        if (dontPollSignalStrength || (cm.getRadioState().isCdma())) {
            // The radio is telling us about signal strength changes
            // we don't have to ask it
            return;
        }

        Message msg;

        msg = obtainMessage();
        msg.what = EVENT_POLL_SIGNAL_STRENGTH;

        long nextTime;

        // TODO Done't poll signal strength if screen is off
        sendMessageDelayed(msg, POLL_PERIOD_MILLIS);
    }

    /**
     *  send signal-strength-changed notification if rssi changed
     *  Called both for solicited and unsolicited signal stength updates
     */
    private void
    onSignalStrengthResult(AsyncResult ar) {
        int oldRSSI = rssi;

        if (ar.exception != null) {
            // 99 = unknown
            // most likely radio is resetting/disconnected
            rssi = 99;
        } else {
            int[] ints = (int[])ar.result;

            // bug 658816 seems to be a case where the result is 0-length
            if (ints.length != 0) {
                rssi = ints[0];
            } else {
                Log.e(LOG_TAG, "Bogus signal strength response");
                rssi = 99;
            }
        }

        if (rssi != oldRSSI) {
            try { // This takes care of delayed EVENT_POLL_SIGNAL_STRENGTH (scheduled after
                  // POLL_PERIOD_MILLIS) during Radio Technology Change)
                phone.notifySignalStrength();
           } catch (NullPointerException ex) {
                log("onSignalStrengthResult() Phone already destroyed: " + ex 
                        + "Signal Stranth not notified");
           }
        }
    }

    /** code is registration state 0-5 from TS 27.007 7.2 */
    private int
    regCodeToServiceState(int code) {
        switch (code) {
            case 0:
            case 2: // 2 is "searching"
            case 3: // 3 is "registration denied"
            case 4: // 4 is "unknown" no vaild in current baseband
                return ServiceState.STATE_OUT_OF_SERVICE;

            case 1:
                return ServiceState.STATE_IN_SERVICE;

            case 5:
                // in service, roam
                return ServiceState.STATE_IN_SERVICE;

            default:
                Log.w(LOG_TAG, "unexpected service state " + code);
                return ServiceState.STATE_OUT_OF_SERVICE;
        }
    }


    /**
     * code is registration state 0-5 from TS 27.007 7.2
     * returns true if registered roam, false otherwise
     */
    private boolean
    regCodeIsRoaming (int code) {
        // 5 is  "in service -- roam"
        return 5 == code;
    }

    /**
     * Set roaming state when gsmRoaming is true and, if operator mcc is the
     * same as sim mcc, ons is different from spn
     * @param gsmRoaming TS 27.007 7.2 CREG registered roaming
     * @param s ServiceState hold current ons
     * @return true for roaming state set
     */
    private
    boolean isRoamingBetweenOperators(boolean gsmRoaming, ServiceState s) {
        String spn = SystemProperties.get(PROPERTY_ICC_OPERATOR_ALPHA, "empty");

        String onsl = s.getOperatorAlphaLong();
        String onss = s.getOperatorAlphaShort();

        boolean equalsOnsl = onsl != null && spn.equals(onsl);
        boolean equalsOnss = onss != null && spn.equals(onss);

        String simNumeric = SystemProperties.get(PROPERTY_ICC_OPERATOR_NUMERIC, "");
        String  operatorNumeric = s.getOperatorNumeric();

        boolean equalsMcc = true;
        try {
            equalsMcc = simNumeric.substring(0, 3).
                    equals(operatorNumeric.substring(0, 3));
        } catch (Exception e){
        }

        return gsmRoaming && !(equalsMcc && (equalsOnsl || equalsOnss));
    }

    private static
    int twoDigitsAt(String s, int offset) {
        int a, b;

        a = Character.digit(s.charAt(offset), 10);
        b = Character.digit(s.charAt(offset+1), 10);

        if (a < 0 || b < 0) {

            throw new RuntimeException("invalid format");
        }

        return a*10 + b;
    }

    /**
     * @return The current GPRS state. IN_SERVICE is the same as "attached"
     * and OUT_OF_SERVICE is the same as detached.
     */
    /*package*/ int getCurrentGprsState() {
        return gprsState;
    }

    /**
     * @return true if phone is camping on a technology (eg UMTS)
     * that could support voice and data simultaniously.
     */
    boolean isConcurrentVoiceAndData() {
        return (networkType == DATA_ACCESS_UMTS);
    }

    /**
     * Provides the name of the algorithmic time zone for the specified
     * offset.  Taken from TimeZone.java.
     */
    private static String displayNameFor(int off) {
        off = off / 1000 / 60;

        char[] buf = new char[9];
        buf[0] = 'G';
        buf[1] = 'M';
        buf[2] = 'T';

        if (off < 0) {
            buf[3] = '-';
            off = -off;
        } else {
            buf[3] = '+';
        }

        int hours = off / 60;
        int minutes = off % 60;

        buf[4] = (char) ('0' + hours / 10);
        buf[5] = (char) ('0' + hours % 10);

        buf[6] = ':';

        buf[7] = (char) ('0' + minutes / 10);
        buf[8] = (char) ('0' + minutes % 10);

        return new String(buf);
    }

    /**
     * nitzReceiveTime is time_t that the NITZ time was posted
     */

    private
    void setTimeFromNITZString (String nitz, int nitzReceiveTime) {
        // "yy/mm/dd,hh:mm:ss(+/-)tz"
        // tz is in number of quarter-hours

        Log.i(LOG_TAG, "setTimeFromNITZString: " +
            nitz + "," + nitzReceiveTime);

        try {
            /* NITZ time (hour:min:sec) will be in UTC but it supplies the timezone
             * offset as well (which we won't worry about until later) */
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

            c.clear();
            c.set(Calendar.DST_OFFSET, 0);

            String[] nitzSubs = nitz.split("[/:,+-]");

            int year = 2000 + Integer.parseInt(nitzSubs[0]);
            c.set(Calendar.YEAR, year);

            // month is 0 based!
            int month = Integer.parseInt(nitzSubs[1]) - 1;
            c.set(Calendar.MONTH, month);

            int date = Integer.parseInt(nitzSubs[2]);
            c.set(Calendar.DATE, date);

            int hour = Integer.parseInt(nitzSubs[3]);
            c.set(Calendar.HOUR, hour);

            int minute = Integer.parseInt(nitzSubs[4]);
            c.set(Calendar.MINUTE, minute);

            int second = Integer.parseInt(nitzSubs[5]);
            c.set(Calendar.SECOND, second);

            boolean sign = (nitz.indexOf('-') == -1);

            int tzOffset = Integer.parseInt(nitzSubs[6]);

            int dst = (nitzSubs.length >= 8 ) ? Integer.parseInt(nitzSubs[7])
                                              : 0;

            // The zone offset received from NITZ is for current local time,
            // so DST correction is already applied.  Don't add it again.
            //
            // tzOffset += dst * 4;
            //
            // We could unapply it if we wanted the raw offset.

            tzOffset = (sign ? 1 : -1) * tzOffset * 15 * 60 * 1000;

            TimeZone    zone = null;

            // As a special extension, the Android emulator appends the name of
            // the host computer's timezone to the nitz string. this is zoneinfo
            // timezone name of the form Area!Location or Area!Location!SubLocation
            // so we need to convert the ! into /
            if (nitzSubs.length >= 9) {
                String  tzname = nitzSubs[8].replace('!','/');
                zone = TimeZone.getTimeZone( tzname );
            }

            String iso = SystemProperties.get(PROPERTY_OPERATOR_ISO_COUNTRY);

            if (zone == null) {

                if (mGotCountryCode) {
                    if (iso != null && iso.length() > 0) {
                        zone = TimeUtils.getTimeZone(tzOffset, dst != 0,
                                c.getTimeInMillis(),
                                iso);
                    } else {
                        // We don't have a valid iso country code.  This is
                        // most likely because we're on a test network that's
                        // using a bogus MCC (eg, "001"), so get a TimeZone
                        // based only on the NITZ parameters.
                        zone = getNitzTimeZone(tzOffset, (dst != 0), c.getTimeInMillis());
                    }
                }
            }

            if (zone == null) {
                // We got the time before the country, so we don't know
                // how to identify the DST rules yet.  Save the information
                // and hope to fix it up later.

                mNeedFixZone = true;
                mZoneOffset  = tzOffset;
                mZoneDst     = dst != 0;
                mZoneTime    = c.getTimeInMillis();
            }

            if (zone != null) {
                Context context = phone.getContext();
                if (getAutoTime()) {
                    AlarmManager alarm =
                            (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                    alarm.setTimeZone(zone.getID());
                }
                saveNitzTimeZone(zone.getID());
            }

            long millisSinceNitzReceived
                    = System.currentTimeMillis() - (nitzReceiveTime * 1000L);

            if (millisSinceNitzReceived < 0) {
                // Sanity check: something is wrong
                Log.i(LOG_TAG, "NITZ: not setting time, clock has rolled "
                                    + "backwards since NITZ time received, "
                                    + nitz);
                return;
            }

            if (millisSinceNitzReceived > (1000L * 1000L)) {
                // If the time is this far off, something is wrong
                Log.i(LOG_TAG, "NITZ: not setting time, more than 1000 seconds "
                                + " have elapsed since time received, "
                                + nitz);

                return;
            }

            // Note: with range checks above, cast to int is safe
            c.add(Calendar.MILLISECOND, (int)millisSinceNitzReceived);

            String ignore = SystemProperties.get("gsm.ignore-nitz");
            if (ignore != null && ignore.equals("yes")) {
                Log.i(LOG_TAG,
                      "Not setting clock because gsm.ignore-nitz is set");
                return;
            }

            if (getAutoTime()) {
                Log.i(LOG_TAG, "Setting time of day to " + c.getTime()
                    + " NITZ receive delay(ms): " + millisSinceNitzReceived
                    + " gained(ms): "
                    + (c.getTimeInMillis() - System.currentTimeMillis())
                    + " from " + nitz);

                SystemClock.setCurrentTimeMillis(c.getTimeInMillis());
            }
            SystemProperties.set("gsm.nitz.time", String.valueOf(c.getTimeInMillis()));
            saveNitzTime(c.getTimeInMillis());
        } catch (RuntimeException ex) {
            Log.e(LOG_TAG, "Parsing NITZ time " + nitz, ex);
        }
    }

    private boolean getAutoTime() {
        try {
            return Settings.System.getInt(phone.getContext().getContentResolver(),
                    Settings.System.AUTO_TIME) > 0;
        } catch (SettingNotFoundException snfe) {
            return true;
        }
    }

    private void saveNitzTimeZone(String zoneId) {
        mSavedTimeZone = zoneId;
        // Send out a sticky broadcast so the system can determine if
        // the timezone was set by the carrier...
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intent.putExtra("time-zone", zoneId);
        phone.getContext().sendStickyBroadcast(intent);
    }

    private void saveNitzTime(long time) {
        mSavedTime = time;
        mSavedAtTime = SystemClock.elapsedRealtime();
        // Send out a sticky broadcast so the system can determine if
        // the time was set by the carrier...
        Intent intent = new Intent(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intent.putExtra("time", time);
        phone.getContext().sendStickyBroadcast(intent);
    }

    private void revertToNitz() {
        if (Settings.System.getInt(phone.getContext().getContentResolver(),
                Settings.System.AUTO_TIME, 0) == 0) {
            return;
        }
        Log.d(LOG_TAG, "Reverting to NITZ: tz='" + mSavedTimeZone
                + "' mSavedTime=" + mSavedTime
                + " mSavedAtTime=" + mSavedAtTime);
        if (mSavedTimeZone != null && mSavedTime != 0 && mSavedAtTime != 0) {
            AlarmManager alarm =
                (AlarmManager) phone.getContext().getSystemService(Context.ALARM_SERVICE);
            alarm.setTimeZone(mSavedTimeZone);
            SystemClock.setCurrentTimeMillis(mSavedTime
                    + (SystemClock.elapsedRealtime() - mSavedAtTime));
        }
    }

    protected void log(String s) {
        Log.d(LOG_TAG, "[GsmServiceStateTracker] " + s);
    }
}
