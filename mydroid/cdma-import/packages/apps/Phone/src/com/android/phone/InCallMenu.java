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

package com.android.phone;

import android.content.Context;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.Phone;
import android.util.Log;
import android.view.ContextThemeWrapper;


/**
 * Helper class to manage the options menu for the InCallScreen.
 *
 * This class is the "Model" (in M-V-C nomenclature) for the in-call menu;
 * it knows about all possible menu items, and contains logic to determine
 * the current state and enabledness of each item based on the state of
 * the Phone.
 *
 * The corresponding View classes are InCallMenuView, which is used purely
 * to lay out and draw the menu, and InCallMenuItemView, which is the View
 * for a single item.
 */
class InCallMenu {
    private static final String LOG_TAG = "PHONE/InCallMenu";
    private static final boolean DBG = false;

    /**
     * Reference to the InCallScreen activity that owns us.  This will be
     * null if we haven't been initialized yet *or* after the InCallScreen
     * activity has been destroyed.
     */
    private InCallScreen mInCallScreen;

    /**
     * Our corresponding View class.
     */
    private InCallMenuView mInCallMenuView;

    /**
     * All possible menu items (see initMenu().)
     */
    InCallMenuItemView mManageConference;
    InCallMenuItemView mEndCall;
    InCallMenuItemView mAddCall;
    InCallMenuItemView mSwapCalls;
    InCallMenuItemView mMergeCalls;
    InCallMenuItemView mBluetooth;
    InCallMenuItemView mSpeaker;
    InCallMenuItemView mMute;
    InCallMenuItemView mHold;
    InCallMenuItemView mAnswerAndHold;
    InCallMenuItemView mAnswerAndEnd;

    InCallMenu(InCallScreen inCallScreen) {
        if (DBG) log("InCallMenu constructor...");
        mInCallScreen = inCallScreen;
    }

    /**
     * Null out our reference to the InCallScreen activity.
     * This indicates that the InCallScreen activity has been destroyed.
     */
    void clearInCallScreenReference() {
        mInCallScreen = null;
        if (mInCallMenuView != null) mInCallMenuView.clearInCallScreenReference();
    }

    /* package */ InCallMenuView getView() {
        return mInCallMenuView;
    }

    /**
     * Initializes the in-call menu by creating a new InCallMenuView,
     * creating all possible menu items, and loading them into the
     * InCallMenuView.
     *
     * The only initialization of the individual items we do here is
     * one-time stuff, like setting the ID and click listener, or calling
     * setIndicatorVisible() for buttons that have a green LED, or calling
     * setText() for buttons whose text never changes.  The actual
     * *current* state and enabledness of each item is set in
     * updateItems().
     */
    /* package */ void initMenu() {
        if (DBG) log("initMenu()...");

        // Explicitly use the "icon menu" theme for the Views we create.
        Context wrappedContext = new ContextThemeWrapper(
                mInCallScreen,
                com.android.internal.R.style.Theme_IconMenu);

        mInCallMenuView = new InCallMenuView(wrappedContext, mInCallScreen);

        //
        // Create all possible InCallMenuView objects.
        //

        mManageConference = new InCallMenuItemView(wrappedContext);
        mManageConference.setId(R.id.menuManageConference);
        mManageConference.setOnClickListener(mInCallScreen);
        mManageConference.setText(R.string.menu_manageConference);
        mManageConference.setIconResource(R.drawable.ic_menu_manage_conference);

        mEndCall = new InCallMenuItemView(wrappedContext);
        mEndCall.setId(R.id.menuEndCall);
        mEndCall.setOnClickListener(mInCallScreen);
        mEndCall.setText(R.string.menu_endCall);
        mEndCall.setIconResource(R.drawable.ic_menu_end_call);

        mAddCall = new InCallMenuItemView(wrappedContext);
        mAddCall.setId(R.id.menuAddCall);
        mAddCall.setOnClickListener(mInCallScreen);
        mAddCall.setText(R.string.menu_addCall);
        mAddCall.setIconResource(R.drawable.ic_menu_add);

        mSwapCalls = new InCallMenuItemView(wrappedContext);
        mSwapCalls.setId(R.id.menuSwapCalls);
        mSwapCalls.setOnClickListener(mInCallScreen);
        mSwapCalls.setText(R.string.menu_swapCalls);
        mSwapCalls.setIconResource(R.drawable.ic_menu_swap_calls);

        mMergeCalls = new InCallMenuItemView(wrappedContext);
        mMergeCalls.setId(R.id.menuMergeCalls);
        mMergeCalls.setOnClickListener(mInCallScreen);
        mMergeCalls.setText(R.string.menu_mergeCalls);
        mMergeCalls.setIconResource(R.drawable.ic_menu_merge_calls);

        // TODO: Icons for menu items we don't have yet:
        //   R.drawable.ic_menu_answer_call
        //   R.drawable.ic_menu_silence_ringer

        mBluetooth = new InCallMenuItemView(wrappedContext);
        mBluetooth.setId(R.id.menuBluetooth);
        mBluetooth.setOnClickListener(mInCallScreen);
        mBluetooth.setText(R.string.menu_bluetooth);
        mBluetooth.setIndicatorVisible(true);

        mSpeaker = new InCallMenuItemView(wrappedContext);
        mSpeaker.setId(R.id.menuSpeaker);
        mSpeaker.setOnClickListener(mInCallScreen);
        mSpeaker.setText(R.string.menu_speaker);
        mSpeaker.setIndicatorVisible(true);

        mMute = new InCallMenuItemView(wrappedContext);
        mMute.setId(R.id.menuMute);
        mMute.setOnClickListener(mInCallScreen);
        mMute.setText(R.string.menu_mute);
        mMute.setIndicatorVisible(true);

        mHold = new InCallMenuItemView(wrappedContext);
        mHold.setId(R.id.menuHold);
        mHold.setOnClickListener(mInCallScreen);
        mHold.setText(R.string.menu_hold);
        mHold.setIndicatorVisible(true);

        mAnswerAndHold = new InCallMenuItemView(wrappedContext);
        mAnswerAndHold.setId(R.id.menuAnswerAndHold);
        mAnswerAndHold.setOnClickListener(mInCallScreen);
        mAnswerAndHold.setText(R.string.menu_answerAndHold);

        mAnswerAndEnd = new InCallMenuItemView(wrappedContext);
        mAnswerAndEnd.setId(R.id.menuAnswerAndEnd);
        mAnswerAndEnd.setOnClickListener(mInCallScreen);
        mAnswerAndEnd.setText(R.string.menu_answerAndEnd);

        //
        // Load all the items into the correct "slots" in the InCallMenuView.
        //
        // Row 0 is the topmost row onscreen, item 0 is the leftmost item in a row.
        //
        // Individual items may be disabled or hidden, but never move between
        // rows or change their order within a row.
        //
        // TODO: these items and their layout ought be specifiable
        // entirely in XML (just like we currently do with res/menu/*.xml
        // files.)
        //

        // Row 0:
        mInCallMenuView.addItemView(mManageConference, 0);

        // Row 1:
        mInCallMenuView.addItemView(mSwapCalls, 1);
        mInCallMenuView.addItemView(mMergeCalls, 1);
        mInCallMenuView.addItemView(mAddCall, 1);
        mInCallMenuView.addItemView(mEndCall, 1);

        // Row 2:
        // In this row we see *either*  bluetooth/speaker/mute/hold
        // *or* answerAndHold/answerAndEnd, but never all 6 together.
        mInCallMenuView.addItemView(mHold, 2);
        mInCallMenuView.addItemView(mMute, 2);
        mInCallMenuView.addItemView(mSpeaker, 2);
        mInCallMenuView.addItemView(mBluetooth, 2);
        mInCallMenuView.addItemView(mAnswerAndHold, 2);
        mInCallMenuView.addItemView(mAnswerAndEnd, 2);

        mInCallMenuView.dumpState();
    }

