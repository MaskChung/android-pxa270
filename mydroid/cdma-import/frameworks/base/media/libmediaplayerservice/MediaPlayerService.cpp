/*
**
** Copyright 2008, The Android Open Source Project
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

// Proxy for media player implementations

//#define LOG_NDEBUG 0
#define LOG_TAG "MediaPlayerService"
#include <utils/Log.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>

#include <string.h>
#include <cutils/atomic.h>

#include <android_runtime/ActivityManager.h>
#include <utils/IPCThreadState.h>
#include <utils/IServiceManager.h>
#include <utils/MemoryHeapBase.h>
#include <utils/MemoryBase.h>

#include <media/MediaPlayerInterface.h>
#include <media/AudioTrack.h>

#include "MediaPlayerService.h"
#include "MidiFile.h"
#include "VorbisPlayer.h"
#include <media/PVPlayer.h>

/* desktop Linux needs a little help with gettid() */
#if defined(HAVE_GETTID) && !defined(HAVE_ANDROID_OS)
#define __KERNEL__
# include <linux/unistd.h>
#ifdef _syscall0
_syscall0(pid_t,gettid)
#else
pid_t gettid() { return syscall(__NR_gettid);}
#endif
#undef __KERNEL__
#endif

/*
    When USE_SIGBUS_HANDLER is set to 1, a handler for SIGBUS will be
    installed, which allows us to recover when there is a read error
    when accessing an mmap'ed file. However, since the kernel folks
    don't seem to like it when non kernel folks install signal handlers
    in their own process, this is currently disabled.
    Without the handler, the process hosting this service will die and
    then be restarted. This is mostly OK right now because the process is
    not being shared with any other services, and clients of the service
    will be notified of its death in their MediaPlayer.onErrorListener
    callback, assuming they have installed one, and can then attempt to
    do their own recovery.
    It does open us up to a DOS attack against the media server, where
    a malicious application can trivially force the media server to
    restart continuously.
*/
#define USE_SIGBUS_HANDLER 0
 
// TODO: Temp hack until we can register players
static const char* MIDI_FILE_EXTS[] =
{
        ".mid",
        ".smf",
        ".xmf",
        ".imy",
        ".rtttl",
        ".rtx",
        ".ota"
};

namespace android {

// TODO: should come from audio driver
/* static */ const uint32_t MediaPlayerService::AudioOutput::kDriverLatencyInMsecs = 150;

static struct sigaction oldact;
static pthread_key_t sigbuskey;

static void sigbushandler(int signal, siginfo_t *info, void *context)
{
    char *faultaddr = (char*) info->si_addr;
    LOGE("SIGBUS at %p\n", faultaddr);

    struct mediasigbushandler* h = (struct mediasigbushandler*) pthread_getspecific(sigbuskey);

    if (h) {
        if (h->len) {
            if (faultaddr < h->base || faultaddr >= h->base + h->len) {
                // outside specified range, call old handler
                if (oldact.sa_flags & SA_SIGINFO) {
                    oldact.sa_sigaction(signal, info, context);
                } else {
                    oldact.sa_handler(signal);
                }
                return;
            }
        }

        // no range specified or address was in range

        if (h->handlesigbus) {
            if (h->handlesigbus(info, h)) {
                // thread's handler didn't handle the signal
                if (oldact.sa_flags & SA_SIGINFO) {
                    oldact.sa_sigaction(signal, info, context);
                } else {
                    oldact.sa_handler(signal);
                }
            }
            return;
        }

        if (h->sigbusvar) {
            // map in a zeroed out page so the operation can succeed
            long pagesize = sysconf(_SC_PAGE_SIZE);
            long pagemask = ~(pagesize - 1);
            void * pageaddr = (void*) (((long)(faultaddr)) & pagemask);

            void * bar = mmap( pageaddr, pagesize, PROT_READ, MAP_ANONYMOUS|MAP_PRIVATE|MAP_FIXED, -1, 0);
            if (bar == MAP_FAILED) {
                LOGE("couldn't map zero page at %p: %s", pageaddr, strerror(errno));
                if (oldact.sa_flags & SA_SIGINFO) {
                    oldact.sa_sigaction(signal, info, context);
                } else {
                    oldact.sa_handler(signal);
                }
                return;
            }

            LOGE("setting sigbusvar at %p", h->sigbusvar);
            *(h->sigbusvar) = 1;
            return;
        }
    }

    LOGE("SIGBUS: no handler, or improperly configured handler (%p)", h);

    if (oldact.sa_flags & SA_SIGINFO) {
        oldact.sa_sigaction(signal, info, context);
    } else {
        oldact.sa_handler(signal);
    }
    return;
}

void MediaPlayerService::instantiate() {
    defaultServiceManager()->addService(
            String16("media.player"), new MediaPlayerService());
}

MediaPlayerService::MediaPlayerService()
{
    LOGV("MediaPlayerService created");
    mNextConnId = 1;

    pthread_key_create(&sigbuskey, NULL);

  
#if USE_SIGBUS_HANDLER
    struct sigaction act;
    memset(&act,0, sizeof act);
    act.sa_sigaction = sigbushandler;
    act.sa_flags = SA_SIGINFO;
    sigaction(SIGBUS, &act, &oldact);
#endif
}

MediaPlayerService::~MediaPlayerService()
{
#if USE_SIGBUS_HANDLER
    sigaction(SIGBUS, &oldact, NULL);
#endif
    pthread_key_delete(sigbuskey);
    LOGV("MediaPlayerService destroyed");
}

sp<IMediaPlayer> MediaPlayerService::create(pid_t pid, const sp<IMediaPlayerClient>& client, const char* url)
{
    int32_t connId = android_atomic_inc(&mNextConnId);
    sp<Client> c = new Client(this, pid, connId, client);
    LOGV("Create new client(%d) from pid %d, url=%s, connId=%d", connId, pid, url, connId);
    if (NO_ERROR != c->setDataSource(url))
    {
        c.clear();
        return c;
    }
    wp<Client> w = c;
    Mutex::Autolock lock(mLock);
    mClients.add(w);
    return c;
}

sp<IMediaPlayer> MediaPlayerService::create(pid_t pid, const sp<IMediaPlayerClient>& client,
        int fd, int64_t offset, int64_t length)
{
    int32_t connId = android_atomic_inc(&mNextConnId);
    sp<Client> c = new Client(this, pid, connId, client);
    LOGV("Create new client(%d) from pid %d, fd=%d, offset=%lld, length=%lld",
            connId, pid, fd, offset, length);
    if (NO_ERROR != c->setDataSource(fd, offset, length)) {
        c.clear();
    } else {
        wp<Client> w = c;
        Mutex::Autolock lock(mLock);
        mClients.add(w);
    }
    ::close(fd);
    return c;
}

status_t MediaPlayerService::AudioCache::dump(int fd, const Vector<String16>& args) const
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    result.append(" AudioCache\n");
    if (mHeap != 0) {
        snprintf(buffer, 255, "  heap base(%p), size(%d), flags(%d), device(%s)\n",
                mHeap->getBase(), mHeap->getSize(), mHeap->getFlags(), mHeap->getDevice());
        result.append(buffer);
    }
    snprintf(buffer, 255, "  msec per frame(%f), channel count(%ld), frame count(%ld)\n",
            mMsecsPerFrame, mChannelCount, mFrameCount);
    result.append(buffer);
    snprintf(buffer, 255, "  sample rate(%d), size(%d), error(%d), command complete(%s)\n",
            mSampleRate, mSize, mError, mCommandComplete?"true":"false");
    result.append(buffer);
    ::write(fd, result.string(), result.size());
    return NO_ERROR;
}

status_t MediaPlayerService::AudioOutput::dump(int fd, const Vector<String16>& args) const
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;

    result.append(" AudioOutput\n");
    snprintf(buffer, 255, "  stream type(%d), left - right volume(%f, %f)\n",
            mStreamType, mLeftVolume, mRightVolume);
    result.append(buffer);
    snprintf(buffer, 255, "  msec per frame(%f), latency (%d), driver latency(%d)\n",
            mMsecsPerFrame, mLatency, kDriverLatencyInMsecs);
    result.append(buffer);
    ::write(fd, result.string(), result.size());
    if (mTrack != 0) {
        mTrack->dump(fd, args);
    }
    return NO_ERROR;
}

