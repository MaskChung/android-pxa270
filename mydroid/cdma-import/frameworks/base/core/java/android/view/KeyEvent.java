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

package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.KeyCharacterMap;
import android.view.KeyCharacterMap.KeyData;

/**
 * Contains constants for key events.
 */
public class KeyEvent implements Parcelable {
    // key codes
    public static final int KEYCODE_UNKNOWN         = 0;
    public static final int KEYCODE_SOFT_LEFT       = 1;
    public static final int KEYCODE_SOFT_RIGHT      = 2;
    public static final int KEYCODE_HOME            = 3;
    public static final int KEYCODE_BACK            = 4;
    public static final int KEYCODE_CALL            = 5;
    public static final int KEYCODE_ENDCALL         = 6;
    public static final int KEYCODE_0               = 7;
    public static final int KEYCODE_1               = 8;
    public static final int KEYCODE_2               = 9;
    public static final int KEYCODE_3               = 10;
    public static final int KEYCODE_4               = 11;
    public static final int KEYCODE_5               = 12;
    public static final int KEYCODE_6               = 13;
    public static final int KEYCODE_7               = 14;
    public static final int KEYCODE_8               = 15;
    public static final int KEYCODE_9               = 16;
    public static final int KEYCODE_STAR            = 17;
    public static final int KEYCODE_POUND           = 18;
    public static final int KEYCODE_DPAD_UP         = 19;
    public static final int KEYCODE_DPAD_DOWN       = 20;
    public static final int KEYCODE_DPAD_LEFT       = 21;
    public static final int KEYCODE_DPAD_RIGHT      = 22;
    public static final int KEYCODE_DPAD_CENTER     = 23;
    public static final int KEYCODE_VOLUME_UP       = 24;
    public static final int KEYCODE_VOLUME_DOWN     = 25;
    public static final int KEYCODE_POWER           = 26;
    public static final int KEYCODE_CAMERA          = 27;
    public static final int KEYCODE_CLEAR           = 28;
    public static final int KEYCODE_A               = 29;
    public static final int KEYCODE_B               = 30;
    public static final int KEYCODE_C               = 31;
    public static final int KEYCODE_D               = 32;
    public static final int KEYCODE_E               = 33;
    public static final int KEYCODE_F               = 34;
    public static final int KEYCODE_G               = 35;
    public static final int KEYCODE_H               = 36;
    public static final int KEYCODE_I               = 37;
    public static final int KEYCODE_J               = 38;
    public static final int KEYCODE_K               = 39;
    public static final int KEYCODE_L               = 40;
    public static final int KEYCODE_M               = 41;
    public static final int KEYCODE_N               = 42;
    public static final int KEYCODE_O               = 43;
    public static final int KEYCODE_P               = 44;
    public static final int KEYCODE_Q               = 45;
    public static final int KEYCODE_R               = 46;
    public static final int KEYCODE_S               = 47;
    public static final int KEYCODE_T               = 48;
    public static final int KEYCODE_U               = 49;
    public static final int KEYCODE_V               = 50;
    public static final int KEYCODE_W               = 51;
    public static final int KEYCODE_X               = 52;
    public static final int KEYCODE_Y               = 53;
    public static final int KEYCODE_Z               = 54;
    public static final int KEYCODE_COMMA           = 55;
    public static final int KEYCODE_PERIOD          = 56;
    public static final int KEYCODE_ALT_LEFT        = 57;
    public static final int KEYCODE_ALT_RIGHT       = 58;
    public static final int KEYCODE_SHIFT_LEFT      = 59;
    public static final int KEYCODE_SHIFT_RIGHT     = 60;
    public static final int KEYCODE_TAB             = 61;
    public static final int KEYCODE_SPACE           = 62;
    public static final int KEYCODE_SYM             = 63;
    public static final int KEYCODE_EXPLORER        = 64;
    public static final int KEYCODE_ENVELOPE        = 65;
    public static final int KEYCODE_ENTER         = 66;
    public static final int KEYCODE_DEL             = 67;
    public static final int KEYCODE_GRAVE           = 68;
    public static final int KEYCODE_MINUS           = 69;
    public static final int KEYCODE_EQUALS          = 70;
    public static final int KEYCODE_LEFT_BRACKET    = 71;
    public static final int KEYCODE_RIGHT_BRACKET   = 72;
    public static final int KEYCODE_BACKSLASH       = 73;
    public static final int KEYCODE_SEMICOLON       = 74;
    public static final int KEYCODE_APOSTROPHE      = 75;
    public static final int KEYCODE_SLASH           = 76;
    public static final int KEYCODE_AT              = 77;
    public static final int KEYCODE_NUM             = 78;
    public static final int KEYCODE_HEADSETHOOK     = 79;
    public static final int KEYCODE_FOCUS           = 80;   // *Camera* focus
    public static final int KEYCODE_PLUS            = 81;
    public static final int KEYCODE_MENU            = 82;
    public static final int KEYCODE_NOTIFICATION    = 83;
    public static final int KEYCODE_SEARCH          = 84;

