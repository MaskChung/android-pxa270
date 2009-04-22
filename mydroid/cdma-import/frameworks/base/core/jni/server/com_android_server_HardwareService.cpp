/* //device/libs/android_runtime/android_os_Vibrator.cpp
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#define LOG_TAG "Vibrator"

#include "jni.h"
#include "JNIHelp.h"
#include <stdio.h>
#include "android_runtime/AndroidRuntime.h"
#include <utils/misc.h>
#include <utils/Log.h>
#include <hardware/vibrator.h>

namespace android
{

static void on(JNIEnv *env, jobject clazz)
{
    // LOGI("on\n");
    vibrator_on();
}

static void off(JNIEnv *env, jobject clazz)
{
    // LOGI("off\n");
    vibrator_off();
}

static JNINativeMethod method_table[] = {
    { "on", "()V", (void*)on },
    { "off", "()V", (void*)off }
};

int register_android_os_Vibrator(JNIEnv *env)
{
    return jniRegisterNativeMethods(env, "com/android/server/HardwareService",
            method_table, NELEM(method_table));
}

};
