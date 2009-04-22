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

#ifndef ANDROID_HARDWARE_ICAMERA_H
#define ANDROID_HARDWARE_ICAMERA_H

#include <utils/RefBase.h>
#include <utils/IInterface.h>
#include <utils/Parcel.h>

#include <ui/ISurface.h>
#include <utils/IMemory.h>
#include <utils/String8.h>

namespace android {

class ICamera: public IInterface
{
public:
    DECLARE_META_INTERFACE(Camera);

    virtual void            disconnect() = 0;

    // pass the buffered ISurface to the camera service
    virtual status_t        setPreviewDisplay(const sp<ISurface>& surface) = 0;
    
    // tell the service whether to callback with each preview frame
    virtual void            setHasFrameCallback(bool installed) = 0;

    // start preview mode, must call setPreviewDisplay first
    virtual status_t        startPreview() = 0;

    // stop preview mode
    virtual void            stopPreview() = 0;

    // auto focus
    virtual status_t        autoFocus() = 0;

    // take a picture
    virtual status_t        takePicture() = 0;

    // set preview/capture parameters - key/value pairs
    virtual status_t        setParameters(const String8& params) = 0;

    // get preview/capture parameters - key/value pairs
    virtual String8         getParameters() const = 0;
};

// ----------------------------------------------------------------------------

class BnCamera: public BnInterface<ICamera>
{
public:
    virtual status_t    onTransact( uint32_t code,
                                    const Parcel& data,
                                    Parcel* reply,
                                    uint32_t flags = 0);
};

}; // namespace android

#endif
