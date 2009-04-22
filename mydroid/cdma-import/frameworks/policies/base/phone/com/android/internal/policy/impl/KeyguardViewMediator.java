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

package com.android.internal.policy.impl;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.StatusBarManager;
import static android.app.StatusBarManager.DISABLE_NONE;
import static android.app.StatusBarManager.DISABLE_EXPAND;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.LocalPowerManager;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Config;
import android.util.Log;
import android.util.EventLog;
import android.view.KeyEvent;
import android.view.WindowManagerImpl;
import android.view.WindowManagerPolicy;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.widget.LockPatternUtils;

/**
 * Mediates requests related to the keyguard.  This includes queries about the
 * state of the keyguard, power management events that effect whether the keyguard
 * should be shown or reset, callbacks to the phone window manager to notify
 * it of when the keyguard is showing, and events from the keyguard view itself
 * stating that the keyguard was succesfully unlocked.
 *
 * Note that the keyguard view is shown when the screen is off (as appropriate)
 * so that once the screen comes on, it will be ready immediately.
 *
 * Example queries about the keyguard:
 * - is {movement, key} one that should wake the keygaurd?
 * - is the keyguard showing?
 * - are input events restricted due to the state of the keyguard?
 *
 * Callbacks to the phone window manager:
 * - the keyguard is showing
 *
 * Example external events that translate to keyguard view changes:
 * - screen turned off -> reset the keyguard, and show it so it will be ready
 *   next time the screen turns on
 * - keyboard is slid open -> if the keyguard is not secure, hide it
 *
 * Events from the keyguard view:
 * - user succesfully unlocked keyguard -> hide keyguard view, and no longer
 *   restrict input events.
 *
 * Note: in addition to normal power managment events that effect the state of
 * whether the keyguard should be showing, external apps and services may request
 * that the keyguard be disabled via {@link #setKeyguardEnabled(boolean)}.  When
 * false, this will override all other conditions for turning on the keyguard.
 *
 * Threading and synchronization:
 * This class is created by the initialization routine of the {@link WindowManagerPolicy},
 * and runs on its thread.  The keyguard UI is created from that thread in the
 * constructor of this class.  The apis may be called from other threads, including the
 * {@link com.android.server.KeyInputQueue}'s and {@link android.view.WindowManager}'s.
 * Therefore, methods on this class are synchronized, and any action that is pointed
 * directly to the keyguard UI is posted to a {@link Handler} to ensure it is taken on the UI
 * thread of the keyguard.
 */
