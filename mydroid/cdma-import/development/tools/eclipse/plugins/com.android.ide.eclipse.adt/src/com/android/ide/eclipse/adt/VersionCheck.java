/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt;

import com.android.ddmlib.Device;
import com.android.ide.eclipse.adt.AdtPlugin.CheckSdkErrorHandler;
import com.android.ide.eclipse.common.AndroidConstants;

import org.osgi.framework.Constants;
import org.osgi.framework.Version;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class handling the version check for the plugin vs. the SDK.<br>
 * The plugin must be able to support all version of the SDK.
 * 
 * <p/>An SDK can require a new version of the plugin.
 * <p/>The SDK contains a file with the minimum version for the plugin. This file is inside the
 * <code>tools/lib</code> directory, and is called <code>plugin.prop</code>.<br>
 * Inside that text file, there is a line in the format "plugin.version=#.#.#". This is checked
 * against the current plugin version.<br>
 *
 */
final class VersionCheck {

    /** Pattern to get the SDK build incremental version from the
     * <code>$SDK/tools/lib/build.prop file</code>. */
    private final static Pattern sBuildVersionPattern = Pattern.compile(
            "^" + Device.PROP_BUILD_VERSION + "=(.+)$"); //$NON-NLS-1$

    /**
     * Pattern to parse release type SDK version number. This parses the content read with
     * <code>sBuildIdPattern</code>.
     */
    private final static Pattern sSdkVersionPattern = Pattern.compile(
            "^(\\d+)\\.(\\d+)_r(\\d+)$", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$

    /**
     * Pattern to get the minimum plugin version supported by the SDK. This is read from
     * the file <code>$SDK/tools/lib/plugin.prop</code>.
     */
    private final static Pattern sPluginVersionPattern = Pattern.compile(
            "^plugin.version=(\\d+)\\.(\\d+)\\.(\\d+).*$"); //$NON-NLS-1$

    /**
     * Checks the plugin and the SDK have compatible versions.
     * @param osSdkPath The path to the SDK
     * @return true if compatible.
     */
    public static boolean checkVersion(String osSdkPath, CheckSdkErrorHandler errorHandler) {
        AdtPlugin plugin = AdtPlugin.getDefault();
        String osLibs = osSdkPath + AndroidConstants.OS_SDK_LIBS_FOLDER;

        /*
         * All plugins should work with all SDKs. Newer SDKs may require a newer plugin
         * but this is handled below.
         * Still, we need to grab the SDK version from this file. This is used
         * to compare to running emulator/device when launching run/debug sessions.
         */
        try {
            FileReader reader = new FileReader(osLibs + AndroidConstants.FN_BUILD_PROP);
            BufferedReader bReader = new BufferedReader(reader);
            String line;
            while ((line = bReader.readLine()) != null) {
                Matcher m = sBuildVersionPattern.matcher(line);
                if (m.matches()) {
                    plugin.mSdkApiVersion = m.group(1).trim();
                    
                    /*
                     * No checks on the version at the moment.
                     */
                    /*
                    if (plugin.mSdkBuildVersion != null) {
                        // attempt to get version number from the build id
                        m = sSdkVersionPattern.matcher(plugin.mSdkBuildVersion);
                        if (m.matches()) {
                            // get the platform version number
                            int platformMajor = Integer.parseInt(m.group(1));
                            int platformMinor = Integer.parseInt(m.group(2));
                            @SuppressWarnings("unused") //$NON-NLS-1$
                            int sdkRelease = Integer.parseInt(m.group(3));
                            
                            if (platformMajor != 0 || platformMinor != 9) {
                                return errorHandler.handleError(String.format(
                                        "This version of ADT requires the Android SDK version 0.9\n\nCurrent version is %1$s.\n\nPlease update your SDK to the latest version.",
                                        plugin.mSdkBuildVersion));
                            }
                        } else {
                            // unknown version format.
                            AdtPlugin.printErrorToConsole(
                                    (Object)String.format(Messages.VersionCheck_Unable_To_Parse_Version_s,
                                            plugin.mSdkBuildVersion));
                        }
                    }
                    */
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            // the build id will be null, and this is handled by the builders.
        } catch (IOException e) {
            // the build id will be null, and this is handled by the builders.
        }

        // get the plugin property file, and grab the minimum plugin version required
        // to work with the sdk
        int minMajorVersion = -1;
        int minMinorVersion = -1;
        int minMicroVersion = -1;
        try {
            FileReader reader = new FileReader(osLibs + AndroidConstants.FN_PLUGIN_PROP);
            BufferedReader bReader = new BufferedReader(reader);
            String line;
            while ((line = bReader.readLine()) != null) {
                Matcher m = sPluginVersionPattern.matcher(line);
                if (m.matches()) {
                    minMajorVersion = Integer.parseInt(m.group(1));
                    minMinorVersion = Integer.parseInt(m.group(2));
                    minMicroVersion = Integer.parseInt(m.group(3));
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            // the build id will be null, and this is handled by the builders.
        } catch (IOException e) {
            // the build id will be null, and this is handled by the builders.
        }

        // Failed to get the min plugin version number?
        if (minMajorVersion == -1 || minMinorVersion == -1 || minMicroVersion ==-1) {
            return errorHandler.handleWarning(Messages.VersionCheck_Plugin_Version_Failed);
        }

        // test the plugin number
        String versionString = (String) plugin.getBundle().getHeaders().get(
                Constants.BUNDLE_VERSION);
        Version version = new Version(versionString);

        boolean valid = true;
        if (version.getMajor() < minMajorVersion) {
            valid = false;
        } else if (version.getMajor() == minMajorVersion) {
            if (version.getMinor() < minMinorVersion) {
                valid = false;
            } else if (version.getMinor() == minMinorVersion) {
                if (version.getMicro() < minMicroVersion) {
                    valid = false;
                }
            }
        }

        if (valid == false) {
            return errorHandler.handleWarning(
                    String.format(Messages.VersionCheck_Plugin_Too_Old,
                            minMajorVersion, minMinorVersion, minMicroVersion, versionString));
        }

        return true; // no error!
    }
}
