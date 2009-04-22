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

#include <ui/PixelFormat.h>
#include <pixelflinger/format.h>

namespace android {

ssize_t bytesPerPixel(PixelFormat format)
{
    PixelFormatInfo info;
    status_t err = getPixelFormatInfo(format, &info);
    return (err < 0) ? err : info.bytesPerPixel;
}

ssize_t bitsPerPixel(PixelFormat format)
{
    PixelFormatInfo info;
    status_t err = getPixelFormatInfo(format, &info);
    return (err < 0) ? err : info.bitsPerPixel;
}

status_t getPixelFormatInfo(PixelFormat format, PixelFormatInfo* info)
{
    if (format < 0)
        return BAD_VALUE;

    if (info->version != sizeof(PixelFormatInfo))
        return INVALID_OPERATION;

    size_t numEntries;
    const GGLFormat *i = gglGetPixelFormatTable(&numEntries) + format;
    bool valid = uint32_t(format) < numEntries;
    if (!valid) {
        return BAD_INDEX;
    }

    info->format = format;
    info->bytesPerPixel = i->size;
    info->bitsPerPixel  = i->bitsPerPixel;
    info->h_alpha       = i->ah;
    info->l_alpha       = i->al;
    info->h_red         = i->rh;
    info->l_red         = i->rl;
    info->h_green       = i->gh;
    info->l_green       = i->gl;
    info->h_blue        = i->bh;
    info->l_blue        = i->bl;
    return NO_ERROR;
}

}; // namespace android