status_t MediaPlayerService::Client::dump(int fd, const Vector<String16>& args) const
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    result.append(" Client\n");
    snprintf(buffer, 255, "  pid(%d), connId(%d), status(%d), looping(%s)\n",
            mPid, mConnId, mStatus, mLoop?"true": "false");
    result.append(buffer);
    write(fd, result.string(), result.size());
    if (mAudioOutput != 0) {
        mAudioOutput->dump(fd, args);
    }
    write(fd, "\n", 1);
    return NO_ERROR;
}

static int myTid() {
#ifdef HAVE_GETTID
    return gettid();
#else
    return getpid();
#endif
}

status_t MediaPlayerService::dump(int fd, const Vector<String16>& args)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    if (checkCallingPermission(String16("android.permission.DUMP")) == false) {
        snprintf(buffer, SIZE, "Permission Denial: "
                "can't dump MediaPlayerService from pid=%d, uid=%d\n",
                IPCThreadState::self()->getCallingPid(),
                IPCThreadState::self()->getCallingUid());
        result.append(buffer);
    } else {
        Mutex::Autolock lock(mLock);
        for (int i = 0, n = mClients.size(); i < n; ++i) {
            sp<Client> c = mClients[i].promote();
            if (c != 0) c->dump(fd, args);
        }
        result.append(" Files opened and/or mapped:\n");
        snprintf(buffer, SIZE, "/proc/%d/maps", myTid());
        FILE *f = fopen(buffer, "r");
        if (f) {
            while (!feof(f)) {
                fgets(buffer, SIZE, f);
                if (strstr(buffer, " /sdcard/") || 
                    strstr(buffer, " /system/sounds/") ||
                    strstr(buffer, " /system/media/")) {
                    result.append("  ");
                    result.append(buffer);
                }
            }
            fclose(f);
        } else {
            result.append("couldn't open ");
            result.append(buffer);
            result.append("\n");
        }

        snprintf(buffer, SIZE, "/proc/%d/fd", myTid());
        DIR *d = opendir(buffer);
        if (d) {
            struct dirent *ent;
            while((ent = readdir(d)) != NULL) {
                if (strcmp(ent->d_name,".") && strcmp(ent->d_name,"..")) { 
                    snprintf(buffer, SIZE, "/proc/%d/fd/%s", myTid(), ent->d_name);
                    struct stat s;
                    if (lstat(buffer, &s) == 0) {
                        if ((s.st_mode & S_IFMT) == S_IFLNK) {
                            char linkto[256];
                            int len = readlink(buffer, linkto, sizeof(linkto));
                            if(len > 0) {
                                if(len > 255) {
                                    linkto[252] = '.';
                                    linkto[253] = '.';
                                    linkto[254] = '.';
                                    linkto[255] = 0;
                                } else {
                                    linkto[len] = 0;
                                }
                                if (strstr(linkto, "/sdcard/") == linkto || 
                                    strstr(linkto, "/system/sounds/") == linkto ||
                                    strstr(linkto, "/system/media/") == linkto) {
                                    result.append("  ");
                                    result.append(buffer);
                                    result.append(" -> ");
                                    result.append(linkto);
                                    result.append("\n");
                                }
                            }
                        } else {
                            result.append("  unexpected type for ");
                            result.append(buffer);
                            result.append("\n");
                        }
                    }
                }
            }
            closedir(d);
        } else {
            result.append("couldn't open ");
            result.append(buffer);
            result.append("\n");
        }
    }
    write(fd, result.string(), result.size());
    return NO_ERROR;
}

void MediaPlayerService::removeClient(wp<Client> client)
{
    Mutex::Autolock lock(mLock);
    mClients.remove(client);
}

MediaPlayerService::Client::Client(const sp<MediaPlayerService>& service, pid_t pid,
        int32_t connId, const sp<IMediaPlayerClient>& client)
{
    LOGV("Client(%d) constructor", connId);
    mPid = pid;
    mConnId = connId;
    mService = service;
    mClient = client;
    mLoop = false;
    mStatus = NO_INIT;
#if CALLBACK_ANTAGONIZER
    LOGD("create Antagonizer");
    mAntagonizer = new Antagonizer(notify, this);
#endif
}

MediaPlayerService::Client::~Client()
{
    LOGV("Client(%d) destructor pid = %d", mConnId, mPid);
    mAudioOutput.clear();
    wp<Client> client(this);
    disconnect();
    mService->removeClient(client);
}

