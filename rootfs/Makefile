.PHONY: all install distclean
all:

install:
ifeq "$(NFS_ROOT)" "y"
	tar zxvf $(ROOTFS) -C $(NFS_ROOT_DIR)
else
	$(shell file $(ROOTFS) | awk '{print $2 " -d -c -v $(ROOTFS)"}' | sh - | tar xvf - -C $(TARGET_ROOTFS_DIR)/..)
	#rsync -r --exclude='.svn' $(PRJROOT)/rootfs/rootfs.overwrite/* $(TARGET_ROOTFS_DIR)
	#tar zxvf $(ROOTFS) -C $(TARGET_ROOTFS_DIR)/..
###	$(PRJROOT)/scripts/bin/mkfs.jffs2 -v -e 131072 --pad=0xf00000 -r $(TARGET_ROOTFS_DIR) -o $(TARGET_DIR)/rootfs.jffs2

endif

distclean:
