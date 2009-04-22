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
/*
 * Support for -Xcheck:jni (the "careful" version of the JNI interfaces).
 *
 * We want to verify types, make sure class and field IDs are valid, and
 * ensure that JNI's semantic expectations are being met.  JNI seems to
 * be relatively lax when it comes to requirements for permission checks,
 * e.g. access to private methods is generally allowed from anywhere.
 *
 * TODO: keep a counter on global Get/Release.  Report a warning if some Gets
 * were not Released.  Do not count explicit Add/DeleteGlobalRef calls (or
 * count them separately, so we can complain if they exceed a certain
 * threshold).
 *
 * TODO: verify that the methodID passed into the Call functions is for
 * a method in the specified class.
 */
#include "Dalvik.h"
#include "JniInternal.h"

#define JNI_ENTER()     dvmChangeStatus(NULL, THREAD_RUNNING)
#define JNI_EXIT()      dvmChangeStatus(NULL, THREAD_NATIVE)

#define BASE_ENV(_env)  (((JNIEnvExt*)_env)->baseFuncTable)
#define BASE_VM(_vm)    (((JavaVMExt*)_vm)->baseFuncTable)

/*
 * Flags passed into checkThread().
 */
#define kFlag_Default       0x0000

#define kFlag_CritBad       0x0000      /* calling while in critical is bad */
#define kFlag_CritOkay      0x0001      /* ...okay */
#define kFlag_CritGet       0x0002      /* this is a critical "get" */
#define kFlag_CritRelease   0x0003      /* this is a critical "release" */
#define kFlag_CritMask      0x0003      /* bit mask to get "crit" value */

#define kFlag_ExcepBad      0x0000      /* raised exceptions are bad */
#define kFlag_ExcepOkay     0x0004      /* ...okay */

/*
 * Enter/exit macros for JNI env "check" functions.  These do not change
 * the thread state within the VM.
 */
#define CHECK_ENTER(_env, _flags)                                           \
    do {                                                                    \
        JNI_TRACE(true, true);                                              \
        checkThread(_env, _flags, __FUNCTION__);                            \
    } while(false)

#define CHECK_EXIT(_env)                                                    \
    do { JNI_TRACE(false, true); } while(false)


/*
 * Enter/exit macros for JNI invocation interface "check" functions.  These
 * do not change the thread state within the VM.
 *
 * Set "_hasmeth" to true if we have a valid thread with a method pointer.
 * We won't have one before attaching a thread, after detaching a thread, or
 * after destroying the VM.
 */
#define CHECK_VMENTER(_vm, _hasmeth)                                        \
    do { JNI_TRACE(true, _hasmeth); } while(false)
#define CHECK_VMEXIT(_vm, _hasmeth)                                         \
    do { JNI_TRACE(false, _hasmeth); } while(false)

#define CHECK_FIELD_TYPE(_obj, _fieldid, _prim, _isstatic)                  \
    checkFieldType(_obj, _fieldid, _prim, _isstatic, __FUNCTION__)
#define CHECK_CLASS(_env, _clazz)                                           \
    checkClass(_env, _clazz, __FUNCTION__)
#define CHECK_STRING(_env, _str)                                            \
    checkString(_env, _str, __FUNCTION__)
#define CHECK_UTF_STRING(_env, _str, _nullok)                               \
    checkUtfString(_env, _str, _nullok, __FUNCTION__)
#define CHECK_OBJECT(_env, _obj)                                            \
    checkObject(_env, _obj, __FUNCTION__)
#define CHECK_ARRAY(_env, _array)                                           \
    checkArray(_env, _array, __FUNCTION__)
#define CHECK_LENGTH_POSITIVE(_env, _length)                                \
    checkLengthPositive(_env, _length, __FUNCTION__)

#define CHECK_SIG(_env, _methid, _sigbyte, _isstatic)                       \
    checkSig(_env, _methid, _sigbyte, _isstatic, __FUNCTION__)

/*
 * Print trace message when both "checkJNI" and "verbose:jni" are enabled.
 */
#define JNI_TRACE(_entry, _hasmeth)                                         \
    do {                                                                    \
        if (gDvm.verboseJni && (_entry)) {                                  \
            static const char* classDescriptor = "???";                     \
            static const char* methodName = "???";                          \
            if (_hasmeth) {                                                 \
                const Method* meth = dvmGetCurrentJNIMethod();              \
                classDescriptor = meth->clazz->descriptor;                  \
                methodName = meth->name;                                    \
            }                                                               \
            /* use +6 to drop the leading "Check_" */                       \
            LOGI("JNI: %s (from %s.%s)",                                    \
                (__FUNCTION__)+6, classDescriptor, methodName);             \
        }                                                                   \
    } while(false)

/*
 * Log the current location.
 *
 * "func" looks like "Check_DeleteLocalRef"; we drop the "Check_".
 */
static void showLocation(const Method* meth, const char* func)
{
    char* desc = dexProtoCopyMethodDescriptor(&meth->prototype);
    LOGW("             in %s.%s %s (%s)\n",
        meth->clazz->descriptor, meth->name, desc, func + 6);
    free(desc);
}

/*
 * Abort if we are configured to bail out on JNI warnings.
 */
static inline void abortMaybe()
{
    if (gDvm.jniWarnError) {
        dvmDumpThread(dvmThreadSelf(), false);
        dvmAbort();
    }
}

/*
 * Verify that the current thread is (a) attached and (b) associated with
 * this particular instance of JNIEnv.
 *
 * Verify that, if this thread previously made a critical "get" call, we
 * do the corresponding "release" call before we try anything else.
 *
 * Verify that, if an exception has been raised, the native code doesn't
 * make any JNI calls other than the Exception* methods.
 *
 * TODO? if we add support for non-JNI native calls, make sure that the
 * method at the top of the interpreted stack is a JNI method call.  (Or
 * set a flag in the Thread/JNIEnv when the call is made and clear it on
 * return?)
 *
 * NOTE: we are still in THREAD_NATIVE mode.  A GC could happen at any time.
 */
static void checkThread(JNIEnv* env, int flags, const char* func)
{
    JNIEnvExt* threadEnv;
    bool printWarn = false;
    bool printException = false;

    /* get the *correct* JNIEnv by going through our TLS pointer */
    threadEnv = dvmGetJNIEnvForThread();

    /*
     * Verify that the JNIEnv we've been handed matches what we expected
     * to receive.
     */
    if (threadEnv == NULL) {
        LOGE("JNI ERROR: non-VM thread making JNI calls\n");
        // don't set printWarn
    } else if ((JNIEnvExt*) env != threadEnv) {
        if (dvmThreadSelf()->threadId != threadEnv->envThreadId) {
            LOGE("JNI: threadEnv != thread->env?\n");
            dvmAbort();
        }

        LOGW("JNI WARNING: threadid=%d using env from threadid=%d\n",
            threadEnv->envThreadId, ((JNIEnvExt*)env)->envThreadId);
        printWarn = true;

        /* this is a bad idea -- need to throw as we exit, or abort func */
        //dvmThrowException("Ljava/lang/RuntimeException;",
        //    "invalid use of JNI env ptr");
    } else if (((JNIEnvExt*) env)->self != dvmThreadSelf()) {
        /* correct JNIEnv*; make sure the "self" pointer is correct */
        LOGE("JNI: env->self != thread-self\n");
        dvmAbort();
    }

    /*
     * Check for critical resource misuse.
     */
    switch (flags & kFlag_CritMask) {
    case kFlag_CritOkay:    // okay to call this method
        break;
    case kFlag_CritBad:     // not okay to call
        if (threadEnv->critical) {
            LOGW("JNI WARNING: threadid=%d using JNI after critical get\n",
                threadEnv->envThreadId);
            printWarn = true;
        }
        break;
    case kFlag_CritGet:     // this is a "get" call
        /* don't check here; we allow nested gets */
        threadEnv->critical++;
        break;
    case kFlag_CritRelease: // this is a "release" call
        threadEnv->critical--;
        if (threadEnv->critical < 0) {
            LOGW("JNI WARNING: threadid=%d called too many crit releases\n",
                threadEnv->envThreadId);
            printWarn = true;
        }
        break;
    default:
        assert(false);
    }

    /*
     * Check for raised exceptions.
     */
    if ((flags & kFlag_ExcepOkay) == 0 && dvmCheckException(dvmThreadSelf())) {
        LOGW("JNI WARNING: JNI method called with exception raised\n");
        printWarn = true;
        printException = true;
    }

    if (false) {
        Thread* self = dvmThreadSelf();
        LOGW("NOW: %d\n",
            (int) dvmReferenceTableEntries(&self->internalLocalRefTable));
    }

    if (printWarn)
        showLocation(dvmGetCurrentJNIMethod(), func);
    if (printException) {
        LOGW("Pending exception is:\n");
        dvmLogExceptionStackTrace();
    }
    if (printWarn)
        abortMaybe();
}

/*
 * Verify that the field is of the appropriate type.  If the field has an
 * object type, "obj" is the object we're trying to assign into it.
 *
 * Works for both static and instance fields.
 */
static void checkFieldType(jobject obj, jfieldID fieldID, PrimitiveType prim,
    bool isStatic, const char* func)
{
    static const char* primNameList[] = {
        "Object/Array", "boolean", "char", "float", "double",
        "byte", "short", "int", "long", "void"
    };
    const char** primNames = &primNameList[1];      // shift up for PRIM_NOT
    Field* field = (Field*) fieldID;
    bool printWarn = false;

    if (fieldID == NULL) {
        LOGE("JNI ERROR: null field ID\n");
        abortMaybe();
    }

    if (field->signature[0] == 'L' || field->signature[0] == '[') {
        if (obj != NULL) {
            ClassObject* fieldClass =
                dvmFindLoadedClass(field->signature);
            ClassObject* objClass = ((Object*)obj)->clazz;

            assert(fieldClass != NULL);
            assert(objClass != NULL);

            if (!dvmInstanceof(objClass, fieldClass)) {
                LOGW("JNI WARNING: field '%s' with type '%s' set with wrong type (%s)\n",
                    field->name, field->signature, objClass->descriptor);
                printWarn = true;
            }
        }
    } else if (field->signature[0] != PRIM_TYPE_TO_LETTER[prim]) {
        LOGW("JNI WARNING: field '%s' with type '%s' set with wrong type (%s)\n",
            field->name, field->signature, primNames[prim]);
        printWarn = true;
    } else if (isStatic && !dvmIsStaticField(field)) {
        if (isStatic)
            LOGW("JNI WARNING: accessing non-static field %s as static\n",
                field->name);
        else
            LOGW("JNI WARNING: accessing static field %s as non-static\n",
                field->name);
        printWarn = true;
    }

    if (printWarn) {
        showLocation(dvmGetCurrentJNIMethod(), func);
        abortMaybe();
    }
}

/*
 * Verify that "obj" is a valid object, and that it's an object that JNI
 * is allowed to know about.  We allow NULL references.
 *
 * The caller should have switched to "running" mode before calling here.
 */
static void checkObject(JNIEnv* env, jobject obj, const char* func)
{
    UNUSED_PARAMETER(env);
    bool printWarn = false;

    if (obj == NULL)
        return;
    if (!dvmIsValidObject(obj)) {
        LOGW("JNI WARNING: native code passing in bad object %p (%s)\n",
            obj, func);
        printWarn = true;
    } else if (dvmGetJNIRefType(obj) == JNIInvalidRefType) {
        LOGW("JNI WARNING: ref %p should not be visible to native code\n", obj);
        printWarn = true;
    }

    if (printWarn) {
        showLocation(dvmGetCurrentJNIMethod(), func);
        abortMaybe();
    }
}

/*
 * Verify that "clazz" actually points to a class object.  (Also performs
 * checkObject.)
 *
 * We probably don't need to identify where we're being called from,
 * because the VM is most likely about to crash and leave a core dump
 * if something is wrong.
 *
 * Because we're looking at an object on the GC heap, we have to switch
 * to "running" mode before doing the checks.
 */
static void checkClass(JNIEnv* env, jclass jclazz, const char* func)
{
    JNI_ENTER();
    bool printWarn = false;

    ClassObject* clazz = (ClassObject*) jclazz;

    if (clazz == NULL) {
        LOGW("JNI WARNING: received null jclass\n");
        printWarn = true;
    } else if (!dvmIsValidObject((Object*) clazz)) {
        LOGW("JNI WARNING: jclass points to invalid object %p\n", clazz);
        printWarn = true;
    } else if (clazz->obj.clazz != gDvm.classJavaLangClass) {
        LOGW("JNI WARNING: jclass does not point to class object (%p - %s)\n",
            jclazz, clazz->descriptor);
        printWarn = true;
    } else {
        checkObject(env, jclazz, func);
    }

    if (printWarn)
        abortMaybe();

    JNI_EXIT();
}

/*
 * Verify that "str" is non-NULL and points to a String object.
 *
 * Since we're dealing with objects, switch to "running" mode.
 */
static void checkString(JNIEnv* env, jstring str, const char* func)
{
    JNI_ENTER();
    bool printWarn = false;

    Object* obj = (Object*) str;

    if (obj == NULL) {
        LOGW("JNI WARNING: received null jstring (%s)\n", func);
        printWarn = true;
    } else if (obj->clazz != gDvm.classJavaLangString) {
        if (dvmIsValidObject(obj))
            LOGW("JNI WARNING: jstring points to non-string object\n");
        else
            LOGW("JNI WARNING: jstring is bogus (%p)\n", str);
        printWarn = true;
    } else {
        checkObject(env, str, func);
    }

    if (printWarn)
        abortMaybe();

    JNI_EXIT();
}

/*
 * Verify that "bytes" points to valid "modified UTF-8" data.
 */
static void checkUtfString(JNIEnv* env, const char* bytes, bool nullOk,
    const char* func)
{
    const char* origBytes = bytes;

    if (bytes == NULL) {
        if (!nullOk) {
            LOGW("JNI WARNING: unexpectedly null UTF string\n");
            goto fail;
        }

        return;
    }

    while (*bytes != '\0') {
        u1 utf8 = *(bytes++);
        // Switch on the high four bits.
        switch (utf8 >> 4) {
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x04:
            case 0x05:
            case 0x06:
            case 0x07: {
                // Bit pattern 0xxx. No need for any extra bytes.
                break;
            }
            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0f: {
                /*
                 * Bit pattern 10xx or 1111, which are illegal start bytes.
                 * Note: 1111 is valid for normal UTF-8, but not the
                 * modified UTF-8 used here.
                 */
                LOGW("JNI WARNING: illegal start byte 0x%x\n", utf8);
                goto fail;
            }
            case 0x0e: {
                // Bit pattern 1110, so there are two additional bytes.
                utf8 = *(bytes++);
                if ((utf8 & 0xc0) != 0x80) {
                    LOGW("JNI WARNING: illegal continuation byte 0x%x\n", utf8);
                    goto fail;
                }
                // Fall through to take care of the final byte.
            }
            case 0x0c:
            case 0x0d: {
                // Bit pattern 110x, so there is one additional byte.
                utf8 = *(bytes++);
                if ((utf8 & 0xc0) != 0x80) {
                    LOGW("JNI WARNING: illegal continuation byte 0x%x\n", utf8);
                    goto fail;
                }
                break;
            }
        }
    }

    return;

fail:
    LOGW("             string: '%s'\n", origBytes);
    showLocation(dvmGetCurrentJNIMethod(), func);
    abortMaybe();
}

/*
 * Verify that "array" is non-NULL and points to an Array object.
 *
 * Since we're dealing with objects, switch to "running" mode.
 */
static void checkArray(JNIEnv* env, jarray array, const char* func)
{
    JNI_ENTER();
    bool printWarn = false;

    Object* obj = (Object*) array;

    if (obj == NULL) {
        LOGW("JNI WARNING: received null array (%s)\n", func);
        printWarn = true;
    } else if (obj->clazz->descriptor[0] != '[') {
        if (dvmIsValidObject(obj))
            LOGW("JNI WARNING: jarray points to non-array object\n");
        else
            LOGW("JNI WARNING: jarray is bogus (%p)\n", array);
        printWarn = true;
    } else {
        checkObject(env, array, func);
    }

    if (printWarn)
        abortMaybe();

    JNI_EXIT();
}

