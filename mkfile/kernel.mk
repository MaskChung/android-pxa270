#include setenv.mk

### maybe change KERNEL_SRC_DIR to KERNEL_SRC
KERNEL_SRC_DIR := $(PRJROOT)/$(call path-for,kernel)/$(patsubst "%",%,$(KERNEL_SRC))

MODULES += kernel

.PHONY: build_kernel install_kernel clean_kernel
build_kernel:
	@$(MAKE) check_dir ### maybe take off
	@if [ ! -e $(KERNEL_SRC_DIR)/.config ]; then \
		cd $(KERNEL_SRC_DIR) && $(MAKE) defconfig; \
	fi
	cd $(KERNEL_SRC_DIR) && $(MAKE)

install_kernel:
	cd $(KERNEL_SRC_DIR) && $(MAKE) INSTALL_MOD_PATH=$(call path-for,target-rootfs) modules_install 
	###cd $(KERNEL_SRC_DIR) && $(MAKE) INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR) modules_install 
	cp -f $(KERNEL_SRC_DIR)/arch/$(ARCH)/boot/zImage $(call path-for,target-bin)
	###cp -f $(KERNEL_SRC_DIR)/arch/$(ARCH)/boot/zImage $(TARGET_BIN_DIR)
	gzip -9 -f $(call path-for,target-bin)/zImage
	###gzip -9 -f $(TARGET_BIN_DIR)/zImage
	rm -f $(call path-for,target-bin)/uImage
	###rm -f $(TARGET_BIN_DIR)/uImage
	$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(call path-for,target-bin)/zImage.gz $(call path-for,target-bin)/uImage
	###$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(TARGET_BIN_DIR)/zImage.gz $(TARGET_BIN_DIR)/uImage
	rm -f $(call path-for,target-bin)/zImage.gz
	###rm -f $(TARGET_BIN_DIR)/zImage.gz

clean_kernel:
	cd $(KERNEL_SRC_DIR) && $(MAKE) distclean

