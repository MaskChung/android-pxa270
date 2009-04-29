#
##export BASE_ROOTFS		:= $(ROOTFS_DIR)/$(patsubst "%",%,$(BASE_ROOTFS))
##export ANDROID
##export ANDROID_ROOTFS		:= $(patsubst "%",%,$(ANDROID_ROOTFS))
###export TARGET_ANDROID_ROOTFS_DIR	:= $(TARGET_DIR)/android_rootfs
##export ANDROID_GIT
##export ANDROID_GIT_ROOTFS	:= $(patsubst "%",%,$(ANDROID_GIT_ROOTFS))
#
###export ANDROID
#export ANDROID_ROOTFS		:= $(patsubst "%",%,$(ANDROID_ROOTFS))
##export TARGET_ANDROID_ROOTFS_DIR	:= $(TARGET_DIR)/android_rootfs
#export ANDROID_GIT
#export ANDROID_GIT_ROOTFS	:= $(patsubst "%",%,$(ANDROID_GIT_ROOTFS))


BASE_ROOTFS := $(PRJROOT)/$(call path-for,rootfs)/$(patsubst "%",%,$(BASE_ROOTFS))
DEMO_ROOTFS := $(PRJROOT)/$(call path-for,rootfs)/demo.tgz

MODULES += rootfs

.PHONY: build_rootfs install_rootfs clean_rootfs
build_rootfs:
install_rootfs:
	rm -rf $(PRJROOT)/$(call path-for,target-rootfs)
	rm -rf $(PRJROOT)/$(call path-for,target-android-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)

ifeq "$(ANDROID)" "y"
	mkdir -p $(PRJROOT)/$(call path-for,target-android-rootfs)
ifeq "$(ANDROID_DEMO)" "y"
	file -b $(DEMO_ROOTFS) | awk '{print $$1 " -d -c -v $(DEMO_ROOTFS)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,target-android-rootfs)
endif
endif

	file -b $(BASE_ROOTFS) | awk '{print $$1 " -d -c -v $(BASE_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(PRJROOT)/$(call path-for,target-rootfs)
	rsync -r --exclude='.svn' $(PRJROOT)/$(call path-for,rootfs-overwrite)/* $(PRJROOT)/$(call path-for,target-rootfs)

ifeq "$(ANDROID)" "y"
	rsync -r --exclude='.svn' $(PRJROOT)/$(call path-for,rootfs-overwrite-android)/* $(PRJROOT)/$(call path-for,target-android-rootfs)
endif

clean_rootfs:

