/* 
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

#define LOG_TAG "GLLogger"

#include <ctype.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>

#include <sys/ioctl.h>

#if HAVE_ANDROID_OS
#include <linux/android_pmem.h>
#endif

#include <GLES/egl.h>

#include <cutils/log.h>
#include <cutils/atomic.h>
#include <cutils/properties.h>
#include <cutils/memory.h>

#include <utils/IMemory.h>
#include <utils/KeyedVector.h>
#include <utils/threads.h>
#include <utils/IServiceManager.h>
#include <utils/IPCThreadState.h>
#include <utils/Parcel.h>

#include <ui/EGLDisplaySurface.h>
#include <ui/ISurfaceComposer.h>

#include "gl_logger.h"

#undef NELEM

#define GL_LOGGER                   0
#define USE_SLOW_BINDING            0
#define NELEM(x)                    (sizeof(x)/sizeof(*(x)))
#define MAX_NUMBER_OF_GL_EXTENSIONS 32
#define MAKE_CONFIG(_impl, _index)  ((EGLConfig)(((_impl)<<24) | (_index)))
#define setError(_e, _r) setErrorEtc(__FUNCTION__, __LINE__, _e, _r)

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------

//  EGLDisplay are global, not attached to a given thread
static const unsigned int NUM_DISPLAYS = 1;
static const unsigned int IMPL_HARDWARE                 = 0;
static const unsigned int IMPL_SOFTWARE                 = 1;
static const unsigned int IMPL_HARDWARE_CONTEXT_LOST    = 2;
static const unsigned int IMPL_SOFTWARE_CONTEXT_LOST    = 3;
static const unsigned int IMPL_NO_CONTEXT               = 4;

// ----------------------------------------------------------------------------

struct gl_hooks_t;

struct egl_connection_t
{
    void volatile *     dso;
    gl_hooks_t *        hooks;
    EGLint              major;
    EGLint              minor;
    int                 unavailable;
};

template <int MAGIC>
struct egl_object_t
{
    egl_object_t() : magic(MAGIC) { }
    ~egl_object_t() { magic = 0; }
    bool isValid() const { return magic == MAGIC; }
private:
    uint32_t    magic;
};

struct egl_display_t : public egl_object_t<'_dpy'>
{
    EGLDisplay  dpys[2];
    EGLConfig*  configs[2];
    EGLint      numConfigs[2];
    EGLint      numTotalConfigs;
    char const* extensionsString;
    volatile int32_t refs;
    struct strings_t {
        char const * vendor;
        char const * version;
        char const * clientApi;
        char const * extensions;
        char const * extensions_config;
    };
    strings_t   queryString[2];
};

struct egl_surface_t : public egl_object_t<'_srf'>
{
    egl_surface_t(EGLDisplay dpy, EGLSurface surface,
            NativeWindowType window, int impl, egl_connection_t const* cnx) 
    : dpy(dpy), surface(surface), window(window), impl(impl), cnx(cnx)
    {
        // NOTE: window must be incRef'ed and connected already
    }
    ~egl_surface_t() {
        if (window) {
            if (window->disconnect)
                window->disconnect(window);
            window->decRef(window);
        }
    }
    EGLDisplay                  dpy;
    EGLSurface                  surface;
    NativeWindowType            window;
    int                         impl;
    egl_connection_t const*     cnx;
};

struct egl_context_t : public egl_object_t<'_ctx'>
{
    egl_context_t(EGLDisplay dpy, EGLContext context,
            int impl, egl_connection_t const* cnx) 
    : dpy(dpy), context(context), read(0), draw(0), impl(impl), cnx(cnx)
    {
    }
    EGLDisplay                  dpy;
    EGLContext                  context;
    EGLSurface                  read;
    EGLSurface                  draw;
    int                         impl;
    egl_connection_t const*     cnx;
};

struct tls_t
{
    tls_t() : error(EGL_SUCCESS), ctx(0) { }
    EGLint      error;
    EGLContext  ctx;
};


// GL / EGL hooks

typedef void(*proc_t)();

struct gl_hooks_t {
    struct gl_t {
        #define GL_ENTRY(_r, _api, ...) _r (*_api)(__VA_ARGS__);
        #include "gl_entries.cpp"
        #undef GL_ENTRY
    } gl;
    struct egl_t {
        #define EGL_ENTRY(_r, _api, ...) _r (*_api)(__VA_ARGS__);
        #include "egl_entries.cpp"
        #undef EGL_ENTRY
    } egl;
    struct gl_ext_t {
        void (*extensions[MAX_NUMBER_OF_GL_EXTENSIONS])(void);
    } ext;
};

static char const * const gl_names[] = {
    #define GL_ENTRY(_r, _api, ...) #_api,
    #include "gl_entries.cpp"
    #undef GL_ENTRY
    NULL
};

static char const * const egl_names[] = {
    #define EGL_ENTRY(_r, _api, ...) #_api,
    #include "egl_entries.cpp"
    #undef EGL_ENTRY
    NULL
};

static void gl_unimplemented() {
    LOGE("called unimplemented OpenGL ES API");
}

// ----------------------------------------------------------------------------

static egl_connection_t gEGLImpl[2];
static egl_display_t gDisplay[NUM_DISPLAYS];
static gl_hooks_t gHooks[5];
static pthread_mutex_t gThreadLocalStorageKeyMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_key_t gEGLThreadLocalStorageKey = -1;

// ----------------------------------------------------------------------------

#if defined(HAVE_ANDROID_OS) && !USE_SLOW_BINDING && !GL_LOGGER

#include <sys/tls.h>
// We have a dedicated TLS slot in bionic
static inline void setGlThreadSpecific(gl_hooks_t const *value) {
    ((uint32_t *)__get_tls())[TLS_SLOT_OPENGL_API] = (uint32_t)value;
}
static gl_hooks_t const* getGlThreadSpecific() {
    gl_hooks_t const* hooks = (gl_hooks_t const *)(((unsigned const *)__get_tls())[TLS_SLOT_OPENGL_API]);
    if (hooks) return hooks;
    return &gHooks[IMPL_NO_CONTEXT];
}

#else

static pthread_key_t gGLWrapperKey = -1;
static inline void setGlThreadSpecific(gl_hooks_t const *value) {
    pthread_setspecific(gGLWrapperKey, value);
}
static gl_hooks_t const* getGlThreadSpecific() {
    gl_hooks_t const* hooks =  static_cast<gl_hooks_t*>(pthread_getspecific(gGLWrapperKey));
    if (hooks) return hooks;
    return &gHooks[IMPL_NO_CONTEXT];
}

#endif

static __attribute__((noinline))
const char *egl_strerror(EGLint err)
{
    switch (err){
        case EGL_SUCCESS:               return "EGL_SUCCESS";
        case EGL_NOT_INITIALIZED:       return "EGL_NOT_INITIALIZED";
        case EGL_BAD_ACCESS:            return "EGL_BAD_ACCESS";
        case EGL_BAD_ALLOC:             return "EGL_BAD_ALLOC";
        case EGL_BAD_ATTRIBUTE:         return "EGL_BAD_ATTRIBUTE";
        case EGL_BAD_CONFIG:            return "EGL_BAD_CONFIG";
        case EGL_BAD_CONTEXT:           return "EGL_BAD_CONTEXT";
        case EGL_BAD_CURRENT_SURFACE:   return "EGL_BAD_CURRENT_SURFACE";
        case EGL_BAD_DISPLAY:           return "EGL_BAD_DISPLAY";
        case EGL_BAD_MATCH:             return "EGL_BAD_MATCH";
        case EGL_BAD_NATIVE_PIXMAP:     return "EGL_BAD_NATIVE_PIXMAP";
        case EGL_BAD_NATIVE_WINDOW:     return "EGL_BAD_NATIVE_WINDOW";
        case EGL_BAD_PARAMETER:         return "EGL_BAD_PARAMETER";
        case EGL_BAD_SURFACE:           return "EGL_BAD_SURFACE";
        case EGL_CONTEXT_LOST:          return "EGL_CONTEXT_LOST";
        default: return "UNKNOWN";
    }
}

static __attribute__((noinline))
void clearTLS() {
    if (gEGLThreadLocalStorageKey != -1) {
        tls_t* tls = (tls_t*)pthread_getspecific(gEGLThreadLocalStorageKey);
        if (tls) {
            delete tls;
            pthread_setspecific(gEGLThreadLocalStorageKey, 0);
        }
    }
}

static tls_t* getTLS()
{
    tls_t* tls = (tls_t*)pthread_getspecific(gEGLThreadLocalStorageKey);
    if (tls == 0) {
        tls = new tls_t;
        pthread_setspecific(gEGLThreadLocalStorageKey, tls);
    }
    return tls;
}

template<typename T>
static __attribute__((noinline))
T setErrorEtc(const char* caller, int line, EGLint error, T returnValue) {
    if (gEGLThreadLocalStorageKey == -1) {
        pthread_mutex_lock(&gThreadLocalStorageKeyMutex);
        if (gEGLThreadLocalStorageKey == -1)
            pthread_key_create(&gEGLThreadLocalStorageKey, NULL);
        pthread_mutex_unlock(&gThreadLocalStorageKeyMutex);
    }
    tls_t* tls = getTLS();
    if (tls->error != error) {
        LOGE("%s:%d error %x (%s)", caller, line, error, egl_strerror(error));
        tls->error = error;
    }
    return returnValue;
}

static __attribute__((noinline))
GLint getError() {
    if (gEGLThreadLocalStorageKey == -1)
        return EGL_SUCCESS;
    tls_t* tls = (tls_t*)pthread_getspecific(gEGLThreadLocalStorageKey);
    if (!tls) return EGL_SUCCESS;
    GLint error = tls->error;
    tls->error = EGL_SUCCESS;
    return error;
}

static __attribute__((noinline))
void setContext(EGLContext ctx) {
    if (gEGLThreadLocalStorageKey == -1) {
        pthread_mutex_lock(&gThreadLocalStorageKeyMutex);
        if (gEGLThreadLocalStorageKey == -1)
            pthread_key_create(&gEGLThreadLocalStorageKey, NULL);
        pthread_mutex_unlock(&gThreadLocalStorageKeyMutex);
    }
    tls_t* tls = getTLS();
    tls->ctx = ctx;
}

static __attribute__((noinline))
EGLContext getContext() {
    if (gEGLThreadLocalStorageKey == -1)
        return EGL_NO_CONTEXT;
    tls_t* tls = (tls_t*)pthread_getspecific(gEGLThreadLocalStorageKey);
    if (!tls) return EGL_NO_CONTEXT;
    return tls->ctx;
}

/*****************************************************************************/

/*
 * we provide our own allocators for the GPU regions, these
 * allocators go through surfaceflinger 
 */

static Mutex                            gRegionsLock;
static request_gpu_t                    gRegions;
static sp<ISurfaceComposer>             gSurfaceManager;
ISurfaceComposer*                       GLES_localSurfaceManager = 0;

const sp<ISurfaceComposer>& getSurfaceFlinger()
{
    Mutex::Autolock _l(gRegionsLock);

    /*
     * There is a little bit of voodoo magic here. We want to access
     * surfaceflinger for allocating GPU regions, however, when we are
     * running as part of surfaceflinger, we want to bypass the
     * service manager because surfaceflinger might not be registered yet.
     * SurfaceFlinger will populate "GLES_localSurfaceManager" with its
     * own address, so we can just use that.
     */
    if (gSurfaceManager == 0) {
        if (GLES_localSurfaceManager) {
            // we're running in SurfaceFlinger's context
            gSurfaceManager =  GLES_localSurfaceManager;
        } else {
            // we're a remote process or not part of surfaceflinger,
            // go through the service manager
            sp<IServiceManager> sm = defaultServiceManager();
            if (sm != NULL) {
                sp<IBinder> binder = sm->getService(String16("SurfaceFlinger"));
                gSurfaceManager = interface_cast<ISurfaceComposer>(binder);
            }
        }
    }
    return gSurfaceManager;
}

class GPURevokeRequester : public BnGPUCallback
{
public:
    virtual void gpuLost() {
        LOGD("CONTEXT_LOST: Releasing GPU upon request from SurfaceFlinger.");
        gEGLImpl[IMPL_HARDWARE].hooks = &gHooks[IMPL_HARDWARE_CONTEXT_LOST];
    }
};

static sp<GPURevokeRequester> gRevokerCallback;

static request_gpu_t* gpu_acquire(void* user)
{
    sp<ISurfaceComposer> server( getSurfaceFlinger() );

    Mutex::Autolock _l(gRegionsLock);
    if (server == NULL) {
        return 0;
    }
    
    ISurfaceComposer::gpu_info_t info;
    gRevokerCallback = new GPURevokeRequester();
    status_t err = server->requestGPU(gRevokerCallback, &info);
    if (err != NO_ERROR) {
        LOGD("requestGPU returned %d", err);
        return 0;
    }

    bool failed = false;
    request_gpu_t* gpu = &gRegions;
    memset(gpu, 0, sizeof(*gpu));
    
    if (info.regs != 0) {
        sp<IMemoryHeap> heap(info.regs->getMemory());
        if (heap != 0) {
            int fd = heap->heapID();
            gpu->regs.fd = fd;
            gpu->regs.base = info.regs->pointer(); 
            gpu->regs.size = info.regs->size(); 
            gpu->regs.user = info.regs.get();
#if HAVE_ANDROID_OS
            struct pmem_region region;
            if (ioctl(fd, PMEM_GET_PHYS, &region) >= 0)
                gpu->regs.phys = (void*)region.offset;
#endif
            info.regs->incStrong(gpu);
        } else {
            LOGE("GPU register handle %p is invalid!", info.regs.get());
            failed = true;
        }
    }

    for (size_t i=0 ; i<info.count && !failed ; i++) {
        sp<IMemory>& region(info.regions[i].region);
        if (region != 0) {
            sp<IMemoryHeap> heap(region->getMemory());
            if (heap != 0) {
                const int fd = heap->heapID();
                gpu->gpu[i].fd = fd;
                gpu->gpu[i].base = region->pointer(); 
                gpu->gpu[i].size = region->size(); 
                gpu->gpu[i].user = region.get();
                gpu->gpu[i].offset = info.regions[i].reserved;
#if HAVE_ANDROID_OS
                struct pmem_region reg;
                if (ioctl(fd, PMEM_GET_PHYS, &reg) >= 0)
                    gpu->gpu[i].phys = (void*)reg.offset;
#endif
                region->incStrong(gpu);
            } else {
                LOGE("GPU region handle [%d, %p] is invalid!", i, region.get());
                failed = true;
            }
        }
    }
    
    if (failed) {
        // something went wrong, clean up everything!
        if (gpu->regs.user) {
            static_cast<IMemory*>(gpu->regs.user)->decStrong(gpu);
            for (size_t i=0 ; i<info.count ; i++) {
                if (gpu->gpu[i].user) {
                    static_cast<IMemory*>(gpu->gpu[i].user)->decStrong(gpu);
                }
            }
        }
    }
    
    gpu->count = info.count;
    return gpu;
}

static int gpu_release(void*, request_gpu_t* gpu)
{
    sp<IMemory> regs;

    { // scope for lock
        Mutex::Autolock _l(gRegionsLock);
        regs = static_cast<IMemory*>(gpu->regs.user);   
        gpu->regs.user = 0;
        if (regs != 0) regs->decStrong(gpu);
        
        for (int i=0 ; i<gpu->count ; i++) {
            sp<IMemory> r(static_cast<IMemory*>(gpu->gpu[i].user));
            gpu->gpu[i].user = 0;
            if (r != 0) r->decStrong(gpu);
        }
    }
    
    // there is a special transaction to relinquish the GPU
    // (it will happen automatically anyway if we don't do this)
    Parcel data, reply;
    // NOTE: this transaction does not require an interface token
    regs->asBinder()->transact(1000, data, &reply);
    return 1;
}

/*****************************************************************************/

static __attribute__((noinline))
void *load_driver(const char* driver, gl_hooks_t* hooks)
{
    void* dso = dlopen(driver, RTLD_NOW | RTLD_LOCAL);
    LOGE_IF(!dso,
            "couldn't load <%s> library (%s)",
            driver, dlerror());

    if (dso) {
        void** curr;
        char const * const * api;
        gl_hooks_t::gl_t* gl = &hooks->gl;
        curr = (void**)gl;
        api = gl_names;
        while (*api) {
            void* f = dlsym(dso, *api);
            //LOGD("<%s> @ 0x%p", *api, f);
            if (f == NULL) {
                //LOGW("<%s> not found in %s", *api, driver);
                f = (void*)gl_unimplemented;
            }
            *curr++ = f;
            api++;
        }
        gl_hooks_t::egl_t* egl = &hooks->egl;
        curr = (void**)egl;
        api = egl_names;
        while (*api) {
            void* f = dlsym(dso, *api);
            if (f == NULL) {
                //LOGW("<%s> not found in %s", *api, driver);
                f = (void*)0;
            }
            *curr++ = f;
            api++;
        }

        // hook this driver up with surfaceflinger if needed
        register_gpu_t register_gpu = 
            (register_gpu_t)dlsym(dso, "oem_register_gpu");

        if (register_gpu != NULL) {
            if (getSurfaceFlinger() != 0) {
                register_gpu(dso, gpu_acquire, gpu_release);
            }
        }
    }
    return dso;
}

template<typename T>
static __attribute__((noinline))
int binarySearch(
        T const sortedArray[], int first, int last, T key)
{
    while (first <= last) {
        int mid = (first + last) / 2;
        if (key > sortedArray[mid]) { 
            first = mid + 1;
        } else if (key < sortedArray[mid]) { 
            last = mid - 1;
        } else {
            return mid;
        }
    }
    return -1;
}

static int cmp_configs(const void* a, const void *b)
{
    EGLConfig c0 = *(EGLConfig const *)a;
    EGLConfig c1 = *(EGLConfig const *)b;
    return c0<c1 ? -1 : (c0>c1 ? 1 : 0);
}

static char const * const gVendorString     = "Android";
static char const * const gVersionString    = "1.2 Android META-EGL";
static char const * const gClientApiString  = "OpenGL ES";

struct extention_map_t {
    const char* name;
    void (*address)(void);
};

static const extention_map_t gExtentionMap[] = {
    { "eglSwapRectangleANDROID",         (void(*)())&eglSwapRectangleANDROID },
    { "eglQueryStringConfigANDROID",     (void(*)())&eglQueryStringConfigANDROID },
};

static extention_map_t gGLExtentionMap[MAX_NUMBER_OF_GL_EXTENSIONS];

static void(*findProcAddress(const char* name,
        const extention_map_t* map, size_t n))() 
{
    for (uint32_t i=0 ; i<n ; i++) {
        if (!strcmp(name, map[i].name)) {
            return map[i].address;
        }
    }
    return NULL;
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------

using namespace android;


// ----------------------------------------------------------------------------
// extensions for the framework
// ----------------------------------------------------------------------------

void glColorPointerBounds(GLint size, GLenum type, GLsizei stride,
        const GLvoid *ptr, GLsizei count) {
    glColorPointer(size, type, stride, ptr);
}
void glNormalPointerBounds(GLenum type, GLsizei stride,
        const GLvoid *pointer, GLsizei count) {
    glNormalPointer(type, stride, pointer);
}
void glTexCoordPointerBounds(GLint size, GLenum type,
        GLsizei stride, const GLvoid *pointer, GLsizei count) {
    glTexCoordPointer(size, type, stride, pointer);
}
void glVertexPointerBounds(GLint size, GLenum type,
        GLsizei stride, const GLvoid *pointer, GLsizei count) {
    glVertexPointer(size, type, stride, pointer);
}


// ----------------------------------------------------------------------------
// Actual GL wrappers
// ----------------------------------------------------------------------------

#if __OPTIMIZE__ && defined(__arm__) && !defined(__thumb__) && !USE_SLOW_BINDING && !GL_LOGGER

    #define API_ENTRY(_api) __attribute__((naked)) _api
    #define CALL_GL_API(_api, ...)                              \
         asm volatile(                                          \
            "mov   r12, #0xFFFF0FFF   \n"                       \
            "ldr   r12, [r12, #-15]   \n"                       \
            "ldr   r12, [r12, %[tls]] \n"                       \
            "cmp   r12, #0            \n"                       \
            "ldrne pc,  [r12, %[api]] \n"                       \
            "bx    lr                 \n"                       \
            :                                                   \
            : [tls] "J"(TLS_SLOT_OPENGL_API*4),                 \
              [api] "J"(__builtin_offsetof(gl_hooks_t, gl._api))    \
            :                                                   \
            );
    
    #define CALL_GL_API_RETURN(_api, ...) \
        CALL_GL_API(_api, __VA_ARGS__) \
        return 0; // placate gcc's warnings. never reached.

#else

    #define API_ENTRY(_api) _api
    #if GL_LOGGER

        #define CALL_GL_API(_api, ...)          \
            gl_hooks_t::gl_t const * const _c = &getGlThreadSpecific()->gl; \
            log_##_api(__VA_ARGS__); \
            _c->_api(__VA_ARGS__);
        
        #define CALL_GL_API_RETURN(_api, ...)   \
            gl_hooks_t::gl_t const * const _c = &getGlThreadSpecific()->gl; \
            log_##_api(__VA_ARGS__); \
            return _c->_api(__VA_ARGS__)

    #else

        #define CALL_GL_API(_api, ...)          \
            gl_hooks_t::gl_t const * const _c = &getGlThreadSpecific()->gl; \
            _c->_api(__VA_ARGS__);
        
        #define CALL_GL_API_RETURN(_api, ...)   \
            gl_hooks_t::gl_t const * const _c = &getGlThreadSpecific()->gl; \
            return _c->_api(__VA_ARGS__)

    #endif

#endif

#include "gl_api.cpp"

#undef API_ENTRY
#undef CALL_GL_API
#undef CALL_GL_API_RETURN

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------

static int gl_context_lost() {
    setGlThreadSpecific(&gHooks[IMPL_HARDWARE_CONTEXT_LOST]);
    return 0;
}
static int egl_context_lost() {
    setGlThreadSpecific(&gHooks[IMPL_HARDWARE_CONTEXT_LOST]);
    return EGL_FALSE;
}
static EGLBoolean egl_context_lost_swap_buffers(void*, void*) {
    usleep(100000); // don't use all the CPU
    setGlThreadSpecific(&gHooks[IMPL_HARDWARE_CONTEXT_LOST]);
    return EGL_FALSE;
}
static GLint egl_context_lost_get_error() {
    return EGL_CONTEXT_LOST;
}
static int ext_context_lost() {
    return 0;
}

static void gl_no_context() {
    LOGE("call to OpenGL ES API with no current context");
}
static void early_egl_init(void) 
{
#if !defined(HAVE_ANDROID_OS) || USE_SLOW_BINDING || GL_LOGGER
    pthread_key_create(&gGLWrapperKey, NULL);
#endif
    uint32_t addr = (uint32_t)((void*)gl_no_context);
    android_memset32((uint32_t*)(void*)&gHooks[IMPL_NO_CONTEXT], addr, sizeof(gHooks[IMPL_NO_CONTEXT]));
    setGlThreadSpecific(&gHooks[IMPL_NO_CONTEXT]);
}

static pthread_once_t once_control = PTHREAD_ONCE_INIT;
static int sEarlyInitState = pthread_once(&once_control, &early_egl_init);


static inline
egl_display_t* get_display(EGLDisplay dpy)
{
    uintptr_t index = uintptr_t(dpy)-1U;
    return (index >= NUM_DISPLAYS) ? NULL : &gDisplay[index];
}

static inline
egl_surface_t* get_surface(EGLSurface surface)
{
    egl_surface_t* s = (egl_surface_t *)surface;
    return s;
}

static inline
egl_context_t* get_context(EGLContext context)
{
    egl_context_t* c = (egl_context_t *)context;
    return c;
}

static egl_connection_t* validate_display_config(
        EGLDisplay dpy, EGLConfig config,
        egl_display_t const*& dp, int& impl, int& index)
{
    dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, (egl_connection_t*)NULL);

    impl = uintptr_t(config)>>24;
    if (uint32_t(impl) >= 2) {
        return setError(EGL_BAD_CONFIG, (egl_connection_t*)NULL);
    } 
    index = uintptr_t(config) & 0xFFFFFF;
    if (index >= dp->numConfigs[impl]) {
        return setError(EGL_BAD_CONFIG, (egl_connection_t*)NULL);
    }
    egl_connection_t* const cnx = &gEGLImpl[impl];
    if (cnx->dso == 0) {
        return setError(EGL_BAD_CONFIG, (egl_connection_t*)NULL);
    }
    return cnx;
}

static EGLBoolean validate_display_context(EGLDisplay dpy, EGLContext ctx)
{
    if ((uintptr_t(dpy)-1U) >= NUM_DISPLAYS)
        return setError(EGL_BAD_DISPLAY, EGL_FALSE);
    if (!get_display(dpy)->isValid())
        return setError(EGL_BAD_DISPLAY, EGL_FALSE);
    if (!ctx) // TODO: make sure context is a valid object
        return setError(EGL_BAD_CONTEXT, EGL_FALSE);
    if (!get_context(ctx)->isValid())
        return setError(EGL_BAD_CONTEXT, EGL_FALSE);
    return EGL_TRUE;
}

static EGLBoolean validate_display_surface(EGLDisplay dpy, EGLSurface surface)
{
    if ((uintptr_t(dpy)-1U) >= NUM_DISPLAYS)
        return setError(EGL_BAD_DISPLAY, EGL_FALSE);
    if (!get_display(dpy)->isValid())
        return setError(EGL_BAD_DISPLAY, EGL_FALSE);
    if (!surface) // TODO: make sure surface is a valid object
        return setError(EGL_BAD_SURFACE, EGL_FALSE);
    if (!get_surface(surface)->isValid())
        return setError(EGL_BAD_SURFACE, EGL_FALSE);
    return EGL_TRUE;
}

