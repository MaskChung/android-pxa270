# Copyright 2006 The Android Open Source Project

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= icache_main.c icache.S icache2.S

LOCAL_SHARED_LIBRARIES := libc

LOCAL_MODULE:= icache

LOCAL_MODULE_TAGS := tests

include $(BUILD_EXECUTABLE)
