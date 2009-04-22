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

package com.android.phone;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.CallerInfoAsyncQuery;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.widget.SlidingDrawer;
import com.android.phone.PhoneUtils.VoiceMailNumberMissingException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Checkin;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Phone app "in call" screen.
 */
public class InCallScreen extends Activity
        implements View.OnClickListener, CallerInfoAsyncQuery.OnQueryCompleteListener {
    private static final String LOG_TAG = "PHONE/InCallScreen";

    // this debug flag is now attached to the "userdebuggable" builds
    // to keep useful logging available.
    private static final boolean DBG =
        (SystemProperties.getInt("ro.debuggable", 0) == 1);

    // Enable detailed logs of user actions while on the in-call screen.
    // TODO: For now this is totally disabled.  But for future user
    // research, we should change the PHONE_UI_EVENT_* events to use
    // android.util.EventLog rather than Checkin.logEvent, so that we can
    // select which of those events to upload on a tag-by-tag basis
    // (which means that we could actually ship devices with this logging
    // enabled.)
    /* package */ static final boolean ENABLE_PHONE_UI_EVENT_LOGGING = false;

    // Event values used with Checkin.Events.Tag.PHONE_UI events:
    /** The in-call UI became active */
    static final String PHONE_UI_EVENT_ENTER = "enter";
    /** User exited the in-call UI */
    static final String PHONE_UI_EVENT_EXIT = "exit";
    /** User clicked one of the touchable in-call buttons */
    static final String PHONE_UI_EVENT_BUTTON_CLICK = "button_click";
    /** User successfully slid the card to hang up */
    static final String PHONE_UI_EVENT_SLIDE_TO_HANGUP = "slide_hangup";
    /** User successfully slid the card to answer an incoming call */
    static final String PHONE_UI_EVENT_SLIDE_TO_ANSWER = "slide_answer";
    /** An attempted slide was unsuccessful (the user didn't slide far enough,
        or bailed out mid-slide */
    static final String PHONE_UI_EVENT_SLIDE_ABORTED = "slide_aborted";
    /** We rejected a "fat touch" event during a slide in progress.
        (We also append the touch event size to this value.) */
    static final String PHONE_UI_EVENT_REJECTED_FAT_TOUCH = "fat_touch";

    // Amount of time (in msec) that we display the "Call ended" state.
    // The "short" value is for calls ended by the local user, and the
    // "long" value is for calls ended by the remote caller.
    private static final int CALL_ENDED_SHORT_DELAY =  200;  // msec
    private static final int CALL_ENDED_LONG_DELAY = 2000;  // msec

    // Amount of time (in msec) that we keep the in-call menu onscreen
    // *after* the user changes the state of one of the toggle buttons.
    private static final int MENU_DISMISS_DELAY =  1000;  // msec

    // See CallTracker.MAX_CONNECTIONS_PER_CALL
    private static final int MAX_CALLERS_IN_CONFERENCE = 5;

    // Is the "sliding card" feature enabled?  (If false, we never
    // instantiate a SlidingCardManager, and we don't create a PopupWindow
    // (i.e. an extra surface) for the "call card"; instead the call card
    // is inflated directly into the in-call UI main frame.)
    private static final boolean ENABLE_SLIDING_CARD = false;

    // Message codes; see mHandler below.
    // Note message codes < 100 are reserved for the PhoneApp.
    private static final int PHONE_STATE_CHANGED = 101;
    private static final int PHONE_DISCONNECT = 102;
    private static final int EVENT_HEADSET_PLUG_STATE_CHANGED = 103;
    private static final int POST_ON_DIAL_CHARS = 104;
    private static final int WILD_PROMPT_CHAR_ENTERED = 105;
    private static final int ADD_VOICEMAIL_NUMBER = 106;
    private static final int DONT_ADD_VOICEMAIL_NUMBER = 107;
    private static final int DELAYED_CLEANUP_AFTER_DISCONNECT = 108;
    private static final int SUPP_SERVICE_FAILED = 110;
    private static final int DISMISS_MENU = 111;


    // High-level "modes" of the in-call UI.
    private enum InCallScreenMode {
        /**
         * Normal in-call UI elements visible, with sliding card on top.
         */
        NORMAL,
        /**
         * "Manage conference" UI is visible, totally replacing the
         * normal in-call UI.  Sliding card is not visible.
         */
        MANAGE_CONFERENCE,
        /**
         * Non-interactive UI state.  Sliding card is centered onscreen,
         * displaying information about the call that just ended.  Normal
         * in-call UI is totally hidden.
         */
        CALL_ENDED
    }
    private InCallScreenMode mInCallScreenMode;

    // Possible error conditions that can happen on startup.
    // These are returned as status codes from the various helper
    // functions we call from onCreate() and/or onResume().
    // See syncWithPhoneState() and checkIfOkToInitiateOutgoingCall() for details.
    private enum InCallInitStatus {
        SUCCESS,
        VOICEMAIL_NUMBER_MISSING,
        POWER_OFF,
        EMERGENCY_ONLY,
        PHONE_NOT_IN_USE,
        NO_PHONE_NUMBER_SUPPLIED,
        DIALED_MMI,
        CALL_FAILED
    }
    private InCallInitStatus mInCallInitialStatus;  // see onResume()

    private boolean mRegisteredForPhoneStates;

    private Phone mPhone;
    private Call mForegroundCall;
    private Call mBackgroundCall;
    private Call mRingingCall;

    private BluetoothHandsfree mBluetoothHandsfree;
    private BluetoothHeadset mBluetoothHeadset;  // valid only between onResume and onPause
    private boolean mBluetoothConnectionPending;
    private long mBluetoothConnectionRequestTime;

    // Main in-call UI ViewGroups
    private ViewGroup mMainFrame;
    private ViewGroup mInCallPanel;

    // Menu button hint below the "main frame"
    private TextView mMenuButtonHint;

    // In-call UI elements that aren't actually part of the
    // InCallScreen's main View hierarchy:
    private SlidingCardManager mSlidingCardManager;
    private CallCard mCallCard;
    private InCallMenu mInCallMenu;  // created lazily on first MENU keypress

    /** Dialer objects, including the model and the sliding drawer / dialer UI container*/
    private DTMFTwelveKeyDialer mDialer;
    private SlidingDrawer mDialerDrawer;

    // "Manage conference" UI elements
    private ViewGroup mManageConferencePanel;
    private Button mButtonManageConferenceDone;
    private ViewGroup[] mConferenceCallList;
    private int mNumCallersInConference;
    private Chronometer mConferenceTime;

    private EditText mWildPromptText;

    // Various dialogs we bring up (see dismissAllDialogs())
    // The MMI started dialog can actually be one of 2 items:
    //   1. An alert dialog if the MMI code is a normal MMI
    //   2. A progress dialog if the user requested a USSD
    private Dialog mMmiStartedDialog;
    private AlertDialog mMissingVoicemailDialog;
    private AlertDialog mGenericErrorDialog;
    private AlertDialog mSuppServiceFailureDialog;
    private AlertDialog mWaitPromptDialog;
    private AlertDialog mWildPromptDialog;

    // TODO: If the Activity class ever provides an "isDestroyed" flag itself,
    // we can remove this one.
    private boolean mIsDestroyed = false;

    // Flag indicating whether or not we should bring up the Call Log when
    // exiting the in-call UI due to the Phone becoming idle.  (This is
    // true if the most recently disconnected Call was initiated by the
    // user, or false if it was an incoming call.)
    // This flag is used by delayedCleanupAfterDisconnect(), and is set by
    // onDisconnect() (which is the only place that either posts a
    // DELAYED_CLEANUP_AFTER_DISCONNECT event *or* calls
    // delayedCleanupAfterDisconnect() directly.)
    private boolean mShowCallLogAfterDisconnect;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mIsDestroyed) {
                if (DBG) log("Handler: ignoring message " + msg + "; we're destroyed!");
                return;
            }

            switch (msg.what) {
                case SUPP_SERVICE_FAILED:
                    onSuppServiceFailed((AsyncResult) msg.obj);
                    break;

                case PHONE_STATE_CHANGED:
                    onPhoneStateChanged((AsyncResult) msg.obj);
                    break;

                case PHONE_DISCONNECT:
                    onDisconnect((AsyncResult) msg.obj);
                    break;

                case EVENT_HEADSET_PLUG_STATE_CHANGED:
                    // Update the in-call UI, since some UI elements (in
                    // particular the "Speaker" menu button) change state
                    // depending on whether a headset is plugged in.
                    // TODO: A full updateScreen() is overkill here, since
                    // the value of PhoneApp.isHeadsetPlugged() only affects a
                    // single menu item.  (But even a full updateScreen()
                    // is still pretty cheap, so let's keep this simple
                    // for now.)
                    if (msg.arg1 != 1 && !isBluetoothAudioConnected()){
                        // If the state is "not connected", restore the speaker state.
                        // We ONLY want to do this on the wired headset connect /
                        // disconnect events for now though, so we're only triggering
                        // on EVENT_HEADSET_PLUG_STATE_CHANGED.
                        PhoneUtils.restoreSpeakerMode(getApplicationContext());
                    }
                    updateScreen();
                    break;

                case PhoneApp.MMI_INITIATE:
                    onMMIInitiate((AsyncResult) msg.obj);
                    break;

                case PhoneApp.MMI_CANCEL:
                    onMMICancel();
                    break;

                // handle the mmi complete message.
                // since the message display class has been replaced with
                // a system dialog in PhoneUtils.displayMMIComplete(), we
                // should finish the activity here to close the window.
                case PhoneApp.MMI_COMPLETE:
                    // Check the code to see if the request is ready to
                    // finish, this includes any MMI state that is not
                    // PENDING.
                    MmiCode mmiCode = (MmiCode) ((AsyncResult) msg.obj).result;
                    if (mmiCode.getState() != MmiCode.State.PENDING) {
                        finish();
                    }
                    break;

                case POST_ON_DIAL_CHARS:
                    handlePostOnDialChars((AsyncResult) msg.obj, (char) msg.arg1);
                    break;

                case ADD_VOICEMAIL_NUMBER:
                    addVoiceMailNumberPanel();
                    break;

                case DONT_ADD_VOICEMAIL_NUMBER:
                    dontAddVoiceMailNumber();
                    break;

                case DELAYED_CLEANUP_AFTER_DISCONNECT:
                    delayedCleanupAfterDisconnect();
                    break;

                case DISMISS_MENU:
                    // dismissMenu() has no effect if the menu is already closed.
                    dismissMenu(true);  // dismissImmediate = true
                    break;
            }
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                    // Listen for ACTION_HEADSET_PLUG broadcasts so that we
                    // can update the onscreen UI when the headset state changes.
                    // if (DBG) log("mReceiver: ACTION_HEADSET_PLUG");
                    // if (DBG) log("==> intent: " + intent);
                    // if (DBG) log("    state: " + intent.getIntExtra("state", 0));
                    // if (DBG) log("    name: " + intent.getStringExtra("name"));
                    // send the event and add the state as an argument.
                    Message message = Message.obtain(mHandler, EVENT_HEADSET_PLUG_STATE_CHANGED,
                            intent.getIntExtra("state", 0), 0);
                    mHandler.sendMessage(message);
                }
            }
        };


    @Override
    protected void onCreate(Bundle icicle) {
        if (DBG) log("onCreate()...  this = " + this);

        Profiler.callScreenOnCreate();

        super.onCreate(icicle);

        PhoneApp app = PhoneApp.getInstance();

        setPhone(app.phone);  // Sets mPhone and mForegroundCall/mBackgroundCall/mRingingCall

        // allow the call notifier to handle the disconnect event for us
        // while we are setting up.  This is a catch-all in case the
        // handler used in registerForDisconnect does NOT get the
        // disconnect signal in time.
        app.notifier.setCallScreen(this);

        mBluetoothHandsfree = app.getBluetoothHandsfree();
        if (DBG) log("- mBluetoothHandsfree: " + mBluetoothHandsfree);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Inflate everything in incall_screen.xml and add it to the screen.
        setContentView(R.layout.incall_screen);
        mDialerDrawer = (SlidingDrawer) findViewById(R.id.dialer_container);

        initInCallScreen();

        registerForPhoneStates();

        // No need to change wake state here; that happens in onResume() when we
        // are actually displayed.

        // Handle the Intent we were launched with, but only if this is the
        // the very first time we're being launched (ie. NOT if we're being
        // re-initialized after previously being shut down.)
        // Once we're up and running, any future Intents we need
        // to handle will come in via the onNewIntent() method.
        if (icicle == null) {
            if (DBG) log("onCreate(): this is our very first launch, checking intent...");

            // Stash the result code from internalResolveIntent() in the
            // mInCallInitialStatus field.  If it's an error code, we'll
            // handle it in onResume().
            mInCallInitialStatus = internalResolveIntent(getIntent());
            if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
                Log.w(LOG_TAG, "onCreate: status " + mInCallInitialStatus
                      + " from internalResolveIntent()");
                // See onResume() for the actual error handling.
            }
        } else {
            mInCallInitialStatus = InCallInitStatus.SUCCESS;
        }

        // Create the dtmf dialer.
        mDialer = new DTMFTwelveKeyDialer(this);

        // When in landscape mode, the user can enter dtmf tones
        // at any time.  We need to make sure the DTMFDialer is
        // setup correctly.
        if (mDialer != null && ConfigurationHelper.isLandscape()) {
            mDialer.startDialerSession();
            if (DBG) log("Dialer initialized (in landscape mode).");
        }

        Profiler.callScreenCreated();
    }

    /**
     * Sets the Phone object used internally by the InCallScreen.
     *
     * In normal operation this is called from onCreate(), and the
     * passed-in Phone object comes from the PhoneApp.
     * For testing, test classes can use this method to
     * inject a test Phone instance.
     */
    /* package */ void setPhone(Phone phone) {
        mPhone = phone;
        // Hang onto the three Call objects too; they're singletons that
        // are constant (and never null) for the life of the Phone.
        mForegroundCall = mPhone.getForegroundCall();
        mBackgroundCall = mPhone.getBackgroundCall();
        mRingingCall = mPhone.getRingingCall();

        if (mSlidingCardManager != null) mSlidingCardManager.setPhone(phone);
    }

    @Override
    protected void onResume() {
        if (DBG) log("onResume()...");
        super.onResume();

        // Disable the keyguard the entire time the InCallScreen is
        // active.  (This is necessary only for the case of receiving an
        // incoming call while the device is locked; we need to disable
        // the keyguard so you can answer the call and use the in-call UI,
        // but we always re-enable the keyguard as soon as you leave this
        // screen (see onPause().))
        PhoneApp.getInstance().disableKeyguard();

        // Disable the status bar "window shade" the entire time we're on
        // the in-call screen.
        NotificationMgr.getDefault().getStatusBarMgr().enableExpandedView(false);

        // Register for headset plug events (so we can update the onscreen
        // UI when the headset state changes.)
        registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));

        // Check for any failures that happened during onCreate().
        if (DBG) log("- onResume: mInCallInitialStatus = " + mInCallInitialStatus);
        if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
            if (DBG) log("- onResume: failure during startup: " + mInCallInitialStatus);

            // Don't bring up the regular Phone UI!  Instead bring up
            // something more specific to let the user deal with the
            // problem.
            handleStartupError(mInCallInitialStatus);
            mInCallInitialStatus = InCallInitStatus.SUCCESS;
            return;
        }

        // Set the volume control handler while we are in the foreground.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        takeKeyEvents(true);

        // Always start off in NORMAL mode.
        setInCallScreenMode(InCallScreenMode.NORMAL);

        // Before checking the state of the phone, clean up any
        // connections in the DISCONNECTED state.
        // (The DISCONNECTED state is used only to drive the "call ended"
        // UI; it's totally useless when *entering* the InCallScreen.)
        mPhone.clearDisconnected();

        InCallInitStatus status = syncWithPhoneState();
        if (status != InCallInitStatus.SUCCESS) {
            if (DBG) log("- syncWithPhoneState failed! status = " + status);
            // Couldn't update the UI, presumably because the phone is
            // totally not in use.  We shouldn't even be in the in-call UI
            // in the first place, so bail out:
            if (DBG) log("- onResume: bailing out...");
            finish();
            return;
        }

        if (ENABLE_PHONE_UI_EVENT_LOGGING) {
            // InCallScreen is now active.
            Checkin.logEvent(getContentResolver(),
                             Checkin.Events.Tag.PHONE_UI,
                             PHONE_UI_EVENT_ENTER);
        }

        // Hang on to a BluetoothHeadset instance while we're in the foreground.
        if (mBluetoothHandsfree != null) {
            // The PhoneApp only creates a BluetoothHandsfree instance in the
            // first place if getSystemService(Context.BLUETOOTH_SERVICE)
            // succeeds.  So at this point we know the device is BT-capable.
            mBluetoothHeadset = new BluetoothHeadset(this);
            if (DBG) log("- Got BluetoothHeadset: " + mBluetoothHeadset);
        }

        // Tell the PhoneApp we're now the foreground activity.
        PhoneApp app = PhoneApp.getInstance();
        app.setActiveInCallScreenInstance(this);

        // Now we can handle the finish() call ourselves instead of relying on
        // the call notifier to do so.
        app.notifier.setCallScreen(null);

        // making sure we update the poke lock and wake lock when we move to
        // the foreground.
        updateWakeState();

        // Restore the mute state if the last mute state change was NOT
        // done by the user.
        if (app.getRestoreMuteOnInCallResume()) {
            PhoneUtils.restoreMuteState(mPhone);
            app.setRestoreMuteOnInCallResume(false);
        }

        Profiler.profileViewCreate(getWindow(), InCallScreen.class.getName());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (DBG) log("onSaveInstanceState()...");
        super.onSaveInstanceState(outState);

        // TODO: Save any state of the UI that needs to persist across
        // configuration changes (ie. switching between portrait and
        // landscape.)
    }

    @Override
    protected void onPause() {
        if (DBG) log("onPause()...");
        super.onPause();

        // make sure the chronometer is stopped when we move away from
        // the foreground.
        if (mConferenceTime != null) {
            mConferenceTime.stop();
        }

        // If the device is put to sleep as the phone call is ending,
        // we may see cases where the DELAYED_CLEANUP_AFTER_DISCONNECT
        // event gets handled AFTER the device goes to sleep and wakes
        // up again.

        // This is because it is possible for a sleep command
        // (executed with the End Call key) to come during the 2
        // seconds that the "Call Ended" screen is up.  Sleep then
        // pauses the device (including the cleanup event) and
        // resumes the event when it wakes up.

        // To fix this, we introduce a bit of code that pushes the UI
        // to the background if we pause and see a request to
        // DELAYED_CLEANUP_AFTER_DISCONNECT.

        // Note: We can try to finish directly, by:
        //  1. Removing the DELAYED_CLEANUP_AFTER_DISCONNECT messages
        //  2. Calling delayedCleanupAfterDisconnect directly

        // However, doing so can cause problems between the phone
        // app and the keyguard - the keyguard is trying to sleep at
        // the same time that the phone state is changing.  This can
        // end up causing the sleep request to be ignored.
        if (mHandler.hasMessages(DELAYED_CLEANUP_AFTER_DISCONNECT)) {
            if (DBG) log("DELAYED_CLEANUP_AFTER_DISCONNECT detected, moving UI to background.");
            moveTaskToBack(false);
        }

        if (ENABLE_PHONE_UI_EVENT_LOGGING) {
            // InCallScreen is no longer active.
            Checkin.logEvent(getContentResolver(),
                             Checkin.Events.Tag.PHONE_UI,
                             PHONE_UI_EVENT_EXIT);
        }

        // Clean up the menu, in case we get paused while the menu is up
        // for some reason.
        dismissMenu(true);  // dismiss immediately

        // Dismiss any dialogs we may have brought up, just to be 100%
        // sure they won't still be around when we get back here.
        dismissAllDialogs();

        // Re-enable the status bar (which we disabled in onResume().)
        NotificationMgr.getDefault().getStatusBarMgr().enableExpandedView(true);

        // Unregister for headset plug events.  (These events only affect
        // the in-call menu, so we only care about them while we're in the
        // foreground.)
        unregisterReceiver(mReceiver);

        // Release the BluetoothHeadset instance.
        if (mBluetoothHeadset != null) {
            mBluetoothHeadset.close();
            mBluetoothHeadset = null;
        }

        // The keyguard was disabled the entire time the InCallScreen was
        // active (see onResume()).  Re-enable it now.
        PhoneApp.getInstance().reenableKeyguard();

        // Tell the PhoneApp we're no longer the foreground activity.
        PhoneApp.getInstance().setActiveInCallScreenInstance(null);

        // making sure we revert the poke lock and wake lock when we move to
        // the background.
        updateWakeState();
    }

    @Override
    protected void onStop() {
        if (DBG) log("onStop()...");
        super.onStop();

        stopTimer();

        Phone.State state = mPhone.getState();
        if (DBG) log("onStop: state = " + state);

        // Take down the "sliding card" PopupWindow.  (It's definitely
        // safe to do this here in onStop(), which is called when we are
        // no longer visible to the user.)
        if (mSlidingCardManager != null) mSlidingCardManager.dismissPopup();

        if (state == Phone.State.IDLE) {
            // we don't want the call screen to remain in the activity history
            // if there are not active or ringing calls.
            if (DBG) log("- onStop: calling finish() to clear activity history...");
            finish();
        } else {
            if (DBG) log("onStop: keep call screen in history");
            PhoneApp.getInstance().notifier.setCallScreen(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (DBG) log("onDestroy()...");
        super.onDestroy();

        // Set the magic flag that tells us NOT to handle any handler
        // messages that come in asynchronously after we get destroyed.
        mIsDestroyed = true;

        // Also clear out the SlidingCardManager's and InCallMenu's
        // references to us (which lets them know we've been destroyed).
        if (mSlidingCardManager != null) mSlidingCardManager.clearInCallScreenReference();
        if (mInCallMenu != null) {
            mInCallMenu.clearInCallScreenReference();
        }

        // Make sure that the dialer session is over and done with.
        // 1. In Landscape mode, we stop the tone generator directly
        // 2. In portrait mode, the tone generator is stopped
        // whenever the dialer is closed by the framework, (either
        // from a user request or calling close on the drawer
        // directly), so all we have to do is to make sure the
        // dialer is closed {see DTMFTwelvKeyDialer.onDialerClose}
        // (it is ok to call this even if the dialer is not open).
        if (mDialer != null) {
            if (ConfigurationHelper.isLandscape()) {
                mDialer.stopDialerSession();
            } else {
                // make sure the dialer drawer is closed.
                mDialer.closeDialer(false);
            }
            mDialer.clearInCallScreenReference();
            mDialer = null;
        }

        unregisterForPhoneStates();
        // No need to change wake state here; that happens in onPause() when we
        // are moving out of the foreground.
    }

    @Override
    public void finish() {
        if (DBG) log("finish()...");
        super.finish();

        PhoneApp.getInstance().notifier.setCallScreen(null);
    }

    private void registerForPhoneStates() {
        if (!mRegisteredForPhoneStates) {
            mPhone.registerForPhoneStateChanged(mHandler, PHONE_STATE_CHANGED, null);
            mPhone.registerForDisconnect(mHandler, PHONE_DISCONNECT, null);
            mPhone.registerForMmiInitiate(mHandler, PhoneApp.MMI_INITIATE, null);

            // register for the MMI complete message.  Upon completion,
            // PhoneUtils will bring up a system dialog instead of the
            // message display class in PhoneUtils.displayMMIComplete().
            // We'll listen for that message too, so that we can finish
            // the activity at the same time.
            mPhone.registerForMmiComplete(mHandler, PhoneApp.MMI_COMPLETE, null);

            mPhone.setOnPostDialCharacter(mHandler, POST_ON_DIAL_CHARS, null);
            mPhone.registerForSuppServiceFailed(mHandler, SUPP_SERVICE_FAILED, null);
            mRegisteredForPhoneStates = true;
        }
    }

    private void unregisterForPhoneStates() {
        mPhone.unregisterForPhoneStateChanged(mHandler);
        mPhone.unregisterForDisconnect(mHandler);
        mPhone.unregisterForMmiInitiate(mHandler);
        mPhone.setOnPostDialCharacter(null, POST_ON_DIAL_CHARS, null);
        mRegisteredForPhoneStates = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DBG) log("onNewIntent: intent=" + intent);

        // We're being re-launched with a new Intent, for example getting
        // a new ACTION_CALL while we were already using the other line.
        // Activities are always paused before receiving a new intent, so
        // we can count on our onResume() method being called next.
        //
        // So just like in onCreate(), we stash the result code from
        // internalResolveIntent() in the mInCallInitialStatus field.
        // If it's an error code, we'll handle it in onResume().
        mInCallInitialStatus = internalResolveIntent(intent);
        if (mInCallInitialStatus != InCallInitStatus.SUCCESS) {
            Log.w(LOG_TAG, "onNewIntent: status " + mInCallInitialStatus
                  + " from internalResolveIntent()");
            // See onResume() for the actual error handling.
        }
    }

    private InCallInitStatus internalResolveIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return InCallInitStatus.SUCCESS;
        }

        String action = intent.getAction();
        if (DBG) log("internalResolveIntent: action=" + action);

        // The calls to setRestoreMuteOnInCallResume() inform the phone
        // that we're dealing with new connections (either a placing an
        // outgoing call or answering an incoming one, and NOT handling
        // an aborted "Add Call" request), so we should let the mute state
        // be handled by the PhoneUtils phone state change handler.
        PhoneApp app = PhoneApp.getInstance();
        if (action.equals(intent.ACTION_ANSWER)) {
            internalAnswerCall();
            app.setRestoreMuteOnInCallResume(false);
            return InCallInitStatus.SUCCESS;
        } else if (action.equals(Intent.ACTION_CALL)
                || action.equals(Intent.ACTION_CALL_EMERGENCY)) {
            app.setRestoreMuteOnInCallResume(false);
            return placeCall(intent);
        }
        return InCallInitStatus.SUCCESS;
    }

    private void stopTimer() {
        if (mCallCard != null) mCallCard.stopTimer();
    }

    private void initInCallScreen() {
        if (DBG) log("initInCallScreen()...");

        // Have the WindowManager filter out touch events that are "too fat".
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

        mMainFrame = (ViewGroup) findViewById(R.id.mainFrame);
        mInCallPanel = (ViewGroup) findViewById(R.id.inCallPanel);

        ConfigurationHelper.initConfiguration(getResources().getConfiguration());

        if (ENABLE_SLIDING_CARD) {
            // Create the SlidingCardManager, which includes a CallCard instance.
            mSlidingCardManager = new SlidingCardManager();
            mSlidingCardManager.init(mPhone, this, mMainFrame);
            mCallCard = mSlidingCardManager.getCallCard();
        } else {
            // We're not using a SlidingCardManager, so manually create a
            // CallCard and add it to our View hierarchy.
            // (Note R.layout.call_card_popup is the exact same layout
            // resource that we inflate into the PopupWindow when using
            // the SlidingCardManager.)
            View callCardLayout = getLayoutInflater().inflate(
                    R.layout.call_card_popup,
                    mInCallPanel);
            mCallCard = (CallCard) callCardLayout.findViewById(R.id.callCard);
            if (DBG) log("  - mCallCard = " + mCallCard);
            mCallCard.reset();
            mCallCard.setSlidingCardManager(null);
        }

        // Menu Button hint
        mMenuButtonHint = (TextView) findViewById(R.id.menuButtonHint);

        // Add a WindowAttachNotifierView to our main hierarchy, purely so
        // we'll find out when it's OK to bring up the CallCard
        // PopupWindow.  (See the WindowAttachNotifierView class for more info.)
        if (mSlidingCardManager != null) {
            SlidingCardManager.WindowAttachNotifierView wanv =
                    new SlidingCardManager.WindowAttachNotifierView(this);
            wanv.setSlidingCardManager(mSlidingCardManager);
            wanv.setVisibility(View.GONE);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(0, 0);
            mMainFrame.addView(wanv, lp);
        }

        // If sliding is enabled, draw the "slide track" background image
        // underneath the sliding card.
        if (mSlidingCardManager != null) {
            mMainFrame.setBackgroundResource(R.drawable.background_frame_portrait);
            // (See ConfigurationHelper for the landscape-mode equivalent.)
        }

        // Make any final updates to our View hierarchy that depend on the
        // current configuration.
        ConfigurationHelper.applyConfigurationToLayout(this);
    }

    /**
     * Returns true if the phone is "in use", meaning that at least one line
     * is active (ie. off hook or ringing or dialing).  Conversely, a return
     * value of false means there's currently no phone activity at all.
     */
    private boolean phoneIsInUse() {
        return mPhone.getState() != Phone.State.IDLE;
    }

    private boolean handleDialerKeyDown(int keyCode, KeyEvent event) {
        if (DBG) log("handleDialerKeyDown: keyCode " + keyCode + ", event " + event + "...");

        // As soon as the user starts typing valid dialable keys on the
        // keyboard (presumably to type DTMF tones) we start passing the
        // key events to the DTMFDialer's onDialerKeyDown.  We do so
        // only if the okToDialDTMFTones() conditions pass.
        if (mDialer != null && okToDialDTMFTones()) {
            return mDialer.onDialerKeyDown(event);
        }

        return false;
    }

    /**
     * Handles a DOWN keypress on the BACK key.
     */
    private boolean handleBackKey() {
        if (DBG) log("handleBackKey()...");

        // If the user presses BACK while an incoming call is ringing, we
        // silence the ringer (just like with VOLUME_UP or VOLUME_DOWN)
        // *without* sending the call to voicemail (like ENDCALL would),
        // *and* also bail out of the "Incoming call" UI.
        CallNotifier notifier = PhoneApp.getInstance().notifier;
        if (notifier.isRinging()) {
            PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_IDLE);
            if (DBG) log("BACK key: silence ringer");
            notifier.silenceRinger();
            if (mBluetoothHandsfree != null) {
                mBluetoothHandsfree.ignoreRing();
            }
            // Don't consume the key; instead let the BACK event *also*
            // get handled normally by the framework (which presumably
            // will cause us to exit out of this activity.)
            return false;
        }

        // BACK is also used to exit out of any "special modes" of the
        // in-call UI:

        if (mDialer != null && mDialer.isOpened()) {
            mDialer.closeDialer(true);  // do the "closing" animation
            return true;
        }

        if (mInCallScreenMode == InCallScreenMode.MANAGE_CONFERENCE) {
            // Hide the Manage Conference panel, return to NORMAL mode.
            setInCallScreenMode(InCallScreenMode.NORMAL);
            return true;
        }

        return false;
    }

    /**
     * Handles the green CALL key while in-call.
     * @return true if we consumed the event.
     */
    private boolean handleCallKey() {
        final boolean hasRingingCall = !mRingingCall.isIdle();
        final boolean hasActiveCall = !mForegroundCall.isIdle();
        final boolean hasHoldingCall = !mBackgroundCall.isIdle(); 

        if (mPhone.getPhoneName() == "CDMA") {
            // The green CALL button means either "Answer", "Swap calls/On Hold", or
            // "Add to 3WC", depending on the current state of the Phone.

            if (hasRingingCall) {
                if (DBG) log("handleCallKey: ringing ==> answer!");
                internalAnswerCall();  // Automatically holds the current active call,
                                       // if there is one
            } else { 
                // send an empty CDMA flash string
                PhoneUtils.switchHoldingAndActive(mPhone);
            }
        } else {
            // The green CALL button means either "Answer", "Add another", or
            // "Swap calls", depending on the current state of the Phone.

            if (hasRingingCall) {
                if (hasActiveCall && hasHoldingCall) {
                    if (DBG) log("handleCallKey: ringing (both lines in use) ==> answer!");
                    internalAnswerCallBothLinesInUse();
                } else {
                    if (DBG) log("handleCallKey: ringing ==> answer!");
                    internalAnswerCall();  // Automatically holds the current active call,
                                           // if there is one
                }
            } else if (hasActiveCall && hasHoldingCall) {
                if (DBG) log("handleCallKey: both lines in use ==> swap calls.");
                PhoneUtils.switchHoldingAndActive(mPhone);
            } else {
                // Note we *don't* allow "Add call" if the foreground call is
                // still DIALING or ALERTING.
                if (PhoneUtils.okToAddCall(mPhone)) {
                    if (DBG) log("handleCallKey: <2 lines in use ==> add another...");
                    PhoneUtils.startNewCall(mPhone);  // Fires off a ACTION_DIAL intent
                } else {
                    if (DBG) log("handleCallKey: <2 lines in use but CAN'T add call; ignoring...");
                }
            }
        }

        // We *always* consume the CALL key, since the system-wide default
        // action ("go to the in-call screen") is useless here.
        return true;
    }

    boolean isKeyEventAcceptableDTMF (KeyEvent event) {
        return (mDialer != null && mDialer.isKeyEventAcceptable(event));
    }

    /**
     * Overriden to track relevant focus changes.
     *
     * If a key is down and some time later the focus changes, we may
     * NOT recieve the keyup event; logically the keyup event has not
     * occured in this window.  This issue is fixed by treating a focus
     * changed event as an interruption to the keydown, making sure
     * that any code that needs to be run in onKeyUp is ALSO run here.
     *
     * Note, this focus change event happens AFTER the in-call menu is
     * displayed, so mIsMenuDisplayed should always be correct by the
     * time this method is called in the framework, please see:
     * {@link onCreatePanelView}, {@link onOptionsMenuClosed}
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // the dtmf tones should no longer be played
        if (DBG) log("handling key up event...");
        if (!hasFocus && mDialer != null) {
            mDialer.onDialerKeyUp(null);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // push input to the dialer.
        if (DBG) log("handling key up event...");
        if ((mDialer != null) && (mDialer.onDialerKeyUp(event))){
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_CALL) {
            if (handleCallKey()) {
                return true;
            } else {
                Log.w(LOG_TAG, "InCallScreen should always handle KEYCODE_CALL in onKeyUp");
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                // consume KEYCODE_CALL so PhoneWindow doesn't do anything with it
                return true;

            // Note there's no KeyEvent.KEYCODE_ENDCALL case here.
            // The standard system-wide handling of the ENDCALL key
            // (see PhoneWindowManager's handling of KEYCODE_ENDCALL)
            // already implements exactly what the UI spec wants,
            // namely (1) "hang up" if there's a current active call,
            // or (2) "don't answer" if there's a current ringing call.

            case KeyEvent.KEYCODE_BACK:
                if (handleBackKey()) {
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_CAMERA:
                // Disable the CAMERA button while in-call since it's too
                // easy to press accidentally.
                return true;

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                // Use the CallNotifier's ringer reference to keep things tidy
                // when we are requesting that the ringer be silenced.
                CallNotifier notifier = PhoneApp.getInstance().notifier;
                if (notifier.isRinging()) {
                    PhoneUtils.setAudioControlState(PhoneUtils.AUDIO_IDLE);
                    if (DBG) log("VOLUME key: silence ringer");
                    notifier.silenceRinger();
                    return true;
                }
                break;

            // Various testing/debugging features, enabled ONLY when DBG == true.
            case KeyEvent.KEYCODE_SLASH:
                if (DBG) {
                    log("----------- InCallScreen View dump --------------");
                    // Dump starting from the top-level view of the entire activity:
                    Window w = this.getWindow();
                    View decorView = w.getDecorView();
                    decorView.debug();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_EQUALS:
                if (DBG) {
                    log("----------- InCallScreen call state dump --------------");
                    PhoneUtils.dumpCallState(mPhone);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_GRAVE:
                if (DBG) {
                    // Placeholder for other misc temp testing
                    log("------------ Temp testing -----------------");
                    return true;
                }
                break;
        }

        if (event.getRepeatCount() == 0 && handleDialerKeyDown(keyCode, event)) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Handle a failure notification for a supplementary service
     * (i.e. conference, switch, separate, transfer, etc.).
     */
    void onSuppServiceFailed(AsyncResult r) {
        Phone.SuppService service = (Phone.SuppService) r.result;
        if (DBG) log("onSuppServiceFailed: " + service);

        int errorMessageResId;
        switch (service) {
            case SWITCH:
                // Attempt to switch foreground and background/incoming calls failed
                // ("Failed to switch calls")
                errorMessageResId = R.string.incall_error_supp_service_switch;
                break;

            case SEPARATE:
                // Attempt to separate a call from a conference call
                // failed ("Failed to separate out call")
                errorMessageResId = R.string.incall_error_supp_service_separate;
                break;

            case TRANSFER:
                // Attempt to connect foreground and background calls to
                // each other (and hanging up user's line) failed ("Call
                // transfer failed")
                errorMessageResId = R.string.incall_error_supp_service_transfer;
                break;

            case CONFERENCE:
                // Attempt to add a call to conference call failed
                // ("Conference call failed")
                errorMessageResId = R.string.incall_error_supp_service_conference;
                break;

            case REJECT:
                // Attempt to reject an incoming call failed
                // ("Call rejection failed")
                errorMessageResId = R.string.incall_error_supp_service_reject;
                break;

            case HANGUP:
                // Attempt to release a call failed ("Failed to release call(s)")
                errorMessageResId = R.string.incall_error_supp_service_hangup;
                break;

            case UNKNOWN:
            default:
                // Attempt to use a service we don't recognize or support
                // ("Unsupported service" or "Selected service failed")
                errorMessageResId = R.string.incall_error_supp_service_unknown;
                break;
        }

        // mSuppServiceFailureDialog is a generic dialog used for any
        // supp service failure, and there's only ever have one
        // instance at a time.  So just in case a previous dialog is
        // still around, dismiss it.
        if (mSuppServiceFailureDialog != null) {
            if (DBG) log("- DISMISSING mSuppServiceFailureDialog.");
            mSuppServiceFailureDialog.dismiss();  // It's safe to dismiss() a dialog
                                                  // that's already dismissed.
            mSuppServiceFailureDialog = null;
        }

        mSuppServiceFailureDialog = new AlertDialog.Builder(this)
                .setMessage(errorMessageResId)
                .setPositiveButton(R.string.ok, null)
                .setCancelable(true)
                .create();
        mSuppServiceFailureDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mSuppServiceFailureDialog.show();
    }

    /**
     * Something has changed in the phone's state.  Update the UI.
     */
    private void onPhoneStateChanged(AsyncResult r) {
        if (DBG) log("onPhoneStateChanged()...");
        updateScreen();

        // making sure we update the poke lock and wake lock when certain
        // phone state changes occur.
        updateWakeState();
    }

    /**
     * Handle the setting of the wake state, including the poke lock and
     * the wake lock.  This helps to centralize the calls to the
     * PowerManagerService from the phone code.
     */
    /*package*/ void updateWakeState() {
        PhoneApp app = PhoneApp.getInstance();
        Phone.State state = mPhone.getState();

        // Short delay when the dialer is up, longer delay when the
        // in call screen is visible, and default delay otherwise.
        if (mDialer != null && mDialer.isOpened()) {
            app.setScreenTimeout(PhoneApp.ScreenTimeoutDuration.SHORT);
        } else if (app.isShowingCallScreen()){
            app.setScreenTimeout(PhoneApp.ScreenTimeoutDuration.MEDIUM);
        } else {
            app.setScreenTimeout(PhoneApp.ScreenTimeoutDuration.DEFAULT);
        }

        // wake the screen if the phone is ringing or on any disconnect.
        app.keepScreenOn(state == Phone.State.RINGING ||
                PhoneUtils.hasDisconnectedConnections(mPhone));
    }

    /**
     * Updates the UI after a phone connection is disconnected, as follows:
     *
     * - If this was a missed or rejected incoming call, and no other
     *   calls are active, dismiss the in-call UI immediately.  (The
     *   CallNotifier will still create a "missed call" notification if
     *   necessary.)
     *
     * - With any other disconnect cause, if the phone is now totally
     *   idle, display the "Call ended" state for a couple of seconds.
     *
     * - Or, if the phone is still in use, stay on the in-call screen
     *   (and update the UI to reflect the current state of the Phone.)
     *
     * @param r r.result contains the connection that just ended
     */
    private void onDisconnect(AsyncResult r) {
        Connection c = (Connection) r.result;
        Connection.DisconnectCause cause = c.getDisconnectCause();
        if (DBG) log("onDisconnect: " + c + ", cause=" + cause);

        // Under certain call disconnected states, we want to alert the user
        // with a dialog instead of going through the normal disconnect
        // routine.
        if (cause == Connection.DisconnectCause.CALL_BARRED) {
            showGenericErrorDialog(R.string.callFailed_cb_enabled, false);
            return;
        } else if (cause == Connection.DisconnectCause.FDN_BLOCKED) {
            showGenericErrorDialog(R.string.callFailed_fdn_only, false);
            return;
        }

        PhoneApp app = PhoneApp.getInstance();
        boolean currentlyIdle = !phoneIsInUse();

        // Explicitly clean up up any DISCONNECTED connections
        // in a conference call.
        // [Background: Even after a connection gets disconnected, its
        // Connection object still stays around for a few seconds, in the
        // DISCONNECTED state.  With regular calls, this state drives the
        // "call ended" UI.  But when a single person disconnects from a
        // conference call there's no "call ended" state at all; in that
        // case we blow away any DISCONNECTED connections right now to make sure
        // the UI updates instantly to reflect the current state.]
        Call call = c.getCall();
        if (call != null) {
            // We only care about situation of a single caller
            // disconnecting from a conference call.  In that case, the
            // call will have more than one Connection (including the one
            // that just disconnected, which will be in the DISCONNECTED
            // state) *and* at least one ACTIVE connection.  (If the Call
            // has *no* ACTIVE connections, that means that the entire
            // conference call just ended, so we *do* want to show the
            // "Call ended" state.)
            List<Connection> connections = call.getConnections();
            if (connections != null && connections.size() > 1) {
                for (Connection conn : connections) {
                    if (conn.getState() == Call.State.ACTIVE) {
                        // This call still has at least one ACTIVE connection!
                        // So blow away any DISCONNECTED connections
                        // (including, presumably, the one that just
                        // disconnected from this conference call.)

                        // We also force the wake state to refresh, just in
                        // case the disconnected connections are removed
                        // before the phone state change.
                        if (DBG) log("- Still-active conf call; clearing DISCONNECTED...");
                        updateWakeState();
                        mPhone.clearDisconnected();  // This happens synchronously.
                        break;
                    }
                }
            }
        }

        // Retrieve the emergency call retry count from this intent, in
        // case we need to retry the call again.
        int emergencyCallRetryCount = getIntent().getIntExtra(
                EmergencyCallHandler.EMERGENCY_CALL_RETRY_KEY,
                EmergencyCallHandler.INITIAL_ATTEMPT);

        // Note: see CallNotifier.onDisconnect() for some other behavior
        // that might be triggered by a disconnect event, like playing the
        // busy/congestion tone.

        // Keep track of whether this call was user-initiated or not.
        // (This affects where we take the user next; see delayedCleanupAfterDisconnect().)
        mShowCallLogAfterDisconnect = !c.isIncoming();

        // We bail out immediately (and *don't* display the "call ended"
        // state at all) in a couple of cases, including those where we
        // are waiting for the radio to finish powering up for an
        // emergency call:
        boolean bailOutImmediately =
                ((cause == Connection.DisconnectCause.INCOMING_MISSED)
                 || (cause == Connection.DisconnectCause.INCOMING_REJECTED)
                 || ((cause == Connection.DisconnectCause.OUT_OF_SERVICE)
                         && (emergencyCallRetryCount > 0)))
                && currentlyIdle;

        if (bailOutImmediately) {
            if (DBG) log("- onDisconnect: bailOutImmediately...");
            // Exit the in-call UI!
            // (This is basically the same "delayed cleanup" we do below,
            // just with zero delay.  Since the Phone is currently idle,
            // this call is guaranteed to immediately finish() this activity.)
            delayedCleanupAfterDisconnect();

            // Retry the call, by resending the intent to the emergency
            // call handler activity.
            if ((cause == Connection.DisconnectCause.OUT_OF_SERVICE)
                    && (emergencyCallRetryCount > 0)) {
                startActivity(getIntent()
                        .setClassName(this, EmergencyCallHandler.class.getName()));
            }
        } else {
            if (DBG) log("- onDisconnect: delayed bailout...");
            // Stay on the in-call screen for now.  (Either the phone is
            // still in use, or the phone is idle but we want to display
            // the "call ended" state for a couple of seconds.)

            // Force a UI update in case we need to display anything
            // special given this connection's DisconnectCause (see
            // CallCard.getCallFailedString()).
            updateScreen();

            // If the Phone *is* totally idle now, display the "Call
            // ended" state.
            if (currentlyIdle) {
                setInCallScreenMode(InCallScreenMode.CALL_ENDED);
            }

            // Some other misc cleanup that we do if the call that just
            // disconnected was the foreground call.
            final boolean hasActiveCall = !mForegroundCall.isIdle();
            if (!hasActiveCall) {
                if (DBG) log("- onDisconnect: cleaning up after FG call disconnect...");

                // Dismiss any dialogs which are only meaningful for an
                // active call *and* which become moot if the call ends.
                if (mWaitPromptDialog != null) {
                    if (DBG) log("- DISMISSING mWaitPromptDialog.");
                    mWaitPromptDialog.dismiss();  // safe even if already dismissed
                    mWaitPromptDialog = null;
                }
                if (mWildPromptDialog != null) {
                    if (DBG) log("- DISMISSING mWildPromptDialog.");
                    mWildPromptDialog.dismiss();  // safe even if already dismissed
                    mWildPromptDialog = null;
                }
            }

            // Updating the screen wake state is done in onPhoneStateChanged().

            // Finally, arrange for delayedCleanupAfterDisconnect() to get
            // called after a short interval (during which we display the
            // "call ended" state.)  At that point, if the
            // Phone is idle, we'll finish() out of this activity.
            int callEndedDisplayDelay =
                    (cause == Connection.DisconnectCause.LOCAL)
                    ? CALL_ENDED_SHORT_DELAY : CALL_ENDED_LONG_DELAY;
            mHandler.removeMessages(DELAYED_CLEANUP_AFTER_DISCONNECT);
            Message message = Message.obtain(mHandler, DELAYED_CLEANUP_AFTER_DISCONNECT);
            mHandler.sendMessageDelayed(message, callEndedDisplayDelay);
        }
    }

    /**
     * Brings up the "MMI Started" dialog.
     */
    private void onMMIInitiate(AsyncResult r) {
        if (DBG) log("onMMIInitiate()...  AsyncResult r = " + r);

        // Watch out: don't do this if we're in the middle of bailing out
        // of this activity, since in the Dialog.show() will just fail
        // because we don't have a valid window token any more...
        // (That exact sequence can happen if you try to start an MMI code
        // while the radio is off or out of service.)
        if (isFinishing()) {
            if (DBG) log("Activity finishing! Bailing out...");
            return;
        }

        // Also, if any other dialog is up right now (presumably the
        // generic error dialog displaying the "Starting MMI..."  message)
        // take it down before bringing up the real "MMI Started" dialog
        // in its place.
        dismissAllDialogs();

        MmiCode mmiCode = (MmiCode) r.result;
        if (DBG) log("  - MmiCode: " + mmiCode);

        Message message = Message.obtain(mHandler, PhoneApp.MMI_CANCEL);
        mMmiStartedDialog = PhoneUtils.displayMMIInitiate(this, mmiCode,
                                                          message, mMmiStartedDialog);
    }

    /**
     * Handles an MMI_CANCEL event, which is triggered by the button
     * (labeled either "OK" or "Cancel") on the "MMI Started" dialog.
     * @see onMMIInitiate
     * @see PhoneUtils.cancelMmiCode
     */
    private void onMMICancel() {
        if (DBG) log("onMMICancel()...");

        // First of all, cancel the outstanding MMI code (if possible.)
        PhoneUtils.cancelMmiCode(mPhone);

        // Regardless of whether the current MMI code was cancelable, the
        // PhoneApp will get an MMI_COMPLETE event very soon, which will
        // take us to the MMI Complete dialog (see
        // PhoneUtils.displayMMIComplete().)
        //
        // But until that event comes in, we *don't* want to stay here on
        // the in-call screen, since we'll be visible in a
        // partially-constructed state as soon as the "MMI Started" dialog
        // gets dismissed.  So let's forcibly bail out right now.
        finish();
    }

    /**
     * Handles the POST_ON_DIAL_CHARS message from the Phone
     * (see our call to mPhone.setOnPostDialCharacter() above.)
     *
     * TODO: NEED TO TEST THIS SEQUENCE now that we no longer handle
     * "dialable" key events here in the InCallScreen: we do directly to the
     * Dialer UI instead.  Similarly, we may now need to go directly to the
     * Dialer to handle POST_ON_DIAL_CHARS too.
     */
    private void handlePostOnDialChars(AsyncResult r, char ch) {
        Connection c = (Connection) r.result;

        if (c != null) {
            Connection.PostDialState state =
                    (Connection.PostDialState) r.userObj;

            if (DBG) log("handlePostOnDialChar: state = " +
                    state + ", ch = " + ch);

            switch (state) {
                case STARTED:
                    // TODO: is this needed, now that you can't actually
                    // type DTMF chars or dial directly from here?
                    // If so, we'd need to yank you out of the in-call screen
                    // here too (and take you to the 12-key dialer in "in-call" mode.)
                    // displayPostDialedChar(ch);
                    break;

                case WAIT:
                    //if (DBG) log("show wait prompt...");
                    String postDialStr = c.getRemainingPostDialString();
                    showWaitPromptDialog(c, postDialStr);
                    break;

                case WILD:
                    //if (DBG) log("prompt user to replace WILD char");
                    showWildPromptDialog(c);
                    break;

                case COMPLETE:
                    break;

                default:
                    break;
            }
        }
    }

    private void showWaitPromptDialog(final Connection c, String postDialStr) {
        Resources r = getResources();
        StringBuilder buf = new StringBuilder();
        buf.append(r.getText(R.string.wait_prompt_str));
        buf.append(postDialStr);

        if (mWaitPromptDialog != null) {
            if (DBG) log("- DISMISSING mWaitPromptDialog.");
            mWaitPromptDialog.dismiss();  // safe even if already dismissed
            mWaitPromptDialog = null;
        }

        mWaitPromptDialog = new AlertDialog.Builder(this)
                .setMessage(buf.toString())
                .setPositiveButton(R.string.send_button, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            if (DBG) log("handle WAIT_PROMPT_CONFIRMED, proceed...");
                            c.proceedAfterWaitChar();
                        }
                    })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            if (DBG) log("handle POST_DIAL_CANCELED!");
                            c.cancelPostDial();
                        }
                    })
                .create();
        mWaitPromptDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mWaitPromptDialog.show();
    }

    private View createWildPromptView() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(5, 5, 5, 5);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView promptMsg = new TextView(this);
        promptMsg.setTextSize(14);
        promptMsg.setTypeface(Typeface.DEFAULT_BOLD);
        promptMsg.setText(getResources().getText(R.string.wild_prompt_str));

        result.addView(promptMsg, lp);

        mWildPromptText = new EditText(this);
        mWildPromptText.setKeyListener(DialerKeyListener.getInstance());
        mWildPromptText.setMovementMethod(null);
        mWildPromptText.setTextSize(14);
        mWildPromptText.setMaxLines(1);
        mWildPromptText.setHorizontallyScrolling(true);
        mWildPromptText.setBackgroundResource(android.R.drawable.editbox_background);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.FILL_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, 3, 0, 0);

        result.addView(mWildPromptText, lp2);

        return result;
    }

    private void showWildPromptDialog(final Connection c) {
        View v = createWildPromptView();

        if (mWildPromptDialog != null) {
            if (DBG) log("- DISMISSING mWildPromptDialog.");
            mWildPromptDialog.dismiss();  // safe even if already dismissed
            mWildPromptDialog = null;
        }

        mWildPromptDialog = new AlertDialog.Builder(this)
                .setView(v)
                .setPositiveButton(
                        R.string.send_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                if (DBG) log("handle WILD_PROMPT_CHAR_ENTERED, proceed...");
                                String replacement = null;
                                if (mWildPromptText != null) {
                                    replacement = mWildPromptText.getText().toString();
                                    mWildPromptText = null;
                                }
                                c.proceedAfterWildChar(replacement);
                            }
                        })
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                if (DBG) log("handle POST_DIAL_CANCELED!");
                                c.cancelPostDial();
                            }
                        })
                .create();
        mWildPromptDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        mWildPromptDialog.show();

        mWildPromptText.requestFocus();
    }

    /**
     * Updates the state of the in-call UI based on the current state of
     * the Phone.
     */
    private void updateScreen() {
        if (DBG) log("updateScreen()...");

        // Watch out: don't update anything if we're in the middle of
        // bailing out of this activity, since that'll cause a visible
        // glitch in the "activity ending" transition.
        if (isFinishing()) {
            if (DBG) log("- updateScreen: Activity finishing! Bailing out...");
            return;
        }

        // Update the state of the in-call menu items.
        if (mInCallMenu != null) {
            // TODO: do this only if the menu is visible!
            if (DBG) log("- updateScreen: updating menu items...");
            boolean okToShowMenu = mInCallMenu.updateItems(mPhone);
            if (!okToShowMenu) {
                // Uh oh: we were only trying to update the state of the
                // menu items, but the logic in InCallMenu.updateItems()
                // just decided the menu shouldn't be visible at all!
                // (That's probably means that the call ended
                // asynchronously while the menu was up.)
                //
                // So take the menu down ASAP.
                if (DBG) log("- updateScreen: Tried to update menu; now need to dismiss!");
                // dismissMenu() has no effect if the menu is already closed.
                dismissMenu(true);  // dismissImmediate = true
            }
        }

        if (mInCallScreenMode == InCallScreenMode.MANAGE_CONFERENCE) {
            if (DBG) log("- updateScreen: manage conference mode (NOT updating in-call UI)...");
            updateManageConferencePanelIfNecessary();
            return;
        } else if (mInCallScreenMode == InCallScreenMode.CALL_ENDED) {
            if (DBG) log("- updateScreen: call ended state (NOT updating in-call UI)...");
            return;
        }

        if (DBG) log("- updateScreen: updating the in-call UI...");
        mCallCard.updateState(mPhone);
        if (mSlidingCardManager != null) {
            mSlidingCardManager.updateCardPreferredPosition();
            mSlidingCardManager.updateCardSlideHints();
        }

        updateDialerDrawer();

        updateMenuButtonHint();
    }

    /**
     * (Re)synchronizes the onscreen UI with the current state of the
     * Phone.
     *
     * @return InCallInitStatus.SUCCESS if we successfully updated the UI, or
     *    InCallInitStatus.PHONE_NOT_IN_USE if there was no phone state to sync
     *    with (ie. the phone was completely idle).  In the latter case, we
     *    shouldn't even be in the in-call UI in the first place, and it's
     *    the caller's responsibility to bail out of this activity (by
     *    calling finish()) if appropriate.
     */
    private InCallInitStatus syncWithPhoneState() {
        boolean updateSuccessful = false;
        if (DBG) log("syncWithPhoneState()...");
        if (DBG) PhoneUtils.dumpCallState(mPhone);

        // Make sure the Phone is in use (otherwise we shouldn't be here
        // in the first place.)
        // Need to treat running MMI codes as a connection as well.
        if (!mForegroundCall.isIdle() || !mBackgroundCall.isIdle() || !mRingingCall.isIdle()
            || !mPhone.getPendingMmiCodes().isEmpty()) {
            if (DBG) log("syncWithPhoneState: update screen");
            updateScreen();
            return InCallInitStatus.SUCCESS;
        }

        if (DBG) log("syncWithPhoneState: couldn't sync; phone is idle!");
        return InCallInitStatus.PHONE_NOT_IN_USE;
    }

    /**
     * Given the Intent we were initially launched with,
     * figure out the actual phone number we should dial.
     *
     * @return the phone number corresponding to the
     *   specified Intent, or null if the Intent is not
     *   a ACTION_CALL intent or if the intent's data is
     *   malformed or missing.
     *
     * @throws VoiceMailNumberMissingException if the intent
     *   contains a "voicemail" URI, but there's no voicemail
     *   number configured on the device.
     */
    private String getInitialNumber(Intent intent)
            throws PhoneUtils.VoiceMailNumberMissingException {
        String action = intent.getAction();

        if (action == null) {
            return null;
        }

        if (action != null && action.equals(Intent.ACTION_CALL) &&
                intent.hasExtra(Intent.EXTRA_PHONE_NUMBER)) {
            return intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
        }

        return PhoneUtils.getNumberFromIntent(this, mPhone, intent);
    }

    /**
     * Make a call to whomever the intent tells us to.
     *
     * @param intent the Intent we were launched with
     * @return InCallInitStatus.SUCCESS if we successfully initiated an
     *    outgoing call.  If there was some kind of failure, return one of
     *    the other InCallInitStatus codes indicating what went wrong.
     */
    private InCallInitStatus placeCall(Intent intent) {
        if (DBG) log("placeCall()...  intent = " + intent);

        String number;

        // Check the current ServiceState to make sure it's OK
        // to even try making a call.
        InCallInitStatus okToCallStatus = checkIfOkToInitiateOutgoingCall();

        try {
            number = getInitialNumber(intent);
        } catch (PhoneUtils.VoiceMailNumberMissingException ex) {
            // If the call status is NOT in an acceptable state, it
            // may effect the way the voicemail number is being
            // retrieved.  Mask the VoiceMailNumberMissingException
            // with the underlying issue of the phone state.
            if (okToCallStatus != InCallInitStatus.SUCCESS) {
                if (DBG) log("Voicemail number not reachable in current SIM card state.");
                return okToCallStatus;
            }
            if (DBG) log("VoiceMailNumberMissingException from getInitialNumber()");
            return InCallInitStatus.VOICEMAIL_NUMBER_MISSING;
        }

        if (number == null) {
            Log.w(LOG_TAG, "placeCall: couldn't get a phone number from Intent " + intent);
            return InCallInitStatus.NO_PHONE_NUMBER_SUPPLIED;
        }

        boolean isEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(number);
        boolean isEmergencyIntent = Intent.ACTION_CALL_EMERGENCY.equals(intent.getAction());

        if (isEmergencyNumber && !isEmergencyIntent) {
            Log.e(LOG_TAG, "Non-CALL_EMERGENCY Intent " + intent
                    + " attempted to call emergency number " + number
                    + ".");
            return InCallInitStatus.CALL_FAILED;
        } else if (!isEmergencyNumber && isEmergencyIntent) {
            Log.e(LOG_TAG, "Received CALL_EMERGENCY Intent " + intent
                    + " with non-emergency number " + number
                    + " -- failing call.");
            return InCallInitStatus.CALL_FAILED;
        }

        // need to make sure that the state is adjusted if we are ONLY
        // allowed to dial emergency numbers AND we encounter an
        // emergency number request.
        if (isEmergencyNumber && okToCallStatus == InCallInitStatus.EMERGENCY_ONLY) {
            okToCallStatus = InCallInitStatus.SUCCESS;
            if (DBG) log("Emergency number detected, changing state to: " + okToCallStatus);
        }

        if (okToCallStatus != InCallInitStatus.SUCCESS) {
            // If this is an emergency call, we call the emergency call
            // handler activity to turn on the radio and do whatever else
            // is needed. For now, we finish the InCallScreen (since were
            // expecting a callback when the emergency call handler dictates
            // it) and just return the success state.
            if (isEmergencyNumber && (okToCallStatus == InCallInitStatus.POWER_OFF)) {
                startActivity(getIntent()
                        .setClassName(this, EmergencyCallHandler.class.getName()));
                finish();
                return InCallInitStatus.SUCCESS;
            } else {
                return okToCallStatus;
            }
        }

        // We have a valid number, so try to actually place a call:
        //make sure we pass along the URI as a reference to the contact.
        int callStatus = PhoneUtils.placeCall(mPhone, number, intent.getData());
        switch (callStatus) {
            case PhoneUtils.CALL_STATUS_DIALED:
                if (DBG) log("placeCall: PhoneUtils.placeCall() succeeded for regular call '"
                             + number + "'.");
                return InCallInitStatus.SUCCESS;
            case PhoneUtils.CALL_STATUS_DIALED_MMI:
                if (DBG) log("placeCall: specified number was an MMI code: '" + number + "'.");
                // The passed-in number was an MMI code, not a regular phone number!
                // This isn't really a failure; the Dialer may have deliberately
                // fired a ACTION_CALL intent to dial an MMI code, like for a
                // USSD call.
                //
                // Presumably an MMI_INITIATE message will come in shortly
                // (and we'll bring up the "MMI Started" dialog), or else
                // an MMI_COMPLETE will come in (which will take us to a
                // different Activity; see PhoneUtils.displayMMIComplete()).
                return InCallInitStatus.DIALED_MMI;
            case PhoneUtils.CALL_STATUS_FAILED:
                Log.w(LOG_TAG, "placeCall: PhoneUtils.placeCall() FAILED for number '"
                      + number + "'.");
                // We couldn't successfully place the call; there was some
                // failure in the telephony layer.
                return InCallInitStatus.CALL_FAILED;
            default:
                Log.w(LOG_TAG, "placeCall: unknown callStatus " + callStatus
                      + " from PhoneUtils.placeCall() for number '" + number + "'.");
                return InCallInitStatus.SUCCESS;  // Try to continue anyway...
        }
    }

    /**
     * Checks the current ServiceState to make sure it's OK
     * to try making an outgoing call to the specified number.
     *
     * @return InCallInitStatus.SUCCESS if it's OK to try calling the specified
     *    number.  If not, like if the radio is powered off or we have no
     *    signal, return one of the other InCallInitStatus codes indicating what
     *    the problem is.
     */
    private InCallInitStatus checkIfOkToInitiateOutgoingCall() {
        // Watch out: do NOT use PhoneStateIntentReceiver.getServiceState() here;
        // that's not guaranteed to be fresh.  To synchronously get the
        // CURRENT service state, ask the Phone object directly:
        int state = mPhone.getServiceState().getState();
        if (DBG) log("checkIfOkToInitiateOutgoingCall: ServiceState = " + state);

        switch (state) {
            case ServiceState.STATE_IN_SERVICE:
                // Normal operation.  It's OK to make outgoing calls.
                return InCallInitStatus.SUCCESS;


            case ServiceState.STATE_POWER_OFF:
                // Radio is explictly powered off.
                return InCallInitStatus.POWER_OFF;

            case ServiceState.STATE_OUT_OF_SERVICE:
            case ServiceState.STATE_EMERGENCY_ONLY:
                // The phone is registered, but locked. Only emergency
                // numbers are allowed.
                return InCallInitStatus.EMERGENCY_ONLY;
            default:
                throw new IllegalStateException("Unexpected ServiceState: " + state);
        }
    }

    private void handleMissingVoiceMailNumber() {
        if (DBG) log("handleMissingVoiceMailNumber");

        final Message msg = Message.obtain(mHandler);
        msg.what = DONT_ADD_VOICEMAIL_NUMBER;

        final Message msg2 = Message.obtain(mHandler);
        msg2.what = ADD_VOICEMAIL_NUMBER;

        mMissingVoicemailDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.no_vm_number)
                .setMessage(R.string.no_vm_number_msg)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (DBG) log("Missing voicemail AlertDialog: POSITIVE click...");
                            msg.sendToTarget();  // see dontAddVoiceMailNumber()
                        }})
                .setNegativeButton(R.string.add_vm_number_str,
                                   new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (DBG) log("Missing voicemail AlertDialog: NEGATIVE click...");
                            msg2.sendToTarget();  // see addVoiceMailNumber()
                        }})
                .setOnCancelListener(new OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            if (DBG) log("Missing voicemail AlertDialog: CANCEL handler...");
                            msg.sendToTarget();  // see dontAddVoiceMailNumber()
                        }})
                .create();

        // When the dialog is up, completely hide the in-call UI
        // underneath (which is in a partially-constructed state).
        mMissingVoicemailDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mMissingVoicemailDialog.show();
    }

    private void addVoiceMailNumberPanel() {
        if (mMissingVoicemailDialog != null) {
            mMissingVoicemailDialog.dismiss();
            mMissingVoicemailDialog = null;
        }
        finish();

        if (DBG) log("show vm setting");

        // navigate to the Voicemail setting in the Call Settings activity.
        Intent intent = new Intent(CallFeaturesSetting.ACTION_ADD_VOICEMAIL);
        intent.setClass(this, CallFeaturesSetting.class);
        startActivity(intent);
    }

    private void dontAddVoiceMailNumber() {
        if (mMissingVoicemailDialog != null) {
            mMissingVoicemailDialog.dismiss();
            mMissingVoicemailDialog = null;
        }
        finish();
    }

    /**
     * Do some delayed cleanup after a Phone call gets disconnected.
     *
     * This method gets called a couple of seconds after any DISCONNECT
     * event from the Phone; it's triggered by the
     * DELAYED_CLEANUP_AFTER_DISCONNECT message we send in onDisconnect().
     *
     * If the Phone is totally idle right now, that means we've already
     * shown the "call ended" state for a couple of seconds, and it's now
     * time to finish() this activity.
     *
     * If the Phone is *not* idle right now, that probably means that one
     * call ended but the other line is still in use.  In that case, we
     * *don't* exit the in-call screen, but we at least turn off the
     * backlight (which we turned on in onDisconnect().)
     */
    private void delayedCleanupAfterDisconnect() {
        if (DBG) log("delayedCleanupAfterDisconnect()...  Phone state = " + mPhone.getState());

        // Clean up any connections in the DISCONNECTED state.
        //
        // [Background: Even after a connection gets disconnected, its
        // Connection object still stays around, in the special
        // DISCONNECTED state.  This is necessary because we we need the
        // caller-id information from that Connection to properly draw the
        // "Call ended" state of the CallCard.
        //   But at this point we truly don't need that connection any
        // more, so tell the Phone that it's now OK to to clean up any
        // connections still in that state.]
        mPhone.clearDisconnected();

        PhoneApp app = PhoneApp.getInstance();
        if (!phoneIsInUse()) {
            // Phone is idle!  We should exit this screen now.
            if (DBG) log("- delayedCleanupAfterDisconnect: phone is idle...");

            // No need to re-enable keyguard or screen wake state here;
            // that happens in onPause() when we actually exit.

            // And (finally!) exit from the in-call screen
            // (but not if we're already in the process of finishing...)
            if (isFinishing()) {
                if (DBG) log("- delayedCleanupAfterDisconnect: already finished, doing nothing.");
            } else {
                if (DBG) log("- delayedCleanupAfterDisconnect: finishing...");

                // If this is a call that was initiated by the user, and
                // we're *not* in emergency mode, finish the call by
                // taking the user to the Call Log.
                // Otherwise we simply call finish(), which will take us
                // back to wherever we came from.
                if (mShowCallLogAfterDisconnect && !isPhoneStateRestricted()) {
                    if (DBG) log("- Show Call Log after disconnect...");
                    final Intent intent = PhoneApp.createCallLogIntent();
                    startActivity(intent);
                    // Even in this case we still call finish() (below),
                    // to make sure we don't stay in the activity history.
                }

                finish();
            }
        } else {
            // The phone is still in use.  Stay here in this activity, but
            // we don't need to keep the screen on.
            if (DBG) log("- delayedCleanupAfterDisconnect: staying on the InCallScreen...");
            if (DBG) PhoneUtils.dumpCallState(mPhone);
            // No need to re-enable keyguard or screen wake state here;
            // should be taken care of in onPhoneStateChanged();
        }
    }


    //
    // Callbacks for buttons / menu items.
    //

    public void onClick(View view) {
        int id = view.getId();
        if (DBG) log("onClick(View " + view + ", id " + id + ")...");
        if (DBG && view instanceof InCallMenuItemView) {
            InCallMenuItemView item = (InCallMenuItemView) view;
            if (DBG) log("  ==> menu item! " + item);
        }

        // Most menu items dismiss the menu immediately once you click
        // them.  But some items (the "toggle" buttons) are different:
        // they want the menu to stay visible for a second afterwards to
        // give you feedback about the state change.
        boolean dismissMenuImmediate = true;

        switch (id) {
            case R.id.menuAnswerAndHold:
                if (DBG) log("onClick: AnswerAndHold...");
                internalAnswerCall();  // Automatically holds the current active call
                break;

            case R.id.menuAnswerAndEnd:
                if (DBG) log("onClick: AnswerAndEnd...");
                internalAnswerAndEnd();
                break;

            case R.id.menuSwapCalls:
                if (DBG) log("onClick: SwapCalls...");
                PhoneUtils.switchHoldingAndActive(mPhone);
                break;

            case R.id.menuMergeCalls:
                if (DBG) log("onClick: MergeCalls...");
                PhoneUtils.mergeCalls(mPhone);
                break;

            case R.id.menuManageConference:
                if (DBG) log("onClick: ManageConference...");
                // Show the Manage Conference panel.
                setInCallScreenMode(InCallScreenMode.MANAGE_CONFERENCE);
                break;

            case R.id.manage_done:  // mButtonManageConferenceDone
                if (DBG) log("onClick: mButtonManageConferenceDone...");
                // Hide the Manage Conference panel, return to NORMAL mode.
                setInCallScreenMode(InCallScreenMode.NORMAL);
                break;

            case R.id.menuSpeaker:
                if (DBG) log("onClick: Speaker...");
                // TODO: Turning on the speaker seems to enable the mic
                //   whether or not the "mute" feature is active!
                // Not sure if this is an feature of the telephony API
                //   that I need to handle specially, or just a bug.
                Context context = getApplicationContext();
                boolean newSpeakerState = !PhoneUtils.isSpeakerOn(context);
                if (newSpeakerState && isBluetoothAvailable() && isBluetoothAudioConnected()) {
                    disconnectBluetoothAudio();
                }
                PhoneUtils.turnOnSpeaker(context, newSpeakerState);
                // This is a "toggle" button; let the user see the new state for a moment.
                dismissMenuImmediate = false;
                break;

            case R.id.menuBluetooth:
                if (DBG) log("onClick: Bluetooth...");
                onBluetoothClick();
                // This is a "toggle" button; let the user see the new state for a moment.
                dismissMenuImmediate = false;
                break;

            case R.id.menuMute:
                if (DBG) log("onClick: Mute...");
                boolean newMuteState = !PhoneUtils.getMute(mPhone);
                PhoneUtils.setMute(mPhone, newMuteState);
                // This is a "toggle" button; let the user see the new state for a moment.
                dismissMenuImmediate = false;
                break;

            case R.id.menuHold:
                if (DBG) log("onClick: Hold...");
                onHoldClick();
                // This is a "toggle" button; let the user see the new state for a moment.
                dismissMenuImmediate = false;
                break;

            case R.id.menuAddCall:
                if (DBG) log("onClick: AddCall...");
                PhoneUtils.startNewCall(mPhone);  // Fires off a ACTION_DIAL intent
                break;

            case R.id.menuEndCall:
                if (DBG) log("onClick: EndCall...");
                PhoneUtils.hangup(mPhone);
                break;

            default:
                Log.w(LOG_TAG,
                      "Got click from unexpected View ID " + id + " (View = " + view + ")");
                break;
        }

        if (ENABLE_PHONE_UI_EVENT_LOGGING) {
            // TODO: For now we care only about whether the user uses the
            // in-call buttons at all.  But in the future we may want to
            // log exactly which buttons are being clicked.  (Maybe just
            // call view.getText() here, and append that to the event value?)
            Checkin.logEvent(getContentResolver(),
                             Checkin.Events.Tag.PHONE_UI,
                             PHONE_UI_EVENT_BUTTON_CLICK);
        }

        // If the user just clicked a "stateful" menu item (i.e. one of
        // the toggle buttons), we keep the menu onscreen briefly to
        // provide visual feedback.  Since we want the user to see the
        // *new* current state, force the menu items to update right now.
        //
        // Note that some toggle buttons ("Hold" in particular) do NOT
        // immediately change the state of the Phone.  In that case, the
        // updateItems() call below won't have any visible effect.
        // Instead, the menu will get updated by the updateScreen() call
        // that happens from onPhoneStateChanged().

        if (!dismissMenuImmediate) {
            // TODO: mInCallMenu.updateItems() is a very big hammer; it
            // would be more efficient to update *only* the menu item(s)
            // we just changed.  (Doing it this way doesn't seem to cause
            // a noticeable performance problem, though.)
            if (DBG) log("- onClick: updating menu to show 'new' current state...");
            boolean okToShowMenu = mInCallMenu.updateItems(mPhone);
            if (!okToShowMenu) {
                // Uh oh.  All we tried to do was update the state of the
                // menu items, but the logic in InCallMenu.updateItems()
                // just decided the menu shouldn't be visible at all!
                // (That probably means that the call ended asynchronously
                // while the menu was up.)
                //
                // That's OK; just make sure to take the menu down ASAP.
                if (DBG) log("onClick: Tried to update menu, but now need to take it down!");
                dismissMenuImmediate = true;
            }
        }


        // Finally, *any* action handled here closes the menu (either
        // immediately, or after a short delay).
        //
        // Note that some of the clicks we handle here aren't even menu
        // items in the first place, like the mButtonManageConferenceDone
        // button.  That's OK; if the menu is already closed, the
        // dismissMenu() call does nothing.
        dismissMenu(dismissMenuImmediate);
    }

    private void onHoldClick() {
        if (DBG) log("onHoldClick()...");

        final boolean hasActiveCall = !mForegroundCall.isIdle();
        final boolean hasHoldingCall = !mBackgroundCall.isIdle();
        if (DBG) log("- hasActiveCall = " + hasActiveCall
                     + ", hasHoldingCall = " + hasHoldingCall);
        boolean newHoldState;
        boolean holdButtonEnabled;
        if (hasActiveCall && !hasHoldingCall) {
            // There's only one line in use, and that line is active.
            PhoneUtils.switchHoldingAndActive(mPhone);  // Really means "hold" in this state
            newHoldState = true;
            holdButtonEnabled = true;
        } else if (!hasActiveCall && hasHoldingCall) {
            // There's only one line in use, and that line is on hold.
            PhoneUtils.switchHoldingAndActive(mPhone);  // Really means "unhold" in this state
            newHoldState = false;
            holdButtonEnabled = true;
        } else {
            // Either zero or 2 lines are in use; "hold/unhold" is meaningless.
            newHoldState = false;
            holdButtonEnabled = false;
        }
        // TODO: we *could* now forcibly update the "Hold" button based on
        // "newHoldState" and "holdButtonEnabled".  But for now, do
        // nothing here, and instead let the menu get updated when the
        // onPhoneStateChanged() callback comes in.  (This seems to be
        // responsive enough.)
    }

    private void onBluetoothClick() {
        if (DBG) log("onBluetoothClick()...");

        if (isBluetoothAvailable()) {
            // Toggle the bluetooth audio connection state:
            if (isBluetoothAudioConnected()) {
                disconnectBluetoothAudio();
            } else {
                connectBluetoothAudio();
            }
        } else {
            // Bluetooth isn't available; the "Audio" button shouldn't have
            // been enabled in the first place!
            Log.w(LOG_TAG, "Got onBluetoothClick, but bluetooth is unavailable");
        }
    }

    /**
     * Sent by the SlidingCardManager if user slides the CallCard more than 1/4
     * of the way down.  (If the screen is currently off, doing this "partial
     * slide" is supposed to turn the screen back on.)
     */
    /* package */ void onPartialCardSlide() {
        if (DBG) log("onPartialCardSlide()...");
    }

    /**
     * Updates the "Press Menu for more options" hint based on the current
     * state of the Phone.
     */
    private void updateMenuButtonHint() {
        if (DBG) log("updateMenuButtonHint()...");
        boolean hintVisible = true;

        final boolean hasRingingCall = !mRingingCall.isIdle();
        final boolean hasActiveCall = !mForegroundCall.isIdle();
        final boolean hasHoldingCall = !mBackgroundCall.isIdle();

        // The hint is hidden only when there's no menu at all,
        // which only happens in a few specific cases:

        if (mInCallScreenMode == InCallScreenMode.CALL_ENDED) {
            // The "Call ended" state.
            hintVisible = false;
        } else if (hasRingingCall && !(hasActiveCall && !hasHoldingCall)) {
            // An incoming call where you *don't* have the option to
            // "answer & end" or "answer & hold".
            hintVisible = false;
        }
        int hintVisibility = (hintVisible) ? View.VISIBLE : View.GONE;

        // We actually have two separate "menu button hint" TextViews; one
        // used only in portrait mode (part of the CallCard) and one used
        // only in landscape mode (part of the InCallScreen.)
        TextView callCardMenuButtonHint = mCallCard.getMenuButtonHint();
        if (ConfigurationHelper.isLandscape()) {
            callCardMenuButtonHint.setVisibility(View.GONE);
            mMenuButtonHint.setVisibility(hintVisibility);
        } else {
            callCardMenuButtonHint.setVisibility(hintVisibility);
            mMenuButtonHint.setVisibility(View.GONE);
        }

        // TODO: Consider hiding the hint(s) whenever the menu is onscreen!
        // (Currently, the menu is rendered on top of the hint, but the
        // menu is semitransparent so you can still see the hint
        // underneath, and the hint is *just* visible enough to be
        // distracting.)
    }

    /**
     * Brings up UI to handle the various error conditions that
     * can occur when first initializing the in-call UI.
     *
     * @param status one of the InCallInitStatus error codes.
     */
    private void handleStartupError(InCallInitStatus status) {
        if (DBG) log("handleStartupError(): status = " + status);

        // NOTE that the regular Phone UI is in an uninitialized state at
        // this point, so we don't ever want the user to see it.
        // That means:
        // - Any cases here that need to go to some other activity should
        //   call startActivity() AND immediately call finish() on this one.
        // - Any cases here that bring up a Dialog must ensure that the
        //   Dialog handles both OK *and* cancel by calling finish() on this
        //   Activity.  (See showGenericErrorDialog() for an example.)

        switch(status) {

            case VOICEMAIL_NUMBER_MISSING:
                // Bring up the "Missing Voicemail Number" dialog, which
                // will ultimately take us to some other Activity (or else
                // just bail out of this activity.)
                handleMissingVoiceMailNumber();
                break;

            case POWER_OFF:
                // Radio is explictly powered off.

                // TODO: This UI is ultra-simple for 1.0.  It would be nicer
                // to bring up a Dialog instead with the option "turn on radio
                // now".  If selected, we'd turn the radio on, wait for
                // network registration to complete, and then make the call.

                showGenericErrorDialog(R.string.incall_error_power_off, true);
                break;

            case EMERGENCY_ONLY:
                // Only emergency numbers are allowed, but we tried to dial
                // a non-emergency number.
                showGenericErrorDialog(R.string.incall_error_emergency_only, true);
                break;

            case PHONE_NOT_IN_USE:
                // This error is handled directly in onResume() (by bailing
                // out of the activity.)  We should never see it here.
                Log.w(LOG_TAG,
                      "handleStartupError: unexpected PHONE_NOT_IN_USE status");
                break;

            case NO_PHONE_NUMBER_SUPPLIED:
                // The supplied Intent didn't contain a valid phone number.
                // TODO: Need UI spec for this failure case; for now just
                // show a generic error.
                showGenericErrorDialog(R.string.incall_error_no_phone_number_supplied, true);
                break;

            case DIALED_MMI:
                // Our initial phone number was actually an MMI sequence.
                // There's no real "error" here, but we do bring up the
                // a Toast (as requested of the New UI paradigm).
                //
                // In-call MMIs do not trigger the normal MMI Initiate
                // Notifications, so we should notify the user here.
                // Otherwise, the code in PhoneUtils.java should handle
                // user notifications in the form of Toasts or Dialogs.
                if (mPhone.getState() == Phone.State.OFFHOOK) {
                    Toast.makeText(this, R.string.incall_status_dialed_mmi, Toast.LENGTH_SHORT)
                        .show();
                }
                break;

            case CALL_FAILED:
                // We couldn't successfully place the call; there was some
                // failure in the telephony layer.
                // TODO: Need UI spec for this failure case; for now just
                // show a generic error.
                showGenericErrorDialog(R.string.incall_error_call_failed, true);
                break;

            default:
                Log.w(LOG_TAG, "handleStartupError: unexpected status code " + status);
                showGenericErrorDialog(R.string.incall_error_call_failed, true);
                break;
        }
    }

    /**
     * Utility function to bring up a generic "error" dialog, and then bail
     * out of the in-call UI when the user hits OK (or the BACK button.)
     */
    private void showGenericErrorDialog(int resid, boolean isStartupError) {
        CharSequence msg = getResources().getText(resid);
        if (DBG) log("showGenericErrorDialog('" + msg + "')...");

        // create the clicklistener and cancel listener as needed.
        DialogInterface.OnClickListener clickListener;
        OnCancelListener cancelListener;
        if (isStartupError) {
            clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    bailOutAfterErrorDialog();
                }};
            cancelListener = new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    bailOutAfterErrorDialog();
                }};
        } else {
            clickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    delayedCleanupAfterDisconnect();
                }};
            cancelListener = new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    delayedCleanupAfterDisconnect();
                }};
        }

        // TODO: Consider adding a setTitle() call here (with some generic
        // "failure" title?)
        mGenericErrorDialog = new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, clickListener)
                .setOnCancelListener(cancelListener)
                .create();

        // When the dialog is up, completely hide the in-call UI
        // underneath (which is in a partially-constructed state).
        mGenericErrorDialog.getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        mGenericErrorDialog.show();
    }

    private void bailOutAfterErrorDialog() {
        if (DBG) log("bailOutAfterErrorDialog()...");
        if (mGenericErrorDialog != null) {
            if (DBG) log("bailOutAfterErrorDialog: DISMISSING mGenericErrorDialog.");
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }
        finish();
    }

    /**
     * Dismisses (and nulls out) all persistent Dialogs managed
     * by the InCallScreen.  Useful if (a) we're about to bring up
     * a dialog and want to pre-empt any currently visible dialogs,
     * or (b) as a cleanup step when the Activity is going away.
     */
    private void dismissAllDialogs() {
        if (DBG) log("dismissAllDialogs()...");

        // Note it's safe to dismiss() a dialog that's already dismissed.
        // (Even if the AlertDialog object(s) below are still around, it's
        // possible that the actual dialog(s) may have already been
        // dismissed by the user.)

        if (mMissingVoicemailDialog != null) {
            if (DBG) log("- DISMISSING mMissingVoicemailDialog.");
            mMissingVoicemailDialog.dismiss();
            mMissingVoicemailDialog = null;
        }
        if (mMmiStartedDialog != null) {
            if (DBG) log("- DISMISSING mMmiStartedDialog.");
            mMmiStartedDialog.dismiss();
            mMmiStartedDialog = null;
        }
        if (mGenericErrorDialog != null) {
            if (DBG) log("- DISMISSING mGenericErrorDialog.");
            mGenericErrorDialog.dismiss();
            mGenericErrorDialog = null;
        }
        if (mSuppServiceFailureDialog != null) {
            if (DBG) log("- DISMISSING mSuppServiceFailureDialog.");
            mSuppServiceFailureDialog.dismiss();
            mSuppServiceFailureDialog = null;
        }
        if (mWaitPromptDialog != null) {
            if (DBG) log("- DISMISSING mWaitPromptDialog.");
            mWaitPromptDialog.dismiss();
            mWaitPromptDialog = null;
        }
        if (mWildPromptDialog != null) {
            if (DBG) log("- DISMISSING mWildPromptDialog.");
            mWildPromptDialog.dismiss();
            mWildPromptDialog = null;
        }
    }


    //
    // Helper functions for answering incoming calls.
    //

    /**
     * Answer the ringing call.
     */
    /* package */ void internalAnswerCall() {
        if (DBG) log("internalAnswerCall()...");
        // if (DBG) PhoneUtils.dumpCallState(mPhone);
        PhoneUtils.answerCall(mPhone);  // Automatically holds the current active call,
                                        // if there is one
    }

    /**
     * Answer the ringing call *and* hang up the ongoing call.
     */
    /* package */ void internalAnswerAndEnd() {
        if (DBG) log("internalAnswerAndEnd()...");
        // if (DBG) PhoneUtils.dumpCallState(mPhone);
        PhoneUtils.answerAndEndActive(mPhone);
    }

    /**
     * Answer the ringing call, in the special case where both lines
     * are already in use.
     *
     * We "answer incoming, end ongoing" in this case, according to the
     * current UI spec.
     */
    /* package */ void internalAnswerCallBothLinesInUse() {
        if (DBG) log("internalAnswerCallBothLinesInUse()...");
        // if (DBG) PhoneUtils.dumpCallState(mPhone);

        PhoneUtils.answerAndEndActive(mPhone);
        // Alternatively, we could use
        //    PhoneUtils.answerAndEndHolding(mPhone);
        // here to end the on-hold call instead.
    }

    //
    // One last "answer" option I don't yet use here, but might eventually
    // need: "hang up the ringing call", aka "Don't answer":
    //     PhoneUtils.hangupRingingCall(phone);
    // With the current UI specs, this action only ever happens
    // from the ENDCALL button, so we don't have any UI elements
    // in *this* activity that need to do this.
    //


    //
    // "Manage conference" UI.
    //
    // TODO: There's a lot of code here, and this source file is already too large.
    // Consider moving all this code out to a separate class.
    //

    private void initManageConferencePanel() {
        if (DBG) log("initManageConferencePanel()...");
        if (mManageConferencePanel == null) {
            mManageConferencePanel = (ViewGroup) findViewById(R.id.manageConferencePanel);

            // set up the Conference Call chronometer
            mConferenceTime = (Chronometer) findViewById(R.id.manageConferencePanelHeader);
            mConferenceTime.setFormat(getString(R.string.caller_manage_header));

            // Create list of conference call widgets
            mConferenceCallList = new ViewGroup[MAX_CALLERS_IN_CONFERENCE];
            {
                final int[] viewGroupIdList = {R.id.caller0, R.id.caller1, R.id.caller2,
                        R.id.caller3, R.id.caller4};
                for (int i = 0; i < MAX_CALLERS_IN_CONFERENCE; i++) {
                    mConferenceCallList[i] = (ViewGroup) findViewById(viewGroupIdList[i]);
                }
            }

            mButtonManageConferenceDone =
                    (Button) findViewById(R.id.manage_done);
            mButtonManageConferenceDone.setOnClickListener(this);
        }
    }

    /**
     * Sets the current high-level "mode" of the in-call UI.
     *
     * NOTE: if newMode is CALL_ENDED, the caller is responsible for
     * posting a delayed DELAYED_CLEANUP_AFTER_DISCONNECT message, to make
     * sure the "call ended" state goes away after a couple of seconds.
     */
    private void setInCallScreenMode(InCallScreenMode newMode) {
        if (DBG) log("setInCallScreenMode: " + newMode);
        mInCallScreenMode = newMode;
        switch (mInCallScreenMode) {
            case MANAGE_CONFERENCE:
                if (!PhoneUtils.isConferenceCall(mForegroundCall)) {
                    Log.w(LOG_TAG, "MANAGE_CONFERENCE: no active conference call!");
                    // Hide the Manage Conference panel, return to NORMAL mode.
                    setInCallScreenMode(InCallScreenMode.NORMAL);
                    return;
                }
                List<Connection> connections = mForegroundCall.getConnections();
                // There almost certainly will be > 1 connection,
                // since isConferenceCall() just returned true.
                if ((connections == null) || (connections.size() <= 1)) {
                    Log.w(LOG_TAG,
                          "MANAGE_CONFERENCE: Bogus TRUE from isConferenceCall(); connections = "
                          + connections);
                    // Hide the Manage Conference panel, return to NORMAL mode.
                    setInCallScreenMode(InCallScreenMode.NORMAL);
                    return;
                }

                initManageConferencePanel();  // if necessary
                updateManageConferencePanel(connections);

                // The "Manage conference" UI takes up the full main frame,
                // replacing the inCallPanel and CallCard PopupWindow.
                mManageConferencePanel.setVisibility(View.VISIBLE);

                // start the chronometer.
                long callDuration = mForegroundCall.getEarliestConnection().getDurationMillis();
                mConferenceTime.setBase(SystemClock.elapsedRealtime() - callDuration);
                mConferenceTime.start();

                mInCallPanel.setVisibility(View.GONE);
                if (mSlidingCardManager != null) mSlidingCardManager.dismissPopup();

                break;

            case CALL_ENDED:
                // Display the CallCard (in the "Call ended" state)
                // and hide all other UI.

                if (mManageConferencePanel != null) {
                    mManageConferencePanel.setVisibility(View.GONE);
                    // stop the timer if the panel is hidden.
                    mConferenceTime.stop();
                }
                updateMenuButtonHint();  // Hide the Menu button hint

                if (mSlidingCardManager != null) {
                    // Show the sliding card, and hide any other in-call
                    // UI.  (The showPopup() method will automatically
                    // switch to "Call ended" mode if it notices the Phone
                    // is idle.)
                    mSlidingCardManager.showPopup();
                    mInCallPanel.setVisibility(View.INVISIBLE);
                } else {
                    // If there's no sliding card, then the CallCard is
                    // part of mInCallPanel.  Make sure it's visible.
                    mInCallPanel.setVisibility(View.VISIBLE);
                }

                break;

            case NORMAL:
                mInCallPanel.setVisibility(View.VISIBLE);
                if (mSlidingCardManager != null) mSlidingCardManager.showPopup();
                if (mManageConferencePanel != null) {
                    mManageConferencePanel.setVisibility(View.GONE);
                    // stop the timer if the panel is hidden.
                    mConferenceTime.stop();
                }
                break;
        }

        // Update the visibility of the DTMF dialer tab on any state
        // change.
        updateDialerDrawer();
    }

    /**
     * Updates the "Manage conference" UI based on the specified List of
     * connections.
     *
     * @param connections the List of connections belonging to
     *        the current foreground call; size must be greater than 1
     *        (or it wouldn't be a conference call in the first place.)
     */
    private void updateManageConferencePanel(List<Connection> connections) {
        mNumCallersInConference = connections.size();
        if (DBG) log("updateManageConferencePanel()... num connections in conference = "
                     + mNumCallersInConference);

        // Can we give the user the option to separate out ("go private with") a single
        // caller from this conference?
        final boolean hasActiveCall = !mForegroundCall.isIdle();
        final boolean hasHoldingCall = !mBackgroundCall.isIdle();
        boolean canSeparate = !(hasActiveCall && hasHoldingCall);

        for (int i = 0; i < MAX_CALLERS_IN_CONFERENCE; i++) {
            if (i < mNumCallersInConference) {
                // Fill in the row in the UI for this caller.
                Connection connection = (Connection) connections.get(i);
                updateManageConferenceRow(i, connection, canSeparate);
            } else {
                // Blank out this row in the UI
                updateManageConferenceRow(i, null, false);
            }
        }
    }

    /**
     * Checks if the "Manage conference" UI needs to be updated.
     * If the state of the current conference call has changed
     * since our previous call to updateManageConferencePanel()),
     * do a fresh update.
     */
    private void updateManageConferencePanelIfNecessary() {
        if (DBG) log("updateManageConferencePanel: mForegroundCall " + mForegroundCall + "...");

        List<Connection> connections = mForegroundCall.getConnections();
        if (connections == null) {
            if (DBG) log("==> no connections on foreground call!");
            // Hide the Manage Conference panel, return to NORMAL mode.
            setInCallScreenMode(InCallScreenMode.NORMAL);
            InCallInitStatus status = syncWithPhoneState();
            if (status != InCallInitStatus.SUCCESS) {
                Log.w(LOG_TAG, "- syncWithPhoneState failed! status = " + status);
                // We shouldn't even be in the in-call UI in the first
                // place, so bail out:
                if (DBG) log("- bailing out...");
                finish();
                return;
            }
            return;
        }

        int numConnections = connections.size();
        if (numConnections <= 1) {
            if (DBG) log("==> foreground call no longer a conference!");
            // Hide the Manage Conference panel, return to NORMAL mode.
            setInCallScreenMode(InCallScreenMode.NORMAL);
            InCallInitStatus status = syncWithPhoneState();
            if (status != InCallInitStatus.SUCCESS) {
                Log.w(LOG_TAG, "- syncWithPhoneState failed! status = " + status);
                // We shouldn't even be in the in-call UI in the first
                // place, so bail out:
                if (DBG) log("- bailing out...");
                finish();
                return;
            }
            return;
        }
        if (numConnections != mNumCallersInConference) {
            if (DBG) log("==> Conference size has changed; need to rebuild UI!");
            updateManageConferencePanel(connections);
            return;
        }
    }

    /**
     * Updates a single row of the "Manage conference" UI.  (One row in this
     * UI represents a single caller in the conference.)
     *
     * @param i the row to update
     * @param connection the Connection corresponding to this caller.
     *        If null, that means this is an "empty slot" in the conference,
     *        so hide this row in the UI.
     * @param canSeparate if true, show a "Separate" (i.e. "Private") button
     *        on this row in the UI.
     */
    private void updateManageConferenceRow(final int i,
                                           final Connection connection,
                                           boolean canSeparate) {
        if (DBG) log("updateManageConferenceRow(" + i + ")...  connection = " + connection);

        if (connection != null) {
            // Activate this row of the Manage conference panel:
            mConferenceCallList[i].setVisibility(View.VISIBLE);

            // get the relevant children views
            ImageButton endButton = (ImageButton) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerDisconnect);
            ImageButton separateButton = (ImageButton) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerSeparate);
            TextView nameTextView = (TextView) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerName);
            TextView numberTextView = (TextView) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerNumber);
            TextView numberTypeTextView = (TextView) mConferenceCallList[i].findViewById(
                    R.id.conferenceCallerNumberType);

            if (DBG) log("- button: " + endButton + ", nameTextView: " + nameTextView);

            // Hook up this row's buttons.
            View.OnClickListener endThisConnection = new View.OnClickListener() {
                    public void onClick(View v) {
                        endConferenceConnection(i, connection);
                    }
                };
            endButton.setOnClickListener(endThisConnection);
            //
            if (canSeparate) {
                View.OnClickListener separateThisConnection = new View.OnClickListener() {
                        public void onClick(View v) {
                            separateConferenceConnection(i, connection);
                        }
                    };
                separateButton.setOnClickListener(separateThisConnection);
                separateButton.setVisibility(View.VISIBLE);
            } else {
                separateButton.setVisibility(View.INVISIBLE);
            }

            // Name/number for this caller.
            // TODO: need to deal with private or blocked caller id?
            PhoneUtils.CallerInfoToken info = PhoneUtils.startGetCallerInfo(this, connection,
                    this, mConferenceCallList[i]);

            // display the CallerInfo.
            displayCallerInfoForConferenceRow (info.currentInfo, nameTextView,
                    numberTypeTextView, numberTextView);
        } else {
            // Disable this row of the Manage conference panel:
            mConferenceCallList[i].setVisibility(View.GONE);
        }
    }

    /**
     * Implemented for CallerInfoAsyncQuery.OnQueryCompleteListener interface.
     * refreshes the nameTextView when called.
     */
    public void onQueryComplete(int token, Object cookie, CallerInfo ci){
        if (DBG) log("callerinfo query complete, updating UI.");

        // get the viewgroup (conference call list item) and make it visible
        ViewGroup vg = (ViewGroup) cookie;
        vg.setVisibility(View.VISIBLE);

        // update the list item with this information.
        displayCallerInfoForConferenceRow (ci,
                (TextView) vg.findViewById(R.id.conferenceCallerName),
                (TextView) vg.findViewById(R.id.conferenceCallerNumberType),
                (TextView) vg.findViewById(R.id.conferenceCallerNumber));
    }

    /**
     * Helper function to fill out the Conference Call(er) information
     * for each item in the "Manage Conference Call" list.
     */
    private final void displayCallerInfoForConferenceRow(CallerInfo ci, TextView nameTextView,
            TextView numberTypeTextView, TextView numberTextView) {

        // gather the correct name and number information.
        String callerName = "";
        String callerNumber = "";
        String callerNumberType = "";
        if (ci != null) {
            callerName = ci.name;
            if (TextUtils.isEmpty(callerName)) {
                callerName = ci.phoneNumber;
                if (TextUtils.isEmpty(callerName)) {
                    callerName = getString(R.string.unknown);
                }
            } else {
                callerNumber = ci.phoneNumber;
                callerNumberType = ci.phoneLabel;
            }
        }

        // set the caller name
        nameTextView.setText(callerName);

        // set the caller number in subscript, or make the field disappear.
        if (TextUtils.isEmpty(callerNumber)) {
            numberTextView.setVisibility(View.GONE);
            numberTypeTextView.setVisibility(View.GONE);
        } else {
            numberTextView.setVisibility(View.VISIBLE);
            numberTextView.setText(callerNumber);
            numberTypeTextView.setVisibility(View.VISIBLE);
            numberTypeTextView.setText(callerNumberType);
        }
    }

    /**
     * Ends the specified connection on a conference call.  This method is
     * run (via a closure containing a row index and Connection) when the
     * user clicks the "End" button on a specific row in the Manage
     * conference UI.
     */
    private void endConferenceConnection(int i, Connection connection) {
        if (DBG) log("===> ENDING conference connection " + i
                     + ": Connection " + connection);
        // The actual work of ending the connection:
        PhoneUtils.hangup(connection);
        // No need to manually update the "Manage conference" UI here;
        // that'll happen automatically very soon (when we get the
        // onDisconnect() callback triggered by this hangup() call.)
    }

    /**
     * Separates out the specified connection on a conference call.  This
     * method is run (via a closure containing a row index and Connection)
     * when the user clicks the "Separate" (i.e. "Private") button on a
     * specific row in the Manage conference UI.
     */
    private void separateConferenceConnection(int i, Connection connection) {
        if (DBG) log("===> SEPARATING conference connection " + i
                     + ": Connection " + connection);

        PhoneUtils.separateCall(connection);

        // Note that separateCall() automagically makes the
        // newly-separated call into the foreground call (which is the
        // desired UI), so there's no need to do any further
        // call-switching here.
        // There's also no need to manually update (or hide) the "Manage
        // conference" UI; that'll happen on its own in a moment (when we
        // get the phone state change event triggered by the call to
        // separateCall().)
    }

    /**
     * Updates the DTMF dialer tab based on the current state of the phone
     * and/or the current InCallScreenMode.
     */
    private void updateDialerDrawer() {
        if (mDialerDrawer != null) {

            // The sliding drawer tab is visible only in portrait mode,
            // and only when the conditions in okToDialDTMFTones() pass.
            int visibility = View.GONE;
            if (!ConfigurationHelper.isLandscape() && okToDialDTMFTones()) {
                visibility = View.VISIBLE;
            }
            mDialerDrawer.setVisibility(visibility);
        }
    }

    /**
     * Determines when we can dial DTMF tones.
     */
    private boolean okToDialDTMFTones() {
        final boolean hasRingingCall = !mRingingCall.isIdle();
        final Call.State fgCallState = mForegroundCall.getState();

        // We're allowed to send DTMF tones when there's an ACTIVE
        // foreground call, and not when an incoming call is ringing
        // (since DTMF tones are useless in that state), or if the
        // Manage Conference UI is visible (since the tab interferes
        // with the "Back to call" button.)

        // We can also dial while in ALERTING state because there are
        // some connections that never update to an ACTIVE state (no
        // indication from the network).
        boolean canDial =
            (fgCallState == Call.State.ACTIVE || fgCallState == Call.State.ALERTING)
            && !hasRingingCall
            && (mInCallScreenMode != InCallScreenMode.MANAGE_CONFERENCE);

        if (DBG) log ("[okToDialDTMFTones] foreground state: " + fgCallState +
                ", ringing state: " + hasRingingCall +
                ", call screen mode: " + mInCallScreenMode +
                ", result: " + canDial);

        return canDial;
    }

    /**
     * Helper class to manage the (small number of) manual layout and UI
     * changes needed by the in-call UI when switching between landscape
     * and portrait mode.
     *
     * TODO: Ideally, all this information should come directly from
     * resources, with alternate sets of resources for for different
     * configurations (like alternate layouts under res/layout-land
     * or res/layout-finger.)
     *
     * But for now, we don't use any alternate resources.  Instead, the
     * resources under res/layout are hardwired for portrait mode, and we
     * use this class's applyConfigurationToLayout() method to reach into
     * our View hierarchy and manually patch up anything that needs to be
     * different for landscape mode.
     */
    /* package */ static class ConfigurationHelper {
        /** This class is never instantiated. */
        private ConfigurationHelper() {
        }

        // "Configuration constants" set by initConfiguration()
        static int sOrientation = Configuration.ORIENTATION_UNDEFINED;
        static int sPopupWidth, sPopupHeight;

        static boolean isLandscape() {
            return sOrientation == Configuration.ORIENTATION_LANDSCAPE;
        }

        /**
         * Initializes the "Configuration constants" based on the
         * specified configuration.
         */
        static void initConfiguration(Configuration config) {
            if (DBG) Log.d(LOG_TAG, "[InCallScreen.ConfigurationHelper] "
                           + "initConfiguration(" + config + ")...");

            sOrientation = config.orientation;
            sPopupWidth =
                    (isLandscape())
                    ? SlidingCardManager.POPUP_WINDOW_WIDTH_LANDSCAPE
                    : SlidingCardManager.POPUP_WINDOW_WIDTH_PORTRAIT;
            sPopupHeight =
                    (isLandscape())
                    ? SlidingCardManager.POPUP_WINDOW_HEIGHT_LANDSCAPE
                    : SlidingCardManager.POPUP_WINDOW_HEIGHT_PORTRAIT;
        }

        /**
         * Updates the InCallScreen's View hierarchy, applying any
         * necessary changes given the current configuration.
         */
        static void applyConfigurationToLayout(InCallScreen inCallScreen) {
            if (sOrientation == Configuration.ORIENTATION_UNDEFINED) {
                throw new IllegalStateException("need to call initConfiguration first");
            }

            // Our layout resources describe the *portrait mode* layout of
            // the Phone UI (see the TODO above in the doc comment for
            // the ConfigurationHelper class.)  So if we're in landscape
            // mode now, reach into our View hierarchy and update the
            // (few) layout params that need to be different.
            if (isLandscape()) {

                // Update PopupWindow-related stuff
                if (inCallScreen.mSlidingCardManager != null) {
                    inCallScreen.mSlidingCardManager.updateForLandscapeMode();
                }

                // Update CallCard-related stuff
                inCallScreen.mCallCard.updateForLandscapeMode();

                // Use the landscape version of the main frame "slide
                // track" background image (only if sliding is enabled).
                if (inCallScreen.mSlidingCardManager != null) {
                    inCallScreen.mMainFrame.setBackgroundResource(
                            R.drawable.background_frame_landscape);
                }
            }
        }
    }

    /**
     * @return true if we're in restricted / emergency dialing only mode.
     */
    public boolean isPhoneStateRestricted() {
        // TODO:  This needs to work IN TANDEM with the KeyGuardViewMediator Code.
        // Right now, it looks like the mInputRestricted flag is INTERNAL to the
        // KeyGuardViewMediator and SPECIFICALLY set to be FALSE while the emergency
        // phone call is being made, to allow for input into the InCallScreen.
        // Having the InCallScreen judge the state of the device from this flag
        // becomes meaningless since it is always false for us.  The mediator should
        // have an additional API to let this app know that it should be restricted.
        return ((mPhone.getServiceState().getState() == ServiceState.STATE_EMERGENCY_ONLY) ||
                (mPhone.getServiceState().getState() == ServiceState.STATE_OUT_OF_SERVICE) ||
                (PhoneApp.getInstance().getKeyguardManager().inKeyguardRestrictedInputMode()));
    }

    //
    // In-call menu UI
    //

    /**
     * Override onCreatePanelView(), in order to get complete control
     * over the UI that comes up when the user presses MENU.
     *
     * This callback allows me to return a totally custom View hierarchy
     * (with custom layout and custom "item" views) to be shown instead
     * of a standard android.view.Menu hierarchy.
     *
     * This gets called (with featureId == FEATURE_OPTIONS_PANEL) every
     * time we need to bring up the menu.  (And in cases where we return
     * non-null, that means that the "standard" menu callbacks
     * onCreateOptionsMenu() and onPrepareOptionsMenu() won't get called
     * at all.)
     */
    @Override
    public View onCreatePanelView(int featureId) {
        if (DBG) log("onCreatePanelView(featureId = " + featureId + ")...");

        // We only want this special behavior for the "options panel"
        // feature (i.e. the standard menu triggered by the MENU button.)
        if (featureId != Window.FEATURE_OPTIONS_PANEL) {
            return null;
        }

        // TODO: May need to revisit the wake state here if this needs to be
        // tweaked.

        // Make sure there are no pending messages to *dismiss* the menu.
        mHandler.removeMessages(DISMISS_MENU);

        if (mInCallMenu == null) {
            if (DBG) log("onCreatePanelView: creating mInCallMenu (first time)...");
            mInCallMenu = new InCallMenu(this);
            mInCallMenu.initMenu();
        }

        boolean okToShowMenu = mInCallMenu.updateItems(mPhone);
        return okToShowMenu ? mInCallMenu.getView() : null;
    }

    /**
     * Dismisses the menu panel (see onCreatePanelView().)
     *
     * @param dismissImmediate If true, hide the panel immediately.
     *            If false, leave the menu visible onscreen for
     *            a brief interval before dismissing it (so the
     *            user can see the state change resulting from
     *            his original click.)
     */
    /* package */ void dismissMenu(boolean dismissImmediate) {
        if (DBG) log("dismissMenu(immediate = " + dismissImmediate + ")...");

        if (dismissImmediate) {
            closeOptionsMenu();
        } else {
            mHandler.removeMessages(DISMISS_MENU);
            Message message = Message.obtain(mHandler, DISMISS_MENU);
            mHandler.sendMessageDelayed(message, MENU_DISMISS_DELAY);
            // This will result in a dismissMenu(true) call shortly.
        }
    }

    /**
     * Override onPanelClosed() to capture the panel closing event,
     * allowing us to set the poke lock correctly whenever the option
     * menu panel goes away.
     */
    @Override
    public void onPanelClosed(int featureId, Menu menu) {
        if (DBG) log("onPanelClosed(featureId = " + featureId + ")...");

        // We only want this special behavior for the "options panel"
        // feature (i.e. the standard menu triggered by the MENU button.)
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            // TODO: May need to return to the original wake state here
            // if onCreatePanelView ends up changing the wake state.
        }

        super.onPanelClosed(featureId, menu);
    }

    //
    // Bluetooth helper methods.
    //
    // - BluetoothDevice is the Bluetooth system service
    //   (Context.BLUETOOTH_SERVICE).  If getSystemService() returns null
    //   then the device is not BT capable.  Use BluetoothDevice.isEnabled()
    //   to see if BT is enabled on the device.
    //
    // - BluetoothHeadset is the API for the control connection to a
    //   Bluetooth Headset.  This lets you completely connect/disconnect a
    //   headset (which we don't do from the Phone UI!) but also lets you
    //   get the address of the currently active headset and see whether
    //   it's currently connected.
    //
    // - BluetoothHandsfree is the API to control the audio connection to
    //   a bluetooth headset. We use this API to switch the headset on and
    //   off when the user presses the "Bluetooth" button.
    //   Our BluetoothHandsfree instance (mBluetoothHandsfree) is created
    //   by the PhoneApp and will be null if the device is not BT capable.
    //

    /**
     * @return true if the Bluetooth on/off switch in the UI should be
     *         available to the user (i.e. if the device is BT-capable
     *         and a headset is connected.)
     */
    /* package */ boolean isBluetoothAvailable() {
        if (DBG) log("isBluetoothAvailable()...");
        if (mBluetoothHandsfree == null) {
            // Device is not BT capable.
            if (DBG) log("  ==> FALSE (not BT capable)");
            return false;
        }

        // There's no need to ask the Bluetooth system service if BT is enabled:
        //
        //    BluetoothDevice bluetoothDevice =
        //            (BluetoothDevice) getSystemService(Context.BLUETOOTH_SERVICE);
        //    if ((bluetoothDevice == null) || !bluetoothDevice.isEnabled()) {
        //        if (DBG) log("  ==> FALSE (BT not enabled)");
        //        return false;
        //    }
        //    if (DBG) log("  - BT enabled!  device name " + bluetoothDevice.getName()
        //                 + ", address " + bluetoothDevice.getAddress());
        //
        // ...since we already have a BluetoothHeadset instance.  We can just
        // call isConnected() on that, and assume it'll be false if BT isn't
        // enabled at all.

        // Check if there's a connected headset, using the BluetoothHeadset API.
        boolean isConnected = false;
        if (mBluetoothHeadset != null) {
            if (DBG) log("  - headset state = " + mBluetoothHeadset.getState());
            String headsetAddress = mBluetoothHeadset.getHeadsetAddress();
            if (DBG) log("  - headset address: " + headsetAddress);
            if (headsetAddress != null) {
                isConnected = mBluetoothHeadset.isConnected(headsetAddress);
                if (DBG) log("  - isConnected: " + isConnected);
            }
        }

        if (DBG) log("  ==> " + isConnected);
        return isConnected;
    }

    /**
     * @return true if a BT device is available, and its audio is currently connected.
     */
    /* package */ boolean isBluetoothAudioConnected() {
        if (mBluetoothHandsfree == null) {
            if (DBG) log("isBluetoothAudioConnected: ==> FALSE (null mBluetoothHandsfree)");
            return false;
        }
        boolean isAudioOn = mBluetoothHandsfree.isAudioOn();
        if (DBG) log("isBluetoothAudioConnected: ==> isAudioOn = " + isAudioOn);
        return isAudioOn;
    }

    /**
     * Helper method used to control the state of the green LED in the
     * "Bluetooth" menu item.
     *
     * @return true if a BT device is available and its audio is currently connected,
     *              <b>or</b> if we issued a BluetoothHandsfree.userWantsAudioOn()
     *              call within the last 5 seconds (which presumably means
     *              that the BT audio connection is currently being set
     *              up, and will be connected soon.)
     */
    /* package */ boolean isBluetoothAudioConnectedOrPending() {
        if (isBluetoothAudioConnected()) {
            if (DBG) log("isBluetoothAudioConnectedOrPending: ==> TRUE (really connected)");
            return true;
        }

        // If we issued a userWantsAudioOn() call "recently enough", even
        // if BT isn't actually connected yet, let's still pretend BT is
        // on.  This is how we make the green LED in the menu item turn on
        // right away.
        if (mBluetoothConnectionPending) {
            long timeSinceRequest =
                    SystemClock.elapsedRealtime() - mBluetoothConnectionRequestTime;
            if (timeSinceRequest < 5000 /* 5 seconds */) {
                if (DBG) log("isBluetoothAudioConnectedOrPending: ==> TRUE (requested "
                             + timeSinceRequest + " msec ago)");
                return true;
            } else {
                if (DBG) log("isBluetoothAudioConnectedOrPending: ==> FALSE (request too old: "
                             + timeSinceRequest + " msec ago)");
                mBluetoothConnectionPending = false;
                return false;
            }
        }

        if (DBG) log("isBluetoothAudioConnectedOrPending: ==> FALSE");
        return false;
    }

    /* package */ void connectBluetoothAudio() {
        if (DBG) log("connectBluetoothAudio()...");
        if (mBluetoothHandsfree != null) {
            mBluetoothHandsfree.userWantsAudioOn();
        }

        // Watch out: The bluetooth connection doesn't happen instantly;
        // the userWantsAudioOn() call returns instantly but does its real
        // work in another thread.  Also, in practice the BT connection
        // takes longer than MENU_DISMISS_DELAY to complete(!) so we need
        // a little trickery here to make the menu item's green LED update
        // instantly.
        // (See isBluetoothAudioConnectedOrPending() above.)
        mBluetoothConnectionPending = true;
        mBluetoothConnectionRequestTime = SystemClock.elapsedRealtime();
    }

    /* package */ void disconnectBluetoothAudio() {
        if (DBG) log("disconnectBluetoothAudio()...");
        if (mBluetoothHandsfree != null) {
            mBluetoothHandsfree.userWantsAudioOff();
        }
        mBluetoothConnectionPending = false;
    }


    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
