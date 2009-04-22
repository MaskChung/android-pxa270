/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#include <stdint.h>
#include <sys/types.h>

#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <sched.h>
#include <fcntl.h>
#include <sys/ioctl.h>

#define LOG_TAG "AudioHardware"
#include <utils/Log.h>
#include <utils/String8.h>

#include "AudioHardwareGeneric.h"

namespace android {

// ----------------------------------------------------------------------------

static char const * const kAudioDeviceName = "/dev/eac";

// ----------------------------------------------------------------------------

AudioHardwareGeneric::AudioHardwareGeneric()
    : mOutput(0), mInput(0),  mFd(-1), mMicMute(false)
{
    mFd = ::open(kAudioDeviceName, O_RDWR);
}

AudioHardwareGeneric::~AudioHardwareGeneric()
{
    if (mFd >= 0) ::close(mFd);
    delete mOutput;
    delete mInput;
}

status_t AudioHardwareGeneric::initCheck()
{
    if (mFd >= 0) {
        if (::access(kAudioDeviceName, O_RDWR) == NO_ERROR)
            return NO_ERROR;
    }
    return NO_INIT;
}

status_t AudioHardwareGeneric::standby()
{
    // Implement: audio hardware to standby mode
    return NO_ERROR;
}

AudioStreamOut* AudioHardwareGeneric::openOutputStream(
        int format, int channelCount, uint32_t sampleRate)
{
    AutoMutex lock(mLock);

    // only one output stream allowed
    if (mOutput) return 0;

    // create new output stream
    AudioStreamOutGeneric* out = new AudioStreamOutGeneric();
    if (out->set(this, mFd, format, channelCount, sampleRate) == NO_ERROR) {
        mOutput = out;
    } else {
        delete out;
    }
    return mOutput;
}

void AudioHardwareGeneric::closeOutputStream(AudioStreamOutGeneric* out) {
    if (out == mOutput) mOutput = 0;
}

AudioStreamIn* AudioHardwareGeneric::openInputStream(
        int format, int channelCount, uint32_t sampleRate)
{
    AutoMutex lock(mLock);

    // only one input stream allowed
    if (mInput) return 0;

    // create new output stream
    AudioStreamInGeneric* in = new AudioStreamInGeneric();
    if (in->set(this, mFd, format, channelCount, sampleRate) == NO_ERROR) {
        mInput = in;
    } else {
        delete in;
    }
    return mInput;
}

void AudioHardwareGeneric::closeInputStream(AudioStreamInGeneric* in) {
    if (in == mInput) mInput = 0;
}

status_t AudioHardwareGeneric::setVoiceVolume(float v)
{
    // Implement: set voice volume
    return NO_ERROR;
}

status_t AudioHardwareGeneric::setMasterVolume(float v)
{
    // Implement: set master volume
    // return error - software mixer will handle it
    return INVALID_OPERATION;
}

status_t AudioHardwareGeneric::setMicMute(bool state)
{
    mMicMute = state;
    return NO_ERROR;
}

status_t AudioHardwareGeneric::getMicMute(bool* state)
{
    *state = mMicMute;
    return NO_ERROR;
}

status_t AudioHardwareGeneric::dumpInternals(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    result.append("AudioHardwareGeneric::dumpInternals\n");
    snprintf(buffer, SIZE, "\tmFd: %d mMicMute: %s\n",  mFd, mMicMute? "true": "false");
    result.append(buffer);
    ::write(fd, result.string(), result.size());
    return NO_ERROR;
}

status_t AudioHardwareGeneric::dump(int fd, const Vector<String16>& args)
{
    dumpInternals(fd, args);
    if (mInput) {
        mInput->dump(fd, args);
    }
    if (mOutput) {
        mOutput->dump(fd, args);
    }
    return NO_ERROR;
}

// ----------------------------------------------------------------------------

status_t AudioStreamOutGeneric::set(
        AudioHardwareGeneric *hw,
        int fd,
        int format,
        int channels,
        uint32_t rate)
{
    // fix up defaults
    if (format == 0) format = AudioSystem::PCM_16_BIT;
    if (channels == 0) channels = channelCount();
    if (rate == 0) rate = sampleRate();

    // check values
    if ((format != AudioSystem::PCM_16_BIT) ||
            (channels != channelCount()) ||
            (rate != sampleRate()))
        return BAD_VALUE;

    mAudioHardware = hw;
    mFd = fd;
    return NO_ERROR;
}

AudioStreamOutGeneric::~AudioStreamOutGeneric()
{
    if (mAudioHardware)
        mAudioHardware->closeOutputStream(this);
}

ssize_t AudioStreamOutGeneric::write(const void* buffer, size_t bytes)
{
    Mutex::Autolock _l(mLock);
    return ssize_t(::write(mFd, buffer, bytes));
}

status_t AudioStreamOutGeneric::dump(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    snprintf(buffer, SIZE, "AudioStreamOutGeneric::dump\n");
    result.append(buffer);
    snprintf(buffer, SIZE, "\tsample rate: %d\n", sampleRate());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tbuffer size: %d\n", bufferSize());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tchannel count: %d\n", channelCount());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tformat: %d\n", format());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tmAudioHardware: %p\n", mAudioHardware);
    result.append(buffer);
    snprintf(buffer, SIZE, "\tmFd: %d\n", mFd);
    result.append(buffer);
    ::write(fd, result.string(), result.size());
    return NO_ERROR;
}

// ----------------------------------------------------------------------------

// record functions
status_t AudioStreamInGeneric::set(
        AudioHardwareGeneric *hw,
        int fd,
        int format,
        int channels,
        uint32_t rate)
{
    // FIXME: remove logging
    LOGD("AudioStreamInGeneric::set(%p, %d, %d, %d, %u)", hw, fd, format, channels, rate);
    // check values
    if ((format != AudioSystem::PCM_16_BIT) ||
            (channels != channelCount()) ||
            (rate != sampleRate())) {
        LOGE("Error opening input channel");
        return BAD_VALUE;
    }

    mAudioHardware = hw;
    mFd = fd;
    return NO_ERROR;
}

AudioStreamInGeneric::~AudioStreamInGeneric()
{
    // FIXME: remove logging
    LOGD("AudioStreamInGeneric destructor");
    if (mAudioHardware)
        mAudioHardware->closeInputStream(this);
}

ssize_t AudioStreamInGeneric::read(void* buffer, ssize_t bytes)
{
    // FIXME: remove logging
    LOGD("AudioStreamInGeneric::read(%p, %d) from fd %d", buffer, bytes, mFd);
    AutoMutex lock(mLock);
    if (mFd < 0) {
        LOGE("Attempt to read from unopened device");
        return NO_INIT;
    }
    return ::read(mFd, buffer, bytes);
}

status_t AudioStreamInGeneric::dump(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    snprintf(buffer, SIZE, "AudioStreamInGeneric::dump\n");
    result.append(buffer);
    snprintf(buffer, SIZE, "\tsample rate: %d\n", sampleRate());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tbuffer size: %d\n", bufferSize());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tchannel count: %d\n", channelCount());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tformat: %d\n", format());
    result.append(buffer);
    snprintf(buffer, SIZE, "\tmAudioHardware: %p\n", mAudioHardware);
    result.append(buffer);
    snprintf(buffer, SIZE, "\tmFd: %d\n", mFd);
    result.append(buffer);
    ::write(fd, result.string(), result.size());
    return NO_ERROR;
}

// ----------------------------------------------------------------------------

}; // namespace android
