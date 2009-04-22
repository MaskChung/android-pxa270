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
 * Object synchronization functions.
 */
#ifndef _DALVIK_SYNC
#define _DALVIK_SYNC

struct Object;
struct Monitor;
struct Thread;
typedef struct Monitor Monitor;

#define QUIET_ZYGOTE_MONITOR 1

/*
 * Synchronization lock, included in every object.
 *
 * We want this to be a 32-bit "thin lock", holding the lock level and
 * the owner's threadId, that inflates to a Monitor pointer when there
 * is contention or somebody waits on it.
 */
typedef union Lock {
    u4          thin;
    Monitor*    mon;
} Lock;

/*
 * Initialize a Lock to the proper starting value.
 * This is necessary for thin locking.
 */
#define THIN_LOCKING 1
#if THIN_LOCKING
#define DVM_LOCK_INITIAL_THIN_VALUE (0x1)
#else
#define DVM_LOCK_INITIAL_THIN_VALUE (0)
#endif
#define DVM_LOCK_INIT(lock) \
    do { (lock)->thin = DVM_LOCK_INITIAL_THIN_VALUE; } while (0)

/*
 * Returns true if the lock has been fattened.
 */
#define IS_LOCK_FAT(lock)   (((lock)->thin & 1) == 0 && (lock)->mon != NULL)

/*
 * Acquire the object's monitor.
 */
void dvmLockObject(struct Thread* self, struct Object* obj);

/* Returns true if the unlock succeeded.
 * If the unlock failed, an exception will be pending.
 */
bool dvmUnlockObject(struct Thread* self, struct Object* obj);

/*
 * Implementations of some java/lang/Object calls.
 */
void dvmObjectWait(struct Thread* self, struct Object* obj,
    s8 timeout, s4 nanos, bool interruptShouldThrow);
void dvmObjectNotify(struct Thread* self, struct Object* obj);
void dvmObjectNotifyAll(struct Thread* self, struct Object* obj);

/*
 * Implementation of Thread.sleep().
 */
void dvmThreadSleep(u8 msec, u4 nsec);

/* create a new Monitor struct */
Monitor* dvmCreateMonitor(struct Object* obj);

/* free an object's monitor during GC */
void dvmFreeObjectMonitor_internal(Lock* lock);
#define dvmFreeObjectMonitor(obj) \
    do { \
        Object *DFM_obj_ = (obj); \
        if (IS_LOCK_FAT(&DFM_obj_->lock)) { \
            dvmFreeObjectMonitor_internal(&DFM_obj_->lock); \
        } \
    } while (0)

/* free monitor list */
void dvmFreeMonitorList(void);

/*
 * Get the object a monitor is part of.
 *
 * Returns NULL if "mon" is NULL or the monitor is not part of an object
 * (which should only happen for Thread.sleep() in the current implementation).
 */
struct Object* dvmGetMonitorObject(Monitor* mon);

/*
 * Checks whether the object is held by the specified thread.
 */
bool dvmHoldsLock(struct Thread* thread, struct Object* obj);

/*
 * Debug.
 */
void dvmDumpMonitorInfo(const char* msg);

#endif /*_DALVIK_SYNC*/
