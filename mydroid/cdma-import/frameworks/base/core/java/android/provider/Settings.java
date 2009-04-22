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

package android.provider;

import com.google.android.collect.Maps;

import org.apache.commons.codec.binary.Base64;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ContentQueryMap;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.*;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AndroidException;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;


/**
 * The Settings provider contains global system-level device preferences.
 */
public final class Settings {

    // Intent actions for Settings

    /**
     * Activity Action: Show system settings.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SETTINGS = "android.settings.SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of APNs.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APN_SETTINGS = "android.settings.APN_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of current location
     * sources.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCATION_SOURCE_SETTINGS =
            "android.settings.LOCATION_SOURCE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of wireless controls
     * such as Wi-Fi, Bluetooth and Mobile networks.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIRELESS_SETTINGS =
            "android.settings.WIRELESS_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of security and
     * location privacy.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SECURITY_SETTINGS =
            "android.settings.SECURITY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Wi-Fi.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_WIFI_SETTINGS =
            "android.settings.WIFI_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of Bluetooth.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_BLUETOOTH_SETTINGS =
            "android.settings.BLUETOOTH_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of date and time.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DATE_SETTINGS =
            "android.settings.DATE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of sound and volume.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SOUND_SETTINGS =
            "android.settings.SOUND_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of display.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_DISPLAY_SETTINGS =
            "android.settings.DISPLAY_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of locale.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_LOCALE_SETTINGS =
            "android.settings.LOCALE_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of application-related settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_APPLICATION_SETTINGS =
            "android.settings.APPLICATION_SETTINGS";

    /**
     * Activity Action: Show settings to allow configuration of sync settings.
     * <p>
     * In some cases, a matching Activity may not exist, so ensure you
     * safeguard against this.
     * <p>
     * Input: Nothing.
     * <p>
     * Output: Nothing.
     * 
     * @hide
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SYNC_SETTINGS =
            "android.settings.SYNC_SETTINGS";
    
    // End of Intent actions for Settings

    private static final String JID_RESOURCE_PREFIX = "android";

    public static final String AUTHORITY = "settings";

    private static final String TAG = "Settings";

    private static String sJidResource = null;

    public static class SettingNotFoundException extends AndroidException {
        public SettingNotFoundException(String msg) {
            super(msg);
        }
    }

    /**
     * Common base for tables of name/value settings.
     *
     * 
     */
    public static class NameValueTable implements BaseColumns {
        public static final String NAME = "name";
        public static final String VALUE = "value";

        protected static boolean putString(ContentResolver resolver, Uri uri,
                String name, String value) {
            // The database will take care of replacing duplicates.
            try {
                ContentValues values = new ContentValues();
                values.put(NAME, name);
                values.put(VALUE, value);
                resolver.insert(uri, values);
                return true;
            } catch (SQLException e) {
                Log.e(TAG, "Can't set key " + name + " in " + uri, e);
                return false;
            }
        }

        public static Uri getUriFor(Uri uri, String name) {
            return Uri.withAppendedPath(uri, name);
        }
    }

    private static class NameValueCache {
        private final String mVersionSystemProperty;
        private final HashMap<String, String> mValues = Maps.newHashMap();
        private long mValuesVersion = 0;
        private final Uri mUri;

        NameValueCache(String versionSystemProperty, Uri uri) {
            mVersionSystemProperty = versionSystemProperty;
            mUri = uri;
        }

        String getString(ContentResolver cr, String name) {
            long newValuesVersion = SystemProperties.getLong(mVersionSystemProperty, 0);
            if (mValuesVersion != newValuesVersion) {
                mValues.clear();
                mValuesVersion = newValuesVersion;
            }
            if (!mValues.containsKey(name)) {
                String value = null;
                Cursor c = null;
                try {
                    c = cr.query(mUri, new String[] { Settings.NameValueTable.VALUE },
                            Settings.NameValueTable.NAME + "=?", new String[]{name}, null);
                    if (c.moveToNext()) value = c.getString(0);
                    mValues.put(name, value);
                } catch (SQLException e) {
                    // SQL error: return null, but don't cache it.
                    Log.e(TAG, "Can't get key " + name + " from " + mUri, e);
                } finally {
                    if (c != null) c.close();
                }
                return value;
            } else {
                return mValues.get(name);
            }
        }
    }

    /**
     * System settings, containing miscellaneous system preferences.  This
     * table holds simple name/value pairs.  There are convenience
     * functions for accessing individual settings entries.
     */
    public static final class System extends NameValueTable {
        public static final String SYS_PROP_SETTING_VERSION = "sys.settings_system_version";

        private static volatile NameValueCache mNameValueCache = null;

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public synchronized static String getString(ContentResolver resolver, String name) {
            if (mNameValueCache == null) {
                mNameValueCache = new NameValueCache(SYS_PROP_SETTING_VERSION, CONTENT_URI);
            }
            return mNameValueCache.getString(resolver, name);
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver,
                String name, String value) {
            return putString(resolver, CONTENT_URI, name, value);
        }

        /**
         * Construct the content URI for a particular name/value pair,
         * useful for monitoring changes with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI, or null if not present
         */
        public static Uri getUriFor(String name) {
            return getUriFor(CONTENT_URI, name);
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.  The default value will be returned if the setting is
         * not defined or not an integer.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid integer.
         */
        public static int getInt(ContentResolver cr, String name, int def) {
            String v = getString(cr, name);
            try {
                return v != null ? Integer.parseInt(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as an integer.  Note that internally setting values are always
         * stored as strings; this function converts the string to an integer
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not an integer.
         *
         * @return The setting's current value.
         */
        public static int getInt(ContentResolver cr, String name)
                throws SettingNotFoundException {
            String v = getString(cr, name);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as an
         * integer. This will either create a new entry in the table if the
         * given name does not exist, or modify the value of the existing row
         * with that name.  Note that internally setting values are always
         * stored as strings, so this function converts the given value to a
         * string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putInt(ContentResolver cr, String name, int value) {
            return putString(cr, name, Integer.toString(value));
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a floating point number.  Note that internally setting values are
         * always stored as strings; this function converts the string to an
         * float for you. The default value will be returned if the setting
         * is not defined or not a valid float.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         * @param def Value to return if the setting is not defined.
         *
         * @return The setting's current value, or 'def' if it is not defined
         * or not a valid float.
         */
        public static float getFloat(ContentResolver cr, String name, float def) {
            String v = getString(cr, name);
            try {
                return v != null ? Float.parseFloat(v) : def;
            } catch (NumberFormatException e) {
                return def;
            }
        }

        /**
         * Convenience function for retrieving a single system settings value
         * as a float.  Note that internally setting values are always
         * stored as strings; this function converts the string to a float
         * for you.
         * <p>
         * This version does not take a default value.  If the setting has not
         * been set, or the string value is not a number,
         * it throws {@link SettingNotFoundException}.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to retrieve.
         *
         * @throws SettingNotFoundException Thrown if a setting by the given
         * name can't be found or the setting value is not a float.
         *
         * @return The setting's current value.
         */
        public static float getFloat(ContentResolver cr, String name)
                throws SettingNotFoundException {
            String v = getString(cr, name);
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                throw new SettingNotFoundException(name);
            }
        }

        /**
         * Convenience function for updating a single settings value as a
         * floating point number. This will either create a new entry in the
         * table if the given name does not exist, or modify the value of the
         * existing row with that name.  Note that internally setting values
         * are always stored as strings, so this function converts the given
         * value to a string before storing it.
         *
         * @param cr The ContentResolver to access.
         * @param name The name of the setting to modify.
         * @param value The new value for the setting.
         * @return true if the value was set, false on database errors
         */
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            return putString(cr, name, Float.toString(value));
        }

        /**
         * Convenience function to read all of the current
         * configuration-related settings into a
         * {@link Configuration} object.
         *
         * @param cr The ContentResolver to access.
         * @param outConfig Where to place the configuration settings.
         */
        public static void getConfiguration(ContentResolver cr, Configuration outConfig) {
            outConfig.fontScale = Settings.System.getFloat(
                cr, FONT_SCALE, outConfig.fontScale);
            if (outConfig.fontScale < 0) {
                outConfig.fontScale = 1;
            }
        }

        /**
         * Convenience function to write a batch of configuration-related
         * settings from a {@link Configuration} object.
         *
         * @param cr The ContentResolver to access.
         * @param config The settings to write.
         * @return true if the values were set, false on database errors
         */
        public static boolean putConfiguration(ContentResolver cr, Configuration config) {
            return Settings.System.putFloat(cr, FONT_SCALE, config.fontScale);
        }

        public static boolean getShowGTalkServiceStatus(ContentResolver cr) {
            return getInt(cr, SHOW_GTALK_SERVICE_STATUS, 0) != 0;
        }

        public static void setShowGTalkServiceStatus(ContentResolver cr, boolean flag) {
            putInt(cr, SHOW_GTALK_SERVICE_STATUS, flag ? 1 : 0);
        }

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/system");

        /**
         * Whether we keep the device on while the device is plugged in.
         * 0=no  1=yes
         */
        public static final String STAY_ON_WHILE_PLUGGED_IN = "stay_on_while_plugged_in";

        /**
         * What happens when the user presses the end call button if they're not
         * on a call.<br/>
         * <b>Values:</b><br/>
         * 0 - The end button does nothing.<br/>
         * 1 - The end button goes to the home screen.<br/>
         * 2 - The end button puts the device to sleep and locks the keyguard.<br/>
         * 3 - The end button goes to the home screen.  If the user is already on the
         * home screen, it puts the device to sleep.
         */
        public static final String END_BUTTON_BEHAVIOR = "end_button_behavior";

        /**
         * Whether Airplane Mode is on.
         */
        public static final String AIRPLANE_MODE_ON = "airplane_mode_on";

        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify Bluetooth radio.
         */
        public static final String RADIO_BLUETOOTH = "bluetooth";

        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify Wi-Fi radio.
         */
        public static final String RADIO_WIFI = "wifi";

        /**
         * Constant for use in AIRPLANE_MODE_RADIOS to specify Cellular radio.
         */
        public static final String RADIO_CELL = "cell";

        /**
         * A comma separated list of radios that need to be disabled when airplane mode
         * is on. This overrides WIFI_ON and BLUETOOTH_ON, if Wi-Fi and bluetooth are
         * included in the comma separated list.
         */
        public static final String AIRPLANE_MODE_RADIOS = "airplane_mode_radios";

        /**
         * Whether the Wi-Fi should be on.  Only the Wi-Fi service should touch this.
         */
        public static final String WIFI_ON = "wifi_on";

        /**
         * Whether to notify the user of open networks.
         * <p>
         * If not connected and the scan results have an open network, we will
         * put this notification up. If we attempt to connect to a network or
         * the open network(s) disappear, we remove the notification. When we
         * show the notification, we will not show it again for
         * {@link #WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY} time.
         */
        public static final String WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON =
                "wifi_networks_available_notification_on";

        /**
         * Delay (in seconds) before repeating the Wi-Fi networks available notification.
         * Connecting to a network will reset the timer.
         */
        public static final String WIFI_NETWORKS_AVAILABLE_REPEAT_DELAY =
                "wifi_networks_available_repeat_delay";

        /**
         * When the number of open networks exceeds this number, the
         * least-recently-used excess networks will be removed.
         */
        public static final String WIFI_NUM_OPEN_NETWORKS_KEPT = "wifi_num_open_networks_kept";

        /**
         * Whether the Wi-Fi watchdog is enabled.
         */
        public static final String WIFI_WATCHDOG_ON = "wifi_watchdog_on";

        /**
         * The number of access points required for a network in order for the
         * watchdog to monitor it.
         */
        public static final String WIFI_WATCHDOG_AP_COUNT = "wifi_watchdog_ap_count";

        /**
         * The number of initial pings to perform that *may* be ignored if they
         * fail. Again, if these fail, they will *not* be used in packet loss
         * calculation. For example, one network always seemed to time out for
         * the first couple pings, so this is set to 3 by default.
         */
        public static final String WIFI_WATCHDOG_INITIAL_IGNORED_PING_COUNT = 
                "wifi_watchdog_initial_ignored_ping_count";
        
        /**
         * The number of pings to test if an access point is a good connection.
         */
        public static final String WIFI_WATCHDOG_PING_COUNT = "wifi_watchdog_ping_count";
        
        /**
         * The timeout per ping.
         */
        public static final String WIFI_WATCHDOG_PING_TIMEOUT_MS = "wifi_watchdog_ping_timeout_ms";

        /**
         * The delay between pings.
         */
        public static final String WIFI_WATCHDOG_PING_DELAY_MS = "wifi_watchdog_ping_delay_ms";

        /**
         * The acceptable packet loss percentage (range 0 - 100) before trying
         * another AP on the same network.
         */
        public static final String WIFI_WATCHDOG_ACCEPTABLE_PACKET_LOSS_PERCENTAGE =
                "wifi_watchdog_acceptable_packet_loss_percentage";

        /**
         * The maximum number of access points (per network) to attempt to test.
         * If this number is reached, the watchdog will no longer monitor the
         * initial connection state for the network. This is a safeguard for
         * networks containing multiple APs whose DNS does not respond to pings.
         */
        public static final String WIFI_WATCHDOG_MAX_AP_CHECKS = "wifi_watchdog_max_ap_checks";

        /**
         * Whether the Wi-Fi watchdog is enabled for background checking even
         * after it thinks the user has connected to a good access point.
         */
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_ENABLED =
                "wifi_watchdog_background_check_enabled";

        /**
         * The timeout for a background ping
         */
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_TIMEOUT_MS =
                "wifi_watchdog_background_check_timeout_ms";
        
        /**
         * The delay between background checks.
         */
        public static final String WIFI_WATCHDOG_BACKGROUND_CHECK_DELAY_MS =
                "wifi_watchdog_background_check_delay_ms";
        
        /**
         * Whether to use static IP and other static network attributes.
         * <p>
         * Set to 1 for true and 0 for false.
         */
        public static final String WIFI_USE_STATIC_IP = "wifi_use_static_ip";

        /**
         * The static IP address.
         * <p>
         * Example: "192.168.1.51"
         */
        public static final String WIFI_STATIC_IP = "wifi_static_ip";

        /**
         * If using static IP, the gateway's IP address.
         * <p>
         * Example: "192.168.1.1"
         */
        public static final String WIFI_STATIC_GATEWAY = "wifi_static_gateway";

        /**
         * If using static IP, the net mask.
         * <p>
         * Example: "255.255.255.0"
         */
        public static final String WIFI_STATIC_NETMASK = "wifi_static_netmask";

        /**
         * If using static IP, the primary DNS's IP address.
         * <p>
         * Example: "192.168.1.1"
         */
        public static final String WIFI_STATIC_DNS1 = "wifi_static_dns1";

        /**
         * If using static IP, the secondary DNS's IP address.
         * <p>
         * Example: "192.168.1.2"
         */
        public static final String WIFI_STATIC_DNS2 = "wifi_static_dns2";

        /**
         * User preference for which network(s) should be used. Only the
         * connectivity service should touch this.
         */
        public static final String NETWORK_PREFERENCE = "network_preference";

        /**
         * Whether bluetooth is enabled/disabled
         * 0=disabled. 1=enabled.
         */
        public static final String BLUETOOTH_ON = "bluetooth_on";

        /**
         * Determines whether remote devices may discover and/or connect to
         * this device.
         * <P>Type: INT</P>
         * 2 -- discoverable and connectable
         * 1 -- connectable but not discoverable
         * 0 -- neither connectable nor discoverable
         */
        public static final String BLUETOOTH_DISCOVERABILITY =
            "bluetooth_discoverability";

        /**
         * Bluetooth discoverability timeout.  If this value is nonzero, then
         * Bluetooth becomes discoverable for a certain number of seconds,
         * after which is becomes simply connectable.  The value is in seconds.
         */
        public static final String BLUETOOTH_DISCOVERABILITY_TIMEOUT =
            "bluetooth_discoverability_timeout";

        /**
         * Whether autolock is enabled (0 = false, 1 = true)
         */
        public static final String LOCK_PATTERN_ENABLED = "lock_pattern_autolock";

        /**
         * Whether the device has been provisioned (0 = false, 1 = true)
         */
        public static final String DEVICE_PROVISIONED = "device_provisioned";

        /**
         * Whether lock pattern is visible as user enters (0 = false, 1 = true)
         */
        public static final String LOCK_PATTERN_VISIBLE = "lock_pattern_visible_pattern";


        /**
         * A formatted string of the next alarm that is set, or the empty string
         * if there is no alarm set.
         */
        public static final String NEXT_ALARM_FORMATTED = "next_alarm_formatted";

        /**
         * Comma-separated list of location providers that activities may access.
         */
        public static final String LOCATION_PROVIDERS_ALLOWED = "location_providers_allowed";

        /**
         * Whether or not data roaming is enabled. (0 = false, 1 = true)
         */
        public static final String DATA_ROAMING = "data_roaming";

        /**
         * The CDMA roaming mode 0 = Home Networks, CDMA default
         *                       1 = Roaming on Affiliated networks
         *                       2 = Roaming on any networks
         */
        public static final String CDMA_ROAMING_MODE = "roaming_settings";
        
        /**
         * The CDMA subscription mode 0 = RUIM/SIM (default)
         *                                1 = NV
         */
        public static final String CDMA_SUBSCRIPTION_MODE = "subscription_mode";

        /**
         * Scaling factor for fonts, float.
         */
        public static final String FONT_SCALE = "font_scale";

        /**
         * Name of an application package to be debugged.
         */
        public static final String DEBUG_APP = "debug_app";

        /**
         * If 1, when launching DEBUG_APP it will wait for the debugger before
         * starting user code.  If 0, it will run normally.
         */
        public static final String WAIT_FOR_DEBUGGER = "wait_for_debugger";

        /**
         * represents current active phone class
         * 1 = GSM-Phone, 0 = CDMA-Phone
         */
        public static final String CURRENT_ACTIVE_PHONE = "current_active_phone";

        /**
         * Whether or not to dim the screen. 0=no  1=yes
         */
        public static final String DIM_SCREEN = "dim_screen";

        /**
         * The timeout before the screen turns off.
         */
        public static final String SCREEN_OFF_TIMEOUT = "screen_off_timeout";

        /**
         * The screen backlight brightness between 0 and 255.
         */
        public static final String SCREEN_BRIGHTNESS = "screen_brightness";

        /**
         * Control whether the process CPU usage meter should be shown.
         */
        public static final String SHOW_PROCESSES = "show_processes";

        /**
         * If 1, the activity manager will aggressively finish activities and
         * processes as soon as they are no longer needed.  If 0, the normal
         * extended lifetime is used.
         */
        public static final String ALWAYS_FINISH_ACTIVITIES =
                "always_finish_activities";


        /**
         * Ringer mode. This is used internally, changing this value will not
         * change the ringer mode. See AudioManager.
         */
        public static final String MODE_RINGER = "mode_ringer";

        /**
         * Determines which streams are affected by ringer mode changes. The
         * stream type's bit should be set to 1 if it should be muted when going
         * into an inaudible ringer mode.
         */
        public static final String MODE_RINGER_STREAMS_AFFECTED = "mode_ringer_streams_affected";

         /**
          * Determines which streams are affected by mute. The
          * stream type's bit should be set to 1 if it should be muted when a mute request
          * is received.
          */
         public static final String MUTE_STREAMS_AFFECTED = "mute_streams_affected";

        /**
         * Whether vibrate is on for different events. This is used internally,
         * changing this value will not change the vibrate. See AudioManager.
         */
        public static final String VIBRATE_ON = "vibrate_on";

        /**
         * Ringer volume. This is used internally, changing this value will not
         * change the volume. See AudioManager.
         */
        public static final String VOLUME_RING = "volume_ring";

        /**
         * System/notifications volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_SYSTEM = "volume_system";

        /**
         * Voice call volume. This is used internally, changing this value will
         * not change the volume. See AudioManager.
         */
        public static final String VOLUME_VOICE = "volume_voice";

        /**
         * Music/media/gaming volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_MUSIC = "volume_music";

        /**
         * Alarm volume. This is used internally, changing this
         * value will not change the volume. See AudioManager.
         */
        public static final String VOLUME_ALARM = "volume_alarm";

        /**
         * The mapping of stream type (integer) to its setting.
         */
        public static final String[] VOLUME_SETTINGS = {
            VOLUME_VOICE, VOLUME_SYSTEM, VOLUME_RING, VOLUME_MUSIC, VOLUME_ALARM
        };

        /**
         * Appended to various volume related settings to record the previous
         * values before they the settings were affected by a silent/vibrate
         * ringer mode change.
         */
        public static final String APPEND_FOR_LAST_AUDIBLE = "_last_audible";

        /**
         * Persistent store for the system-wide default ringtone URI.
         * <p>
         * If you need to play the default ringtone at any given time, it is recommended
         * you give {@link #DEFAULT_RINGTONE_URI} to the media player.  It will resolve
         * to the set default ringtone at the time of playing.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final String RINGTONE = "ringtone";

        /**
         * A {@link Uri} that will point to the current default ringtone at any
         * given time.
         * <p>
         * If the current default ringtone is in the DRM provider and the caller
         * does not have permission, the exception will be a
         * FileNotFoundException.
         */
        public static final Uri DEFAULT_RINGTONE_URI = getUriFor(RINGTONE);

        /**
         * Persistent store for the system-wide default notification sound.
         *
         * @see #RINGTONE
         * @see #DEFAULT_NOTIFICATION_URI
         */
        public static final String NOTIFICATION_SOUND = "notification_sound";

        /**
         * A {@link Uri} that will point to the current default notification
         * sound at any given time.
         *
         * @see #DEFAULT_RINGTONE_URI
         */
        public static final Uri DEFAULT_NOTIFICATION_URI = getUriFor(NOTIFICATION_SOUND);

        /**
         * Setting to enable Auto Replace (AutoText) in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_AUTO_REPLACE = "auto_replace";

        /**
         * Setting to enable Auto Caps in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_AUTO_CAPS = "auto_caps";

        /**
         * Setting to enable Auto Punctuate in text editors. 1 = On, 0 = Off. This
         * feature converts two spaces to a "." and space.
         */
        public static final String TEXT_AUTO_PUNCTUATE = "auto_punctuate";

        /**
         * Setting to showing password characters in text editors. 1 = On, 0 = Off
         */
        public static final String TEXT_SHOW_PASSWORD = "show_password";
        /**
         * USB Mass Storage Enabled
         */
        public static final String USB_MASS_STORAGE_ENABLED =
                "usb_mass_storage_enabled";

        public static final String SHOW_GTALK_SERVICE_STATUS =
                "SHOW_GTALK_SERVICE_STATUS";

        /**
         * Name of activity to use for wallpaper on the home screen.
         */
        public static final String WALLPAPER_ACTIVITY = "wallpaper_activity";

        /**
         * Host name and port for a user-selected proxy.
         */
        public static final String HTTP_PROXY = "http_proxy";

        /**
         * Value to specify if the user prefers the date, time and time zone
         * to be automatically fetched from the network (NITZ). 1=yes, 0=no
         */
        public static final String AUTO_TIME = "auto_time";

        /**
         * Display times as 12 or 24 hours
         *   12
         *   24
         */
        public static final String TIME_12_24 = "time_12_24";

        /**
         * Date format string
         *   mm/dd/yyyy
         *   dd/mm/yyyy
         *   yyyy/mm/dd
         */
        public static final String DATE_FORMAT = "date_format";

        /**
         * Settings classname to launch when Settings is clicked from All
         * Applications.  Needed because of user testing between the old
         * and new Settings apps. TODO: 881807
         */
        public static final String SETTINGS_CLASSNAME = "settings_classname";

        /**
         * Whether the setup wizard has been run before (on first boot), or if
         * it still needs to be run.
         *
         * nonzero = it has been run in the past
         * 0 = it has not been run in the past
         */
        public static final String SETUP_WIZARD_HAS_RUN = "setup_wizard_has_run";

        /**
         * The Android ID (a unique 64-bit value) as a hex string.
         * Identical to that obtained by calling
         * GoogleLoginService.getAndroidId(); it is also placed here
         * so you can get it without binding to a service.
         */
        public static final String ANDROID_ID = "android_id";

        /**
         * The Logging ID (a unique 64-bit value) as a hex string.
         * Used as a pseudonymous identifier for logging.
         */
        public static final String LOGGING_ID = "logging_id";

        /**
         * If this setting is set (to anything), then all references
         * to Gmail on the device must change to Google Mail.
         */
        public static final String USE_GOOGLE_MAIL = "use_google_mail";

        /**
         * Whether the package installer should allow installation of apps downloaded from
         * sources other than the Android Market (vending machine).
         *
         * 1 = allow installing from other sources
         * 0 = only allow installing from the Android Market
         */
        public static final String INSTALL_NON_MARKET_APPS = "install_non_market_apps";

        /**
         * Scaling factor for normal window animations. Setting to 0 will disable window
         * animations.
         */
        public static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";

        /**
         * Scaling factor for activity transition animations. Setting to 0 will disable window
         * animations.
         */
        public static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";

        public static final String PARENTAL_CONTROL_ENABLED =
            "parental_control_enabled";

        public static final String PARENTAL_CONTROL_REDIRECT_URL =
            "parental_control_redirect_url";

        public static final String PARENTAL_CONTROL_LAST_UPDATE =
          "parental_control_last_update";

        /**
         * Whether ADB is enabled.
         */
        public static final String ADB_ENABLED = "adb_enabled";

        /**
         * Whether the audible DTMF tones are played by the dialer when dialing. The value is
         * boolean (1 or 0).
         */
        public static final String DTMF_TONE_WHEN_DIALING = "dtmf_tone";

        /**
         * Whether the sounds effects (key clicks, lid open ...) are enabled. The value is
         * boolean (1 or 0).
         */
        public static final String SOUND_EFFECTS_ENABLED = "sound_effects_enabled";

        /**
         * The preferred network mode 7 = Global, CDMA default
         *                            4 = CDMA only
         *                            3 = GSM/UMTS only
         */
        public static final String PREFERRED_NETWORK_MODE = 
                "preferred_network_mode";

        /**
         * CDMA Cell Broadcast SMS
         *                            0 = CDMA Cell Broadcast SMS disabled
         *                            1 = CDMA Cell Broadcast SMS enabled
         */
        public static final String CDMA_CELL_BROADCAST_SMS =
                "cdma_cell_broadcast_sms";

        /**
         * The cdma subscription 0 = Subscription from RUIM, when available
         *                       1 = Subscription from NV
         */
        public static final String PREFERRED_CDMA_SUBSCRIPTION = 
                "preferred_cdma_subscription";    
        
        /**
        * Whether the enhanced voice privacy mode is enabled.
        * 0 = normal voice privacy
        * 1 = enhanced voice privacy
        */
       public static final String ENHANCED_VOICE_PRIVACY_ENABLED = "enhanced_voice_privacy_enabled";
       
       /**
        * Whether the TTY mode mode is enabled.
        * 0 = disabled
        * 1 = enabled
        */
       public static final String TTY_MODE_ENABLED = "tty_mode_enabled";
    }


    /**
     * Gservices settings, containing the network names for Google's
     * various services. This table holds simple name/addr pairs.
     * Addresses can be accessed through the getString() method.
     * @hide
     */
    public static final class Gservices extends NameValueTable {
        public static final String SYS_PROP_SETTING_VERSION = "sys.settings_gservices_version";

        private static volatile NameValueCache mNameValueCache = null;
        private static final Object mNameValueCacheLock = new Object();

        /**
         * Look up a name in the database.
         * @param resolver to access the database with
         * @param name to look up in the table
         * @return the corresponding value, or null if not present
         */
        public static String getString(ContentResolver resolver, String name) {
            synchronized (mNameValueCacheLock) {
                if (mNameValueCache == null) {
                    mNameValueCache = new NameValueCache(SYS_PROP_SETTING_VERSION, CONTENT_URI);
                }
                return mNameValueCache.getString(resolver, name);
            }
        }

        /**
         * Store a name/value pair into the database.
         * @param resolver to access the database with
         * @param name to store
         * @param value to associate with the name
         * @return true if the value was set, false on database errors
         */
        public static boolean putString(ContentResolver resolver,
                String name, String value) {
            return putString(resolver, CONTENT_URI, name, value);
        }

        /**
         * Look up the value for name in the database, convert it to an int using Integer.parseInt
         * and return it. If it is null or if a NumberFormatException is caught during the
         * conversion then return defValue.
         */
        public static int getInt(ContentResolver resolver, String name, int defValue) {
            String valString = getString(resolver, name);
            int value;
            try {
                value = valString != null ? Integer.parseInt(valString) : defValue;
            } catch (NumberFormatException e) {
                value = defValue;
            }
            return value;
        }

        /**
         * Look up the value for name in the database, convert it to a long using Long.parseLong
         * and return it. If it is null or if a NumberFormatException is caught during the
         * conversion then return defValue.
         */
        public static long getLong(ContentResolver resolver, String name, long defValue) {
            String valString = getString(resolver, name);
            long value;
            try {
                value = valString != null ? Long.parseLong(valString) : defValue;
            } catch (NumberFormatException e) {
                value = defValue;
            }
            return value;
        }

        /**
         * Construct the content URI for a particular name/value pair,
         * useful for monitoring changes with a ContentObserver.
         * @param name to look up in the table
         * @return the corresponding content URI, or null if not present
         */
        public static Uri getUriFor(String name) {
            return getUriFor(CONTENT_URI, name);
        }

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/gservices");

        /**
         * MMS - URL to use for HTTP "x-wap-profile" header
         */
        public static final String MMS_X_WAP_PROFILE_URL
                = "mms_x_wap_profile_url";

        /**
         * YouTube - "most viewed" url
         */
        public static final String YOUTUBE_MOST_VIEWED_URL
                = "youtube_most_viewed_url";

        /**
         * YouTube - "most recent" url
         */
        public static final String YOUTUBE_MOST_RECENT_URL
                = "youtube_most_recent_url";

        /**
         * YouTube - "top favorites" url
         */
        public static final String YOUTUBE_TOP_FAVORITES_URL
                = "youtube_top_favorites_url";

        /**
         * YouTube - "most discussed" url
         */
        public static final String YOUTUBE_MOST_DISCUSSED_URL
                = "youtube_most_discussed_url";

        /**
         * YouTube - "most responded" url
         */
        public static final String YOUTUBE_MOST_RESPONDED_URL
                = "youtube_most_responded_url";

        /**
         * YouTube - "most linked" url
         */
        public static final String YOUTUBE_MOST_LINKED_URL
                = "youtube_most_linked_url";

        /**
         * YouTube - "top rated" url
         */
        public static final String YOUTUBE_TOP_RATED_URL
                = "youtube_top_rated_url";

        /**
         * YouTube - "recently featured" url
         */
        public static final String YOUTUBE_RECENTLY_FEATURED_URL
                = "youtube_recently_featured_url";

        /**
         * YouTube - my uploaded videos
         */
        public static final String YOUTUBE_MY_VIDEOS_URL
                = "youtube_my_videos_url";

        /**
         * YouTube - "my favorite" videos url
         */
        public static final String YOUTUBE_MY_FAVORITES_URL
                = "youtube_my_favorites_url";

        /**
         * YouTube - "by author" videos url -- used for My videos
         */
        public static final String YOUTUBE_BY_AUTHOR_URL
                = "youtube_by_author_url";

        /**
         * YouTube - save a video to favorite videos url
         */
        public static final String YOUTUBE_SAVE_TO_FAVORITES_URL
                = "youtube_save_to_favorites_url";

        /**
         * YouTube - "mobile" videos url
         */
        public static final String YOUTUBE_MOBILE_VIDEOS_URL
                = "youtube_mobile_videos_url";

        /**
         * YouTube - search videos url
         */
        public static final String YOUTUBE_SEARCH_URL
                = "youtube_search_url";

        /**
         * YouTube - category search videos url
         */
        public static final String YOUTUBE_CATEGORY_SEARCH_URL
                = "youtube_category_search_url";

        /**
         * YouTube - url to get the list of categories
         */
        public static final String YOUTUBE_CATEGORY_LIST_URL
                = "youtube_category_list_url";

        /**
         * YouTube - related videos url
         */
        public static final String YOUTUBE_RELATED_VIDEOS_URL
                = "youtube_related_videos_url";

        /**
         * YouTube - individual video url
         */
        public static final String YOUTUBE_INDIVIDUAL_VIDEO_URL
                = "youtube_individual_video_url";

        /**
         * YouTube - user's playlist url
         */
        public static final String YOUTUBE_MY_PLAYLISTS_URL
                = "youtube_my_playlists_url";

        /**
         * YouTube - user's subscriptions url
         */
        public static final String YOUTUBE_MY_SUBSCRIPTIONS_URL
                = "youtube_my_subscriptions_url";

        /**
         * YouTube - the url we use to contact YouTube to get a device id
         */
        public static final String YOUTUBE_REGISTER_DEVICE_URL
                = "youtube_register_device_url";

        /**
         * YouTube - the flag to indicate whether to use proxy
         */
        public static final String YOUTUBE_USE_PROXY
                = "youtube_use_proxy";

        /**
         * Event tags from the kernel event log to upload during checkin.
         */
        public static final String CHECKIN_EVENTS = "checkin_events";

        /**
         * The interval (in seconds) between periodic checkin attempts.
         */
        public static final String CHECKIN_INTERVAL = "checkin_interval";

        /**
         * How frequently (in seconds) to check the memory status of the
         * device.
         */
        public static final String MEMCHECK_INTERVAL = "memcheck_interval";

        /**
         * Max frequency (in seconds) to log memory check stats, in realtime
         * seconds.  This allows for throttling of logs when the device is
         * running for large amounts of time.
         */
        public static final String MEMCHECK_LOG_REALTIME_INTERVAL = 
                "memcheck_log_realtime_interval";

        /**
         * Boolean indicating whether rebooting due to system memory checks
         * is enabled.
         */
        public static final String MEMCHECK_SYSTEM_ENABLED = "memcheck_system_enabled";

        /**
         * How many bytes the system process must be below to avoid scheduling
         * a soft reboot.  This reboot will happen when it is next determined
         * to be a good time.
         */
        public static final String MEMCHECK_SYSTEM_SOFT_THRESHOLD = "memcheck_system_soft";

        /**
         * How many bytes the system process must be below to avoid scheduling
         * a hard reboot.  This reboot will happen immediately.
         */
        public static final String MEMCHECK_SYSTEM_HARD_THRESHOLD = "memcheck_system_hard";

        /**
         * How many bytes the phone process must be below to avoid scheduling
         * a soft restart.  This restart will happen when it is next determined
         * to be a good time.
         */
        public static final String MEMCHECK_PHONE_SOFT_THRESHOLD = "memcheck_phone_soft";

        /**
         * How many bytes the phone process must be below to avoid scheduling
         * a hard restart.  This restart will happen immediately.
         */
        public static final String MEMCHECK_PHONE_HARD_THRESHOLD = "memcheck_phone_hard";

        /**
         * Boolean indicating whether restarting the phone process due to
         * memory checks is enabled.
         */
        public static final String MEMCHECK_PHONE_ENABLED = "memcheck_phone_enabled";

        /**
         * First time during the day it is okay to kill processes
         * or reboot the device due to low memory situations.  This number is
         * in seconds since midnight.
         */
        public static final String MEMCHECK_EXEC_START_TIME = "memcheck_exec_start_time";

        /**
         * Last time during the day it is okay to kill processes
         * or reboot the device due to low memory situations.  This number is
         * in seconds since midnight.
         */
        public static final String MEMCHECK_EXEC_END_TIME = "memcheck_exec_end_time";

        /**
         * How long the screen must have been off in order to kill processes
         * or reboot.  This number is in seconds.  A value of -1 means to
         * entirely disregard whether the screen is on.
         */
        public static final String MEMCHECK_MIN_SCREEN_OFF = "memcheck_min_screen_off";

        /**
         * How much time there must be until the next alarm in order to kill processes
         * or reboot.  This number is in seconds.  Note: this value must be
         * smaller than {@link #MEMCHECK_RECHECK_INTERVAL} or else it will
         * always see an alarm scheduled within its time.
         */
        public static final String MEMCHECK_MIN_ALARM = "memcheck_min_alarm";

        /**
         * How frequently to check whether it is a good time to restart things,
         * if the device is in a bad state.  This number is in seconds.  Note:
         * this value must be larger than {@link #MEMCHECK_MIN_ALARM} or else
         * the alarm to schedule the recheck will always appear within the
         * minimum "do not execute now" time.
         */
        public static final String MEMCHECK_RECHECK_INTERVAL = "memcheck_recheck_interval";

        /**
         * How frequently (in DAYS) to reboot the device.  If 0, no reboots
         * will occur.
         */
        public static final String REBOOT_INTERVAL = "reboot_interval";

        /**
         * First time during the day it is okay to force a reboot of the
         * device (if REBOOT_INTERVAL is set).  This number is
         * in seconds since midnight.
         */
        public static final String REBOOT_START_TIME = "reboot_start_time";

        /**
         * The window of time (in seconds) after each REBOOT_INTERVAL in which
         * a reboot can be executed.  If 0, a reboot will always be executed at
         * exactly the given time.  Otherwise, it will only be executed if
         * the device is idle within the window.
         */
        public static final String REBOOT_WINDOW = "reboot_window";
        
        /**
         * The minimum version of the server that is required in order for the device to accept
         * the server's recommendations about the initial sync settings to use. When this is unset,
         * blank or can't be interpreted as an integer then we will not ask the server for a
         * recommendation.
         */
        public static final String GMAIL_CONFIG_INFO_MIN_SERVER_VERSION =
                "gmail_config_info_min_server_version";

        /**
         * Controls whether Gmail offers a preview button for images.
         */
        public static final String GMAIL_DISALLOW_IMAGE_PREVIEWS = "gmail_disallow_image_previews";

        /**
         * The timeout in milliseconds that Gmail uses when opening a connection and reading
         * from it. A missing value or a value of -1 instructs Gmail to use the defaults provided
         * by GoogleHttpClient.
         */
        public static final String GMAIL_TIMEOUT_MS = "gmail_timeout_ms";

        /**
         * Hostname of the GTalk server.
         */
        public static final String GTALK_SERVICE_HOSTNAME = "gtalk_hostname";

        /**
         * Secure port of the GTalk server.
         */
        public static final String GTALK_SERVICE_SECURE_PORT = "gtalk_secure_port";

        /**
         * The server configurable RMQ acking interval
         */
        public static final String GTALK_SERVICE_RMQ_ACK_INTERVAL = "gtalk_rmq_ack_interval";

        /**
         * The minimum reconnect delay for short network outages or when the network is suspended
         * due to phone use.
         */
        public static final String GTALK_SERVICE_MIN_RECONNECT_DELAY_SHORT =
                "gtalk_min_reconnect_delay_short";

        /**
         * The reconnect variant range for short network outages or when the network is suspended
         * due to phone use. A random number between 0 and this constant is computed and
         * added to {@link #GTALK_SERVICE_MIN_RECONNECT_DELAY_SHORT} to form the initial reconnect
         * delay.
         */
        public static final String GTALK_SERVICE_RECONNECT_VARIANT_SHORT =
                "gtalk_reconnect_variant_short";

        /**
         * The minimum reconnect delay for long network outages
         */
        public static final String GTALK_SERVICE_MIN_RECONNECT_DELAY_LONG =
                "gtalk_min_reconnect_delay_long";

        /**
         * The reconnect variant range for long network outages.  A random number between 0 and this
         * constant is computed and added to {@link #GTALK_SERVICE_MIN_RECONNECT_DELAY_LONG} to
         * form the initial reconnect delay.
         */
        public static final String GTALK_SERVICE_RECONNECT_VARIANT_LONG =
                "gtalk_reconnect_variant_long";

        /**
         * The maximum reconnect delay time, in milliseconds.
         */
        public static final String GTALK_SERVICE_MAX_RECONNECT_DELAY =
                "gtalk_max_reconnect_delay";

        /**
         * The network downtime that is considered "short" for the above calculations,
         * in milliseconds.
         */
        public static final String GTALK_SERVICE_SHORT_NETWORK_DOWNTIME =
                "gtalk_short_network_downtime";

        /**
         * How frequently we send heartbeat pings to the GTalk server. Receiving a server packet
         * will reset the heartbeat timer. The away heartbeat should be used when the user is
         * logged into the GTalk app, but not actively using it.
         */
        public static final String GTALK_SERVICE_AWAY_HEARTBEAT_INTERVAL_MS =
                "gtalk_heartbeat_ping_interval_ms";  // keep the string backward compatible

        /**
         * How frequently we send heartbeat pings to the GTalk server. Receiving a server packet
         * will reset the heartbeat timer. The active heartbeat should be used when the user is
         * actively using the GTalk app.
         */
        public static final String GTALK_SERVICE_ACTIVE_HEARTBEAT_INTERVAL_MS =
                "gtalk_active_heartbeat_ping_interval_ms";

        /**
         * How frequently we send heartbeat pings to the GTalk server. Receiving a server packet
         * will reset the heartbeat timer. The sync heartbeat should be used when the user isn't
         * logged into the GTalk app, but auto-sync is enabled.
         */
        public static final String GTALK_SERVICE_SYNC_HEARTBEAT_INTERVAL_MS =
                "gtalk_sync_heartbeat_ping_interval_ms";

        /**
         * How frequently we send heartbeat pings to the GTalk server. Receiving a server packet
         * will reset the heartbeat timer. The no sync heartbeat should be used when the user isn't
         * logged into the GTalk app, and auto-sync is not enabled.
         */
        public static final String GTALK_SERVICE_NOSYNC_HEARTBEAT_INTERVAL_MS =
                "gtalk_nosync_heartbeat_ping_interval_ms";

        /**
         * How long we wait to receive a heartbeat ping acknowledgement (or another packet)
         * from the GTalk server, before deeming the connection dead.
         */
        public static final String GTALK_SERVICE_HEARTBEAT_ACK_TIMEOUT_MS =
                "gtalk_heartbeat_ack_timeout_ms";

        /**
         * How long after screen is turned off before we consider the user to be idle.
         */
        public static final String GTALK_SERVICE_IDLE_TIMEOUT_MS =
                "gtalk_idle_timeout_ms";

        /**
         * By default, GTalkService will always connect to the server regardless of the auto-sync
         * setting. However, if this parameter is true, then GTalkService will only connect
         * if auto-sync is enabled. Using the GTalk app will trigger the connection too.
         */
        public static final String GTALK_SERVICE_CONNECT_ON_AUTO_SYNC =
                "gtalk_connect_on_auto_sync";

        /**
         * GTalkService holds a wakelock while broadcasting the intent for data message received.
         * It then automatically release the wakelock after a timeout. This setting controls what
         * the timeout should be.
         */
        public static final String GTALK_DATA_MESSAGE_WAKELOCK_MS =
                "gtalk_data_message_wakelock_ms";

        /**
         * The socket read timeout used to control how long ssl handshake wait for reads before
         * timing out. This is needed so the ssl handshake doesn't hang for a long time in some
         * circumstances.
         */
        public static final String GTALK_SSL_HANDSHAKE_TIMEOUT_MS =
                "gtalk_ssl_handshake_timeout_ms";

        /**
         * How many bytes long a message has to be, in order to be gzipped.
         */
        public static final String SYNC_MIN_GZIP_BYTES =
                "sync_min_gzip_bytes";

        /**
         * The hash value of the current provisioning settings
         */
        public static final String PROVISIONING_DIGEST = "digest";

        /**
         * Provisioning keys to block from server update
         */
        public static final String PROVISIONING_OVERRIDE = "override";

        /**
         * "Generic" service name for  authentication requests.
         */
        public static final String GOOGLE_LOGIN_GENERIC_AUTH_SERVICE
                = "google_login_generic_auth_service";

        /**
         * Frequency in milliseconds at which we should sync the locally installed Vending Machine
         * content with the server.
         */
        public static final String VENDING_SYNC_FREQUENCY_MS = "vending_sync_frequency_ms";

        /**
         * Support URL that is opened in a browser when user clicks on 'Help and Info' in Vending
         * Machine.
         */
        public static final String VENDING_SUPPORT_URL = "vending_support_url";

        /**
         * Indicates if Vending Machine requires a SIM to be in the phone to allow a purchase.
         *
         * true = SIM is required
         * false = SIM is not required
         */
        public static final String VENDING_REQUIRE_SIM_FOR_PURCHASE =
                "vending_require_sim_for_purchase";

        /**
         * The current version id of the Vending Machine terms of service.
         */
        public static final String VENDING_TOS_VERSION = "vending_tos_version";

        /**
         * URL that points to the terms of service for Vending Machine.
         */
        public static final String VENDING_TOS_URL = "vending_tos_url";

        /**
         * Whether to use sierraqa instead of sierra tokens for the purchase flow in
         * Vending Machine.
         *
         * true = use sierraqa
         * false = use sierra (default)
         */
        public static final String VENDING_USE_CHECKOUT_QA_SERVICE =
                "vending_use_checkout_qa_service";

        /**
         * URL that points to the legal terms of service to display in Settings.
         * <p>
         * This should be a https URL. For a pretty user-friendly URL, use
         * {@link #SETTINGS_TOS_PRETTY_URL}.
         */
        public static final String SETTINGS_TOS_URL = "settings_tos_url";

        /**
         * URL that points to the legal terms of service to display in Settings.
         * <p>
         * This should be a pretty http URL. For the URL the device will access
         * via Settings, use {@link #SETTINGS_TOS_URL}.
         */
        public static final String SETTINGS_TOS_PRETTY_URL = "settings_tos_pretty_url";

        /**
         * URL that points to the contributors to display in Settings.
         * <p>
         * This should be a https URL. For a pretty user-friendly URL, use
         * {@link #SETTINGS_CONTRIBUTORS_PRETTY_URL}.
         */
        public static final String SETTINGS_CONTRIBUTORS_URL = "settings_contributors_url";

        /**
         * URL that points to the contributors to display in Settings.
         * <p>
         * This should be a pretty http URL. For the URL the device will access
         * via Settings, use {@link #SETTINGS_CONTRIBUTORS_URL}.
         */
        public static final String SETTINGS_CONTRIBUTORS_PRETTY_URL =
                "settings_contributors_pretty_url";

        /**
         * Request an MSISDN token for various Google services.
         */
        public static final String USE_MSISDN_TOKEN = "use_msisdn_token";

        /**
         * RSA public key used to encrypt passwords stored in the database.
         */
        public static final String GLS_PUBLIC_KEY = "google_login_public_key";

        /**
         * Only check parental control status if this is set to "true".
         */
        public static final String PARENTAL_CONTROL_CHECK_ENABLED =
                "parental_control_check_enabled";


        /**
         * Duration in which parental control status is valid.
         */
        public static final String PARENTAL_CONTROL_TIMEOUT_IN_MS =
                "parental_control_timeout_in_ms";

        /**
         * When parental control is off, we expect to get this string from the
         * litmus url.
         */
        public static final String PARENTAL_CONTROL_EXPECTED_RESPONSE =
                "parental_control_expected_response";

        /**
         * When the litmus url returns a 302, declare parental control to be on
         * only if the redirect url matches this regular expression.
         */
        public static final String PARENTAL_CONTROL_REDIRECT_REGEX =
                "parental_control_redirect_regex";

        /**
         * Threshold for the amount of change in disk free space required to report the amount of
         * free space. Used to prevent spamming the logs when the disk free space isn't changing
         * frequently.
         */
        public static final String DISK_FREE_CHANGE_REPORTING_THRESHOLD =
                "disk_free_change_reporting_threshold";
        
        /**
         * Prefix for new Google services published by the checkin
         * server.
         */
        public static final String GOOGLE_SERVICES_PREFIX
                = "google_services:";

        /**
         * The maximum reconnect delay for short network outages or when the network is suspended
         * due to phone use.
         */
        public static final String SYNC_MAX_RETRY_DELAY_IN_SECONDS =
                "sync_max_retry_delay_in_seconds";
        
        /**
         * Minimum percentage of free storage on the device that is used to determine if
         * the device is running low on storage. 
         * Say this value is set to 10, the device is considered running low on storage
         * if 90% or more of the device storage is filled up.
         */
        public static final String SYS_STORAGE_THRESHOLD_PERCENTAGE = 
                "sys_storage_threshold_percentage";
        
        /**
         * The interval in minutes after which the amount of free storage left on the 
         * device is logged to the event log
         */
        public static final String SYS_FREE_STORAGE_LOG_INTERVAL = 
                "sys_free_storage_log_interval";

        /**
         * The interval in milliseconds at which to check packet counts on the
         * mobile data interface when screen is on, to detect possible data
         * connection problems.
         */
        public static final String PDP_WATCHDOG_POLL_INTERVAL_MS =
                "pdp_watchdog_poll_interval_ms";

        /**
         * The interval in milliseconds at which to check packet counts on the
         * mobile data interface when screen is off, to detect possible data
         * connection problems.
         */
        public static final String PDP_WATCHDOG_LONG_POLL_INTERVAL_MS =
            "pdp_watchdog_long_poll_interval_ms";
        
        /**
         * The interval in milliseconds at which to check packet counts on the
         * mobile data interface after {@link #PDP_WATCHDOG_TRIGGER_PACKET_COUNT}
         * outgoing packets has been reached without incoming packets.
         */
        public static final String PDP_WATCHDOG_ERROR_POLL_INTERVAL_MS = 
                "pdp_watchdog_error_poll_interval_ms";

        /**
         * The number of outgoing packets sent without seeing an incoming packet
         * that triggers a countdown (of {@link #PDP_WATCHDOG_ERROR_POLL_COUNT} 
         * device is logged to the event log
         */
        public static final String PDP_WATCHDOG_TRIGGER_PACKET_COUNT = 
                "pdp_watchdog_trigger_packet_count";

        /**
         * The number of polls to perform (at {@link #PDP_WATCHDOG_ERROR_POLL_INTERVAL_MS})
         * after hitting {@link #PDP_WATCHDOG_TRIGGER_PACKET_COUNT} before
         * attempting data connection recovery.
         */
        public static final String PDP_WATCHDOG_ERROR_POLL_COUNT = 
                "pdp_watchdog_error_poll_count";

        /**
         * The number of failed PDP reset attempts before moving to something more
         * drastic: re-registering to the network.
         */
        public static final String PDP_WATCHDOG_MAX_PDP_RESET_FAIL_COUNT = 
                "pdp_watchdog_max_pdp_reset_fail_count";

        /**
         * Address to ping as a last sanity check before attempting any recovery.
         * Unset or set to "0.0.0.0" to skip this check.
         */
        public static final String PDP_WATCHDOG_PING_ADDRESS = 
                "pdp_watchdog_ping_address";

        /**
         * The "-w deadline" parameter for the ping, ie, the max time in
         * seconds to spend pinging.
         */
        public static final String PDP_WATCHDOG_PING_DEADLINE = 
                "pdp_watchdog_ping_deadline";

        /**
         * The interval in milliseconds after which Wi-Fi is considered idle.
         * When idle, it is possible for the device to be switched from Wi-Fi to
         * the mobile data network.
         */
        public static final String WIFI_IDLE_MS = "wifi_idle_ms";

        /**
         * The interval in milliseconds at which we forcefully release the
         * transition-to-mobile-data wake lock.
         */
        public static final String WIFI_MOBILE_DATA_TRANSITION_WAKELOCK_TIMEOUT_MS =
                "wifi_mobile_data_transition_wakelock_timeout_ms";

        /**
         * The maximum number of times we will retry a connection to an access
         * point for which we have failed in acquiring an IP address from DHCP.
         * A value of N means that we will make N+1 connection attempts in all.
         */
        public static final String WIFI_MAX_DHCP_RETRY_COUNT = "wifi_max_dhcp_retry_count";

        /**
         * @deprecated
         * @hide
         */
        @Deprecated  // Obviated by NameValueCache: just fetch the value directly.
        public static class QueryMap extends ContentQueryMap {

            public QueryMap(ContentResolver contentResolver, Cursor cursor, boolean keepUpdated,
                    Handler handlerForUpdateNotifications) {
                super(cursor, NAME, keepUpdated, handlerForUpdateNotifications);
            }

            public QueryMap(ContentResolver contentResolver, boolean keepUpdated,
                    Handler handlerForUpdateNotifications) {
                this(contentResolver,
                        contentResolver.query(CONTENT_URI, null, null, null, null),
                        keepUpdated, handlerForUpdateNotifications);
            }

            public String getString(String name) {
                ContentValues cv = getValues(name);
                if (cv == null) return null;
                return cv.getAsString(VALUE);
            }
        }

    }

    /**
     * User-defined bookmarks and shortcuts.  The target of each bookmark is an
     * Intent URL, allowing it to be either a web page or a particular
     * application activity.
     *
     * @hide
     */
    public static final class Bookmarks implements BaseColumns {
        private static final String TAG = "Bookmarks";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/bookmarks");

        /**
         * The row ID.
         * <p>Type: INTEGER</p>
         */
        public static final String ID = "_id";

        /**
         * Descriptive name of the bookmark that can be displayed to the user.
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * Arbitrary string (displayed to the user) that allows bookmarks to be
         * organized into categories.  There are some special names for
         * standard folders, which all start with '@'.  The label displayed for
         * the folder changes with the locale (via {@link #labelForFolder}) but
         * the folder name does not change so you can consistently query for
         * the folder regardless of the current locale.
         *
         * <P>Type: TEXT</P>
         *
         */
        public static final String FOLDER = "folder";

        /**
         * The Intent URL of the bookmark, describing what it points to.  This
         * value is given to {@link android.content.Intent#getIntent} to create
         * an Intent that can be launched.
         * <P>Type: TEXT</P>
         */
        public static final String INTENT = "intent";

        /**
         * Optional shortcut character associated with this bookmark.
         * <P>Type: INTEGER</P>
         */
        public static final String SHORTCUT = "shortcut";

        /**
         * The order in which the bookmark should be displayed
         * <P>Type: INTEGER</P>
         */
        public static final String ORDERING = "ordering";

        private static final String[] sIntentProjection = { INTENT };
        private static final String[] sShortcutProjection = { ID, SHORTCUT };
        private static final String sShortcutSelection = SHORTCUT + "=?";

        /**
         * Convenience function to retrieve the bookmarked Intent for a
         * particular shortcut key.
         *
         * @param cr The ContentResolver to query.
         * @param shortcut The shortcut key.
         *
         * @return Intent The bookmarked URL, or null if there is no bookmark
         *         matching the given shortcut.
         */
        public static Intent getIntentForShortcut(ContentResolver cr, char shortcut) {
            Intent intent = null;

            Cursor c = cr.query(CONTENT_URI,
                    sIntentProjection, sShortcutSelection,
                    new String[] { String.valueOf((int) shortcut) }, ORDERING);
            // Keep trying until we find a valid shortcut
            try {
                while (intent == null && c.moveToNext()) {
                    try {
                        String intentURI = c.getString(c.getColumnIndexOrThrow(INTENT));
                        intent = Intent.getIntent(intentURI);
                    } catch (java.net.URISyntaxException e) {
                        // The stored URL is bad...  ignore it.
                    } catch (IllegalArgumentException e) {
                        // Column not found
                        Log.e(TAG, "Intent column not found", e);
                    }
                }
            } finally {
                if (c != null) c.close();
            }

            return intent;
        }

        /**
         * Add a new bookmark to the system.
         *
         * @param cr The ContentResolver to query.
         * @param intent The desired target of the bookmark.
         * @param title Bookmark title that is shown to the user; null if none.
         * @param folder Folder in which to place the bookmark; null if none.
         * @param shortcut Shortcut that will invoke the bookmark; 0 if none.
         *                 If this is non-zero and there is an existing
         *                 bookmark entry with this same shortcut, then that
         *                 existing shortcut is cleared (the bookmark is not
         *                 removed).
         *
         * @return The unique content URL for the new bookmark entry.
         */
        public static Uri add(ContentResolver cr,
                                           Intent intent,
                                           String title,
                                           String folder,
                                           char shortcut,
                                           int ordering) {
            // If a shortcut is supplied, and it is already defined for
            // another bookmark, then remove the old definition.
            if (shortcut != 0) {
                Cursor c = cr.query(CONTENT_URI,
                        sShortcutProjection, sShortcutSelection,
                        new String[] { String.valueOf((int) shortcut) }, null);
                try {
                    if (c.moveToFirst()) {
                        while (c.getCount() > 0) {
                            if (!c.deleteRow()) {
                                Log.w(TAG, "Could not delete existing shortcut row");
                            }
                        }
                    }
                } finally {
                    if (c != null) c.close();
                }
            }

            ContentValues values = new ContentValues();
            if (title != null) values.put(TITLE, title);
            if (folder != null) values.put(FOLDER, folder);
            values.put(INTENT, intent.toURI());
            if (shortcut != 0) values.put(SHORTCUT, (int) shortcut);
            values.put(ORDERING, ordering);
            return cr.insert(CONTENT_URI, values);
        }

        /**
         * Return the folder name as it should be displayed to the user.  This
         * takes care of localizing special folders.
         *
         * @param r Resources object for current locale; only need access to
         *          system resources.
         * @param folder The value found in the {@link #FOLDER} column.
         *
         * @return CharSequence The label for this folder that should be shown
         *         to the user.
         */
        public static CharSequence labelForFolder(Resources r, String folder) {
            return folder;
        }
    }

    /**
     * Returns the GTalk JID resource associated with this device.
     *
     * @return  String  the JID resource of the device. It uses the Device ID (IMEI for GSM and MEID
     * for CDMA) in the computation.
     * of the JID resource. If Device ID is not ready (i.e. telephony module not ready), we'll
     * return an empty string.
     * @hide
     */
    // TODO: we shouldn't not have a permenant Jid resource, as that's an easy target for
    // spams. We should change it once a while, like when we resubscribe to the subscription feeds
    // server.
    // (also, should this live in GTalkService?)
    public static synchronized String getJidResource() {
        if (sJidResource != null) {
            return sJidResource;
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("this should never happen");
        }

        String deviceId = TelephonyManager.getDefault().getDeviceId();
        if (TextUtils.isEmpty(deviceId)) {
            return "";
        }

        byte[] hashedDeviceId = digest.digest(deviceId.getBytes());
        String id = new String(Base64.encodeBase64(hashedDeviceId), 0, 12);
        id = id.replaceAll("/", "_");
        sJidResource = JID_RESOURCE_PREFIX + id;
        return sJidResource;
    }

    /**
     * Returns the device ID that we should use when connecting to the mobile gtalk server.
     * This is a string like "android-0x1242", where the hex string is the Android ID obtained
     * from the GoogleLoginService.
     *
     * @param androidId The Android ID for this device.
     * @return The device ID that should be used when connecting to the mobile gtalk server.
     * @hide
     */
    public static String getGTalkDeviceId(long androidId) {
        return "android-" + Long.toHexString(androidId);
    }
}
