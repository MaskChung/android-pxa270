/* //device/libs/android_runtime/android_util_AssetManager.cpp
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

#define LOG_TAG "asset"

#include <android_runtime/android_util_AssetManager.h>

#include "jni.h"
#include "JNIHelp.h"
#include "android_util_Binder.h"
#include <utils/misc.h>
#include <android_runtime/AndroidRuntime.h>
#include <utils/Log.h>

#include <utils/Asset.h>
#include <utils/AssetManager.h>
#include <utils/ResourceTypes.h>

#include <stdio.h>

namespace android {

// ----------------------------------------------------------------------------

static struct typedvalue_offsets_t
{
    jfieldID mType;
    jfieldID mData;
    jfieldID mString;
    jfieldID mAssetCookie;
    jfieldID mResourceId;
    jfieldID mChangingConfigurations;
} gTypedValueOffsets;

static struct assetfiledescriptor_offsets_t
{
    jfieldID mFd;
    jfieldID mStartOffset;
    jfieldID mLength;
} gAssetFileDescriptorOffsets;

static struct assetmanager_offsets_t
{
    jfieldID mObject;
} gAssetManagerOffsets;

jclass g_stringClass = NULL;

// ----------------------------------------------------------------------------

static void doThrow(JNIEnv* env, const char* exc, const char* msg = NULL)
{
    jclass npeClazz;

    npeClazz = env->FindClass(exc);
    LOG_FATAL_IF(npeClazz == NULL, "Unable to find class %s", exc);

    env->ThrowNew(npeClazz, msg);
}

enum {
    STYLE_NUM_ENTRIES = 5,
    STYLE_TYPE = 0,
    STYLE_DATA = 1,
    STYLE_ASSET_COOKIE = 2,
    STYLE_RESOURCE_ID = 3,
    STYLE_CHANGING_CONFIGURATIONS = 4
};

static jint copyValue(JNIEnv* env, jobject outValue, const ResTable* table,
                      const Res_value& value, uint32_t ref, ssize_t block,
                      uint32_t typeSpecFlags)
{
    env->SetIntField(outValue, gTypedValueOffsets.mType, value.dataType);
    env->SetIntField(outValue, gTypedValueOffsets.mAssetCookie,
                        (jint)table->getTableCookie(block));
    env->SetIntField(outValue, gTypedValueOffsets.mData, value.data);
    env->SetObjectField(outValue, gTypedValueOffsets.mString, NULL);
    env->SetIntField(outValue, gTypedValueOffsets.mResourceId, ref);
    env->SetIntField(outValue, gTypedValueOffsets.mChangingConfigurations,
            typeSpecFlags);
    return block;
}

// ----------------------------------------------------------------------------

// this guy is exported to other jni routines
AssetManager* assetManagerForJavaObject(JNIEnv* env, jobject obj)
{
    AssetManager* am = (AssetManager*)env->GetIntField(obj, gAssetManagerOffsets.mObject);
    if (am != NULL) {
        return am;
    }
    jniThrowException(env, "java/lang/IllegalStateException", "AssetManager has been finalized!");
    return NULL;
}

static jint android_content_AssetManager_openAsset(JNIEnv* env, jobject clazz,
                                                jstring fileName, jint mode)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    LOGV("openAsset in %p (Java object %p)\n", am, clazz);

    if (fileName == NULL || am == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    if (mode != Asset::ACCESS_UNKNOWN && mode != Asset::ACCESS_RANDOM
        && mode != Asset::ACCESS_STREAMING && mode != Asset::ACCESS_BUFFER) {
        doThrow(env, "java/lang/IllegalArgumentException");
        return -1;
    }

    const char* fileName8 = env->GetStringUTFChars(fileName, NULL);
    Asset* a = am->open(fileName8, (Asset::AccessMode)mode);

    if (a == NULL) {
        doThrow(env, "java/io/FileNotFoundException", fileName8);
        env->ReleaseStringUTFChars(fileName, fileName8);
        return -1;
    }
    env->ReleaseStringUTFChars(fileName, fileName8);

    //printf("Created Asset Stream: %p\n", a);

    return (jint)a;
}

static jobject returnParcelFileDescriptor(JNIEnv* env, Asset* a, jlongArray outOffsets)
{
    off_t startOffset, length;
    int fd = a->openFileDescriptor(&startOffset, &length);
    delete a;
    
    if (fd < 0) {
        doThrow(env, "java/io/FileNotFoundException",
                "This file can not be opened as a file descriptor; it is probably compressed");
        return NULL;
    }
    
    jlong* offsets = (jlong*)env->GetPrimitiveArrayCritical(outOffsets, 0);
    if (offsets == NULL) {
        close(fd);
        return NULL;
    }
    
    offsets[0] = startOffset;
    offsets[1] = length;
    
    env->ReleasePrimitiveArrayCritical(outOffsets, offsets, 0);
    
    jobject fileDesc = newFileDescriptor(env, fd);
    if (fileDesc == NULL) {
        close(fd);
        return NULL;
    }
    
    return newParcelFileDescriptor(env, fileDesc);
}

static jobject android_content_AssetManager_openAssetFd(JNIEnv* env, jobject clazz,
                                                jstring fileName, jlongArray outOffsets)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    LOGV("openAssetFd in %p (Java object %p)\n", am, clazz);

    if (fileName == NULL || am == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return NULL;
    }

    const char* fileName8 = env->GetStringUTFChars(fileName, NULL);
    Asset* a = am->open(fileName8, Asset::ACCESS_RANDOM);

    if (a == NULL) {
        doThrow(env, "java/io/FileNotFoundException", fileName8);
        env->ReleaseStringUTFChars(fileName, fileName8);
        return NULL;
    }
    env->ReleaseStringUTFChars(fileName, fileName8);

    //printf("Created Asset Stream: %p\n", a);

    return returnParcelFileDescriptor(env, a, outOffsets);
}

static jint android_content_AssetManager_openNonAssetNative(JNIEnv* env, jobject clazz,
                                                         jint cookie,
                                                         jstring fileName,
                                                         jint mode)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    LOGV("openNonAssetNative in %p (Java object %p)\n", am, clazz);

    if (fileName == NULL || am == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    if (mode != Asset::ACCESS_UNKNOWN && mode != Asset::ACCESS_RANDOM
        && mode != Asset::ACCESS_STREAMING && mode != Asset::ACCESS_BUFFER) {
        doThrow(env, "java/lang/IllegalArgumentException");
        return -1;
    }

    const char* fileName8 = env->GetStringUTFChars(fileName, NULL);
    Asset* a = cookie
        ? am->openNonAsset((void*)cookie, fileName8, (Asset::AccessMode)mode)
        : am->openNonAsset(fileName8, (Asset::AccessMode)mode);

    if (a == NULL) {
        doThrow(env, "java/io/FileNotFoundException", fileName8);
        env->ReleaseStringUTFChars(fileName, fileName8);
        return -1;
    }
    env->ReleaseStringUTFChars(fileName, fileName8);

    //printf("Created Asset Stream: %p\n", a);

    return (jint)a;
}

static jobject android_content_AssetManager_openNonAssetFdNative(JNIEnv* env, jobject clazz,
                                                         jint cookie,
                                                         jstring fileName,
                                                         jlongArray outOffsets)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    LOGV("openNonAssetFd in %p (Java object %p)\n", am, clazz);

    if (fileName == NULL || am == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return NULL;
    }

    const char* fileName8 = env->GetStringUTFChars(fileName, NULL);
    Asset* a = cookie
        ? am->openNonAsset((void*)cookie, fileName8, Asset::ACCESS_RANDOM)
        : am->openNonAsset(fileName8, Asset::ACCESS_RANDOM);

    if (a == NULL) {
        doThrow(env, "java/io/FileNotFoundException", fileName8);
        env->ReleaseStringUTFChars(fileName, fileName8);
        return NULL;
    }
    env->ReleaseStringUTFChars(fileName, fileName8);

    //printf("Created Asset Stream: %p\n", a);

    return returnParcelFileDescriptor(env, a, outOffsets);
}

static jobjectArray android_content_AssetManager_list(JNIEnv* env, jobject clazz,
                                                   jstring fileName)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    if (fileName == NULL || am == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return NULL;
    }

    const char* fileName8 = env->GetStringUTFChars(fileName, NULL);

    AssetDir* dir = am->openDir(fileName8);

    env->ReleaseStringUTFChars(fileName, fileName8);

    if (dir == NULL) {
        doThrow(env, "java/io/FileNotFoundException", fileName8);
        return NULL;
    }

    jclass cls = env->FindClass("java/lang/String");
    LOG_FATAL_IF(cls == NULL, "No string class?!?");
    if (cls == NULL) {
        delete dir;
        return NULL;
    }

    size_t N = dir->getFileCount();

    jobjectArray array = env->NewObjectArray(dir->getFileCount(),
                                                cls, NULL);
    if (array == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        delete dir;
        return NULL;
    }

    for (size_t i=0; i<N; i++) {
        const String8& name = dir->getFileName(i);
        jstring str = env->NewStringUTF(name.string());
        if (str == NULL) {
            doThrow(env, "java/lang/OutOfMemoryError");
            delete dir;
            return NULL;
        }
        env->SetObjectArrayElement(array, i, str);
    }

    delete dir;

    return array;
}

static void android_content_AssetManager_destroyAsset(JNIEnv* env, jobject clazz,
                                                   jint asset)
{
    Asset* a = (Asset*)asset;

    //printf("Destroying Asset Stream: %p\n", a);

    if (a == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return;
    }

    delete a;
}

static jint android_content_AssetManager_readAssetChar(JNIEnv* env, jobject clazz,
                                                    jint asset)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    uint8_t b;
    ssize_t res = a->read(&b, 1);
    return res == 1 ? b : -1;
}

static jint android_content_AssetManager_readAsset(JNIEnv* env, jobject clazz,
                                                jint asset, jbyteArray bArray,
                                                jint off, jint len)
{
    Asset* a = (Asset*)asset;

    if (a == NULL || bArray == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    if (len == 0) {
        return 0;
    }
    
    jsize bLen = env->GetArrayLength(bArray);
    if (off < 0 || off >= bLen || len < 0 || len > bLen || (off+len) > bLen) {
        doThrow(env, "java/lang/IndexOutOfBoundsException");
        return -1;
    }

    jbyte* b = env->GetByteArrayElements(bArray, NULL);
    ssize_t res = a->read(b+off, len);
    env->ReleaseByteArrayElements(bArray, b, 0);

    if (res > 0) return res;

    if (res < 0) {
        doThrow(env, "java/io/IOException");
    }
    return -1;
}

static jlong android_content_AssetManager_seekAsset(JNIEnv* env, jobject clazz,
                                                 jint asset,
                                                 jlong offset, jint whence)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    return a->seek(
        offset, (whence > 0) ? SEEK_END : (whence < 0 ? SEEK_SET : SEEK_CUR));
}

static jlong android_content_AssetManager_getAssetLength(JNIEnv* env, jobject clazz,
                                                      jint asset)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    return a->getLength();
}

static jlong android_content_AssetManager_getAssetRemainingLength(JNIEnv* env, jobject clazz,
                                                               jint asset)
{
    Asset* a = (Asset*)asset;

    if (a == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return -1;
    }

    return a->getRemainingLength();
}

static jint android_content_AssetManager_addAssetPath(JNIEnv* env, jobject clazz,
                                                       jstring path)
{
    if (path == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }

    const char* path8 = env->GetStringUTFChars(path, NULL);

    void* cookie;
    bool res = am->addAssetPath(String8(path8), &cookie);

    env->ReleaseStringUTFChars(path, path8);

    return (res) ? (jint)cookie : 0;
}

static jboolean android_content_AssetManager_isUpToDate(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_TRUE;
    }
    return am->isUpToDate() ? JNI_TRUE : JNI_FALSE;
}

static void android_content_AssetManager_setLocale(JNIEnv* env, jobject clazz,
                                                jstring locale)
{
    if (locale == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return;
    }

    const char* locale8 = env->GetStringUTFChars(locale, NULL);

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return;
    }

    am->setLocale(locale8);

    env->ReleaseStringUTFChars(locale, locale8);
}

static jobjectArray android_content_AssetManager_getLocales(JNIEnv* env, jobject clazz)
{
    Vector<String8> locales;

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }

    am->getLocales(&locales);

    const int N = locales.size();

    jobjectArray result = env->NewObjectArray(N, g_stringClass, NULL);
    if (result == NULL) {
        return NULL;
    }

    for (int i=0; i<N; i++) {
        LOGD("locale %2d: '%s'", i, locales[i].string());
        env->SetObjectArrayElement(result, i, env->NewStringUTF(locales[i].string()));
    }

    return result;
}

static void android_content_AssetManager_setConfiguration(JNIEnv* env, jobject clazz,
                                                          jint mcc, jint mnc,
                                                          jstring locale, jint orientation,
                                                          jint touchscreen, jint density,
                                                          jint keyboard, jint keyboardHidden,
                                                          jint navigation,
                                                          jint screenWidth, jint screenHeight,
                                                          jint sdkVersion)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return;
    }

    ResTable_config config;
    memset(&config, 0, sizeof(config));
    
    const char* locale8 = locale != NULL ? env->GetStringUTFChars(locale, NULL) : NULL;
    
    config.mcc = (uint16_t)mcc;
    config.mnc = (uint16_t)mnc;
    config.orientation = (uint8_t)orientation;
    config.touchscreen = (uint8_t)touchscreen;
    config.density = (uint16_t)density;
    config.keyboard = (uint8_t)keyboard;
    config.inputFlags = (uint8_t)keyboardHidden<<ResTable_config::SHIFT_KEYSHIDDEN;
    config.navigation = (uint8_t)navigation;
    config.screenWidth = (uint16_t)screenWidth;
    config.screenHeight = (uint16_t)screenHeight;
    config.sdkVersion = (uint16_t)sdkVersion;
    config.minorVersion = 0;
    am->setConfiguration(config, locale8);
    
    if (locale != NULL) env->ReleaseStringUTFChars(locale, locale8);
}

static jint android_content_AssetManager_getResourceIdentifier(JNIEnv* env, jobject clazz,
                                                            jstring name,
                                                            jstring defType,
                                                            jstring defPackage)
{
    if (name == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return 0;
    }

    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    const char16_t* name16 = env->GetStringChars(name, NULL);
    jsize nameLen = env->GetStringLength(name);
    const char16_t* defType16 = defType
        ? env->GetStringChars(defType, NULL) : NULL;
    jsize defTypeLen = defType
        ? env->GetStringLength(defType) : 0;
    const char16_t* defPackage16 = defPackage
        ? env->GetStringChars(defPackage, NULL) : NULL;
    jsize defPackageLen = defPackage
        ? env->GetStringLength(defPackage) : 0;

    jint ident = am->getResources().identifierForName(
        name16, nameLen, defType16, defTypeLen, defPackage16, defPackageLen);

    if (defPackage16) {
        env->ReleaseStringChars(defPackage, defPackage16);
    }
    if (defType16) {
        env->ReleaseStringChars(defType, defType16);
    }
    env->ReleaseStringChars(name, name16);

    return ident;
}

static jstring android_content_AssetManager_getResourceName(JNIEnv* env, jobject clazz,
                                                            jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    
    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }
    
    String16 str;
    if (name.package != NULL) {
        str.setTo(name.package, name.packageLen);
    }
    if (name.type != NULL) {
        if (str.size() > 0) {
            char16_t div = ':';
            str.append(&div, 1);
        }
        str.append(name.type, name.typeLen);
    }
    if (name.name != NULL) {
        if (str.size() > 0) {
            char16_t div = '/';
            str.append(&div, 1);
        }
        str.append(name.name, name.nameLen);
    }
    
    return env->NewString((const jchar*)str.string(), str.size());
}

static jstring android_content_AssetManager_getResourcePackageName(JNIEnv* env, jobject clazz,
                                                                   jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    
    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }
    
    if (name.package != NULL) {
        return env->NewString((const jchar*)name.package, name.packageLen);
    }
    
    return NULL;
}

static jstring android_content_AssetManager_getResourceTypeName(JNIEnv* env, jobject clazz,
                                                                jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    
    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }
    
    if (name.type != NULL) {
        return env->NewString((const jchar*)name.type, name.typeLen);
    }
    
    return NULL;
}

static jstring android_content_AssetManager_getResourceEntryName(JNIEnv* env, jobject clazz,
                                                                 jint resid)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    
    ResTable::resource_name name;
    if (!am->getResources().getResourceName(resid, &name)) {
        return NULL;
    }
    
    if (name.name != NULL) {
        return env->NewString((const jchar*)name.name, name.nameLen);
    }
    
    return NULL;
}

static jint android_content_AssetManager_loadResourceValue(JNIEnv* env, jobject clazz,
                                                           jint ident,
                                                           jobject outValue,
                                                           jboolean resolve)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    const ResTable& res(am->getResources());

    Res_value value;
    uint32_t typeSpecFlags;
    ssize_t block = res.getResource(ident, &value, false, &typeSpecFlags);
    uint32_t ref = ident;
    if (resolve) {
        block = res.resolveReference(&value, block, &ref);
    }
    return block >= 0 ? copyValue(env, outValue, &res, value, ref, block, typeSpecFlags) : block;
}

static jint android_content_AssetManager_loadResourceBagValue(JNIEnv* env, jobject clazz,
                                                           jint ident, jint bagEntryId,
                                                           jobject outValue, jboolean resolve)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    const ResTable& res(am->getResources());
    
    // Now lock down the resource object and start pulling stuff from it.
    res.lock();
    
    ssize_t block = -1;
    Res_value value;

    const ResTable::bag_entry* entry = NULL;
    uint32_t typeSpecFlags;
    ssize_t entryCount = res.getBagLocked(ident, &entry, &typeSpecFlags);

    for (ssize_t i=0; i<entryCount; i++) {
        if (((uint32_t)bagEntryId) == entry->map.name.ident) {
            block = entry->stringBlock;
            value = entry->map.value;
        }
        entry++;
    }

    res.unlock();

    if (block < 0) {
        return block;
    }
    
    uint32_t ref = ident;
    if (resolve) {
        block = res.resolveReference(&value, block, &ref, &typeSpecFlags);
    }
    return block >= 0 ? copyValue(env, outValue, &res, value, ref, block, typeSpecFlags) : block;
}

static jint android_content_AssetManager_getStringBlockCount(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    return am->getResources().getTableCount();
}

static jint android_content_AssetManager_getNativeStringBlock(JNIEnv* env, jobject clazz,
                                                           jint block)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    return (jint)am->getResources().getTableStringBlock(block);
}

static jstring android_content_AssetManager_getCookieName(JNIEnv* env, jobject clazz,
                                                       jint cookie)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    String8 name(am->getAssetPath((void*)cookie));
    if (name.length() == 0) {
        doThrow(env, "java/lang/IndexOutOfBoundsException");
        return NULL;
    }
    jstring str = env->NewStringUTF(name.string());
    if (str == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        return NULL;
    }
    return str;
}

static jint android_content_AssetManager_newTheme(JNIEnv* env, jobject clazz)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }
    return (jint)(new ResTable::Theme(am->getResources()));
}

static void android_content_AssetManager_deleteTheme(JNIEnv* env, jobject clazz,
                                                     jint themeInt)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    delete theme;
}

static void android_content_AssetManager_applyThemeStyle(JNIEnv* env, jobject clazz,
                                                         jint themeInt,
                                                         jint styleRes,
                                                         jboolean force)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    theme->applyStyle(styleRes, force ? true : false);
}

static void android_content_AssetManager_copyTheme(JNIEnv* env, jobject clazz,
                                                   jint destInt, jint srcInt)
{
    ResTable::Theme* dest = (ResTable::Theme*)destInt;
    ResTable::Theme* src = (ResTable::Theme*)srcInt;
    dest->setTo(*src);
}

static jint android_content_AssetManager_loadThemeAttributeValue(
    JNIEnv* env, jobject clazz, jint themeInt, jint ident, jobject outValue, jboolean resolve)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    const ResTable& res(theme->getResTable());

    Res_value value;
    // XXX value could be different in different configs!
    uint32_t typeSpecFlags = 0;
    ssize_t block = theme->getAttribute(ident, &value, &typeSpecFlags);
    uint32_t ref = 0;
    if (resolve) {
        block = res.resolveReference(&value, block, &ref, &typeSpecFlags);
    }
    return block >= 0 ? copyValue(env, outValue, &res, value, ref, block, typeSpecFlags) : block;
}

static void android_content_AssetManager_dumpTheme(JNIEnv* env, jobject clazz,
                                                   jint themeInt, jint pri,
                                                   jstring tag, jstring prefix)
{
    ResTable::Theme* theme = (ResTable::Theme*)themeInt;
    const ResTable& res(theme->getResTable());
    
    if (tag == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return;
    }
    
    const char* tag8 = env->GetStringUTFChars(tag, NULL);
    const char* prefix8 = NULL;
    if (prefix != NULL) {
        prefix8 = env->GetStringUTFChars(prefix, NULL);
    }
    
    // XXX Need to use params.
    theme->dumpToLog();
    
    if (prefix8 != NULL) {
        env->ReleaseStringUTFChars(prefix, prefix8);
    }
    env->ReleaseStringUTFChars(tag, tag8);
}

static jboolean android_content_AssetManager_applyStyle(JNIEnv* env, jobject clazz,
                                                        jint themeToken,
                                                        jint defStyleAttr,
                                                        jint defStyleRes,
                                                        jint xmlParserToken,
                                                        jintArray attrs,
                                                        jintArray outValues,
                                                        jintArray outIndices)
{
    if (themeToken == 0 || attrs == NULL || outValues == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }

    ResTable::Theme* theme = (ResTable::Theme*)themeToken;
    const ResTable& res = theme->getResTable();
    ResXMLParser* xmlParser = (ResXMLParser*)xmlParserToken;
    Res_value value;

    const jsize NI = env->GetArrayLength(attrs);
    const jsize NV = env->GetArrayLength(outValues);
    if (NV < (NI*STYLE_NUM_ENTRIES)) {
        doThrow(env, "java/lang/IndexOutOfBoundsException");
        return JNI_FALSE;
    }

    jint* src = (jint*)env->GetPrimitiveArrayCritical(attrs, 0);
    if (src == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        return JNI_FALSE;
    }

    jint* dest = (jint*)env->GetPrimitiveArrayCritical(outValues, 0);
    if (dest == NULL) {
        env->ReleasePrimitiveArrayCritical(attrs, src, 0);
        doThrow(env, "java/lang/OutOfMemoryError");
        return JNI_FALSE;
    }

    jint* indices = NULL;
    int indicesIdx = 0;
    if (outIndices != NULL) {
        if (env->GetArrayLength(outIndices) > NI) {
            indices = (jint*)env->GetPrimitiveArrayCritical(outIndices, 0);
        }
    }

    // Load default style from attribute, if specified...
    uint32_t defStyleBagTypeSetFlags = 0;
    if (defStyleAttr != 0) {
        Res_value value;
        if (theme->getAttribute(defStyleAttr, &value, &defStyleBagTypeSetFlags) >= 0) {
            if (value.dataType == Res_value::TYPE_REFERENCE) {
                defStyleRes = value.data;
            }
        }
    }

    // Retrieve the style class associated with the current XML tag.
    int style = 0;
    uint32_t styleBagTypeSetFlags = 0;
    if (xmlParser != NULL) {
        ssize_t idx = xmlParser->indexOfStyle();
        if (idx >= 0 && xmlParser->getAttributeValue(idx, &value) >= 0) {
            if (value.dataType == value.TYPE_ATTRIBUTE) {
                if (theme->getAttribute(value.data, &value, &styleBagTypeSetFlags) < 0) {
                    value.dataType = Res_value::TYPE_NULL;
                }
            }
            if (value.dataType == value.TYPE_REFERENCE) {
                style = value.data;
            }
        }
    }

    // Now lock down the resource object and start pulling stuff from it.
    res.lock();

    // Retrieve the default style bag, if requested.
    const ResTable::bag_entry* defStyleEnt = NULL;
    uint32_t defStyleTypeSetFlags = 0;
    ssize_t bagOff = defStyleRes != 0
            ? res.getBagLocked(defStyleRes, &defStyleEnt, &defStyleTypeSetFlags) : -1;
    defStyleTypeSetFlags |= defStyleBagTypeSetFlags;
    const ResTable::bag_entry* endDefStyleEnt = defStyleEnt +
        (bagOff >= 0 ? bagOff : 0);

    // Retrieve the style class bag, if requested.
    const ResTable::bag_entry* styleEnt = NULL;
    uint32_t styleTypeSetFlags = 0;
    bagOff = style != 0 ? res.getBagLocked(style, &styleEnt, &styleTypeSetFlags) : -1;
    styleTypeSetFlags |= styleBagTypeSetFlags;
    const ResTable::bag_entry* endStyleEnt = styleEnt +
        (bagOff >= 0 ? bagOff : 0);

    // Retrieve the XML attributes, if requested.
    const jsize NX = xmlParser ? xmlParser->getAttributeCount() : 0;
    jsize ix=0;
    uint32_t curXmlAttr = xmlParser ? xmlParser->getAttributeNameResID(ix) : 0;

    static const ssize_t kXmlBlock = 0x10000000;

    // Now iterate through all of the attributes that the client has requested,
    // filling in each with whatever data we can find.
    ssize_t block = 0;
    uint32_t typeSetFlags;
    for (jsize ii=0; ii<NI; ii++) {
        const uint32_t curIdent = (uint32_t)src[ii];

        // Try to find a value for this attribute...  we prioritize values
        // coming from, first XML attributes, then XML style, then default
        // style, and finally the theme.
        value.dataType = Res_value::TYPE_NULL;
        value.data = 0;
        typeSetFlags = 0;

        // Skip through XML attributes until the end or the next possible match.
        while (ix < NX && curIdent > curXmlAttr) {
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }
        // Retrieve the current XML attribute if it matches, and step to next.
        if (ix < NX && curIdent == curXmlAttr) {
            block = kXmlBlock;
            xmlParser->getAttributeValue(ix, &value);
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }

        // Skip through the style values until the end or the next possible match.
        while (styleEnt < endStyleEnt && curIdent > styleEnt->map.name.ident) {
            styleEnt++;
        }
        // Retrieve the current style attribute if it matches, and step to next.
        if (styleEnt < endStyleEnt && curIdent == styleEnt->map.name.ident) {
            if (value.dataType == Res_value::TYPE_NULL) {
                block = styleEnt->stringBlock;
                typeSetFlags = styleTypeSetFlags;
                value = styleEnt->map.value;
            }
            styleEnt++;
        }

        // Skip through the default style values until the end or the next possible match.
        while (defStyleEnt < endDefStyleEnt && curIdent > defStyleEnt->map.name.ident) {
            defStyleEnt++;
        }
        // Retrieve the current default style attribute if it matches, and step to next.
        if (defStyleEnt < endDefStyleEnt && curIdent == defStyleEnt->map.name.ident) {
            if (value.dataType == Res_value::TYPE_NULL) {
                block = defStyleEnt->stringBlock;
                typeSetFlags = defStyleTypeSetFlags;
                value = defStyleEnt->map.value;
            }
            defStyleEnt++;
        }

        //printf("Attribute 0x%08x: type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);
        uint32_t resid = 0;
        if (value.dataType != Res_value::TYPE_NULL) {
            // Take care of resolving the found resource to its final value.
            //printf("Resolving attribute reference\n");
            ssize_t newBlock = theme->resolveAttributeReference(&value, block, &resid, &typeSetFlags);
            if (newBlock >= 0) block = newBlock;
        } else {
            // If we still don't have a value for this attribute, try to find
            // it in the theme!
            //printf("Looking up in theme\n");
            ssize_t newBlock = theme->getAttribute(curIdent, &value, &typeSetFlags);
            if (newBlock >= 0) {
                //printf("Resolving resource reference\n");
                newBlock = res.resolveReference(&value, block, &resid, &typeSetFlags);
                if (newBlock >= 0) block = newBlock;
            }
        }

        // Deal with the special @null value -- it turns back to TYPE_NULL.
        if (value.dataType == Res_value::TYPE_REFERENCE && value.data == 0) {
            value.dataType = Res_value::TYPE_NULL;
        }

        //printf("Attribute 0x%08x: final type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);

        // Write the final value back to Java.
        dest[STYLE_TYPE] = value.dataType;
        dest[STYLE_DATA] = value.data;
        dest[STYLE_ASSET_COOKIE] =
            block != kXmlBlock ? (jint)res.getTableCookie(block) : (jint)-1;
        dest[STYLE_RESOURCE_ID] = resid;
        dest[STYLE_CHANGING_CONFIGURATIONS] = typeSetFlags;
        
        if (indices != NULL && value.dataType != Res_value::TYPE_NULL) {
            indicesIdx++;
            indices[indicesIdx] = ii;
        }
        
        dest += STYLE_NUM_ENTRIES;
    }

    res.unlock();

    if (indices != NULL) {
        indices[0] = indicesIdx;
        env->ReleasePrimitiveArrayCritical(outIndices, indices, 0);
    }
    env->ReleasePrimitiveArrayCritical(outValues, dest, 0);
    env->ReleasePrimitiveArrayCritical(attrs, src, 0);

    return JNI_TRUE;
}

static jboolean android_content_AssetManager_retrieveAttributes(JNIEnv* env, jobject clazz,
                                                        jint xmlParserToken,
                                                        jintArray attrs,
                                                        jintArray outValues,
                                                        jintArray outIndices)
{
    if (xmlParserToken == 0 || attrs == NULL || outValues == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }
    const ResTable& res(am->getResources());
    ResXMLParser* xmlParser = (ResXMLParser*)xmlParserToken;
    Res_value value;
    
    const jsize NI = env->GetArrayLength(attrs);
    const jsize NV = env->GetArrayLength(outValues);
    if (NV < (NI*STYLE_NUM_ENTRIES)) {
        doThrow(env, "java/lang/IndexOutOfBoundsException");
        return JNI_FALSE;
    }
    
    jint* src = (jint*)env->GetPrimitiveArrayCritical(attrs, 0);
    if (src == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        return JNI_FALSE;
    }
    
    jint* dest = (jint*)env->GetPrimitiveArrayCritical(outValues, 0);
    if (dest == NULL) {
        env->ReleasePrimitiveArrayCritical(attrs, src, 0);
        doThrow(env, "java/lang/OutOfMemoryError");
        return JNI_FALSE;
    }
    
    jint* indices = NULL;
    int indicesIdx = 0;
    if (outIndices != NULL) {
        if (env->GetArrayLength(outIndices) > NI) {
            indices = (jint*)env->GetPrimitiveArrayCritical(outIndices, 0);
        }
    }

    // Now lock down the resource object and start pulling stuff from it.
    res.lock();
    
    // Retrieve the XML attributes, if requested.
    const jsize NX = xmlParser->getAttributeCount();
    jsize ix=0;
    uint32_t curXmlAttr = xmlParser->getAttributeNameResID(ix);
    
    static const ssize_t kXmlBlock = 0x10000000;
    
    // Now iterate through all of the attributes that the client has requested,
    // filling in each with whatever data we can find.
    ssize_t block = 0;
    uint32_t typeSetFlags;
    for (jsize ii=0; ii<NI; ii++) {
        const uint32_t curIdent = (uint32_t)src[ii];
        
        // Try to find a value for this attribute...
        value.dataType = Res_value::TYPE_NULL;
        value.data = 0;
        typeSetFlags = 0;
        
        // Skip through XML attributes until the end or the next possible match.
        while (ix < NX && curIdent > curXmlAttr) {
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }
        // Retrieve the current XML attribute if it matches, and step to next.
        if (ix < NX && curIdent == curXmlAttr) {
            block = kXmlBlock;
            xmlParser->getAttributeValue(ix, &value);
            ix++;
            curXmlAttr = xmlParser->getAttributeNameResID(ix);
        }
        
        //printf("Attribute 0x%08x: type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);
        uint32_t resid = 0;
        if (value.dataType != Res_value::TYPE_NULL) {
            // Take care of resolving the found resource to its final value.
            //printf("Resolving attribute reference\n");
            ssize_t newBlock = res.resolveReference(&value, block, &resid, &typeSetFlags);
            if (newBlock >= 0) block = newBlock;
        }
        
        // Deal with the special @null value -- it turns back to TYPE_NULL.
        if (value.dataType == Res_value::TYPE_REFERENCE && value.data == 0) {
            value.dataType = Res_value::TYPE_NULL;
        }
        
        //printf("Attribute 0x%08x: final type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);
        
        // Write the final value back to Java.
        dest[STYLE_TYPE] = value.dataType;
        dest[STYLE_DATA] = value.data;
        dest[STYLE_ASSET_COOKIE] =
            block != kXmlBlock ? (jint)res.getTableCookie(block) : (jint)-1;
        dest[STYLE_RESOURCE_ID] = resid;
        dest[STYLE_CHANGING_CONFIGURATIONS] = typeSetFlags;
        
        if (indices != NULL && value.dataType != Res_value::TYPE_NULL) {
            indicesIdx++;
            indices[indicesIdx] = ii;
        }
        
        dest += STYLE_NUM_ENTRIES;
    }
    
    res.unlock();
    
    if (indices != NULL) {
        indices[0] = indicesIdx;
        env->ReleasePrimitiveArrayCritical(outIndices, indices, 0);
    }
    
    env->ReleasePrimitiveArrayCritical(outValues, dest, 0);
    env->ReleasePrimitiveArrayCritical(attrs, src, 0);
    
    return JNI_TRUE;
}

static jint android_content_AssetManager_getArraySize(JNIEnv* env, jobject clazz,
                                                       jint id)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());
    
    res.lock();
    const ResTable::bag_entry* defStyleEnt = NULL;
    ssize_t bagOff = res.getBagLocked(id, &defStyleEnt);
    res.unlock();
    
    return bagOff;
}

static jint android_content_AssetManager_retrieveArray(JNIEnv* env, jobject clazz,
                                                        jint id,
                                                        jintArray outValues)
{
    if (outValues == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return JNI_FALSE;
    }
    const ResTable& res(am->getResources());
    Res_value value;
    ssize_t block;
    
    const jsize NV = env->GetArrayLength(outValues);
    
    jint* dest = (jint*)env->GetPrimitiveArrayCritical(outValues, 0);
    if (dest == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        return JNI_FALSE;
    }
    
    // Now lock down the resource object and start pulling stuff from it.
    res.lock();
    
    const ResTable::bag_entry* arrayEnt = NULL;
    uint32_t arrayTypeSetFlags = 0;
    ssize_t bagOff = res.getBagLocked(id, &arrayEnt, &arrayTypeSetFlags);
    const ResTable::bag_entry* endArrayEnt = arrayEnt +
        (bagOff >= 0 ? bagOff : 0);
    
    int i = 0;
    uint32_t typeSetFlags;
    while (i < NV && arrayEnt < endArrayEnt) {
        block = arrayEnt->stringBlock;
        typeSetFlags = arrayTypeSetFlags;
        value = arrayEnt->map.value;
                
        uint32_t resid = 0;
        if (value.dataType != Res_value::TYPE_NULL) {
            // Take care of resolving the found resource to its final value.
            //printf("Resolving attribute reference\n");
            ssize_t newBlock = res.resolveReference(&value, block, &resid, &typeSetFlags);
            if (newBlock >= 0) block = newBlock;
        }

        // Deal with the special @null value -- it turns back to TYPE_NULL.
        if (value.dataType == Res_value::TYPE_REFERENCE && value.data == 0) {
            value.dataType = Res_value::TYPE_NULL;
        }

        //printf("Attribute 0x%08x: final type=0x%x, data=0x%08x\n", curIdent, value.dataType, value.data);

        // Write the final value back to Java.
        dest[STYLE_TYPE] = value.dataType;
        dest[STYLE_DATA] = value.data;
        dest[STYLE_ASSET_COOKIE] = (jint)res.getTableCookie(block);
        dest[STYLE_RESOURCE_ID] = resid;
        dest[STYLE_CHANGING_CONFIGURATIONS] = typeSetFlags;
        dest += STYLE_NUM_ENTRIES;
        i+= STYLE_NUM_ENTRIES;
        arrayEnt++;
    }
    
    i /= STYLE_NUM_ENTRIES;
    
    res.unlock();
    
    env->ReleasePrimitiveArrayCritical(outValues, dest, 0);
    
    return i;
}

static jint android_content_AssetManager_openXmlAssetNative(JNIEnv* env, jobject clazz,
                                                         jint cookie,
                                                         jstring fileName)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return 0;
    }

    LOGV("openXmlAsset in %p (Java object %p)\n", am, clazz);

    if (fileName == NULL || am == NULL) {
        doThrow(env, "java/lang/NullPointerException");
        return 0;
    }

    const char* fileName8 = env->GetStringUTFChars(fileName, NULL);
    Asset* a = cookie
        ? am->openNonAsset((void*)cookie, fileName8, Asset::ACCESS_BUFFER)
        : am->openNonAsset(fileName8, Asset::ACCESS_BUFFER);

    if (a == NULL) {
        doThrow(env, "java/io/FileNotFoundException", fileName8);
        env->ReleaseStringUTFChars(fileName, fileName8);
        return 0;
    }
    env->ReleaseStringUTFChars(fileName, fileName8);

    ResXMLTree* block = new ResXMLTree();
    status_t err = block->setTo(a->getBuffer(true), a->getLength(), true);
    a->close();
    delete a;

    if (err != NO_ERROR) {
        doThrow(env, "java/io/FileNotFoundException", "Corrupt XML binary file");
        return 0;
    }

    return (jint)block;
}

static jintArray android_content_AssetManager_getArrayStringInfo(JNIEnv* env, jobject clazz,
                                                                 jint arrayResId)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());

    const ResTable::bag_entry* startOfBag;
    const ssize_t N = res.lockBag(arrayResId, &startOfBag);
    if (N < 0) {
        return NULL;
    }

    jintArray array = env->NewIntArray(N * 2);
    if (array == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        res.unlockBag(startOfBag);
        return NULL;
    }

    Res_value value;
    const ResTable::bag_entry* bag = startOfBag;
    for (size_t i = 0, j = 0; ((ssize_t)i)<N; i++, bag++) {
        jint stringIndex = -1;
        jint stringBlock = 0;
        value = bag->map.value;
        
        // Take care of resolving the found resource to its final value.
        stringBlock = res.resolveReference(&value, bag->stringBlock, NULL);
        if (value.dataType == Res_value::TYPE_STRING) {
            stringIndex = value.data;
        }
        
        //todo: It might be faster to allocate a C array to contain
        //      the blocknums and indices, put them in there and then
        //      do just one SetIntArrayRegion()
        env->SetIntArrayRegion(array, j, 1, &stringBlock);
        env->SetIntArrayRegion(array, j + 1, 1, &stringIndex);
        j = j + 2;
    }
    res.unlockBag(startOfBag);
    return array;
}

static jobjectArray android_content_AssetManager_getArrayStringResource(JNIEnv* env, jobject clazz,
                                                                        jint arrayResId)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());

    jclass cls = env->FindClass("java/lang/String");
    LOG_FATAL_IF(cls == NULL, "No string class?!?");
    if (cls == NULL) {
        return NULL;
    }

    const ResTable::bag_entry* startOfBag;
    const ssize_t N = res.lockBag(arrayResId, &startOfBag);
    if (N < 0) {
        return NULL;
    }

    jobjectArray array = env->NewObjectArray(N, cls, NULL);
    if (array == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        res.unlockBag(startOfBag);
        return NULL;
    }

    Res_value value;
    const ResTable::bag_entry* bag = startOfBag;
    size_t strLen = 0;
    for (size_t i=0; ((ssize_t)i)<N; i++, bag++) {
        value = bag->map.value;
        jstring str = NULL;
        
        // Take care of resolving the found resource to its final value.
        ssize_t block = res.resolveReference(&value, bag->stringBlock, NULL);
        if (value.dataType == Res_value::TYPE_STRING) {
            const char16_t* str16 = res.getTableStringBlock(block)->stringAt(value.data, &strLen);
            str = env->NewString(str16, strLen);
            if (str == NULL) {
                doThrow(env, "java/lang/OutOfMemoryError");
                res.unlockBag(startOfBag);
                return NULL;
            }
        }
        
        env->SetObjectArrayElement(array, i, str);
    }
    res.unlockBag(startOfBag);
    return array;
}

static jintArray android_content_AssetManager_getArrayIntResource(JNIEnv* env, jobject clazz,
                                                                        jint arrayResId)
{
    AssetManager* am = assetManagerForJavaObject(env, clazz);
    if (am == NULL) {
        return NULL;
    }
    const ResTable& res(am->getResources());

    const ResTable::bag_entry* startOfBag;
    const ssize_t N = res.lockBag(arrayResId, &startOfBag);
    if (N < 0) {
        return NULL;
    }

    jintArray array = env->NewIntArray(N);
    if (array == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        res.unlockBag(startOfBag);
        return NULL;
    }

    Res_value value;
    const ResTable::bag_entry* bag = startOfBag;
    for (size_t i=0; ((ssize_t)i)<N; i++, bag++) {
        value = bag->map.value;
        
        // Take care of resolving the found resource to its final value.
        ssize_t block = res.resolveReference(&value, bag->stringBlock, NULL);
        if (value.dataType >= Res_value::TYPE_FIRST_INT
                && value.dataType <= Res_value::TYPE_LAST_INT) {
            int intVal = value.data;
            env->SetIntArrayRegion(array, i, 1, &intVal);
        }
    }
    res.unlockBag(startOfBag);
    return array;
}

static void android_content_AssetManager_init(JNIEnv* env, jobject clazz)
{
    AssetManager* am = new AssetManager();
    if (am == NULL) {
        doThrow(env, "java/lang/OutOfMemoryError");
        return;
    }

    am->addDefaultAssets();

    LOGV("Created AssetManager %p for Java object %p\n", am, clazz);
    env->SetIntField(clazz, gAssetManagerOffsets.mObject, (jint)am);
}

static void android_content_AssetManager_destroy(JNIEnv* env, jobject clazz)
{
    AssetManager* am = (AssetManager*)
        (env->GetIntField(clazz, gAssetManagerOffsets.mObject));
    LOGV("Destroying AssetManager %p for Java object %p\n", am, clazz);
    if (am != NULL) {
        delete am;
        env->SetIntField(clazz, gAssetManagerOffsets.mObject, 0);
    }
}

static jint android_content_AssetManager_getGlobalAssetCount(JNIEnv* env, jobject clazz)
{
    return Asset::getGlobalCount();
}

static jint android_content_AssetManager_getGlobalAssetManagerCount(JNIEnv* env, jobject clazz)
{
    return AssetManager::getGlobalCount();
}

// ----------------------------------------------------------------------------

/*
 * JNI registration.
 */