static void add_extension(egl_display_t* dp, char const*& p, const char* ext)
{
    if (!strstr(p, ext)) {
        p = (char const*)realloc((void*)p, strlen(p) + 1 + strlen(ext) + 1);
        strcat((char*)p, " ");
        strcat((char*)p, ext);
    }
    if (!strstr(dp->extensionsString, ext)) {
        char const*& es = dp->extensionsString;
        es = (char const*)realloc((void*)es, strlen(es) + 1 + strlen(ext) + 1);
        strcat((char*)es, " ");
        strcat((char*)es, ext);
    }    
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------

EGLDisplay eglGetDisplay(NativeDisplayType display)
{
    if (sEarlyInitState) {
        return EGL_NO_DISPLAY;
    }

    uint32_t index = uint32_t(display);
    if (index >= NUM_DISPLAYS) {
        return EGL_NO_DISPLAY;
    }
    
    EGLDisplay dpy = EGLDisplay(uintptr_t(display) + 1LU);
    egl_display_t* d = &gDisplay[index];
        
    // dynamically load all our EGL implementations for that display
    // and call into the real eglGetGisplay()
    egl_connection_t* cnx = &gEGLImpl[IMPL_SOFTWARE];
    if (cnx->dso == 0) {
        cnx->hooks = &gHooks[IMPL_SOFTWARE];
        cnx->dso = load_driver("libagl.so", cnx->hooks);
    }
    if (cnx->dso && d->dpys[IMPL_SOFTWARE]==EGL_NO_DISPLAY) {
        d->dpys[IMPL_SOFTWARE] = cnx->hooks->egl.eglGetDisplay(display);
        LOGE_IF(d->dpys[IMPL_SOFTWARE]==EGL_NO_DISPLAY,
                "No EGLDisplay for software EGL!");
    }

    cnx = &gEGLImpl[IMPL_HARDWARE];
    if (cnx->dso == 0 && cnx->unavailable == 0) {
        char value[PROPERTY_VALUE_MAX];
        property_get("debug.egl.hw", value, "1");
        if (atoi(value) != 0) {
            cnx->hooks = &gHooks[IMPL_HARDWARE];
            cnx->dso = load_driver("libhgl.so", cnx->hooks);
        } else {
            LOGD("3D hardware acceleration is disabled");
        }
    }
    if (cnx->dso && d->dpys[IMPL_HARDWARE]==EGL_NO_DISPLAY) {
        android_memset32(
                (uint32_t*)(void*)&gHooks[IMPL_HARDWARE_CONTEXT_LOST].gl,
                (uint32_t)((void*)gl_context_lost),
                sizeof(gHooks[IMPL_HARDWARE_CONTEXT_LOST].gl));
        android_memset32(
                (uint32_t*)(void*)&gHooks[IMPL_HARDWARE_CONTEXT_LOST].egl,
                (uint32_t)((void*)egl_context_lost),
                sizeof(gHooks[IMPL_HARDWARE_CONTEXT_LOST].egl));
        android_memset32(
                (uint32_t*)(void*)&gHooks[IMPL_HARDWARE_CONTEXT_LOST].ext,
                (uint32_t)((void*)ext_context_lost),
                sizeof(gHooks[IMPL_HARDWARE_CONTEXT_LOST].ext));

        gHooks[IMPL_HARDWARE_CONTEXT_LOST].egl.eglSwapBuffers =
                egl_context_lost_swap_buffers;
        
        gHooks[IMPL_HARDWARE_CONTEXT_LOST].egl.eglGetError =
                egl_context_lost_get_error;

        gHooks[IMPL_HARDWARE_CONTEXT_LOST].egl.eglTerminate =
                gHooks[IMPL_HARDWARE].egl.eglTerminate;
        
        d->dpys[IMPL_HARDWARE] = cnx->hooks->egl.eglGetDisplay(display);
        if (d->dpys[IMPL_HARDWARE] == EGL_NO_DISPLAY) {
            dlclose((void*)cnx->dso);
            cnx->dso = 0;
            // in case of failure, we want to make sure we don't try again
            // as it's expensive.
            cnx->unavailable = 1;
        }
    }

    return dpy;
}

// ----------------------------------------------------------------------------
// Initialization
// ----------------------------------------------------------------------------

EGLBoolean eglInitialize(EGLDisplay dpy, EGLint *major, EGLint *minor)
{
    egl_display_t * const dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, EGL_FALSE);

    if (android_atomic_inc(&dp->refs) > 0) {
        if (major != NULL) *major = 1;
        if (minor != NULL) *minor = 2;
        return EGL_TRUE;
    }
    
    setGlThreadSpecific(&gHooks[IMPL_NO_CONTEXT]);
    
    // initialize each EGL and
    // build our own extension string first, based on the extension we know
    // and the extension supported by our client implementation
    dp->extensionsString = strdup("EGL_ANDROID_query_string_config");
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        cnx->major = -1;
        cnx->minor = -1;
        if (cnx->dso && cnx->hooks->egl.eglInitialize(
                dp->dpys[i], &cnx->major, &cnx->minor)) {

            //LOGD("initialized %d dpy=%p, ver=%d.%d, cnx=%p",
            //        i, dp->dpys[i], cnx->major, cnx->minor, cnx);

            // get the query-strings for this display for each implementation
            dp->queryString[i].vendor =
                cnx->hooks->egl.eglQueryString(dp->dpys[i], EGL_VENDOR);
            dp->queryString[i].version =
                cnx->hooks->egl.eglQueryString(dp->dpys[i], EGL_VERSION);
            dp->queryString[i].extensions = strdup(
                cnx->hooks->egl.eglQueryString(dp->dpys[i], EGL_EXTENSIONS));
            dp->queryString[i].clientApi =
                cnx->hooks->egl.eglQueryString(dp->dpys[i], EGL_CLIENT_APIS);
            
            // Dynamically insert extensions we know about
            if (cnx->hooks->egl.eglSwapRectangleANDROID)
                add_extension(dp, dp->queryString[i].extensions,
                        "EGL_ANDROID_swap_rectangle");

            if (cnx->hooks->egl.eglQueryStringConfigANDROID)
                add_extension(dp, dp->queryString[i].extensions,
                        "EGL_ANDROID_query_string_config");
        }
    }
            
    // Build the extension list that depends on the current config.
    // It is the intersection of our extension list and the
    // underlaying EGL's extensions list
    EGLBoolean res = EGL_FALSE;
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso && cnx->major>=0 && cnx->minor>=0) {
            char const* const their_extensions = dp->queryString[i].extensions;            
            char* our_extensions = strdup(dp->extensionsString);
            char* const our_extensions_org = our_extensions;
            char* extensions_config = (char*)calloc(strlen(our_extensions)+2, 1);
            char* p;
            do {
                p = strchr(our_extensions, ' ');
                if (p)  *p++ = 0;
                else    p = strchr(our_extensions, 0);
                if (strstr(their_extensions, our_extensions)) {
                    strcat(extensions_config, our_extensions);
                    strcat(extensions_config, " ");
                }
                our_extensions = p;
            } while (*p);
            free((void*)our_extensions_org);

            // remove the trailling white space
            if (extensions_config[0] != 0) {
                size_t l = strlen(extensions_config) - 1; // new size
                extensions_config[l] = 0; // remove the trailling white space
                extensions_config = (char*)realloc(extensions_config, l+1);
            } else {
                extensions_config = (char*)realloc(extensions_config, 1);
            }
            dp->queryString[i].extensions_config = extensions_config;

            EGLint n;
            if (cnx->hooks->egl.eglGetConfigs(dp->dpys[i], 0, 0, &n)) {
                dp->configs[i] = (EGLConfig*)malloc(sizeof(EGLConfig)*n);
                if (dp->configs[i]) {
                    if (cnx->hooks->egl.eglGetConfigs(
                            dp->dpys[i], dp->configs[i], n, &dp->numConfigs[i]))
                    {
                        // sort the configurations so we can do binary searches
                        qsort(  dp->configs[i],
                                dp->numConfigs[i],
                                sizeof(EGLConfig), cmp_configs);

                        dp->numTotalConfigs += n;
                        res = EGL_TRUE;
                    }
                }
            }
        }
    }

    if (res == EGL_TRUE) {
        if (major != NULL) *major = 1;
        if (minor != NULL) *minor = 2;
        return EGL_TRUE;
    }
    return setError(EGL_NOT_INITIALIZED, EGL_FALSE);
}

