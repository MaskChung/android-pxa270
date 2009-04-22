LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CFLAGS += -DHAVE_CONFIG_H -DKHTML_NO_EXCEPTIONS -DGKWQ_NO_JAVA
LOCAL_CFLAGS += -DNO_SUPPORT_JS_BINDING -DQT_NO_WHEELEVENT -DKHTML_NO_XBL
LOCAL_CFLAGS += -U__APPLE__

ifeq ($(TARGET_ARCH), arm)
	LOCAL_CFLAGS += -DPACKED="__attribute__ ((packed))"
else
	LOCAL_CFLAGS += -DPACKED=""
endif

LOCAL_SRC_FILES:= \
	ActivityManager.cpp \
	AndroidRuntime.cpp \
	CursorWindow.cpp \
	com_google_android_gles_jni_EGLImpl.cpp \
	com_google_android_gles_jni_GLImpl.cpp.arm \
	android_database_CursorWindow.cpp \
	android_database_SQLiteDebug.cpp \
	android_database_SQLiteDatabase.cpp \
	android_database_SQLiteProgram.cpp \
	android_database_SQLiteQuery.cpp \
	android_database_SQLiteStatement.cpp \
	android_view_Display.cpp \
	android_view_Surface.cpp \
	android_view_ViewRoot.cpp \
	android_text_AndroidCharacter.cpp \
	android_text_KeyCharacterMap.cpp \
	android_os_Debug.cpp \
	android_os_Exec.cpp \
	android_os_FileUtils.cpp \
	android_os_MemoryFile.cpp \
	android_os_ParcelFileDescriptor.cpp \
	android_os_Power.cpp \
	android_os_StatFs.cpp \
	android_os_SystemClock.cpp \
	android_os_SystemProperties.cpp \
	android_os_UEventObserver.cpp \
	android_os_NetStat.cpp \
	android_os_Hardware.cpp \
	android_net_LocalSocketImpl.cpp \
	android_net_NetUtils.cpp \
	android_net_wifi_Wifi.cpp \
	android_nio_utils.cpp \
	android_pim_EventRecurrence.cpp \
	android_pim_Time.cpp \
	android_security_Md5MessageDigest.cpp \
	android_util_AssetManager.cpp \
	android_util_Binder.cpp \
	android_util_EventLog.cpp \
	android_util_Log.cpp \
	android_util_FloatMath.cpp \
	android_util_Process.cpp \
	android_util_StringBlock.cpp \
	android_util_XmlBlock.cpp \
	android_util_Base64.cpp \
	android/graphics/Bitmap.cpp \
	android/graphics/BitmapFactory.cpp \
	android/graphics/Camera.cpp \
	android/graphics/Canvas.cpp \
	android/graphics/ColorFilter.cpp \
	android/graphics/DrawFilter.cpp \
	android/graphics/CreateJavaOutputStreamAdaptor.cpp \
	android/graphics/Graphics.cpp \
	android/graphics/Interpolator.cpp \
	android/graphics/LayerRasterizer.cpp \
	android/graphics/MaskFilter.cpp \
	android/graphics/Matrix.cpp \
	android/graphics/Movie.cpp \
	android/graphics/NIOBuffer.cpp \
	android/graphics/NinePatch.cpp \
	android/graphics/NinePatchImpl.cpp \
	android/graphics/Paint.cpp \
	android/graphics/Path.cpp \
	android/graphics/PathMeasure.cpp \
	android/graphics/PathEffect.cpp \
	android_graphics_PixelFormat.cpp \
	android/graphics/Picture.cpp \
	android/graphics/PorterDuff.cpp \
	android/graphics/Rasterizer.cpp \
	android/graphics/Region.cpp \
	android/graphics/Shader.cpp \
	android/graphics/Typeface.cpp \
	android/graphics/Xfermode.cpp \
	android_media_AudioSystem.cpp \
	android_media_ToneGenerator.cpp \
	android_hardware_Camera.cpp \
	android_hardware_SensorManager.cpp \
	android_debug_JNITest.cpp \
	android_util_FileObserver.cpp \
	android/opengl/poly_clip.cpp.arm \
	android/opengl/util.cpp.arm \
	android_bluetooth_Database.cpp \
	android_bluetooth_HeadsetBase.cpp \
	android_bluetooth_common.cpp \
	android_bluetooth_BluetoothAudioGateway.cpp \
	android_bluetooth_RfcommSocket.cpp \
	android_bluetooth_ScoSocket.cpp \
	android_server_BluetoothDeviceService.cpp \
	android_server_BluetoothEventLoop.cpp \
	android_message_digest_sha1.cpp \
	android_ddm_DdmHandleNativeHeap.cpp \
	android_location_GpsLocationProvider.cpp \
	com_android_internal_os_ZygoteInit.cpp \
	com_android_internal_graphics_NativeUtils.cpp

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	$(LOCAL_PATH)/android/graphics \
	$(call include-path-for, corecg graphics) \
	$(call include-path-for, libhardware)/hardware \
	$(LOCAL_PATH)/../../include/ui \
	$(LOCAL_PATH)/../../include/utils \
	external/sqlite/dist \
	external/sqlite/android \
	external/expat/lib \
	external/openssl/include \
	external/tremor/Tremor \
	external/icu4c/i18n \
	external/icu4c/common \

LOCAL_SHARED_LIBRARIES := \
	libexpat \
	libnativehelper \
	libcutils \
	libutils \
	libnetutils \
	libui \
	libsgl \
	libcorecg \
	libsqlite \
	libdvm \
	libGLES_CM \
	libhardware \
	libsonivox \
	libcrypto \
	libssl \
	libicuuc \
	libicui18n \
	libicudata \
	libmedia \
	libwpa_client

ifeq ($(BOARD_HAVE_BLUETOOTH),true)
LOCAL_C_INCLUDES += \
	external/dbus \
	external/bluez/libs/include
LOCAL_CFLAGS += -DHAVE_BLUETOOTH
LOCAL_SHARED_LIBRARIES += libbluedroid libdbus
endif

ifeq ($(TARGET_ARCH),arm)
LOCAL_SHARED_LIBRARIES += \
	libdl
endif

LOCAL_LDLIBS += -lpthread -ldl

ifeq ($(TARGET_OS),linux)
ifeq ($(TARGET_ARCH),x86)
LOCAL_LDLIBS += -lrt
endif
endif

ifeq ($(WITH_MALLOC_LEAK_CHECK),true)
	LOCAL_CFLAGS += -DMALLOC_LEAK_CHECK
endif

LOCAL_MODULE:= libandroid_runtime

include $(BUILD_SHARED_LIBRARY)


include $(call all-makefiles-under,$(LOCAL_PATH))