/*
 * Verify that the length argument to array-creation calls is >= 0.
 */
static void checkLengthPositive(JNIEnv* env, jsize length, const char* func)
{
    if (length < 0) {
        LOGW("JNI WARNING: negative length for array allocation (%s)\n", func);
        abortMaybe();
    }
}

/*
 * Verify that the method's return type matches the type of call.
 *
 * "expectedSigByte" will be 'L' for all objects, including arrays.
 */
static void checkSig(JNIEnv* env, jmethodID methodID, char expectedSigByte,
    bool isStatic, const char* func)
{
    const Method* meth = (const Method*) methodID;
    bool printWarn = false;

    if (expectedSigByte != meth->shorty[0]) {
        LOGW("JNI WARNING: expected return type '%c'\n", expectedSigByte);
        printWarn = true;
    } else if (isStatic && !dvmIsStaticMethod(meth)) {
        if (isStatic)
            LOGW("JNI WARNING: calling non-static method with static call\n");
        else
            LOGW("JNI WARNING: calling static method with non-static call\n");
        printWarn = true;
    }

    if (printWarn) {
        char* desc = dexProtoCopyMethodDescriptor(&meth->prototype);
        LOGW("             calling %s.%s %s\n",
            meth->clazz->descriptor, meth->name, desc);
        free(desc);
        showLocation(dvmGetCurrentJNIMethod(), func);
        abortMaybe();
    }
}

/*
 * Verify that this static field ID is valid for this class.
 */
static void checkStaticFieldID(JNIEnv* env, jclass clazz, jfieldID fieldID)
{
    StaticField* base = ((ClassObject*) clazz)->sfields;
    int fieldCount = ((ClassObject*) clazz)->sfieldCount;

    if ((StaticField*) fieldID < base ||
        (StaticField*) fieldID >= base + fieldCount)
    {
        LOGW("JNI WARNING: static fieldID %p not valid for class %s\n",
            fieldID, ((ClassObject*) clazz)->descriptor);
        LOGW("             base=%p count=%d\n", base, fieldCount);
        abortMaybe();
    }
}

/*
 * Verify that this instance field ID is valid for this object.
 */
static void checkInstanceFieldID(JNIEnv* env, jobject obj, jfieldID fieldID)
{
    ClassObject* clazz = ((Object*)obj)->clazz;

    /*
     * Check this class and all of its superclasses for a matching field.
     * Don't need to scan interfaces.
     */
    while (clazz != NULL) {
        if ((InstField*) fieldID >= clazz->ifields &&
            (InstField*) fieldID < clazz->ifields + clazz->ifieldCount)
        {
            return;
        }

        clazz = clazz->super;
    }

    LOGW("JNI WARNING: inst fieldID %p not valid for class %s\n",
        fieldID, ((Object*)obj)->clazz->descriptor);
    abortMaybe();
}


/*
 * ===========================================================================
 *      JNI functions
 * ===========================================================================
 */

static jint Check_GetVersion(JNIEnv* env)
{
    CHECK_ENTER(env, kFlag_Default);
    jint result;
    result = BASE_ENV(env)->GetVersion(env);
    CHECK_EXIT(env);
    return result;
}

static jclass Check_DefineClass(JNIEnv* env, const char* name, jobject loader,
    const jbyte* buf, jsize bufLen)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, loader);
    CHECK_UTF_STRING(env, name, false);
    jclass result;
    result = BASE_ENV(env)->DefineClass(env, name, loader, buf, bufLen);
    CHECK_EXIT(env);
    return result;
}

static jclass Check_FindClass(JNIEnv* env, const char* name)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_UTF_STRING(env, name, false);
    jclass result;
    result = BASE_ENV(env)->FindClass(env, name);
    CHECK_EXIT(env);
    return result;
}

static jclass Check_GetSuperclass(JNIEnv* env, jclass clazz)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jclass result;
    result = BASE_ENV(env)->GetSuperclass(env, clazz);
    CHECK_EXIT(env);
    return result;
}

static jboolean Check_IsAssignableFrom(JNIEnv* env, jclass clazz1,
    jclass clazz2)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz1);
    CHECK_CLASS(env, clazz2);
    jboolean result;
    result = BASE_ENV(env)->IsAssignableFrom(env, clazz1, clazz2);
    CHECK_EXIT(env);
    return result;
}

static jmethodID Check_FromReflectedMethod(JNIEnv* env, jobject method)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, method);
    jmethodID result;
    result = BASE_ENV(env)->FromReflectedMethod(env, method);
    CHECK_EXIT(env);
    return result;
}

static jfieldID Check_FromReflectedField(JNIEnv* env, jobject field)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, field);
    jfieldID result;
    result = BASE_ENV(env)->FromReflectedField(env, field);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_ToReflectedMethod(JNIEnv* env, jclass cls,
    jmethodID methodID, jboolean isStatic)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, cls);
    jobject result;
    result = BASE_ENV(env)->ToReflectedMethod(env, cls, methodID, isStatic);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_ToReflectedField(JNIEnv* env, jclass cls, jfieldID fieldID,
    jboolean isStatic)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, cls);
    jobject result;
    result = BASE_ENV(env)->ToReflectedField(env, cls, fieldID, isStatic);
    CHECK_EXIT(env);
    return result;
}

static jint Check_Throw(JNIEnv* env, jthrowable obj)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    jint result;
    result = BASE_ENV(env)->Throw(env, obj);
    CHECK_EXIT(env);
    return result;
}

static jint Check_ThrowNew(JNIEnv* env, jclass clazz, const char* message)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    CHECK_UTF_STRING(env, message, true);
    jint result;
    result = BASE_ENV(env)->ThrowNew(env, clazz, message);
    CHECK_EXIT(env);
    return result;
}

static jthrowable Check_ExceptionOccurred(JNIEnv* env)
{
    CHECK_ENTER(env, kFlag_ExcepOkay);
    jthrowable result;
    result = BASE_ENV(env)->ExceptionOccurred(env);
    CHECK_EXIT(env);
    return result;
}

static void Check_ExceptionDescribe(JNIEnv* env)
{
    CHECK_ENTER(env, kFlag_ExcepOkay);
    BASE_ENV(env)->ExceptionDescribe(env);
    CHECK_EXIT(env);
}

static void Check_ExceptionClear(JNIEnv* env)
{
    CHECK_ENTER(env, kFlag_ExcepOkay);
    BASE_ENV(env)->ExceptionClear(env);
    CHECK_EXIT(env);
}

static void Check_FatalError(JNIEnv* env, const char* msg)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_UTF_STRING(env, msg, true);
    BASE_ENV(env)->FatalError(env, msg);
    CHECK_EXIT(env);
}

static jint Check_PushLocalFrame(JNIEnv* env, jint capacity)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    jint result;
    result = BASE_ENV(env)->PushLocalFrame(env, capacity);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_PopLocalFrame(JNIEnv* env, jobject res)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    CHECK_OBJECT(env, res);
    jobject result;
    result = BASE_ENV(env)->PopLocalFrame(env, res);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_NewGlobalRef(JNIEnv* env, jobject obj)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    jobject result;
    result = BASE_ENV(env)->NewGlobalRef(env, obj);
    CHECK_EXIT(env);
    return result;
}

static void Check_DeleteGlobalRef(JNIEnv* env, jobject localRef)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    CHECK_OBJECT(env, localRef);
    BASE_ENV(env)->DeleteGlobalRef(env, localRef);
    CHECK_EXIT(env);
}

static jobject Check_NewLocalRef(JNIEnv* env, jobject ref)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, ref);
    jobject result;
    result = BASE_ENV(env)->NewLocalRef(env, ref);
    CHECK_EXIT(env);
    return result;
}

static void Check_DeleteLocalRef(JNIEnv* env, jobject globalRef)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    CHECK_OBJECT(env, globalRef);
    BASE_ENV(env)->DeleteLocalRef(env, globalRef);
    CHECK_EXIT(env);
}

static jint Check_EnsureLocalCapacity(JNIEnv *env, jint capacity)
{
    CHECK_ENTER(env, kFlag_Default);
    jint result;
    result = BASE_ENV(env)->EnsureLocalCapacity(env, capacity);
    CHECK_EXIT(env);
    return result;
}