EGLBoolean eglTerminate(EGLDisplay dpy)
{
    egl_display_t* const dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, EGL_FALSE);
    if (android_atomic_dec(&dp->refs) != 1)
        return EGL_TRUE;
        
    EGLBoolean res = EGL_FALSE;
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            cnx->hooks->egl.eglTerminate(dp->dpys[i]);
            
            /* REVISIT: it's unclear what to do if eglTerminate() fails,
             * on one end we shouldn't care, on the other end if it fails
             * it might not be safe to call dlclose() (there could be some
             * threads around). */
            
            free(dp->configs[i]);
            free((void*)dp->queryString[i].extensions_config);
            free((void*)dp->queryString[i].extensions);
            dp->numConfigs[i] = 0;
            dp->dpys[i] = EGL_NO_DISPLAY;
            dlclose((void*)cnx->dso);
            cnx->dso = 0;
            res = EGL_TRUE;
        }
    }
    free((void*)dp->extensionsString);
    dp->extensionsString = 0;
    dp->numTotalConfigs = 0;
    clearTLS();
    return res;
}

// ----------------------------------------------------------------------------
// configuration
// ----------------------------------------------------------------------------

EGLBoolean eglGetConfigs(   EGLDisplay dpy,
                            EGLConfig *configs,
                            EGLint config_size, EGLint *num_config)
{
    egl_display_t const * const dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, EGL_FALSE);

    GLint numConfigs = dp->numTotalConfigs;
    if (!configs) {
        *num_config = numConfigs;
        return EGL_TRUE;
    }
    GLint n = 0;
    for (int j=0 ; j<2 ; j++) {
        for (int i=0 ; i<dp->numConfigs[j] && config_size ; i++) {
            *configs++ = MAKE_CONFIG(j, i);
            config_size--;
            n++;
        }
    }    
    
    *num_config = n;
    return EGL_TRUE;
}

EGLBoolean eglChooseConfig( EGLDisplay dpy, const EGLint *attrib_list,
                            EGLConfig *configs, EGLint config_size,
                            EGLint *num_config)
{
    egl_display_t const * const dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, EGL_FALSE);

    if (configs == 0) {
        *num_config = 0;
        return EGL_TRUE;
    }

    EGLBoolean res = EGL_FALSE;
    *num_config = 0;
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            EGLint n;
            if (cnx->hooks->egl.eglChooseConfig(
                    dp->dpys[i], attrib_list, configs, config_size, &n))
            {
                // now we need to convert these client EGLConfig to our
                // internal EGLConfig format. This is done in O(n log n).
                for (int j=0 ; j<n ; j++) {
                    int index = binarySearch<EGLConfig>(
                            dp->configs[i], 0, dp->numConfigs[i]-1, configs[j]);
                    if (index >= 0) {
                        configs[j] = MAKE_CONFIG(i, index);
                    } else {
                        return setError(EGL_BAD_CONFIG, EGL_FALSE);
                    }
                }
                configs += n;
                config_size -= n;
                *num_config += n;
                res = EGL_TRUE;
            }
        }
    }
    return res;
}

EGLBoolean eglGetConfigAttrib(EGLDisplay dpy, EGLConfig config,
        EGLint attribute, EGLint *value)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (!cnx) return EGL_FALSE;
    return cnx->hooks->egl.eglGetConfigAttrib(
            dp->dpys[i], dp->configs[i][index], attribute, value);
}

// ----------------------------------------------------------------------------
// surfaces
// ----------------------------------------------------------------------------

EGLSurface eglCreateWindowSurface(  EGLDisplay dpy, EGLConfig config,
                                    NativeWindowType window,
                                    const EGLint *attrib_list)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (cnx) {
        // window must be connected upon calling underlying
        // eglCreateWindowSurface
        if (window) {
            window->incRef(window);
            if (window->connect)
                window->connect(window);
        }

        EGLSurface surface = cnx->hooks->egl.eglCreateWindowSurface(
                dp->dpys[i], dp->configs[i][index], window, attrib_list);       
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s = new egl_surface_t(dpy, surface, window, i, cnx);
            return s;
        }
        
        // something went wrong, disconnect and free window
        // (will disconnect() automatically)
        if (window) {
            window->decRef(window);
        }        
    }
    return EGL_NO_SURFACE;
}

EGLSurface eglCreatePixmapSurface(  EGLDisplay dpy, EGLConfig config,
                                    NativePixmapType pixmap,
                                    const EGLint *attrib_list)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (cnx) {
        EGLSurface surface = cnx->hooks->egl.eglCreatePixmapSurface(
                dp->dpys[i], dp->configs[i][index], pixmap, attrib_list);
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s = new egl_surface_t(dpy, surface, NULL, i, cnx);
            return s;
        }
    }
    return EGL_NO_SURFACE;
}

EGLSurface eglCreatePbufferSurface( EGLDisplay dpy, EGLConfig config,
                                    const EGLint *attrib_list)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (cnx) {
        EGLSurface surface = cnx->hooks->egl.eglCreatePbufferSurface(
                dp->dpys[i], dp->configs[i][index], attrib_list);
        if (surface != EGL_NO_SURFACE) {
            egl_surface_t* s = new egl_surface_t(dpy, surface, NULL, i, cnx);
            return s;
        }
    }
    return EGL_NO_SURFACE;
}
                                    
EGLBoolean eglDestroySurface(EGLDisplay dpy, EGLSurface surface)
{
    if (!validate_display_surface(dpy, surface))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(surface);

    EGLBoolean result = s->cnx->hooks->egl.eglDestroySurface(
            dp->dpys[s->impl], s->surface);
    
    delete s;
    return result;
}

EGLBoolean eglQuerySurface( EGLDisplay dpy, EGLSurface surface,
                            EGLint attribute, EGLint *value)
{
    if (!validate_display_surface(dpy, surface))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(surface);

    return s->cnx->hooks->egl.eglQuerySurface(
            dp->dpys[s->impl], s->surface, attribute, value);
}

