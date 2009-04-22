#
# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

# We have a special case here where we build the library's resources
# independently from its code, so we need to find where the resource
# class source got placed in the course of building the resources.
# Thus, the magic here.
# Also, this module cannot depend directly on the R.java file; if it
# did, the PRIVATE_* vars for R.java wouldn't be guaranteed to be correct.
# Instead, it depends on the R.stamp file, which lists the corresponding
# R.java file as a prerequisite.
# TODO: find a more appropriate way to do this.
framework-res-source-path := APPS/framework-res_intermediates/src

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call find-other-java-files,$(FRAMEWORKS_BASE_SUBDIRS))

# The following filters out code we are temporarily not including at all.
# TODO: Move AWT and beans (and associated harmony code) back into libcore.
# TODO: Maybe remove javax.microedition entirely?
# TODO: Move SyncML (org.mobilecontrol.*) into its own library.
LOCAL_SRC_FILES := $(filter-out \
			org/mobilecontrol/% \
			,$(LOCAL_SRC_FILES))

## READ ME: ########################################################
##
## When updading this list of aidl files, consider if that aidl is
## part of the SDK API.  If it is, also add it to the list below that
## is preprocessed and distributed with the SDK.  This list should
## not contain any aidl files for parcelables, but the one below should
## if you intend for 3rd parties to be able to send those objects
## across process boundaries.
##
## READ ME: ########################################################
LOCAL_SRC_FILES += \
	core/java/android/accounts/IAccountsService.aidl \
	core/java/android/app/IActivityPendingResult.aidl \
	core/java/android/app/IActivityWatcher.aidl \
	core/java/android/app/IAlarmManager.aidl \
	core/java/android/app/IInstrumentationWatcher.aidl \
	core/java/android/app/IIntentReceiver.aidl \
	core/java/android/app/IIntentSender.aidl \
	core/java/android/app/INotificationManager.aidl \
	core/java/android/app/ISearchManager.aidl \
	core/java/android/app/IServiceConnection.aidl \
	core/java/android/app/IStatusBar.aidl \
	core/java/android/app/IThumbnailReceiver.aidl \
	core/java/android/app/ITransientNotification.aidl \
	core/java/android/app/IWallpaperService.aidl \
	core/java/android/app/IWallpaperServiceCallback.aidl \
	core/java/android/bluetooth/IBluetoothDevice.aidl \
	core/java/android/bluetooth/IBluetoothDeviceCallback.aidl \
	core/java/android/bluetooth/IBluetoothHeadset.aidl \
	core/java/android/bluetooth/IBluetoothHeadsetCallback.aidl \
	core/java/android/content/ISyncAdapter.aidl \
	core/java/android/content/ISyncContext.aidl \
	core/java/android/content/pm/IPackageDataObserver.aidl \
	core/java/android/content/pm/IPackageDeleteObserver.aidl \
	core/java/android/content/pm/IPackageInstallObserver.aidl \
	core/java/android/content/pm/IPackageManager.aidl \
	core/java/android/content/pm/IPackageStatsObserver.aidl \
	core/java/android/database/IContentObserver.aidl \
	core/java/android/hardware/ISensorService.aidl \
	core/java/android/net/IConnectivityManager.aidl \
	core/java/android/os/ICheckinService.aidl \
	core/java/android/os/IHardwareService.aidl \
	core/java/android/os/IMessenger.aidl \
	core/java/android/os/IMountService.aidl \
	core/java/android/os/INetStatService.aidl \
	core/java/android/os/IParentalControlCallback.aidl \
	core/java/android/os/IPermissionController.aidl \
	core/java/android/os/IPowerManager.aidl \
	core/java/android/text/IClipboard.aidl \
	core/java/android/view/IApplicationToken.aidl \
	core/java/android/view/IOnKeyguardExitResult.aidl \
	core/java/android/view/IRotationWatcher.aidl \
	core/java/android/view/IWindow.aidl \
	core/java/android/view/IWindowManager.aidl \
	core/java/android/view/IWindowSession.aidl \
	core/java/com/android/internal/app/IBatteryStats.aidl \
	location/java/android/location/IGpsStatusListener.aidl \
	location/java/android/location/ILocationListener.aidl \
	location/java/android/location/ILocationManager.aidl \
	media/java/android/media/IAudioService.aidl \
	media/java/android/media/IMediaScannerListener.aidl \
	media/java/android/media/IMediaScannerService.aidl \
	telephony/java/com/android/internal/telephony/IPhoneStateListener.aidl \
	telephony/java/com/android/internal/telephony/IPhoneSubInfo.aidl \
	telephony/java/com/android/internal/telephony/ITelephony.aidl \
	telephony/java/com/android/internal/telephony/ITelephonyRegistry.aidl \
	telephony/java/com/android/internal/telephony/IIccPhoneBook.aidl \
	telephony/java/com/android/internal/telephony/ISms.aidl \
	wifi/java/android/net/wifi/IWifiManager.aidl

