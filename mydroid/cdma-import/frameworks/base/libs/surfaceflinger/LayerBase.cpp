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

#include "clz.h"
#include "LayerBase.h"
#include "LayerBlur.h"
#include "SurfaceFlinger.h"
#include "DisplayHardware/DisplayHardware.h"


// We don't honor the premultipliad alpha flags, which means that
// premultiplied surface may be composed using a non-premultiplied
// equation. We do this because it may be a lot faster on some hardware
// The correct value is HONOR_PREMULTIPLIED_ALPHA = 1
#define HONOR_PREMULTIPLIED_ALPHA   0

namespace android {

// ---------------------------------------------------------------------------

const uint32_t LayerBase::typeInfo = 1;
const char* const LayerBase::typeID = "LayerBase";

const uint32_t LayerBaseClient::typeInfo = LayerBase::typeInfo | 2;
const char* const LayerBaseClient::typeID = "LayerBaseClient";

// ---------------------------------------------------------------------------

Vector<GLuint> LayerBase::deletedTextures; 

int32_t LayerBase::sIdentity = 0;

LayerBase::LayerBase(SurfaceFlinger* flinger, DisplayID display)
    : dpy(display), invalidate(false),
      mFlinger(flinger),
      mTransformed(false),
      mOrientation(0),
      mCanUseCopyBit(false),
      mTransactionFlags(0),
      mPremultipliedAlpha(true),
      mIdentity(uint32_t(android_atomic_inc(&sIdentity)))
{
    const DisplayHardware& hw(flinger->graphicPlane(0).displayHardware());
    mFlags = hw.getFlags();
}

LayerBase::~LayerBase()
{
}

const GraphicPlane& LayerBase::graphicPlane(int dpy) const
{ 
    return mFlinger->graphicPlane(dpy);
}

GraphicPlane& LayerBase::graphicPlane(int dpy)
{
    return mFlinger->graphicPlane(dpy); 
}

void LayerBase::initStates(uint32_t w, uint32_t h, uint32_t flags)
{
    uint32_t layerFlags = 0;
    if (flags & ISurfaceComposer::eHidden)
        layerFlags = ISurfaceComposer::eLayerHidden;

    if (flags & ISurfaceComposer::eNonPremultiplied)
        mPremultipliedAlpha = false;

    mCurrentState.z         = 0;
    mCurrentState.w         = w;
    mCurrentState.h         = h;
    mCurrentState.alpha     = 0xFF;
    mCurrentState.flags     = layerFlags;
    mCurrentState.sequence  = 0;
    mCurrentState.transform.set(0, 0);

    // drawing state & current state are identical
    mDrawingState = mCurrentState;
}

void LayerBase::commitTransaction(bool skipSize) {
    const uint32_t w = mDrawingState.w;
    const uint32_t h = mDrawingState.h;
    mDrawingState = mCurrentState;
    if (skipSize) {
        mDrawingState.w = w;
        mDrawingState.h = h;
    }
}
bool LayerBase::requestTransaction() {
    int32_t old = setTransactionFlags(eTransactionNeeded);
    return ((old & eTransactionNeeded) == 0);
}
uint32_t LayerBase::getTransactionFlags(uint32_t flags) {
    return android_atomic_and(~flags, &mTransactionFlags) & flags;
}
uint32_t LayerBase::setTransactionFlags(uint32_t flags) {
    return android_atomic_or(flags, &mTransactionFlags);
}

void LayerBase::setSizeChanged(uint32_t w, uint32_t h) {
}

bool LayerBase::setPosition(int32_t x, int32_t y) {
    if (mCurrentState.transform.tx() == x && mCurrentState.transform.ty() == y)
        return false;
    mCurrentState.sequence++;
    mCurrentState.transform.set(x, y);
    requestTransaction();
    return true;
}
bool LayerBase::setLayer(uint32_t z) {
    if (mCurrentState.z == z)
        return false;
    mCurrentState.sequence++;
    mCurrentState.z = z;
    requestTransaction();
    return true;
}
bool LayerBase::setSize(uint32_t w, uint32_t h) {
    if (mCurrentState.w == w && mCurrentState.h == h)
        return false;
    setSizeChanged(w, h);
    mCurrentState.w = w;
    mCurrentState.h = h;
    requestTransaction();
    return true;
}
bool LayerBase::setAlpha(uint8_t alpha) {
    if (mCurrentState.alpha == alpha)
        return false;
    mCurrentState.sequence++;
    mCurrentState.alpha = alpha;
    requestTransaction();
    return true;
}
bool LayerBase::setMatrix(const layer_state_t::matrix22_t& matrix) {
    // TODO: check the matrix has changed
    mCurrentState.sequence++;
    mCurrentState.transform.set(
            matrix.dsdx, matrix.dsdy, matrix.dtdx, matrix.dtdy);
    requestTransaction();
    return true;
}
bool LayerBase::setTransparentRegionHint(const Region& transparent) {
    // TODO: check the region has changed
    mCurrentState.sequence++;
    mCurrentState.transparentRegion = transparent;
    requestTransaction();
    return true;
}
bool LayerBase::setFlags(uint8_t flags, uint8_t mask) {
    const uint32_t newFlags = (mCurrentState.flags & ~mask) | (flags & mask);
    if (mCurrentState.flags == newFlags)
        return false;
    mCurrentState.sequence++;
    mCurrentState.flags = newFlags;
    requestTransaction();
    return true;
}

Rect LayerBase::visibleBounds() const
{
    return mTransformedBounds;
}      

void LayerBase::setVisibleRegion(const Region& visibleRegion) {
    // always called from main thread
    visibleRegionScreen = visibleRegion;
}

void LayerBase::setCoveredRegion(const Region& coveredRegion) {
    // always called from main thread
    coveredRegionScreen = coveredRegion;
}

uint32_t LayerBase::doTransaction(uint32_t flags)
{
    const Layer::State& front(drawingState());
    const Layer::State& temp(currentState());

    if (temp.sequence != front.sequence) {
        // invalidate and recompute the visible regions if needed
        flags |= eVisibleRegion;
        this->invalidate = true;
    }
    
    // Commit the transaction
    commitTransaction(flags & eRestartTransaction);
    return flags;
}

Point LayerBase::getPhysicalSize() const
{
    const Layer::State& front(drawingState());
    return Point(front.w, front.h);
}

void LayerBase::validateVisibility(const Transform& planeTransform)
{
    const Layer::State& s(drawingState());
    const Transform tr(planeTransform * s.transform);
    const bool transformed = tr.transformed();
   
    const Point size(getPhysicalSize());
    uint32_t w = size.x;
    uint32_t h = size.y;    
    tr.transform(mVertices[0], 0, 0);
    tr.transform(mVertices[1], 0, h);
    tr.transform(mVertices[2], w, h);
    tr.transform(mVertices[3], w, 0);
    if (UNLIKELY(transformed)) {
        // NOTE: here we could also punt if we have too many rectangles
        // in the transparent region
        if (tr.preserveRects()) {
            // transform the transparent region
            transparentRegionScreen = tr.transform(s.transparentRegion);
        } else {
            // transformation too complex, can't do the transparent region
            // optimization.
            transparentRegionScreen.clear();
        }
    } else {
        transparentRegionScreen = s.transparentRegion;
    }

    // cache a few things...
    mOrientation = tr.getOrientation();
    mTransformedBounds = tr.makeBounds(w, h);
    mTransformed = transformed;
    mLeft = tr.tx();
    mTop  = tr.ty();

    // see if we can/should use 2D h/w with the new configuration
    mCanUseCopyBit = false;
    copybit_t* copybit = mFlinger->getBlitEngine();
    if (copybit) { 
        const int step = copybit->get(copybit, COPYBIT_ROTATION_STEP_DEG);
        const int scaleBits = copybit->get(copybit, COPYBIT_SCALING_FRAC_BITS);
        mCanUseCopyBit = true;
        if ((mOrientation < 0) && (step > 1)) {
            // arbitrary orientations not supported
            mCanUseCopyBit = false;
        } else if ((mOrientation > 0) && (step > 90)) {
            // 90 deg rotations not supported
            mCanUseCopyBit = false;
        } else if ((tr.getType() & SkMatrix::kScale_Mask) && (scaleBits < 12)) { 
            // arbitrary scaling not supported
            mCanUseCopyBit = false;
        }
#if HONOR_PREMULTIPLIED_ALPHA 
        else if (needsBlending() && mPremultipliedAlpha) {
            // pre-multiplied alpha not supported
            mCanUseCopyBit = false;
        }
#endif
        else {
            // here, we determined we can use copybit
            if (tr.getType() & SkMatrix::kScale_Mask) {
                // and we have scaling
                if (!transparentRegionScreen.isRect()) {
                    // we punt because blending is cheap (h/w) and the region is
                    // complex, which may causes artifacts when copying
                    // scaled content
                    transparentRegionScreen.clear();
                }
            }
        }
    }
}

void LayerBase::lockPageFlip(bool& recomputeVisibleRegions)
{
}

void LayerBase::unlockPageFlip(
        const Transform& planeTransform, Region& outDirtyRegion)
{
}

void LayerBase::finishPageFlip()
{
}

void LayerBase::drawRegion(const Region& reg) const
{
    Region::iterator iterator(reg);
    if (iterator) {
        Rect r;
        const DisplayHardware& hw(graphicPlane(0).displayHardware());
        const int32_t fbWidth  = hw.getWidth();
        const int32_t fbHeight = hw.getHeight();
        const GLshort vertices[][2] = { { 0, 0 }, { fbWidth, 0 }, 
                { fbWidth, fbHeight }, { 0, fbHeight }  };
        glVertexPointer(2, GL_SHORT, 0, vertices);
        while (iterator.iterate(&r)) {
            const GLint sy = fbHeight - (r.top + r.height());
            glScissor(r.left, sy, r.width(), r.height());
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
        }
    }
}

void LayerBase::draw(const Region& inClip) const
{
    // invalidate the region we'll update
    Region clip(inClip);  // copy-on-write, so no-op most of the time

    // Remove the transparent area from the clipping region
    const State& s = drawingState();
    if (LIKELY(!s.transparentRegion.isEmpty())) {
        clip.subtract(transparentRegionScreen);
        if (clip.isEmpty()) {
            // usually this won't happen because this should be taken care of
            // by SurfaceFlinger::computeVisibleRegions()
            return;
        }        
    }
    onDraw(clip);

    /*
    glDisable(GL_TEXTURE_2D);
    glDisable(GL_DITHER);
    glEnable(GL_BLEND);
    glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
    glColor4x(0, 0x8000, 0, 0x10000);
    drawRegion(transparentRegionScreen);
    glDisable(GL_BLEND);
    */
}

GLuint LayerBase::createTexture() const
{
    GLuint textureName = -1;
    glGenTextures(1, &textureName);
    glBindTexture(GL_TEXTURE_2D, textureName);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (mFlags & DisplayHardware::SLOW_CONFIG) {
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    } else {
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    }
    return textureName;
}

void LayerBase::clearWithOpenGL(const Region& clip) const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    const uint32_t fbHeight = hw.getHeight();
    glColor4x(0,0,0,0);
    glDisable(GL_TEXTURE_2D);
    glDisable(GL_BLEND);
    glDisable(GL_DITHER);
    Rect r;
    Region::iterator iterator(clip);
    if (iterator) {
        glVertexPointer(2, GL_FIXED, 0, mVertices);
        while (iterator.iterate(&r)) {
            const GLint sy = fbHeight - (r.top + r.height());
            glScissor(r.left, sy, r.width(), r.height());
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
        }
    }
}

