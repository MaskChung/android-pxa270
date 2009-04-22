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

#define LOG_TAG "SurfaceFlinger"

#include <stdlib.h>
#include <stdint.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/Log.h>

#include "BlurFilter.h"
#include "LayerBlur.h"
#include "SurfaceFlinger.h"
#include "DisplayHardware/DisplayHardware.h"

namespace android {
// ---------------------------------------------------------------------------

const uint32_t LayerBlur::typeInfo = LayerBaseClient::typeInfo | 8;
const char* const LayerBlur::typeID = "LayerBlur";

// ---------------------------------------------------------------------------

LayerBlur::LayerBlur(SurfaceFlinger* flinger, DisplayID display,
        Client* client, int32_t i)
     : LayerBaseClient(flinger, display, client, i), mCacheDirty(true),
     mRefreshCache(true), mCacheAge(0), mTextureName(-1U)
{
}

LayerBlur::~LayerBlur()
{
    if (mTextureName != -1U) {
        //glDeleteTextures(1, &mTextureName);
        deletedTextures.add(mTextureName);
    }
}

void LayerBlur::setVisibleRegion(const Region& visibleRegion)
{
    LayerBaseClient::setVisibleRegion(visibleRegion);
    if (visibleRegionScreen.isEmpty()) {
        if (mTextureName != -1U) {
            // We're not visible, free the texture up.
            glBindTexture(GL_TEXTURE_2D, 0);
            glDeleteTextures(1, &mTextureName);
            mTextureName = -1U;
        }
    }
}

uint32_t LayerBlur::doTransaction(uint32_t flags)
{
    // we're doing a transaction, refresh the cache!
    if (!mFlinger->isFrozen()) {
        mRefreshCache = true;
        mCacheDirty = true;
        flags |= eVisibleRegion;
        this->invalidate = true;
    }
    return LayerBase::doTransaction(flags);    
}

void LayerBlur::unlockPageFlip(const Transform& planeTransform, Region& outDirtyRegion)
{
    // this code-path must be as tight as possible, it's called each time
    // the screen is composited.
    if (UNLIKELY(!visibleRegionScreen.isEmpty())) {
        // if anything visible below us is invalidated, the cache becomes dirty
        if (!mCacheDirty && 
                !visibleRegionScreen.intersect(outDirtyRegion).isEmpty()) {
            mCacheDirty = true;
        }
        if (mCacheDirty) {
            if (!mFlinger->isFrozen()) {
                // update everything below us that is visible
                outDirtyRegion.orSelf(visibleRegionScreen);
                nsecs_t now = systemTime();
                if ((now - mCacheAge) >= ms2ns(500)) {
                    mCacheAge = now;
                    mRefreshCache = true;
                    mCacheDirty = false;
                } else {
                    if (!mAutoRefreshPending) {
                        mFlinger->signalDelayedEvent(ms2ns(500));
                        mAutoRefreshPending = true;
                    }
                }
            }
        }
    }
    LayerBase::unlockPageFlip(planeTransform, outDirtyRegion);
}

void LayerBlur::onDraw(const Region& clip) const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    const uint32_t fbHeight = hw.getHeight();
    int x = mTransformedBounds.left;
    int y = mTransformedBounds.top;
    int w = mTransformedBounds.width();
    int h = mTransformedBounds.height();
    GLint X = x;
    GLint Y = fbHeight - (y + h);
    if (X < 0) {
        w += X;
        X = 0;
    }
    if (Y < 0) {
        h += Y;
        Y = 0;
    }
    if (w<0 || h<0) {
        // we're outside of the framebuffer
        return;
    }

    if (mTextureName == -1U) {
        // create the texture name the first time
        // can't do that in the ctor, because it runs in another thread.
        glGenTextures(1, &mTextureName);
    }

    Region::iterator iterator(clip);
    if (iterator) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, mTextureName);
    
        if (mRefreshCache) {
            mRefreshCache = false;
            mAutoRefreshPending = false;
            
            uint16_t* const pixels = (uint16_t*)malloc(w*h*2);

            // this reads the frame-buffer, so a h/w GL would have to
            // finish() its rendering first. we don't want to do that
            // too often.
            glReadPixels(X, Y, w, h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, pixels);
            
            // blur that texture.
            GGLSurface bl;
            bl.version = sizeof(GGLSurface);
            bl.width = w;
            bl.height = h;
            bl.stride = w;
            bl.format = GGL_PIXEL_FORMAT_RGB_565;
            bl.data = (GGLubyte*)pixels;            
            blurFilter(&bl, 8, 2);
            
            // NOTE: this works only because we have POT. we'd have to round the
            // texture size up, otherwise.
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, w, h, 0,
                    GL_RGB, GL_UNSIGNED_SHORT_5_6_5, pixels);

            free((void*)pixels);
        }
        
        const State& s = drawingState();
        if (UNLIKELY(s.alpha < 0xFF)) {
            const GGLfixed alpha = (s.alpha << 16)/255;
            glColor4x(0, 0, 0, alpha);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
        } else {
            glDisable(GL_BLEND);
        }

        glDisable(GL_DITHER);
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        if (UNLIKELY(transformed()
                || !(mFlags & DisplayHardware::DRAW_TEXTURE_EXTENSION) )) {
            // This is a very rare scenario.
            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            glScalef(1.0f/w, -1.0f/h, 1);
            glTranslatef(-x, -y, 0);
            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glVertexPointer(2, GL_FIXED, 0, mVertices);
            glTexCoordPointer(2, GL_FIXED, 0, mVertices);
            Rect r;
            while (iterator.iterate(&r)) {
                const GLint sy = fbHeight - (r.top + r.height());
                glScissor(r.left, sy, r.width(), r.height());
                glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
            }       
        } else {
            Region::iterator iterator(clip);
            if (iterator) {
                // NOTE: this is marginally faster with the software gl, because
                // glReadPixels() reads the fb bottom-to-top, however we'll
                // skip all the jaccobian computations.
                Rect r;
                GLint crop[4] = { 0, 0, w, h };
                glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);
                y = fbHeight - (y + h);
                while (iterator.iterate(&r)) {
                    const GLint sy = fbHeight - (r.top + r.height());
                    glScissor(r.left, sy, r.width(), r.height());
                    glDrawTexiOES(x, y, 0, w, h);
                }
            }
        }
    }

    glDisableClientState(GL_TEXTURE_COORD_ARRAY);
}

// ---------------------------------------------------------------------------

}; // namespace android
