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
 * VM thread support.
 */
#ifndef _DALVIK_THREAD
#define _DALVIK_THREAD

#include "jni.h"

#if defined(CHECK_MUTEX) && !defined(__USE_UNIX98)
/* Linux lacks this unless you #define __USE_UNIX98 */
int pthread_mutexattr_settype(pthread_mutexattr_t *attr, int type);
enum { PTHREAD_MUTEX_ERRORCHECK = PTHREAD_MUTEX_ERRORCHECK_NP };
#endif

#ifdef WITH_MONITOR_TRACKING
struct LockedObjectData;
#endif

/*
 * Current status; these map to JDWP constants, so don't rearrange them.
 * (If you do alter this, update the strings in dvmDumpThread and the
 * conversion table in VMThread.java.)
 *
 * Note that "suspended" is orthogonal to these values (so says JDWP).
 */
typedef enum ThreadStatus {
    /* these match up with JDWP values */
    THREAD_ZOMBIE       = 0,        /* TERMINATED */
    THREAD_RUNNING      = 1,        /* RUNNABLE or running now */
    THREAD_TIMED_WAIT   = 2,        /* TIMED_WAITING in Object.wait() */
    THREAD_MONITOR      = 3,        /* BLOCKED on a monitor */
    THREAD_WAIT         = 4,        /* WAITING in Object.wait() */
    /* non-JDWP states */
    THREAD_INITIALIZING = 5,        /* allocated, not yet running */
    THREAD_STARTING     = 6,        /* started, not yet on thread list */
    THREAD_NATIVE       = 7,        /* off in a JNI native method */
    THREAD_VMWAIT       = 8,        /* waiting on a VM resource */
} ThreadStatus;

/* thread priorities, from java.lang.Thread */
enum {
    THREAD_MIN_PRIORITY     = 1,
    THREAD_NORM_PRIORITY    = 5,
    THREAD_MAX_PRIORITY     = 10,
};


/* initialization */
bool dvmThreadStartup(void);
bool dvmThreadObjStartup(void);
void dvmThreadShutdown(void);
void dvmSlayDaemons(void);


#define kJniLocalRefMax         512     /* arbitrary; should be plenty */
#define kInternalRefDefault     32      /* equally arbitrary */
#define kInternalRefMax         4096    /* mainly a sanity check */

#define kMinStackSize       (512 + STACK_OVERFLOW_RESERVE)
#define kDefaultStackSize   (8*1024)    /* two 4K pages */
#define kMaxStackSize       (256*1024 + STACK_OVERFLOW_RESERVE)

/*
 * Our per-thread data.
 *
 * These are allocated on the system heap.
 */
typedef struct Thread {
    /* small unique integer; useful for "thin" locks and debug messages */
    u4          threadId;

    /*
     * Thread's current status.  Can only be changed by the thread itself
     * (i.e. don't mess with this from other threads).
     */
    ThreadStatus status;

    /*
     * This is the number of times the thread has been suspended.  When the
     * count drops to zero, the thread resumes.
     *
     * "dbgSuspendCount" is the portion of the suspend count that the
     * debugger is responsible for.  This has to be tracked separately so
     * that we can recover correctly if the debugger abruptly disconnects
     * (suspendCount -= dbgSuspendCount).  The debugger should not be able
     * to resume GC-suspended threads, because we ignore the debugger while
     * a GC is in progress.
     *
     * Both of these are guarded by gDvm.threadSuspendCountLock.
     *
     * (We could store both of these in the same 32-bit, using 16-bit
     * halves, to make atomic ops possible.  In practice, you only need
     * to read suspendCount, and we need to hold a mutex when making
     * changes, so there's no need to merge them.  Note the non-debug
     * component will rarely be other than 1 or 0 -- not sure it's even
     * possible with the way mutexes are currently used.)
     */
    int         suspendCount;
    int         dbgSuspendCount;

    /*
     * Set to true when the thread suspends itself, false when it wakes up.
     * This is only expected to be set when status==THREAD_RUNNING.
     */
    bool        isSuspended;

    /* thread handle, as reported by pthread_self() */
    pthread_t   handle;

    /* thread ID, only useful under Linux */
    pid_t       systemTid;

    /* start (high addr) of interp stack (subtract size to get malloc addr) */
    u1*         interpStackStart;

    /* current limit of stack; flexes for StackOverflowError */
    const u1*   interpStackEnd;

    /* interpreter stack size; our stacks are fixed-length */
    int         interpStackSize;
    bool        stackOverflowed;

    /* FP of bottom-most (currently executing) stack frame on interp stack */
    void*       curFrame;

    /* current exception, or NULL if nothing pending */
    Object*     exception;

    /* the java/lang/Thread that we are associated with */
    Object*     threadObj;

    /* the JNIEnv pointer associated with this thread */
    JNIEnv*     jniEnv;

    /* internal reference tracking */
    ReferenceTable  internalLocalRefTable;

    /* JNI local reference tracking */
    ReferenceTable  jniLocalRefTable;

    /* JNI native monitor reference tracking (initialized on first use) */
    ReferenceTable  jniMonitorRefTable;

    /* hack to make JNI_OnLoad work right */
    Object*     classLoaderOverride;

    /* pointer to the monitor lock we're currently waiting on */
    /* (do not set or clear unless the Monitor itself is held) */
    /* TODO: consider changing this to Object* for better JDWP interaction */
    Monitor*    waitMonitor;
    /* set when we confirm that the thread must be interrupted from a wait */
    bool        interruptingWait;
    /* thread "interrupted" status; stays raised until queried or thrown */
    bool        interrupted;

    /*
     * Set to true when the thread is in the process of throwing an
     * OutOfMemoryError.
     */
    bool        throwingOOME;

    /* links to rest of thread list; grab global lock before traversing */
    struct Thread* prev;
    struct Thread* next;

    /* JDWP invoke-during-breakpoint support */
    DebugInvokeReq  invokeReq;

#ifdef WITH_MONITOR_TRACKING
    /* objects locked by this thread; most recent is at head of list */
    struct LockedObjectData* pLockedObjects;
#endif

#ifdef WITH_ALLOC_LIMITS
    /* allocation limit, for Debug.setAllocationLimit() regression testing */
    int         allocLimit;
#endif

#ifdef WITH_PROFILER
    /* base time for per-thread CPU timing */
    bool        cpuClockBaseSet;
    u8          cpuClockBase;

    /* memory allocation profiling state */
    AllocProfState allocProf;
#endif

#ifdef WITH_JNI_STACK_CHECK
    u4          stackCrc;
#endif
} Thread;

