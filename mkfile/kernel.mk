ifdef KERNEL_SRC

KERNEL_SRC := $(PRJROOT)/$(call path-for,kernel)/$(patsubst "%",%,$(KERNEL_SRC))
KERNEL_CONF := $(PRJROOT)/$(call path-for,config)/kernel.conf

MODULES += kernel

.PHONY: build_kernel install_kernel clean_kernel
build_kernel:
	if [ ! -d $(KERNEL_SRC) ] ; then \
		echo Missing kernel source: $(KERNEL_SRC); \
		exit 1; \
	elif [ ! -e $(KERNEL_SRC)/.config ]; then \
		if [ ! -e $(KERNEL_CONF) ] ; then \
			$(MAKE) -C $(KERNEL_SRC) defconfig ARCH=$(ARCH); \
		else \
			cp $(KERNEL_CONF) $(KERNEL_SRC)/.config; \
		fi; \
	fi
	$(MAKE) -C $(KERNEL_SRC)

install_kernel:
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-bin)
	$(MAKE) -C $(KERNEL_SRC) INSTALL_MOD_PATH=$(PRJROOT)/$(call path-for,target-rootfs) modules_install 
	cp -f $(KERNEL_SRC)/arch/$(ARCH)/boot/zImage $(PRJROOT)/$(call path-for,target-bin)
	gzip -9 -f $(PRJROOT)/$(call path-for,target-bin)/zImage
	rm -f $(PRJROOT)/$(call path-for,target-bin)/uImage
	$(PRJROOT)/scripts/bin/mkimage -A arm -O linux -T kernel -C gzip -a 0xa0008000 -e 0xa0008000 -n "EPS-Android" -d $(PRJROOT)/$(call path-for,target-bin)/zImage.gz $(PRJROOT)/$(call path-for,target-bin)/uImage ### change mkimage to path-for
	rm -f $(PRJROOT)/$(call path-for,target-bin)/zImage.gz

clean_kernel:
	if [ -e $(KERNEL_SRC)/.config ]; then \
		cp -f $(KERNEL_SRC)/.config $(KERNEL_CONF); \
	fi
	$(MAKE) -C $(KERNEL_SRC) distclean

endif
