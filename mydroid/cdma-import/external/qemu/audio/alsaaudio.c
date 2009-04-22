/*
 * QEMU ALSA audio driver
 *
 * Copyright (c) 2008 The Android Open Source Project
 * Copyright (c) 2005 Vassili Karpov (malc)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
#include <alsa/asoundlib.h>
#include "vl.h"

#define AUDIO_CAP "alsa"
#include "audio_int.h"
#include <dlfcn.h>
#include <pthread.h>
#include "android_debug.h"

#define  DEBUG  1

#if DEBUG
#  include <stdio.h>
#  define D(...)  VERBOSE_PRINT(audio,__VA_ARGS__)
#  define D_ACTIVE  VERBOSE_CHECK(audio)
#  define O(...)  VERBOSE_PRINT(audioout,__VA_ARGS__)
#  define I(...)  VERBOSE_PRINT(audioin,__VA_ARGS__)
#else
#  define D(...)  ((void)0)
#  define D_ACTIVE  0
#  define O(...)  ((void)0)
#  define I(...)  ((void)0)
#endif


#define  STRINGIFY_(x)  #x
#define  STRINGIFY(x)   STRINGIFY_(x)

#define  DYN_SYMBOLS   \
    DYN_FUNCTION(size_t,snd_pcm_sw_params_sizeof,(void))    \
    DYN_FUNCTION(int,snd_pcm_hw_params_current,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params)) \
    DYN_FUNCTION(int,snd_pcm_sw_params_set_start_threshold,(snd_pcm_t *pcm, snd_pcm_sw_params_t *params, snd_pcm_uframes_t val))  \
    DYN_FUNCTION(int,snd_pcm_sw_params,(snd_pcm_t *pcm, snd_pcm_sw_params_t *params))  \
    DYN_FUNCTION(int,snd_pcm_sw_params_current,(snd_pcm_t *pcm, snd_pcm_sw_params_t *params)) \
    DYN_FUNCTION(size_t,snd_pcm_hw_params_sizeof,(void))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_any,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_access,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, snd_pcm_access_t _access))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_format,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, snd_pcm_format_t val))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_rate_near,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, unsigned int *val, int *dir))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_channels_near,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, unsigned int *val))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_buffer_time_near,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, unsigned int *val, int *dir))  \
    DYN_FUNCTION(int,snd_pcm_hw_params,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_get_buffer_size,(const snd_pcm_hw_params_t *params, snd_pcm_uframes_t *val))  \
    DYN_FUNCTION(int,snd_pcm_prepare,(snd_pcm_t *pcm))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_get_period_size,(const snd_pcm_hw_params_t *params, snd_pcm_uframes_t *frames, int *dir))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_get_period_size_min,(const snd_pcm_hw_params_t *params, snd_pcm_uframes_t *frames, int *dir))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_period_size,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, snd_pcm_uframes_t val, int dir))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_get_buffer_size_min,(const snd_pcm_hw_params_t *params, snd_pcm_uframes_t *val)) \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_buffer_size,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, snd_pcm_uframes_t val))  \
    DYN_FUNCTION(int,snd_pcm_hw_params_set_period_time_near,(snd_pcm_t *pcm, snd_pcm_hw_params_t *params, unsigned int *val, int *dir))  \
    DYN_FUNCTION(snd_pcm_sframes_t,snd_pcm_avail_update,(snd_pcm_t *pcm)) \
    DYN_FUNCTION(int,snd_pcm_drop,(snd_pcm_t *pcm))  \
    DYN_FUNCTION(snd_pcm_sframes_t,snd_pcm_writei,(snd_pcm_t *pcm, const void *buffer, snd_pcm_uframes_t size))  \
    DYN_FUNCTION(snd_pcm_sframes_t,snd_pcm_readi,(snd_pcm_t *pcm, void *buffer, snd_pcm_uframes_t size))  \
    DYN_FUNCTION(snd_pcm_state_t,snd_pcm_state,(snd_pcm_t *pcm))  \
    DYN_FUNCTION(const char*,snd_strerror,(int errnum)) \
    DYN_FUNCTION(int,snd_pcm_open,(snd_pcm_t **pcm, const char *name,snd_pcm_stream_t stream, int mode)) \
    DYN_FUNCTION(int,snd_pcm_close,(snd_pcm_t *pcm))  \



/* define pointers to library functions we're going to use */
#define  DYN_FUNCTION(ret,name,sig)   \
    static ret  (*func_ ## name)sig;

DYN_SYMBOLS

#undef  DYN_FUNCTION

#define func_snd_pcm_hw_params_alloca(ptr) \
    do { assert(ptr); *ptr = (snd_pcm_hw_params_t *) alloca(func_snd_pcm_hw_params_sizeof()); memset(*ptr, 0, func_snd_pcm_hw_params_sizeof()); } while (0)

#define func_snd_pcm_sw_params_alloca(ptr) \
    do { assert(ptr); *ptr = (snd_pcm_sw_params_t *) alloca(func_snd_pcm_sw_params_sizeof()); memset(*ptr, 0, func_snd_pcm_sw_params_sizeof()); } while (0)

static void*  alsa_lib;

typedef struct ALSAVoiceOut {
    HWVoiceOut hw;
    void *pcm_buf;
    snd_pcm_t *handle;
} ALSAVoiceOut;

typedef struct ALSAVoiceIn {
    HWVoiceIn hw;
    snd_pcm_t *handle;
    void *pcm_buf;
} ALSAVoiceIn;

static struct {
    int size_in_usec_in;
    int size_in_usec_out;
    const char *pcm_name_in;
    const char *pcm_name_out;
    unsigned int buffer_size_in;
    unsigned int period_size_in;
    unsigned int buffer_size_out;
    unsigned int period_size_out;
    unsigned int threshold;

    int buffer_size_in_overriden;
    int period_size_in_overriden;

    int buffer_size_out_overriden;
    int period_size_out_overriden;
    int verbose;
} conf = {
#ifdef HIGH_LATENCY
    .size_in_usec_in = 1,
    .size_in_usec_out = 1,
#endif
    .pcm_name_out = "default",
    .pcm_name_in = "default",
#ifdef HIGH_LATENCY
    .buffer_size_in = 400000,
    .period_size_in = 400000 / 4,
    .buffer_size_out = 400000,
    .period_size_out = 400000 / 4,
#else
#define DEFAULT_BUFFER_SIZE 1024
#define DEFAULT_PERIOD_SIZE 256
    .buffer_size_in = DEFAULT_BUFFER_SIZE * 4,
    .period_size_in = DEFAULT_PERIOD_SIZE * 4,
    .buffer_size_out = DEFAULT_BUFFER_SIZE,
    .period_size_out = DEFAULT_PERIOD_SIZE,
    .buffer_size_in_overriden = 0,
    .buffer_size_out_overriden = 0,
    .period_size_in_overriden = 0,
    .period_size_out_overriden = 0,
#endif
    .threshold = 0,
    .verbose = 0
};

struct alsa_params_req {
    int freq;
    audfmt_e fmt;
    int nchannels;
    unsigned int buffer_size;
    unsigned int period_size;
};

struct alsa_params_obt {
    int freq;
    audfmt_e fmt;
    int nchannels;
    snd_pcm_uframes_t samples;
};

static void GCC_FMT_ATTR (2, 3) alsa_logerr (int err, const char *fmt, ...)
{
    va_list ap;

    va_start (ap, fmt);
    AUD_vlog (AUDIO_CAP, fmt, ap);
    va_end (ap);

    AUD_log (AUDIO_CAP, "Reason: %s\n", func_snd_strerror (err));
}

static void GCC_FMT_ATTR (3, 4) alsa_logerr2 (
    int err,
    const char *typ,
    const char *fmt,
    ...
    )
{
    va_list ap;

    AUD_log (AUDIO_CAP, "Could not initialize %s\n", typ);

    va_start (ap, fmt);
    AUD_vlog (AUDIO_CAP, fmt, ap);
    va_end (ap);

    AUD_log (AUDIO_CAP, "Reason: %s\n", func_snd_strerror (err));
}

static void alsa_anal_close (snd_pcm_t **handlep)
{
    int err = func_snd_pcm_close (*handlep);
    if (err) {
        alsa_logerr (err, "Failed to close PCM handle %p\n", *handlep);
    }
    *handlep = NULL;
}

static int alsa_write (SWVoiceOut *sw, void *buf, int len)
{
    return audio_pcm_sw_write (sw, buf, len);
}

static int aud_to_alsafmt (audfmt_e fmt)
{
    switch (fmt) {
    case AUD_FMT_S8:
        return SND_PCM_FORMAT_S8;

    case AUD_FMT_U8:
        return SND_PCM_FORMAT_U8;

    case AUD_FMT_S16:
        return SND_PCM_FORMAT_S16_LE;

    case AUD_FMT_U16:
        return SND_PCM_FORMAT_U16_LE;

    default:
        dolog ("Internal logic error: Bad audio format %d\n", fmt);
#ifdef DEBUG_AUDIO
        abort ();
#endif
        return SND_PCM_FORMAT_U8;
    }
}

static int alsa_to_audfmt (int alsafmt, audfmt_e *fmt, int *endianness)
{
    switch (alsafmt) {
    case SND_PCM_FORMAT_S8:
        *endianness = 0;
        *fmt = AUD_FMT_S8;
        break;

    case SND_PCM_FORMAT_U8:
        *endianness = 0;
        *fmt = AUD_FMT_U8;
        break;

    case SND_PCM_FORMAT_S16_LE:
        *endianness = 0;
        *fmt = AUD_FMT_S16;
        break;

    case SND_PCM_FORMAT_U16_LE:
        *endianness = 0;
        *fmt = AUD_FMT_U16;
        break;

    case SND_PCM_FORMAT_S16_BE:
        *endianness = 1;
        *fmt = AUD_FMT_S16;
        break;

    case SND_PCM_FORMAT_U16_BE:
        *endianness = 1;
        *fmt = AUD_FMT_U16;
        break;

    default:
        dolog ("Unrecognized audio format %d\n", alsafmt);
        return -1;
    }

    return 0;
}

#if defined DEBUG_MISMATCHES || defined DEBUG
static void alsa_dump_info (struct alsa_params_req *req,
                            struct alsa_params_obt *obt)
{
    dolog ("parameter | requested value | obtained value\n");
    dolog ("format    |      %10d |     %10d\n", req->fmt, obt->fmt);
    dolog ("channels  |      %10d |     %10d\n",
           req->nchannels, obt->nchannels);
    dolog ("frequency |      %10d |     %10d\n", req->freq, obt->freq);
    dolog ("============================================\n");
    dolog ("requested: buffer size %d period size %d\n",
           req->buffer_size, req->period_size);
    dolog ("obtained: samples %ld\n", obt->samples);
}
#endif

static void alsa_set_threshold (snd_pcm_t *handle, snd_pcm_uframes_t threshold)
{
    int err;
    snd_pcm_sw_params_t *sw_params;

    func_snd_pcm_sw_params_alloca (&sw_params);

    err = func_snd_pcm_sw_params_current (handle, sw_params);
    if (err < 0) {
        dolog ("Could not fully initialize DAC\n");
        alsa_logerr (err, "Failed to get current software parameters\n");
        return;
    }

    err = func_snd_pcm_sw_params_set_start_threshold (handle, sw_params, threshold);
    if (err < 0) {
        dolog ("Could not fully initialize DAC\n");
        alsa_logerr (err, "Failed to set software threshold to %ld\n",
                     threshold);
        return;
    }

    err = func_snd_pcm_sw_params (handle, sw_params);
    if (err < 0) {
        dolog ("Could not fully initialize DAC\n");
        alsa_logerr (err, "Failed to set software parameters\n");
        return;
    }
}

static int alsa_open (int in, struct alsa_params_req *req,
                      struct alsa_params_obt *obt, snd_pcm_t **handlep)
{
    snd_pcm_t *handle;
    snd_pcm_hw_params_t *hw_params;
    int err, freq, nchannels;
    const char *pcm_name = in ? conf.pcm_name_in : conf.pcm_name_out;
    unsigned int period_size, buffer_size;
    snd_pcm_uframes_t obt_buffer_size;
    const char *typ = in ? "ADC" : "DAC";

    freq = req->freq;
    period_size = req->period_size;
    buffer_size = req->buffer_size;
    nchannels = req->nchannels;

    func_snd_pcm_hw_params_alloca (&hw_params);

    err = func_snd_pcm_open (
        &handle,
        pcm_name,
        in ? SND_PCM_STREAM_CAPTURE : SND_PCM_STREAM_PLAYBACK,
        SND_PCM_NONBLOCK
        );
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to open `%s':\n", pcm_name);
        return -1;
    }

    err = func_snd_pcm_hw_params_any (handle, hw_params);
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to initialize hardware parameters\n");
        goto err;
    }

    err = func_snd_pcm_hw_params_set_access (
        handle,
        hw_params,
        SND_PCM_ACCESS_RW_INTERLEAVED
        );
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to set access type\n");
        goto err;
    }

    err = func_snd_pcm_hw_params_set_format (handle, hw_params, req->fmt);
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to set format %d\n", req->fmt);
        goto err;
    }

    err = func_snd_pcm_hw_params_set_rate_near (handle, hw_params, (unsigned*)&freq, 0);
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to set frequency %d\n", req->freq);
        goto err;
    }

    err = func_snd_pcm_hw_params_set_channels_near (
        handle,
        hw_params,
        (unsigned*)&nchannels
        );
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to set number of channels %d\n",
                      req->nchannels);
        goto err;
    }

    if (nchannels != 1 && nchannels != 2) {
        alsa_logerr2 (err, typ,
                      "Can not handle obtained number of channels %d\n",
                      nchannels);
        goto err;
    }

    if (!((in && conf.size_in_usec_in) || (!in && conf.size_in_usec_out))) {
        if (!buffer_size) {
            buffer_size = DEFAULT_BUFFER_SIZE;
            period_size= DEFAULT_PERIOD_SIZE;
        }
    }

    if (buffer_size) {
        if ((in && conf.size_in_usec_in) || (!in && conf.size_in_usec_out)) {
            if (period_size) {
                err = func_snd_pcm_hw_params_set_period_time_near (
                    handle,
                    hw_params,
                    &period_size,
                    0
                    );
                if (err < 0) {
                    alsa_logerr2 (err, typ,
                                  "Failed to set period time %d\n",
                                  req->period_size);
                    goto err;
                }
            }

            err = func_snd_pcm_hw_params_set_buffer_time_near (
                handle,
                hw_params,
                &buffer_size,
                0
                );

            if (err < 0) {
                alsa_logerr2 (err, typ,
                              "Failed to set buffer time %d\n",
                              req->buffer_size);
                goto err;
            }
        }
        else {
            int dir;
            snd_pcm_uframes_t minval;

            if (period_size) {
                minval = period_size;
                dir = 0;

                err = func_snd_pcm_hw_params_get_period_size_min (
                    hw_params,
                    &minval,
                    &dir
                    );
                if (err < 0) {
                    alsa_logerr (
                        err,
                        "Could not get minmal period size for %s\n",
                        typ
                        );
                }
                else {
                    if (period_size < minval) {
                        if ((in && conf.period_size_in_overriden)
                            || (!in && conf.period_size_out_overriden)) {
                            dolog ("%s period size(%d) is less "
                                   "than minmal period size(%ld)\n",
                                   typ,
                                   period_size,
                                   minval);
                        }
                        period_size = minval;
                    }
                }

                err = func_snd_pcm_hw_params_set_period_size (
                    handle,
                    hw_params,
                    period_size,
                    0
                    );
                if (err < 0) {
                    alsa_logerr2 (err, typ, "Failed to set period size %d\n",
                                  req->period_size);
                    goto err;
                }
            }

            minval = buffer_size;
            err = func_snd_pcm_hw_params_get_buffer_size_min (
                hw_params,
                &minval
                );
            if (err < 0) {
                alsa_logerr (err, "Could not get minmal buffer size for %s\n",
                             typ);
            }
            else {
                if (buffer_size < minval) {
                    if ((in && conf.buffer_size_in_overriden)
                        || (!in && conf.buffer_size_out_overriden)) {
                        dolog (
                            "%s buffer size(%d) is less "
                            "than minimal buffer size(%ld)\n",
                            typ,
                            buffer_size,
                            minval
                            );
                    }
                    buffer_size = minval;
                }
            }

            err = func_snd_pcm_hw_params_set_buffer_size (
                handle,
                hw_params,
                buffer_size
                );
            if (err < 0) {
                alsa_logerr2 (err, typ, "Failed to set buffer size %d\n",
                              req->buffer_size);
                goto err;
            }
        }
    }
    else {
        dolog ("warning: Buffer size is not set\n");
    }

    err = func_snd_pcm_hw_params (handle, hw_params);
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to apply audio parameters\n");
        goto err;
    }

    err = func_snd_pcm_hw_params_get_buffer_size (hw_params, &obt_buffer_size);
    if (err < 0) {
        alsa_logerr2 (err, typ, "Failed to get buffer size\n");
        goto err;
    }

    err = func_snd_pcm_prepare (handle);
    if (err < 0) {
        alsa_logerr2 (err, typ, "Could not prepare handle %p\n", handle);
        goto err;
    }

    if (!in && conf.threshold) {
        snd_pcm_uframes_t threshold;
        int bytes_per_sec;

        bytes_per_sec = freq
            << (nchannels == 2)
            << (req->fmt == AUD_FMT_S16 || req->fmt == AUD_FMT_U16);

        threshold = (conf.threshold * bytes_per_sec) / 1000;
        alsa_set_threshold (handle, threshold);
    }

    obt->fmt = req->fmt;
    obt->nchannels = nchannels;
    obt->freq = freq;
    obt->samples = obt_buffer_size;
    *handlep = handle;

#if defined DEBUG_MISMATCHES || defined DEBUG
    if (obt->fmt != req->fmt ||
        obt->nchannels != req->nchannels ||
        obt->freq != req->freq) {
        dolog ("Audio paramters mismatch for %s\n", typ);
        alsa_dump_info (req, obt);
    }
#endif

#ifdef DEBUG
    alsa_dump_info (req, obt);
#endif
    return 0;

 err:
    alsa_anal_close (&handle);
    return -1;
}

static int alsa_recover (snd_pcm_t *handle)
{
    int err = func_snd_pcm_prepare (handle);
    if (err < 0) {
        alsa_logerr (err, "Failed to prepare handle %p\n", handle);
        return -1;
    }
    return 0;
}

static snd_pcm_sframes_t alsa_get_avail (snd_pcm_t *handle)
{
    snd_pcm_sframes_t avail;

    avail = func_snd_pcm_avail_update (handle);
    if (avail < 0) {
        if (avail == -EPIPE) {
            if (!alsa_recover (handle)) {
                avail = func_snd_pcm_avail_update (handle);
            }
        }

        if (avail < 0) {
            alsa_logerr (avail,
                         "Could not obtain number of available frames\n");
            return -1;
        }
    }

    return avail;
}

static int alsa_run_out (HWVoiceOut *hw)
{
    ALSAVoiceOut *alsa = (ALSAVoiceOut *) hw;
    int rpos, live, decr;
    int samples;
    uint8_t *dst;
    st_sample_t *src;
    snd_pcm_sframes_t avail;

    live = audio_pcm_hw_get_live_out (hw);
    if (!live) {
        return 0;
    }

    avail = alsa_get_avail (alsa->handle);
    if (avail < 0) {
        dolog ("Could not get number of available playback frames\n");
        return 0;
    }

    decr = audio_MIN (live, avail);
    samples = decr;
    rpos = hw->rpos;
    while (samples) {
        int left_till_end_samples = hw->samples - rpos;
        int len = audio_MIN (samples, left_till_end_samples);
        snd_pcm_sframes_t written;

        src = hw->mix_buf + rpos;
        dst = advance (alsa->pcm_buf, rpos << hw->info.shift);

        hw->clip (dst, src, len);

        while (len) {
            written = func_snd_pcm_writei (alsa->handle, dst, len);

            if (written <= 0) {
                switch (written) {
                case 0:
                    if (conf.verbose) {
                        dolog ("Failed to write %d frames (wrote zero)\n", len);
                    }
                    goto exit;

                case -EPIPE:
                    if (alsa_recover (alsa->handle)) {
                        alsa_logerr (written, "Failed to write %d frames\n",
                                     len);
                        goto exit;
                    }
                    if (conf.verbose) {
                        dolog ("Recovering from playback xrun\n");
                    }
                    continue;

                case -EAGAIN:
                    goto exit;

                default:
                    alsa_logerr (written, "Failed to write %d frames to %p\n",
                                 len, dst);
                    goto exit;
                }
            }

            rpos = (rpos + written) % hw->samples;
            samples -= written;
            len -= written;
            dst = advance (dst, written << hw->info.shift);
            src += written;
        }
    }

 exit:
    hw->rpos = rpos;
    return decr;
}

static void alsa_fini_out (HWVoiceOut *hw)
{
    ALSAVoiceOut *alsa = (ALSAVoiceOut *) hw;

    ldebug ("alsa_fini\n");
    alsa_anal_close (&alsa->handle);

    if (alsa->pcm_buf) {
        qemu_free (alsa->pcm_buf);
        alsa->pcm_buf = NULL;
    }
}

static int alsa_init_out (HWVoiceOut *hw, audsettings_t *as)
{
    ALSAVoiceOut *alsa = (ALSAVoiceOut *) hw;
    struct alsa_params_req req;
    struct alsa_params_obt obt;
    audfmt_e effective_fmt;
    int endianness;
    int err, result = -1;
    snd_pcm_t *handle;
    audsettings_t obt_as;

    /* shut alsa debug spew */
    if (!D_ACTIVE)
        stdio_disable();

    req.fmt = aud_to_alsafmt (as->fmt);
    req.freq = as->freq;
    req.nchannels = as->nchannels;
    req.period_size = conf.period_size_out;
    req.buffer_size = conf.buffer_size_out;

    if (alsa_open (0, &req, &obt, &handle)) {
        goto Exit;
    }

    err = alsa_to_audfmt (obt.fmt, &effective_fmt, &endianness);
    if (err) {
        alsa_anal_close (&handle);
        goto Exit;
    }

    obt_as.freq = obt.freq;
    obt_as.nchannels = obt.nchannels;
    obt_as.fmt = effective_fmt;
    obt_as.endianness = endianness;

    audio_pcm_init_info (&hw->info, &obt_as);
    hw->samples = obt.samples;

    alsa->pcm_buf = audio_calloc (AUDIO_FUNC, obt.samples, 1 << hw->info.shift);
    if (!alsa->pcm_buf) {
        dolog ("Could not allocate DAC buffer (%d samples, each %d bytes)\n",
               hw->samples, 1 << hw->info.shift);
        alsa_anal_close (&handle);
        goto Exit;
    }

    alsa->handle = handle;
    result       = 0;  /* success */

Exit:
    if (!D_ACTIVE)
        stdio_enable();

    return result;
}

static int alsa_voice_ctl (snd_pcm_t *handle, const char *typ, int pause)
{
    int err;

    if (pause) {
        err = func_snd_pcm_drop (handle);
        if (err < 0) {
            alsa_logerr (err, "Could not stop %s\n", typ);
            return -1;
        }
    }
    else {
        err = func_snd_pcm_prepare (handle);
        if (err < 0) {
            alsa_logerr (err, "Could not prepare handle for %s\n", typ);
            return -1;
        }
    }

    return 0;
}

static int alsa_ctl_out (HWVoiceOut *hw, int cmd, ...)
{
    ALSAVoiceOut *alsa = (ALSAVoiceOut *) hw;

    switch (cmd) {
    case VOICE_ENABLE:
        ldebug ("enabling voice\n");
        return alsa_voice_ctl (alsa->handle, "playback", 0);

    case VOICE_DISABLE:
        ldebug ("disabling voice\n");
        return alsa_voice_ctl (alsa->handle, "playback", 1);
    }

    return -1;
}

static int alsa_init_in (HWVoiceIn *hw, audsettings_t *as)
{
    ALSAVoiceIn *alsa = (ALSAVoiceIn *) hw;
    struct alsa_params_req req;
    struct alsa_params_obt obt;
    int endianness;
    int err, result = -1;
    audfmt_e effective_fmt;
    snd_pcm_t *handle;
    audsettings_t obt_as;

    /* shut alsa debug spew */
    if (!D_ACTIVE)
        stdio_disable();

    req.fmt = aud_to_alsafmt (as->fmt);
    req.freq = as->freq;
    req.nchannels = as->nchannels;
    req.period_size = conf.period_size_in;
    req.buffer_size = conf.buffer_size_in;

    if (alsa_open (1, &req, &obt, &handle)) {
        goto Exit;
    }

    err = alsa_to_audfmt (obt.fmt, &effective_fmt, &endianness);
    if (err) {
        alsa_anal_close (&handle);
        goto Exit;
    }

    obt_as.freq = obt.freq;
    obt_as.nchannels = obt.nchannels;
    obt_as.fmt = effective_fmt;
    obt_as.endianness = endianness;

    audio_pcm_init_info (&hw->info, &obt_as);
    hw->samples = obt.samples;

    alsa->pcm_buf = audio_calloc (AUDIO_FUNC, hw->samples, 1 << hw->info.shift);
    if (!alsa->pcm_buf) {
        dolog ("Could not allocate ADC buffer (%d samples, each %d bytes)\n",
               hw->samples, 1 << hw->info.shift);
        alsa_anal_close (&handle);
        goto Exit;
    }

    alsa->handle = handle;
    result       = 0;  /* success */

Exit:
    if (!D_ACTIVE)
        stdio_enable();

    return result;
}

static void alsa_fini_in (HWVoiceIn *hw)
{
    ALSAVoiceIn *alsa = (ALSAVoiceIn *) hw;

    alsa_anal_close (&alsa->handle);

    if (alsa->pcm_buf) {
        qemu_free (alsa->pcm_buf);
        alsa->pcm_buf = NULL;
    }
}

static int alsa_run_in (HWVoiceIn *hw)
{
    ALSAVoiceIn *alsa = (ALSAVoiceIn *) hw;
    int hwshift = hw->info.shift;
    int i;
    int live = audio_pcm_hw_get_live_in (hw);
    int dead = hw->samples - live;
    int decr;
    struct {
        int add;
        int len;
    } bufs[2] = {
        { hw->wpos, 0 },
        { 0, 0 }
    };
    snd_pcm_sframes_t avail;
    snd_pcm_uframes_t read_samples = 0;

    if (!dead) {
        return 0;
    }

    avail = alsa_get_avail (alsa->handle);
    if (avail < 0) {
        dolog ("Could not get number of captured frames\n");
        return 0;
    }

    if (!avail && (func_snd_pcm_state (alsa->handle) == SND_PCM_STATE_PREPARED)) {
        avail = hw->samples;
    }

    decr = audio_MIN (dead, avail);
    if (!decr) {
        return 0;
    }

    if (hw->wpos + decr > hw->samples) {
        bufs[0].len = (hw->samples - hw->wpos);
        bufs[1].len = (decr - (hw->samples - hw->wpos));
    }
    else {
        bufs[0].len = decr;
    }

    for (i = 0; i < 2; ++i) {
        void *src;
        st_sample_t *dst;
        snd_pcm_sframes_t nread;
        snd_pcm_uframes_t len;

        len = bufs[i].len;

        src = advance (alsa->pcm_buf, bufs[i].add << hwshift);
        dst = hw->conv_buf + bufs[i].add;

        while (len) {
            nread = func_snd_pcm_readi (alsa->handle, src, len);

            if (nread <= 0) {
                switch (nread) {
                case 0:
                    if (conf.verbose) {
                        dolog ("Failed to read %ld frames (read zero)\n", len);
                    }
                    goto exit;

                case -EPIPE:
                    if (alsa_recover (alsa->handle)) {
                        alsa_logerr (nread, "Failed to read %ld frames\n", len);
                        goto exit;
                    }
                    if (conf.verbose) {
                        dolog ("Recovering from capture xrun\n");
                    }
                    continue;

                case -EAGAIN:
                    goto exit;

                default:
                    alsa_logerr (
                        nread,
                        "Failed to read %ld frames from %p\n",
                        len,
                        src
                        );
                    goto exit;
                }
            }

            hw->conv (dst, src, nread, &nominal_volume);

            src = advance (src, nread << hwshift);
            dst += nread;

            read_samples += nread;
            len -= nread;
        }
    }

 exit:
    hw->wpos = (hw->wpos + read_samples) % hw->samples;
    return read_samples;
}

static int alsa_read (SWVoiceIn *sw, void *buf, int size)
{
    return audio_pcm_sw_read (sw, buf, size);
}

static int alsa_ctl_in (HWVoiceIn *hw, int cmd, ...)
{
    ALSAVoiceIn *alsa = (ALSAVoiceIn *) hw;

    switch (cmd) {
    case VOICE_ENABLE:
        ldebug ("enabling voice\n");
        return alsa_voice_ctl (alsa->handle, "capture", 0);

    case VOICE_DISABLE:
        ldebug ("disabling voice\n");
        return alsa_voice_ctl (alsa->handle, "capture", 1);
    }

    return -1;
}

static void *alsa_audio_init (void)
{
    void*    result = NULL;

    alsa_lib = dlopen( "libasound.so", RTLD_NOW );
    if (alsa_lib == NULL)
        alsa_lib = dlopen( "libasound.so.2", RTLD_NOW );

    if (alsa_lib == NULL) {
        ldebug("could not find libasound on this system\n");
        goto Exit;
    }

#undef  DYN_FUNCTION
#define DYN_FUNCTION(ret,name,sig)                                               \
    do {                                                                         \
        (func_ ##name) = dlsym( alsa_lib, STRINGIFY(name) );                     \
        if ((func_##name) == NULL) {                                             \
            ldebug("could not find %s in libasound\n", STRINGIFY(name)); \
            goto Fail;                                                           \
        }                                                                        \
    } while (0);

    DYN_SYMBOLS

    result = &conf;
    goto Exit;

Fail:
    ldebug("%s: failed to open library\n", __FUNCTION__);
    dlclose(alsa_lib);

Exit:
    return result;
}

static void alsa_audio_fini (void *opaque)
{
    if (alsa_lib != NULL) {
        dlclose(alsa_lib);
        alsa_lib = NULL;
    }
    (void) opaque;
}

static struct audio_option alsa_options[] = {
    {"DAC_SIZE_IN_USEC", AUD_OPT_BOOL, &conf.size_in_usec_out,
     "DAC period/buffer size in microseconds (otherwise in frames)", NULL, 0},
    {"DAC_PERIOD_SIZE", AUD_OPT_INT, &conf.period_size_out,
     "DAC period size", &conf.period_size_out_overriden, 0},
    {"DAC_BUFFER_SIZE", AUD_OPT_INT, &conf.buffer_size_out,
     "DAC buffer size", &conf.buffer_size_out_overriden, 0},

    {"ADC_SIZE_IN_USEC", AUD_OPT_BOOL, &conf.size_in_usec_in,
     "ADC period/buffer size in microseconds (otherwise in frames)", NULL, 0},
    {"ADC_PERIOD_SIZE", AUD_OPT_INT, &conf.period_size_in,
     "ADC period size", &conf.period_size_in_overriden, 0},
    {"ADC_BUFFER_SIZE", AUD_OPT_INT, &conf.buffer_size_in,
     "ADC buffer size", &conf.buffer_size_in_overriden, 0},

    {"THRESHOLD", AUD_OPT_INT, &conf.threshold,
     "(undocumented)", NULL, 0},

    {"DAC_DEV", AUD_OPT_STR, &conf.pcm_name_out,
     "DAC device name (for instance dmix)", NULL, 0},

    {"ADC_DEV", AUD_OPT_STR, &conf.pcm_name_in,
     "ADC device name", NULL, 0},

    {"VERBOSE", AUD_OPT_BOOL, &conf.verbose,
     "Behave in a more verbose way", NULL, 0},

    {NULL, 0, NULL, NULL, NULL, 0}
};

static struct audio_pcm_ops alsa_pcm_ops = {
    alsa_init_out,
    alsa_fini_out,
    alsa_run_out,
    alsa_write,
    alsa_ctl_out,

    alsa_init_in,
    alsa_fini_in,
    alsa_run_in,
    alsa_read,
    alsa_ctl_in
};

struct audio_driver alsa_audio_driver = {
    INIT_FIELD (name           = ) "alsa",
    INIT_FIELD (descr          = ) "ALSA audio (www.alsa-project.org)",
    INIT_FIELD (options        = ) alsa_options,
    INIT_FIELD (init           = ) alsa_audio_init,
    INIT_FIELD (fini           = ) alsa_audio_fini,
    INIT_FIELD (pcm_ops        = ) &alsa_pcm_ops,
    INIT_FIELD (can_be_default = ) 1,
    INIT_FIELD (max_voices_out = ) INT_MAX,
    INIT_FIELD (max_voices_in  = ) INT_MAX,
    INIT_FIELD (voice_size_out = ) sizeof (ALSAVoiceOut),
    INIT_FIELD (voice_size_in  = ) sizeof (ALSAVoiceIn)
};
