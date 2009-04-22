# Configuration for Linux on ARM.
# Included by combo/select.make

# You can set TARGET_ARCH_VERSION to use an arch version other
# than ARMv5TE
ifeq ($(strip $(TARGET_ARCH_VERSION)),)
TARGET_ARCH_VERSION := armv5te
endif

# This set of if blocks sets makefile variables similar to preprocesser
# defines in system/core/include/arch/<combo>/AndroidConfig.h. Their
# purpose is to allow module Android.mk files to selctively compile
# different versions of code based upon the funtionality and 
# instructions available in a given architecture version.
#
# The blocks also define specific arch_version_cflags, which 
# include defines, and compiler settings for the given architecture
# version.
#
# Note: Hard coding the 'tune' value here is probably not ideal,
# and a better solution should be found in the future.
#
# With two or three different versions this if block approach is
# fine. If/when this becomes large, please change this to include
# architecture versions specific Makefiles which define these
# variables.
# 
ifeq ($(TARGET_ARCH_VERSION),armv5te)
ARCH_ARM_HAVE_THUMB_SUPPORT := true
ARCH_ARM_HAVE_FAST_INTERWORKING := true
ARCH_ARM_HAVE_64BIT_DATA := true
ARCH_ARM_HAVE_HALFWORD_MULTIPLY := true
ARCH_ARM_HAVE_CLZ := true
ARCH_ARM_HAVE_FFS := true

arch_version_cflags := -march=armv5te -mtune=xscale  -D__ARM_ARCH_5__ \
	-D__ARM_ARCH_5T__ -D__ARM_ARCH_5TE__
else
ifeq ($(TARGET_ARCH_VERSION),armv4)
$(warning ARMv4 support is currently a work in progress. It does not work right now!)
ARCH_ARM_HAVE_THUMB_SUPPORT := false
ARCH_ARM_HAVE_THUMB_INTERWORKING := false
ARCH_ARM_HAVE_64BIT_DATA := false
ARCH_ARM_HAVE_HALFWORD_MULTIPLY := false
ARCH_ARM_HAVE_CLZ := false
ARCH_ARM_HAVE_FFS := false

DEFAULT_TARGET_CPU := arm920

arch_version_cflags := -march=armv4 -mtune=arm920 -D__ARM_ARCH_4__
else
$(error Unknown ARM architecture version: $(TARGET_ARCH_VERSION))
endif
endif

# You can set TARGET_TOOLS_PREFIX to get gcc from somewhere else
ifeq ($(strip $($(combo_target)TOOLS_PREFIX)),)
$(combo_target)TOOLS_PREFIX := \
	prebuilt/$(HOST_PREBUILT_TAG)/toolchain/arm-eabi-4.2.1/bin/arm-eabi-
endif

$(combo_target)CC := $($(combo_target)TOOLS_PREFIX)gcc$(HOST_EXECUTABLE_SUFFIX)
$(combo_target)CXX := $($(combo_target)TOOLS_PREFIX)g++$(HOST_EXECUTABLE_SUFFIX)
$(combo_target)AR := $($(combo_target)TOOLS_PREFIX)ar$(HOST_EXECUTABLE_SUFFIX)
$(combo_target)OBJCOPY := $($(combo_target)TOOLS_PREFIX)objcopy$(HOST_EXECUTABLE_SUFFIX)
$(combo_target)LD := $($(combo_target)TOOLS_PREFIX)ld$(HOST_EXECUTABLE_SUFFIX)

$(combo_target)NO_UNDEFINED_LDFLAGS := -Wl,--no-undefined

TARGET_arm_release_CFLAGS :=    -fomit-frame-pointer \
                                -fstrict-aliasing    \
                                -funswitch-loops     \
                                -finline-limit=300

# Modules can choose to compile some source as thumb. As 
# non-thumb enabled targets are supported, this is treated
# as a 'hint'. If thumb is not enabled, these files are just
# compiled as ARM.
ifeq ($(ARCH_ARM_HAVE_THUMB_SUPPORT),true)
TARGET_thumb_release_CFLAGS :=  -mthumb \
                                -Os \
                                -fomit-frame-pointer \
                                -fno-strict-aliasing \
                                -finline-limit=64
else
TARGET_thumb_release_CFLAGS := $(TARGET_arm_release_CFLAGS)
endif

# When building for debug, compile everything as arm.
TARGET_arm_debug_CFLAGS := $(TARGET_arm_release_CFLAGS) -fno-omit-frame-pointer -fno-strict-aliasing
TARGET_thumb_debug_CFLAGS := $(TARGET_thumb_release_CFLAGS) -marm -fno-omit-frame-pointer

# NOTE: if you try to build a debug build with thumb, several
# of the libraries (libpv, libwebcore, libkjs) need to be built
# with -mlong-calls.  When built at -O0, those libraries are
# too big for a thumb "BL <label>" to go from one end to the other.

## As hopefully a temporary hack,
## use this to force a full ARM build (for easier debugging in gdb)
## (don't forget to do a clean build)
##TARGET_arm_release_CFLAGS := $(TARGET_arm_release_CFLAGS) -fno-omit-frame-pointer
##TARGET_thumb_release_CFLAGS := $(TARGET_thumb_release_CFLAGS) -marm -fno-omit-frame-pointer

android_config_h := $(call select-android-config-h,linux-arm)
arch_include_dir := $(dir $(android_config_h))

$(combo_target)GLOBAL_CFLAGS += \
			-msoft-float -fpic \
			-ffunction-sections \
			-funwind-tables \
			-fstack-protector \
			$(arch_version_cflags) \
			-include $(android_config_h) \
			-I $(arch_include_dir)

