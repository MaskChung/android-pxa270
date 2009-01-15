export PRJROOT:=$(PWD)

export ARCH			:= arm
export CROSS_COMPILE		:= arm-none-linux-gnueabi-
export CONFIG_DIR		:= $(PRJROOT)/config
export KERNEL_SRC		:= $(PRJROOT)/linux-2.6.25-android-1.0_r1
export ROOTFS_DIR		:= $(PRJROOT)/rootfs
export BUSYBOX_SRC		:= $(PRJROOT)/busybox-1.13.2
export TARGET_DIR		:= $(PRJROOT)/target
#export UTILS_DIR		:= $(PRJROOT)/utils

export TARGET_ROOTFS_DIR	:= $(TARGET_DIR)/rootfs
#export TARGET_MOD_DIR		:= $(TARGET_ROOTFS_DIR)/lib/modules/2.6.25
#include config/setenv.mk

modules:=kernel busybox rootfs

.PHONY: all ckeck_dir build_all install_all clean_all distclean
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
	@if [ ! -e $(KERNEL_SRC)/.config ] ; then \
		cd $(KERNEL_SRC) && $(MAKE) defconfig; \
		#cd $(KERNEL_SRC) && $(MAKE) ARCH=arm defconfig;
	fi
	cd $(KERNEL_SRC) && $(MAKE)

install_kernel:
	cd $(KERNEL_SRC) && $(MAKE) INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR) modules_install 
	#cd $(KERNEL_SRC) && $(MAKE) ARCH=arm modules_install INSTALL_MOD_PATH=$(TARGET_ROOTFS_DIR)
	cp $(KERNEL_SRC)/arch/arm/boot/zImage $(TARGET_IMAGE_DIR)
	### mkimage
	#$(PRJROOT)/scripts/mkimage

clean_kernel:
	cd $(KERNEL_SRC) && $(MAKE) distclean
	#cd $(KERNEL_SRC) && $(MAKE) distclean ARCH=arm

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	@if [ ! -e $(BUSYBOX_SRC)/.config ] ; then \
		cp config/busybox_config $(BUSYBOX_SRC)/.config; \
		cd $(BUSYBOX_SRC) && $(MAKE) oldconfig; \
	fi
	cd $(BUSYBOX_SRC) && $(MAKE)

install_busybox:
	cd $(BUSYBOX_SRC) && $(MAKE) install

clean_busybox:
	cd $(BUSYBOX_SRC) && $(MAKE) distclean

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
