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

package com.android.settings;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.util.Config;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceInfoSettings extends PreferenceActivity {

    private static final String TAG = "DeviceInfoSettings";
    private static final boolean LOGD = false || Config.LOGD;

    private static final String KEY_CONTAINER = "container";
    private static final String KEY_TEAM = "team";
    private static final String KEY_CONTRIBUTORS = "contributors";
    private static final String KEY_TERMS = "terms";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_COPYRIGHT = "copyright";
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        addPreferencesFromResource(R.xml.device_info_settings);
       
        setSummary("firmware_version", "ro.build.version.release");
        setSummary("baseband_version", "gsm.version.baseband");
        setSummary("device_model", "ro.product.model");
        setSummary("build_number", "ro.build.description");
        findPreference("kernel_version").setSummary(getFormattedKernelVersion());

        /*
         * Settings is a generic app and should not contain any device-specific
         * info.
         */
        PreferenceGroup parentPreference = (PreferenceGroup) findPreference(KEY_CONTAINER);
        Utils.updatePreferenceToSpecificActivityOrRemove(this, parentPreference, KEY_TERMS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(this, parentPreference, KEY_LICENSE,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(this, parentPreference, KEY_CONTRIBUTORS,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(this, parentPreference, KEY_COPYRIGHT,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
        Utils.updatePreferenceToSpecificActivityOrRemove(this, parentPreference, KEY_TEAM,
                Utils.UPDATE_PREFERENCE_FLAG_SET_TITLE_TO_MATCHING_ACTIVITY);
    }
    
    private void setSummary(String preference, String property) {
        try {
            findPreference(preference).setSummary(
                    SystemProperties.get(property, 
                            getResources().getString(R.string.device_info_default)));
        } catch (RuntimeException e) {
            
        }
    }

    private String getFormattedKernelVersion() {
        String procVersionStr;

        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/version"), 256);
            try {
                procVersionStr = reader.readLine();
            } finally {
                reader.close();
            }

            final String PROC_VERSION_REGEX =
                "\\w+\\s+" + /* ignore: Linux */
                "\\w+\\s+" + /* ignore: version */
                "([^\\s]+)\\s+" + /* group 1: 2.6.22-omap1 */
                "\\(([^\\s@]+(?:@[^\\s.]+)?)[^)]*\\)\\s+" + /* group 2: (xxxxxx@xxxxx.constant) */
                "\\([^)]+\\)\\s+" + /* ignore: (gcc ..) */
                "([^\\s]+)\\s+" + /* group 3: #26 */
                "(?:PREEMPT\\s+)?" + /* ignore: PREEMPT (optional) */
                "(.+)"; /* group 4: date */

            Pattern p = Pattern.compile(PROC_VERSION_REGEX);
            Matcher m = p.matcher(procVersionStr);

            if (!m.matches()) {
                Log.e(TAG, "Regex did not match on /proc/version: " + procVersionStr);
                return "Unavailable";
            } else if (m.groupCount() < 4) {
                Log.e(TAG, "Regex match on /proc/version only returned " + m.groupCount()
                        + " groups");
                return "Unavailable";
            } else {
                return (new StringBuilder(m.group(1)).append("\n").append(
                        m.group(2)).append(" ").append(m.group(3)).append("\n")
                        .append(m.group(4))).toString();
            }
        } catch (IOException e) {  
            Log.e(TAG,
                "IO Exception when getting kernel version for Device Info screen",
                e);

            return "Unavailable";
        }
    }

}
