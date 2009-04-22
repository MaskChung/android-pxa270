# This is included by the top-level Makefile.
# It sets up standard variables based on the
# current configuration and platform, which
# are not specific to what is being built.

# Standard source directories.
SRC_DOCS:= $(TOPDIR)docs
# TODO: Enforce some kind of layering; only add include paths
#       when a module links against a particular library.
# TODO: See if we can remove most of these from the global list.
SRC_HEADERS := \
	$(TOPDIR)system/core/include \
	$(TOPDIR)hardware/libhardware/include \
	$(TOPDIR)hardware/ril/include \
	$(TOPDIR)dalvik/libnativehelper/include \
	$(TOPDIR)frameworks/base/include \
	$(TOPDIR)external/skia/include
SRC_HOST_HEADERS:=$(TOPDIR)tools/include
SRC_LIBRARIES:= $(TOPDIR)libs
SRC_SERVERS:= $(TOPDIR)servers
SRC_TOOLS:= $(TOPDIR)tools
SRC_TARGET_DIR := $(TOPDIR)build/target

# Some specific paths to tools
SRC_DROIDDOC_DIR := $(TOPDIR)build/tools/droiddoc

# Various mappings to avoid hard-coding paths all over the place
include $(BUILD_SYSTEM)/pathmap.mk

# ###############################################################
# Build system internal files
# ###############################################################

BUILD_COMBOS:= $(BUILD_SYSTEM)/combo

CLEAR_VARS:= $(BUILD_SYSTEM)/clear_vars.mk
BUILD_HOST_STATIC_LIBRARY:= $(BUILD_SYSTEM)/host_static_library.mk
BUILD_HOST_SHARED_LIBRARY:= $(BUILD_SYSTEM)/host_shared_library.mk
BUILD_STATIC_LIBRARY:= $(BUILD_SYSTEM)/static_library.mk
BUILD_RAW_STATIC_LIBRARY := $(BUILD_SYSTEM)/raw_static_library.mk
BUILD_SHARED_LIBRARY:= $(BUILD_SYSTEM)/shared_library.mk
BUILD_EXECUTABLE:= $(BUILD_SYSTEM)/executable.mk
BUILD_RAW_EXECUTABLE:= $(BUILD_SYSTEM)/raw_executable.mk
BUILD_HOST_EXECUTABLE:= $(BUILD_SYSTEM)/host_executable.mk
BUILD_PACKAGE:= $(BUILD_SYSTEM)/package.mk
BUILD_HOST_PREBUILT:= $(BUILD_SYSTEM)/host_prebuilt.mk
BUILD_PREBUILT:= $(BUILD_SYSTEM)/prebuilt.mk
BUILD_MULTI_PREBUILT:= $(BUILD_SYSTEM)/multi_prebuilt.mk
BUILD_JAVA_LIBRARY:= $(BUILD_SYSTEM)/java_library.mk
BUILD_STATIC_JAVA_LIBRARY:= $(BUILD_SYSTEM)/static_java_library.mk
BUILD_HOST_JAVA_LIBRARY:= $(BUILD_SYSTEM)/host_java_library.mk
BUILD_DROIDDOC:= $(BUILD_SYSTEM)/droiddoc.mk
BUILD_COPY_HEADERS := $(BUILD_SYSTEM)/copy_headers.mk
BUILD_KEY_CHAR_MAP := $(BUILD_SYSTEM)/key_char_map.mk

# ###############################################################
# Parse out any modifier targets.
# ###############################################################

# The 'showcommands' goal says to show the full command
# lines being executed, instead of a short message about
# the kind of operation being done.
SHOW_COMMANDS:= $(filter showcommands,$(MAKECMDGOALS))


# ###############################################################
# Set common values
# ###############################################################

# These can be changed to modify both host and device modules.
COMMON_GLOBAL_CFLAGS:= -DANDROID -fmessage-length=0 -W -Wall -Wno-unused
COMMON_DEBUG_CFLAGS:=
COMMON_RELEASE_CFLAGS:= -DNDEBUG -UDEBUG

COMMON_GLOBAL_CPPFLAGS:=
COMMON_DEBUG_CPPFLAGS:=
COMMON_RELEASE_CPPFLAGS:=

# Set the extensions used for various packages
COMMON_PACKAGE_SUFFIX := .zip
COMMON_JAVA_PACKAGE_SUFFIX := .jar
COMMON_ANDROID_PACKAGE_SUFFIX := .apk


# ###############################################################
# Include sub-configuration files
# ###############################################################

# ---------------------------------------------------------------
# Try to include buildspec.mk, which will try to set stuff up.
# If this file doesn't exist, the environemnt variables will
# be used, and if that doesn't work, then the default is an
# arm build
-include $(TOPDIR)buildspec.mk

# ---------------------------------------------------------------
# Handle the setup of TARGET_PRODUCT and related variables.
include $(BUILD_SYSTEM)/product_config.mk

