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

package com.android.internal.policy.impl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import static android.os.BatteryManager.BATTERY_STATUS_CHARGING;
import static android.os.BatteryManager.BATTERY_STATUS_FULL;
import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.provider.Telephony;
import static android.provider.Telephony.Intents.EXTRA_PLMN;
import static android.provider.Telephony.Intents.EXTRA_SHOW_PLMN;
import static android.provider.Telephony.Intents.EXTRA_SHOW_SPN;
import static android.provider.Telephony.Intents.EXTRA_SPN;
import static android.provider.Telephony.Intents.SPN_STRINGS_UPDATED_ACTION;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import android.util.Log;
import com.android.internal.R;
import com.google.android.collect.Lists;

import java.util.ArrayList;

/**
 * Watches for updates that may be interesting to the keyguard, and provides
 * the up to date information as well as a registration for callbacks that care
 * to be updated.
 *
 * Note: under time crunch, this has been extended to include some stuff that
 * doesn't really belong here.  see {@link #handleBatteryUpdate} where it shutdowns
 * the device, and {@link #getFailedAttempts()}, {@link #reportFailedAttempt()}
 * and {@link #clearFailedAttempts()}.  Maybe we should rename this 'KeyguardContext'...
 */
public class KeyguardUpdateMonitor {

    static private final String TAG = "KeyguardUpdateMonitor";
    static private final boolean DEBUG = false;

    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final Context mContext;

    private IccCard.State mSimState = IccCard.State.READY;
    private boolean mInPortrait;
    private boolean mKeyboardOpen;

    private boolean mDevicePluggedIn;
    
    private boolean mDeviceProvisioned;
    
    private int mBatteryLevel;

    private CharSequence mTelephonyPlmn;
    private CharSequence mTelephonySpn;

    private int mFailedAttempts = 0;

    private Handler mHandler;

    private ArrayList<ConfigurationChangeCallback> mConfigurationChangeCallbacks
            = Lists.newArrayList();
    private ArrayList<InfoCallback> mInfoCallbacks = Lists.newArrayList();
    private ArrayList<SimStateCallback> mSimStateCallbacks = Lists.newArrayList();
    private ContentObserver mContentObserver;
    

    // messages for the handler
    private static final int MSG_CONFIGURATION_CHANGED = 300;
    private static final int MSG_TIME_UPDATE = 301;
    private static final int MSG_BATTERY_UPDATE = 302;
    private static final int MSG_CARRIER_INFO_UPDATE = 303;
    private static final int MSG_SIM_STATE_CHANGE = 304;


    /**
     * When we receive a 
     * {@link com.android.internal.telephony.TelephonyIntents#ACTION_SIM_STATE_CHANGED} broadcast, 
     * and then pass a result via our handler to {@link KeyguardUpdateMonitor#handleSimStateChange},
     * we need a single object to pass to the handler.  This class helps decode
     * the intent and provide a {@link SimCard.State} result. 
     */
    private static class SimArgs {

        public final IccCard.State simState;

