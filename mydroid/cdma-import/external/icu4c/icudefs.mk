# Make definitions that are shared by the different subprojects of ICU.
#
# Yves Arrouye.
#
# Copyright (C) 2000-2007, International Business Machines Corporation and others.
# All Rights Reserved.

#
# Some of these variables are overridden in the config/mh-* files.
#

# Shell to use

SHELL = /bin/sh

# Standard directories

prefix = /usr/local
exec_prefix = ${prefix}

bindir = ${exec_prefix}/bin
sbindir = ${exec_prefix}/sbin
datadir = ${prefix}/share
libdir = ${exec_prefix}/lib
includedir = ${prefix}/include
mandir = ${prefix}/man
sysconfdir = ${prefix}/etc

# Package information

PACKAGE = icu
VERSION = 3.8
UNICODE_VERSION = 5.0
SO_TARGET_VERSION = 38.0
SO_TARGET_VERSION_MAJOR = 38

# The ICU data external name is usually icudata; the entry point name is
# the version-dependent name (for no particular reason except it was easier
# to change the build this way). When building in common mode, the data
# name is the versioned platform-dependent one. 

ICUDATA_DIR = ${prefix}/share/$(PACKAGE)$(ICULIBSUFFIX)/$(VERSION)

ICUDATA_BASENAME_VERSION = $(ICUPREFIX)dt38
ICUDATA_ENTRY_POINT = $(ICUDATA_BASENAME_VERSION)
ICUDATA_CHAR = l
ICUDATA_PLATFORM_NAME = $(ICUDATA_BASENAME_VERSION)$(ICUDATA_CHAR)
PKGDATA_LIBSTATICNAME = -L $(STATIC_PREFIX)$(ICUPREFIX)$(DATA_STUBNAME)$(ICULIBSUFFIX)
ifeq ($(strip $(PKGDATA_MODE)),)
PKGDATA_MODE=dll
endif
ifeq ($(PKGDATA_MODE),common)
ICUDATA_NAME = $(ICUDATA_PLATFORM_NAME)
ICUPKGDATA_DIR = $(ICUDATA_DIR)
else
ifeq ($(PKGDATA_MODE),dll)
ICUDATA_NAME = $(ICUDATA_PLATFORM_NAME)
PKGDATA_LIBNAME = -L $(ICUPREFIX)$(DATA_STUBNAME)$(ICULIBSUFFIX)
ICUPKGDATA_DIR = $(libdir)
else
ICUDATA_NAME = $(ICUDATA_PLATFORM_NAME)
ICUPKGDATA_DIR = $(ICUDATA_DIR)
endif
endif
# This is needed so that make -j2 doesn't complain when invoking pkgdata's make
PKGDATA_INVOKE_OPTS = MAKEFLAGS=

# These are defined here because mh-cygwin-msvc needs to override these values.
ICUPKGDATA_INSTALL_DIR = $(DESTDIR)$(ICUPKGDATA_DIR)
ICUPKGDATA_INSTALL_LIBDIR = $(DESTDIR)$(libdir)

# If defined to a valid value, pkgdata will generate a data library more quickly
GENCCODE_ASSEMBLY = -a gcc

# ICU specific directories

pkgdatadir = $(datadir)/$(PACKAGE)$(ICULIBSUFFIX)/$(VERSION)
pkglibdir = $(libdir)/$(PACKAGE)$(ICULIBSUFFIX)/$(VERSION)
pkgsysconfdir = $(sysconfdir)/$(PACKAGE)$(ICULIBSUFFIX)

# Installation programs

MKINSTALLDIRS = $(SHELL) $(top_srcdir)/mkinstalldirs

INSTALL = /usr/bin/install -c
INSTALL_PROGRAM = ${INSTALL}
INSTALL_DATA = ${INSTALL} -m 644
INSTALL_SCRIPT = ${INSTALL}

# Library suffix (to support different C++ compilers)

ICULIBSUFFIX=

# Compiler and tools

ENABLE_DEBUG = 0
ENABLE_RELEASE = 1
EXEEXT = 
CC = gcc
CXX = g++
AR = /usr/bin/ar
ARFLAGS =  r
RANLIB = ranlib
COMPILE_LINK_ENVVAR = 

# Various flags for the tools

# DEFS is for common macro definitions.
# configure prevents user defined DEFS, and configure's DEFS is not needed
# So we ignore the DEFS that comes from configure
DEFS =
# CFLAGS is for C only flags
CFLAGS =  -O3 $(THREADSCFLAGS)
# CXXFLAGS is for C++ only flags
CXXFLAGS =  -O $(THREADSCXXFLAGS)
# CPPFLAGS is for C Pre-Processor flags
CPPFLAGS =  $(THREADSCPPFLAGS)
# LIBCFLAGS are the flags for static and shared libraries.
LIBCFLAGS = -fvisibility=hidden
# LIBCXXFLAGS are the flags for static and shared libraries.
LIBCXXFLAGS = -fvisibility=hidden
# DEFAULT_LIBS are the default libraries to link against
DEFAULT_LIBS = -lpthread -lm 
# LIB_M is for linking against the math library
LIB_M = 
# LIB_THREAD is for linking against the threading library
LIB_THREAD = 
# OUTOPT is for creating a specific output name
OUTOPT = -o # The extra space after the argument is needed.
# AR_OUTOPT is for creating a specific output name for static libraries.
AR_OUTOPT =

ENABLE_RPATH = NO
ifeq ($(ENABLE_RPATH),YES)
RPATHLDFLAGS = $(LD_RPATH)$(LD_RPATH_PRE)$(libdir)
endif
LDFLAGS =  $(RPATHLDFLAGS)

# What kind of libraries are we building and linking against?
ENABLE_STATIC = 
ENABLE_SHARED = YES

# Echo w/o newline

#ECHO_N = -n
#ECHO_C = 

# Commands to compile
COMPILE.c=    $(CC) $(CPPFLAGS) $(DEFS) $(CFLAGS) -c
COMPILE.cc=   $(CXX) $(CPPFLAGS) $(DEFS) $(CXXFLAGS) -c

# Commands to link
LINK.c=       $(CC) $(CFLAGS) $(LDFLAGS)
LINK.cc=      $(CXX) $(CXXFLAGS) $(LDFLAGS)

# Commands to make a shared library
SHLIB.c=      $(CC) $(CFLAGS) $(LDFLAGS) -shared
SHLIB.cc=     $(CXX) $(CXXFLAGS) $(LDFLAGS) -shared

# Environment variable to set a runtime search path
LDLIBRARYPATH_ENVVAR = LD_LIBRARY_PATH

# Versioned target for a shared library.
FINAL_SO_TARGET = $(SO_TARGET).$(SO_TARGET_VERSION)
MIDDLE_SO_TARGET = $(SO_TARGET).$(SO_TARGET_VERSION_MAJOR)
SHARED_OBJECT = $(FINAL_SO_TARGET)

##  How ICU libraries are named...  ex. $(LIBICU)uc$(SO)
# Prefix for the ICU library names
ICUPREFIX = icu
LIBPREFIX = lib
LIBICU = $(LIBPREFIX)$(ICUPREFIX)

## If we can't use the shared libraries, use the static libraries
ifneq ($(ENABLE_SHARED),YES)
STATIC_PREFIX_WHEN_USED = s
else
STATIC_PREFIX_WHEN_USED = 
endif

# Static library prefix and file extension
STATIC_PREFIX = s
LIBSICU = $(LIBPREFIX)$(STATIC_PREFIX)$(ICUPREFIX)
A = a
SOBJ = $(SO)

# Force removal [for make clean]
RMV = rm -rf

# Platform commands to remove or move executable and library targets
# INSTALL-L installs libraries. Override in mh-* file to INSTALL_PROGRAM
#           when the library needs to have executable permissions
INSTALL-S = $(INSTALL_PROGRAM)
INSTALL-L = $(INSTALL_DATA)

# Location of the libraries before "make install" is used
LIBDIR=$(top_builddir)/lib

# Location of the executables before "make install" is used
BINDIR=$(top_builddir)/bin

# Current full path directory.
CURR_FULL_DIR=$(shell pwd)
# Current full path directory for use in source code in a -D compiler option.
CURR_SRCCODE_FULL_DIR=$(shell pwd)

# Name flexibility for the library naming scheme.  Any modifications should
# be made in the mh- file for the specific platform.
DATA_STUBNAME = data
COMMON_STUBNAME = uc
I18N_STUBNAME = i18n
LAYOUT_STUBNAME = le
LAYOUTEX_STUBNAME = lx
IO_STUBNAME = io
TOOLUTIL_STUBNAME = tu
CTESTFW_STUBNAME = test

# Link commands to link to ICU libs
LIBICUDT=       -L$(LIBDIR) -L$(top_builddir)/stubdata -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(DATA_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)
LIBICUUC=       -L$(LIBDIR) -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(COMMON_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX) $(LIBICUDT)
LIBICUI18N=     -L$(LIBDIR) -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(I18N_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)
LIBICULE=       -L$(LIBDIR) -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(LAYOUT_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)
LIBICULX=       -L$(LIBDIR) -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(LAYOUTEX_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)
LIBCTESTFW=     -L$(top_builddir)/tools/ctestfw -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(CTESTFW_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)
LIBICUTOOLUTIL= -L$(LIBDIR) -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(TOOLUTIL_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)
LIBICUIO=       -L$(LIBDIR) -l$(STATIC_PREFIX_WHEN_USED)$(ICUPREFIX)$(IO_STUBNAME)$(ICULIBSUFFIX)$(SO_TARGET_VERSION_SUFFIX)

# Invoke, set library path for all ICU libraries.
INVOKE = $(LDLIBRARYPATH_ENVVAR)=$(LIBRARY_PATH_PREFIX)$(LIBDIR):$(top_builddir)/stubdata:$(top_builddir)/tools/ctestfw:$$$(LDLIBRARYPATH_ENVVAR) $(LEAK_CHECKER)

# Platform-specific setup
include $(top_srcdir)/config/mh-linux

# When shared libraries are disabled and static libraries are enabled,
# the C++ compiler must be used to link in the libraries for the tools.
ifneq ($(ENABLE_SHARED),YES)
LINK.c = $(LINK.cc)
endif

