
export PRJROOT:=$(PWD)

include $(PRJROOT)/Rules.mak
-include $(PRJROOT)/.config

export PATH			:= $(ARM_TOOLCHAIN):$(shell echo $$PATH)
#export PATH			:= $(ARM_TOOLCHAIN):$$PATH
#export ARCH			:= arm
#export CROSS_COMPILE		:= arm-none-linux-gnueabi-
export CONFIG_DIR		:= $(PRJROOT)/config
#KERNEL_SRC_DIR			:= $(subst "\"",,$(KERNEL_SRC))
export KERNEL_SRC_DIR			:= $(patsubst "%",%,$(KERNEL_SRC))
#KERNEL_SRC_DIR			:= $(subst \",,$(KERNEL_SRC))
export KERNEL_SRC_DIR		:= $(PRJROOT)/$(KERNEL_SRC_DIR)
#export KERNEL_SRC_DIR		:= $(PRJROOT)/$(subst\",,$(KERNEL_SRC))
#export KERNEL_SRC_DIR		:= $(PRJROOT)/$(patsubst\",,$(KERNEL_SRC))
#export KERNEL_SRC_DIR		:= $(PRJROOT)/$(subset\",,$(KERNEL_SRC))
#export KERNEL_SRC_DIR		:= $(PRJROOT)/`echo -e -n $(KERNEL_SRC)`
#export KERNEL_SRC		:= $(PRJROOT)/linux-2.6.25-android-1.0_r1
export ROOTFS_DIR		:= $(PRJROOT)/rootfs
export BUSYBOX_SRC_DIR		:= $(PRJROOT)/$(BUSYBOX_SRC)
#export BUSYBOX_SRC		:= $(PRJROOT)/busybox-1.13.2
export TARGET_DIR		:= $(PRJROOT)/target
#export UTILS_DIR		:= $(PRJROOT)/utils

export TARGET_ROOTFS_DIR	:= $(TARGET_DIR)/rootfs
#export TARGET_MOD_DIR		:= $(TARGET_ROOTFS_DIR)/lib/modules/2.6.25
#include config/setenv.mk
export TFTP_DIR			:=/home/tftp

modules:=kernel busybox rootfs

.PHONY: all ckeck_dir build_all install_all clean_all distclean menuconfig
all: check_dir
	$(MAKE) build_all
	$(MAKE) install_all

check_dir:
	@test -d $(TARGET_ROOTFS_DIR) || mkdir -p $(TARGET_ROOTFS_DIR)

build_all: $(addprefix build_,$(modules))

install_all: $(addprefix install_,$(modules))

clean_all: $(addprefix clean_,$(modules))

distclean:
	rm -rf $(TARGET_ROOTFS_DIR)
	$(MAKE) clean_all

.PHONY: build_kernel install_kernel clean_kernel
build_kernel:
	if [ ! -e $(KERNEL_SRC_DIR)/.config ]; then \
		cd $(KERNEL_SRC_DIR) && $(MAKE) defconfig; \
	fi
	echo '${PATH}'
	cd $(KERNEL_SRC_DIR) && $(MAKE)

install_kernel:
	cd $(KERNEL_SRC_DIR) && $(MAKE) INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR) modules_install 
	#cd $(KERNEL_SRC_DIR) && $(MAKE) ARCH=arm modules_install INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR)
	#cp -f $(KERNEL_SRC_DIR)/arch/arm/boot/zImage $(TARGET_DIR)
	### mkimage
	#gzip -9 $(TARGET_DIR)/zImage
	#$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "Creator-Android" -d zImage.gz uImage
	#cp $(TARGET_DIR)/uImage $(TFTP_DIR)

clean_kernel:
	cd $(KERNEL_SRC_DIR) && $(MAKE) distclean
	#cd $(KERNEL_SRC_DIR) && $(MAKE) distclean ARCH=arm

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	if [ ! -e $(BUSYBOX_SRC_DIR)/.config ] ; then \
		cp config/busybox_config $(BUSYBOX_SRC_DIR)/.config; \
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
	#cd $(ROOTFS_DIR) && fakeroot $(MAKE)

install_rootfs:
	cd $(ROOTFS_DIR) && fakeroot $(MAKE) install
	#cd $(ROOTFS_DIR) && $(MAKE) install

clean_rootfs:
	rm -rf $(TARGET_ROOTFS_DIR)
	cd $(ROOTFS_DIR) && $(MAKE) clean

menuconfig:
	if [ ! -e scripts/config/mconf ] ; then \
		cd scripts/config/ && $(MAKE); \
	fi
	./scripts/config/mconf ./scripts/Config.in