static jboolean Check_IsSameObject(JNIEnv* env, jobject ref1, jobject ref2)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, ref1);
    CHECK_OBJECT(env, ref2);
    jboolean result;
    result = BASE_ENV(env)->IsSameObject(env, ref1, ref2);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_AllocObject(JNIEnv* env, jclass clazz)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jobject result;
    result = BASE_ENV(env)->AllocObject(env, clazz);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_NewObject(JNIEnv* env, jclass clazz, jmethodID methodID,
    ...)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jobject result;
    va_list args;

    va_start(args, methodID);
    result = BASE_ENV(env)->NewObjectV(env, clazz, methodID, args);
    va_end(args);

    CHECK_EXIT(env);
    return result;
}
static jobject Check_NewObjectV(JNIEnv* env, jclass clazz, jmethodID methodID,
    va_list args)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jobject result;
    result = BASE_ENV(env)->NewObjectV(env, clazz, methodID, args);
    CHECK_EXIT(env);
    return result;
}
static jobject Check_NewObjectA(JNIEnv* env, jclass clazz, jmethodID methodID,
    jvalue* args)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jobject result;
    result = BASE_ENV(env)->NewObjectA(env, clazz, methodID, args);
    CHECK_EXIT(env);
    return result;
}

static jclass Check_GetObjectClass(JNIEnv* env, jobject obj)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    jclass result;
    result = BASE_ENV(env)->GetObjectClass(env, obj);
    CHECK_EXIT(env);
    return result;
}

static jboolean Check_IsInstanceOf(JNIEnv* env, jobject obj, jclass clazz)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    CHECK_CLASS(env, clazz);
    jboolean result;
    result = BASE_ENV(env)->IsInstanceOf(env, obj, clazz);
    CHECK_EXIT(env);
    return result;
}

static jmethodID Check_GetMethodID(JNIEnv* env, jclass clazz, const char* name,
    const char* sig)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    CHECK_UTF_STRING(env, name, false);
    CHECK_UTF_STRING(env, sig, false);
    jmethodID result;
    result = BASE_ENV(env)->GetMethodID(env, clazz, name, sig);
    CHECK_EXIT(env);
    return result;
}

static jfieldID Check_GetFieldID(JNIEnv* env, jclass clazz,
    const char* name, const char* sig)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    CHECK_UTF_STRING(env, name, false);
    CHECK_UTF_STRING(env, sig, false);
    jfieldID result;
    result = BASE_ENV(env)->GetFieldID(env, clazz, name, sig);
    CHECK_EXIT(env);
    return result;
}

static jmethodID Check_GetStaticMethodID(JNIEnv* env, jclass clazz,
    const char* name, const char* sig)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    CHECK_UTF_STRING(env, name, false);
    CHECK_UTF_STRING(env, sig, false);
    jmethodID result;
    result = BASE_ENV(env)->GetStaticMethodID(env, clazz, name, sig);
    CHECK_EXIT(env);
    return result;
}

static jfieldID Check_GetStaticFieldID(JNIEnv* env, jclass clazz,
    const char* name, const char* sig)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    CHECK_UTF_STRING(env, name, false);
    CHECK_UTF_STRING(env, sig, false);
    jfieldID result;
    result = BASE_ENV(env)->GetStaticFieldID(env, clazz, name, sig);
    CHECK_EXIT(env);
    return result;
}

#define GET_STATIC_TYPE_FIELD(_ctype, _jname, _isref)                       \
    static _ctype Check_GetStatic##_jname##Field(JNIEnv* env, jclass clazz, \
        jfieldID fieldID)                                                   \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        _ctype result;                                                      \
        checkStaticFieldID(env, clazz, fieldID);                            \
        result = BASE_ENV(env)->GetStatic##_jname##Field(env, clazz,        \
            fieldID);                                                       \
        CHECK_EXIT(env);                                                    \
        return result;                                                      \
    }
GET_STATIC_TYPE_FIELD(jobject, Object, true);
GET_STATIC_TYPE_FIELD(jboolean, Boolean, false);
GET_STATIC_TYPE_FIELD(jbyte, Byte, false);
GET_STATIC_TYPE_FIELD(jchar, Char, false);
GET_STATIC_TYPE_FIELD(jshort, Short, false);
GET_STATIC_TYPE_FIELD(jint, Int, false);
GET_STATIC_TYPE_FIELD(jlong, Long, false);
GET_STATIC_TYPE_FIELD(jfloat, Float, false);
GET_STATIC_TYPE_FIELD(jdouble, Double, false);

#define SET_STATIC_TYPE_FIELD(_ctype, _jname, _ftype)                       \
    static void Check_SetStatic##_jname##Field(JNIEnv* env, jclass clazz,   \
        jfieldID fieldID, _ctype value)                                     \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        checkStaticFieldID(env, clazz, fieldID);                            \
        CHECK_FIELD_TYPE((jobject)(u4)value, fieldID, _ftype, true);        \
        BASE_ENV(env)->SetStatic##_jname##Field(env, clazz, fieldID,        \
            value);                                                         \
        CHECK_EXIT(env);                                                    \
    }
SET_STATIC_TYPE_FIELD(jobject, Object, PRIM_NOT);
SET_STATIC_TYPE_FIELD(jboolean, Boolean, PRIM_BOOLEAN);
SET_STATIC_TYPE_FIELD(jbyte, Byte, PRIM_BYTE);
SET_STATIC_TYPE_FIELD(jchar, Char, PRIM_CHAR);
SET_STATIC_TYPE_FIELD(jshort, Short, PRIM_SHORT);
SET_STATIC_TYPE_FIELD(jint, Int, PRIM_INT);
SET_STATIC_TYPE_FIELD(jlong, Long, PRIM_LONG);
SET_STATIC_TYPE_FIELD(jfloat, Float, PRIM_FLOAT);
SET_STATIC_TYPE_FIELD(jdouble, Double, PRIM_DOUBLE);

#define GET_TYPE_FIELD(_ctype, _jname, _isref)                              \
    static _ctype Check_Get##_jname##Field(JNIEnv* env, jobject obj,        \
        jfieldID fieldID)                                                   \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_OBJECT(env, obj);                                             \
        _ctype result;                                                      \
        checkInstanceFieldID(env, obj, fieldID);                            \
        result = BASE_ENV(env)->Get##_jname##Field(env, obj, fieldID);      \
        CHECK_EXIT(env);                                                    \
        return result;                                                      \
    }
GET_TYPE_FIELD(jobject, Object, true);
GET_TYPE_FIELD(jboolean, Boolean, false);
GET_TYPE_FIELD(jbyte, Byte, false);
GET_TYPE_FIELD(jchar, Char, false);
GET_TYPE_FIELD(jshort, Short, false);
GET_TYPE_FIELD(jint, Int, false);
GET_TYPE_FIELD(jlong, Long, false);
GET_TYPE_FIELD(jfloat, Float, false);
GET_TYPE_FIELD(jdouble, Double, false);

#define SET_TYPE_FIELD(_ctype, _jname, _ftype)                              \
    static void Check_Set##_jname##Field(JNIEnv* env, jobject obj,          \
        jfieldID fieldID, _ctype value)                                     \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_OBJECT(env, obj);                                             \
        checkInstanceFieldID(env, obj, fieldID);                            \
        CHECK_FIELD_TYPE((jobject)(u4) value, fieldID, _ftype, false);      \
        BASE_ENV(env)->Set##_jname##Field(env, obj, fieldID, value);        \
        CHECK_EXIT(env);                                                    \
    }
SET_TYPE_FIELD(jobject, Object, PRIM_NOT);
SET_TYPE_FIELD(jboolean, Boolean, PRIM_BOOLEAN);
SET_TYPE_FIELD(jbyte, Byte, PRIM_BYTE);
SET_TYPE_FIELD(jchar, Char, PRIM_CHAR);
SET_TYPE_FIELD(jshort, Short, PRIM_SHORT);
SET_TYPE_FIELD(jint, Int, PRIM_INT);
SET_TYPE_FIELD(jlong, Long, PRIM_LONG);
SET_TYPE_FIELD(jfloat, Float, PRIM_FLOAT);
SET_TYPE_FIELD(jdouble, Double, PRIM_DOUBLE);

