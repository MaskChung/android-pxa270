/*
 * Copyright (C) 2007 The Android Open Source Project
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

#ifndef ANDROID_LAYER_H
#define ANDROID_LAYER_H

#include <stdint.h>
#include <sys/types.h>

#include <ui/PixelFormat.h>

#include <private/ui/SharedState.h>
#include <private/ui/LayerState.h>

#include <pixelflinger/pixelflinger.h>

#include "LayerBitmap.h"
#include "LayerBase.h"
#include "Transform.h"

namespace android {

// ---------------------------------------------------------------------------

class Client;
class LayerBitmap;
class MemoryDealer;
class FreezeLock;

// ---------------------------------------------------------------------------

class Layer : public LayerBaseClient
{
public:    
    static const uint32_t typeInfo;
    static const char* const typeID;
    virtual char const* getTypeID() const { return typeID; }
    virtual uint32_t getTypeInfo() const { return typeInfo; }

                 Layer(SurfaceFlinger* flinger, DisplayID display,
                         Client* c, int32_t i);

        virtual ~Layer();

    inline PixelFormat pixelFormat() const {
        return frontBuffer().pixelFormat();
    }

    status_t setBuffers(    Client* client,
                            uint32_t w, uint32_t h,
                            PixelFormat format, uint32_t flags=0);

    virtual void onDraw(const Region& clip) const;
    virtual void initStates(uint32_t w, uint32_t h, uint32_t flags);
    virtual void setSizeChanged(uint32_t w, uint32_t h);
    virtual uint32_t doTransaction(uint32_t transactionFlags);
    virtual Point getPhysicalSize() const;
    virtual void lockPageFlip(bool& recomputeVisibleRegions);
    virtual void unlockPageFlip(const Transform& planeTransform, Region& outDirtyRegion);
    virtual void finishPageFlip();
    virtual bool needsBlending() const      { return mNeedsBlending; }
    virtual bool isSecure() const           { return mSecure; }
    virtual GLuint getTextureName() const   { return mTextureName; }
    virtual sp<Surface> getSurface() const;

    const LayerBitmap& getBuffer(int i) const { return mBuffers[i]; }
          LayerBitmap& getBuffer(int i)       { return mBuffers[i]; }

    // only for debugging
    const sp<FreezeLock>&  getFreezeLock() const { return mFreezeLock; }

private:
    inline const LayerBitmap&
            frontBuffer() const { return getBuffer(mFrontBufferIndex); }
    inline LayerBitmap&
            frontBuffer()       { return getBuffer(mFrontBufferIndex); }
    inline const LayerBitmap&
            backBuffer() const  { return getBuffer(1-mFrontBufferIndex); }
    inline LayerBitmap&
            backBuffer()        { return getBuffer(1-mFrontBufferIndex); }

    void reloadTexture(const Region& dirty);

    status_t resize(int32_t index, uint32_t w, uint32_t h, const char* what);
    Region post(uint32_t* oldState, bool& recomputeVisibleRegions);
    status_t reallocateBuffer(int32_t index, uint32_t w, uint32_t h);

    sp<Surface>             mSurface;

            bool            mSecure;
            LayerBitmap     mBuffers[2];
            int32_t         mFrontBufferIndex;
            bool            mNeedsBlending;
            bool            mResizeTransactionDone;
            Region          mPostedDirtyRegion;
            sp<FreezeLock>  mFreezeLock;
            
            GLuint          mTextureName;
            GLuint          mTextureWidth;
            GLuint          mTextureHeight;
};

// ---------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_LAYER_H
