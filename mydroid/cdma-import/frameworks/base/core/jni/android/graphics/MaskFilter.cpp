#include "GraphicsJNI.h"
#include "SkMaskFilter.h"
#include "SkBlurMaskFilter.h"

#include <jni.h>

class SkMaskFilterGlue {
public:
    static void destructor(JNIEnv* env, jobject, SkMaskFilter* filter) {
        SkASSERT(filter);
        filter->unref();
    }

    static SkMaskFilter* createBlur(JNIEnv* env, jobject, float radius, int blurStyle) {
        return SkBlurMaskFilter::Create(SkFloatToScalar(radius), (SkBlurMaskFilter::BlurStyle)blurStyle);
    }
 
    static SkMaskFilter* createEmboss(JNIEnv* env, jobject, jfloatArray dirArray, float ambient, float specular, float radius) {
        SkScalar direction[3];

        AutoJavaFloatArray autoDir(env, dirArray, 3);
        float* values = autoDir.ptr();
        for (int i = 0; i < 3; i++) {
            direction[i] = SkFloatToScalar(values[i]);
        }

        return SkBlurMaskFilter::CreateEmboss(direction, SkFloatToScalar(ambient),
                                              SkFloatToScalar(specular), SkFloatToScalar(radius));
    }
};

static JNINativeMethod gMaskFilterMethods[] = {
    { "nativeDestructor",   "(I)V",     (void*)SkMaskFilterGlue::destructor      }
};

static JNINativeMethod gBlurMaskFilterMethods[] = {
    { "nativeConstructor",  "(FI)I",    (void*)SkMaskFilterGlue::createBlur      }
};

static JNINativeMethod gEmbossMaskFilterMethods[] = {
    { "nativeConstructor",  "([FFFF)I", (void*)SkMaskFilterGlue::createEmboss    }
};

#include <android_runtime/AndroidRuntime.h>

#define REG(env, name, array)                                                                       \
    result = android::AndroidRuntime::registerNativeMethods(env, name, array, SK_ARRAY_COUNT(array));  \
    if (result < 0) return result

int register_android_graphics_MaskFilter(JNIEnv* env);
int register_android_graphics_MaskFilter(JNIEnv* env)
{
    int result;
    
    REG(env, "android/graphics/MaskFilter", gMaskFilterMethods);
    REG(env, "android/graphics/BlurMaskFilter", gBlurMaskFilterMethods);
    REG(env, "android/graphics/EmbossMaskFilter", gEmbossMaskFilterMethods);
    
    return 0;
}