#define CALL_VIRTUAL(_ctype, _jname, _retfail, _retdecl, _retasgn, _retok,  \
        _retsig)                                                            \
    static _ctype Check_Call##_jname##Method(JNIEnv* env, jobject obj,      \
        jmethodID methodID, ...)                                            \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_OBJECT(env, obj);                                             \
        CHECK_SIG(env, methodID, _retsig, false);                           \
        _retdecl;                                                           \
        va_list args;                                                       \
        va_start(args, methodID);                                           \
        _retasgn BASE_ENV(env)->Call##_jname##MethodV(env, obj, methodID,   \
            args);                                                          \
        va_end(args);                                                       \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }                                                                       \
    static _ctype Check_Call##_jname##MethodV(JNIEnv* env, jobject obj,     \
        jmethodID methodID, va_list args)                                   \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_OBJECT(env, obj);                                             \
        CHECK_SIG(env, methodID, _retsig, false);                           \
        _retdecl;                                                           \
        _retasgn BASE_ENV(env)->Call##_jname##MethodV(env, obj, methodID,   \
            args);                                                          \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }                                                                       \
    static _ctype Check_Call##_jname##MethodA(JNIEnv* env, jobject obj,     \
        jmethodID methodID, jvalue* args)                                   \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_OBJECT(env, obj);                                             \
        CHECK_SIG(env, methodID, _retsig, false);                           \
        _retdecl;                                                           \
        _retasgn BASE_ENV(env)->Call##_jname##MethodA(env, obj, methodID,   \
            args);                                                          \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }
CALL_VIRTUAL(jobject, Object, NULL, Object* result, result=, result, 'L');
CALL_VIRTUAL(jboolean, Boolean, 0, jboolean result, result=, result, 'Z');
CALL_VIRTUAL(jbyte, Byte, 0, jbyte result, result=, result, 'B');
CALL_VIRTUAL(jchar, Char, 0, jchar result, result=, result, 'C');
CALL_VIRTUAL(jshort, Short, 0, jshort result, result=, result, 'S');
CALL_VIRTUAL(jint, Int, 0, jint result, result=, result, 'I');
CALL_VIRTUAL(jlong, Long, 0, jlong result, result=, result, 'J');
CALL_VIRTUAL(jfloat, Float, 0.0f, jfloat result, result=, *(float*)&result, 'F');
CALL_VIRTUAL(jdouble, Double, 0.0, jdouble result, result=, *(double*)&result, 'D');
CALL_VIRTUAL(void, Void, , , , , 'V');

#define CALL_NONVIRTUAL(_ctype, _jname, _retfail, _retdecl, _retasgn,       \
        _retok, _retsig)                                                    \
    static _ctype Check_CallNonvirtual##_jname##Method(JNIEnv* env,         \
        jobject obj, jclass clazz, jmethodID methodID, ...)                 \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        CHECK_OBJECT(env, obj);                                             \
        CHECK_SIG(env, methodID, _retsig, false);                           \
        _retdecl;                                                           \
        va_list args;                                                       \
        va_start(args, methodID);                                           \
        _retasgn BASE_ENV(env)->CallNonvirtual##_jname##MethodV(env, obj,   \
            clazz, methodID, args);                                         \
        va_end(args);                                                       \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }                                                                       \
    static _ctype Check_CallNonvirtual##_jname##MethodV(JNIEnv* env,        \
        jobject obj, jclass clazz, jmethodID methodID, va_list args)        \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        CHECK_OBJECT(env, obj);                                             \
        CHECK_SIG(env, methodID, _retsig, false);                           \
        _retdecl;                                                           \
        _retasgn BASE_ENV(env)->CallNonvirtual##_jname##MethodV(env, obj,   \
            clazz, methodID, args);                                         \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }                                                                       \
    static _ctype Check_CallNonvirtual##_jname##MethodA(JNIEnv* env,        \
        jobject obj, jclass clazz, jmethodID methodID, jvalue* args)        \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        CHECK_OBJECT(env, obj);                                             \
        CHECK_SIG(env, methodID, _retsig, false);                           \
        _retdecl;                                                           \
        _retasgn BASE_ENV(env)->CallNonvirtual##_jname##MethodA(env, obj,   \
            clazz, methodID, args);                                         \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }
CALL_NONVIRTUAL(jobject, Object, NULL, Object* result, result=, (Object*)(u4)result, 'L');
CALL_NONVIRTUAL(jboolean, Boolean, 0, jboolean result, result=, result, 'Z');
CALL_NONVIRTUAL(jbyte, Byte, 0, jbyte result, result=, result, 'B');
CALL_NONVIRTUAL(jchar, Char, 0, jchar result, result=, result, 'C');
CALL_NONVIRTUAL(jshort, Short, 0, jshort result, result=, result, 'S');
CALL_NONVIRTUAL(jint, Int, 0, jint result, result=, result, 'I');
CALL_NONVIRTUAL(jlong, Long, 0, jlong result, result=, result, 'J');
CALL_NONVIRTUAL(jfloat, Float, 0.0f, jfloat result, result=, *(float*)&result, 'F');
CALL_NONVIRTUAL(jdouble, Double, 0.0, jdouble result, result=, *(double*)&result, 'D');
CALL_NONVIRTUAL(void, Void, , , , , 'V');


#define CALL_STATIC(_ctype, _jname, _retfail, _retdecl, _retasgn, _retok,   \
        _retsig)                                                            \
    static _ctype Check_CallStatic##_jname##Method(JNIEnv* env,             \
        jclass clazz, jmethodID methodID, ...)                              \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        CHECK_SIG(env, methodID, _retsig, true);                            \
        _retdecl;                                                           \
        va_list args;                                                       \
        va_start(args, methodID);                                           \
        _retasgn BASE_ENV(env)->CallStatic##_jname##MethodV(env, clazz,     \
            methodID, args);                                                \
        va_end(args);                                                       \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }                                                                       \
    static _ctype Check_CallStatic##_jname##MethodV(JNIEnv* env,            \
        jclass clazz, jmethodID methodID, va_list args)                     \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        CHECK_SIG(env, methodID, _retsig, true);                            \
        _retdecl;                                                           \
        _retasgn BASE_ENV(env)->CallStatic##_jname##MethodV(env, clazz,     \
            methodID, args);                                                \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }                                                                       \
    static _ctype Check_CallStatic##_jname##MethodA(JNIEnv* env,            \
        jclass clazz, jmethodID methodID, jvalue* args)                     \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_CLASS(env, clazz);                                            \
        CHECK_SIG(env, methodID, _retsig, true);                            \
        _retdecl;                                                           \
        _retasgn BASE_ENV(env)->CallStatic##_jname##MethodA(env, clazz,     \
            methodID, args);                                                \
        CHECK_EXIT(env);                                                    \
        return _retok;                                                      \
    }
CALL_STATIC(jobject, Object, NULL, Object* result, result=, (Object*)(u4)result, 'L');
CALL_STATIC(jboolean, Boolean, 0, jboolean result, result=, result, 'Z');
CALL_STATIC(jbyte, Byte, 0, jbyte result, result=, result, 'B');
CALL_STATIC(jchar, Char, 0, jchar result, result=, result, 'C');
CALL_STATIC(jshort, Short, 0, jshort result, result=, result, 'S');
CALL_STATIC(jint, Int, 0, jint result, result=, result, 'I');
CALL_STATIC(jlong, Long, 0, jlong result, result=, result, 'J');
CALL_STATIC(jfloat, Float, 0.0f, jfloat result, result=, *(float*)&result, 'F');
CALL_STATIC(jdouble, Double, 0.0, jdouble result, result=, *(double*)&result, 'D');
CALL_STATIC(void, Void, , , , , 'V');

static jstring Check_NewString(JNIEnv* env, const jchar* unicodeChars,
    jsize len)
{
    CHECK_ENTER(env, kFlag_Default);
    jstring result;
    result = BASE_ENV(env)->NewString(env, unicodeChars, len);
    CHECK_EXIT(env);
    return result;
}