void MediaPlayerService::Client::disconnect()
{
    LOGV("disconnect(%d) from pid %d", mConnId, mPid);
    // grab local reference and clear main reference to prevent future
    // access to object
    sp<MediaPlayerBase> p;
    {
        Mutex::Autolock l(mLock);
        p = mPlayer;
    }
    mPlayer.clear();

    // clear the notification to prevent callbacks to dead client
    // and reset the player. We assume the player will serialize
    // access to itself if necessary.
    if (p != 0) {
        p->setNotifyCallback(0, 0);
#if CALLBACK_ANTAGONIZER
        LOGD("kill Antagonizer");
        mAntagonizer->kill();
#endif
        p->reset();
    }

    IPCThreadState::self()->flushCommands();
}

static player_type getPlayerType(int fd, int64_t offset, int64_t length)
{
    char buf[20];
    lseek(fd, offset, SEEK_SET);
    read(fd, buf, sizeof(buf));
    lseek(fd, offset, SEEK_SET);

    long ident = *((long*)buf);

    // Ogg vorbis?
    if (ident == 0x5367674f) // 'OggS'
        return VORBIS_PLAYER;

    // Some kind of MIDI?
    EAS_DATA_HANDLE easdata;
    if (EAS_Init(&easdata) == EAS_SUCCESS) {
        EAS_FILE locator;
        locator.path = NULL;
        locator.fd = fd;
        locator.offset = offset;
        locator.length = length;
        EAS_HANDLE  eashandle;
        if (EAS_OpenFile(easdata, &locator, &eashandle, NULL) == EAS_SUCCESS) {
            EAS_CloseFile(easdata, eashandle);
            EAS_Shutdown(easdata);
            return SONIVOX_PLAYER;
        }
        EAS_Shutdown(easdata);
    }

    // Fall through to PV
    return PV_PLAYER;
}

static player_type getPlayerType(const char* url)
{

    // use MidiFile for MIDI extensions
    int lenURL = strlen(url);
    for (int i = 0; i < NELEM(MIDI_FILE_EXTS); ++i) {
        int len = strlen(MIDI_FILE_EXTS[i]);
        int start = lenURL - len;
        if (start > 0) {
            if (!strncmp(url + start, MIDI_FILE_EXTS[i], len)) {
                LOGV("Type is MIDI");
                return SONIVOX_PLAYER;
            }
        }
    }

    if (strcmp(url + strlen(url) - 4, ".ogg") == 0) {
        LOGV("Type is Vorbis");
        return VORBIS_PLAYER;
    }

    // Fall through to PV
    return PV_PLAYER;
}

static sp<MediaPlayerBase> createPlayer(player_type playerType, void* cookie,
        notify_callback_f notifyFunc)
{
    sp<MediaPlayerBase> p;
    switch (playerType) {
        case PV_PLAYER:
            LOGV(" create PVPlayer");
            p = new PVPlayer();
            break;
        case SONIVOX_PLAYER:
            LOGV(" create MidiFile");
            p = new MidiFile();
            break;
        case VORBIS_PLAYER:
            LOGV(" create VorbisPlayer");
            p = new VorbisPlayer();
            break;
    }
    if (p != NULL) {
        if (p->initCheck() == NO_ERROR) {
            p->setNotifyCallback(cookie, notifyFunc);
            p->setSigBusHandlerStructTLSKey(sigbuskey);
        } else {
            p.clear();
        }
    }
    if (p == NULL) {
        LOGE("Failed to create player object");
    }
    return p;
}

sp<MediaPlayerBase> MediaPlayerService::Client::createPlayer(player_type playerType)
{
    // determine if we have the right player type
    sp<MediaPlayerBase> p = mPlayer;
    if ((p != NULL) && (p->playerType() != playerType)) {
        LOGV("delete player");
        p.clear();
    }
    if (p == NULL) {
        p = android::createPlayer(playerType, this, notify);
    }
    return p;
}

status_t MediaPlayerService::Client::setDataSource(const char *url)
{
    LOGV("setDataSource(%s)", url);
    if (url == NULL)
        return UNKNOWN_ERROR;

    if (strncmp(url, "content://", 10) == 0) {
        // get a filedescriptor for the content Uri and
        // pass it to the setDataSource(fd) method

        String16 url16(url);
        int fd = android::openContentProviderFile(url16);
        if (fd < 0)
        {
            LOGE("Couldn't open fd for %s", url);
            return UNKNOWN_ERROR;
        }
        setDataSource(fd, 0, 0x7fffffffffLL); // this sets mStatus
        close(fd);
        return mStatus;
    } else {
        player_type playerType = getPlayerType(url);
        LOGV("player type = %d", playerType);

        // create the right type of player
        sp<MediaPlayerBase> p = createPlayer(playerType);
        if (p == NULL) return NO_INIT;

        if (!p->hardwareOutput()) {
            mAudioOutput = new AudioOutput();
            static_cast<MediaPlayerInterface*>(p.get())->setAudioSink(mAudioOutput);
        }

        // now set data source
        LOGV(" setDataSource");
        mStatus = p->setDataSource(url);
        if (mStatus == NO_ERROR) mPlayer = p;
        return mStatus;
    }
}

status_t MediaPlayerService::Client::setDataSource(int fd, int64_t offset, int64_t length)
{
    LOGV("setDataSource fd=%d, offset=%lld, length=%lld", fd, offset, length);
    struct stat sb;
    int ret = fstat(fd, &sb);
    if (ret != 0) {
        LOGE("fstat(%d) failed: %d, %s", fd, ret, strerror(errno));
        return UNKNOWN_ERROR;
    }

    LOGV("st_dev  = %llu", sb.st_dev);
    LOGV("st_mode = %u", sb.st_mode);
    LOGV("st_uid  = %lu", sb.st_uid);
    LOGV("st_gid  = %lu", sb.st_gid);
    LOGV("st_size = %llu", sb.st_size);

    if (offset >= sb.st_size) {
        LOGE("offset error");
        ::close(fd);
        return UNKNOWN_ERROR;
    }
    if (offset + length > sb.st_size) {
        length = sb.st_size - offset;
        LOGV("calculated length = %lld", length);
    }

    player_type playerType = getPlayerType(fd, offset, length);
    LOGV("player type = %d", playerType);

    // create the right type of player
    sp<MediaPlayerBase> p = createPlayer(playerType);
    if (p == NULL) return NO_INIT;

    if (!p->hardwareOutput()) {
        mAudioOutput = new AudioOutput();
        static_cast<MediaPlayerInterface*>(p.get())->setAudioSink(mAudioOutput);
    }

    // now set data source
    mStatus = p->setDataSource(fd, offset, length);
    if (mStatus == NO_ERROR) mPlayer = p;
    return mStatus;
}

status_t MediaPlayerService::Client::setVideoSurface(const sp<ISurface>& surface)
{
    LOGV("[%d] setVideoSurface(%p)", mConnId, surface.get());
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    return p->setVideoSurface(surface);
}

status_t MediaPlayerService::Client::prepareAsync()
{
    LOGV("[%d] prepareAsync", mConnId);
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    status_t ret = p->prepareAsync();
#if CALLBACK_ANTAGONIZER
    LOGD("start Antagonizer");
    if (ret == NO_ERROR) mAntagonizer->start();
#endif
    return ret;
}

status_t MediaPlayerService::Client::start()
{
    LOGV("[%d] start", mConnId);
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    p->setLooping(mLoop);
    return p->start();
}

status_t MediaPlayerService::Client::stop()
{
    LOGV("[%d] stop", mConnId);
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    return p->stop();
}

status_t MediaPlayerService::Client::pause()
{
    LOGV("[%d] pause", mConnId);
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    return p->pause();
}

status_t MediaPlayerService::Client::isPlaying(bool* state)
{
    *state = false;
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    *state = p->isPlaying();
    LOGV("[%d] isPlaying: %d", mConnId, *state);
    return NO_ERROR;
}

status_t MediaPlayerService::Client::getVideoSize(int *w, int *h)
{
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    status_t ret = p->getVideoWidth(w);
    if (ret == NO_ERROR) ret = p->getVideoHeight(h);
    if (ret == NO_ERROR) {
        LOGV("[%d] getVideoWidth = (%d, %d)", mConnId, *w, *h);
    } else {
        LOGE("getVideoSize returned %d", ret);
    }
    return ret;
}