# We only need thumb interworking in cases where thumb support
# is available in the architecture, and just to be sure, (and
# since sometimes thumb-interwork appears to be default), we
# specifically disable when thumb support is unavailable.
ifeq ($(ARCH_ARM_HAVE_THUMB_SUPPORT),true)
$(combo_target)GLOBAL_CFLAGS +=	-mthumb-interwork
else
$(combo_target)GLOBAL_CFLAGS +=	-mno-thumb-interwork
endif

$(combo_target)GLOBAL_CPPFLAGS += -fvisibility-inlines-hidden

$(combo_target)RELEASE_CFLAGS := \
			-DSK_RELEASE -DNDEBUG \
			-O2 -g \
			-Wstrict-aliasing=2 \
			-finline-functions \
			-fno-inline-functions-called-once \
			-fgcse-after-reload \
			-frerun-cse-after-loop \
			-frename-registers

libc_root := bionic/libc
libm_root := bionic/libm
libstdc++_root := bionic/libstdc++
libthread_db_root := bionic/libthread_db


## on some hosts, the target cross-compiler is not available so do not run this command
ifneq ($(wildcard $($(combo_target)CC)),)
# We compile with the global cflags to ensure that 
# any flags which affect libgcc are correctly taken
# into account.
$(combo_target)LIBGCC := $(shell $($(combo_target)CC) $($(combo_target)GLOBAL_CFLAGS) -print-libgcc-file-name)
endif

# unless CUSTOM_KERNEL_HEADERS is defined, we're going to use
# symlinks located in out/ to point to the appropriate kernel
# headers. see 'config/kernel_headers.make' for more details
#
ifneq ($(CUSTOM_KERNEL_HEADERS),)
    KERNEL_HEADERS_COMMON := $(CUSTOM_KERNEL_HEADERS)
    KERNEL_HEADERS_ARCH   := $(CUSTOM_KERNEL_HEADERS)
else
    KERNEL_HEADERS_COMMON := $(libc_root)/kernel/common
    KERNEL_HEADERS_ARCH   := $(libc_root)/kernel/arch-$(TARGET_ARCH)
endif
KERNEL_HEADERS := $(KERNEL_HEADERS_COMMON) $(KERNEL_HEADERS_ARCH)

$(combo_target)C_INCLUDES := \
	$(libc_root)/arch-arm/include \
	$(libc_root)/include \
	$(libstdc++_root)/include \
	$(KERNEL_HEADERS) \
	$(libm_root)/include \
	$(libm_root)/include/arch/arm \
	$(libthread_db_root)/include

TARGET_CRTBEGIN_STATIC_O := $(TARGET_OUT_STATIC_LIBRARIES)/crtbegin_static.o
TARGET_CRTBEGIN_DYNAMIC_O := $(TARGET_OUT_STATIC_LIBRARIES)/crtbegin_dynamic.o
TARGET_CRTEND_O := $(TARGET_OUT_STATIC_LIBRARIES)/crtend_android.o

TARGET_STRIP_MODULE:=true

$(combo_target)DEFAULT_SYSTEM_SHARED_LIBRARIES := libc libstdc++ libm

$(combo_target)CUSTOM_LD_COMMAND := true
define transform-o-to-shared-lib-inner
$(TARGET_CXX) \
	-nostdlib -Wl,-soname,$(notdir $@) -Wl,-T,$(BUILD_SYSTEM)/armelf.xsc \
	-Wl,--gc-sections \
	-Wl,-shared,-Bsymbolic \
	$(TARGET_GLOBAL_LD_DIRS) \
	$(PRIVATE_ALL_OBJECTS) \
	-Wl,--whole-archive \
	$(call normalize-host-libraries,$(PRIVATE_ALL_WHOLE_STATIC_LIBRARIES)) \
	-Wl,--no-whole-archive \
	$(call normalize-target-libraries,$(PRIVATE_ALL_STATIC_LIBRARIES)) \
	$(call normalize-target-libraries,$(PRIVATE_ALL_SHARED_LIBRARIES)) \
	-o $@ \
	$(PRIVATE_LDFLAGS) \
	$(TARGET_LIBGCC)
endef

define transform-o-to-executable-inner
$(TARGET_CXX) -nostdlib -Bdynamic -Wl,-T,$(BUILD_SYSTEM)/armelf.x \
	-Wl,-dynamic-linker,/system/bin/linker \
    -Wl,--gc-sections \
	-Wl,-z,nocopyreloc \
	-o $@ \
	$(TARGET_GLOBAL_LD_DIRS) \
	-Wl,-rpath-link=$(TARGET_OUT_INTERMEDIATE_LIBRARIES) \
	$(call normalize-target-libraries,$(PRIVATE_ALL_SHARED_LIBRARIES)) \
	$(TARGET_CRTBEGIN_DYNAMIC_O) \
	$(PRIVATE_ALL_OBJECTS) \
	$(call normalize-target-libraries,$(PRIVATE_ALL_STATIC_LIBRARIES)) \
	$(PRIVATE_LDFLAGS) \
	$(TARGET_LIBGCC) \
	$(TARGET_CRTEND_O)
endef

define transform-o-to-static-executable-inner
$(TARGET_CXX) -nostdlib -Bstatic -Wl,-T,$(BUILD_SYSTEM)/armelf.x \
    -Wl,--gc-sections \
	-o $@ \
	$(TARGET_GLOBAL_LD_DIRS) \
	$(TARGET_CRTBEGIN_STATIC_O) \
	$(PRIVATE_LDFLAGS) \
	$(PRIVATE_ALL_OBJECTS) \
	$(call normalize-target-libraries,$(PRIVATE_ALL_STATIC_LIBRARIES)) \
	$(TARGET_LIBGCC) \
	$(TARGET_CRTEND_O)
endef
