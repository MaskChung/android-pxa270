/*
 * Copyright 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "wifi"

#include "jni.h"
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>

#include "wifi.h"

#define WIFI_PKG_NAME "android/net/wifi/WifiNative"

namespace android {

/*
 * The following remembers the jfieldID's of the fields
 * of the DhcpInfo Java object, so that we don't have
 * to look them up every time.
 */
static struct fieldIds {
    jclass dhcpInfoClass;
    jmethodID constructorId;
    jfieldID ipaddress;
    jfieldID gateway;
    jfieldID netmask;
    jfieldID dns1;
    jfieldID dns2;
    jfieldID serverAddress;
    jfieldID leaseDuration;
} dhcpInfoFieldIds;

static int doCommand(const char *cmd, char *replybuf, int replybuflen)
{
    size_t reply_len = replybuflen - 1;

    if (::wifi_command(cmd, replybuf, &reply_len) != 0)
        return -1;
    else {
        // Strip off trailing newline
        if (reply_len > 0 && replybuf[reply_len-1] == '\n')
            replybuf[reply_len-1] = '\0';
        else
            replybuf[reply_len] = '\0';
        return 0;
    }
}

static jint doIntCommand(const char *cmd)
{
    char reply[256];

    if (doCommand(cmd, reply, sizeof(reply)) != 0) {
        return (jint)-1;
    } else {
        return (jint)atoi(reply);
    }
}

static jboolean doBooleanCommand(const char *cmd, const char *expect)
{
    char reply[256];

    if (doCommand(cmd, reply, sizeof(reply)) != 0) {
        return (jboolean)JNI_FALSE;
    } else {
        return (jboolean)(strcmp(reply, expect) == 0);
    }
}

// Send a command to the supplicant, and return the reply as a String
static jstring doStringCommand(JNIEnv *env, const char *cmd)
{
    char reply[4096];

    if (doCommand(cmd, reply, sizeof(reply)) != 0) {
        return env->NewStringUTF(NULL);
    } else {
        return env->NewStringUTF(reply);
    }
}

static jboolean android_net_wifi_loadDriver(JNIEnv* env, jobject clazz)
{
    return (jboolean)(::wifi_load_driver() == 0);
}

static jboolean android_net_wifi_unloadDriver(JNIEnv* env, jobject clazz)
{
    return (jboolean)(::wifi_unload_driver() == 0);
}

static jboolean android_net_wifi_startSupplicant(JNIEnv* env, jobject clazz)
{
    return (jboolean)(::wifi_start_supplicant() == 0);
}

static jboolean android_net_wifi_stopSupplicant(JNIEnv* env, jobject clazz)
{
    return (jboolean)(::wifi_stop_supplicant() == 0);
}

static jboolean android_net_wifi_connectToSupplicant(JNIEnv* env, jobject clazz)
{
    return (jboolean)(::wifi_connect_to_supplicant() == 0);
}

static void android_net_wifi_closeSupplicantConnection(JNIEnv* env, jobject clazz)
{
    ::wifi_close_supplicant_connection();
}

static jstring android_net_wifi_waitForEvent(JNIEnv* env, jobject clazz)
{
    char buf[256];

    int nread = ::wifi_wait_for_event(buf, sizeof buf);
    if (nread > 0) {
        return env->NewStringUTF(buf);
    } else {
        return  env->NewStringUTF(NULL);
    }
}

static jstring android_net_wifi_listNetworksCommand(JNIEnv* env, jobject clazz)
{
    return doStringCommand(env, "LIST_NETWORKS");
}

static jint android_net_wifi_addNetworkCommand(JNIEnv* env, jobject clazz)
{
    return doIntCommand("ADD_NETWORK");
}

