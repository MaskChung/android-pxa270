/*
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

#include <android_runtime/AndroidRuntime.h>
#include <utils/misc.h>

#include <GLES/egl.h>
#include <GLES/gl.h>

#include <ui/EGLNativeWindowSurface.h>
#include <ui/Surface.h>
#include <graphics/SkBitmap.h>
#include <graphics/SkPixelRef.h>

namespace android {

static jclass gDisplay_class;
static jclass gContext_class;
static jclass gSurface_class;
static jclass gConfig_class;

static jmethodID gConfig_ctorID;

static jfieldID gDisplay_EGLDisplayFieldID;
static jfieldID gContext_EGLContextFieldID;
static jfieldID gSurface_EGLSurfaceFieldID;
static jfieldID gSurface_NativePixelRefFieldID;
static jfieldID gConfig_EGLConfigFieldID;
static jfieldID gSurface_SurfaceFieldID;
static jfieldID gBitmap_NativeBitmapFieldID;

static __attribute__((noinline))
void doThrow(JNIEnv* env, const char* exc, const char* msg = NULL)
{
    jclass npeClazz = env->FindClass(exc);
    env->ThrowNew(npeClazz, msg);
}

static __attribute__((noinline))
bool hasException(JNIEnv *env) {
    if (env->ExceptionCheck() != 0) {
        env->ExceptionDescribe();
        return true;
    }
    return false;
}

static __attribute__((noinline))
jclass make_globalref(JNIEnv* env, const char classname[]) {
    jclass c = env->FindClass(classname);
    return (jclass)env->NewGlobalRef(c);
}

static inline EGLDisplay getDisplay(JNIEnv* env, jobject o) {
    if (!o) return EGL_NO_DISPLAY;
    return (EGLDisplay)env->GetIntField(o, gDisplay_EGLDisplayFieldID);
}
static inline EGLSurface getSurface(JNIEnv* env, jobject o) {
    if (!o) return EGL_NO_SURFACE;
    return (EGLSurface)env->GetIntField(o, gSurface_EGLSurfaceFieldID);
}
static inline EGLContext getContext(JNIEnv* env, jobject o) {
    if (!o) return EGL_NO_CONTEXT;
    return (EGLContext)env->GetIntField(o, gContext_EGLContextFieldID);
}
static inline EGLConfig getConfig(JNIEnv* env, jobject o) {
    if (!o) return 0;
    return (EGLConfig)env->GetIntField(o, gConfig_EGLConfigFieldID);
}
static void nativeClassInit(JNIEnv *_env, jclass eglImplClass)
{
    gDisplay_class = make_globalref(_env, "com/google/android/gles_jni/EGLDisplayImpl");
    gContext_class = make_globalref(_env, "com/google/android/gles_jni/EGLContextImpl");
    gSurface_class = make_globalref(_env, "com/google/android/gles_jni/EGLSurfaceImpl");
    gConfig_class  = make_globalref(_env, "com/google/android/gles_jni/EGLConfigImpl");

    gConfig_ctorID  = _env->GetMethodID(gConfig_class,  "<init>", "(I)V");
    
    gDisplay_EGLDisplayFieldID = _env->GetFieldID(gDisplay_class, "mEGLDisplay", "I");
    gContext_EGLContextFieldID = _env->GetFieldID(gContext_class, "mEGLContext", "I");
    gSurface_EGLSurfaceFieldID = _env->GetFieldID(gSurface_class, "mEGLSurface", "I");
    gSurface_NativePixelRefFieldID = _env->GetFieldID(gSurface_class, "mNativePixelRef", "I");
    gConfig_EGLConfigFieldID   = _env->GetFieldID(gConfig_class,  "mEGLConfig",  "I");

    jclass surface_class = _env->FindClass("android/view/Surface");
    gSurface_SurfaceFieldID = _env->GetFieldID(surface_class, "mSurface", "I");

    jclass bitmap_class = _env->FindClass("android/graphics/Bitmap");
    gBitmap_NativeBitmapFieldID = _env->GetFieldID(bitmap_class, "mNativeBitmap", "I");    
}

jboolean jni_eglInitialize(JNIEnv *_env, jobject _this, jobject display,
        jintArray major_minor) {
    
    EGLDisplay dpy = getDisplay(_env, display);
    jboolean success = eglInitialize(dpy, NULL, NULL);
    if (success && major_minor) {
        int len = _env->GetArrayLength(major_minor);
        if (len) {
            // we're exposing only EGL 1.0
            jint* base = (jint *)_env->GetPrimitiveArrayCritical(major_minor, (jboolean *)0);
            if (len >= 1) base[0] = 1;
            if (len >= 2) base[1] = 0;
            _env->ReleasePrimitiveArrayCritical(major_minor, base, JNI_ABORT);
        }
    }
    return success;
}

jboolean jni_eglQueryContext(JNIEnv *_env, jobject _this, jobject display,
        jobject context, jint attribute, jintArray value) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLContext ctx = getContext(_env, context);
    if (value == NULL) {
        doThrow(_env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    jboolean success = JNI_FALSE;
    int len = _env->GetArrayLength(value);
    if (len) {
        jint* base = (jint *)_env->GetPrimitiveArrayCritical(value, (jboolean *)0);
        success = eglQueryContext(dpy, ctx, attribute, base);
        _env->ReleasePrimitiveArrayCritical(value, base, JNI_ABORT);
    }
    return success;
}
    
jboolean jni_eglQuerySurface(JNIEnv *_env, jobject _this, jobject display,
        jobject surface, jint attribute, jintArray value) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLContext sur = getSurface(_env, surface);
    if (value == NULL) {
        doThrow(_env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    jboolean success = JNI_FALSE;
    int len = _env->GetArrayLength(value);
    if (len) {
        jint* base = (jint *)_env->GetPrimitiveArrayCritical(value, (jboolean *)0);
        success = eglQuerySurface(dpy, sur, attribute, base);
        _env->ReleasePrimitiveArrayCritical(value, base, JNI_ABORT);
    }
    return success;
}

jboolean jni_eglChooseConfig(JNIEnv *_env, jobject _this, jobject display,
        jintArray attrib_list, jobjectArray configs, jint config_size, jintArray num_config) {    
    EGLDisplay dpy = getDisplay(_env, display);
    if (attrib_list==NULL || configs==NULL || num_config==NULL) {
        doThrow(_env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    jboolean success = JNI_FALSE;
    jint* attrib_base  = (jint *)_env->GetPrimitiveArrayCritical(attrib_list, (jboolean *)0);
    jint* num_base     = (jint *)_env->GetPrimitiveArrayCritical(num_config, (jboolean *)0);
    EGLConfig nativeConfigs[config_size];
    success = eglChooseConfig(dpy, attrib_base, nativeConfigs, config_size, num_base);
    int num = num_base[0];
    _env->ReleasePrimitiveArrayCritical(num_config, num_base, JNI_ABORT);
    _env->ReleasePrimitiveArrayCritical(attrib_list, attrib_base, JNI_ABORT);
    if (success) {
        for (int i=0 ; i<num ; i++) {
            jobject obj = _env->NewObject(gConfig_class, gConfig_ctorID, (jint)nativeConfigs[i]);
            _env->SetObjectArrayElement(configs, i, obj);
        }
    }
    return success;
} 

jint jni_eglCreateContext(JNIEnv *_env, jobject _this, jobject display,
        jobject config, jobject share_context, jintArray attrib_list) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLConfig  cnf = getConfig(_env, config);
    EGLContext shr = getContext(_env, share_context);
    jint* base = 0;
    if (attrib_list) {
        // XXX: if array is malformed, we should return an NPE instead of segfault
        base = (jint *)_env->GetPrimitiveArrayCritical(attrib_list, (jboolean *)0);
    }
    EGLContext ctx = eglCreateContext(dpy, cnf, shr, base);
    if (attrib_list) {
        _env->ReleasePrimitiveArrayCritical(attrib_list, base, JNI_ABORT);
    }
    return (jint)ctx;
}

jint jni_eglCreatePbufferSurface(JNIEnv *_env, jobject _this, jobject display,
        jobject config, jintArray attrib_list) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLConfig  cnf = getConfig(_env, config);
    jint* base = 0;
    if (attrib_list) {
        // XXX: if array is malformed, we should return an NPE instead of segfault
        base = (jint *)_env->GetPrimitiveArrayCritical(attrib_list, (jboolean *)0);
    }
    EGLSurface sur = eglCreatePbufferSurface(dpy, cnf, base);
    if (attrib_list) {
        _env->ReleasePrimitiveArrayCritical(attrib_list, base, JNI_ABORT);
    }
    return (jint)sur;
}

static PixelFormat convertPixelFormat(SkBitmap::Config format)
{
    switch (format) {
    case SkBitmap::kARGB_8888_Config:   return PIXEL_FORMAT_RGBA_8888;
    case SkBitmap::kARGB_4444_Config:   return PIXEL_FORMAT_RGBA_4444;
    case SkBitmap::kRGB_565_Config:     return PIXEL_FORMAT_RGB_565;
    case SkBitmap::kA8_Config:          return PIXEL_FORMAT_A_8;
    default:                            return PIXEL_FORMAT_NONE;
    }
}

void jni_eglCreatePixmapSurface(JNIEnv *_env, jobject _this, jobject out_sur,
        jobject display, jobject config, jobject native_pixmap,
        jintArray attrib_list) 
{
    EGLDisplay dpy = getDisplay(_env, display);
    EGLConfig  cnf = getConfig(_env, config);
    jint* base = 0;

    SkBitmap const * nativeBitmap =
            (SkBitmap const *)_env->GetIntField(native_pixmap,
                    gBitmap_NativeBitmapFieldID);
    SkPixelRef* ref = nativeBitmap ? nativeBitmap->pixelRef() : 0;
    if (ref == NULL) {
        doThrow(_env, "java/lang/NullPointerException", "Bitmap has no PixelRef");
        return;
    }
    
    ref->safeRef();
    ref->lockPixels();
    
    egl_native_pixmap_t pixmap;
    pixmap.version = sizeof(pixmap);
    pixmap.width  = nativeBitmap->width();
    pixmap.height = nativeBitmap->height();
    pixmap.stride = nativeBitmap->rowBytes() / nativeBitmap->bytesPerPixel();
    pixmap.format = convertPixelFormat(nativeBitmap->config());
    pixmap.data   = (uint8_t*)ref->pixels();
    
    if (attrib_list) {
        // XXX: if array is malformed, we should return an NPE instead of segfault
        base = (jint *)_env->GetPrimitiveArrayCritical(attrib_list, (jboolean *)0);
    }
    EGLSurface sur = eglCreatePixmapSurface(dpy, cnf, &pixmap, base);
    if (attrib_list) {
        _env->ReleasePrimitiveArrayCritical(attrib_list, base, JNI_ABORT);
    }

    if (sur != EGL_NO_SURFACE) {
        _env->SetIntField(out_sur, gSurface_EGLSurfaceFieldID, (int)sur);
        _env->SetIntField(out_sur, gSurface_NativePixelRefFieldID, (int)ref);
    } else {
        ref->unlockPixels();
        ref->safeUnref();
    }
}

jint jni_eglCreateWindowSurface(JNIEnv *_env, jobject _this, jobject display,
        jobject config, jobject native_window, jintArray attrib_list) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLContext cnf = getConfig(_env, config);
    Surface* window = 0;
    if (native_window == NULL) {
not_valid_surface:
        doThrow(_env, "java/lang/NullPointerException",
                "Make sure the SurfaceView or associated SurfaceHolder has a valid Surface");
        return 0;
    }
    window = (Surface*)_env->GetIntField(native_window, gSurface_SurfaceFieldID);
    if (window == NULL)
        goto not_valid_surface;

    jint* base = 0;
    if (attrib_list) {
        // XXX: if array is malformed, we should return an NPE instead of segfault
        base = (jint *)_env->GetPrimitiveArrayCritical(attrib_list, (jboolean *)0);
    }
    EGLSurface sur = eglCreateWindowSurface(dpy, cnf, new EGLNativeWindowSurface(window), base);
    if (attrib_list) {
        _env->ReleasePrimitiveArrayCritical(attrib_list, base, JNI_ABORT);
    }
    return (jint)sur;
}

jboolean jni_eglGetConfigAttrib(JNIEnv *_env, jobject _this, jobject display,
        jobject config, jint attribute, jintArray value) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLContext cnf = getConfig(_env, config);
    if (value == NULL) {
        doThrow(_env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    jboolean success = JNI_FALSE;
    int len = _env->GetArrayLength(value);
    if (len) {
        jint* base = (jint *)_env->GetPrimitiveArrayCritical(value, (jboolean *)0);
        success = eglGetConfigAttrib(dpy, cnf, attribute, base);
        _env->ReleasePrimitiveArrayCritical(value, base, JNI_ABORT);
    }
    return success;
}

jboolean jni_eglGetConfigs(JNIEnv *_env, jobject _this, jobject display,
        jobjectArray configs, jint config_size, jintArray num_config) {
    EGLDisplay dpy = getDisplay(_env, display);
    jboolean success = JNI_FALSE;
    if (num_config == NULL) {
        doThrow(_env, "java/lang/NullPointerException");
        return JNI_FALSE;
    }
    jint* num_base = (jint *)_env->GetPrimitiveArrayCritical(num_config, (jboolean *)0);
    EGLConfig nativeConfigs[config_size];
    success = eglGetConfigs(dpy, configs ? nativeConfigs : 0, config_size, num_base);
    int num = num_base[0];
    _env->ReleasePrimitiveArrayCritical(num_config, num_base, JNI_ABORT);

    if (success && configs) {
        for (int i=0 ; i<num ; i++) {
            jobject obj = _env->GetObjectArrayElement(configs, i);
            if (obj == NULL) {
                doThrow(_env, "java/lang/NullPointerException");
                break;
            }
            _env->SetIntField(obj, gConfig_EGLConfigFieldID, (jint)nativeConfigs[i]);
        }
    }
    return success;
}
    
jint jni_eglGetError(JNIEnv *_env, jobject _this) {
    EGLint error = eglGetError();
    return error;
}

jint jni_eglGetCurrentContext(JNIEnv *_env, jobject _this) {
    return (jint)eglGetCurrentContext();
}

jint jni_eglGetCurrentDisplay(JNIEnv *_env, jobject _this) {
    return (jint)eglGetCurrentDisplay();
}

jint jni_eglGetCurrentSurface(JNIEnv *_env, jobject _this, jint readdraw) {
    return (jint)eglGetCurrentSurface(readdraw);
}

jboolean jni_eglDestroyContext(JNIEnv *_env, jobject _this, jobject display, jobject context) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLContext ctx = getContext(_env, context);
    return eglDestroyContext(dpy, ctx);
}

jboolean jni_eglDestroySurface(JNIEnv *_env, jobject _this, jobject display, jobject surface) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLSurface sur = getSurface(_env, surface);

    if (sur) {
        SkPixelRef* ref = (SkPixelRef*)(_env->GetIntField(surface,
                gSurface_NativePixelRefFieldID));
        if (ref) {
            ref->unlockPixels();
            ref->safeUnref();
        }
    }
    return eglDestroySurface(dpy, sur);
}

jint jni_eglGetDisplay(JNIEnv *_env, jobject _this, jobject native_display) {
    return (jint)eglGetDisplay(EGL_DEFAULT_DISPLAY);
}

jboolean jni_eglMakeCurrent(JNIEnv *_env, jobject _this, jobject display, jobject draw, jobject read, jobject context) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLSurface sdr = getSurface(_env, draw);
    EGLSurface srd = getSurface(_env, read);
    EGLContext ctx = getContext(_env, context);
    return eglMakeCurrent(dpy, sdr, srd, ctx);
}

jstring jni_eglQueryString(JNIEnv *_env, jobject _this, jobject display, jint name) {
    EGLDisplay dpy = getDisplay(_env, display);
    const char* chars = eglQueryString(dpy, name);
    return _env->NewString((const jchar *)chars,
                            (jsize)strlen((const char *)chars));
}

jboolean jni_eglSwapBuffers(JNIEnv *_env, jobject _this, jobject display, jobject surface) {
    EGLDisplay dpy = getDisplay(_env, display);
    EGLSurface sur = getSurface(_env, surface);
    return eglSwapBuffers(dpy, sur);
}

jboolean jni_eglTerminate(JNIEnv *_env, jobject _this, jobject display) {
    EGLDisplay dpy = getDisplay(_env, display);
    return eglTerminate(dpy);
}

jboolean jni_eglCopyBuffers(JNIEnv *_env, jobject _this, jobject display,
        jobject surface, jobject native_pixmap) {
    // TODO: implement me
    return JNI_FALSE;
}

jboolean jni_eglWaitGL(JNIEnv *_env, jobject _this) {
    return eglWaitGL();
}

jboolean jni_eglWaitNative(JNIEnv *_env, jobject _this, jint engine, jobject bindTarget) {
    return eglWaitNative(engine);
}


static const char *classPathName = "com/google/android/gles_jni/EGLImpl";

#define DISPLAY "Ljavax/microedition/khronos/egl/EGLDisplay;"
#define CONTEXT "Ljavax/microedition/khronos/egl/EGLContext;"
#define CONFIG  "Ljavax/microedition/khronos/egl/EGLConfig;"
#define SURFACE "Ljavax/microedition/khronos/egl/EGLSurface;"
#define OBJECT  "Ljava/lang/Object;"
#define STRING  "Ljava/lang/String;"

static JNINativeMethod methods[] = {
{"_nativeClassInit","()V", (void*)nativeClassInit },
{"eglWaitGL",       "()Z", (void*)jni_eglWaitGL },
{"eglInitialize",   "(" DISPLAY "[I)Z", (void*)jni_eglInitialize },
{"eglQueryContext", "(" DISPLAY CONTEXT "I[I)Z", (void*)jni_eglQueryContext },
{"eglQuerySurface", "(" DISPLAY SURFACE "I[I)Z", (void*)jni_eglQuerySurface },
{"eglChooseConfig", "(" DISPLAY "[I[" CONFIG "I[I)Z", (void*)jni_eglChooseConfig },
{"_eglCreateContext","(" DISPLAY CONFIG CONTEXT "[I)I", (void*)jni_eglCreateContext },
{"eglGetConfigs",   "(" DISPLAY "[" CONFIG "I[I)Z", (void*)jni_eglGetConfigs },
{"eglTerminate",    "(" DISPLAY ")Z", (void*)jni_eglTerminate },
{"eglCopyBuffers",  "(" DISPLAY SURFACE OBJECT ")Z", (void*)jni_eglCopyBuffers },
{"eglWaitNative",   "(I" OBJECT ")Z", (void*)jni_eglWaitNative },
{"eglGetError",     "()I", (void*)jni_eglGetError },
{"eglGetConfigAttrib", "(" DISPLAY CONFIG "I[I)Z", (void*)jni_eglGetConfigAttrib },
{"_eglGetDisplay",   "(" OBJECT ")I", (void*)jni_eglGetDisplay },
{"_eglGetCurrentContext",  "()I", (void*)jni_eglGetCurrentContext },
{"_eglGetCurrentDisplay",  "()I", (void*)jni_eglGetCurrentDisplay },
{"_eglGetCurrentSurface",  "(I)I", (void*)jni_eglGetCurrentSurface },
{"_eglCreatePbufferSurface","(" DISPLAY CONFIG "[I)I", (void*)jni_eglCreatePbufferSurface },
{"_eglCreatePixmapSurface", "(" SURFACE DISPLAY CONFIG OBJECT "[I)V", (void*)jni_eglCreatePixmapSurface },
{"_eglCreateWindowSurface", "(" DISPLAY CONFIG OBJECT "[I)I", (void*)jni_eglCreateWindowSurface },
{"eglDestroyContext",      "(" DISPLAY CONTEXT ")Z", (void*)jni_eglDestroyContext },
{"eglDestroySurface",      "(" DISPLAY SURFACE ")Z", (void*)jni_eglDestroySurface },
{"eglMakeCurrent",         "(" DISPLAY SURFACE SURFACE CONTEXT")Z", (void*)jni_eglMakeCurrent },
{"eglQueryString",         "(" DISPLAY "I)" STRING, (void*)jni_eglQueryString },
{"eglSwapBuffers",         "(" DISPLAY SURFACE ")Z", (void*)jni_eglSwapBuffers },
};

} // namespace android

int register_com_google_android_gles_jni_EGLImpl(JNIEnv *_env)
{
    int err;
    err = android::AndroidRuntime::registerNativeMethods(_env,
            android::classPathName, android::methods, NELEM(android::methods));
    return err;
}