static jsize Check_GetStringLength(JNIEnv* env, jstring string)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_STRING(env, string);
    jsize result;
    result = BASE_ENV(env)->GetStringLength(env, string);
    CHECK_EXIT(env);
    return result;
}

static const jchar* Check_GetStringChars(JNIEnv* env, jstring string,
    jboolean* isCopy)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_STRING(env, string);
    const jchar* result;
    result = BASE_ENV(env)->GetStringChars(env, string, isCopy);
    CHECK_EXIT(env);
    return result;
}

static void Check_ReleaseStringChars(JNIEnv* env, jstring string,
    const jchar* chars)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    CHECK_STRING(env, string);
    BASE_ENV(env)->ReleaseStringChars(env, string, chars);
    CHECK_EXIT(env);
}

static jstring Check_NewStringUTF(JNIEnv* env, const char* bytes)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_UTF_STRING(env, bytes, true);
    jstring result;
    result = BASE_ENV(env)->NewStringUTF(env, bytes);
    CHECK_EXIT(env);
    return result;
}

static jsize Check_GetStringUTFLength(JNIEnv* env, jstring string)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_STRING(env, string);
    jsize result;
    result = BASE_ENV(env)->GetStringUTFLength(env, string);
    CHECK_EXIT(env);
    return result;
}

static const char* Check_GetStringUTFChars(JNIEnv* env, jstring string,
    jboolean* isCopy)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_STRING(env, string);
    const char* result;
    result = BASE_ENV(env)->GetStringUTFChars(env, string, isCopy);
    CHECK_EXIT(env);
    return result;
}

static void Check_ReleaseStringUTFChars(JNIEnv* env, jstring string,
    const char* utf)
{
    CHECK_ENTER(env, kFlag_ExcepOkay);
    CHECK_STRING(env, string);
    BASE_ENV(env)->ReleaseStringUTFChars(env, string, utf);
    CHECK_EXIT(env);
}

static jsize Check_GetArrayLength(JNIEnv* env, jarray array)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_ARRAY(env, array);
    jsize result;
    result = BASE_ENV(env)->GetArrayLength(env, array);
    CHECK_EXIT(env);
    return result;
}

static jobjectArray Check_NewObjectArray(JNIEnv* env, jsize length,
    jclass elementClass, jobject initialElement)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, elementClass);
    CHECK_OBJECT(env, initialElement);
    CHECK_LENGTH_POSITIVE(env, length);
    jobjectArray result;
    result = BASE_ENV(env)->NewObjectArray(env, length, elementClass,
                                            initialElement);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_GetObjectArrayElement(JNIEnv* env, jobjectArray array,
    jsize index)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_ARRAY(env, array);
    jobject result;
    result = BASE_ENV(env)->GetObjectArrayElement(env, array, index);
    CHECK_EXIT(env);
    return result;
}

static void Check_SetObjectArrayElement(JNIEnv* env, jobjectArray array,
    jsize index, jobject value)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_ARRAY(env, array);
    BASE_ENV(env)->SetObjectArrayElement(env, array, index, value);
    CHECK_EXIT(env);
}

#define NEW_PRIMITIVE_ARRAY(_artype, _jname, _typechar)                     \
    static _artype Check_New##_jname##Array(JNIEnv* env, jsize length)      \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_LENGTH_POSITIVE(env, length);                                 \
        _artype result;                                                     \
        result = BASE_ENV(env)->New##_jname##Array(env, length);            \
        CHECK_EXIT(env);                                                    \
        return result;                                                      \
    }
NEW_PRIMITIVE_ARRAY(jbooleanArray, Boolean, 'Z');
NEW_PRIMITIVE_ARRAY(jbyteArray, Byte, 'B');
NEW_PRIMITIVE_ARRAY(jcharArray, Char, 'C');
NEW_PRIMITIVE_ARRAY(jshortArray, Short, 'S');
NEW_PRIMITIVE_ARRAY(jintArray, Int, 'I');
NEW_PRIMITIVE_ARRAY(jlongArray, Long, 'J');
NEW_PRIMITIVE_ARRAY(jfloatArray, Float, 'F');
NEW_PRIMITIVE_ARRAY(jdoubleArray, Double, 'D');

#define GET_PRIMITIVE_ARRAY_ELEMENTS(_ctype, _jname)                        \
    static _ctype* Check_Get##_jname##ArrayElements(JNIEnv* env,            \
        _ctype##Array array, jboolean* isCopy)                              \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_ARRAY(env, array);                                            \
        _ctype* result;                                                     \
        result = BASE_ENV(env)->Get##_jname##ArrayElements(env,             \
            array, isCopy);                                                 \
        CHECK_EXIT(env);                                                    \
        return result;                                                      \
    }

#define RELEASE_PRIMITIVE_ARRAY_ELEMENTS(_ctype, _jname)                    \
    static void Check_Release##_jname##ArrayElements(JNIEnv* env,           \
        _ctype##Array array, _ctype* elems, jint mode)                      \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);                  \
        CHECK_ARRAY(env, array);                                            \
        BASE_ENV(env)->Release##_jname##ArrayElements(env,                  \
            array, elems, mode);                                            \
        CHECK_EXIT(env);                                                    \
    }

#define GET_PRIMITIVE_ARRAY_REGION(_ctype, _jname)                          \
    static void Check_Get##_jname##ArrayRegion(JNIEnv* env,                 \
        _ctype##Array array, jsize start, jsize len, _ctype* buf)           \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_ARRAY(env, array);                                            \
        BASE_ENV(env)->Get##_jname##ArrayRegion(env, array, start,          \
            len, buf);                                                      \
        CHECK_EXIT(env);                                                    \
    }

#define SET_PRIMITIVE_ARRAY_REGION(_ctype, _jname)                          \
    static void Check_Set##_jname##ArrayRegion(JNIEnv* env,                 \
        _ctype##Array array, jsize start, jsize len, const _ctype* buf)     \
    {                                                                       \
        CHECK_ENTER(env, kFlag_Default);                                    \
        CHECK_ARRAY(env, array);                                            \
        BASE_ENV(env)->Set##_jname##ArrayRegion(env, array, start,          \
            len, buf);                                                      \
        CHECK_EXIT(env);                                                    \
    }

#define PRIMITIVE_ARRAY_FUNCTIONS(_ctype, _jname)                           \
    GET_PRIMITIVE_ARRAY_ELEMENTS(_ctype, _jname);                           \
    RELEASE_PRIMITIVE_ARRAY_ELEMENTS(_ctype, _jname);                       \
    GET_PRIMITIVE_ARRAY_REGION(_ctype, _jname);                             \
    SET_PRIMITIVE_ARRAY_REGION(_ctype, _jname);

PRIMITIVE_ARRAY_FUNCTIONS(jboolean, Boolean);
PRIMITIVE_ARRAY_FUNCTIONS(jbyte, Byte);
PRIMITIVE_ARRAY_FUNCTIONS(jchar, Char);
PRIMITIVE_ARRAY_FUNCTIONS(jshort, Short);
PRIMITIVE_ARRAY_FUNCTIONS(jint, Int);
PRIMITIVE_ARRAY_FUNCTIONS(jlong, Long);
PRIMITIVE_ARRAY_FUNCTIONS(jfloat, Float);
PRIMITIVE_ARRAY_FUNCTIONS(jdouble, Double);

static jint Check_RegisterNatives(JNIEnv* env, jclass clazz,
    const JNINativeMethod* methods, jint nMethods)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jint result;
    result = BASE_ENV(env)->RegisterNatives(env, clazz, methods, nMethods);
    CHECK_EXIT(env);
    return result;
}

static jint Check_UnregisterNatives(JNIEnv* env, jclass clazz)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_CLASS(env, clazz);
    jint result;
    result = BASE_ENV(env)->UnregisterNatives(env, clazz);
    CHECK_EXIT(env);
    return result;
}

static jint Check_MonitorEnter(JNIEnv* env, jobject obj)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    jint result;
    result = BASE_ENV(env)->MonitorEnter(env, obj);
    CHECK_EXIT(env);
    return result;
}

static jint Check_MonitorExit(JNIEnv* env, jobject obj)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    CHECK_OBJECT(env, obj);
    jint result;
    result = BASE_ENV(env)->MonitorExit(env, obj);
    CHECK_EXIT(env);
    return result;
}

static jint Check_GetJavaVM(JNIEnv *env, JavaVM **vm)
{
    CHECK_ENTER(env, kFlag_Default);
    jint result;
    result = BASE_ENV(env)->GetJavaVM(env, vm);
    CHECK_EXIT(env);
    return result;
}

static void Check_GetStringRegion(JNIEnv* env, jstring str, jsize start,
    jsize len, jchar* buf)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_STRING(env, str);
    BASE_ENV(env)->GetStringRegion(env, str, start, len, buf);
    CHECK_EXIT(env);
}

static void Check_GetStringUTFRegion(JNIEnv* env, jstring str, jsize start,
    jsize len, char* buf)
{
    CHECK_ENTER(env, kFlag_CritOkay);
    CHECK_STRING(env, str);
    BASE_ENV(env)->GetStringUTFRegion(env, str, start, len, buf);
    CHECK_EXIT(env);
}

static void* Check_GetPrimitiveArrayCritical(JNIEnv* env, jarray array,
    jboolean* isCopy)
{
    CHECK_ENTER(env, kFlag_CritGet);
    CHECK_ARRAY(env, array);
    void* result;
    result = BASE_ENV(env)->GetPrimitiveArrayCritical(env, array, isCopy);
    CHECK_EXIT(env);
    return result;
}

static void Check_ReleasePrimitiveArrayCritical(JNIEnv* env, jarray array,
    void* carray, jint mode)
{
    CHECK_ENTER(env, kFlag_CritRelease | kFlag_ExcepOkay);
    CHECK_ARRAY(env, array);
    BASE_ENV(env)->ReleasePrimitiveArrayCritical(env, array, carray, mode);
    CHECK_EXIT(env);
}

static const jchar* Check_GetStringCritical(JNIEnv* env, jstring string,
    jboolean* isCopy)
{
    CHECK_ENTER(env, kFlag_CritGet);
    CHECK_STRING(env, string);
    const jchar* result;
    result = BASE_ENV(env)->GetStringCritical(env, string, isCopy);
    CHECK_EXIT(env);
    return result;
}

static void Check_ReleaseStringCritical(JNIEnv* env, jstring string,
    const jchar* carray)
{
    CHECK_ENTER(env, kFlag_CritRelease | kFlag_ExcepOkay);
    CHECK_STRING(env, string);
    BASE_ENV(env)->ReleaseStringCritical(env, string, carray);
    CHECK_EXIT(env);
}

static jweak Check_NewWeakGlobalRef(JNIEnv* env, jobject obj)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    jweak result;
    result = BASE_ENV(env)->NewWeakGlobalRef(env, obj);
    CHECK_EXIT(env);
    return result;
}

static void Check_DeleteWeakGlobalRef(JNIEnv* env, jweak obj)
{
    CHECK_ENTER(env, kFlag_Default | kFlag_ExcepOkay);
    CHECK_OBJECT(env, obj);
    BASE_ENV(env)->DeleteWeakGlobalRef(env, obj);
    CHECK_EXIT(env);
}

static jboolean Check_ExceptionCheck(JNIEnv* env)
{
    CHECK_ENTER(env, kFlag_CritOkay | kFlag_ExcepOkay);
    jboolean result;
    result = BASE_ENV(env)->ExceptionCheck(env);
    CHECK_EXIT(env);
    return result;
}

static jobjectRefType Check_GetObjectRefType(JNIEnv* env, jobject obj)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, obj);
    jobjectRefType result;
    result = BASE_ENV(env)->GetObjectRefType(env, obj);
    CHECK_EXIT(env);
    return result;
}

static jobject Check_NewDirectByteBuffer(JNIEnv* env, void* address,
    jlong capacity)
{
    CHECK_ENTER(env, kFlag_Default);
    jobject result;
    if (address == NULL || capacity < 0) {
        LOGW("JNI WARNING: invalid values for address (%p) or capacity (%ld)\n",
            address, (long) capacity);
        abortMaybe();
    }
    result = BASE_ENV(env)->NewDirectByteBuffer(env, address, capacity);
    CHECK_EXIT(env);
    return result;
}

static void* Check_GetDirectBufferAddress(JNIEnv* env, jobject buf)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, buf);
    void* result;
    //if (buf == NULL)
    //    result = NULL;
    //else
        result = BASE_ENV(env)->GetDirectBufferAddress(env, buf);
    CHECK_EXIT(env);
    return result;
}

static jlong Check_GetDirectBufferCapacity(JNIEnv* env, jobject buf)
{
    CHECK_ENTER(env, kFlag_Default);
    CHECK_OBJECT(env, buf);
    jlong result;
    //if (buf == NULL)
    //    result = -1;
    //else
        result = BASE_ENV(env)->GetDirectBufferCapacity(env, buf);
    CHECK_EXIT(env);
    return result;
}


/*
 * ===========================================================================
 *      JNI invocation functions
 * ===========================================================================
 */

static jint Check_DestroyJavaVM(JavaVM* vm)
{
    CHECK_VMENTER(vm, false);
    jint result;
    result = BASE_VM(vm)->DestroyJavaVM(vm);
    CHECK_VMEXIT(vm, false);
    return result;
}

static jint Check_AttachCurrentThread(JavaVM* vm, JNIEnv** p_env,
    void* thr_args)
{
    CHECK_VMENTER(vm, false);
    jint result;
    result = BASE_VM(vm)->AttachCurrentThread(vm, p_env, thr_args);
    CHECK_VMEXIT(vm, true);
    return result;
}

static jint Check_AttachCurrentThreadAsDaemon(JavaVM* vm, JNIEnv** p_env,
    void* thr_args)
{
    CHECK_VMENTER(vm, false);
    jint result;
    result = BASE_VM(vm)->AttachCurrentThreadAsDaemon(vm, p_env, thr_args);
    CHECK_VMEXIT(vm, true);
    return result;
}

static jint Check_DetachCurrentThread(JavaVM* vm)
{
    CHECK_VMENTER(vm, true);
    jint result;
    result = BASE_VM(vm)->DetachCurrentThread(vm);
    CHECK_VMEXIT(vm, false);
    return result;
}

static jint Check_GetEnv(JavaVM* vm, void** env, jint version)
{
    CHECK_VMENTER(vm, true);
    jint result;
    result = BASE_VM(vm)->GetEnv(vm, env, version);
    CHECK_VMEXIT(vm, true);
    return result;
}


/*
 * ===========================================================================
 *      Function tables
 * ===========================================================================
 */