void LayerBase::drawWithOpenGL(const Region& clip,
        GLint textureName, const GGLSurface& t) const
{
    const DisplayHardware& hw(graphicPlane(0).displayHardware());
    const uint32_t fbHeight = hw.getHeight();
    const State& s(drawingState());

    // bind our texture
    validateTexture(textureName);
    glEnable(GL_TEXTURE_2D);

    // Dithering...
    if (s.flags & ISurfaceComposer::eLayerDither) {
        glEnable(GL_DITHER);
    } else {
        glDisable(GL_DITHER);
    }

    if (UNLIKELY(s.alpha < 0xFF)) {
        // We have an alpha-modulation. We need to modulate all
        // texture components by alpha because we're always using 
        // premultiplied alpha.
        
        // If the texture doesn't have an alpha channel we can
        // use REPLACE and switch to non premultiplied-alpha
        // blending (SRCA/ONE_MINUS_SRCA).
        
        GLenum env, src;
        if (needsBlending()) {
            env = GL_MODULATE;
            src = mPremultipliedAlpha ? GL_ONE : GL_SRC_ALPHA;
        } else {
            env = GL_REPLACE;
            src = GL_SRC_ALPHA;
        }
        const GGLfixed alpha = (s.alpha << 16)/255;
        glColor4x(alpha, alpha, alpha, alpha);
        glEnable(GL_BLEND);
        glBlendFunc(src, GL_ONE_MINUS_SRC_ALPHA);
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, env);
    } else {
        glTexEnvx(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_REPLACE);
        if (needsBlending()) {
            GLenum src = mPremultipliedAlpha ? GL_ONE : GL_SRC_ALPHA;
            glEnable(GL_BLEND);
            glBlendFunc(src, GL_ONE_MINUS_SRC_ALPHA);
            glColor4x(0x10000, 0x10000, 0x10000, 0x10000);
        } else {
            glDisable(GL_BLEND);
        }
    }

    if (UNLIKELY(transformed()
            || !(mFlags & DisplayHardware::DRAW_TEXTURE_EXTENSION) )) 
    {
        //StopWatch watch("GL transformed");
        Region::iterator iterator(clip);
        if (iterator) {
            // always use high-quality filtering with fast configurations
            bool fast = !(mFlags & DisplayHardware::SLOW_CONFIG);
            if (!fast && s.flags & ISurfaceComposer::eLayerFilter) {
                glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            }            
            const GLfixed texCoords[4][2] = {
                    { 0,        0 },
                    { 0,        0x10000 },
                    { 0x10000,  0x10000 },
                    { 0x10000,  0 }
            };

            glMatrixMode(GL_TEXTURE);
            glLoadIdentity();
            if (!(mFlags & DisplayHardware::NPOT_EXTENSION)) {
                // find the smalest power-of-two that will accomodate our surface
                GLuint tw = 1 << (31 - clz(t.width));
                GLuint th = 1 << (31 - clz(t.height));
                if (tw < t.width)  tw <<= 1;
                if (th < t.height) th <<= 1;
                // this divide should be relatively fast because it's
                // a power-of-two (optimized path in libgcc)
                GLfloat ws = GLfloat(t.width) /tw;
                GLfloat hs = GLfloat(t.height)/th;
                glScalef(ws, hs, 1.0f);
            }

            glEnableClientState(GL_TEXTURE_COORD_ARRAY);
            glVertexPointer(2, GL_FIXED, 0, mVertices);
            glTexCoordPointer(2, GL_FIXED, 0, texCoords);

            Rect r;
            while (iterator.iterate(&r)) {
                const GLint sy = fbHeight - (r.top + r.height());
                glScissor(r.left, sy, r.width(), r.height());
                glDrawArrays(GL_TRIANGLE_FAN, 0, 4); 
            }

            if (!fast && s.flags & ISurfaceComposer::eLayerFilter) {
                glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameterx(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            }
            glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
    } else {
        Region::iterator iterator(clip);
        if (iterator) {
            Rect r;
            GLint crop[4] = { 0, t.height, t.width, -t.height };
            glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_CROP_RECT_OES, crop);
            int x = tx();
            int y = ty();
            y = fbHeight - (y + t.height);
            while (iterator.iterate(&r)) {
                const GLint sy = fbHeight - (r.top + r.height());
                glScissor(r.left, sy, r.width(), r.height());
                glDrawTexiOES(x, y, 0, t.width, t.height);
            }
        }
    }
}

