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

#ifndef ANDROID_VRAM_HEAP_H
#define ANDROID_VRAM_HEAP_H

#include <stdint.h>
#include <sys/types.h>
#include <utils/MemoryDealer.h>

namespace android {

// ---------------------------------------------------------------------------

class PMemHeap;
class MemoryHeapPmem;

// ---------------------------------------------------------------------------

class SurfaceHeapManager  : public RefBase
{
public:
    SurfaceHeapManager(size_t clientHeapSize);
    virtual ~SurfaceHeapManager();
    virtual void onFirstRef();
    sp<MemoryDealer> createHeap(int type);
    
    // used for debugging only...
    sp<SimpleBestFitAllocator> getAllocator(int type) const;

private:
    sp<PMemHeap> getHeap(int type) const;

    mutable Mutex   mLock;
    size_t          mClientHeapSize;
    sp<PMemHeap>    mPMemHeap;
    static int global_pmem_heap;
};

// ---------------------------------------------------------------------------

class PMemHeapInterface : public MemoryHeapBase
{
public:
    PMemHeapInterface(int fd, size_t size);
    PMemHeapInterface(const char* device, size_t size = 0);
    PMemHeapInterface(size_t size, uint32_t flags = 0, char const * name = NULL);
    virtual ~PMemHeapInterface();
    virtual sp<MemoryHeapPmem> createClientHeap() = 0;
};

// ---------------------------------------------------------------------------

class PMemHeap : public PMemHeapInterface
{
public:
                PMemHeap(const char* const vram,
                        size_t size=0, size_t reserved=0);
    virtual     ~PMemHeap();
    
    virtual const sp<SimpleBestFitAllocator>& getAllocator() const {
        return mAllocator; 
    }
    virtual sp<MemoryHeapPmem> createClientHeap();
    
private:
    sp<SimpleBestFitAllocator>  mAllocator;
};

// ---------------------------------------------------------------------------
}; // namespace android

#endif // ANDROID_VRAM_HEAP_H