status_t MediaPlayerService::Client::getCurrentPosition(int *msec)
{
    LOGV("getCurrentPosition");
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    status_t ret = p->getCurrentPosition(msec);
    if (ret == NO_ERROR) {
        LOGV("[%d] getCurrentPosition = %d", mConnId, *msec);
    } else {
        LOGE("getCurrentPosition returned %d", ret);
    }
    return ret;
}

status_t MediaPlayerService::Client::getDuration(int *msec)
{
    LOGV("getDuration");
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    status_t ret = p->getDuration(msec);
    if (ret == NO_ERROR) {
        LOGV("[%d] getDuration = %d", mConnId, *msec);
    } else {
        LOGE("getDuration returned %d", ret);
    }
    return ret;
}

status_t MediaPlayerService::Client::seekTo(int msec)
{
    LOGV("[%d] seekTo(%d)", mConnId, msec);
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    return p->seekTo(msec);
}

status_t MediaPlayerService::Client::reset()
{
    LOGV("[%d] reset", mConnId);
    sp<MediaPlayerBase> p = getPlayer();
    if (p == 0) return UNKNOWN_ERROR;
    return p->reset();
}

status_t MediaPlayerService::Client::setAudioStreamType(int type)
{
    LOGV("[%d] setAudioStreamType(%d)", mConnId, type);
    // TODO: for hardware output, call player instead
    Mutex::Autolock l(mLock);
    if (mAudioOutput != 0) mAudioOutput->setAudioStreamType(type);
    return NO_ERROR;
}

status_t MediaPlayerService::Client::setLooping(int loop)
{
    LOGV("[%d] setLooping(%d)", mConnId, loop);
    mLoop = loop;
    sp<MediaPlayerBase> p = getPlayer();
    if (p != 0) return p->setLooping(loop);
    return NO_ERROR;
}

status_t MediaPlayerService::Client::setVolume(float leftVolume, float rightVolume)
{
    LOGV("[%d] setVolume(%f, %f)", mConnId, leftVolume, rightVolume);
    // TODO: for hardware output, call player instead
    Mutex::Autolock l(mLock);
    if (mAudioOutput != 0) mAudioOutput->setVolume(leftVolume, rightVolume);
    return NO_ERROR;
}

void MediaPlayerService::Client::notify(void* cookie, int msg, int ext1, int ext2)
{
    Client* client = static_cast<Client*>(cookie);
    LOGV("[%d] notify (%p, %d, %d, %d)", client->mConnId, cookie, msg, ext1, ext2);
    client->mClient->notify(msg, ext1, ext2);
}

#if CALLBACK_ANTAGONIZER
const int Antagonizer::interval = 10000; // 10 msecs

Antagonizer::Antagonizer(notify_callback_f cb, void* client) :
    mExit(false), mActive(false), mClient(client), mCb(cb)
{
    createThread(callbackThread, this);
}

void Antagonizer::kill()
{
    Mutex::Autolock _l(mLock);
    mActive = false;
    mExit = true;
    mCondition.wait(mLock);
}

int Antagonizer::callbackThread(void* user)
{
    LOGD("Antagonizer started");
    Antagonizer* p = reinterpret_cast<Antagonizer*>(user);
    while (!p->mExit) {
        if (p->mActive) {
            LOGV("send event");
            p->mCb(p->mClient, 0, 0, 0);
        }
        usleep(interval);
    }
    Mutex::Autolock _l(p->mLock);
    p->mCondition.signal();
    LOGD("Antagonizer stopped");
    return 0;
}
#endif

static size_t kDefaultHeapSize = 1024 * 1024; // 1MB

