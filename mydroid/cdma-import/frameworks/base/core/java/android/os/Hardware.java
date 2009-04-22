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

package android.os;

/**
 * {@hide}
 */
public class Hardware 
{
    /**
     * Control the LED.
     */
    public static native int setLedState(int colorARGB, int onMS, int offMS);
    
    /**
     * Control the Flashlight
     */
    public static native boolean getFlashlightEnabled();
    public static native void setFlashlightEnabled(boolean on);
    public static native void enableCameraFlash(int milliseconds);

    /**
     * Control the backlights
     */
    public static native void setScreenBacklight(int brightness);
    public static native void setKeyboardBacklight(boolean on);
    public static native void setButtonBacklight(boolean on);
}
