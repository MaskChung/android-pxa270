ifeq "$(NFS_ROOT)" "y"
	__TARGET_DIR = $(NFS_ROOT_DIR)
else
	__TARGET_DIR = $(TARGET_ROOTFS_DIR)


.PHONY: all install distclean
all:

install:
	file -b $(BASE_ROOTFS) | awk '{print $$1 " -d -c -v $(BASE_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(__TARGET_DIR)
ifeq "$(ANDROID)" "y"
	file -b $(ANDROID_ROOTFS) | awk '{print $$1 " -d -c -v $(ANDROID_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(__TARGET_DIR)
	mv $(__TARGET_DIR)/system ../
endif
	cp -af overwrite/* $(__TARGET_DIR)
	echo "/root/mkdevs.sh" >> $(__TARGET_DIR)/etc/init.d/rc.local

	#rsync -r --exclude='.svn' $(PRJROOT)/rootfs/rootfs.overwrite/* $(TARGET_ROOTFS_DIR)

### prepare android rootfs
### tar exclude data system cache
### tar data -> target, system -> target, cache -> cache(maybe no need)
endif

distclean:
