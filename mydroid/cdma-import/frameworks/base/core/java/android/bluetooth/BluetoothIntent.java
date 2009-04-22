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

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;

/**
 * The Android Bluetooth API is not finalized, and *will* change. Use at your
 * own risk.
 *
 * Manages the local Bluetooth device. Scan for devices, create bondings,
 * power up and down the adapter.
 *
 * @hide
 */
public interface BluetoothIntent {
    public static final String MODE =
        "android.bluetooth.intent.MODE";
    public static final String ADDRESS =
        "android.bluetooth.intent.ADDRESS";
    public static final String NAME =
        "android.bluetooth.intent.NAME";
    public static final String ALIAS =
        "android.bluetooth.intent.ALIAS";
    public static final String RSSI =
        "android.bluetooth.intent.RSSI";
    public static final String CLASS =
        "android.bluetooth.intent.CLASS";
    public static final String HEADSET_STATE =
        "android.bluetooth.intent.HEADSET_STATE";
    public static final String HEADSET_PREVIOUS_STATE =
        "android.bluetooth.intent.HEADSET_PREVIOUS_STATE";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ENABLED_ACTION          =
        "android.bluetooth.intent.action.ENABLED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String DISABLED_ACTION         =
        "android.bluetooth.intent.action.DISABLED";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String NAME_CHANGED_ACTION  =
        "android.bluetooth.intent.action.NAME_CHANGED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String MODE_CHANGED_ACTION         =
        "android.bluetooth.intent.action.MODE_CHANGED";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String DISCOVERY_STARTED_ACTION          =
        "android.bluetooth.intent.action.DISCOVERY_STARTED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String DISCOVERY_COMPLETED_ACTION        =
        "android.bluetooth.intent.action.DISCOVERY_COMPLETED";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String PAIRING_REQUEST_ACTION            =
        "android.bluetooth.intent.action.PAIRING_REQUEST";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String PAIRING_CANCEL_ACTION             =
        "android.bluetooth.intent.action.PAIRING_CANCEL";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_DEVICE_FOUND_ACTION        =
        "android.bluetooth.intent.action.REMOTE_DEVICE_FOUND";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_DEVICE_DISAPPEARED_ACTION  =
        "android.bluetooth.intent.action.REMOTE_DEVICE_DISAPPEARED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_DEVICE_CLASS_UPDATED_ACTION  =
        "android.bluetooth.intent.action.REMOTE_DEVICE_DISAPPEARED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_DEVICE_CONNECTED_ACTION    =
        "android.bluetooth.intent.action.REMOTE_DEVICE_CONNECTED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_DEVICE_DISCONNECT_REQUESTED_ACTION =
        "android.bluetooth.intent.action.REMOTE_DEVICE_DISCONNECT_REQUESTED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_DEVICE_DISCONNECTED_ACTION =
        "android.bluetooth.intent.action.REMOTE_DEVICE_DISCONNECTED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_NAME_UPDATED_ACTION        =
        "android.bluetooth.intent.action.REMOTE_NAME_UPDATED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_NAME_FAILED_ACTION         =
        "android.bluetooth.intent.action.REMOTE_NAME_FAILED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_ALIAS_CHANGED_ACTION       =
        "android.bluetooth.intent.action.REMOTE_ALIAS_CHANGED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String REMOTE_ALIAS_CLEARED_ACTION       =
        "android.bluetooth.intent.action.REMOTE_ALIAS_CLEARED";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String BONDING_CREATED_ACTION            =
        "android.bluetooth.intent.action.BONDING_CREATED";
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String BONDING_REMOVED_ACTION            =
        "android.bluetooth.intent.action.BONDING_REMOVED";

    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String HEADSET_STATE_CHANGED_ACTION      =
        "android.bluetooth.intent.action.HEADSET_STATE_CHANGED";
}
