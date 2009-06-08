
export PRJROOT:=$(shell pwd)

-include $(PRJROOT)/.config

export PATH = $(shell find $(PRJROOT)/$(call path-for,toolchain) -maxdepth 2 -name "bin" -type d):$(shell echo $$PATH)

MODULES :=
hide := @
err := -

all:

include mkfile/pathmap.mk
include $(call path-for,mkfile)/setenv.mk
include $(call path-for,mkfile)/rules.mk
include $(call path-for,mkfile)/toolchain.mk
include $(call path-for,mkfile)/rootfs.mk
include $(call path-for,mkfile)/kernel.mk
include $(call path-for,mkfile)/busybox.mk
include $(call path-for,mkfile)/version.mk
include $(call path-for,mkfile)/mkfs-jffs2.mk
include $(call path-for,mkfile)/mydroid.mk
include $(call path-for,mkfile)/android-demo.mk

FS :=
ifeq "$(JFFS2)" "y"
	FS += jffs2
endif
ifeq "$(YAFFS2)" "y"
	FS += yaffs2
endif

TOP_CONF := $(PRJROOT)/$(call path-for,config)/top.conf

.PHONY: all install clean distclean menuconfig help
.PHONY: jffs2 yaffs2

all:
	if [ ! -e $(PRJROOT)/.config ] ; then \
		cp $(TOP_CONF) $(PRJROOT)/.config; \
	fi
ifneq "$(words $(CROSS_COMPILE))" "1"
	$(MAKE) clean_toolchain
endif
	$(MAKE) build_toolchain
	$(MAKE) $(addprefix build_,$(MODULES))
	$(MAKE) install
ifneq "$(FS)" ""
	$(MAKE) $(FS)
endif

install:
	for i in $(MODULES); do \
		$(MAKE) $(addprefix install_,$$i); \
	done

clean: distclean
distclean: $(addprefix clean_, $(filter-out toolchain,$(MODULES)))
	$(MAKE) clean_toolchain
	-rm -rf $(PRJROOT)/$(call path-for,target)
	$(MAKE) clean_mconf
	-rm -f .config

menuconfig:
	if [ ! -d $(PRJROOT)/$(call path-for,mconf) ] ; then \
		echo Missing mconf: $(PRJROOT)/$(call path-for,mconf); \
		exit 1; \
	else \
		$(MAKE) -C $(PRJROOT)/$(call path-for,mconf); \
		if [ ! -x $(PRJROOT)/$(call path-for,mconf)/mconf ] ; then \
			echo Missing mconf: $(PRJROOT)/$(call path-for,mconf)/mconf; \
			exit 1; \
		fi; \
	fi
	$(PRJROOT)/$(call path-for,mconf)/mconf $(PRJROOT)/$(call path-for,mconf-conf-in)/Config.in

clean_mconf:
	$(MAKE) -C $(PRJROOT)/$(call path-for,mconf) clean

# compound rules
rebuild_%:
	$(MAKE) clean_$*
	$(MAKE) build_$*

update_%:
	$(MAKE) build_$*
	$(MAKE) install_$*

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
	@for i in $(MODULES); do\
		echo "  $$i"; \
	done
	@echo ""

### remember to strip android rootfs later, maybe it can NOT be strip
strip_rootfs:
	-find $(PRJROOT)/$(call path-for,target-rootfs) -type l -prune -o -name "*.ko" -prune -o -print -exec $(STRIP) {} \;
	-find $(PRJROOT)/$(call path-for,target-rootfs) -name "*.ko" -exec $(STRIP) -g -S -d --strip-debug {} \;


jffs2: strip_rootfs
	if [ ! -x $(PRJROOT)/$(call path-for,mkfs-jffs2)/mkfs.jffs2 ] ; then \
		echo Missing mkfs.jffs2: $(PRJROOT)/$(call path-for,mkfs-jffs2)/mkfs.jffs2; \
		exit 1; \
	fi
	$(PRJROOT)/$(call path-for,mkfs-jffs2)/mkfs.jffs2 -v -e 131072 --pad=0x500000 -r $(PRJROOT)/$(call path-for,target-rootfs) -o $(PRJROOT)/$(call path-for,target-bin)/rootfs.jffs2


yaffs2: strip_rootfs
	if [ ! -x $(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image ] ; then \
		echo Missing mkyaffs2image: $(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image; \
		exit 1; \
	fi
	$(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image $(PRJROOT)/$(call path-for,target-rootfs) $(PRJROOT)/$(call path-for,target-bin)/rootfs.yaffs2

