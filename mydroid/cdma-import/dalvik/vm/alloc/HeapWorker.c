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
 * An async worker thread to handle certain heap operations that
 * need to be done in a separate thread to avoid synchronization
 * problems.  HeapWorkers and reference clearing/enqueuing are
 * handled by this thread.
 */
#include "Dalvik.h"
#include "HeapInternal.h"

#include <sys/time.h>
#include <stdlib.h>
#include <pthread.h>
#include <signal.h>
#include <errno.h>  // for ETIMEDOUT, etc.

static void* heapWorkerThreadStart(void* arg);

/*
 * Initialize any HeapWorker state that Heap.c
 * cares about.  This lets the GC start before the
 * HeapWorker thread is initialized.
 */
void dvmInitializeHeapWorkerState()
{
    assert(!gDvm.heapWorkerInitialized);

    dvmInitMutex(&gDvm.heapWorkerLock);
    pthread_cond_init(&gDvm.heapWorkerCond, NULL);
    pthread_cond_init(&gDvm.heapWorkerIdleCond, NULL);

    gDvm.heapWorkerInitialized = true;
}

/*
 * Crank up the heap worker thread.
 *
 * Does not return until the thread is ready for business.
 */
bool dvmHeapWorkerStartup(void)
{
    assert(!gDvm.haltHeapWorker);
    assert(!gDvm.heapWorkerReady);
    assert(gDvm.heapWorkerHandle == 0);
    assert(gDvm.heapWorkerInitialized);

    /* use heapWorkerLock/heapWorkerCond to communicate readiness */
    dvmLockMutex(&gDvm.heapWorkerLock);

//BUG: If a GC happens in here or in the new thread while we hold the lock,
//     the GC will deadlock when trying to acquire heapWorkerLock.
    if (!dvmCreateInternalThread(&gDvm.heapWorkerHandle,
                "HeapWorker", heapWorkerThreadStart, NULL))
    {
        dvmUnlockMutex(&gDvm.heapWorkerLock);
        return false;
    }

    /*
     * Wait for the heap worker to come up.  We know the thread was created,
     * so this should not get stuck.
     */
    while (!gDvm.heapWorkerReady) {
        int cc = pthread_cond_wait(&gDvm.heapWorkerCond, &gDvm.heapWorkerLock);
        assert(cc == 0);
    }

    dvmUnlockMutex(&gDvm.heapWorkerLock);
    return true;
}

/*
 * Shut down the heap worker thread if it was started.
 */
void dvmHeapWorkerShutdown(void)
{
    void* threadReturn;

    /* note: assuming that (pthread_t)0 is not a valid thread handle */
    if (gDvm.heapWorkerHandle != 0) {
        gDvm.haltHeapWorker = true;
        dvmSignalHeapWorker(true);

        /*
         * We may not want to wait for the heapWorkers to complete.  It's
         * a good idea to do so, in case they're holding some sort of OS
         * resource that doesn't get reclaimed when the process exits
         * (e.g. an open temp file).
         */
        if (pthread_join(gDvm.heapWorkerHandle, &threadReturn) != 0)
            LOGW("HeapWorker thread join failed\n");
        else
            LOGD("HeapWorker thread has shut down\n");

        gDvm.heapWorkerReady = false;
    }
}

/* Make sure that the HeapWorker thread hasn't spent an inordinate
 * amount of time inside interpreted a finalizer.
 *
 * Aborts the VM if the thread appears to be wedged.
 *
 * The caller must hold the heapWorkerLock to guarantee an atomic
 * read of the watchdog values.
 */
void dvmAssertHeapWorkerThreadRunning()
{
    if (gDvm.gcHeap->heapWorkerCurrentObject != NULL) {
        static const u8 HEAP_WORKER_WATCHDOG_TIMEOUT = 10*1000*1000LL; // 10sec

        u8 heapWorkerInterpStartTime = gDvm.gcHeap->heapWorkerInterpStartTime;
        u8 now = dvmGetRelativeTimeUsec();
        u8 delta = now - heapWorkerInterpStartTime;

        u8 heapWorkerInterpCpuStartTime =
            gDvm.gcHeap->heapWorkerInterpCpuStartTime;
        u8 nowCpu = dvmGetOtherThreadCpuTimeUsec(gDvm.heapWorkerHandle);
        u8 deltaCpu = nowCpu - heapWorkerInterpCpuStartTime;

        if (delta > HEAP_WORKER_WATCHDOG_TIMEOUT && gDvm.debuggerActive) {
            /*
             * Debugger suspension can block the thread indefinitely.  For
             * best results we should reset this explicitly whenever the
             * HeapWorker thread is resumed.  Ignoring the yelp isn't
             * quite right but will do for a quick fix.
             */
            LOGI("Debugger is attached -- suppressing HeapWorker watchdog\n");
            heapWorkerInterpStartTime = now;        /* reset timer */
        } else if (delta > HEAP_WORKER_WATCHDOG_TIMEOUT) {
            char* desc = dexProtoCopyMethodDescriptor(
                    &gDvm.gcHeap->heapWorkerCurrentMethod->prototype);
            LOGE("HeapWorker is wedged: %lldms spent inside %s.%s%s\n",
                    delta / 1000,
                    gDvm.gcHeap->heapWorkerCurrentObject->clazz->descriptor,
                    gDvm.gcHeap->heapWorkerCurrentMethod->name, desc);
            free(desc);
            dvmDumpAllThreads(true);

            /* abort the VM */
            dvmAbort();
        } else if (delta > HEAP_WORKER_WATCHDOG_TIMEOUT / 2) {
            char* desc = dexProtoCopyMethodDescriptor(
                    &gDvm.gcHeap->heapWorkerCurrentMethod->prototype);
            LOGW("HeapWorker may be wedged: %lldms spent inside %s.%s%s\n",
                    delta / 1000,
                    gDvm.gcHeap->heapWorkerCurrentObject->clazz->descriptor,
                    gDvm.gcHeap->heapWorkerCurrentMethod->name, desc);
            free(desc);
        }
    }
}

static void callMethod(Thread *self, Object *obj, Method *method)
{
    JValue unused;

    /* Keep track of the method we're about to call and
     * the current time so that other threads can detect
     * when this thread wedges and provide useful information.
     */
    gDvm.gcHeap->heapWorkerInterpStartTime = dvmGetRelativeTimeUsec();
    gDvm.gcHeap->heapWorkerInterpCpuStartTime = dvmGetThreadCpuTimeUsec();
    gDvm.gcHeap->heapWorkerCurrentMethod = method;
    gDvm.gcHeap->heapWorkerCurrentObject = obj;

    /* Call the method.
     *
     * Don't hold the lock when executing interpreted
     * code.  It may suspend, and the GC needs to grab
     * heapWorkerLock.
     */
    dvmUnlockMutex(&gDvm.heapWorkerLock);
    if (false) {
        /* Log entry/exit; this will likely flood the log enough to
         * cause "logcat" to drop entries.
         */
        char tmpTag[16];
        sprintf(tmpTag, "HW%d", self->systemTid);
        LOG(LOG_DEBUG, tmpTag, "Call %s\n", method->clazz->descriptor);
        dvmCallMethod(self, method, obj, &unused);
        LOG(LOG_DEBUG, tmpTag, " done\n");
    } else {
        dvmCallMethod(self, method, obj, &unused);
    }
    dvmLockMutex(&gDvm.heapWorkerLock);

    gDvm.gcHeap->heapWorkerCurrentObject = NULL;
    gDvm.gcHeap->heapWorkerCurrentMethod = NULL;
    gDvm.gcHeap->heapWorkerInterpStartTime = 0LL;

    /* Exceptions thrown during these calls interrupt
     * the method, but are otherwise ignored.
     */
    if (dvmCheckException(self)) {
#if DVM_SHOW_EXCEPTION >= 1
        LOGI("Uncaught exception thrown by finalizer (will be discarded):\n");
        dvmLogExceptionStackTrace();
#endif
        dvmClearException(self);
    }
}

/* Process all enqueued heap work, including finalizers and reference
 * clearing/enqueueing.
 *
 * Caller must hold gDvm.heapWorkerLock.
 */
static void doHeapWork(Thread *self)
{
    Object *obj;
    HeapWorkerOperation op;
    int numFinalizersCalled, numReferencesEnqueued;
#if FANCY_REFERENCE_SUBCLASS
    int numReferencesCleared = 0;
#endif

    assert(gDvm.voffJavaLangObject_finalize >= 0);
#if FANCY_REFERENCE_SUBCLASS
    assert(gDvm.voffJavaLangRefReference_clear >= 0);
    assert(gDvm.voffJavaLangRefReference_enqueue >= 0);
#else
    assert(gDvm.methJavaLangRefReference_enqueueInternal != NULL);
#endif

    numFinalizersCalled = 0;
    numReferencesEnqueued = 0;
    while ((obj = dvmGetNextHeapWorkerObject(&op)) != NULL) {
        Method *method = NULL;

        /* Make sure the object hasn't been collected since
         * being scheduled.
         */
        assert(dvmIsValidObject(obj));

        /* Call the appropriate method(s).
         */
        if (op == WORKER_FINALIZE) {
            numFinalizersCalled++;
            method = obj->clazz->vtable[gDvm.voffJavaLangObject_finalize];
            assert(dvmCompareNameDescriptorAndMethod("finalize", "()V",
                            method) == 0);
            assert(method->clazz != gDvm.classJavaLangObject);
            callMethod(self, obj, method);
        } else {
#if FANCY_REFERENCE_SUBCLASS
            /* clear() *must* happen before enqueue(), otherwise
             * a non-clear reference could appear on a reference
             * queue.
             */
            if (op & WORKER_CLEAR) {
                numReferencesCleared++;
                method = obj->clazz->vtable[
                        gDvm.voffJavaLangRefReference_clear];
                assert(dvmCompareNameDescriptorAndMethod("clear", "()V",
                                method) == 0);
                assert(method->clazz != gDvm.classJavaLangRefReference);
                callMethod(self, obj, method);
            }
            if (op & WORKER_ENQUEUE) {
                numReferencesEnqueued++;
                method = obj->clazz->vtable[
                        gDvm.voffJavaLangRefReference_enqueue];
                assert(dvmCompareNameDescriptorAndMethod("enqueue", "()Z",
                                method) == 0);
                /* We call enqueue() even when it isn't overridden,
                 * so don't assert(!classJavaLangRefReference) here.
                 */
                callMethod(self, obj, method);
            }
#else
            assert((op & WORKER_CLEAR) == 0);
            if (op & WORKER_ENQUEUE) {
                numReferencesEnqueued++;
                callMethod(self, obj,
                        gDvm.methJavaLangRefReference_enqueueInternal);
            }
#endif
        }

        /* Let the GC collect the object.
         */
        dvmReleaseTrackedAlloc(obj, self);
    }
    LOGV("Called %d finalizers\n", numFinalizersCalled);
    LOGV("Enqueued %d references\n", numReferencesEnqueued);
#if FANCY_REFERENCE_SUBCLASS
    LOGV("Cleared %d overridden references\n", numReferencesCleared);
#endif
}

/*
 * The heap worker thread sits quietly until the GC tells it there's work
 * to do.
 */
static void* heapWorkerThreadStart(void* arg)
{
    Thread *self = dvmThreadSelf();
    int cc;

    UNUSED_PARAMETER(arg);

    LOGV("HeapWorker thread started (threadid=%d)\n", self->threadId);

    /* tell the main thread that we're ready */
    dvmLockMutex(&gDvm.heapWorkerLock);
    gDvm.heapWorkerReady = true;
    cc = pthread_cond_signal(&gDvm.heapWorkerCond);
    assert(cc == 0);
    dvmUnlockMutex(&gDvm.heapWorkerLock);

    dvmLockMutex(&gDvm.heapWorkerLock);
    while (!gDvm.haltHeapWorker) {
        struct timespec trimtime;
        bool timedwait = false;

        /* We're done running interpreted code for now. */
        dvmChangeStatus(NULL, THREAD_VMWAIT);

        /* Signal anyone who wants to know when we're done. */
        cc = pthread_cond_broadcast(&gDvm.heapWorkerIdleCond);
        assert(cc == 0);

        /* Trim the heap if we were asked to. */
        trimtime = gDvm.gcHeap->heapWorkerNextTrim;
        if (trimtime.tv_sec != 0 && trimtime.tv_nsec != 0) {
            struct timeval now;

            gettimeofday(&now, NULL);
            if (trimtime.tv_sec < now.tv_sec ||
                (trimtime.tv_sec == now.tv_sec && 
                 trimtime.tv_nsec <= now.tv_usec * 1000))
            {
                size_t madvisedSizes[HEAP_SOURCE_MAX_HEAP_COUNT];

                /* The heap must be locked before the HeapWorker;
                 * unroll and re-order the locks.  dvmLockHeap()
                 * will put us in VMWAIT if necessary.  Once it
                 * returns, there shouldn't be any contention on
                 * heapWorkerLock.
                 */
                dvmUnlockMutex(&gDvm.heapWorkerLock);
                dvmLockHeap();
                dvmLockMutex(&gDvm.heapWorkerLock);

                memset(madvisedSizes, 0, sizeof(madvisedSizes));
                dvmHeapSourceTrim(madvisedSizes, HEAP_SOURCE_MAX_HEAP_COUNT);
                dvmLogMadviseStats(madvisedSizes, HEAP_SOURCE_MAX_HEAP_COUNT);

                dvmUnlockHeap();

                trimtime.tv_sec = 0;
                trimtime.tv_nsec = 0;
                gDvm.gcHeap->heapWorkerNextTrim = trimtime;
            } else {
                timedwait = true;
            }
        }

        /* sleep until signaled */
        if (timedwait) {
            cc = pthread_cond_timedwait(&gDvm.heapWorkerCond,
                    &gDvm.heapWorkerLock, &trimtime);
            assert(cc == 0 || cc == ETIMEDOUT || cc == EINTR);
        } else {
            cc = pthread_cond_wait(&gDvm.heapWorkerCond, &gDvm.heapWorkerLock);
            assert(cc == 0);
        }

        /* dvmChangeStatus() may block;  don't hold heapWorkerLock.
         */
        dvmUnlockMutex(&gDvm.heapWorkerLock);
        dvmChangeStatus(NULL, THREAD_RUNNING);
        dvmLockMutex(&gDvm.heapWorkerLock);
        LOGV("HeapWorker is awake\n");

        /* Process any events in the queue.
         */
        doHeapWork(self);
    }
    dvmUnlockMutex(&gDvm.heapWorkerLock);

    LOGD("HeapWorker thread shutting down\n");
    return NULL;
}

/*
 * Wake up the heap worker to let it know that there's work to be done.
 */
void dvmSignalHeapWorker(bool shouldLock)
{
    int cc;

    if (shouldLock) {
        dvmLockMutex(&gDvm.heapWorkerLock);
    }

    cc = pthread_cond_signal(&gDvm.heapWorkerCond);
    assert(cc == 0);

    if (shouldLock) {
        dvmUnlockMutex(&gDvm.heapWorkerLock);
    }
}

/*
 * Block until all pending heap worker work has finished.
 */
void dvmWaitForHeapWorkerIdle()
{
    int cc;

    assert(gDvm.heapWorkerReady);

    dvmChangeStatus(NULL, THREAD_VMWAIT);

    dvmLockMutex(&gDvm.heapWorkerLock);

    /* Wake up the heap worker and wait for it to finish. */
    //TODO(http://b/issue?id=699704): This will deadlock if
    //     called from finalize(), enqueue(), or clear().  We
    //     need to detect when this is called from the HeapWorker
    //     context and just give up.
    dvmSignalHeapWorker(false);
    cc = pthread_cond_wait(&gDvm.heapWorkerIdleCond, &gDvm.heapWorkerLock);
    assert(cc == 0);

    dvmUnlockMutex(&gDvm.heapWorkerLock);

    dvmChangeStatus(NULL, THREAD_RUNNING);
}

/*
 * Do not return until any pending heap work has finished.  This may
 * or may not happen in the context of the calling thread.
 * No exceptions will escape.
 */
void dvmRunFinalizationSync()
{
    if (gDvm.zygote) {
        assert(!gDvm.heapWorkerReady);

        /* When in zygote mode, there is no heap worker.
         * Do the work in the current thread.
         */
        dvmLockMutex(&gDvm.heapWorkerLock);
        doHeapWork(dvmThreadSelf());
        dvmUnlockMutex(&gDvm.heapWorkerLock);
    } else {
        /* Outside of zygote mode, we can just ask the
         * heap worker thread to do the work.
         */
        dvmWaitForHeapWorkerIdle();
    }
}

/*
 * Requests that dvmHeapSourceTrim() be called no sooner
 * than timeoutSec seconds from now.  If timeoutSec
 * is zero, any pending trim is cancelled.
 *
 * Caller must hold heapWorkerLock.
 */
void dvmScheduleHeapSourceTrim(size_t timeoutSec)
{
    GcHeap *gcHeap = gDvm.gcHeap;
    struct timespec timeout;

    if (timeoutSec == 0) {
        timeout.tv_sec = 0;
        timeout.tv_nsec = 0;
        /* Don't wake up the thread just to tell it to cancel.
         * If it wakes up naturally, we can avoid the extra
         * context switch.
         */
    } else {
        struct timeval now;

        gettimeofday(&now, NULL);
        timeout.tv_sec = now.tv_sec + timeoutSec;
        timeout.tv_nsec = now.tv_usec * 1000;
        dvmSignalHeapWorker(false);
    }
    gcHeap->heapWorkerNextTrim = timeout;
}
