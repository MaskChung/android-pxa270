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

/**
 * Within a keyguard, there may be several screens that need a callback
 * to the host keyguard view.
 */
public interface KeyguardScreenCallback extends KeyguardViewCallback {

    /**
     * Transition to the lock screen.
     */
    void goToLockScreen();

    /**
     * Transitino to th unlock screen.
     */
    void goToUnlockScreen();

    /**
     * @return Whether the keyguard requires some sort of PIN.
     */
    boolean isSecure();

    /**
     * @return Whether we are in a mode where we only want to verify the
     *   user can get past the keyguard.
     */
    boolean isVerifyUnlockOnly();

    /**
     * Stay on me, but recreate me (so I can use a different layout).
     */
    void recreateMe();

    /**
     * Take action to send an emergency call.
     */
    void takeEmergencyCallAction();

    /**
     * Report that the user had a failed attempt unlocking via the pattern.
     */
    void reportFailedPatternAttempt();

}
