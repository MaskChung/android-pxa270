
#export BASE_ROOTFS		:= $(ROOTFS_DIR)/$(patsubst "%",%,$(BASE_ROOTFS))
#export ANDROID
#export ANDROID_ROOTFS		:= $(patsubst "%",%,$(ANDROID_ROOTFS))
##export TARGET_ANDROID_ROOTFS_DIR	:= $(TARGET_DIR)/android_rootfs
#export ANDROID_GIT
#export ANDROID_GIT_ROOTFS	:= $(patsubst "%",%,$(ANDROID_GIT_ROOTFS))

export BASE_ROOTFS		:= $(ROOTFS_DIR)/$(patsubst "%",%,$(BASE_ROOTFS))
##export ANDROID
export ANDROID_ROOTFS		:= $(patsubst "%",%,$(ANDROID_ROOTFS))
#export TARGET_ANDROID_ROOTFS_DIR	:= $(TARGET_DIR)/android_rootfs
export ANDROID_GIT
export ANDROID_GIT_ROOTFS	:= $(patsubst "%",%,$(ANDROID_GIT_ROOTFS))





.PHONY: build_rootfs install_rootfs clean_rootfs
build_rootfs:
install_rootfs:
	rm -rf $(PRJROOT)/$(call path-for,target-rootfs)
	#$(MAKE) check_dir
ifeq "$(ANDROID)" "y"
	#file -b $(ANDROID_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(TARGET_ROOTFS_DIR)
	file -b $(ANDROID_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_ROOTFS)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,target-rootfs)
	rm -f $(PRJROOT)/$(call path-for,target-rootfs)/etc
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)/etc
	cd $(PRJROOT)/$(call path-for,target-rootfs)/etc && ln -s ../system/etc/* .
	-rm -rf $(PRJROOT)/$(call path-for,target-rootfs)/data/busybox
endif
ifeq "$(ANDROID_GIT)" "y"
	file -b $(ANDROID_GIT_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_GIT_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(call path-for,target-rootfs) root
	file -b $(ANDROID_GIT_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_GIT_ROOTFS)"}' | sh - | tar xvf - -C $(call path-for,target-rootfs) data
	file -b $(ANDROID_GIT_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_GIT_ROOTFS)"}' | sh - | tar xvf - -C $(call path-for,target-rootfs) system
	-cd $(call path-for,target-rootfs)/etc && ln -s ../system/etc/* .
endif
	file -b $(BASE_ROOTFS) | awk '{print $$1 " -d -c -v $(BASE_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(call path-for,target-rootfs)
	#cp -af overwrite/* $(call path-for,target-rootfs)
	rsync -r --exclude='.svn' $(PRJROOT)/rootfs/overwrite/* $(call path-for,target-rootfs)
	#echo "/root/mkdevs.sh" >> $(__TARGET_DIR)/etc/init.d/rc.local


#	$(MAKE) build_version
#	$(MAKE) install_version
	#rsync -r --exclude='.svn' $(PRJROOT)/rootfs/rootfs.overwrite/* $(TARGET_ROOTFS_DIR)
	#rm -f ContactsProvider.apk
#
#install_rootfs:
#	rm -rf $(TARGET_ROOTFS_DIR)
#	$(MAKE) check_dir
#	cd $(ROOTFS_DIR) && $(MAKE) install
#	$(MAKE) build_version
#	$(MAKE) install_version
#	#rsync -r --exclude='.svn' $(PRJROOT)/rootfs/rootfs.overwrite/* $(TARGET_ROOTFS_DIR)
#	#rm -f ContactsProvider.apk

clean_rootfs:

