EGL_ENTRY(EGLDisplay, eglGetDisplay, NativeDisplayType)
EGL_ENTRY(EGLBoolean, eglInitialize, EGLDisplay, EGLint*, EGLint*)
EGL_ENTRY(EGLBoolean, eglTerminate, EGLDisplay)
EGL_ENTRY(EGLBoolean, eglGetConfigs, EGLDisplay, EGLConfig*, EGLint, EGLint*)
EGL_ENTRY(EGLBoolean, eglChooseConfig, EGLDisplay, const EGLint *, EGLConfig *, EGLint, EGLint *)

EGL_ENTRY(EGLBoolean, eglGetConfigAttrib, EGLDisplay, EGLConfig, EGLint, EGLint *)
EGL_ENTRY(EGLSurface, eglCreateWindowSurface, EGLDisplay, EGLConfig, NativeWindowType, const EGLint *)
EGL_ENTRY(EGLSurface, eglCreatePixmapSurface, EGLDisplay, EGLConfig, NativePixmapType, const EGLint *)
EGL_ENTRY(EGLSurface, eglCreatePbufferSurface,  EGLDisplay, EGLConfig, const EGLint *)
EGL_ENTRY(EGLBoolean, eglDestroySurface, EGLDisplay, EGLSurface)
EGL_ENTRY(EGLBoolean, eglQuerySurface,  EGLDisplay, EGLSurface, EGLint, EGLint *)
EGL_ENTRY(EGLContext, eglCreateContext, EGLDisplay, EGLConfig, EGLContext, const EGLint *)
EGL_ENTRY(EGLBoolean, eglDestroyContext, EGLDisplay, EGLContext)
EGL_ENTRY(EGLBoolean, eglMakeCurrent, EGLDisplay, EGLSurface, EGLSurface, EGLContext)
EGL_ENTRY(EGLContext, eglGetCurrentContext, void)
EGL_ENTRY(EGLSurface, eglGetCurrentSurface, EGLint)
EGL_ENTRY(EGLDisplay, eglGetCurrentDisplay, void)
EGL_ENTRY(EGLBoolean, eglQueryContext,  EGLDisplay, EGLContext, EGLint, EGLint *)
EGL_ENTRY(EGLBoolean, eglWaitGL, void)
EGL_ENTRY(EGLBoolean, eglWaitNative, EGLint)
EGL_ENTRY(EGLBoolean, eglSwapBuffers, EGLDisplay, EGLSurface)
EGL_ENTRY(EGLBoolean, eglCopyBuffers, EGLDisplay, EGLSurface, NativePixmapType)
EGL_ENTRY(EGLint, eglGetError, void)
EGL_ENTRY(const char*, eglQueryString, EGLDisplay, EGLint)
EGL_ENTRY(proc_t, eglGetProcAddress, const char *)

/* EGL 1.1 */

EGL_ENTRY(EGLBoolean, eglSurfaceAttrib, EGLDisplay, EGLSurface, EGLint, EGLint)
EGL_ENTRY(EGLBoolean, eglBindTexImage, EGLDisplay, EGLSurface, EGLint)
EGL_ENTRY(EGLBoolean, eglReleaseTexImage, EGLDisplay, EGLSurface, EGLint)
EGL_ENTRY(EGLBoolean, eglSwapInterval, EGLDisplay, EGLint)

/* EGL 1.2 */

EGL_ENTRY(EGLBoolean, eglBindAPI, EGLenum)
EGL_ENTRY(EGLenum, eglQueryAPI, void)
EGL_ENTRY(EGLBoolean, eglWaitClient, void)
EGL_ENTRY(EGLBoolean, eglReleaseThread, void)
EGL_ENTRY(EGLSurface, eglCreatePbufferFromClientBuffer, EGLDisplay, EGLenum, EGLClientBuffer, EGLConfig, const EGLint *)

/* Android extentions */

EGL_ENTRY(EGLBoolean, eglSwapRectangleANDROID, EGLDisplay, EGLSurface , EGLint, EGLint, EGLint, EGLint)
EGL_ENTRY(const char*, eglQueryStringConfigANDROID, EGLDisplay, EGLConfig, EGLint)