static jboolean android_net_wifi_setNetworkVariableCommand(JNIEnv* env,
                                                           jobject clazz,
                                                           jint netId,
                                                           jstring name,
                                                           jstring value)
{
    char cmdstr[256];
    jboolean isCopy;

    const char *nameStr = env->GetStringUTFChars(name, &isCopy);
    const char *valueStr = env->GetStringUTFChars(value, &isCopy);

    if (nameStr == NULL || valueStr == NULL)
        return JNI_FALSE;

    int cmdTooLong = snprintf(cmdstr, sizeof(cmdstr), "SET_NETWORK %d %s %s",
                 netId, nameStr, valueStr) >= (int)sizeof(cmdstr);

    env->ReleaseStringUTFChars(name, nameStr);
    env->ReleaseStringUTFChars(value, valueStr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jstring android_net_wifi_getNetworkVariableCommand(JNIEnv* env,
                                                          jobject clazz,
                                                          jint netId,
                                                          jstring name)
{
    char cmdstr[256];
    jboolean isCopy;

    const char *nameStr = env->GetStringUTFChars(name, &isCopy);

    if (nameStr == NULL)
        return env->NewStringUTF(NULL);

    int cmdTooLong = snprintf(cmdstr, sizeof(cmdstr), "GET_NETWORK %d %s",
                             netId, nameStr) >= (int)sizeof(cmdstr);

    env->ReleaseStringUTFChars(name, nameStr);

    return cmdTooLong ? env->NewStringUTF(NULL) : doStringCommand(env, cmdstr);
}

static jboolean android_net_wifi_removeNetworkCommand(JNIEnv* env, jobject clazz, jint netId)
{
    char cmdstr[256];

    int numWritten = snprintf(cmdstr, sizeof(cmdstr), "REMOVE_NETWORK %d", netId);
    int cmdTooLong = numWritten >= (int)sizeof(cmdstr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jboolean android_net_wifi_enableNetworkCommand(JNIEnv* env,
                                                  jobject clazz,
                                                  jint netId,
                                                  jboolean disableOthers)
{
    char cmdstr[256];
    const char *cmd = disableOthers ? "SELECT_NETWORK" : "ENABLE_NETWORK";

    int numWritten = snprintf(cmdstr, sizeof(cmdstr), "%s %d", cmd, netId);
    int cmdTooLong = numWritten >= (int)sizeof(cmdstr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jboolean android_net_wifi_disableNetworkCommand(JNIEnv* env, jobject clazz, jint netId)
{
    char cmdstr[256];

    int numWritten = snprintf(cmdstr, sizeof(cmdstr), "DISABLE_NETWORK %d", netId);
    int cmdTooLong = numWritten >= (int)sizeof(cmdstr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jstring android_net_wifi_statusCommand(JNIEnv* env, jobject clazz)
{
    return doStringCommand(env, "STATUS");
}

static jboolean android_net_wifi_pingCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("PING", "PONG");
}

static jstring android_net_wifi_scanResultsCommand(JNIEnv* env, jobject clazz)
{
    return doStringCommand(env, "SCAN_RESULTS");
}

static jboolean android_net_wifi_disconnectCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("DISCONNECT", "OK");
}

static jboolean android_net_wifi_reconnectCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("RECONNECT", "OK");
}
static jboolean android_net_wifi_reassociateCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("REASSOCIATE", "OK");
}

static jboolean android_net_wifi_scanCommand(JNIEnv* env, jobject clazz)
{
    jboolean result;
    // Ignore any error from setting the scan mode.
    // The scan will still work.
    (void)doBooleanCommand("DRIVER SCAN-ACTIVE", "OK");
    result = doBooleanCommand("SCAN", "OK");
    (void)doBooleanCommand("DRIVER SCAN-PASSIVE", "OK");
    return result;
}

static jboolean android_net_wifi_setScanModeCommand(JNIEnv* env, jobject clazz, jboolean setActive)
{
    jboolean result;
    // Ignore any error from setting the scan mode.
    // The scan will still work.
    if (setActive) {
        return doBooleanCommand("DRIVER SCAN-ACTIVE", "OK");
    } else {
        return doBooleanCommand("DRIVER SCAN-PASSIVE", "OK");
    }
}

static jboolean android_net_wifi_startDriverCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("DRIVER START", "OK");
}

static jboolean android_net_wifi_stopDriverCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("DRIVER STOP", "OK");
}

static jint android_net_wifi_getRssiCommand(JNIEnv* env, jobject clazz)
{
    char reply[256];
    int rssi = -200;

    if (doCommand("DRIVER RSSI", reply, sizeof(reply)) != 0) {
        return (jint)-1;
    }
    // reply comes back in the form "<SSID> rssi XX" where XX is the
    // number we're interested in.  if we're associating, it returns "OK".
    if (strcmp(reply, "OK") != 0) {
    	sscanf(reply, "%*s %*s %d", &rssi);
    }
    return (jint)rssi;
}

static jint android_net_wifi_getLinkSpeedCommand(JNIEnv* env, jobject clazz)
{
    char reply[256];
    int linkspeed;

    if (doCommand("DRIVER LINKSPEED", reply, sizeof(reply)) != 0) {
        return (jint)-1;
    }
    // reply comes back in the form "LinkSpeed XX" where XX is the
    // number we're interested in.
    sscanf(reply, "%*s %u", &linkspeed);
    return (jint)linkspeed;
}

static jstring android_net_wifi_getMacAddressCommand(JNIEnv* env, jobject clazz)
{
    char reply[256];
    char buf[256];

    if (doCommand("DRIVER MACADDR", reply, sizeof(reply)) != 0) {
        return env->NewStringUTF(NULL);
    }
    // reply comes back in the form "Macaddr = XX.XX.XX.XX.XX.XX" where XX
    // is the part of the string we're interested in.
    if (sscanf(reply, "%*s = %255s", buf) == 1)
        return env->NewStringUTF(buf);
    else
        return env->NewStringUTF(NULL);
}

static jboolean android_net_wifi_setPowerModeCommand(JNIEnv* env, jobject clazz, jint mode)
{
    char cmdstr[256];

    int numWritten = snprintf(cmdstr, sizeof(cmdstr), "DRIVER POWERMODE %d", mode);
    int cmdTooLong = numWritten >= (int)sizeof(cmdstr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jboolean android_net_wifi_setBluetoothCoexistenceModeCommand(JNIEnv* env, jobject clazz, jint mode)
{
    char cmdstr[256];

    int numWritten = snprintf(cmdstr, sizeof(cmdstr), "DRIVER BTCOEXMODE %d", mode);
    int cmdTooLong = numWritten >= (int)sizeof(cmdstr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jboolean android_net_wifi_saveConfigCommand(JNIEnv* env, jobject clazz)
{
    // Make sure we never write out a value for AP_SCAN other than 1
    (void)doBooleanCommand("AP_SCAN 1", "OK");
    return doBooleanCommand("SAVE_CONFIG", "OK");
}

static jboolean android_net_wifi_reloadConfigCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("RECONFIGURE", "OK");
}

static jboolean android_net_wifi_setScanResultHandlingCommand(JNIEnv* env, jobject clazz, jint mode)
{
    char cmdstr[256];

    int numWritten = snprintf(cmdstr, sizeof(cmdstr), "AP_SCAN %d", mode);
    int cmdTooLong = numWritten >= (int)sizeof(cmdstr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jboolean android_net_wifi_addToBlacklistCommand(JNIEnv* env, jobject clazz, jstring bssid)
{
    char cmdstr[256];
    jboolean isCopy;

    const char *bssidStr = env->GetStringUTFChars(bssid, &isCopy);

    int cmdTooLong = snprintf(cmdstr, sizeof(cmdstr), "BLACKLIST %s", bssidStr) >= sizeof(cmdstr);

    env->ReleaseStringUTFChars(bssid, bssidStr);

    return (jboolean)!cmdTooLong && doBooleanCommand(cmdstr, "OK");
}

static jboolean android_net_wifi_clearBlacklistCommand(JNIEnv* env, jobject clazz)
{
    return doBooleanCommand("BLACKLIST clear", "OK");
}

static jboolean android_net_wifi_doDhcpRequest(JNIEnv* env, jobject clazz, jobject info)
{
    jint ipaddr, gateway, mask, dns1, dns2, server, lease;
    jboolean succeeded = ((jboolean)::do_dhcp_request(&ipaddr, &gateway, &mask,
                                        &dns1, &dns2, &server, &lease) == 0);
    if (succeeded && dhcpInfoFieldIds.dhcpInfoClass != NULL) {
        env->SetIntField(info, dhcpInfoFieldIds.ipaddress, ipaddr);
        env->SetIntField(info, dhcpInfoFieldIds.gateway, gateway);
        env->SetIntField(info, dhcpInfoFieldIds.netmask, mask);
        env->SetIntField(info, dhcpInfoFieldIds.dns1, dns1);
        env->SetIntField(info, dhcpInfoFieldIds.dns2, dns2);
        env->SetIntField(info, dhcpInfoFieldIds.serverAddress, server);
        env->SetIntField(info, dhcpInfoFieldIds.leaseDuration, lease);
    }
    return succeeded;
}

static jstring android_net_wifi_getDhcpError(JNIEnv* env, jobject clazz)
{
    return env->NewStringUTF(::get_dhcp_error_string());
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gWifiMethods[] = {
    /* name, signature, funcPtr */

    { "loadDriver", "()Z",  (void *)android_net_wifi_loadDriver },
    { "unloadDriver", "()Z",  (void *)android_net_wifi_unloadDriver },
    { "startSupplicant", "()Z",  (void *)android_net_wifi_startSupplicant },
    { "stopSupplicant", "()Z",  (void *)android_net_wifi_stopSupplicant },
    { "connectToSupplicant", "()Z",  (void *)android_net_wifi_connectToSupplicant },
    { "closeSupplicantConnection", "()V",  (void *)android_net_wifi_closeSupplicantConnection },

    { "listNetworksCommand", "()Ljava/lang/String;",
        (void*) android_net_wifi_listNetworksCommand },
    { "addNetworkCommand", "()I", (void*) android_net_wifi_addNetworkCommand },
    { "setNetworkVariableCommand", "(ILjava/lang/String;Ljava/lang/String;)Z",
        (void*) android_net_wifi_setNetworkVariableCommand },
    { "getNetworkVariableCommand", "(ILjava/lang/String;)Ljava/lang/String;",
        (void*) android_net_wifi_getNetworkVariableCommand },
    { "removeNetworkCommand", "(I)Z", (void*) android_net_wifi_removeNetworkCommand },
    { "enableNetworkCommand", "(IZ)Z", (void*) android_net_wifi_enableNetworkCommand },
    { "disableNetworkCommand", "(I)Z", (void*) android_net_wifi_disableNetworkCommand },
    { "waitForEvent", "()Ljava/lang/String;", (void*) android_net_wifi_waitForEvent },
    { "statusCommand", "()Ljava/lang/String;", (void*) android_net_wifi_statusCommand },
    { "scanResultsCommand", "()Ljava/lang/String;", (void*) android_net_wifi_scanResultsCommand },
    { "pingCommand", "()Z",  (void *)android_net_wifi_pingCommand },
    { "disconnectCommand", "()Z",  (void *)android_net_wifi_disconnectCommand },
    { "reconnectCommand", "()Z",  (void *)android_net_wifi_reconnectCommand },
    { "reassociateCommand", "()Z",  (void *)android_net_wifi_reassociateCommand },
    { "scanCommand", "()Z", (void*) android_net_wifi_scanCommand },
    { "setScanModeCommand", "(Z)Z", (void*) android_net_wifi_setScanModeCommand },
    { "startDriverCommand", "()Z", (void*) android_net_wifi_startDriverCommand },
    { "stopDriverCommand", "()Z", (void*) android_net_wifi_stopDriverCommand },
    { "setPowerModeCommand", "(I)Z", (void*) android_net_wifi_setPowerModeCommand },
    { "setBluetoothCoexistenceModeCommand", "(I)Z",
    		(void*) android_net_wifi_setBluetoothCoexistenceModeCommand },
    { "getRssiCommand", "()I", (void*) android_net_wifi_getRssiCommand },
    { "getLinkSpeedCommand", "()I", (void*) android_net_wifi_getLinkSpeedCommand },
    { "getMacAddressCommand", "()Ljava/lang/String;", (void*) android_net_wifi_getMacAddressCommand },
    { "saveConfigCommand", "()Z", (void*) android_net_wifi_saveConfigCommand },
    { "reloadConfigCommand", "()Z", (void*) android_net_wifi_reloadConfigCommand },
    { "setScanResultHandlingCommand", "(I)Z", (void*) android_net_wifi_setScanResultHandlingCommand },
    { "addToBlacklistCommand", "(Ljava/lang/String;)Z", (void*) android_net_wifi_addToBlacklistCommand },
    { "clearBlacklistCommand", "()Z", (void*) android_net_wifi_clearBlacklistCommand },

    { "doDhcpRequest", "(Landroid/net/DhcpInfo;)Z", (void*) android_net_wifi_doDhcpRequest },
    { "getDhcpError", "()Ljava/lang/String;", (void*) android_net_wifi_getDhcpError },
};

int register_android_net_wifi_WifiManager(JNIEnv* env)
{
    jclass wifi = env->FindClass(WIFI_PKG_NAME);
    LOG_FATAL_IF(wifi == NULL, "Unable to find class " WIFI_PKG_NAME);

    dhcpInfoFieldIds.dhcpInfoClass = env->FindClass("android/net/DhcpInfo");
    if (dhcpInfoFieldIds.dhcpInfoClass != NULL) {
        dhcpInfoFieldIds.constructorId = env->GetMethodID(dhcpInfoFieldIds.dhcpInfoClass, "<init>", "()V");
        dhcpInfoFieldIds.ipaddress = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "ipAddress", "I");
        dhcpInfoFieldIds.gateway = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "gateway", "I");
        dhcpInfoFieldIds.netmask = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "netmask", "I");
        dhcpInfoFieldIds.dns1 = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "dns1", "I");
        dhcpInfoFieldIds.dns2 = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "dns2", "I");
        dhcpInfoFieldIds.serverAddress = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "serverAddress", "I");
        dhcpInfoFieldIds.leaseDuration = env->GetFieldID(dhcpInfoFieldIds.dhcpInfoClass, "leaseDuration", "I");
    }

    return AndroidRuntime::registerNativeMethods(env,
            WIFI_PKG_NAME, gWifiMethods, NELEM(gWifiMethods));
}

}; // namespace android
