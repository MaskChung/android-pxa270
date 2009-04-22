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

package com.android.sdksetup;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings.System;

/**
 * Entry point for SDK SetupWizard.
 *
 */
public class DefaultActivity extends Activity {
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Add a persistent setting to allow other apps to know the device has been provisioned.
        System.putInt(getContentResolver(), System.DEVICE_PROVISIONED, 1);

        // Enable the GPS.
        // Not needed since this SDK will contain the Settings app.
        LocationManager locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        System.putString(getContentResolver(), System.LOCATION_PROVIDERS_ALLOWED, LocationManager.GPS_PROVIDER);
        locationManager.updateProviders();
        
        // enable install from non market
        System.putInt(getContentResolver(), System.INSTALL_NON_MARKET_APPS, 1);

        // remove this activity from the package manager.
        PackageManager pm = getPackageManager();
        ComponentName name = new ComponentName(this, DefaultActivity.class);
        pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);

        // terminate the activity.
        finish();
    }
}

