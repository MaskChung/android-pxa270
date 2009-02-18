
.PHONY: all install distclean
all:
install:
ifeq "$(ANDROID)" "y"
	#file -b $(ANDROID_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(TARGET_ROOTFS_DIR)
	file -b $(ANDROID_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_ROOTFS)"}' | sh - | tar xvf - -C $(TARGET_ROOTFS_DIR)
	rm -f $(TARGET_ROOTFS_DIR)/etc
	mkdir -p $(TARGET_ROOTFS_DIR)/etc
#	cd $(TARGET_ROOTFS_DIR)/system/etc && ln -s * ../../etc/
#	ln -s * ../../etc/
	cd $(TARGET_ROOTFS_DIR)/etc && ln -s ../system/etc/* .
	-rm -rf $(TARGET_ROOTFS_DIR)/data/busybox
endif
ifeq "$(ANDROID_GIT)" "y"
#tar zxvf git.tgz -C ./aabb data
	file -b $(ANDROID_GIT_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_GIT_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(TARGET_ROOTFS_DIR) root
	file -b $(ANDROID_GIT_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_GIT_ROOTFS)"}' | sh - | tar xvf - -C $(TARGET_ROOTFS_DIR) data
	file -b $(ANDROID_GIT_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_GIT_ROOTFS)"}' | sh - | tar xvf - -C $(TARGET_ROOTFS_DIR) system
	-cd $(TARGET_ROOTFS_DIR)/etc && ln -s ../system/etc/* .
endif
	file -b $(BASE_ROOTFS) | awk '{print $$1 " -d -c -v $(BASE_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(TARGET_ROOTFS_DIR)
	#cp -af overwrite/* $(TARGET_ROOTFS_DIR)
	rsync -r --exclude='.svn' $(PRJROOT)/rootfs/overwrite/* $(TARGET_ROOTFS_DIR)
	#echo "/root/mkdevs.sh" >> $(__TARGET_DIR)/etc/init.d/rc.local

distclean:
