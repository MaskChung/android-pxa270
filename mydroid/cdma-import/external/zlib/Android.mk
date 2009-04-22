LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# measurements show that the ARM version of ZLib is about x1.17 faster
# than the thumb one...
LOCAL_ARM_MODE := arm

LOCAL_SRC_FILES:= \
	adler32.c \
	compress.c \
	crc32.c \
	gzio.c \
	uncompr.c \
	deflate.c \
	trees.c \
	zutil.c \
	inflate.c \
	infback.c \
	inftrees.c \
	inffast.c

LOCAL_MODULE:= libz

LOCAL_CFLAGS+= -O3 -DUSE_MMAP

include $(BUILD_SHARED_LIBRARY)



unzip_files := \
	adler32.c \
	crc32.c \
	zutil.c \
	inflate.c \
	inftrees.c \
	inffast.c

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(unzip_files)
LOCAL_MODULE:= libunz
LOCAL_ARM_MODE := arm
include $(BUILD_HOST_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_SRC_FILES := $(unzip_files)
LOCAL_MODULE:= libunz
LOCAL_ARM_MODE := arm
include $(BUILD_STATIC_LIBRARY)

