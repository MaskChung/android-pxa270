LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_JAVA_LIBRARIES := android.test.runner

LOCAL_PACKAGE_NAME := HelloActivityTests

LOCAL_MODULE_TAGS := tests

LOCAL_INSTRUMENTATION_FOR := HelloActivity

include $(BUILD_PACKAGE)