    // NOTE: If you add a new keycode here you must also add it to:
    //  isSystem()
    //  include/ui/KeycodeLabels.h
    //  tools/puppet_master/PuppetMaster/nav_keys.py
    //  apps/common/res/values/attrs.xml
    //  commands/monkey/Monkey.java
    //  emulator?
    
    public static final int MAX_KEYCODE             = 84;

    /**
     * {@link #getAction} value: the key has been pressed down.
     */
    public static final int ACTION_DOWN             = 0;
    /**
     * {@link #getAction} value: the key has been released.
     */
    public static final int ACTION_UP               = 1;
    /**
     * {@link #getAction} value: multiple duplicate key events have
     * occurred in a row.  The {#link {@link #getRepeatCount()} method returns
     * the number of duplicates.
     */
    public static final int ACTION_MULTIPLE         = 2;

    /**
     * <p>This mask is used to check whether one of the ALT meta keys is pressed.</p>
     *
     * @see #isAltPressed()
     * @see #getMetaState()
     * @see #KEYCODE_ALT_LEFT
     * @see #KEYCODE_ALT_RIGHT
     */
    public static final int META_ALT_ON = 0x02;

    /**
     * <p>This mask is used to check whether the left ALT meta key is pressed.</p>
     *
     * @see #isAltPressed()
     * @see #getMetaState()
     * @see #KEYCODE_ALT_LEFT
     */
    public static final int META_ALT_LEFT_ON = 0x10;

    /**
     * <p>This mask is used to check whether the right the ALT meta key is pressed.</p>
     *
     * @see #isAltPressed()
     * @see #getMetaState()
     * @see #KEYCODE_ALT_RIGHT
     */
    public static final int META_ALT_RIGHT_ON = 0x20;

    /**
     * <p>This mask is used to check whether one of the SHIFT meta keys is pressed.</p>
     *
     * @see #isShiftPressed()
     * @see #getMetaState()
     * @see #KEYCODE_SHIFT_LEFT
     * @see #KEYCODE_SHIFT_RIGHT
     */
    public static final int META_SHIFT_ON = 0x1;

    /**
     * <p>This mask is used to check whether the left SHIFT meta key is pressed.</p>
     *
     * @see #isShiftPressed()
     * @see #getMetaState()
     * @see #KEYCODE_SHIFT_LEFT
     */
    public static final int META_SHIFT_LEFT_ON = 0x40;

    /**
     * <p>This mask is used to check whether the right SHIFT meta key is pressed.</p>
     *
     * @see #isShiftPressed()
     * @see #getMetaState()
     * @see #KEYCODE_SHIFT_RIGHT
     */
    public static final int META_SHIFT_RIGHT_ON = 0x80;

    /**
     * <p>This mask is used to check whether the SYM meta key is pressed.</p>
     *
     * @see #isSymPressed()
     * @see #getMetaState()
     */
    public static final int META_SYM_ON = 0x4;

    /**
     * This mask is set if the device woke because of this key event.
     */
    public static final int FLAG_WOKE_HERE = 0x1;
    
    /**
     * Get the character that is produced by putting accent on the character
     * c.
     * For example, getDeadChar('`', 'e') returns &egrave;.
     */
    public static int getDeadChar(int accent, int c) {
        return KeyCharacterMap.getDeadChar(accent, c);
    }
    
    private int mMetaState;
    private int mAction;
    private int mKeyCode;
    private int mScancode;
    private int mRepeatCount;
    private int mDeviceId;
    private int mFlags;
    private long mDownTime;
    private long mEventTime;