/* start point for an internal thread; mimics pthread args */
typedef void* (*InternalThreadStart)(void* arg);

/* args for internal thread creation */
typedef struct InternalStartArgs {
    /* inputs */
    InternalThreadStart func;
    void*       funcArg;
    char*       name;
    Object*     group;
    bool        isDaemon;
    /* result */
    volatile Thread** pThread;
    volatile int*     pCreateStatus;
} InternalStartArgs;

/* finish init */
bool dvmPrepMainForJni(JNIEnv* pEnv);
bool dvmPrepMainThread(void);

/* utility function to get the tid */
pid_t dvmGetSysThreadId(void);

/*
 * Get our Thread* from TLS.
 *
 * Returns NULL if this isn't a thread that the VM is aware of.
 */
Thread* dvmThreadSelf(void);

/* grab the thread list global lock */
void dvmLockThreadList(Thread* self);
/* release the thread list global lock */
void dvmUnlockThreadList(void);

/*
 * Thread suspend/resume, used by the GC and debugger.
 */
typedef enum SuspendCause {
    SUSPEND_NOT = 0,
    SUSPEND_FOR_GC,
    SUSPEND_FOR_DEBUG,
    SUSPEND_FOR_DEBUG_EVENT,
    SUSPEND_FOR_STACK_DUMP,
    SUSPEND_FOR_DEX_OPT,
} SuspendCause;
void dvmSuspendThread(Thread* thread);
void dvmSuspendSelf(bool jdwpActivity);
void dvmResumeThread(Thread* thread);
void dvmSuspendAllThreads(SuspendCause why);
void dvmResumeAllThreads(SuspendCause why);
void dvmUndoDebuggerSuspensions(void);

/*
 * Check suspend state.  Grab threadListLock before calling.
 */
bool dvmIsSuspended(Thread* thread);

/*
 * Wait until a thread has suspended.  (Used by debugger support.)
 */
void dvmWaitForSuspend(Thread* thread);

/*
 * Check to see if we should be suspended now.  If so, suspend ourselves
 * by sleeping on a condition variable.
 *
 * If "self" is NULL, this will use dvmThreadSelf().
 */
bool dvmCheckSuspendPending(Thread* self);

/*
 * Fast test for use in the interpreter.  If our suspend count is nonzero,
 * do a more rigorous evaluation.
 */
INLINE void dvmCheckSuspendQuick(Thread* self) {
    if (self->suspendCount != 0)
        dvmCheckSuspendPending(self);
}

/*
 * Used when changing thread state.  Threads may only change their own.
 * The "self" argument, which may be NULL, is accepted as an optimization.
 *
 * If you're calling this before waiting on a resource (e.g. THREAD_WAIT
 * or THREAD_MONITOR), do so in the same function as the wait -- this records
 * the current stack depth for the GC.
 *
 * If you're changing to THREAD_RUNNING, this will check for suspension.
 *
 * Returns the old status.
 */
