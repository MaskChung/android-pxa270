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

package android.os;

/**
 * Class that provides access to some of the power management functions.
 *
 * {@hide}
 */
public class Power
{
    // can't instantiate this class
    private Power()
    {
    }

    /**
     * Wake lock that ensures that the CPU is running.  The screen might
     * not be on.
     */
    public static final int PARTIAL_WAKE_LOCK = 1;

    /**
     * Wake lock that ensures that the screen is on.
     */
    public static final int FULL_WAKE_LOCK = 2;

    public static native void acquireWakeLock(int lock, String id);
    public static native void releaseWakeLock(String id);

    /**
     * Flag to turn on and off the keyboard light.
     */
    public static final int KEYBOARD_LIGHT = 0x00000001;

    /**
     * Flag to turn on and off the screen backlight.
     */
    public static final int SCREEN_LIGHT = 0x00000002;

    /**
     * Flag to turn on and off the button backlight.
     */
    public static final int BUTTON_LIGHT = 0x00000004;

    /**
     * Flags to turn on and off all the backlights.
     */
    public static final int ALL_LIGHTS = (KEYBOARD_LIGHT|SCREEN_LIGHT|BUTTON_LIGHT);

    /**
     * Brightness value for fully off
     */
    public static final int BRIGHTNESS_OFF = 0;

    /**
     * Brightness value for dim backlight
     */
    public static final int BRIGHTNESS_DIM = 20;
    
    /**
     * Brightness value for fully on
     */
    public static final int BRIGHTNESS_ON = 255;
    
    /**
     * Brightness value to use when battery is low
     */
    public static final int BRIGHTNESS_LOW_BATTERY = 10;

    /**
     * Threshold for BRIGHTNESS_LOW_BATTERY (percentage)
     * Screen will stay dim if battery level is <= LOW_BATTERY_THRESHOLD
     */
    public static final int LOW_BATTERY_THRESHOLD = 10;

    /**
     * Set the brightness for one or more lights
     *
     * @param mask flags indicating which lights to change brightness
     * @param brightness new brightness value (0 = off, 255 = fully bright)
     */
    public static native int setLightBrightness(int mask, int brightness);

    /**
     * Turn the screen on or off
     *
     * @param on Whether you want the screen on or off
     */
    public static native int setScreenState(boolean on);

    public static native int setLastUserActivityTimeout(long ms);
    
    /**
     * Turn the device off.
     * 
     * This method is considered deprecated in favor of 
     * {@link android.policy.ShutdownThread.shutdownAfterDisablingRadio()}.
     *
     * @deprecated
     * @hide
     */
    @Deprecated
    public static native void shutdown();

    /**
     * Reboot the device.
     * @param reason code to pass to the kernel (e.g. "recovery"), or null.
     */
    public static native void reboot(String reason);
}