# ---------------------------------------------------------------
# Define most of the global variables.
include $(BUILD_SYSTEM)/envsetup.mk

# $(1): os/arch
define select-android-config-h
system/core/include/arch/$(1)/AndroidConfig.h
endef

combo_target := HOST_
include $(BUILD_SYSTEM)/combo/select.mk

# on windows, the tools have .exe at the end, and we depend on the
# host config stuff being done first

combo_target := TARGET_
include $(BUILD_SYSTEM)/combo/select.mk

# Pick a Java compiler.
include $(BUILD_SYSTEM)/combo/javac.mk

# ---------------------------------------------------------------
# Check that the configuration is current.  We check that
# BUILD_ENV_SEQUENCE_NUMBER is current against this value.

ifneq (,$(strip $(BUILD_ENV_SEQUENCE_NUMBER)))
ifneq ($(BUILD_ENV_SEQUENCE_NUMBER),$(CORRECT_BUILD_ENV_SEQUENCE_NUMBER))
$(warning BUILD_ENV_SEQUENCE_NUMBER is set incorrectly.)
$(info *** If you use envsetup/lunch/choosecombo:)
$(info ***   - Re-execute envsetup (". envsetup.sh"))
$(info ***   - Re-run lunch or choosecombo)
$(info *** If you use buildspec.mk:)
$(info ***   - Look at buildspec.mk.default to see what has changed)
$(info ***   - Update BUILD_ENV_SEQUENCE_NUMBER to "$(CORRECT_BUILD_ENV_SEQUENCE_NUMBER)")
$(error bailing..)
endif
endif


# ---------------------------------------------------------------
# Generic tools.

LEX:= flex
YACC:= bison -d
DOXYGEN:= doxygen
AAPT := $(HOST_OUT_EXECUTABLES)/aapt$(HOST_EXECUTABLE_SUFFIX)
ACP := $(HOST_OUT_EXECUTABLES)/acp$(HOST_EXECUTABLE_SUFFIX)
AIDL := $(HOST_OUT_EXECUTABLES)/aidl$(HOST_EXECUTABLE_SUFFIX)
ICUDATA := $(HOST_OUT_EXECUTABLES)/icudata$(HOST_EXECUTABLE_SUFFIX)
SIGNAPK_JAR := $(HOST_OUT_JAVA_LIBRARIES)/signapk$(COMMON_JAVA_PACKAGE_SUFFIX)
MKBOOTFS := $(HOST_OUT_EXECUTABLES)/mkbootfs$(HOST_EXECUTABLE_SUFFIX)
MKBOOTIMG := $(HOST_OUT_EXECUTABLES)/mkbootimg$(HOST_EXECUTABLE_SUFFIX)
MKYAFFS2 := $(HOST_OUT_EXECUTABLES)/mkyaffs2image$(HOST_EXECUTABLE_SUFFIX)
APICHECK := $(HOST_OUT_EXECUTABLES)/apicheck$(HOST_EXECUTABLE_SUFFIX)

# dx is java behind a shell script; no .exe necessary.
DX := $(HOST_OUT_EXECUTABLES)/dx
KCM := $(HOST_OUT_EXECUTABLES)/kcm$(HOST_EXECUTABLE_SUFFIX)
ZIPALIGN := $(HOST_OUT_EXECUTABLES)/zipalign$(HOST_EXECUTABLE_SUFFIX)
FINDBUGS := prebuilt/common/findbugs/bin/findbugs
LOCALIZE := $(HOST_OUT_EXECUTABLES)/localize$(HOST_EXECUTABLE_SUFFIX)

# Binary prelinker/compressor tools
APRIORI := $(HOST_OUT_EXECUTABLES)/apriori$(HOST_EXECUTABLE_SUFFIX)
LSD := $(HOST_OUT_EXECUTABLES)/lsd$(HOST_EXECUTABLE_SUFFIX)
SOSLIM := $(HOST_OUT_EXECUTABLES)/soslim$(HOST_EXECUTABLE_SUFFIX)

# Deal with archaic version of bison on Mac OS X.
ifeq ($(filter 1.28,$(shell $(YACC) -V)),)
YACC_HEADER_SUFFIX:= .hpp
else
YACC_HEADER_SUFFIX:= .cpp.h
endif

# Don't use column under Windows, cygwin or not
ifeq ($(HOST_OS),windows)
COLUMN:= cat
else
COLUMN:= column
endif

dir := $(shell uname)
ifeq ($(HOST_OS),windows)
dir := $(HOST_OS)
endif
ifeq ($(HOST_OS),darwin)
dir := $(HOST_OS)-$(HOST_ARCH)
endif
OLD_FLEX := prebuilt/$(HOST_PREBUILT_TAG)/flex/flex-2.5.4a$(HOST_EXECUTABLE_SUFFIX)

