LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
    clz.cpp.arm \
    DisplayHardware/DisplayHardware.cpp \
    DisplayHardware/DisplayHardwareBase.cpp \
    GPUHardware/GPUHardware.cpp \
    BootAnimation.cpp \
    BlurFilter.cpp.arm \
    CPUGauge.cpp \
    Layer.cpp \
    LayerBase.cpp \
    LayerBuffer.cpp \
    LayerBlur.cpp \
    LayerBitmap.cpp \
    LayerDim.cpp \
    LayerScreenshot.cpp \
    RFBServer.cpp \
    SurfaceFlinger.cpp \
    Tokenizer.cpp \
    Transform.cpp \
    VRamHeap.cpp


# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt
	endif
endif

LOCAL_SHARED_LIBRARIES := \
	libutils \
	libcutils \
	libui \
	libcorecg \
	libsgl \
	libpixelflinger \
	libGLES_CM

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

LOCAL_MODULE:= libsurfaceflinger

include $(BUILD_SHARED_LIBRARY)