sp<IMemory> MediaPlayerService::decode(const char* url, uint32_t *pSampleRate, int* pNumChannels)
{
    LOGV("decode(%s)", url);
    sp<MemoryBase> mem;
    sp<MediaPlayerBase> player;

    // Protect our precious, precious DRMd ringtones by only allowing
    // decoding of http, but not filesystem paths or content Uris.
    // If the application wants to decode those, it should open a
    // filedescriptor for them and use that.
    if (url != NULL && strncmp(url, "http://", 7) != 0) {
        LOGD("Can't decode %s by path, use filedescriptor instead", url);
        return mem;
    }

    player_type playerType = getPlayerType(url);
    LOGV("player type = %d", playerType);

    // create the right type of player
    sp<AudioCache> cache = new AudioCache(url);
    player = android::createPlayer(playerType, cache.get(), cache->notify);
    if (player == NULL) goto Exit;
    if (player->hardwareOutput()) goto Exit;

    static_cast<MediaPlayerInterface*>(player.get())->setAudioSink(cache);

    // set data source
    if (player->setDataSource(url) != NO_ERROR) goto Exit;

    LOGV("prepare");
    player->prepareAsync();

    LOGV("wait for prepare");
    if (cache->wait() != NO_ERROR) goto Exit;

    LOGV("start");
    player->start();

    LOGV("wait for playback complete");
    if (cache->wait() != NO_ERROR) goto Exit;

    mem = new MemoryBase(cache->getHeap(), 0, cache->size());
    *pSampleRate = cache->sampleRate();
    *pNumChannels = cache->channelCount();
    LOGV("return memory @ %p, sampleRate=%u, channelCount = %d", mem->pointer(), *pSampleRate, *pNumChannels);

Exit:
    if (player != 0) player->reset();
    return mem;
}

sp<IMemory> MediaPlayerService::decode(int fd, int64_t offset, int64_t length, uint32_t *pSampleRate, int* pNumChannels)
{
    LOGV("decode(%d, %lld, %lld)", fd, offset, length);
    sp<MemoryBase> mem;
    sp<MediaPlayerBase> player;

    player_type playerType = getPlayerType(fd, offset, length);
    LOGV("player type = %d", playerType);

    // create the right type of player
    sp<AudioCache> cache = new AudioCache("decode_fd");
    player = android::createPlayer(playerType, cache.get(), cache->notify);
    if (player == NULL) goto Exit;
    if (player->hardwareOutput()) goto Exit;

    static_cast<MediaPlayerInterface*>(player.get())->setAudioSink(cache);

    // set data source
    if (player->setDataSource(fd, offset, length) != NO_ERROR) goto Exit;

    LOGV("prepare");
    player->prepareAsync();

    LOGV("wait for prepare");
    if (cache->wait() != NO_ERROR) goto Exit;

    LOGV("start");
    player->start();

    LOGV("wait for playback complete");
    if (cache->wait() != NO_ERROR) goto Exit;

    mem = new MemoryBase(cache->getHeap(), 0, cache->size());
    *pSampleRate = cache->sampleRate();
    *pNumChannels = cache->channelCount();
    LOGV("return memory @ %p, sampleRate=%u, channelCount = %d", mem->pointer(), *pSampleRate, *pNumChannels);

Exit:
    if (player != 0) player->reset();
    ::close(fd);
    return mem;
}

#undef LOG_TAG
#define LOG_TAG "AudioSink"
MediaPlayerService::AudioOutput::AudioOutput()
{
    mTrack = 0;
    mStreamType = AudioTrack::MUSIC;
    mLeftVolume = 1.0;
    mRightVolume = 1.0;
    mLatency = 0;
    mMsecsPerFrame = 0;
}

MediaPlayerService::AudioOutput::~AudioOutput()
{
    close();
}

ssize_t MediaPlayerService::AudioOutput::bufferSize() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->frameCount() * mTrack->channelCount() * sizeof(int16_t);
}

ssize_t MediaPlayerService::AudioOutput::frameCount() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->frameCount();
}

ssize_t MediaPlayerService::AudioOutput::channelCount() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->channelCount();
}

ssize_t MediaPlayerService::AudioOutput::frameSize() const
{
    if (mTrack == 0) return NO_INIT;
    return mTrack->channelCount() * sizeof(int16_t);
}

uint32_t MediaPlayerService::AudioOutput::latency () const
{
    return mLatency;
}

float MediaPlayerService::AudioOutput::msecsPerFrame() const
{
    return mMsecsPerFrame;
}

status_t MediaPlayerService::AudioOutput::open(uint32_t sampleRate, int channelCount, int bufferCount)
{
    LOGV("open(%u, %d, %d)", sampleRate, channelCount, bufferCount);
    if (mTrack) close();

    AudioTrack *t = new AudioTrack(mStreamType, sampleRate, AudioSystem::PCM_16_BIT, channelCount, bufferCount);
    if ((t == 0) || (t->initCheck() != NO_ERROR)) {
        LOGE("Unable to create audio track");
        delete t;
        return NO_INIT;
    }

    LOGV("setVolume");
    t->setVolume(mLeftVolume, mRightVolume);
    mMsecsPerFrame = 1.e3 / (float) sampleRate;
    mLatency = (mMsecsPerFrame * bufferCount * t->frameCount()) + kDriverLatencyInMsecs;
    mTrack = t;
    return NO_ERROR;
}