        private SimArgs(Intent intent) {
            if (!TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
            if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                this.simState = IccCard.State.ABSENT;
            } else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
                this.simState = IccCard.State.READY;
            } else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
                final String lockedReason = intent
                        .getStringExtra(IccCard.INTENT_KEY_LOCKED_REASON);
                if (IccCard.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                    this.simState = IccCard.State.PIN_REQUIRED;
                } else if (IccCard.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                    this.simState = IccCard.State.PUK_REQUIRED;
                } else {
                    this.simState = IccCard.State.UNKNOWN;
                }
            } else if (IccCard.INTENT_VALUE_LOCKED_NETWORK.equals(stateExtra)) {
                this.simState = IccCard.State.NETWORK_LOCKED;
            } else {
                this.simState = IccCard.State.UNKNOWN;
            }
        }

        public String toString() {
            return simState.toString();
        }
    }

    public KeyguardUpdateMonitor(Context context) {
        mContext = context;
        
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_CONFIGURATION_CHANGED:
                        handleConfigurationChange();
                        break;
                    case MSG_TIME_UPDATE:
                        handleTimeUpdate();
                        break;
                    case MSG_BATTERY_UPDATE:
                        handleBatteryUpdate(msg.arg1,  msg.arg2);
                        break;
                    case MSG_CARRIER_INFO_UPDATE:
                        handleCarrierInfoUpdate();
                        break;
                    case MSG_SIM_STATE_CHANGE:
                        handleSimStateChange((SimArgs) msg.obj);
                        break;
                }
            }
        };

        mDeviceProvisioned = Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.DEVICE_PROVISIONED, 0) != 0;
     
        // Since device can't be un-provisioned, we only need to register a content observer
        // to update mDeviceProvisioned when we are...
        if (!mDeviceProvisioned) {
            mContentObserver = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    mDeviceProvisioned = Settings.System.getInt(mContext.getContentResolver(), 
                            Settings.System.DEVICE_PROVISIONED, 0) != 0;
                    if (mDeviceProvisioned && mContentObserver != null) {
                        // We don't need the observer anymore...
                        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
                        mContentObserver = null;
                    }
                    if (DEBUG) Log.d(TAG, "DEVICE_PROVISIONED state = " + mDeviceProvisioned);
                }
            };
            
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DEVICE_PROVISIONED),
                    false, mContentObserver);
            
            // prevent a race condition between where we check the flag and where we register the
            // observer by grabbing the value once again...
            mDeviceProvisioned = Settings.System.getInt(mContext.getContentResolver(), 
                    Settings.System.DEVICE_PROVISIONED, 0) != 0;
        }
        
        mInPortrait = queryInPortrait();
        mKeyboardOpen = queryKeyboardOpen();

        // take a guess to start
        mSimState = IccCard.State.READY;
        mDevicePluggedIn = true;
        mBatteryLevel = 100;

        mTelephonyPlmn = getDefaultPlmn();

        // setup receiver
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(SPN_STRINGS_UPDATED_ACTION);
        context.registerReceiver(new BroadcastReceiver() {

            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG) Log.d(TAG, "received broadcast " + action);

                if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_CONFIGURATION_CHANGED));
                } else if (Intent.ACTION_TIME_TICK.equals(action)
                        || Intent.ACTION_TIME_CHANGED.equals(action)
                        || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_TIME_UPDATE));
                } else if (SPN_STRINGS_UPDATED_ACTION.equals(action)) {
                    mTelephonyPlmn = getTelephonyPlmnFrom(intent);
                    mTelephonySpn = getTelephonySpnFrom(intent);
                    mHandler.sendMessage(mHandler.obtainMessage(MSG_CARRIER_INFO_UPDATE));
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                    final int pluggedInStatus = intent
                            .getIntExtra("status", BATTERY_STATUS_UNKNOWN);
                    int batteryLevel = intent.getIntExtra("level", 0);
                    final Message msg = mHandler.obtainMessage(
                            MSG_BATTERY_UPDATE,
                            pluggedInStatus,
                            batteryLevel);
                    mHandler.sendMessage(msg);
                } else if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)){
                    mHandler.sendMessage(mHandler.obtainMessage(
                            MSG_SIM_STATE_CHANGE,
                            new SimArgs(intent)));
                }
            }
        }, filter);
    }

    /**
     * Handle {@link #MSG_CONFIGURATION_CHANGED}
     */
    private void handleConfigurationChange() {
        if (DEBUG) Log.d(TAG, "handleConfigurationChange");

        final boolean inPortrait = queryInPortrait();
        if (mInPortrait != inPortrait) {
            mInPortrait = inPortrait;
            for (int i = 0; i < mConfigurationChangeCallbacks.size(); i++) {
                mConfigurationChangeCallbacks.get(i).onOrientationChange(inPortrait);
            }
        }

        final boolean keyboardOpen = queryKeyboardOpen();
        if (mKeyboardOpen != keyboardOpen) {
            mKeyboardOpen = keyboardOpen;
            for (int i = 0; i < mConfigurationChangeCallbacks.size(); i++) {
                mConfigurationChangeCallbacks.get(i).onKeyboardChange(keyboardOpen);
            }
        }
    }

    /**
     * Handle {@link #MSG_TIME_UPDATE}
     */
    private void handleTimeUpdate() {
        if (DEBUG) Log.d(TAG, "handleTimeUpdate");
        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onTimeChanged();
        }
    }

    /**
     * Handle {@link #MSG_BATTERY_UPDATE}
     */
    private void handleBatteryUpdate(int pluggedInStatus, int batteryLevel) {
        if (DEBUG) Log.d(TAG, "handleBatteryUpdate");
        final boolean pluggedIn = isPluggedIn(pluggedInStatus);

        if (isBatteryUpdateInteresting(pluggedIn, batteryLevel)) {
            mBatteryLevel = batteryLevel;
            mDevicePluggedIn = pluggedIn;
            for (int i = 0; i < mInfoCallbacks.size(); i++) {
                mInfoCallbacks.get(i).onRefreshBatteryInfo(
                        shouldShowBatteryInfo(), pluggedIn, batteryLevel);
            }
        }

        // shut down gracefully if our battery is critically low and we are not powered
        if (batteryLevel == 0 &&
                pluggedInStatus != BATTERY_STATUS_CHARGING &&
                pluggedInStatus != BATTERY_STATUS_UNKNOWN) {
            ShutdownThread.shutdownAfterDisablingRadio(mContext, false);
        }
    }

    /**
     * Handle {@link #MSG_CARRIER_INFO_UPDATE}
     */
    private void handleCarrierInfoUpdate() {
        if (DEBUG) Log.d(TAG, "handleCarrierInfoUpdate: plmn = " + mTelephonyPlmn
            + ", spn = " + mTelephonySpn);

        for (int i = 0; i < mInfoCallbacks.size(); i++) {
            mInfoCallbacks.get(i).onRefreshCarrierInfo(mTelephonyPlmn, mTelephonySpn);
        }
    }

    /**
     * Handle {@link #MSG_SIM_STATE_CHANGE}
     */
    private void handleSimStateChange(SimArgs simArgs) {
        final IccCard.State state = simArgs.simState;

        if (DEBUG) {
            Log.d(TAG, "handleSimStateChange: intentValue = " + simArgs + " "
                    + "state resolved to " + state.toString());
        }

        if (state != IccCard.State.UNKNOWN && state != mSimState) {
            mSimState = state;
            for (int i = 0; i < mSimStateCallbacks.size(); i++) {
                mSimStateCallbacks.get(i).onSimStateChanged(state);
            }
        }
    }

    /**
     * @param status One of the statuses of {@link android.os.BatteryManager}
     * @return Whether the status maps to a status for being plugged in.
     */
    private boolean isPluggedIn(int status) {
        return status == BATTERY_STATUS_CHARGING || status == BATTERY_STATUS_FULL;
    }

    private boolean isBatteryUpdateInteresting(boolean pluggedIn, int batteryLevel) {
        // change in plug is always interesting
        if (mDevicePluggedIn != pluggedIn) {
            return true;
        }

        // change in battery level while plugged in
        if (pluggedIn && mBatteryLevel != batteryLevel) {
            return true;
        }

        if (!pluggedIn) {
            // not plugged in and going below threshold
            if (batteryLevel < LOW_BATTERY_THRESHOLD
                    && mBatteryLevel >= LOW_BATTERY_THRESHOLD) {
                return true;
            }
            // not plugged in and going above threshold (sounds impossible, but, meh...)
            if (mBatteryLevel < LOW_BATTERY_THRESHOLD
                    && batteryLevel >= LOW_BATTERY_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * What is the current orientation?
     */
    boolean queryInPortrait() {
        final Configuration configuration = mContext.getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * Is the keyboard currently open?
     */
    boolean queryKeyboardOpen() {
        final Configuration configuration = mContext.getResources().getConfiguration();

        return configuration.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO;
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonyPlmnFrom(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SHOW_PLMN, false)) {
            final String plmn = intent.getStringExtra(EXTRA_PLMN);
            if (plmn != null) {
                return plmn;
            } else {
                return getDefaultPlmn();
            }
        }
        return null;
    }

    /**
     * @return The default plmn (no service)
     */
    private CharSequence getDefaultPlmn() {
        return mContext.getResources().getText(
                        R.string.lockscreen_carrier_default);
    }

    /**
     * @param intent The intent with action {@link Telephony.Intents#SPN_STRINGS_UPDATED_ACTION}
     * @return The string to use for the plmn, or null if it should not be shown.
     */
    private CharSequence getTelephonySpnFrom(Intent intent) {
        if (intent.getBooleanExtra(EXTRA_SHOW_SPN, false)) {
            final String spn = intent.getStringExtra(EXTRA_SPN);
            if (spn != null) {
                return spn;
            }
        }
        return null;
    }

    /**
     * Remove the given observer from being registered from any of the kinds
     * of callbacks.
     * @param observer The observer to remove (an instance of {@link ConfigurationChangeCallback},
     *   {@link InfoCallback} or {@link SimStateCallback}
     */
    public void removeCallback(Object observer) {
        mConfigurationChangeCallbacks.remove(observer);
        mInfoCallbacks.remove(observer);
        mSimStateCallbacks.remove(observer);
    }

    /**
     * Callback for configuration changes.
     */
    interface ConfigurationChangeCallback {
        void onOrientationChange(boolean inPortrait);

        void onKeyboardChange(boolean isKeyboardOpen);
    }

    /**
     * Callback for general information releveant to lock screen.
     */
    interface InfoCallback {
        void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn, int batteryLevel);
        void onTimeChanged();

        /**
         * @param plmn The operator name of the registered network.  May be null if it shouldn't
         *   be displayed.
         * @param spn The service provider name.  May be null if it shouldn't be displayed.
         */
        void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn);
    }

    /**
     * Callback to notify of sim state change.
     */
    interface SimStateCallback {
        void onSimStateChanged(IccCard.State simState);
    }

    /**
     * Register to receive notifications about configuration changes.
     * @param callback The callback.
     */
    public void registerConfigurationChangeCallback(ConfigurationChangeCallback callback) {
        mConfigurationChangeCallbacks.add(callback);
    }

    /**
     * Register to receive notifications about general keyguard information
     * (see {@link InfoCallback}.
     * @param callback The callback.
     */
    public void registerInfoCallback(InfoCallback callback) {
        mInfoCallbacks.add(callback);
    }

    /**
     * Register to be notified of sim state changes.
     * @param callback The callback.
     */
    public void registerSimStateCallback(SimStateCallback callback) {
        mSimStateCallbacks.add(callback);
    }

    public IccCard.State getSimState() {
        return mSimState;
    }

    /**
     * Report that the user succesfully entered the sim pin so we
     * have the information earlier than waiting for the intent
     * broadcast from the telephony code.
     */
    public void reportSimPinUnlocked() {
        mSimState = IccCard.State.READY;
    }

    public boolean isInPortrait() {
        return mInPortrait;
    }

    public boolean isKeyboardOpen() {
        return mKeyboardOpen;
    }

    public boolean isDevicePluggedIn() {
        return mDevicePluggedIn;
    }

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public boolean shouldShowBatteryInfo() {
        return mDevicePluggedIn || mBatteryLevel < LOW_BATTERY_THRESHOLD;
    }

    public CharSequence getTelephonyPlmn() {
        return mTelephonyPlmn;
    }

    public CharSequence getTelephonySpn() {
        return mTelephonySpn;
    }

    /**
     * @return Whether the device is provisioned (whether they have gone through
     *   the setup wizard)
     */
    public boolean isDeviceProvisioned() {
        return mDeviceProvisioned;
    }

    public int getFailedAttempts() {
        return mFailedAttempts;
    }

    public void clearFailedAttempts() {
        mFailedAttempts = 0;
    }

    public void reportFailedAttempt() {
        mFailedAttempts++;
    }
}
