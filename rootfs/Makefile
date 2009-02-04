.PHONY: all install distclean
all:

install:
ifeq "$(NFS_ROOT)" "y"
	file -b $(ROOTFS) | awk '{print $$1 " -d -c -v $(ROOTFS)"}' | sh - | tar xvf - -C $(NFS_ROOT_DIR)
else
	file -b $(ROOTFS) | awk '{print $$1 " -d -c -v $(ROOTFS)"}' | sh - | tar xvf - -C $(TARGET_ROOTFS_DIR)/..
	#rsync -r --exclude='.svn' $(PRJROOT)/rootfs/rootfs.overwrite/* $(TARGET_ROOTFS_DIR)
###	$(PRJROOT)/scripts/bin/mkfs.jffs2 -v -e 131072 --pad=0xf00000 -r $(TARGET_ROOTFS_DIR) -o $(TARGET_DIR)/rootfs.jffs2

endif

distclean:
