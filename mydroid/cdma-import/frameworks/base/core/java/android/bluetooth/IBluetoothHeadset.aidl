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

package android.bluetooth;

import android.bluetooth.IBluetoothHeadsetCallback;

/**
 * System private API for Bluetooth Headset service
 *
 * {@hide}
 */
interface IBluetoothHeadset {
    int getState();

    String getHeadsetAddress();

    // Request that the given headset be connected
    // Assumes the given headset is already bonded
    // Will disconnect any currently connected headset
    // returns false if cannot start a connection (for example, there is
    // already a pending connect). callback will always be called iff this
    // returns true
    boolean connectHeadset(in String address, in IBluetoothHeadsetCallback callback);

    boolean isConnected(in String address);

    void disconnectHeadset();
}