void LayerBase::validateTexture(GLint textureName) const
{
    glBindTexture(GL_TEXTURE_2D, textureName);
    // TODO: reload the texture if needed
    // this is currently done in loadTexture() below
}

void LayerBase::loadTexture(const Region& dirty,
        GLint textureName, const GGLSurface& t,
        GLuint& textureWidth, GLuint& textureHeight) const
{
    // TODO: defer the actual texture reload until LayerBase::validateTexture
    // is called.

    uint32_t flags = mFlags;
    glBindTexture(GL_TEXTURE_2D, textureName);

    GLuint tw = t.width;
    GLuint th = t.height;

    /*
     * In OpenGL ES we can't specify a stride with glTexImage2D (however,
     * GL_UNPACK_ALIGNMENT is 4, which in essence allows a limited form of
     * stride).
     * So if the stride here isn't representable with GL_UNPACK_ALIGNMENT, we
     * need to do something reasonable (here creating a bigger texture).
     * 
     * extra pixels = (((stride - width) * pixelsize) / GL_UNPACK_ALIGNMENT);
     * 
     * This situation doesn't happen often, but some h/w have a limitation
     * for their framebuffer (eg: must be multiple of 8 pixels), and
     * we need to take that into account when using these buffers as
     * textures.
     *
     * This should never be a problem with POT textures
     */

    tw += (((t.stride - tw) * bytesPerPixel(t.format)) / 4);

    /*
     * round to POT if needed 
     */
    
    GLuint texture_w = tw;
    GLuint texture_h = th;
    if (!(flags & DisplayHardware::NPOT_EXTENSION)) {
        // find the smalest power-of-two that will accomodate our surface
        texture_w = 1 << (31 - clz(t.width));
        texture_h = 1 << (31 - clz(t.height));
        if (texture_w < t.width)  texture_w <<= 1;
        if (texture_h < t.height) texture_h <<= 1;
        if (texture_w != tw || texture_h != th) {
            // we can't use DIRECT_TEXTURE since we changed the size
            // of the texture
            flags &= ~DisplayHardware::DIRECT_TEXTURE;
        }
    }

    if (flags & DisplayHardware::DIRECT_TEXTURE) {
        // here we're guaranteed that texture_{w|h} == t{w|h}
        if (t.format == GGL_PIXEL_FORMAT_RGB_565) {
            glTexImage2D(GL_DIRECT_TEXTURE_2D_QUALCOMM, 0,
                    GL_RGB, tw, th, 0,
                    GL_RGB, GL_UNSIGNED_SHORT_5_6_5, t.data);
        } else if (t.format == GGL_PIXEL_FORMAT_RGBA_4444) {
            glTexImage2D(GL_DIRECT_TEXTURE_2D_QUALCOMM, 0,
                    GL_RGBA, tw, th, 0,
                    GL_RGBA, GL_UNSIGNED_SHORT_4_4_4_4, t.data);
        } else if (t.format == GGL_PIXEL_FORMAT_RGBA_8888) {
            glTexImage2D(GL_DIRECT_TEXTURE_2D_QUALCOMM, 0,
                    GL_RGBA, tw, th, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, t.data);
        } else {
            // oops, we don't handle this format, try the regular path
            goto regular;
        }
        textureWidth = tw;
        textureHeight = th;
    } else {
regular:
        Rect bounds(dirty.bounds());
        GLvoid* data = 0;
        if (texture_w!=textureWidth || texture_w!=textureHeight) {
            // texture size changed, we need to create a new one

            if (!textureWidth || !textureHeight) {
                // this is the first time, load the whole texture
                if (texture_w==tw && texture_h==th) {
                    // we can do it one pass
                    data = t.data;
                } else {
                    // we have to create the texture first because it
                    // doesn't match the size of the buffer
                    bounds.set(Rect(tw, th));
                }
            }

            if (t.format == GGL_PIXEL_FORMAT_RGB_565) {
                glTexImage2D(GL_TEXTURE_2D, 0,
                        GL_RGB, tw, th, 0,
                        GL_RGB, GL_UNSIGNED_SHORT_5_6_5, data);
            } else if (t.format == GGL_PIXEL_FORMAT_RGBA_4444) {
                glTexImage2D(GL_TEXTURE_2D, 0,
                        GL_RGBA, tw, th, 0,
                        GL_RGBA, GL_UNSIGNED_SHORT_4_4_4_4, data);
            } else if (t.format == GGL_PIXEL_FORMAT_RGBA_8888) {
                glTexImage2D(GL_TEXTURE_2D, 0,
                        GL_RGBA, tw, th, 0,
                        GL_RGBA, GL_UNSIGNED_BYTE, data);
            } else if ( t.format == GGL_PIXEL_FORMAT_YCbCr_422_SP ||
                        t.format == GGL_PIXEL_FORMAT_YCbCr_420_SP) {
                // just show the Y plane of YUV buffers
                data = t.data;
                glTexImage2D(GL_TEXTURE_2D, 0,
                        GL_LUMINANCE, tw, th, 0,
                        GL_LUMINANCE, GL_UNSIGNED_BYTE, data);
            }
            textureWidth = tw;
            textureHeight = th;
        }
        if (!data) {
            if (t.format == GGL_PIXEL_FORMAT_RGB_565) {
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, bounds.top, t.width, bounds.height(),
                        GL_RGB, GL_UNSIGNED_SHORT_5_6_5,
                        t.data + bounds.top*t.width*2);
            } else if (t.format == GGL_PIXEL_FORMAT_RGBA_4444) {
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, bounds.top, t.width, bounds.height(),
                        GL_RGBA, GL_UNSIGNED_SHORT_4_4_4_4,
                        t.data + bounds.top*t.width*2);
            } else if (t.format == GGL_PIXEL_FORMAT_RGBA_8888) {
                glTexSubImage2D(GL_TEXTURE_2D, 0,
                        0, bounds.top, t.width, bounds.height(),
                        GL_RGBA, GL_UNSIGNED_BYTE,
                        t.data + bounds.top*t.width*4);
            }
        }
    }
}

bool LayerBase::canUseCopybit() const
{
    return mCanUseCopyBit;
}

// ---------------------------------------------------------------------------

LayerBaseClient::LayerBaseClient(SurfaceFlinger* flinger, DisplayID display,
        Client* c, int32_t i)
    : LayerBase(flinger, display), client(c),
      lcblk( c ? &(c->ctrlblk->layers[i]) : 0 ),
      mIndex(i)
{
    if (client) {
        client->bindLayer(this, i);

        // Initialize this layer's control block
        memset(this->lcblk, 0, sizeof(layer_cblk_t));
        this->lcblk->identity = mIdentity;
        Region::writeEmpty(&(this->lcblk->region[0]), sizeof(flat_region_t));
        Region::writeEmpty(&(this->lcblk->region[1]), sizeof(flat_region_t));
    }
}

LayerBaseClient::~LayerBaseClient()
{
    if (client) {
        client->free(mIndex);
    }
}

int32_t LayerBaseClient::serverIndex() const {
    if (client) {
        return (client->cid<<16)|mIndex;
    }
    return 0xFFFF0000 | mIndex;
}

sp<LayerBaseClient::Surface> LayerBaseClient::getSurface() const
{
    return new Surface(clientIndex(), mIdentity);
}


// ---------------------------------------------------------------------------

}; // namespace android
