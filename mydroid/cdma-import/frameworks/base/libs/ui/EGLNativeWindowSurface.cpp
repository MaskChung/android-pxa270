/* 
**
** Copyright 2007 The Android Open Source Project
**
** Licensed under the Apache License Version 2.0(the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing software 
** distributed under the License is distributed on an "AS IS" BASIS 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#define LOG_TAG "EGLNativeWindowSurface"

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include <cutils/log.h>
#include <cutils/atomic.h>

#include <ui/SurfaceComposerClient.h>
#include <ui/DisplayInfo.h>
#include <ui/Rect.h>

#include <GLES/egl.h>

#include <pixelflinger/format.h>

#include <ui/EGLNativeWindowSurface.h>

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------

EGLNativeWindowSurface::EGLNativeWindowSurface(const sp<Surface>& surface)
    : EGLNativeSurface<EGLNativeWindowSurface>(),
    mSurface(surface), mConnected(false)
{
    egl_native_window_t::magic = 0x600913;
    egl_native_window_t::version = sizeof(egl_native_window_t);
    egl_native_window_t::ident = 0;
    egl_native_window_t::incRef = &EGLNativeWindowSurface::hook_incRef;
    egl_native_window_t::decRef = &EGLNativeWindowSurface::hook_decRef;
    egl_native_window_t::swapBuffers = &EGLNativeWindowSurface::hook_swapBuffers;
    egl_native_window_t::nextBuffer = &EGLNativeWindowSurface::hook_nextBuffer;
    egl_native_window_t::setSwapRectangle = &EGLNativeWindowSurface::hook_setSwapRectangle;
    egl_native_window_t::connect = &EGLNativeWindowSurface::hook_connect;
    egl_native_window_t::disconnect = &EGLNativeWindowSurface::hook_disconnect;
    
    DisplayInfo dinfo;
    SurfaceComposerClient::getDisplayInfo(0, &dinfo);
    egl_native_window_t::xdpi = dinfo.xdpi;
    egl_native_window_t::ydpi = dinfo.ydpi;
    egl_native_window_t::fps  = dinfo.fps;
    egl_native_window_t::flags= EGL_NATIVES_FLAG_DESTROY_BACKBUFFER;
}

EGLNativeWindowSurface::~EGLNativeWindowSurface()
{
    disconnect();
    mSurface.clear();
    magic = 0;
}

void EGLNativeWindowSurface::hook_incRef(NativeWindowType window)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    that->incStrong(that);
}

void EGLNativeWindowSurface::hook_decRef(NativeWindowType window)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    that->decStrong(that);
}

void EGLNativeWindowSurface::hook_connect(NativeWindowType window)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    that->connect();
}

void EGLNativeWindowSurface::hook_disconnect(NativeWindowType window)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    that->disconnect();
}

uint32_t EGLNativeWindowSurface::hook_swapBuffers(NativeWindowType window)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    return that->swapBuffers();
}

uint32_t EGLNativeWindowSurface::hook_nextBuffer(NativeWindowType window)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    return that->nextBuffer();
}

void EGLNativeWindowSurface::hook_setSwapRectangle(NativeWindowType window, int l, int t, int w, int h)
{
    EGLNativeWindowSurface* that = static_cast<EGLNativeWindowSurface*>(window);
    that->setSwapRectangle(l, t, w, h);
}

void EGLNativeWindowSurface::setSwapRectangle(int l, int t, int w, int h)
{
    mSurface->setSwapRectangle(Rect(l, t, l+w, t+h));
}

uint32_t EGLNativeWindowSurface::swapBuffers()
{
    const int w = egl_native_window_t::width;
    const int h = egl_native_window_t::height;
    const sp<Surface>& surface(mSurface);
    Surface::SurfaceInfo info;
    surface->unlockAndPost();
    surface->lock(&info);
    // update the address of the buffer to draw to next
    egl_native_window_t::base   = intptr_t(info.base);
    egl_native_window_t::offset = intptr_t(info.bits) - intptr_t(info.base);
    
    // update size if it changed
    if (w != int(info.w) || h != int(info.h)) {
        egl_native_window_t::width  = info.w;
        egl_native_window_t::height = info.h;
        egl_native_window_t::stride = info.bpr / bytesPerPixel(info.format);
        egl_native_window_t::format = info.format;
        return EGL_NATIVES_FLAG_SIZE_CHANGED;
    }
    return 0;
}

uint32_t EGLNativeWindowSurface::nextBuffer()
{
    const sp<Surface>& surface(mSurface);
    Surface::SurfaceInfo info;
    surface->nextBuffer(&info);
    // update the address of the buffer to draw to next
    egl_native_window_t::base   = intptr_t(info.base);
    egl_native_window_t::offset = intptr_t(info.bits) - intptr_t(info.base);
    return 0;
}

void EGLNativeWindowSurface::connect()
{   
    if (!mConnected) {
        Surface::SurfaceInfo info;
        mSurface->lock(&info);
        mSurface->setSwapRectangle(Rect(info.w, info.h));
        mConnected = true;

        egl_native_window_t::width  = info.w;
        egl_native_window_t::height = info.h;
        egl_native_window_t::stride = info.bpr / bytesPerPixel(info.format);
        egl_native_window_t::format = info.format;
        egl_native_window_t::base   = intptr_t(info.base);
        egl_native_window_t::offset = intptr_t(info.bits) - intptr_t(info.base);
        egl_native_window_t::memory_type = mSurface->getMemoryType();
        egl_native_window_t::fd = 0;
    }
}

void EGLNativeWindowSurface::disconnect()
{
    if (mConnected) {
        mSurface->unlock();
        mConnected = false;
    }
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------
