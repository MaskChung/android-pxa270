
export PRJROOT:=$(PWD)

include $(PRJROOT)/Rules.mak
-include $(PRJROOT)/.config

TOOLCHAIN_DIR			:= $(PRJROOT)/scripts/toolchain
TOOLCHAIN			:= $(TOOLCHAIN_DIR)/$(patsubst "%",%,$(TOOLCHAIN))
export PATH			:= $(TOOLCHAIN_DIR)/bin:$(shell echo $$PATH)
CONFIG_DIR			:= $(PRJROOT)/config
KERNEL_SRC_DIR			:= $(PRJROOT)/$(patsubst "%",%,$(KERNEL_SRC))
ROOTFS_DIR			:= $(PRJROOT)/rootfs
export ROOTFS			:= $(ROOTFS_DIR)/$(patsubst "%",%,$(ROOTFS))
BUSYBOX_SRC_DIR			:= $(PRJROOT)/$(patsubst "%",%,$(BUSYBOX_SRC))
export TARGET_DIR		:= $(PRJROOT)/target

export TARGET_ROOTFS_DIR	:= $(TARGET_DIR)/rootfs
export TARGET_BIN_DIR		:= $(TARGET_DIR)/bin

BUILT_VERSION			:= $(TARGET_BIN_DIR)/built_version

modules:=rootfs toolchain kernel busybox version

.PHONY: all ckeck_dir build install clean distclean menuconfig
.PHONY: jffs2 yaffs2
all: check_dir
	$(MAKE) build
	$(MAKE) install
	$(MAKE) jffs2

check_dir:
	@test -d $(TARGET_DIR) || mkdir -p $(TARGET_DIR)
	@test -d $(TARGET_ROOTFS_DIR) || mkdir -p $(TARGET_ROOTFS_DIR)
	@test -d $(TARGET_BIN_DIR) || mkdir -p $(TARGET_BIN_DIR)
	@test -d $(TOOLCHAIN_DIR)/bin || $(MAKE) build_toolchain

build: $(addprefix build_,$(modules))

install: $(addprefix install_,$(modules))

clean: distclean
distclean: $(addprefix clean_,$(modules))
	rm -rf $(TARGET_DIR)

.PHONY: build_toolchain install_toolchain clean_toolchain
build_toolchain:
	@if [ ! -e $(TOOLCHAIN_DIR)/bin ] ; then \
		file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - -C $(TOOLCHAIN_DIR); \
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
	cp -f $(KERNEL_SRC_DIR)/arch/$(ARCH)/boot/zImage $(TARGET_BIN_DIR)
	gzip -9 -f $(TARGET_BIN_DIR)/zImage
	rm -f $(TARGET_BIN_DIR)/uImage
	$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(TARGET_BIN_DIR)/zImage.gz $(TARGET_BIN_DIR)/uImage
	rm -f $(TARGET_BIN_DIR)/zImage.gz
ifeq "$(HOST_TFTP)" "y"
	cp -f $(TARGET_BIN_DIR)/uImage $(TFTP_DIR)
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
	cd $(ROOTFS_DIR) && $(MAKE)

install_rootfs:
	rm -rf $(TARGET_ROOTFS_DIR)/*
	cd $(ROOTFS_DIR) && $(MAKE) install
	#rsync -r --exclude='.svn' $(PRJROOT)/rootfs/rootfs.overwrite/* $(TARGET_ROOTFS_DIR)

clean_rootfs:
	cd $(ROOTFS_DIR) && $(MAKE) distclean

menuconfig:
	@if [ ! -e scripts/config/mconf ] ; then \
		cd scripts/config/ && $(MAKE); \
	fi
	#@./scripts/kconfig/mconf ./scripts/Config.in
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
	@echo ""
	@echo "*** EPS Android build script ***"
	@echo ""
	@echo "Usage: make [targets]"
	@echo ""
	@echo "Available targets:"
	@echo "  all              - build all modules + whole target system"
	@echo "  menuconfig       - update current config utilising a menu based program"
	@echo "  clean            - clean all generated files + whole target system"
	@echo "  build_<module>   - build <module>" 
	@echo "  clean_<module>   - clean <module> generated files"
	@echo "  install_<module> - install <module> files"
	@echo "  rebuild_<module> - run clean_<module> and build_<module>"
	@echo "  update_<module>  - run build_<module> and install_<module>"
	@echo ""
	@echo "Module list:"
	@for i in $(modules); do\
		echo "  $$i"; \
	done
	@echo ""

.PHONY: build_version install_version clean_version
build_version:
	@if [ ! -d $(TARGET_BIN_DIR) ] ; then \
		mkdir -p $(TARGET_BIN_DIR); \
	fi
	@echo "EPS Android Build" > $(BUILT_VERSION)
	@echo "---" >> $(BUILT_VERSION)
	@echo -n "Built date: " >> $(BUILT_VERSION)
	@echo "$(shell date --rfc-3339=second)" >> $(BUILT_VERSION)
	@echo "Builder: $(USER)" >> $(BUILT_VERSION)
	@echo -n "SVN revision: " >> $(BUILT_VERSION)
	@echo "$(shell LANG=C ; svn info $(PRJROOT) | grep -i "revision" | awk '{print $$2}')" >> $(BUILT_VERSION)
	@echo "---" >> $(BUILT_VERSION)

install_version:
	@if [ ! -d $(TARGET_ROOTFS_DIR)/etc ] ; then \
		mkdir -p $(TARGET_ROOTFS_DIR)/etc; \
	fi
	cp $(BUILT_VERSION) $(TARGET_ROOTFS_DIR)/etc

clean_version:
	-rm -f $(BUILT_VERSION)

strip_rootfs:
	-find $(TARGET_ROOTFS_DIR) -type l -prune -o -name "*.ko" -prune -o -print -exec $(STRIP) {} \;
	-find $(TARGET_ROOTFS_DIR) -name "*.ko" -exec $(STRIP) -g -S -d --strip-debug {} \;

jffs2: strip_rootfs
	$(PRJROOT)/scripts/bin/mkfs.jffs2 -v -e 131072 --pad=0xf00000 -r $(TARGET_ROOTFS_DIR) -o $(TARGET_BIN_DIR)/rootfs.jffs2

yaffs2: strip_rootfs
	$(PRJROOT)/scripts/bin/mkyaffs2image $(TARGET_ROOTFS_DIR) $(TARGET_BIN_DIR)/rootfs.yaffs2
