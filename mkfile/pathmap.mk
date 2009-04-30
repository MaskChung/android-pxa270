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

#
# A central place to define mappings to paths, to avoid hard-coding
# them in Android.mk files.
#
# TODO: Allow each project to define stuff like this before the per-module
#       Android.mk files are included, so we don't need to have a big central
#       list.
#

#
# A mapping from shorthand names to include directories.
#
pathmap := \
    toolchain:scripts/toolchain \
    kernel:kernel \
    busybox:app \
    rootfs:rootfs \
    rootfs-overwrite:rootfs/overwrite \
    rootfs-overwrite-android:rootfs/overwrite-android \
    target:target \
    target-rootfs:target/rootfs \
    target-bin:target/bin \
    target-android-rootfs:target/android-rootfs \
    mkyaffs2image:scripts/bin/yaffs2/utils \
    mkfs-jffs2:scripts/bin/mtd/util \
    config:config \
    app-bin:app/bin \
    mconf:scripts/config \
    mconf-conf-in:scripts \
    mkfile:mkfile

#
# Returns the path to the requested module's include directory,
# relative to the root of the source tree.  Does not handle external
# modules.
#
# $(1): a list of modules (or other named entities) to find the includes for
#
define path-for
$(foreach n,$(1),$(patsubst $(n):%,%,$(filter $(n):%,$(pathmap))))
endef