ThreadStatus dvmChangeStatus(Thread* self, ThreadStatus newStatus);

/*
 * Initialize a mutex.
 */
INLINE void dvmInitMutex(pthread_mutex_t* pMutex)
{
#ifdef CHECK_MUTEX
    pthread_mutexattr_t attr;
    int cc;

    pthread_mutexattr_init(&attr);
    cc = pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_ERRORCHECK_NP);
    assert(cc == 0);
    pthread_mutex_init(pMutex, &attr);
    pthread_mutexattr_destroy(&attr);
#else
    pthread_mutex_init(pMutex, NULL);       // default=PTHREAD_MUTEX_FAST_NP
#endif
}

/*
 * Grab a plain mutex.
 */
INLINE void dvmLockMutex(pthread_mutex_t* pMutex)
{
    int cc = pthread_mutex_lock(pMutex);
    assert(cc == 0);
}

/*
 * Unlock pthread mutex.
 */
INLINE void dvmUnlockMutex(pthread_mutex_t* pMutex)
{
    int cc = pthread_mutex_unlock(pMutex);
    assert(cc == 0);
}

/*
 * Destroy a mutex.
 */
INLINE void dvmDestroyMutex(pthread_mutex_t* pMutex)
{
    int cc = pthread_mutex_destroy(pMutex);
    assert(cc == 0);
}

/*
 * Create a thread as a result of java.lang.Thread.start().
 */
bool dvmCreateInterpThread(Object* threadObj, int reqStackSize);

/*
 * Create a thread internal to the VM.  It's visible to interpreted code,
 * but found in the "system" thread group rather than "main".
 */
bool dvmCreateInternalThread(pthread_t* pHandle, const char* name,
    InternalThreadStart func, void* funcArg);

/*
 * Attach or detach the current thread from the VM.
 */
bool dvmAttachCurrentThread(const JavaVMAttachArgs* pArgs, bool isDaemon);
void dvmDetachCurrentThread(void);

/*
 * Get the "main" or "system" thread group.
 */
Object* dvmGetMainThreadGroup(void);
Object* dvmGetSystemThreadGroup(void);

/*
 * Given a java/lang/VMThread object, return our Thread.
 */
Thread* dvmGetThreadFromThreadObject(Object* vmThreadObj);

/*
 * Sleep in a thread.  Returns when the sleep timer returns or the thread
 * is interrupted.
 */
void dvmThreadSleep(u8 msec, u4 nsec);

/*
 * Interrupt a thread.  If it's waiting on a monitor, wake it up.
 */
void dvmThreadInterrupt(Thread* thread);

/*
 * Get the name of a thread.  (For safety, hold the thread list lock.)
 */
char* dvmGetThreadName(Thread* thread);

/*
 * Return true if a thread is on the internal list.  If it is, the
 * thread is part of the GC's root set.
 */
bool dvmIsOnThreadList(const Thread* thread);
 
/*
 * Get/set the JNIEnv field.
 */
INLINE JNIEnv* dvmGetThreadJNIEnv(Thread* self) { return self->jniEnv; }
INLINE void dvmSetThreadJNIEnv(Thread* self, JNIEnv* env) { self->jniEnv = env;}

/*
 * Update the priority value of the underlying pthread.
 */
void dvmChangeThreadPriority(Thread* thread, int newPriority);


/*
 * Debug: dump information about a single thread.
 */
void dvmDumpThread(Thread* thread, bool isRunning);
void dvmDumpThreadEx(const DebugOutputTarget* target, Thread* thread,
    bool isRunning);

/*
 * Debug: dump information about all threads.
 */
void dvmDumpAllThreads(bool grabLock);
void dvmDumpAllThreadsEx(const DebugOutputTarget* target, bool grabLock);


#ifdef WITH_MONITOR_TRACKING
/*
 * Track locks held by the current thread, along with the stack trace at
 * the point the lock was acquired.
 *
 * At any given time the number of locks held across the VM should be
 * fairly small, so there's no reason not to generate and store the entire
 * stack trace.
 */
typedef struct LockedObjectData {
    /* the locked object */
    struct Object*  obj;

    /* number of times it has been locked recursively (zero-based ref count) */
    int             recursionCount;

    /* stack trace at point of initial acquire */
    u4              stackDepth;
    int*            rawStackTrace;

    struct LockedObjectData* next;
} LockedObjectData;

/*
 * Add/remove/find objects from the thread's monitor list.
 */
void dvmAddToMonitorList(Thread* self, Object* obj, bool withTrace);
void dvmRemoveFromMonitorList(Thread* self, Object* obj);
LockedObjectData* dvmFindInMonitorList(const Thread* self, const Object* obj);
#endif

#endif /*_DALVIK_THREAD*/
