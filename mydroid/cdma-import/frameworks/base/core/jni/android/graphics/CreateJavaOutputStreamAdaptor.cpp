#include "CreateJavaOutputStreamAdaptor.h"

#define RETURN_NULL_IF_NULL(value) \
    do { if (!(value)) { SkASSERT(0); return NULL; } } while (false)

static jclass       gInputStream_Clazz;
static jmethodID    gInputStream_resetMethodID;
static jmethodID    gInputStream_availableMethodID;
static jmethodID    gInputStream_readMethodID;
static jmethodID    gInputStream_skipMethodID;

class JavaInputStreamAdaptor : public SkStream {
public:
    JavaInputStreamAdaptor(JNIEnv* env, jobject js, jbyteArray ar)
        : fEnv(env), fJavaInputStream(js), fJavaByteArray(ar) {
        SkASSERT(ar);
        fCapacity   = env->GetArrayLength(ar);
        SkASSERT(fCapacity > 0);
        fBytesRead  = 0;
    }
    
	virtual bool rewind() {
        JNIEnv* env = fEnv;
        
        fBytesRead = 0;

        env->CallVoidMethod(fJavaInputStream, gInputStream_resetMethodID);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            printf("------- reset threw an exception\n");
            return false;
        }
        return true;
    }
    
	virtual size_t read(void* buffer, size_t size) {
        JNIEnv* env = fEnv;
        
        if (buffer == NULL && size == 0) {
            jint avail = env->CallIntMethod(fJavaInputStream,
                                            gInputStream_availableMethodID);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                printf("------- available threw an exception\n");
                avail = 0;
            }
            return avail;
        }

        size_t bytesRead = 0;

        if (buffer == NULL) { // skip
            jlong skipped = env->CallLongMethod(fJavaInputStream,
                                        gInputStream_skipMethodID, (jlong)size);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                printf("------- available threw an exception\n");
                return 0;
            }
            if (skipped < 0) {
                return 0;
            }
            return (size_t)skipped;
        }

        // read the bytes
        do {
            size_t requested = size;
            if (requested > fCapacity)
                requested = fCapacity;

            jint n = env->CallIntMethod(fJavaInputStream,
                    gInputStream_readMethodID, fJavaByteArray, 0, requested);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                printf("---- read threw an exception\n");
                return 0;
            }

            if (n <= 0) {
                break;  // eof
            }

            jbyte* array = env->GetByteArrayElements(fJavaByteArray, NULL);
            memcpy(buffer, array, n);
            env->ReleaseByteArrayElements(fJavaByteArray, array, 0);
            
            buffer = (void*)((char*)buffer + n);
            bytesRead += n;
            size -= n;
            fBytesRead += n;
        } while (size != 0);
        
        return bytesRead;
    }
    
private:
    JNIEnv*     fEnv;
    jobject     fJavaInputStream;   // the caller owns this object
    jbyteArray  fJavaByteArray;     // the caller owns this object
    size_t      fCapacity;
    size_t      fBytesRead;
};

SkStream* CreateJavaInputStreamAdaptor(JNIEnv* env, jobject stream,
                                       jbyteArray storage) {
    static bool gInited;

    if (!gInited) {
        gInputStream_Clazz = env->FindClass("java/io/InputStream");
        RETURN_NULL_IF_NULL(gInputStream_Clazz);
        gInputStream_Clazz = (jclass)env->NewGlobalRef(gInputStream_Clazz);

        gInputStream_resetMethodID      = env->GetMethodID(gInputStream_Clazz,
                                                           "reset", "()V");
        gInputStream_availableMethodID  = env->GetMethodID(gInputStream_Clazz,
                                                           "available", "()I");
        gInputStream_readMethodID       = env->GetMethodID(gInputStream_Clazz,
                                                           "read", "([BII)I");
        gInputStream_skipMethodID       = env->GetMethodID(gInputStream_Clazz,
                                                           "skip", "(J)J");

        RETURN_NULL_IF_NULL(gInputStream_resetMethodID);
        RETURN_NULL_IF_NULL(gInputStream_availableMethodID);
        RETURN_NULL_IF_NULL(gInputStream_availableMethodID);
        RETURN_NULL_IF_NULL(gInputStream_skipMethodID);

        gInited = true;
    }

    return new JavaInputStreamAdaptor(env, stream, storage);
}

///////////////////////////////////////////////////////////////////////////////

static jclass       gOutputStream_Clazz;
static jmethodID    gOutputStream_writeMethodID;
static jmethodID    gOutputStream_flushMethodID;

class SkJavaOutputStream : public SkWStream {
public:
    SkJavaOutputStream(JNIEnv* env, jobject stream, jbyteArray storage)
        : fEnv(env), fJavaOutputStream(stream), fJavaByteArray(storage) {
        fCapacity = env->GetArrayLength(storage);
    }
    
	virtual bool write(const void* buffer, size_t size) {
        JNIEnv* env = fEnv;
        jbyteArray storage = fJavaByteArray;
        
        while (size > 0) {
            size_t requested = size;
            if (requested > fCapacity) {
                requested = fCapacity;
            }

            jbyte* array = env->GetByteArrayElements(storage, NULL);
            memcpy(array, buffer, requested);
            env->ReleaseByteArrayElements(storage, array, 0);

            fEnv->CallVoidMethod(fJavaOutputStream, gOutputStream_writeMethodID,
                                 storage, 0, requested);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
                printf("------- write threw an exception\n");
                return false;
            }
            
            buffer = (void*)((char*)buffer + requested);
            size -= requested;
        }
        return true;
    }
    
    virtual void flush() {
        fEnv->CallVoidMethod(fJavaOutputStream, gOutputStream_flushMethodID);
    }
    
private:
    JNIEnv*     fEnv;
    jobject     fJavaOutputStream;  // the caller owns this object
    jbyteArray  fJavaByteArray;     // the caller owns this object
    size_t      fCapacity;
};

SkWStream* CreateJavaOutputStreamAdaptor(JNIEnv* env, jobject stream,
                                         jbyteArray storage) {
    static bool gInited;

    if (!gInited) {
        gOutputStream_Clazz = env->FindClass("java/io/OutputStream");
        RETURN_NULL_IF_NULL(gOutputStream_Clazz);
        gOutputStream_Clazz = (jclass)env->NewGlobalRef(gOutputStream_Clazz);

        gOutputStream_writeMethodID = env->GetMethodID(gOutputStream_Clazz,
                                                       "write", "([BII)V");
        RETURN_NULL_IF_NULL(gOutputStream_writeMethodID);
        gOutputStream_flushMethodID = env->GetMethodID(gOutputStream_Clazz,
                                                       "flush", "()V");
        RETURN_NULL_IF_NULL(gOutputStream_flushMethodID);

        gInited = true;
    }

    return new SkJavaOutputStream(env, stream, storage);
}

