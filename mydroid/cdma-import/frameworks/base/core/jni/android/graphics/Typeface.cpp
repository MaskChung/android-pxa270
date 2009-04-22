#include "jni.h"
#include <android_runtime/AndroidRuntime.h>

#include "GraphicsJNI.h"
#include <android_runtime/android_util_AssetManager.h>
#include "SkStream.h"
#include "SkTypeface.h"
#include <utils/AssetManager.h>

using namespace android;

class AutoJavaStringToUTF8 {
public:
    AutoJavaStringToUTF8(JNIEnv* env, jstring str) : fEnv(env), fJStr(str)
    {
        fCStr = env->GetStringUTFChars(str, NULL);
    }
    ~AutoJavaStringToUTF8()
    {
        fEnv->ReleaseStringUTFChars(fJStr, fCStr);
    }
    const char* c_str() const { return fCStr; }

private:
    JNIEnv*     fEnv;
    jstring     fJStr;
    const char* fCStr;
};

static SkTypeface* Typeface_create(JNIEnv* env, jobject, jstring name,
                                   SkTypeface::Style style) {
    SkTypeface* face;

    if (NULL == name) {
        face = SkTypeface::Create(NULL, (SkTypeface::Style)style);
    }
    else {
        AutoJavaStringToUTF8    str(env, name);
        face = SkTypeface::Create(str.c_str(), style);
    }
    return face;
}

static SkTypeface* Typeface_createFromTypeface(JNIEnv* env, jobject, SkTypeface* family, int style) {
    return SkTypeface::CreateFromTypeface(family, (SkTypeface::Style)style);
}
 
static void Typeface_unref(JNIEnv* env, jobject obj, SkTypeface* face) {
    face->unref();
}

static int Typeface_getStyle(JNIEnv* env, jobject obj, SkTypeface* face) {
    return face->getStyle();
}

class AssetStream : public SkStream {
public:
    AssetStream(Asset* asset, bool hasMemoryBase) : fAsset(asset)
    {
        fMemoryBase = hasMemoryBase ? fAsset->getBuffer(false) : NULL;
    }

    virtual ~AssetStream()
    {
        delete fAsset;
    }
    
    virtual const void* getMemoryBase()
    {
        return fMemoryBase;
    }

	virtual bool rewind()
    {
        off_t pos = fAsset->seek(0, SEEK_SET);
        return pos != (off_t)-1;
    }
    
	virtual size_t read(void* buffer, size_t size)
    {
        ssize_t amount;
        
        if (NULL == buffer)
        {
            if (0 == size)  // caller is asking us for our total length
                return fAsset->getLength();
            
            // asset->seek returns new total offset
            // we want to return amount that was skipped
            
            off_t oldOffset = fAsset->seek(0, SEEK_CUR);
            if (-1 == oldOffset)
                return 0;
            off_t newOffset = fAsset->seek(size, SEEK_CUR);
            if (-1 == newOffset)
                return 0;
            
            amount = newOffset - oldOffset;
        }
        else
        {
            amount = fAsset->read(buffer, size);
        }
        
        if (amount < 0)
            amount = 0;
        return amount;
    }
    
private:
    Asset*      fAsset;
    const void* fMemoryBase;
};

static SkTypeface* Typeface_createFromAsset(JNIEnv* env, jobject,
                                            jobject jassetMgr,
                                            jstring jpath) {
    
    NPE_CHECK_RETURN_ZERO(env, jassetMgr);
    NPE_CHECK_RETURN_ZERO(env, jpath);
    
    AssetManager* mgr = assetManagerForJavaObject(env, jassetMgr);
    if (NULL == mgr) {
        return NULL;
    }
    
    AutoJavaStringToUTF8    str(env, jpath);
    Asset* asset = mgr->open(str.c_str(), Asset::ACCESS_BUFFER);
    if (NULL == asset) {
        return NULL;
    }
    
    return SkTypeface::CreateFromStream(new AssetStream(asset, true));
}

///////////////////////////////////////////////////////////////////////////////

static JNINativeMethod gTypefaceMethods[] = {
    { "nativeCreate",        "(Ljava/lang/String;I)I", (void*)Typeface_create },
    { "nativeCreateFromTypeface", "(II)I", (void*)Typeface_createFromTypeface },
    { "nativeUnref",              "(I)V",  (void*)Typeface_unref },
    { "nativeGetStyle",           "(I)I",  (void*)Typeface_getStyle },
    { "nativeCreateFromAsset",
                        "(Landroid/content/res/AssetManager;Ljava/lang/String;)I",
                                            (void*)Typeface_createFromAsset }
};

int register_android_graphics_Typeface(JNIEnv* env);
int register_android_graphics_Typeface(JNIEnv* env)
{
    return android::AndroidRuntime::registerNativeMethods(env,
                                                       "android/graphics/Typeface",
                                                       gTypefaceMethods,
                                                       SK_ARRAY_COUNT(gTypefaceMethods));
}