ifeq ($(HOST_OS),darwin)
# Mac OS' screwy version of java uses a non-standard directory layout
# and doesn't even seem to have tools.jar.  On the other hand, javac seems
# to be able to magically find the classes in there, wherever they are, so
# leave this blank
HOST_JDK_TOOLS_JAR :=
else
HOST_JDK_TOOLS_JAR:= $(shell $(BUILD_SYSTEM)/find-jdk-tools-jar.sh)
endif

# It's called md5 on Mac OS and md5sum on Linux
ifeq ($(HOST_OS),darwin)
MD5SUM:=md5 -q
else
MD5SUM:=md5sum
endif

# ###############################################################
# Set up final options.
# ###############################################################

HOST_GLOBAL_CFLAGS += $(COMMON_GLOBAL_CFLAGS)
HOST_DEBUG_CFLAGS += $(COMMON_DEBUG_CFLAGS)
HOST_RELEASE_CFLAGS += $(COMMON_RELEASE_CFLAGS)

HOST_GLOBAL_CPPFLAGS += $(COMMON_GLOBAL_CPPFLAGS)
HOST_DEBUG_CPPFLAGS += $(COMMON_DEBUG_CPPFLAGS)
HOST_RELEASE_CPPFLAGS += $(COMMON_RELEASE_CPPFLAGS)

TARGET_GLOBAL_CFLAGS += $(COMMON_GLOBAL_CFLAGS)
TARGET_DEBUG_CFLAGS += $(COMMON_DEBUG_CFLAGS)
TARGET_RELEASE_CFLAGS += $(COMMON_RELEASE_CFLAGS)

TARGET_GLOBAL_CPPFLAGS += $(COMMON_GLOBAL_CPPFLAGS)
TARGET_DEBUG_CPPFLAGS += $(COMMON_DEBUG_CPPFLAGS)
TARGET_RELEASE_CPPFLAGS += $(COMMON_RELEASE_CPPFLAGS)

HOST_GLOBAL_LD_DIRS += -L$(HOST_OUT_INTERMEDIATE_LIBRARIES) -L/usr/X11R6/lib
TARGET_GLOBAL_LD_DIRS += -L$(TARGET_OUT_INTERMEDIATE_LIBRARIES)

HOST_PROJECT_INCLUDES:= $(SRC_HEADERS) $(SRC_HOST_HEADERS) $(HOST_OUT_HEADERS)
TARGET_PROJECT_INCLUDES:= $(SRC_HEADERS) $(TARGET_OUT_HEADERS)

ifeq ($(HOST_BUILD_TYPE),release)
HOST_GLOBAL_CFLAGS+= $(HOST_RELEASE_CFLAGS)
HOST_GLOBAL_CPPFLAGS+= $(HOST_RELEASE_CPPFLAGS)
else
HOST_GLOBAL_CFLAGS+= $(HOST_DEBUG_CFLAGS)
HOST_GLOBAL_CPPFLAGS+= $(HOST_DEBUG_CPPFLAGS)
endif

ifeq ($(TARGET_BUILD_TYPE),release)
TARGET_GLOBAL_CFLAGS+= $(TARGET_RELEASE_CFLAGS)
TARGET_GLOBAL_CPPFLAGS+= $(TARGET_RELEASE_CPPFLAGS)
else
TARGET_GLOBAL_CFLAGS+= $(TARGET_DEBUG_CFLAGS)
TARGET_GLOBAL_CPPFLAGS+= $(TARGET_DEBUG_CPPFLAGS)
endif

# TODO: do symbol compression
TARGET_COMPRESS_MODULE_SYMBOLS := false
TARGET_PRELINK_MODULE := true

PREBUILT_IS_PRESENT := $(if $(wildcard prebuilt/Android.mk),true)


# ###############################################################
# Collect a list of the SDK versions that we could compile against
# For use with the LOCAL_SDK_VERSION variable for include $(BUILD_PACKAGE)
# ###############################################################

# The files that we can convert into android.jars are are in config/api/*.xml
# The 'current' version is whatever this source tree is.  Once the apicheck
# tool can generate the stubs from the xml files, we'll use that to be
# able to build back-versions.  In the meantime, 'current' is the only
# one supported.  
#
# sgrax     is the opposite of xargs.  It takes the list of args and puts them
#           on each line for sort to process.
# sort -g   is a numeric sort, so 1 2 3 10 instead of 1 10 2 3.
TARGET_AVAILABLE_SDK_VERSIONS := current \
        $(shell function sgrax() { \
                while [ -n "$$1" ] ; do echo $$1 ; shift ; done \
            } ; \
            ( sgrax $(patsubst $(BUILD_SYSTEM)/api/%.xml,%, \
                $(filter-out $(BUILD_SYSTEM)/api/current.xml, \
                $(shell find $(BUILD_SYSTEM)/api -name "*.xml"))) | sort -g ) )


INTERNAL_PLATFORM_API_FILE := $(TARGET_OUT_COMMON_INTERMEDIATES)/PACKAGING/public_api.xml


