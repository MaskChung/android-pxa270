/* //device/extlibs/pv/android/IAudioflinger.cpp
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

#define LOG_TAG "IAudioFlinger"
#include <utils/Log.h>

#include <stdint.h>
#include <sys/types.h>

#include <utils/Parcel.h>

#include <media/IAudioFlinger.h>

namespace android {

enum {
    CREATE_TRACK = IBinder::FIRST_CALL_TRANSACTION,
    OPEN_RECORD,
    SAMPLE_RATE,
    CHANNEL_COUNT,
    FORMAT,
    FRAME_COUNT,
    SET_MASTER_VOLUME,
    SET_MASTER_MUTE,
    MASTER_VOLUME,
    MASTER_MUTE,
    SET_STREAM_VOLUME,
    SET_STREAM_MUTE,
    STREAM_VOLUME,
    STREAM_MUTE,
    SET_MODE,
    GET_MODE,
    SET_ROUTING,
    GET_ROUTING,
    SET_MIC_MUTE,
    GET_MIC_MUTE,
    IS_MUSIC_ACTIVE,
    SET_PARAMETER,
};

class BpAudioFlinger : public BpInterface<IAudioFlinger>
{
public:
    BpAudioFlinger(const sp<IBinder>& impl)
        : BpInterface<IAudioFlinger>(impl)
    {
    }

    virtual sp<IAudioTrack> createTrack(
                                pid_t pid,
                                int streamType,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int bufferCount,
                                uint32_t flags)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(pid);
        data.writeInt32(streamType);
        data.writeInt32(sampleRate);
        data.writeInt32(format);
        data.writeInt32(channelCount);
        data.writeInt32(bufferCount);
        data.writeInt32(flags);
        status_t status = remote()->transact(CREATE_TRACK, data, &reply);
        if ( status != NO_ERROR) {
            LOGE("createTrack error: %s", strerror(-status));
        }
        
        return interface_cast<IAudioTrack>(reply.readStrongBinder());
    }

    virtual sp<IAudioRecord> openRecord(
                                pid_t pid,
                                int streamType,
                                uint32_t sampleRate,
                                int format,
                                int channelCount,
                                int bufferCount,
                                uint32_t flags)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(pid);
        data.writeInt32(streamType);
        data.writeInt32(sampleRate);
        data.writeInt32(format);
        data.writeInt32(channelCount);
        data.writeInt32(bufferCount);
        data.writeInt32(flags);
        remote()->transact(OPEN_RECORD, data, &reply);
        return interface_cast<IAudioRecord>(reply.readStrongBinder());
    }

    virtual uint32_t sampleRate() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(SAMPLE_RATE, data, &reply);
        return reply.readInt32();
    }

    virtual int channelCount() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(CHANNEL_COUNT, data, &reply);
        return reply.readInt32();
    }

    virtual int format() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(FORMAT, data, &reply);
        return reply.readInt32();
    }

    virtual size_t frameCount() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(FRAME_COUNT, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setMasterVolume(float value)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeFloat(value);
        remote()->transact(SET_MASTER_VOLUME, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setMasterMute(bool muted)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(muted);
        remote()->transact(SET_MASTER_MUTE, data, &reply);
        return reply.readInt32();
    }

    virtual float masterVolume() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(MASTER_VOLUME, data, &reply);
        return reply.readFloat();
    }

    virtual bool masterMute() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(MASTER_MUTE, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setStreamVolume(int stream, float value)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(stream);
        data.writeFloat(value);
        remote()->transact(SET_STREAM_VOLUME, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setStreamMute(int stream, bool muted)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(stream);
        data.writeInt32(muted);
        remote()->transact(SET_STREAM_MUTE, data, &reply);
        return reply.readInt32();
    }

    virtual float streamVolume(int stream) const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(stream);
        remote()->transact(STREAM_VOLUME, data, &reply);
        return reply.readFloat();
    }

    virtual bool streamMute(int stream) const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(stream);
        remote()->transact(STREAM_MUTE, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setRouting(int mode, uint32_t routes, uint32_t mask)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(mode);
        data.writeInt32(routes);
        data.writeInt32(mask);
        remote()->transact(SET_ROUTING, data, &reply);
        return reply.readInt32();
    }

    virtual uint32_t getRouting(int mode) const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(mode);
        remote()->transact(GET_ROUTING, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setMode(int mode)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(mode);
        remote()->transact(SET_MODE, data, &reply);
        return reply.readInt32();
    }

    virtual int getMode() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(GET_MODE, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setMicMute(bool state)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeInt32(state);
        remote()->transact(SET_MIC_MUTE, data, &reply);
        return reply.readInt32();
    }

    virtual bool getMicMute() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(GET_MIC_MUTE, data, &reply);
        return reply.readInt32();
    }

    virtual bool isMusicActive() const
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        remote()->transact(IS_MUSIC_ACTIVE, data, &reply);
        return reply.readInt32();
    }

    virtual status_t setParameter(const char* key, const char* value)
    {
        Parcel data, reply;
        data.writeInterfaceToken(IAudioFlinger::getInterfaceDescriptor());
        data.writeCString(key);
        data.writeCString(value);
        remote()->transact(SET_PARAMETER, data, &reply);
        return reply.readInt32();
    }
};

IMPLEMENT_META_INTERFACE(AudioFlinger, "android.media.IAudioFlinger");

// ----------------------------------------------------------------------

#define CHECK_INTERFACE(interface, data, reply) \
        do { if (!data.enforceInterface(interface::getInterfaceDescriptor())) { \
            LOGW("Call incorrectly routed to " #interface); \
            return PERMISSION_DENIED; \
        } } while (0)

status_t BnAudioFlinger::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
        case CREATE_TRACK: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            pid_t pid = data.readInt32();
            int streamType = data.readInt32();
            uint32_t sampleRate = data.readInt32();
            int format = data.readInt32();
            int channelCount = data.readInt32();
            size_t bufferCount = data.readInt32();
            uint32_t flags = data.readInt32();
            sp<IAudioTrack> track = createTrack(pid, 
                    streamType, sampleRate, format,
                    channelCount, bufferCount, flags);
            reply->writeStrongBinder(track->asBinder());
            return NO_ERROR;
        } break;
        case OPEN_RECORD: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            pid_t pid = data.readInt32();
            int streamType = data.readInt32();
            uint32_t sampleRate = data.readInt32();
            int format = data.readInt32();
            int channelCount = data.readInt32();
            size_t bufferCount = data.readInt32();
            uint32_t flags = data.readInt32();
            sp<IAudioRecord> record = openRecord(pid, streamType,
                    sampleRate, format, channelCount, bufferCount, flags);
            reply->writeStrongBinder(record->asBinder());
            return NO_ERROR;
        } break;
        case SAMPLE_RATE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( sampleRate() );
            return NO_ERROR;
        } break;
        case CHANNEL_COUNT: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( channelCount() );
            return NO_ERROR;
        } break;
        case FORMAT: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( format() );
            return NO_ERROR;
        } break;
        case FRAME_COUNT: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( frameCount() );
            return NO_ERROR;
        } break;
        case SET_MASTER_VOLUME: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( setMasterVolume(data.readFloat()) );
            return NO_ERROR;
        } break;
        case SET_MASTER_MUTE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( setMasterMute(data.readInt32()) );
            return NO_ERROR;
        } break;
        case MASTER_VOLUME: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeFloat( masterVolume() );
            return NO_ERROR;
        } break;
        case MASTER_MUTE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( masterMute() );
            return NO_ERROR;
        } break;
        case SET_STREAM_VOLUME: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int stream = data.readInt32();
            reply->writeInt32( setStreamVolume(stream, data.readFloat()) );
            return NO_ERROR;
        } break;
        case SET_STREAM_MUTE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int stream = data.readInt32();
            reply->writeInt32( setStreamMute(stream, data.readInt32()) );
            return NO_ERROR;
        } break;
        case STREAM_VOLUME: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int stream = data.readInt32();
            reply->writeFloat( streamVolume(stream) );
            return NO_ERROR;
        } break;
        case STREAM_MUTE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int stream = data.readInt32();
            reply->writeInt32( streamMute(stream) );
            return NO_ERROR;
        } break;
        case SET_ROUTING: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int mode = data.readInt32();
            uint32_t routes = data.readInt32();
            uint32_t mask = data.readInt32();
            reply->writeInt32( setRouting(mode, routes, mask) );
            return NO_ERROR;
        } break;
        case GET_ROUTING: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int mode = data.readInt32();
            reply->writeInt32( getRouting(mode) );
            return NO_ERROR;
        } break;
        case SET_MODE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int mode = data.readInt32();
            reply->writeInt32( setMode(mode) );
            return NO_ERROR;
        } break;
        case GET_MODE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( getMode() );
            return NO_ERROR;
        } break;
        case SET_MIC_MUTE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            int state = data.readInt32();
            reply->writeInt32( setMicMute(state) );
            return NO_ERROR;
        } break;
        case GET_MIC_MUTE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( getMicMute() );
            return NO_ERROR;
        } break;
        case IS_MUSIC_ACTIVE: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            reply->writeInt32( isMusicActive() );
            return NO_ERROR;
        } break;
        case SET_PARAMETER: {
            CHECK_INTERFACE(IAudioFlinger, data, reply);
            const char *key = data.readCString();
            const char *value = data.readCString();
            reply->writeInt32( setParameter(key, value) );
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

}; // namespace android
