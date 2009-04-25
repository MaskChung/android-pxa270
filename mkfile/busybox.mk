#include setenv.mk

### maybe change KERNEL_SRC_DIR to KERNEL_SRC
BUSYBOX_SRC_DIR := $(PRJROOT)/$(call path-for,busybox)/$(patsubst "%",%,$(BUSYBOX_SRC))

MODULES += busybox

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	@$(MAKE) check_dir
	@if [ ! -e $(BUSYBOX_SRC_DIR)/.config ] ; then \
		cp $(CONFIG_DIR)/busybox_config $(BUSYBOX_SRC_DIR)/.config; \
		cd $(BUSYBOX_SRC_DIR) && $(MAKE) oldconfig; \
	fi
	cd $(BUSYBOX_SRC_DIR) && $(MAKE)

install_busybox:
	cd $(BUSYBOX_SRC_DIR) && $(MAKE) install
	#@if [ -e $(TARGET_ROOTFS_DIR)/bin/busybox ] ; then
	if [ ! -e $(call path-for,target-rootfs)/bin/busybox ] ; then \
		$(error $(call path-for,target-rootfs)/bin/busybox not be installed); \
	else
		chmod u+s $(call path-for,target-rootfs)/bin/busybox; \
	fi

clean_busybox:
	cd $(BUSYBOX_SRC_DIR) && $(MAKE) distclean