LOCAL_AIDL_INCLUDES += $(FRAMEWORKS_BASE_JAVA_SRC_DIRS)

LOCAL_INTERMEDIATE_SOURCES := \
			$(framework-res-source-path)/android/R.java \
			$(framework-res-source-path)/android/Manifest.java \
			$(framework-res-source-path)/com/android/internal/R.java

LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVA_LIBRARIES := core ext

LOCAL_MODULE := framework
LOCAL_MODULE_CLASS := JAVA_LIBRARIES

# List of classes and interfaces which should be loaded by the Zygote.
LOCAL_JAVA_RESOURCE_FILES += $(LOCAL_PATH)/preloaded-classes

LOCAL_DX_FLAGS := --core-library

include $(BUILD_JAVA_LIBRARY)

# Make sure that R.java and Manifest.java are built before we build
# the source for this library.
framework_res_R_stamp := \
	$(call intermediates-dir-for,APPS,framework-res,,COMMON)/src/R.stamp
$(full_classes_jar): $(framework_res_R_stamp)

# Make sure that framework-res is installed when framework is.
$(LOCAL_INSTALLED_MODULE): | $(dir $(LOCAL_INSTALLED_MODULE))framework-res.apk

framework_built := $(LOCAL_BUILT_MODULE)

# AIDL files to be preprocessed and included in the SDK,
# relative to the root of the build tree.
# ============================================================
aidl_files := \
	frameworks/base/core/java/android/accounts/IAccountsService.aidl \
	frameworks/base/core/java/android/app/Notification.aidl \
	frameworks/base/core/java/android/app/PendingIntent.aidl \
	frameworks/base/core/java/android/content/ComponentName.aidl \
	frameworks/base/core/java/android/content/Intent.aidl \
	frameworks/base/core/java/android/content/SyncStats.aidl \
	frameworks/base/core/java/android/content/res/Configuration.aidl \
	frameworks/base/core/java/android/net/Uri.aidl \
	frameworks/base/core/java/android/os/Bundle.aidl \
	frameworks/base/core/java/android/os/ParcelFileDescriptor.aidl \
	frameworks/base/core/java/android/view/KeyEvent.aidl \
	frameworks/base/core/java/android/view/MotionEvent.aidl \
	frameworks/base/core/java/android/view/Surface.aidl \
	frameworks/base/core/java/android/view/WindowManager.aidl \
	frameworks/base/graphics/java/android/graphics/Bitmap.aidl \
	frameworks/base/graphics/java/android/graphics/Rect.aidl \
	frameworks/base/graphics/java/android/graphics/Region.aidl \
	frameworks/base/location/java/android/location/Criteria.aidl \
	frameworks/base/location/java/android/location/Location.aidl \
	frameworks/base/telephony/java/android/telephony/ServiceState.aidl \
	frameworks/base/telephony/java/com/android/internal/telephony/IPhoneSubInfo.aidl \
	frameworks/base/telephony/java/com/android/internal/telephony/ITelephony.aidl

gen := $(TARGET_OUT_COMMON_INTERMEDIATES)/framework.aidl
$(gen): PRIVATE_SRC_FILES := $(aidl_files)
ALL_SDK_FILES += $(gen)
$(gen): $(aidl_files) | $(AIDL)
		@echo Aidl Preprocess: $@
		$(hide) $(AIDL) --preprocess $@ $(PRIVATE_SRC_FILES)

# the documentation
# ============================================================

# TODO: deal with com/google/android/googleapps
packages_to_document := \
	android \
	javax/microedition/khronos

# Search through the base framework dirs for these packages.
# The result will be relative to frameworks/base.
fwbase_dirs_to_document := \
	test-runner \
	$(patsubst $(LOCAL_PATH)/%,%, \
	  $(wildcard \
	    $(foreach dir, $(FRAMEWORKS_BASE_JAVA_SRC_DIRS), \
	      $(addprefix $(dir)/, $(packages_to_document)) \
	     ) \
	   ) \
	 )

# These are relative to dalvik/libcore
# Intentionally not included from libcore:
#     icu openssl suncompat support
libcore_to_document := \
	annotation/src/main/java/java \
	archive/src/main/java/java \
	auth/src/main/java/javax \
	awt-kernel/src/main/java/java \
	concurrent/src/main/java \
	crypto/src/main/java/javax \
	dalvik/src/main/java/dalvik \
	json/src/main/java \
	junit/src/main/java \
	logging/src/main/java/java \
	luni/src/main/java/java \
	luni-kernel/src/main/java/java \
	math/src/main/java/java \
	nio/src/main/java/java \
	nio_char/src/main/java/java \
	prefs/src/main/java/java \
	regex/src/main/java/java \
	security/src/main/java/java \
	security/src/main/java/javax \
	security-kernel/src/main/java/java \
	sql/src/main/java/java \
	sql/src/main/java/javax \
	text/src/main/java/java \
	x-net/src/main/java/javax \
	xml/src/main/java/javax \
	xml/src/main/java/org/xml/sax \
	xml/src/main/java/org/xmlpull/v1 \
	xml/src/main/java/org/w3c

non_base_dirs := \
	../../external/apache-http/src/org/apache/http

# These are relative to frameworks/base
dirs_to_document := \
	$(fwbase_dirs_to_document) \
	$(non_base_dirs) \
	$(addprefix ../../dalvik/libcore/, $(libcore_to_document))

html_dirs := \
	$(FRAMEWORKS_BASE_SUBDIRS) \
	$(non_base_dirs)

# These are relative to frameworks/base
framework_docs_LOCAL_SRC_FILES := \
	$(call find-other-java-files, $(dirs_to_document)) \
	$(call find-other-html-files, $(html_dirs))

framework_docs_LOCAL_DROIDDOC_SOURCE_PATH := \
	$(FRAMEWORKS_BASE_JAVA_SRC_DIRS)

framework_docs_LOCAL_INTERMEDIATE_SOURCES := \
			$(framework-res-source-path)/android/R.java \
			$(framework-res-source-path)/android/Manifest.java \
			$(framework-res-source-path)/com/android/internal/R.java

framework_docs_LOCAL_JAVA_LIBRARIES := \
			core \
			ext \

framework_docs_LOCAL_MODULE_CLASS := JAVA_LIBRARIES
framework_docs_LOCAL_DROIDDOC_HTML_DIR := docs/html
framework_docs_LOCAL_DROIDDOC_OPTIONS := \
		-error 1 -error 2 -error 3 -error 4 -error 6 -error 8 \
		-overview $(LOCAL_PATH)/core/java/overview.html \
		-hdf android.buglink 1 \
		-hdf android.whichdoc framework

sample_dir := development/samples

web_docs_sample_code_flags := \
		-hdf android.hasSamples 1 \
		-samplecode $(sample_dir)/ApiDemos \
		            guide/samples/ApiDemos "API Demos" \
		-samplecode $(sample_dir)/LunarLander \
		            guide/samples/LunarLander "Lunar Lander" \
		-samplecode $(sample_dir)/NotePad \
		            guide/samples/NotePad "Note Pad"


# ====  static html  ==================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES)
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_MODULE:=framework

framework_keep_file := $(OUT_DOCS)/$(LOCAL_MODULE)-keep.txt

