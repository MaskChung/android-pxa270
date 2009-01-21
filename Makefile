
export PRJROOT:=$(PWD)

include $(PRJROOT)/Rules.mak
-include $(PRJROOT)/.config

TOOLCHAIN_DIR			:= $(PRJROOT)/scripts/toolchain
TOOLCHAIN			:= $(TOOLCHAIN_DIR)/$(patsubst "%",%,$(TOOLCHAIN))
export PATH			:= $(TOOLCHAIN_DIR)/bin:$(shell echo $$PATH)
export CONFIG_DIR		:= $(PRJROOT)/config
export KERNEL_SRC_DIR		:= $(PRJROOT)/$(patsubst "%",%,$(KERNEL_SRC))
export ROOTFS_DIR		:= $(PRJROOT)/rootfs
export ROOTFS			:= $(ROOTFS_DIR)/$(patsubst "%",%,$(ROOTFS))
export BUSYBOX_SRC_DIR		:= $(PRJROOT)/$(patsubst "%",%,$(BUSYBOX_SRC))
export TARGET_DIR		:= $(PRJROOT)/target

export TARGET_ROOTFS_DIR	:= $(TARGET_DIR)/rootfs


modules:=rootfs toolchain kernel busybox

.PHONY: all ckeck_dir build_all install_all clean_all distclean menuconfig
all: check_dir
	$(MAKE) build_all
	$(MAKE) install_all

check_dir:
	@test -d $(TARGET_DIR) || mkdir -p $(TARGET_DIR)
	@test -d $(TARGET_ROOTFS_DIR) || mkdir -p $(TARGET_ROOTFS_DIR)
	@test -d $(TOOLCHAIN_DIR)/bin || $(MAKE) build_toolchain

build_all: $(addprefix build_,$(modules))

install_all: $(addprefix install_,$(modules))

clean_all: $(addprefix clean_,$(modules))

distclean:
	rm -rf $(TARGET_DIR)
	$(MAKE) clean_all

.PHONY: build_toolchain install_toolchain clean_toolchain
build_toolchain:
	@if [ ! -e $(TOOLCHAIN_DIR)/bin ] ; then \
		tar jxvf $(TOOLCHAIN) -C $(TOOLCHAIN_DIR); \
	fi

install_toolchain:
clean_toolchain:
	-find $(TOOLCHAIN_DIR)/* -maxdepth 0 -type d -exec rm -rf {} \;

.PHONY: build_kernel install_kernel clean_kernel
build_kernel:
	@if [ ! -e $(KERNEL_SRC_DIR)/.config ]; then \
		cd $(KERNEL_SRC_DIR) && $(MAKE) defconfig; \
	fi
	cd $(KERNEL_SRC_DIR) && $(MAKE)

install_kernel:
	cd $(KERNEL_SRC_DIR) && $(MAKE) INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR) modules_install 
	cp -f $(KERNEL_SRC_DIR)/arch/$(ARCH)/boot/zImage $(TARGET_DIR)
	gzip -9 -f $(TARGET_DIR)/zImage
	rm -f $(TARGET_DIR)/uImage
	$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(TARGET_DIR)/zImage.gz $(TARGET_DIR)/uImage
	rm -f $(TARGET_DIR)/zImage.gz
ifeq "$(HOST_TFTP)" "y"
	cp -f $(TARGET_DIR)/uImage $(TFTP_DIR)
endif

clean_kernel:
	cd $(KERNEL_SRC_DIR) && $(MAKE) distclean

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	@if [ ! -e $(BUSYBOX_SRC_DIR)/.config ] ; then \
		cp $(CONFIG_DIR)/busybox_config $(BUSYBOX_SRC_DIR)/.config; \
		cd $(BUSYBOX_SRC_DIR) && $(MAKE) oldconfig; \
	fi
	cd $(BUSYBOX_SRC_DIR) && $(MAKE)

install_busybox:
	cd $(BUSYBOX_SRC_DIR) && $(MAKE) install

clean_busybox:
	cd $(BUSYBOX_SRC_DIR) && $(MAKE) distclean

.PHONY: build_rootfs install_rootfs clean_rootfs
build_rootfs:
#	cd $(ROOTFS_DIR) && fakeroot $(MAKE)

install_rootfs:
	cd $(ROOTFS_DIR) && $(MAKE) install
	#cd $(ROOTFS_DIR) && fakeroot $(MAKE) install

clean_rootfs:
#	cd $(ROOTFS_DIR) && fakeroot $(MAKE) distclean

menuconfig:
	@if [ ! -e scripts/config/mconf ] ; then \
		cd scripts/config/ && $(MAKE); \
	fi
	@./scripts/config/mconf ./scripts/Config.in

# compound rules
rebuild_%:
	$(MAKE) clean_$*
	$(MAKE) build_$*

update_%:
	$(MAKE) build_$*
	$(MAKE) install_$*

.PHONY: help
help:
	@echo "EPS Android build script"
	@echo "Usage: make [targets]"
	@echo "Available targets:"
	@echo "    all              build all modules"
	@echo "    menuconfig       select components from menu"
	@echo "    clean            clean all generated files"
	@echo "    distclean        clean all generated files and target root filesystem"
	@echo "    build_<module>   build module" 
	@echo "    clean_<module>   clean all generated files"
	@echo "    install_<module> install module files"
	@echo "    rebuild_<module> run clean_<module> and build_<module>"
	@echo "    update_<module>  run build_<module> and install_<module>"
	@echo "Module list:"
	@for i in $(modules); do\
		echo "    $$i"; \
	done
