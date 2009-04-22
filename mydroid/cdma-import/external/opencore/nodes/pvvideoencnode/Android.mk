LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	src/pvmf_videoenc_port.cpp \
	src/pvmf_videoenc_node.cpp \
	src/pvmf_videoenc_node_cap_config.cpp



LOCAL_MODULE := libpvvideoencnode

LOCAL_CFLAGS :=   $(PV_CFLAGS)


LOCAL_C_INCLUDES := \
	$(PV_TOP)//nodes/pvvideoencnode/include \
	$(PV_TOP)//nodes/pvvideoencnode/src \
	$(PV_INCLUDES) 


LOCAL_COPY_HEADERS_TO := $(PV_COPY_HEADERS_TO)
LOCAL_COPY_HEADERS := \
	include/pvmf_videoenc_node_factory.h \
	include/pvmf_videoenc_node_types.h

include $(BUILD_STATIC_LIBRARY)