LOCAL_DROIDDOC_OPTIONS:=\
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		-title "Android SDK" \
		-keeplist $(framework_keep_file) \
		-proofread $(OUT_DOCS)/$(LOCAL_MODULE)-proofread.txt \
		-todo $(OUT_DOCS)/$(LOCAL_MODULE)-docs-todo.html \
		-stubs $(TARGET_OUT_COMMON_INTERMEDIATES)/JAVA_LIBRARIES/android_stubs_current_intermediates/src \
		-apixml $(INTERNAL_PLATFORM_API_FILE) \
		-sdkvalues $(OUT_DOCS) \

include $(BUILD_DROIDDOC)

static_doc_index_redirect := $(out_dir)/index.html
$(static_doc_index_redirect): \
		$(LOCAL_PATH)/docs/docs-documentation-redirect.html | $(ACP)
	$(hide) mkdir -p $(dir $@)
	$(hide) $(ACP) $< $@

$(full_target): $(static_doc_index_redirect)
$(full_target): $(framework_built)
$(framework_keep_file): $(full_target)
$(INTERNAL_PLATFORM_API_FILE): $(full_target)
$(call dist-for-goals,sdk,$(INTERNAL_PLATFORM_API_FILE))


# ====  codesite ezt templates  =======================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES) framework
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(call intermediates-dir-for,JAVA_LIBRARIES,framework)

LOCAL_MODULE:=codesite
LOCAL_DROIDDOC_OPTIONS:=\
		$(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
		$(web_docs_sample_code_flags) \
		-toroot /android/

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=$(SRC_DROIDDOC_DIR)/templates-codesite
LOCAL_DROIDDOC_CUSTOM_ASSET_DIR:=assets-google

include $(BUILD_DROIDDOC)

# ==== docs for the web (on the google app engine server) =======================
include $(CLEAR_VARS)

LOCAL_SRC_FILES:=$(framework_docs_LOCAL_SRC_FILES)
LOCAL_INTERMEDIATE_SOURCES:=$(framework_docs_LOCAL_INTERMEDIATE_SOURCES)
LOCAL_JAVA_LIBRARIES:=$(framework_docs_LOCAL_JAVA_LIBRARIES) framework
LOCAL_MODULE_CLASS:=$(framework_docs_LOCAL_MODULE_CLASS)
LOCAL_DROIDDOC_SOURCE_PATH:=$(framework_docs_LOCAL_DROIDDOC_SOURCE_PATH)
LOCAL_DROIDDOC_HTML_DIR:=$(framework_docs_LOCAL_DROIDDOC_HTML_DIR)
LOCAL_ADDITIONAL_JAVA_DIR:=$(call intermediates-dir-for,JAVA_LIBRARIES,framework)

LOCAL_MODULE:=gae
LOCAL_DROIDDOC_OPTIONS:=\
 $(framework_docs_LOCAL_DROIDDOC_OPTIONS) \
 $(web_docs_sample_code_flags) \
 -toroot /gae/

LOCAL_DROIDDOC_CUSTOM_TEMPLATE_DIR:=$(SRC_DROIDDOC_DIR)/templates
LOCAL_DROIDDOC_CUSTOM_ASSET_DIR:=assets

include $(BUILD_DROIDDOC)


# Build ext.jar
# ============================================================

ext_dirs := \
	../../external/apache-http/src \
	../../external/gdata/src \
	../../external/protobuf/src \
	../../external/tagsoup/src

ext_src_files := $(call all-java-files-under,$(ext_dirs))

# ====  the library  =========================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(ext_src_files)

LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVA_LIBRARIES := core
LOCAL_STATIC_JAVA_LIBRARIES := libgoogleclient

LOCAL_MODULE := ext

include $(BUILD_JAVA_LIBRARY)

# ====  the documentation  ===================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(ext_src_files) docs/overview-ext.html

LOCAL_NO_STANDARD_LIBRARIES := true
LOCAL_JAVA_LIBRARIES := core

LOCAL_MODULE := ext
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_DROIDDOC_OPTIONS := -overview $(LOCAL_PATH)/docs/overview-ext.html

include $(BUILD_DROIDDOC)


# Include subdirectory makefiles
# ============================================================

ifneq ($(SDK_ONLY),true)
  include $(call first-makefiles-under,$(LOCAL_PATH))
endif