static JNINativeMethod gAssetManagerMethods[] = {
    /* name, signature, funcPtr */

    // Basic asset stuff.
    { "openAsset",      "(Ljava/lang/String;I)I",
        (void*) android_content_AssetManager_openAsset },
    { "openAssetFd",      "(Ljava/lang/String;[J)Landroid/os/ParcelFileDescriptor;",
        (void*) android_content_AssetManager_openAssetFd },
    { "openNonAssetNative", "(ILjava/lang/String;I)I",
        (void*) android_content_AssetManager_openNonAssetNative },
    { "openNonAssetFdNative", "(ILjava/lang/String;[J)Landroid/os/ParcelFileDescriptor;",
        (void*) android_content_AssetManager_openNonAssetFdNative },
    { "list",           "(Ljava/lang/String;)[Ljava/lang/String;",
        (void*) android_content_AssetManager_list },
    { "destroyAsset",   "(I)V",
        (void*) android_content_AssetManager_destroyAsset },
    { "readAssetChar",  "(I)I",
        (void*) android_content_AssetManager_readAssetChar },
    { "readAsset",      "(I[BII)I",
        (void*) android_content_AssetManager_readAsset },
    { "seekAsset",      "(IJI)J",
        (void*) android_content_AssetManager_seekAsset },
    { "getAssetLength", "(I)J",
        (void*) android_content_AssetManager_getAssetLength },
    { "getAssetRemainingLength", "(I)J",
        (void*) android_content_AssetManager_getAssetRemainingLength },
    { "addAssetPath",   "(Ljava/lang/String;)I",
        (void*) android_content_AssetManager_addAssetPath },
    { "isUpToDate",     "()Z",
        (void*) android_content_AssetManager_isUpToDate },

    // Resources.
    { "setLocale",      "(Ljava/lang/String;)V",
        (void*) android_content_AssetManager_setLocale },
    { "getLocales",      "()[Ljava/lang/String;",
        (void*) android_content_AssetManager_getLocales },
    { "setConfiguration", "(IILjava/lang/String;IIIIIIIII)V",
        (void*) android_content_AssetManager_setConfiguration },
    { "getResourceIdentifier","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I",
        (void*) android_content_AssetManager_getResourceIdentifier },
    { "getResourceName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourceName },
    { "getResourcePackageName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourcePackageName },
    { "getResourceTypeName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourceTypeName },
    { "getResourceEntryName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getResourceEntryName },
    { "loadResourceValue","(ILandroid/util/TypedValue;Z)I",
        (void*) android_content_AssetManager_loadResourceValue },
    { "loadResourceBagValue","(IILandroid/util/TypedValue;Z)I",
        (void*) android_content_AssetManager_loadResourceBagValue },
    { "getStringBlockCount","()I",
        (void*) android_content_AssetManager_getStringBlockCount },
    { "getNativeStringBlock","(I)I",
        (void*) android_content_AssetManager_getNativeStringBlock },
    { "getCookieName","(I)Ljava/lang/String;",
        (void*) android_content_AssetManager_getCookieName },

    // Themes.
    { "newTheme", "()I",
        (void*) android_content_AssetManager_newTheme },
    { "deleteTheme", "(I)V",
        (void*) android_content_AssetManager_deleteTheme },
    { "applyThemeStyle", "(IIZ)V",
        (void*) android_content_AssetManager_applyThemeStyle },
    { "copyTheme", "(II)V",
        (void*) android_content_AssetManager_copyTheme },
    { "loadThemeAttributeValue", "(IILandroid/util/TypedValue;Z)I",
        (void*) android_content_AssetManager_loadThemeAttributeValue },
    { "dumpTheme", "(IILjava/lang/String;Ljava/lang/String;)V",
        (void*) android_content_AssetManager_dumpTheme },
    { "applyStyle","(IIII[I[I[I)Z",
        (void*) android_content_AssetManager_applyStyle },
    { "retrieveAttributes","(I[I[I[I)Z",
        (void*) android_content_AssetManager_retrieveAttributes },
    { "getArraySize","(I)I",
        (void*) android_content_AssetManager_getArraySize },
    { "retrieveArray","(I[I)I",
        (void*) android_content_AssetManager_retrieveArray },

    // XML files.
    { "openXmlAssetNative", "(ILjava/lang/String;)I",
        (void*) android_content_AssetManager_openXmlAssetNative },

    // Arrays.
    { "getArrayStringResource","(I)[Ljava/lang/String;",
        (void*) android_content_AssetManager_getArrayStringResource },
    { "getArrayStringInfo","(I)[I",
        (void*) android_content_AssetManager_getArrayStringInfo },
    { "getArrayIntResource","(I)[I",
        (void*) android_content_AssetManager_getArrayIntResource },

    // Bookkeeping.
    { "init",           "()V",
        (void*) android_content_AssetManager_init },
    { "destroy",        "()V",
        (void*) android_content_AssetManager_destroy },
    { "getGlobalAssetCount", "()I",
        (void*) android_content_AssetManager_getGlobalAssetCount },
    { "getGlobalAssetManagerCount", "()I",
        (void*) android_content_AssetManager_getGlobalAssetCount },
};

int register_android_content_AssetManager(JNIEnv* env)
{
    jclass typedValue = env->FindClass("android/util/TypedValue");
    LOG_FATAL_IF(typedValue == NULL, "Unable to find class android/util/TypedValue");
    gTypedValueOffsets.mType
        = env->GetFieldID(typedValue, "type", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mType == NULL, "Unable to find TypedValue.type");
    gTypedValueOffsets.mData
        = env->GetFieldID(typedValue, "data", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mData == NULL, "Unable to find TypedValue.data");
    gTypedValueOffsets.mString
        = env->GetFieldID(typedValue, "string", "Ljava/lang/CharSequence;");
    LOG_FATAL_IF(gTypedValueOffsets.mString == NULL, "Unable to find TypedValue.string");
    gTypedValueOffsets.mAssetCookie
        = env->GetFieldID(typedValue, "assetCookie", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mAssetCookie == NULL, "Unable to find TypedValue.assetCookie");
    gTypedValueOffsets.mResourceId
        = env->GetFieldID(typedValue, "resourceId", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mResourceId == NULL, "Unable to find TypedValue.resourceId");
    gTypedValueOffsets.mChangingConfigurations
        = env->GetFieldID(typedValue, "changingConfigurations", "I");
    LOG_FATAL_IF(gTypedValueOffsets.mChangingConfigurations == NULL, "Unable to find TypedValue.changingConfigurations");

    jclass assetFd = env->FindClass("android/content/res/AssetFileDescriptor");
    LOG_FATAL_IF(assetFd == NULL, "Unable to find class android/content/res/AssetFileDescriptor");
    gAssetFileDescriptorOffsets.mFd
        = env->GetFieldID(assetFd, "mFd", "Landroid/os/ParcelFileDescriptor;");
    LOG_FATAL_IF(gAssetFileDescriptorOffsets.mFd == NULL, "Unable to find AssetFileDescriptor.mFd");
    gAssetFileDescriptorOffsets.mStartOffset
        = env->GetFieldID(assetFd, "mStartOffset", "J");
    LOG_FATAL_IF(gAssetFileDescriptorOffsets.mStartOffset == NULL, "Unable to find AssetFileDescriptor.mStartOffset");
    gAssetFileDescriptorOffsets.mLength
        = env->GetFieldID(assetFd, "mLength", "J");
    LOG_FATAL_IF(gAssetFileDescriptorOffsets.mLength == NULL, "Unable to find AssetFileDescriptor.mLength");

    jclass assetManager = env->FindClass("android/content/res/AssetManager");
    LOG_FATAL_IF(assetManager == NULL, "Unable to find class android/content/res/AssetManager");
    gAssetManagerOffsets.mObject
        = env->GetFieldID(assetManager, "mObject", "I");
    LOG_FATAL_IF(gAssetManagerOffsets.mObject == NULL, "Unable to find AssetManager.mObject");

    g_stringClass = env->FindClass("java/lang/String");

    return AndroidRuntime::registerNativeMethods(env,
            "android/content/res/AssetManager", gAssetManagerMethods, NELEM(gAssetManagerMethods));
}

}; // namespace android