// ----------------------------------------------------------------------------
// contextes
// ----------------------------------------------------------------------------

EGLContext eglCreateContext(EGLDisplay dpy, EGLConfig config,
                            EGLContext share_list, const EGLint *attrib_list)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (cnx) {
        EGLContext context = cnx->hooks->egl.eglCreateContext(
                dp->dpys[i], dp->configs[i][index], share_list, attrib_list);
        if (context != EGL_NO_CONTEXT) {
            egl_context_t* c = new egl_context_t(dpy, context, i, cnx);
            return c;
        }
    }
    return EGL_NO_CONTEXT;
}

EGLBoolean eglDestroyContext(EGLDisplay dpy, EGLContext ctx)
{
    if (!validate_display_context(dpy, ctx))
        return EGL_FALSE;
    egl_display_t const * const dp = get_display(dpy);
    egl_context_t * const c = get_context(ctx);
    EGLBoolean result = c->cnx->hooks->egl.eglDestroyContext(
            dp->dpys[c->impl], c->context);
    delete c;
    return result;
}

EGLBoolean eglMakeCurrent(  EGLDisplay dpy, EGLSurface draw,
                            EGLSurface read, EGLContext ctx)
{
    egl_display_t const * const dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, EGL_FALSE);

    if (read == EGL_NO_SURFACE && draw  == EGL_NO_SURFACE &&
            ctx == EGL_NO_CONTEXT) 
    {
        EGLBoolean result = EGL_TRUE;
        ctx = getContext();
        if (ctx) {
            egl_context_t * const c = get_context(ctx);
            result = c->cnx->hooks->egl.eglMakeCurrent(dp->dpys[c->impl], 0, 0, 0);
            if (result == EGL_TRUE) {
                setGlThreadSpecific(&gHooks[IMPL_NO_CONTEXT]);
                setContext(EGL_NO_CONTEXT);
            }
        }
        return result;
    }

    if (!validate_display_context(dpy, ctx))
        return EGL_FALSE;    
    
    egl_context_t * const c = get_context(ctx);
    if (draw != EGL_NO_SURFACE) {
        egl_surface_t const * d = get_surface(draw);
        if (!d) return setError(EGL_BAD_SURFACE, EGL_FALSE);
        if (d->impl != c->impl)
            return setError(EGL_BAD_MATCH, EGL_FALSE);
        draw = d->surface;
    }
    if (read != EGL_NO_SURFACE) {
        egl_surface_t const * r = get_surface(read);
        if (!r) return setError(EGL_BAD_SURFACE, EGL_FALSE);
        if (r->impl != c->impl)
            return setError(EGL_BAD_MATCH, EGL_FALSE);
        read = r->surface;
    }
    EGLBoolean result = c->cnx->hooks->egl.eglMakeCurrent(
            dp->dpys[c->impl], draw, read, c->context);

    if (result == EGL_TRUE) {
        setGlThreadSpecific(c->cnx->hooks);
        setContext(ctx);
        c->read = read;
        c->draw = draw;
    }
    return result;
}


EGLBoolean eglQueryContext( EGLDisplay dpy, EGLContext ctx,
                            EGLint attribute, EGLint *value)
{
    if (!validate_display_context(dpy, ctx))
        return EGL_FALSE;    
    
    egl_display_t const * const dp = get_display(dpy);
    egl_context_t * const c = get_context(ctx);

    return c->cnx->hooks->egl.eglQueryContext(
            dp->dpys[c->impl], c->context, attribute, value);
}

EGLContext eglGetCurrentContext(void)
{
    EGLContext ctx = getContext();
    return ctx;
}

EGLSurface eglGetCurrentSurface(EGLint readdraw)
{
    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_NO_SURFACE);
        switch (readdraw) {
            case EGL_READ: return c->read;
            case EGL_DRAW: return c->draw;            
            default: return setError(EGL_BAD_PARAMETER, EGL_NO_SURFACE);
        }
    }
    return EGL_NO_SURFACE;
}

EGLDisplay eglGetCurrentDisplay(void)
{
    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_NO_SURFACE);
        return c->dpy;
    }
    return EGL_NO_DISPLAY;
}

EGLBoolean eglWaitGL(void)
{
    EGLBoolean res = EGL_TRUE;
    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        if (uint32_t(c->impl)>=2)
            return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        egl_connection_t* const cnx = &gEGLImpl[c->impl];
        if (!cnx->dso) 
            return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        res = cnx->hooks->egl.eglWaitGL();
    }
    return res;
}

EGLBoolean eglWaitNative(EGLint engine)
{
    EGLBoolean res = EGL_TRUE;
    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        if (uint32_t(c->impl)>=2)
            return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        egl_connection_t* const cnx = &gEGLImpl[c->impl];
        if (!cnx->dso) 
            return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        res = cnx->hooks->egl.eglWaitNative(engine);
    }
    return res;
}

EGLint eglGetError(void)
{
    EGLint result = EGL_SUCCESS;
    for (int i=0 ; i<2 ; i++) {
        EGLint err = EGL_SUCCESS;
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso)
            err = cnx->hooks->egl.eglGetError();
        if (err!=EGL_SUCCESS && result==EGL_SUCCESS)
            result = err;
    }
    if (result == EGL_SUCCESS)
        result = getError();
    return result;
}

void (*eglGetProcAddress(const char *procname))()
{
    void (*addr)();
    addr = findProcAddress(procname, gExtentionMap, NELEM(gExtentionMap));
    if (addr) return addr;

    return NULL; // TODO: finish implementation below

    addr = findProcAddress(procname, gGLExtentionMap, NELEM(gGLExtentionMap));
    if (addr) return addr;
    
    addr = 0;
    int slot = -1;
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            if (cnx->hooks->egl.eglGetProcAddress) {
                addr = cnx->hooks->egl.eglGetProcAddress(procname);
                if (addr) {
                    if (slot == -1) {
                        slot = 0; // XXX: find free slot
                        if (slot == -1) {
                            addr = 0;
                            break;
                        }
                    }
                    cnx->hooks->ext.extensions[slot] = addr;
                }
            }
        }
    }
    
    if (slot >= 0) {
        addr = 0; // XXX: address of stub 'slot'
        gGLExtentionMap[slot].name = strdup(procname);
        gGLExtentionMap[slot].address = addr;
    }
    
    return addr;

    
    /*
     *  TODO: For OpenGL ES extensions, we must generate a stub
     *  that looks like
     *      mov     r12, #0xFFFF0FFF
     *      ldr     r12, [r12, #-15]
     *      ldr     r12, [r12, #TLS_SLOT_OPENGL_API*4]
     *      mov     r12, [r12, #api_offset]
     *      ldrne   pc, r12
     *      mov     pc, #unsupported_extension
     * 
     *  and write the address of the extension in *all*
     *  gl_hooks_t::gl_ext_t at offset "api_offset" from gl_hooks_t
     * 
     */
}

EGLBoolean eglSwapBuffers(EGLDisplay dpy, EGLSurface draw)
{
    if (!validate_display_surface(dpy, draw))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(draw);
    return s->cnx->hooks->egl.eglSwapBuffers(dp->dpys[s->impl], s->surface);
}

EGLBoolean eglCopyBuffers(  EGLDisplay dpy, EGLSurface surface,
                            NativePixmapType target)
{
    if (!validate_display_surface(dpy, surface))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(surface);
    return s->cnx->hooks->egl.eglCopyBuffers(
            dp->dpys[s->impl], s->surface, target);
}

const char* eglQueryString(EGLDisplay dpy, EGLint name)
{
    egl_display_t const * const dp = get_display(dpy);
    switch (name) {
        case EGL_VENDOR:
            return gVendorString;
        case EGL_VERSION:
            return gVersionString;
        case EGL_EXTENSIONS:
            return dp->extensionsString;
        case EGL_CLIENT_APIS:
            return gClientApiString;
    }
    return setError(EGL_BAD_PARAMETER, (const char *)0);
}


// ----------------------------------------------------------------------------
// EGL 1.1
// ----------------------------------------------------------------------------

EGLBoolean eglSurfaceAttrib(
        EGLDisplay dpy, EGLSurface surface, EGLint attribute, EGLint value)
{
    if (!validate_display_surface(dpy, surface))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->hooks->egl.eglSurfaceAttrib) {
        return s->cnx->hooks->egl.eglSurfaceAttrib(
                dp->dpys[s->impl], s->surface, attribute, value);
    }
    return setError(EGL_BAD_SURFACE, EGL_FALSE);
}

EGLBoolean eglBindTexImage(
        EGLDisplay dpy, EGLSurface surface, EGLint buffer)
{
    if (!validate_display_surface(dpy, surface))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->hooks->egl.eglBindTexImage) {
        return s->cnx->hooks->egl.eglBindTexImage(
                dp->dpys[s->impl], s->surface, buffer);
    }
    return setError(EGL_BAD_SURFACE, EGL_FALSE);
}

EGLBoolean eglReleaseTexImage(
        EGLDisplay dpy, EGLSurface surface, EGLint buffer)
{
    if (!validate_display_surface(dpy, surface))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(surface);
    if (s->cnx->hooks->egl.eglReleaseTexImage) {
        return s->cnx->hooks->egl.eglReleaseTexImage(
                dp->dpys[s->impl], s->surface, buffer);
    }
    return setError(EGL_BAD_SURFACE, EGL_FALSE);
}

EGLBoolean eglSwapInterval(EGLDisplay dpy, EGLint interval)
{
    egl_display_t * const dp = get_display(dpy);
    if (!dp) return setError(EGL_BAD_DISPLAY, EGL_FALSE);

    EGLBoolean res = EGL_TRUE;
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            if (cnx->hooks->egl.eglSwapInterval) {
                if (cnx->hooks->egl.eglSwapInterval(dp->dpys[i], interval) == EGL_FALSE) {
                    res = EGL_FALSE;
                }
            }
        }
    }
    return res;
}


// ----------------------------------------------------------------------------
// EGL 1.2
// ----------------------------------------------------------------------------

EGLBoolean eglWaitClient(void)
{
    EGLBoolean res = EGL_TRUE;
    EGLContext ctx = getContext();
    if (ctx) {
        egl_context_t const * const c = get_context(ctx);
        if (!c) return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        if (uint32_t(c->impl)>=2)
            return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        egl_connection_t* const cnx = &gEGLImpl[c->impl];
        if (!cnx->dso) 
            return setError(EGL_BAD_CONTEXT, EGL_FALSE);
        if (cnx->hooks->egl.eglWaitClient) {
            res = cnx->hooks->egl.eglWaitClient();
        } else {
            res = cnx->hooks->egl.eglWaitGL();
        }
    }
    return res;
}

EGLBoolean eglBindAPI(EGLenum api)
{
    // bind this API on all EGLs
    EGLBoolean res = EGL_TRUE;
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            if (cnx->hooks->egl.eglBindAPI) {
                if (cnx->hooks->egl.eglBindAPI(api) == EGL_FALSE) {
                    res = EGL_FALSE;
                }
            }
        }
    }
    return res;
}

EGLenum eglQueryAPI(void)
{
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            if (cnx->hooks->egl.eglQueryAPI) {
                // the first one we find is okay, because they all
                // should be the same
                return cnx->hooks->egl.eglQueryAPI();
            }
        }
    }
    // or, it can only be OpenGL ES
    return EGL_OPENGL_ES_API;
}

EGLBoolean eglReleaseThread(void)
{
    for (int i=0 ; i<2 ; i++) {
        egl_connection_t* const cnx = &gEGLImpl[i];
        if (cnx->dso) {
            if (cnx->hooks->egl.eglReleaseThread) {
                cnx->hooks->egl.eglReleaseThread();
            }
        }
    }
    clearTLS();    
    return EGL_TRUE;
}

EGLSurface eglCreatePbufferFromClientBuffer(
          EGLDisplay dpy, EGLenum buftype, EGLClientBuffer buffer,
          EGLConfig config, const EGLint *attrib_list)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (!cnx) return EGL_FALSE;
    if (cnx->hooks->egl.eglCreatePbufferFromClientBuffer) {
        return cnx->hooks->egl.eglCreatePbufferFromClientBuffer(
                dp->dpys[i], buftype, buffer, dp->configs[i][index], attrib_list);
    }
    return setError(EGL_BAD_CONFIG, EGL_NO_SURFACE);
}

// ----------------------------------------------------------------------------
// Android extentions
// ----------------------------------------------------------------------------

EGLBoolean eglSwapRectangleANDROID(
        EGLDisplay dpy, EGLSurface draw,
        EGLint l, EGLint t, EGLint w, EGLint h)
{    
    if (!validate_display_surface(dpy, draw))
        return EGL_FALSE;    
    egl_display_t const * const dp = get_display(dpy);
    egl_surface_t const * const s = get_surface(draw);
    if (s->cnx->hooks->egl.eglSwapRectangleANDROID) {
        return s->cnx->hooks->egl.eglSwapRectangleANDROID(
                dp->dpys[s->impl], s->surface, l, t, w, h);
    }
    return setError(EGL_BAD_SURFACE, EGL_FALSE);
}

const char* eglQueryStringConfigANDROID(
        EGLDisplay dpy, EGLConfig config, EGLint name)
{
    egl_display_t const* dp = 0;
    int i=0, index=0;
    egl_connection_t* cnx = validate_display_config(dpy, config, dp, i, index);
    if (cnx) {
        return dp->queryString[i].extensions_config;
    }
    return setError(EGL_BAD_PARAMETER, (const char *)0);
}