public class KeyguardViewMediator implements KeyguardViewCallback,
        KeyguardUpdateMonitor.ConfigurationChangeCallback, KeyguardUpdateMonitor.SimStateCallback {
    private final static boolean DEBUG = false && Config.LOGD;
    private final static boolean DBG_WAKE = DEBUG || true;

    private final static String TAG = "KeyguardViewMediator";

    private static final String DELAYED_KEYGUARD_ACTION = 
        "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD";

    // used for handler messages
    private static final int TIMEOUT = 1;
    private static final int SHOW = 2;
    private static final int HIDE = 3;
    private static final int RESET = 4;
    private static final int VERIFY_UNLOCK = 5;
    private static final int NOTIFY_SCREEN_OFF = 6;
    private static final int NOTIFY_SCREEN_ON = 7;
    private static final int WAKE_WHEN_READY = 8;
    private static final int KEYGUARD_DONE = 9;
    private static final int KEYGUARD_DONE_DRAWING = 10;
    
    /**
     * The default amount of time we stay awake (used for all key input)
     */
    protected static final int AWAKE_INTERVAL_DEFAULT_MS = 5000;


    /**
     * The default amount of time we stay awake (used for all key input) when
     * the keyboard is open
     */
    protected static final int AWAKE_INTERVAL_DEFAULT_KEYBOARD_OPEN_MS = 10000;

    /**
     * How long to wait after the screen turns off due to timeout before
     * turning on the keyguard (i.e, the user has this much time to turn
     * the screen back on without having to face the keyguard).
     */
    private static final int KEYGUARD_DELAY_MS = 0;

    /**
     * How long we'll wait for the {@link KeyguardViewCallback#keyguardDoneDrawing()}
     * callback before unblocking a call to {@link #setKeyguardEnabled(boolean)}
     * that is reenabling the keyguard.
     */
    private static final int KEYGUARD_DONE_DRAWING_TIMEOUT_MS = 2000;
    
    private Context mContext;
    private AlarmManager mAlarmManager;

    /** Low level access to the power manager for enableUserActivity.  Having this
     * requires that we run in the system process.  */
    LocalPowerManager mRealPowerManager;

    /** High level access to the power manager for WakeLocks */
    private PowerManager mPM;

    /**
     * Used to keep the device awake while the keyguard is showing, i.e for
     * calls to {@link #pokeWakelock()}
     */
    private PowerManager.WakeLock mWakeLock;

    /**
     * Does not turn on screen, held while a call to {@link KeyguardViewManager#wakeWhenReadyTq(int)}
     * is called to make sure the device doesn't sleep before it has a chance to poke
     * the wake lock.
     * @see #wakeWhenReadyLocked(int)
     */
    private PowerManager.WakeLock mWakeAndHandOff;

    /**
     * Used to disable / reenable status bar expansion.
     */
    private StatusBarManager mStatusBarManager;

    private KeyguardViewManager mKeyguardViewManager;

    // these are protected by synchronized (this)

    /**
     * External apps (like the phone app) can tell us to disable the keygaurd.
     */
    private boolean mExternallyEnabled = true;

    /**
     * Remember if an external call to {@link #setKeyguardEnabled} with value
     * false caused us to hide the keyguard, so that we need to reshow it once
     * the keygaurd is reenabled with another call with value true.
     */
    private boolean mNeedToReshowWhenReenabled = false;

    // cached value of whether we are showing (need to know this to quickly
    // answer whether the input should be restricted)
    private boolean mShowing = false;

    /**
     * Helps remember whether the screen has turned on since the last time
     * it turned off due to timeout. see {@link #onScreenTurnedOff(int)}
     */
    private int mDelayedShowingSequence;

    private int mWakelockSequence;

    private PhoneWindowManager mCallback;

    /**
     * If the user has disabled the keyguard, then requests to exit, this is
     * how we'll ultimately let them know whether it was successful.  We use this
     * var being non-null as an indicator that there is an in progress request.
     */
    private WindowManagerPolicy.OnKeyguardExitResult mExitSecureCallback;

    // the properties of the keyguard
    private KeyguardViewProperties mKeyguardViewProperties;

    private KeyguardUpdateMonitor mUpdateMonitor;

    private boolean mKeyboardOpen = false;

    /**
     * {@link #setKeyguardEnabled} waits on this condition when it reenables
     * the keyguard.
     */
    private boolean mWaitingUntilKeyguardVisible = false;

    public KeyguardViewMediator(Context context, PhoneWindowManager callback,
            LocalPowerManager powerManager) {
        mContext = context;

        mRealPowerManager = powerManager;
        mPM = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPM.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, 
                "keyguard");
        mWakeLock.setReferenceCounted(false);

        mWakeAndHandOff = mPM.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "keyguardWakeAndHandOff");
        mWakeAndHandOff.setReferenceCounted(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(DELAYED_KEYGUARD_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        context.registerReceiver(mBroadCastReceiver, filter);
        mAlarmManager = (AlarmManager) context
                .getSystemService(Context.ALARM_SERVICE);
        mCallback = callback;

        mUpdateMonitor = new KeyguardUpdateMonitor(context);

        mUpdateMonitor.registerConfigurationChangeCallback(this);
        mUpdateMonitor.registerSimStateCallback(this);

        mKeyguardViewProperties =
                new LockPatternKeyguardViewProperties(
                        new LockPatternUtils(mContext.getContentResolver()),
                        mUpdateMonitor);

        mKeyguardViewManager = new KeyguardViewManager(
                context, WindowManagerImpl.getDefault(), this,
                mKeyguardViewProperties, mUpdateMonitor);

    }

    /**
     * Let us know that the system is ready after startup.
     */
    public void onSystemReady() {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "onSystemReady");
            doKeyguard();
        }
    }

    /**
     * Called to let us know the screen was turned off.
     * @param why either {@link WindowManagerPolicy#OFF_BECAUSE_OF_USER} or
     *   {@link WindowManagerPolicy#OFF_BECAUSE_OF_TIMEOUT}.
     */
    public void onScreenTurnedOff(int why) {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "onScreenTurnedOff(" + why + ")");

            if (mExitSecureCallback != null) {
                if (DEBUG) Log.d(TAG, "pending exit secure callback cancelled");
                mExitSecureCallback.onKeyguardExitResult(false);
                mExitSecureCallback = null;
                if (!mExternallyEnabled) {
                    hideLocked();                    
                }
            } else if (mShowing) {
                notifyScreenOffLocked();
                resetStateLocked();
            } else if (why == WindowManagerPolicy.OFF_BECAUSE_OF_TIMEOUT) {
                // if the screen turned off because of timeout, set an alarm
                // to enable it a little bit later (i.e, give the user a chance
                // to turn the screen back on within a certain window without
                // having to unlock the screen)
                long when = SystemClock.elapsedRealtime() + KEYGUARD_DELAY_MS;
                Intent intent = new Intent(DELAYED_KEYGUARD_ACTION);
                intent.putExtra("seq", mDelayedShowingSequence);
                PendingIntent sender = PendingIntent.getBroadcast(mContext,
                        0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when,
                        sender);
                if (DEBUG) Log.d(TAG, "setting alarm to turn off keyguard, seq = " 
                                 + mDelayedShowingSequence);
            } else {
                doKeyguard();
            }
        }
    }

    /**
     * Let's us know the screen was turned on.
     */
    public void onScreenTurnedOn() {
        synchronized (this) {
            mDelayedShowingSequence++;
            if (DEBUG) Log.d(TAG, "onScreenTurnedOn, seq = " + mDelayedShowingSequence);
            notifyScreenOnLocked();
        }
    }

    /**
     * Same semantics as {@link WindowManagerPolicy#enableKeyguard}; provide
     * a way for external stuff to override normal keyguard behavior.  For instance
     * the phone app disables the keyguard when it receives incoming calls.
     */
    public void setKeyguardEnabled(boolean enabled) {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "setKeyguardEnabled(" + enabled + ")");


            mExternallyEnabled = enabled;

            if (!enabled && mShowing) {
                if (mExitSecureCallback != null) {
                    if (DEBUG) Log.d(TAG, "in process of verifyUnlock request, ignoring");
                    // we're in the process of handling a request to verify the user
                    // can get past the keyguard. ignore extraneous requests to disable / reenable
                    return;
                }

                // hiding keyguard that is showing, remember to reshow later
                if (DEBUG) Log.d(TAG, "remembering to reshow, hiding keyguard, "
                        + "disabling status bar expansion");
                mNeedToReshowWhenReenabled = true;
                setStatusBarExpandable(false);
                hideLocked();
            } else if (enabled && mNeedToReshowWhenReenabled) {
                // reenabled after previously hidden, reshow
                if (DEBUG) Log.d(TAG, "previously hidden, reshowing, reenabling "
                        + "status bar expansion");
                mNeedToReshowWhenReenabled = false;
                setStatusBarExpandable(true);

                if (mExitSecureCallback != null) {
                    if (DEBUG) Log.d(TAG, "onKeyguardExitResult(false), resetting");
                    mExitSecureCallback.onKeyguardExitResult(false);
                    mExitSecureCallback = null;
                    resetStateLocked();
                } else {
                    showLocked();

                    // block until we know the keygaurd is done drawing (and post a message
                    // to unblock us after a timeout so we don't risk blocking too long
                    // and causing an ANR).
                    mWaitingUntilKeyguardVisible = true;
                    mHandler.sendEmptyMessageDelayed(KEYGUARD_DONE_DRAWING, KEYGUARD_DONE_DRAWING_TIMEOUT_MS);
                    if (DEBUG) Log.d(TAG, "waiting until mWaitingUntilKeyguardVisible is false");
                    while (mWaitingUntilKeyguardVisible) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (DEBUG) Log.d(TAG, "done waiting for mWaitingUntilKeyguardVisible");
                }
            }
        }
    }

    /**
     * @see android.app.KeyguardManager#exitKeyguardSecurely
     */
    public void verifyUnlock(WindowManagerPolicy.OnKeyguardExitResult callback) {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "verifyUnlock");
            if (!mUpdateMonitor.isDeviceProvisioned()) {
                // don't allow this api when the device isn't provisioned
                if (DEBUG) Log.d(TAG, "ignoring because device isn't provisioned");
                callback.onKeyguardExitResult(false);
            } else if (mExternallyEnabled) {
                // this only applies when the user has externally disabled the
                // keyguard.  this is unexpected and means the user is not
                // using the api properly.
                Log.w(TAG, "verifyUnlock called when not externally disabled");
                callback.onKeyguardExitResult(false);
            } else if (mExitSecureCallback != null) {
                // already in progress with someone else
                callback.onKeyguardExitResult(false);
            } else {
                mExitSecureCallback = callback;
                verifyUnlockLocked();
            }
        }
    }


    private void setStatusBarExpandable(boolean isExpandable) {
        if (mStatusBarManager == null) {
            mStatusBarManager =
                    (StatusBarManager) mContext.getSystemService(Context.STATUS_BAR_SERVICE);
        }
        mStatusBarManager.disable(isExpandable ? DISABLE_NONE : DISABLE_EXPAND);
    }

    /**
     * Is the keyguard currently showing?
     */
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Given the state of the keyguard, is the input restricted?
     * Input is restricted when the keyguard is showing, or when the keyguard
     * was suppressed by an app that disabled the keyguard or we haven't been provisioned yet.
     */
    public boolean isInputRestricted() {
        return mShowing || mNeedToReshowWhenReenabled || !mUpdateMonitor.isDeviceProvisioned();
    }


    /**
     * Enable the keyguard if the settings are appropriate.
     */
    private void doKeyguard() {
        synchronized (this) {
            // if another app is disabling us, don't show
            if (!mExternallyEnabled) {
                if (DEBUG) Log.d(TAG, "doKeyguard: not showing because externally disabled");
                return;
            }

            // if the keyguard is already showing, don't bother
            if (mKeyguardViewManager.isShowing()) {
                if (DEBUG) Log.d(TAG, "doKeyguard: not showing because it is already showing");
                return;
            }
            
            // if the setup wizard hasn't run yet, don't show
            final boolean provisioned = mUpdateMonitor.isDeviceProvisioned();
            final IccCard.State state = mUpdateMonitor.getSimState();
            final boolean lockedOrMissing = state.isPinLocked() || (state == IccCard.State.ABSENT);
            if (!lockedOrMissing && !provisioned) {
                if (DEBUG) Log.d(TAG, "doKeyguard: not showing because device isn't provisioned"
                        + " and the sim is not locked or missing");
                return;
            }
            
            if (DEBUG) Log.d(TAG, "doKeyguard: showing the lock screen");
            showLocked();
        }
    }

    /**
     * Send message to keyguard telling it to reset its state.
     * @see #handleReset()
     */
    private void resetStateLocked() {
        if (DEBUG) Log.d(TAG, "resetStateLocked");
        Message msg = mHandler.obtainMessage(RESET);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to verify unlock
     * @see #handleVerifyUnlock()
     */
    private void verifyUnlockLocked() {
        if (DEBUG) Log.d(TAG, "verifyUnlockLocked");
        mHandler.sendEmptyMessage(VERIFY_UNLOCK);
    }


    /**
     * Send a message to keyguard telling it the screen just turned on.
     * @see #onScreenTurnedOff(int)
     * @see #handleNotifyScreenOff
     */
    private void notifyScreenOffLocked() {
        if (DEBUG) Log.d(TAG, "notifyScreenOffLocked");
        mHandler.sendEmptyMessage(NOTIFY_SCREEN_OFF);
    }

    /**
     * Send a message to keyguard telling it the screen just turned on.
     * @see #onScreenTurnedOn() 
     * @see #handleNotifyScreenOn
     */
    private void notifyScreenOnLocked() {
        if (DEBUG) Log.d(TAG, "notifyScreenOnLocked");
        mHandler.sendEmptyMessage(NOTIFY_SCREEN_ON);
    }

    /**
     * Send message to keyguard telling it about a wake key so it can adjust
     * its state accordingly and then poke the wake lock when it is ready.
     * @param keyCode The wake key.
     * @see #handleWakeWhenReady
     * @see #onWakeKeyWhenKeyguardShowingTq(int)
     */
    private void wakeWhenReadyLocked(int keyCode) {
        if (DBG_WAKE) Log.d(TAG, "wakeWhenReadyLocked(" + keyCode + ")");

        /**
         * acquire the handoff lock that will keep the cpu running.  this will
         * be released once the keyguard has set itself up and poked the other wakelock
         * in {@link #handleWakeWhenReady(int)}
         */
        mWakeAndHandOff.acquire();

        Message msg = mHandler.obtainMessage(WAKE_WHEN_READY, keyCode, 0);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to show itself
     * @see #handleShow()
     */
    private void showLocked() {
        if (DEBUG) Log.d(TAG, "showLocked");
        Message msg = mHandler.obtainMessage(SHOW);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to hide itself
     * @see #handleHide() 
     */
    private void hideLocked() {
        if (DEBUG) Log.d(TAG, "hideLocked");
        Message msg = mHandler.obtainMessage(HIDE);
        mHandler.sendMessage(msg);
    }

    /**
     * {@link KeyguardUpdateMonitor} callbacks.
     */

    /** {@inheritDoc} */
    public void onOrientationChange(boolean inPortrait) {

    }

    /** {@inheritDoc} */
    public void onKeyboardChange(boolean isKeyboardOpen) {
        mKeyboardOpen = isKeyboardOpen;

        if (mKeyboardOpen && !mKeyguardViewProperties.isSecure()
                && mKeyguardViewManager.isShowing()) {
            if (DEBUG) Log.d(TAG, "bypassing keyguard on sliding open of keyboard with non-secure keyguard");
            keyguardDone(true);
        }
    }

    /** {@inheritDoc} */
    public void onSimStateChanged(IccCard.State simState) {
        if (DEBUG) Log.d(TAG, "onSimStateChanged: " + simState);

        switch (simState) {
            case ABSENT:
                // only force lock screen in case of missing sim if user hasn't
                // gone through setup wizard
                if (!mUpdateMonitor.isDeviceProvisioned()) {
                    if (!isShowing()) {
                        if (DEBUG) Log.d(TAG, "INTENT_VALUE_ICC_ABSENT and keygaurd isn't showing, we need "
                             + "to show the keyguard since the device isn't provisioned yet.");
                        doKeyguard();
                    } else {
                        resetStateLocked();
                    }
                }
                break;
            case PIN_REQUIRED:
            case PUK_REQUIRED:
                if (!isShowing()) {
                    if (DEBUG) Log.d(TAG, "INTENT_VALUE_ICC_LOCKED and keygaurd isn't showing, we need "
                            + "to show the keyguard so the user can enter their sim pin");
                    doKeyguard();
                } else {
                    resetStateLocked();
                }

                break;
            case READY:
                if (isShowing()) {
                    resetStateLocked();
                }
                break;
        }
    }

    private BroadcastReceiver mBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(DELAYED_KEYGUARD_ACTION)) {

                int sequence = intent.getIntExtra("seq", 0);

                if (false) Log.d(TAG, "received DELAYED_KEYGUARD_ACTION with seq = "
                        + sequence + ", mDelayedShowingSequence = " + mDelayedShowingSequence);

                if (mDelayedShowingSequence == sequence) {
                    doKeyguard();
                }
            }
        }
    };


    /**
     * When a key is received when the screen is off and the keyguard is showing,
     * we need to decide whether to actually turn on the screen, and if so, tell
     * the keyguard to prepare itself and poke the wake lock when it is ready.
     *
     * The 'Tq' suffix is per the documentation in {@link WindowManagerPolicy}.
     * Be sure not to take any action that takes a long time; any significant
     * action should be posted to a handler.
     * 
     * @param keyCode The keycode of the key that woke the device
     * @return Whether we poked the wake lock (and turned the screen on)
     */
    public boolean onWakeKeyWhenKeyguardShowingTq(int keyCode) {
        if (DEBUG) Log.d(TAG, "onWakeKeyWhenKeyguardShowing(" + keyCode + ")");

        if (isWakeKeyWhenKeyguardShowing(keyCode)) {
            // give the keyguard view manager a chance to adjust the state of the
            // keyguard based on the key that woke the device before poking
            // the wake lock
            wakeWhenReadyLocked(keyCode);
            return true;
        } else {
            return false;
        }
    }

    private boolean isWakeKeyWhenKeyguardShowing(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_HEADSETHOOK:
            case KeyEvent.KEYCODE_CAMERA:
                return false;
        }
        return true;
    }

    /**
     * Callbacks from {@link KeyguardViewManager}.
     */

    /** {@inheritDoc} */
    public void pokeWakelock() {
        pokeWakelock(mKeyboardOpen ?
                AWAKE_INTERVAL_DEFAULT_KEYBOARD_OPEN_MS : AWAKE_INTERVAL_DEFAULT_MS);
    }

    /** {@inheritDoc} */
    public void pokeWakelock(int holdMs) {
        synchronized (this) {
            if (DBG_WAKE) Log.d(TAG, "pokeWakelock(" + holdMs + ")");
            mWakeLock.acquire();
            mHandler.removeMessages(TIMEOUT);
            mWakelockSequence++;
            Message msg = mHandler.obtainMessage(TIMEOUT, mWakelockSequence, 0);
            mHandler.sendMessageDelayed(msg, holdMs);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see #handleKeyguardDone 
     */
    public void keyguardDone(boolean authenticated) {
        synchronized (this) {
            EventLog.writeEvent(70000, 2);       
            if (DEBUG) Log.d(TAG, "keyguardDone(" + authenticated + ")");
            Message msg = mHandler.obtainMessage(KEYGUARD_DONE);
            mHandler.sendMessage(msg);

            if (authenticated) {
                mUpdateMonitor.clearFailedAttempts();                
            }

            if (mExitSecureCallback != null) {
                mExitSecureCallback.onKeyguardExitResult(authenticated);
                mExitSecureCallback = null;

                if (authenticated) {
                    // after succesfully exiting securely, no need to reshow
                    // the keyguard when they've released the lock
                    mExternallyEnabled = true;
                    mNeedToReshowWhenReenabled = false;
                    setStatusBarExpandable(true);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @see #handleKeyguardDoneDrawing
     */
    public void keyguardDoneDrawing() {
        mHandler.sendEmptyMessage(KEYGUARD_DONE_DRAWING);
    }

    /**
     * This handler will be associated with the policy thread, which will also
     * be the UI thread of the keyguard.  Since the apis of the policy, and therefore
     * this class, can be called by other threads, any action that directly
     * interacts with the keyguard ui should be posted to this handler, rather
     * than called directly.
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TIMEOUT:
                    handleTimeout(msg.arg1);
                    return ;
                case SHOW:
                    handleShow();
                    return ;
                case HIDE:
                    handleHide();
                    return ;
                case RESET:
                    handleReset();
                    return ;
                case VERIFY_UNLOCK:
                    handleVerifyUnlock();
                    return;
                case NOTIFY_SCREEN_OFF:
                    handleNotifyScreenOff();
                    return;
                case NOTIFY_SCREEN_ON:
                    handleNotifyScreenOn();
                    return;
                case WAKE_WHEN_READY:
                    handleWakeWhenReady(msg.arg1);
                    return;
                case KEYGUARD_DONE:
                    handleKeyguardDone();
                    return;
                case KEYGUARD_DONE_DRAWING:
                    handleKeyguardDoneDrawing();
            }
        }
    };

    /**
     * @see #keyguardDone
     * @see #KEYGUARD_DONE
     */
    private void handleKeyguardDone() {
        if (DEBUG) Log.d(TAG, "handleKeyguardDone");
        handleHide();
        mPM.userActivity(SystemClock.uptimeMillis(), true);
        mWakeLock.release();
    }

    /**
     * @see #keyguardDoneDrawing
     * @see #KEYGUARD_DONE_DRAWING
     */
    private void handleKeyguardDoneDrawing() {
        synchronized(this) {
            if (false) Log.d(TAG, "handleKeyguardDoneDrawing");
            if (mWaitingUntilKeyguardVisible) {
                if (DEBUG) Log.d(TAG, "handleKeyguardDoneDrawing: notifying mWaitingUntilKeyguardVisible");
                mWaitingUntilKeyguardVisible = false;
                notifyAll();

                // there will usually be two of these sent, one as a timeout, and one
                // as a result of the callback, so remove any remaining messages from
                // the queue
                mHandler.removeMessages(KEYGUARD_DONE_DRAWING);
            }
        }
    }

    /**
     * Handles the message sent by {@link #pokeWakelock}
     * @param seq used to determine if anything has changed since the message
     *   was sent.
     * @see #TIMEOUT
     */
    private void handleTimeout(int seq) {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleTimeout");
            if (seq == mWakelockSequence) {
                mWakeLock.release();
            }
        }
    }

    /**
     * Handle message sent by {@link #showLocked}.
     * @see #SHOW
     */
    private void handleShow() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleShow");
            // while we're showing, we control the wake state, so ask the power
            // manager not to honor request for userActivity.
            mRealPowerManager.enableUserActivity(false);

            mCallback.onKeyguardShow();
            mKeyguardViewManager.show();
            mShowing = true;
        }
    }

    /**
     * Handle message sent by {@link #hideLocked()}
     * @see #HIDE
     */
    private void handleHide() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleHide");
            // When we go away, tell the poewr manager to honor requests from userActivity.
            mRealPowerManager.enableUserActivity(true);

            mKeyguardViewManager.hide();
            mShowing = false;
        }
    }

    /**
     * Handle message sent by {@link #wakeWhenReadyLocked(int)}
     * @param keyCode The key that woke the device.
     * @see #WAKE_WHEN_READY
     */
    private void handleWakeWhenReady(int keyCode) {
        synchronized (KeyguardViewMediator.this) {
            if (DBG_WAKE) Log.d(TAG, "handleWakeWhenReady(" + keyCode + ")");

            // this should result in a call to 'poke wakelock' which will set a timeout
            // on releasing the wakelock
            mKeyguardViewManager.wakeWhenReadyTq(keyCode);

            /**
             * Now that the keyguard is ready and has poked the wake lock, we can
             * release the handoff wakelock
             */
            mWakeAndHandOff.release();

            if (!mWakeLock.isHeld()) {
                Log.w(TAG, "mKeyguardViewManager.wakeWhenReadyTq did not poke wake lock");
            }
        }
    }

    /**
     * Handle message sent by {@link #resetStateLocked()} 
     * @see #RESET
     */
    private void handleReset() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleReset");
            mKeyguardViewManager.reset();
        }
    }

    /**
     * Handle message sent by {@link #verifyUnlock}
     * @see #RESET
     */
    private void handleVerifyUnlock() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleVerifyUnlock");
            mKeyguardViewManager.verifyUnlock();
        }
    }

    /**
     * Handle message sent by {@link #notifyScreenOffLocked()}
     * @see #NOTIFY_SCREEN_OFF
     */
    private void handleNotifyScreenOff() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenOff");
            mKeyguardViewManager.onScreenTurnedOff();
        }
    }

    /**
     * Handle message sent by {@link #notifyScreenOnLocked()}
     * @see #NOTIFY_SCREEN_ON
     */
    private void handleNotifyScreenOn() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenOn");
            mKeyguardViewManager.onScreenTurnedOn();
        }
    }
}
