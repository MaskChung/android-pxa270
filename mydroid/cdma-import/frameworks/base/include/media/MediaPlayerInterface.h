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

#ifndef ANDROID_MEDIAPLAYERINTERFACE_H
#define ANDROID_MEDIAPLAYERINTERFACE_H

#include <pthread.h>
#include <signal.h>

#ifdef __cplusplus

#include <ui/ISurface.h>
#include <utils/RefBase.h>

#include <media/mediaplayer.h>

namespace android {

enum player_type {
    PV_PLAYER = 1,
    SONIVOX_PLAYER = 2,
    VORBIS_PLAYER = 3
};

#define DEFAULT_AUDIOSINK_BUFFERCOUNT 4

// callback mechanism for passing messages to MediaPlayer object
typedef void (*notify_callback_f)(void* cookie, int msg, int ext1, int ext2);

// abstract base class - use MediaPlayerInterface
class MediaPlayerBase : public RefBase
{
public:

    // AudioSink: abstraction layer for audio output
    class AudioSink : public RefBase {
    public:
        virtual             ~AudioSink() {}
        virtual bool        ready() const = 0; // audio output is open and ready
        virtual bool        realtime() const = 0; // audio output is real-time output
        virtual ssize_t     bufferSize() const = 0;
        virtual ssize_t     frameCount() const = 0;
        virtual ssize_t     channelCount() const = 0;
        virtual ssize_t     frameSize() const = 0;
        virtual uint32_t    latency() const = 0;
        virtual float       msecsPerFrame() const = 0;
        virtual status_t    open(uint32_t sampleRate, int channelCount, int bufferCount=DEFAULT_AUDIOSINK_BUFFERCOUNT) = 0;
        virtual void        start() = 0;
        virtual ssize_t     write(const void* buffer, size_t size) = 0;
        virtual void        stop() = 0;
        virtual void        flush() = 0;
        virtual void        pause() = 0;
        virtual void        close() = 0;
    };

                        MediaPlayerBase() : mCookie(0), mNotify(0) {}
    virtual             ~MediaPlayerBase() {}
    virtual status_t    initCheck() = 0;
    virtual bool        hardwareOutput() = 0;
    virtual status_t    setSigBusHandlerStructTLSKey(pthread_key_t key) { return 0; }
    virtual status_t    setDataSource(const char *url) = 0;
    virtual status_t    setDataSource(int fd, int64_t offset, int64_t length) = 0;
    virtual status_t    setVideoSurface(const sp<ISurface>& surface) = 0;
    virtual status_t    prepare() = 0;
    virtual status_t    prepareAsync() = 0;
    virtual status_t    start() = 0;
    virtual status_t    stop() = 0;
    virtual status_t    pause() = 0;
    virtual bool        isPlaying() = 0;
    virtual status_t    getVideoWidth(int *w) {return 0;}
    virtual status_t    getVideoHeight(int *h) {return 0;}
    virtual status_t    seekTo(int msec) = 0;
    virtual status_t    getCurrentPosition(int *msec) = 0;
    virtual status_t    getDuration(int *msec) = 0;
    virtual status_t    reset() = 0;
    virtual status_t    setLooping(int loop) = 0;
    virtual player_type playerType() = 0;
    virtual void        setNotifyCallback(void* cookie, notify_callback_f notifyFunc) {
                            mCookie = cookie; mNotify = notifyFunc; }

protected:
    virtual void        sendEvent(int msg, int ext1=0, int ext2=0) { if (mNotify) mNotify(mCookie, msg, ext1, ext2); }

    void*               mCookie;
    notify_callback_f   mNotify;
};

// Implement this class for media players that use the AudioFlinger software mixer
class MediaPlayerInterface : public MediaPlayerBase
{
public:
    virtual             ~MediaPlayerInterface() { }
    virtual bool        hardwareOutput() { return false; }
    virtual void        setAudioSink(const sp<AudioSink>& audioSink) { mAudioSink = audioSink; }
protected:
    sp<AudioSink>       mAudioSink;
};

// Implement this class for media players that output directo to hardware
class MediaPlayerHWInterface : public MediaPlayerBase
{
public:
    virtual             ~MediaPlayerHWInterface() {}
    virtual bool        hardwareOutput() { return true; }
    virtual status_t    setVolume(float leftVolume, float rightVolume) = 0;
    virtual status_t    setAudioStreamType(int streamType) = 0;
};

}; // namespace android

#endif // __cplusplus

// A thread can set the thread local variable identified by the pthread_key_t
// that was passed to the player using the setSigBusHandlerStructTLSKey()
// method to the address of the following structure.
// If 'handlesigbus' is non-NULL, the function it points to will be called,
// and if it returns 0, the signal will be assumed to have been handled,
// and no other action will be taken. If it returns non-zero, the old SIGBUS
// handler will be called.
// If 'handlesigbus is NULL, then sigbusvar must be non NULL. The system's
// SIGBUS handler will map an accessible page filled with zeroes at the
// location that caused the original fault, set the variable pointed to by
// sigbusvar to a non-zero value, and exit (which causes the operation to
// be retried, which should now succeed).
// If base and len are non zero, which is strongly recommended, they will
// be used as additional constraints on the signal handler. That is, when
// specified, the fault address must be in the range specified by base and
// len in order for handlesigbus() to be called or sigbusvar to be set.
// If the fault address is outside of the range, the old SIGBUS handler
// will be called.
struct mediasigbushandler {
    int (*handlesigbus)(siginfo_t *, struct mediasigbushandler *);
    int  *sigbusvar;
    char *base;
    int len;
    // these next two are free for application use
    struct mediasigbushandler *next;
    void *data;
};


#endif // ANDROID_MEDIAPLAYERINTERFACE_H