    /**
     * Updates the enabledness and visibility of all items in the
     * InCallMenuView based on the current state of the Phone.
     *
     * This is called every time we need to display the menu, right before
     * it becomes visible.
     *
     * @return true if we successfully updated the items and it's OK
     *         to go ahead and show the menu, or false if
     *         we shouldn't show the menu at all.
     */
    /* package */ boolean updateItems(Phone phone) {
        if (DBG) log("updateItems()...");
        // if (DBG) PhoneUtils.dumpCallState(phone);

        // If the phone is totally idle (like in the "call ended" state)
        // there's no menu at all.
        if (phone.getState() == Phone.State.IDLE) {
            if (DBG) log("- Phone is idle!  Don't show the menu...");
            return false;
        }

        final boolean hasRingingCall = !phone.getRingingCall().isIdle();
        final boolean hasActiveCall = !phone.getForegroundCall().isIdle();
        final Call.State fgCallState = phone.getForegroundCall().getState();
        final boolean hasHoldingCall = !phone.getBackgroundCall().isIdle();

        // Special cases when an incoming call is ringing.
        if (hasRingingCall) {
            // In the "call waiting" state, show ONLY the "answer & end"
            // and "answer & hold" buttons, and nothing else.
            // TODO: be sure to test this for "only one line in use and it's
            // active" AND for "only one line in use and it's on hold".
            if (hasActiveCall && !hasHoldingCall) {
                mAnswerAndHold.setVisible(true);
                mAnswerAndHold.setEnabled(true);
                mAnswerAndEnd.setVisible(true);
                mAnswerAndEnd.setEnabled(true);

                mManageConference.setVisible(false);
                mEndCall.setVisible(false);
                mAddCall.setVisible(false);
                mSwapCalls.setVisible(false);
                mMergeCalls.setVisible(false);
                mBluetooth.setVisible(false);
                mSpeaker.setVisible(false);
                mMute.setVisible(false);
                mHold.setVisible(false);

                // Done updating the individual items.
                // The last step is to tell the InCallMenuView to update itself
                // based on any visibility changes that just happened.
                mInCallMenuView.updateVisibility();

                return true;
            } else {
                // If there's an incoming ringing call but there aren't
                // any "special actions" to take, don't show a menu at all.
                return false;
            }
        }

        // TODO: double-check if any items here need to be disabled based on:
        //   boolean keyguardRestricted = mInCallScreen.isPhoneStateRestricted();

        // Manage conference: only visible if the foreground call is a conference call.
        boolean canManageConference =
                PhoneUtils.isConferenceCall(phone.getForegroundCall());
        mManageConference.setVisible(canManageConference);
        mManageConference.setEnabled(canManageConference);

        // "End call": this button has no state and is always visible.
        // It's also always enabled.  (Actually it *would* need to be
        // disabled if the phone was totally idle, but the entire in-call
        // menu is already disabled in that case (see above.))
        mEndCall.setVisible(true);
        mEndCall.setEnabled(true);

        // "Add call"
        mAddCall.setVisible(true);
        mAddCall.setEnabled(PhoneUtils.okToAddCall(phone));

        // Swap / merge calls
        boolean canSwap = PhoneUtils.okToSwapCalls(phone);
        boolean canMerge = PhoneUtils.okToMergeCalls(phone);
        mSwapCalls.setVisible(true);
        mSwapCalls.setEnabled(canSwap);
        mMergeCalls.setVisible(true);
        mMergeCalls.setEnabled(canMerge);

        // "Bluetooth": always visible, only enabled if BT is available.
        updateBluetoothButton();

        // "Speaker": always visible.  Disabled if a wired headset is
        // plugged in, otherwise enabled (and indicates the current
        // speaker state.)
        mSpeaker.setVisible(true);
        if (PhoneApp.getInstance().isHeadsetPlugged()) {
            // Wired headset is present; Speaker button is meaningless.
            mSpeaker.setEnabled(false);
            mSpeaker.setIndicatorState(false);
        } else {
            // No wired headset; Speaker button is enabled and behaves normally.
            mSpeaker.setEnabled(true);
            boolean speakerOn = PhoneUtils.isSpeakerOn(mInCallScreen.getApplicationContext());
            mSpeaker.setIndicatorState(speakerOn);
        }

        // "Mute": only enabled when the foreground call is ACTIVE.
        // (It's meaningless while on hold, or while DIALING/ALERTING.)
        mMute.setVisible(true);
        boolean muteOn = PhoneUtils.getMute(phone);
        boolean canMute = (fgCallState == Call.State.ACTIVE);
        mMute.setIndicatorState(muteOn);
        mMute.setEnabled(canMute);

        // "Hold": "On hold" means that there's a holding call and
        // *no* foreground call.  (If there *is* a foreground call,
        // that's "two lines in use".)  "Hold" is disabled if both
        // lines are in use, or if the foreground call is non-idle and
        // in any state other than ACTIVE.
        mHold.setVisible(true);
        boolean onHold = hasHoldingCall && !hasActiveCall;
        boolean canHold = !((hasActiveCall && hasHoldingCall)
                            || (hasActiveCall && (fgCallState != Call.State.ACTIVE)));
        mHold.setIndicatorState(onHold);
        mHold.setEnabled(canHold);

        // "Answer & end" and "Answer & hold" are only useful
        // when there's an incoming ringing call (see above.)
        mAnswerAndHold.setVisible(false);
        mAnswerAndHold.setEnabled(false);
        mAnswerAndEnd.setVisible(false);
        mAnswerAndEnd.setEnabled(false);

        // Done updating the individual items.
        // The last step is to tell the InCallMenuView to update itself
        // based on any visibility changes that just happened.
        mInCallMenuView.updateVisibility();

        return true;
    }

    private void updateBluetoothButton() {
        mBluetooth.setVisible(true);
        if (mInCallScreen.isBluetoothAvailable()) {
            mBluetooth.setEnabled(true);
            boolean audioConnectedOrPending = mInCallScreen.isBluetoothAudioConnectedOrPending();
            mBluetooth.setIndicatorState(audioConnectedOrPending);
        } else {
            mBluetooth.setEnabled(false);
            mBluetooth.setIndicatorState(false);
        }
    }


    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