void MediaPlayerService::AudioOutput::start()
{
    LOGV("start");
    if (mTrack) {
        mTrack->setVolume(mLeftVolume, mRightVolume);
        mTrack->start();
    }
}

ssize_t MediaPlayerService::AudioOutput::write(const void* buffer, size_t size)
{
    //LOGV("write(%p, %u)", buffer, size);
    if (mTrack) return mTrack->write(buffer, size);
    return NO_INIT;
}

void MediaPlayerService::AudioOutput::stop()
{
    LOGV("stop");
    if (mTrack) mTrack->stop();
}

void MediaPlayerService::AudioOutput::flush()
{
    LOGV("flush");
    if (mTrack) mTrack->flush();
}

void MediaPlayerService::AudioOutput::pause()
{
    LOGV("pause");
    if (mTrack) mTrack->pause();
}

void MediaPlayerService::AudioOutput::close()
{
    LOGV("close");
    delete mTrack;
    mTrack = 0;
}

void MediaPlayerService::AudioOutput::setVolume(float left, float right)
{
    LOGV("setVolume(%f, %f)", left, right);
    mLeftVolume = left;
    mRightVolume = right;
    if (mTrack) {
        mTrack->setVolume(left, right);
    }
}

#undef LOG_TAG
#define LOG_TAG "AudioCache"
MediaPlayerService::AudioCache::AudioCache(const char* name) :
    mChannelCount(0), mFrameCount(0), mSampleRate(0), mSize(0),
    mError(NO_ERROR), mCommandComplete(false)
{
    // create ashmem heap
    mHeap = new MemoryHeapBase(kDefaultHeapSize, 0, name);
}

uint32_t MediaPlayerService::AudioCache::latency () const
{
    return 0;
}

float MediaPlayerService::AudioCache::msecsPerFrame() const
{
    return mMsecsPerFrame;
}

status_t MediaPlayerService::AudioCache::open(uint32_t sampleRate, int channelCount, int bufferCount)
{
    LOGV("open(%u, %d, %d)", sampleRate, channelCount, bufferCount);
   if (mHeap->getHeapID() < 0) return NO_INIT;
   mSampleRate = sampleRate;
   mChannelCount = channelCount;
    mMsecsPerFrame = 1.e3 / (float) sampleRate;
    return NO_ERROR;
}

ssize_t MediaPlayerService::AudioCache::write(const void* buffer, size_t size)
{
    LOGV("write(%p, %u)", buffer, size);
    if ((buffer == 0) || (size == 0)) return size;

    uint8_t* p = static_cast<uint8_t*>(mHeap->getBase());
    if (p == NULL) return NO_INIT;
    p += mSize;
    LOGV("memcpy(%p, %p, %u)", p, buffer, size);
    memcpy(p, buffer, size);
    mSize += size;
    return size;
}

// call with lock held
status_t MediaPlayerService::AudioCache::wait()
{
    Mutex::Autolock lock(mLock);
    if (!mCommandComplete) {
        mSignal.wait(mLock);
    }
    mCommandComplete = false;

    if (mError == NO_ERROR) {
        LOGV("wait - success");
    } else {
        LOGV("wait - error");
    }
    return mError;
}

void MediaPlayerService::AudioCache::notify(void* cookie, int msg, int ext1, int ext2)
{
    LOGV("notify(%p, %d, %d, %d)", cookie, msg, ext1, ext2);
    AudioCache* p = static_cast<AudioCache*>(cookie);

    // ignore buffering messages
    if (msg == MEDIA_BUFFERING_UPDATE) return;

    // set error condition
    if (msg == MEDIA_ERROR) {
        LOGE("Error %d, %d occurred", ext1, ext2);
        p->mError = ext1;
    }

    // wake up thread
    LOGV("wakeup thread");
    p->mCommandComplete = true;
    p->mSignal.signal();
}

}; // namespace android
