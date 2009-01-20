.PHONY: all install distclean
all:

install:
ifeq "$(NFS_ROOT)" "y"
	tar zxvf $(ROOTFS) -C $(NFS_ROOT_DIR)
else
	tar zxvf $(ROOTFS) -C $(TARGET_DIR)
	$(PRJROOT)/scripts/bin/mkfs.jffs2 -v -e 131072 --pad=0xf00000 -r $(TARGET_ROOTFS_DIR) -o $(TARGET_DIR)/rootfs.jffs2
endif

distclean:
	rm -rf $(TARGET_ROOTFS_DIR)
