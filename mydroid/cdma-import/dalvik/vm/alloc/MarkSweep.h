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
#ifndef _DALVIK_ALLOC_MARK_SWEEP
#define _DALVIK_ALLOC_MARK_SWEEP

#include "alloc/HeapBitmap.h"
#include "alloc/HeapSource.h"

/* Downward-growing stack for better cache read behavior.
 */
typedef struct {
    /* Lowest address (inclusive)
     */
    const Object **limit;

    /* Current top of the stack (inclusive)
     */
    const Object **top;

    /* Highest address (exclusive)
     */
    const Object **base;
} GcMarkStack;

/* This is declared publicly so that it can be included in gDvm.gcHeap.
 */
typedef struct {
    HeapBitmap bitmaps[HEAP_SOURCE_MAX_HEAP_COUNT];
    size_t numBitmaps;
    GcMarkStack stack;
    const void *finger;   // only used while scanning/recursing.
} GcMarkContext;

enum RefType {
    REF_SOFT,
    REF_WEAK,
    REF_PHANTOM,
    REF_WEAKGLOBAL
};

bool dvmHeapBeginMarkStep(void);
void dvmHeapMarkRootSet(void);
void dvmHeapScanMarkedObjects(void);
void dvmHeapHandleReferences(Object *refListHead, enum RefType refType);
void dvmHeapScheduleFinalizations(void);
void dvmHeapFinishMarkStep(void);

void dvmHeapSweepUnmarkedObjects(int *numFreed, size_t *sizeFreed);

#endif  // _DALVIK_ALLOC_MARK_SWEEP