static const struct JNINativeInterface gCheckNativeInterface = {
    NULL,
    NULL,
    NULL,
    NULL,

    Check_GetVersion,

    Check_DefineClass,
    Check_FindClass,

    Check_FromReflectedMethod,
    Check_FromReflectedField,
    Check_ToReflectedMethod,

    Check_GetSuperclass,
    Check_IsAssignableFrom,

    Check_ToReflectedField,

    Check_Throw,
    Check_ThrowNew,
    Check_ExceptionOccurred,
    Check_ExceptionDescribe,
    Check_ExceptionClear,
    Check_FatalError,

    Check_PushLocalFrame,
    Check_PopLocalFrame,

    Check_NewGlobalRef,
    Check_DeleteGlobalRef,
    Check_DeleteLocalRef,
    Check_IsSameObject,
    Check_NewLocalRef,
    Check_EnsureLocalCapacity,

    Check_AllocObject,
    Check_NewObject,
    Check_NewObjectV,
    Check_NewObjectA,

    Check_GetObjectClass,
    Check_IsInstanceOf,

    Check_GetMethodID,

    Check_CallObjectMethod,
    Check_CallObjectMethodV,
    Check_CallObjectMethodA,
    Check_CallBooleanMethod,
    Check_CallBooleanMethodV,
    Check_CallBooleanMethodA,
    Check_CallByteMethod,
    Check_CallByteMethodV,
    Check_CallByteMethodA,
    Check_CallCharMethod,
    Check_CallCharMethodV,
    Check_CallCharMethodA,
    Check_CallShortMethod,
    Check_CallShortMethodV,
    Check_CallShortMethodA,
    Check_CallIntMethod,
    Check_CallIntMethodV,
    Check_CallIntMethodA,
    Check_CallLongMethod,
    Check_CallLongMethodV,
    Check_CallLongMethodA,
    Check_CallFloatMethod,
    Check_CallFloatMethodV,
    Check_CallFloatMethodA,
    Check_CallDoubleMethod,
    Check_CallDoubleMethodV,
    Check_CallDoubleMethodA,
    Check_CallVoidMethod,
    Check_CallVoidMethodV,
    Check_CallVoidMethodA,

    Check_CallNonvirtualObjectMethod,
    Check_CallNonvirtualObjectMethodV,
    Check_CallNonvirtualObjectMethodA,
    Check_CallNonvirtualBooleanMethod,
    Check_CallNonvirtualBooleanMethodV,
    Check_CallNonvirtualBooleanMethodA,
    Check_CallNonvirtualByteMethod,
    Check_CallNonvirtualByteMethodV,
    Check_CallNonvirtualByteMethodA,
    Check_CallNonvirtualCharMethod,
    Check_CallNonvirtualCharMethodV,
    Check_CallNonvirtualCharMethodA,
    Check_CallNonvirtualShortMethod,
    Check_CallNonvirtualShortMethodV,
    Check_CallNonvirtualShortMethodA,
    Check_CallNonvirtualIntMethod,
    Check_CallNonvirtualIntMethodV,
    Check_CallNonvirtualIntMethodA,
    Check_CallNonvirtualLongMethod,
    Check_CallNonvirtualLongMethodV,
    Check_CallNonvirtualLongMethodA,
    Check_CallNonvirtualFloatMethod,
    Check_CallNonvirtualFloatMethodV,
    Check_CallNonvirtualFloatMethodA,
    Check_CallNonvirtualDoubleMethod,
    Check_CallNonvirtualDoubleMethodV,
    Check_CallNonvirtualDoubleMethodA,
    Check_CallNonvirtualVoidMethod,
    Check_CallNonvirtualVoidMethodV,
    Check_CallNonvirtualVoidMethodA,

    Check_GetFieldID,

    Check_GetObjectField,
    Check_GetBooleanField,
    Check_GetByteField,
    Check_GetCharField,
    Check_GetShortField,
    Check_GetIntField,
    Check_GetLongField,
    Check_GetFloatField,
    Check_GetDoubleField,
    Check_SetObjectField,
    Check_SetBooleanField,
    Check_SetByteField,
    Check_SetCharField,
    Check_SetShortField,
    Check_SetIntField,
    Check_SetLongField,
    Check_SetFloatField,
    Check_SetDoubleField,

    Check_GetStaticMethodID,

    Check_CallStaticObjectMethod,
    Check_CallStaticObjectMethodV,
    Check_CallStaticObjectMethodA,
    Check_CallStaticBooleanMethod,
    Check_CallStaticBooleanMethodV,
    Check_CallStaticBooleanMethodA,
    Check_CallStaticByteMethod,
    Check_CallStaticByteMethodV,
    Check_CallStaticByteMethodA,
    Check_CallStaticCharMethod,
    Check_CallStaticCharMethodV,
    Check_CallStaticCharMethodA,
    Check_CallStaticShortMethod,
    Check_CallStaticShortMethodV,
    Check_CallStaticShortMethodA,
    Check_CallStaticIntMethod,
    Check_CallStaticIntMethodV,
    Check_CallStaticIntMethodA,
    Check_CallStaticLongMethod,
    Check_CallStaticLongMethodV,
    Check_CallStaticLongMethodA,
    Check_CallStaticFloatMethod,
    Check_CallStaticFloatMethodV,
    Check_CallStaticFloatMethodA,
    Check_CallStaticDoubleMethod,
    Check_CallStaticDoubleMethodV,
    Check_CallStaticDoubleMethodA,
    Check_CallStaticVoidMethod,
    Check_CallStaticVoidMethodV,
    Check_CallStaticVoidMethodA,

    Check_GetStaticFieldID,

    Check_GetStaticObjectField,
    Check_GetStaticBooleanField,
    Check_GetStaticByteField,
    Check_GetStaticCharField,
    Check_GetStaticShortField,
    Check_GetStaticIntField,
    Check_GetStaticLongField,
    Check_GetStaticFloatField,
    Check_GetStaticDoubleField,

    Check_SetStaticObjectField,
    Check_SetStaticBooleanField,
    Check_SetStaticByteField,
    Check_SetStaticCharField,
    Check_SetStaticShortField,
    Check_SetStaticIntField,
    Check_SetStaticLongField,
    Check_SetStaticFloatField,
    Check_SetStaticDoubleField,

    Check_NewString,

    Check_GetStringLength,
    Check_GetStringChars,
    Check_ReleaseStringChars,

    Check_NewStringUTF,
    Check_GetStringUTFLength,
    Check_GetStringUTFChars,
    Check_ReleaseStringUTFChars,

    Check_GetArrayLength,
    Check_NewObjectArray,
    Check_GetObjectArrayElement,
    Check_SetObjectArrayElement,

    Check_NewBooleanArray,
    Check_NewByteArray,
    Check_NewCharArray,
    Check_NewShortArray,
    Check_NewIntArray,
    Check_NewLongArray,
    Check_NewFloatArray,
    Check_NewDoubleArray,

    Check_GetBooleanArrayElements,
    Check_GetByteArrayElements,
    Check_GetCharArrayElements,
    Check_GetShortArrayElements,
    Check_GetIntArrayElements,
    Check_GetLongArrayElements,
    Check_GetFloatArrayElements,
    Check_GetDoubleArrayElements,

    Check_ReleaseBooleanArrayElements,
    Check_ReleaseByteArrayElements,
    Check_ReleaseCharArrayElements,
    Check_ReleaseShortArrayElements,
    Check_ReleaseIntArrayElements,
    Check_ReleaseLongArrayElements,
    Check_ReleaseFloatArrayElements,
    Check_ReleaseDoubleArrayElements,

    Check_GetBooleanArrayRegion,
    Check_GetByteArrayRegion,
    Check_GetCharArrayRegion,
    Check_GetShortArrayRegion,
    Check_GetIntArrayRegion,
    Check_GetLongArrayRegion,
    Check_GetFloatArrayRegion,
    Check_GetDoubleArrayRegion,
    Check_SetBooleanArrayRegion,
    Check_SetByteArrayRegion,
    Check_SetCharArrayRegion,
    Check_SetShortArrayRegion,
    Check_SetIntArrayRegion,
    Check_SetLongArrayRegion,
    Check_SetFloatArrayRegion,
    Check_SetDoubleArrayRegion,

    Check_RegisterNatives,
    Check_UnregisterNatives,

    Check_MonitorEnter,
    Check_MonitorExit,

    Check_GetJavaVM,

    Check_GetStringRegion,
    Check_GetStringUTFRegion,

    Check_GetPrimitiveArrayCritical,
    Check_ReleasePrimitiveArrayCritical,

    Check_GetStringCritical,
    Check_ReleaseStringCritical,

    Check_NewWeakGlobalRef,
    Check_DeleteWeakGlobalRef,

    Check_ExceptionCheck,

    Check_NewDirectByteBuffer,
    Check_GetDirectBufferAddress,
    Check_GetDirectBufferCapacity,

    Check_GetObjectRefType
};
static const struct JNIInvokeInterface gCheckInvokeInterface = {
    NULL,
    NULL,
    NULL,

    Check_DestroyJavaVM,
    Check_AttachCurrentThread,
    Check_DetachCurrentThread,

    Check_GetEnv,

    Check_AttachCurrentThreadAsDaemon,
};


/*
 * Replace the normal table with the checked table.
 */
void dvmUseCheckedJniEnv(JNIEnvExt* pEnv)
{
    assert(pEnv->funcTable != &gCheckNativeInterface);
    pEnv->baseFuncTable = pEnv->funcTable;
    pEnv->funcTable = &gCheckNativeInterface;
}

/*
 * Replace the normal table with the checked table.
 */
void dvmUseCheckedJniVm(JavaVMExt* pVm)
{
    assert(pVm->funcTable != &gCheckInvokeInterface);
    pVm->baseFuncTable = pVm->funcTable;
    pVm->funcTable = &gCheckInvokeInterface;
}

