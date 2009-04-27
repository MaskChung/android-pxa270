
KERNEL_SRC := $(PRJROOT)/$(call path-for,kernel)/$(patsubst "%",%,$(KERNEL_SRC))

MODULES += kernel

.PHONY: build_kernel install_kernel clean_kernel
build_kernel:
	if [ ! -d $(KERNEL_SRC) ] ; then \
		$(error $(KERNEL_SRC) was not found); \
	else [ ! -e $(KERNEL_SRC)/.config ]; then \
		$(MAKE) -C $(KERNEL_SRC) defconfig; \
	fi
	$(MAKE) -C $(KERNEL_SRC)

#
#build_kernel:
#	@$(MAKE) check_dir ### maybe take off
#	@if [ ! -e $(KERNEL_SRC)/.config ]; then \
#		cd $(KERNEL_SRC) && $(MAKE) defconfig; \
#	fi
#	cd $(KERNEL_SRC) && $(MAKE)

install_kernel:
	cd $(KERNEL_SRC) && $(MAKE) INSTALL_MOD_PATH=$(PRJROOT)/$(call path-for,target-rootfs) modules_install 
	###cd $(KERNEL_SRC) && $(MAKE) INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR) modules_install 
	cp -f $(KERNEL_SRC)/arch/$(ARCH)/boot/zImage $(PRJROOT)/$(call path-for,target-bin)
	###cp -f $(KERNEL_SRC)/arch/$(ARCH)/boot/zImage $(TARGET_BIN_DIR)
	gzip -9 -f $(PRJROOT)/$(call path-for,target-bin)/zImage
	###gzip -9 -f $(TARGET_BIN_DIR)/zImage
	rm -f $(PRJROOT)/$(call path-for,target-bin)/uImage
	###rm -f $(TARGET_BIN_DIR)/uImage
	$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(PRJROOT)/$(call path-for,target-bin)/zImage.gz $(PRJROOT)/$(call path-for,target-bin)/uImage ### change mkimage to path-for
	###$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(TARGET_BIN_DIR)/zImage.gz $(TARGET_BIN_DIR)/uImage
	rm -f $(PRJROOT)/$(call path-for,target-bin)/zImage.gz
	###rm -f $(TARGET_BIN_DIR)/zImage.gz

clean_kernel:
	$(MAKE) -C $(KERNEL_SRC) distclean

