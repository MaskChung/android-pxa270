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

#ifndef ANDROID_DISPLAY_HARDWARE_H
#define ANDROID_DISPLAY_HARDWARE_H

#include <stdlib.h>

#include <ui/PixelFormat.h>
#include <ui/Region.h>

#include <GLES/egl.h>

#include "DisplayHardware/DisplayHardwareBase.h"

struct copybit_image_t;
struct copybit_t;

namespace android {

class EGLDisplaySurface;

class DisplayHardware : public DisplayHardwareBase
{
public:
    enum {
        COPY_BACK_EXTENSION     = 0x00000001,
        DIRECT_TEXTURE          = 0x00000002,
        SWAP_RECTANGLE_EXTENSION= 0x00000004,
        COPY_BITS_EXTENSION     = 0x00000008,
        NPOT_EXTENSION          = 0x00000100,
        DRAW_TEXTURE_EXTENSION  = 0x00000200,
        BUFFER_PRESERVED        = 0x00010000,
        UPDATE_ON_DEMAND        = 0x00020000,   // video driver feature
        SLOW_CONFIG             = 0x00040000,   // software
    };

    DisplayHardware(
            const sp<SurfaceFlinger>& flinger,
            uint32_t displayIndex);

    ~DisplayHardware();

    void releaseScreen() const;
    void acquireScreen() const;

    // Flip the front and back buffers if the back buffer is "dirty".  Might
    // be instantaneous, might involve copying the frame buffer around.
    void flip(const Region& dirty) const;

    float       getDpiX() const;
    float       getDpiY() const;
    float       getRefreshRate() const;
    int         getWidth() const;
    int         getHeight() const;
    PixelFormat getFormat() const;
    uint32_t    getFlags() const;
    void        makeCurrent() const;

    uint32_t getPageFlipCount() const;
    void getDisplaySurface(copybit_image_t* img) const;
    void getDisplaySurface(GGLSurface* fb) const;
    EGLDisplay getEGLDisplay() const { return mDisplay; }
    copybit_t* getBlitEngine() const { return mBlitEngine; }
    
    Rect bounds() const {
        return Rect(mWidth, mHeight);
    }

private:
    void init(uint32_t displayIndex) __attribute__((noinline));
    void fini() __attribute__((noinline));

    EGLDisplay      mDisplay;
    EGLSurface      mSurface;
    EGLContext      mContext;
    EGLConfig       mConfig;
    float           mDpiX;
    float           mDpiY;
    float           mRefreshRate;
    int             mWidth;
    int             mHeight;
    PixelFormat     mFormat;
    uint32_t        mFlags;
    mutable Region  mDirty;
    sp<EGLDisplaySurface> mDisplaySurface;
    copybit_t*      mBlitEngine;
};

}; // namespace android

#endif // ANDROID_DISPLAY_HARDWARE_H