    public interface Callback {
        /**
         * Called when a key down event has occurred.
         * 
         * @param keyCode The value in event.getKeyCode().
         * @param event Description of the key event.
         * 
         * @return If you handled the event, return true.  If you want to allow
         *         the event to be handled by the next receiver, return false.
         */
        boolean onKeyDown(int keyCode, KeyEvent event);

        /**
         * Called when a key up event has occurred.
         * 
         * @param keyCode The value in event.getKeyCode().
         * @param event Description of the key event.
         * 
         * @return If you handled the event, return true.  If you want to allow
         *         the event to be handled by the next receiver, return false.
         */
        boolean onKeyUp(int keyCode, KeyEvent event);

        /**
         * Called when multiple down/up pairs of the same key have occurred
         * in a row.
         * 
         * @param keyCode The value in event.getKeyCode().
         * @param count Number of pairs as returned by event.getRepeatCount().
         * @param event Description of the key event.
         * 
         * @return If you handled the event, return true.  If you want to allow
         *         the event to be handled by the next receiver, return false.
         */
        boolean onKeyMultiple(int keyCode, int count, KeyEvent event);
    }

    /**
     * Create a new key event.
     * 
     * @param action Action code: either {@link #ACTION_DOWN},
     * {@link #ACTION_UP}, or {@link #ACTION_MULTIPLE}.
     * @param code The key code.
     */
    public KeyEvent(int action, int code) {
        mAction = action;
        mKeyCode = code;
        mRepeatCount = 0;
    }

