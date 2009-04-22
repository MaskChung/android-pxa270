/* //device/include/server/AudioFlinger/AudioFlinger.h
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

#ifndef ANDROID_AUDIO_FLINGER_H
#define ANDROID_AUDIO_FLINGER_H

#include <stdint.h>
#include <sys/types.h>

#include <media/IAudioFlinger.h>
#include <media/IAudioTrack.h>
#include <media/IAudioRecord.h>
#include <media/AudioTrack.h>

#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/threads.h>
#include <utils/MemoryDealer.h>
#include <utils/KeyedVector.h>
#include <utils/SortedVector.h>

#include <hardware/AudioHardwareInterface.h>

#include "AudioBufferProvider.h"

namespace android {

class audio_track_cblk_t;
class AudioMixer;
class AudioBuffer;

// ----------------------------------------------------------------------------

#define LIKELY( exp )       (__builtin_expect( (exp) != 0, true  ))
#define UNLIKELY( exp )     (__builtin_expect( (exp) != 0, false ))

// ----------------------------------------------------------------------------

class AudioFlinger : public BnAudioFlinger, protected Thread
{
public:
    static void instantiate();

    virtual     status_t    dump(int fd, const Vector<String16>& args);

    // Thread virtuals
    virtual     bool        threadLoop();
    virtual     status_t    readyToRun();
    virtual     void        onFirstRef();

    // IAudioFlinger interface
    virtual sp<IAudioTrack> createTrack(
                                pid_t pid,
                                int streamType,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int bufferCount,
                                uint32_t flags);

    virtual     uint32_t    sampleRate() const;
    virtual     int         channelCount() const;
    virtual     int         format() const;
    virtual     size_t      frameCount() const;

    virtual     status_t    setMasterVolume(float value);
    virtual     status_t    setMasterMute(bool muted);

    virtual     float       masterVolume() const;
    virtual     bool        masterMute() const;

    virtual     status_t    setStreamVolume(int stream, float value);
    virtual     status_t    setStreamMute(int stream, bool muted);

    virtual     float       streamVolume(int stream) const;
    virtual     bool        streamMute(int stream) const;

    virtual     status_t    setRouting(int mode, uint32_t routes, uint32_t mask);
    virtual     uint32_t    getRouting(int mode) const;

    virtual     status_t    setMode(int mode);
    virtual     int         getMode() const;

    virtual     status_t    setMicMute(bool state);
    virtual     bool        getMicMute() const;

    virtual     bool        isMusicActive() const;

    virtual     status_t    setParameter(const char* key, const char* value);

    enum hardware_call_state {
        AUDIO_HW_IDLE = 0,
        AUDIO_HW_INIT,
        AUDIO_HW_OUTPUT_OPEN,
        AUDIO_HW_OUTPUT_CLOSE,
        AUDIO_HW_INPUT_OPEN,
        AUDIO_HW_INPUT_CLOSE,
        AUDIO_HW_STANDBY,
        AUDIO_HW_SET_MASTER_VOLUME,
        AUDIO_HW_GET_ROUTING,
        AUDIO_HW_SET_ROUTING,
        AUDIO_HW_GET_MODE,
        AUDIO_HW_SET_MODE,
        AUDIO_HW_GET_MIC_MUTE,
        AUDIO_HW_SET_MIC_MUTE,
        AUDIO_SET_VOICE_VOLUME,
        AUDIO_SET_PARAMETER,
    };

    // record interface
    virtual sp<IAudioRecord> openRecord(
                                pid_t pid,
                                int streamType,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int bufferCount,
                                uint32_t flags);

    virtual     status_t    onTransact(
                                uint32_t code,
                                const Parcel& data,
                                Parcel* reply,
                                uint32_t flags);

private:
                            AudioFlinger();
    virtual                 ~AudioFlinger();
    
    // Internal dump utilites.
    status_t dumpPermissionDenial(int fd, const Vector<String16>& args);
    status_t dumpClients(int fd, const Vector<String16>& args);
    status_t dumpTracks(int fd, const Vector<String16>& args);
    status_t dumpInternals(int fd, const Vector<String16>& args);
    
    // --- Client ---
    class Client : public RefBase {
    public:
                            Client(const sp<AudioFlinger>& audioFlinger, pid_t pid);
        virtual             ~Client();
        const sp<MemoryDealer>&     heap() const;
        pid_t               pid() const { return mPid; }
    private:
                            Client(const Client&);
                            Client& operator = (const Client&);
        sp<AudioFlinger>    mAudioFlinger;
        sp<MemoryDealer>    mMemoryDealer;
        pid_t               mPid;
    };


    // --- Track ---
    class TrackHandle;
    class RecordHandle;
    class AudioRecordThread;

    // base for record and playback
    class TrackBase : public AudioBufferProvider, public RefBase {

    public:
        enum track_state {
            IDLE,
            TERMINATED,
            STOPPED,
            RESUMING,
            ACTIVE,
            PAUSING,
            PAUSED
        };

        enum track_flags {
            STEPSERVER_FAILED = 0x01   //  StepServer could not acquire cblk->lock mutex 
        };
        
                            TrackBase(  const sp<AudioFlinger>& audioFlinger,
                                    const sp<Client>& client,
                                    int streamType,
                                    uint32_t sampleRate,
                                    int format,
                                    int channelCount,
                                    int bufferCount,
                                    int bufferSize);
                            ~TrackBase();

        virtual status_t    start() = 0;
        virtual void        stop() = 0;
                sp<IMemory> getCblk() const;

    protected:
        friend class AudioFlinger;
        friend class RecordHandle;

                            TrackBase(const TrackBase&);
                            TrackBase& operator = (const TrackBase&);

        virtual status_t getNextBuffer(AudioBufferProvider::Buffer* buffer) = 0;
        virtual void releaseBuffer(AudioBufferProvider::Buffer* buffer);

        audio_track_cblk_t* cblk() const {
            return mCblk;
        }

        int type() const {
            return mStreamType;
        }

        int format() const {
            return mFormat;
        }

        int channelCount() const {
            return mChannelCount;
        }

        int bufferCount() const {
            return mBufferCount;
        }

        int sampleRate() const;

        void* getBuffer(int n) const {
            return (char*)mBuffers + n * mBufferSize;
        }

        int name() const {
            return mName;
        }

        bool isStopped() const {
            return mState == STOPPED;
        }

        bool isTerminated() const {
            return mState == TERMINATED;
        }

        bool step();
        void reset();

        sp<AudioFlinger>    mAudioFlinger;
        sp<Client>          mClient;
        sp<IMemory>         mCblkMemory;
        audio_track_cblk_t* mCblk;
        int                 mStreamType;
        uint8_t             mFormat;
        uint8_t             mChannelCount;
        uint8_t             mBufferCount;
        uint8_t             mFlags;
        void*               mBuffers;
        size_t              mBufferSize;
        int                 mName;
        // we don't really need a lock for these
        int                 mState;
        int                 mClientTid;
    };

    // playback track
    class Track : public TrackBase {
    public:
                            Track(  const sp<AudioFlinger>& audioFlinger,
                                    const sp<Client>& client,
                                    int streamType,
                                    uint32_t sampleRate,
                                    int format,
                                    int channelCount,
                                    int bufferCount,
                                    int bufferSize);
                            ~Track();

                void        dump(char* buffer, size_t size);
        virtual status_t    start();
        virtual void        stop();
                void        pause();

                void        flush();
                void        destroy();
                void        mute(bool);
                void        setVolume(float left, float right);

    private:
        friend class AudioFlinger;
        friend class TrackHandle;

                            Track(const Track&);
                            Track& operator = (const Track&);

        virtual status_t getNextBuffer(AudioBufferProvider::Buffer* buffer);

        bool isMuted() const {
            return mMute;
        }

        bool isPausing() const {
            return mState == PAUSING;
        }

        bool isPaused() const {
            return mState == PAUSED;
        }

        bool isReady(uint32_t u, int32_t s) const;

        void setPaused() { mState = PAUSED; }
        void reset();

        // we don't really need a lock for these
        float               mVolume[2];
        volatile bool       mMute;
        // FILLED state is used for suppressing volume ramp at begin of playing
        enum {FS_FILLING, FS_FILLED, FS_ACTIVE};
        mutable uint8_t     mFillingUpStatus;
        int8_t              mRetryCount;
    };  // end of Track

    friend class AudioBuffer;

    class TrackHandle : public android::BnAudioTrack {
    public:
                            TrackHandle(const sp<Track>& track);
        virtual             ~TrackHandle();
        virtual status_t    start();
        virtual void        stop();
        virtual void        flush();
        virtual void        mute(bool);
        virtual void        pause();
        virtual void        setVolume(float left, float right);
        virtual sp<IMemory> getCblk() const;
        virtual status_t onTransact(
            uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);
    private:
        sp<Track> mTrack;
    };

    struct  stream_type_t {
        stream_type_t()
            :   volume(1.0f),
                mute(false)
        {
        }
        float       volume;
        bool        mute;
    };

    friend class Client;
    friend class Track;


                void        removeClient(pid_t pid);

                status_t    addTrack(const sp<Track>& track);
                void        removeTrack(wp<Track> track, int name);
                void        remove_track_l(wp<Track> track, int name);
                void        destroyTrack(const sp<Track>& track);

                AudioMixer& audioMixer() {
                    return *mAudioMixer;
                }

    // record track
    class RecordTrack : public TrackBase {
    public:
                            RecordTrack(  const sp<AudioFlinger>& audioFlinger,
                                    const sp<Client>& client,
                                    int streamType,
                                    uint32_t sampleRate,
                                    int format,
                                    int channelCount,
                                    int bufferCount,
                                    int bufferSize);
                            ~RecordTrack();

        virtual status_t    start();
        virtual void        stop();

                bool        overflow() { bool tmp = mOverflow; mOverflow = false; return tmp; }
                bool        setOverflow() { bool tmp = mOverflow; mOverflow = true; return tmp; }

    private:
        friend class AudioFlinger;
        friend class RecordHandle;
        friend class AudioRecordThread;

                            RecordTrack(const Track&);
                            RecordTrack& operator = (const Track&);

        virtual status_t getNextBuffer(AudioBufferProvider::Buffer* buffer);

        bool                mOverflow;
    };

    class RecordHandle : public android::BnAudioRecord {
    public:
        RecordHandle(const sp<RecordTrack>& recordTrack);
        virtual             ~RecordHandle();
        virtual status_t    start();
        virtual void        stop();
        virtual sp<IMemory> getCblk() const;
        virtual status_t onTransact(
            uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);
    private:
        sp<RecordTrack> mRecordTrack;
    };

    // record thread
    class AudioRecordThread : public Thread
    {
    public:
        AudioRecordThread(const sp<AudioFlinger>& audioFlinger);
        virtual             ~AudioRecordThread();
        virtual bool        threadLoop();
        virtual status_t    readyToRun() { return NO_ERROR; }
        virtual void        onFirstRef() {}

                status_t    open(const sp<RecordTrack>& recordTrack, AudioStreamIn *input);
                status_t    start();
                void        stop();
                status_t    close();
                void        exit();
                
                bool        isOpen() { return bool(mRecordTrack != NULL); }

    private:
                AudioRecordThread();
                sp<AudioFlinger>                    mAudioFlinger;
                wp<RecordTrack>                     mRecordTrack;
                AudioStreamIn*                      mInput;
                Mutex                               mLock;
                Condition                           mWaitWorkCV;
                AudioBufferProvider::Buffer         mBuffer;
                volatile bool                       mActive;
    };

    friend class AudioRecordThread;

                sp<AudioRecordThread> audioRecordThread();
                void        endRecord();
                status_t    startRecord();
                void        stopRecord();
                void        exitRecord();
                
                AudioHardwareInterface* audioHardware() { return mAudioHardware; }

    mutable     Mutex                                       mHardwareLock;
    mutable     Mutex                                       mLock;
    mutable     Condition                                   mWaitWorkCV;
                DefaultKeyedVector< pid_t, wp<Client> >     mClients;
                SortedVector< wp<Track> >                   mActiveTracks;
                SortedVector< sp<Track> >                   mTracks;
                float                               mMasterVolume;
                uint32_t                            mMasterRouting;
                bool                                mMasterMute;
                stream_type_t                       mStreamTypes[AudioTrack::NUM_STREAM_TYPES];

                AudioMixer*                         mAudioMixer;
                AudioHardwareInterface*             mAudioHardware;
                AudioStreamOut*                     mOutput;
                sp<AudioRecordThread>               mAudioRecordThread;
                uint32_t                            mSampleRate;
                size_t                              mFrameCount;
                int                                 mChannelCount;
                int                                 mFormat;
                int                                 mMixBufferSize;
                int16_t*                            mMixBuffer;
    mutable     int                                 mHardwareStatus;
                nsecs_t                             mLastWriteTime;
                int                                 mNumWrites;
                int                                 mNumDelayedWrites;
                bool                                mStandby;
                bool                                mInWrite;
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif // ANDROID_AUDIO_FLINGER_H