    /**
     * Create a new key event.
     * 
     * @param downTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this key code originally went down.
     * @param eventTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this event happened.
     * @param action Action code: either {@link #ACTION_DOWN},
     * {@link #ACTION_UP}, or {@link #ACTION_MULTIPLE}.
     * @param code The key code.
     * @param repeat A repeat count for down events (> 0 if this is after the
     * initial down) or event count for multiple events.
     */
    public KeyEvent(long downTime, long eventTime, int action,
                    int code, int repeat) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeatCount = repeat;
    }

    /**
     * Create a new key event.
     * 
     * @param downTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this key code originally went down.
     * @param eventTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this event happened.
     * @param action Action code: either {@link #ACTION_DOWN},
     * {@link #ACTION_UP}, or {@link #ACTION_MULTIPLE}.
     * @param code The key code.
     * @param repeat A repeat count for down events (> 0 if this is after the
     * initial down) or event count for multiple events.
     * @param metaState Flags indicating which meta keys are currently pressed.
     */
    public KeyEvent(long downTime, long eventTime, int action,
                    int code, int repeat, int metaState) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeatCount = repeat;
        mMetaState = metaState;
    }

    /**
     * Create a new key event.
     * 
     * @param downTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this key code originally went down.
     * @param eventTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this event happened.
     * @param action Action code: either {@link #ACTION_DOWN},
     * {@link #ACTION_UP}, or {@link #ACTION_MULTIPLE}.
     * @param code The key code.
     * @param repeat A repeat count for down events (> 0 if this is after the
     * initial down) or event count for multiple events.
     * @param metaState Flags indicating which meta keys are currently pressed.
     * @param device The device ID that generated the key event.
     * @param scancode Raw device scan code of the event.
     */
    public KeyEvent(long downTime, long eventTime, int action,
                    int code, int repeat, int metaState,
                    int device, int scancode) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeatCount = repeat;
        mMetaState = metaState;
        mDeviceId = device;
        mScancode = scancode;
    }

    /**
     * Create a new key event.
     * 
     * @param downTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this key code originally went down.
     * @param eventTime The time (in {@link android.os.SystemClock#uptimeMillis})
     * at which this event happened.
     * @param action Action code: either {@link #ACTION_DOWN},
     * {@link #ACTION_UP}, or {@link #ACTION_MULTIPLE}.
     * @param code The key code.
     * @param repeat A repeat count for down events (> 0 if this is after the
     * initial down) or event count for multiple events.
     * @param metaState Flags indicating which meta keys are currently pressed.
     * @param device The device ID that generated the key event.
     * @param scancode Raw device scan code of the event.
     * @param flags The flags for this key event
     */
    public KeyEvent(long downTime, long eventTime, int action,
                    int code, int repeat, int metaState,
                    int device, int scancode, int flags) {
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = code;
        mRepeatCount = repeat;
        mMetaState = metaState;
        mDeviceId = device;
        mScancode = scancode;
        mFlags = flags;
    }

    /**
     * Copy an existing key event, modifying its time and repeat count.
     * 
     * @param origEvent The existing event to be copied.
     * @param eventTime The new event time
     * (in {@link android.os.SystemClock#uptimeMillis}) of the event.
     * @param newRepeat The new repeat count of the event.
     */
    public KeyEvent(KeyEvent origEvent, long eventTime, int newRepeat) {
        mDownTime = origEvent.mDownTime;
        mEventTime = eventTime;
        mAction = origEvent.mAction;
        mKeyCode = origEvent.mKeyCode;
        mRepeatCount = newRepeat;
        mMetaState = origEvent.mMetaState;
        mDeviceId = origEvent.mDeviceId;
        mScancode = origEvent.mScancode;
        mFlags = origEvent.mFlags;
    }

    /**
     * Don't use in new code, instead explicitly check
     * {@link #getAction()}.
     * 
     * @return If the action is ACTION_DOWN, returns true; else false.
     *
     * @deprecated
     * @hide
     */
    @Deprecated public final boolean isDown() {
        return mAction == ACTION_DOWN;
    }

    /**
     * Is this a system key?  System keys can not be used for menu shortcuts.
     * 
     * TODO: this information should come from a table somewhere.
     * TODO: should the dpad keys be here?  arguably, because they also shouldn't be menu shortcuts
     */
    public final boolean isSystem() {
        switch (mKeyCode) {
        case KEYCODE_MENU:
        case KEYCODE_SOFT_RIGHT:
        case KEYCODE_HOME:
        case KEYCODE_BACK:
        case KEYCODE_CALL:
        case KEYCODE_ENDCALL:
        case KEYCODE_VOLUME_UP:
        case KEYCODE_VOLUME_DOWN:
        case KEYCODE_POWER:
        case KEYCODE_HEADSETHOOK:
        case KEYCODE_CAMERA:
        case KEYCODE_FOCUS:
        case KEYCODE_SEARCH:
            return true;
        default:
            return false;
        }
    }


    /**
     * <p>Returns the state of the meta keys.</p>
     *
     * @return an integer in which each bit set to 1 represents a pressed
     *         meta key
     *
     * @see #isAltPressed()
     * @see #isShiftPressed()
     * @see #isSymPressed()
     * @see #META_ALT_ON
     * @see #META_SHIFT_ON
     * @see #META_SYM_ON
     */
    public final int getMetaState() {
        return mMetaState;
    }

    /**
     * Returns the flags for this key event.
     *
     * @see #FLAG_WOKE_HERE
     */
    public final int getFlags() {
        return mFlags;
    }

    /**
     * Returns true if this key code is a modifier key.
     *
     * @return whether the provided keyCode is one of
     * {@link #KEYCODE_SHIFT_LEFT} {@link #KEYCODE_SHIFT_RIGHT},
     * {@link #KEYCODE_ALT_LEFT}, {@link #KEYCODE_ALT_RIGHT}
     * or {@link #KEYCODE_SYM}.
     */
    public static boolean isModifierKey(int keyCode) {
        return keyCode == KEYCODE_SHIFT_LEFT || keyCode == KEYCODE_SHIFT_RIGHT
                || keyCode == KEYCODE_ALT_LEFT || keyCode == KEYCODE_ALT_RIGHT
                || keyCode == KEYCODE_SYM;
    }

    /**
     * <p>Returns the pressed state of the ALT meta key.</p>
     *
     * @return true if the ALT key is pressed, false otherwise
     *
     * @see #KEYCODE_ALT_LEFT
     * @see #KEYCODE_ALT_RIGHT
     * @see #META_ALT_ON
     */
    public final boolean isAltPressed() {
        return (mMetaState & META_ALT_ON) != 0;
    }

    /**
     * <p>Returns the pressed state of the SHIFT meta key.</p>
     *
     * @return true if the SHIFT key is pressed, false otherwise
     *
     * @see #KEYCODE_SHIFT_LEFT
     * @see #KEYCODE_SHIFT_RIGHT
     * @see #META_SHIFT_ON
     */
    public final boolean isShiftPressed() {
        return (mMetaState & META_SHIFT_ON) != 0;
    }

    /**
     * <p>Returns the pressed state of the SYM meta key.</p>
     *
     * @return true if the SYM key is pressed, false otherwise
     *
     * @see #KEYCODE_SYM
     * @see #META_SYM_ON
     */
    public final boolean isSymPressed() {
        return (mMetaState & META_SYM_ON) != 0;
    }

    /**
     * Retrieve the action of this key event.  May be either
     * {@link #ACTION_DOWN}, {@link #ACTION_UP}, or {@link #ACTION_MULTIPLE}.
     * 
     * @return The event action: ACTION_DOWN, ACTION_UP, or ACTION_MULTIPLE.
     */
    public final int getAction() {
        return mAction;
    }

    /**
     * Retrieve the key code of the key event.  This is the physical key that
     * was pressed -- not the Unicode character.
     * 
     * @return The key code of the event.
     */
    public final int getKeyCode() {
        return mKeyCode;
    }

    /**
     * Retrieve the hardware key id of this key event.  These values are not
     * reliable and vary from device to device.
     *
     * {@more}
     * Mostly this is here for debugging purposes.
     */
    public final int getScanCode() {
        return mScancode;
    }

    /**
     * Retrieve the repeat count of the event.  For both key up and key down
     * events, this is the number of times the key has repeated with the first
     * down starting at 0 and counting up from there.  For multiple key
     * events, this is the number of down/up pairs that have occurred.
     * 
     * @return The number of times the key has repeated.
     */
    public final int getRepeatCount() {
        return mRepeatCount;
    }

    /**
     * Retrieve the time of the most recent key down event,
     * in the {@link android.os.SystemClock#uptimeMillis} time base.  If this
     * is a down event, this will be the same as {@link #getEventTime()}.
     * Note that when chording keys, this value is the down time of the
     * most recently pressed key, which may <em>not</em> be the same physical
     * key of this event.
     * 
     * @return Returns the most recent key down time, in the
     * {@link android.os.SystemClock#uptimeMillis} time base
     */
    public final long getDownTime() {
        return mDownTime;
    }

    /**
     * Retrieve the time this event occurred, 
     * in the {@link android.os.SystemClock#uptimeMillis} time base.
     * 
     * @return Returns the time this event occurred, 
     * in the {@link android.os.SystemClock#uptimeMillis} time base.
     */
    public final long getEventTime() {
        return mEventTime;
    }

    /**
     * Return the id for the keyboard that this event came from.  A device
     * id of 0 indicates the event didn't come from a physical device and
     * maps to the default keymap.  The other numbers are arbitrary and
     * you shouldn't depend on the values.
     * 
     * @see KeyCharacterMap#load
     */
    public final int getDeviceId() {
        return mDeviceId;
    }

    /**
     * Renamed to {@link #getDeviceId}.
     * 
     * @hide
     * @deprecated
     */
    public final int getKeyboardDevice() {
        return mDeviceId;
    }

    /**
     * Get the primary character for this key.  In other words, the label
     * that is physically printed on it.
     */
    public char getDisplayLabel() {
        return KeyCharacterMap.load(mDeviceId).getDisplayLabel(mKeyCode);
    }
    
    /**
     * <p>
     * Returns the Unicode character that the key would produce.
     * </p><p>
     * Returns 0 if the key is not one that is used to type Unicode
     * characters.
     * </p><p>
     * If the return value has bit 
     * {@link KeyCharacterMap#COMBINING_ACCENT} 
     * set, the key is a "dead key" that should be combined with another to
     * actually produce a character -- see {@link #getDeadChar} --
     * after masking with 
     * {@link KeyCharacterMap#COMBINING_ACCENT_MASK}.
     * </p>
     */
    public int getUnicodeChar() {
        return getUnicodeChar(mMetaState);
    }
    
    /**
     * <p>
     * Returns the Unicode character that the key would produce.
     * </p><p>
     * Returns 0 if the key is not one that is used to type Unicode
     * characters.
     * </p><p>
     * If the return value has bit 
     * {@link KeyCharacterMap#COMBINING_ACCENT} 
     * set, the key is a "dead key" that should be combined with another to
     * actually produce a character -- see {@link #getDeadChar} -- after masking
     * with {@link KeyCharacterMap#COMBINING_ACCENT_MASK}.
     * </p>
     */
    public int getUnicodeChar(int meta) {
        return KeyCharacterMap.load(mDeviceId).get(mKeyCode, meta);
    }
    
    /**
     * Get the characters conversion data for the key event..
     *
     * @param results a {@link KeyData} that will be filled with the results.
     *
     * @return whether the key was mapped or not.  If the key was not mapped,
     *         results is not modified.
     */
    public boolean getKeyData(KeyData results) {
        return KeyCharacterMap.load(mDeviceId).getKeyData(mKeyCode, results);
    }
    
    /**
     * The same as {@link #getMatch(char[],int) getMatch(chars, 0)}.
     */
    public char getMatch(char[] chars) {
        return getMatch(chars, 0);
    }
    
    /**
     * If one of the chars in the array can be generated by the keyCode of this
     * key event, return the char; otherwise return '\0'.
     * @param chars the characters to try to find
     * @param modifiers the modifier bits to prefer.  If any of these bits
     *                  are set, if there are multiple choices, that could
     *                  work, the one for this modifier will be set.
     */
    public char getMatch(char[] chars, int modifiers) {
        return KeyCharacterMap.load(mDeviceId).getMatch(mKeyCode, chars, modifiers);
    }
    
    /**
     * Gets the number or symbol associated with the key.  The character value
     * is returned, not the numeric value.  If the key is not a number, but is
     * a symbol, the symbol is retuned.
     */
    public char getNumber() {
        return KeyCharacterMap.load(mDeviceId).getNumber(mKeyCode);
    }
    
    /**
     * Does the key code of this key produce a glyph?
     */
    public boolean isPrintingKey() {
        return KeyCharacterMap.load(mDeviceId).isPrintingKey(mKeyCode);
    }
    
    /**
     * Deliver this key event to a {@link Callback} interface.  If this is
     * an ACTION_MULTIPLE event and it is not handled, then an attempt will
     * be made to deliver a single normal event.
     * 
     * @param receiver The Callback that will be given the event.
     * 
     * @return The return value from the Callback method that was called.
     */
    public final boolean dispatch(Callback receiver) {
        switch (mAction) {
            case ACTION_DOWN:
                return receiver.onKeyDown(mKeyCode, this);
            case ACTION_UP:
                return receiver.onKeyUp(mKeyCode, this);
            case ACTION_MULTIPLE:
                final int count = mRepeatCount;
                final int code = mKeyCode;
                if (receiver.onKeyMultiple(code, count, this)) {
                    return true;
                }
                mAction = ACTION_DOWN;
                mRepeatCount = 0;
                boolean handled = receiver.onKeyDown(code, this);
                if (handled) {
                    mAction = ACTION_UP;
                    receiver.onKeyUp(code, this);
                }
                mAction = ACTION_MULTIPLE;
                mRepeatCount = count;
                return handled;
        }
        return false;
    }

    public String toString() {
        return "KeyEvent{action=" + mAction + " code=" + mKeyCode
            + " repeat=" + mRepeatCount
            + " meta=" + mMetaState + " scancode=" + mScancode
            + " mFlags=" + mFlags + "}";
    }

    public static final Parcelable.Creator<KeyEvent> CREATOR
            = new Parcelable.Creator<KeyEvent>() {
        public KeyEvent createFromParcel(Parcel in) {
            return new KeyEvent(in);
        }

        public KeyEvent[] newArray(int size) {
            return new KeyEvent[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mAction);
        out.writeInt(mKeyCode);
        out.writeInt(mRepeatCount);
        out.writeInt(mMetaState);
        out.writeInt(mDeviceId);
        out.writeInt(mScancode);
        out.writeInt(mFlags);
        out.writeLong(mDownTime);
        out.writeLong(mEventTime);
    }

    private KeyEvent(Parcel in) {
        mAction = in.readInt();
        mKeyCode = in.readInt();
        mRepeatCount = in.readInt();
        mMetaState = in.readInt();
        mDeviceId = in.readInt();
        mScancode = in.readInt();
        mFlags = in.readInt();
        mDownTime = in.readLong();
        mEventTime = in.readLong();
    }
}
