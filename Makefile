
#export PRJROOT:=$(PWD)
export PRJROOT:=$(shell pwd)

include $(PRJROOT)/Rules.mak
-include $(PRJROOT)/.config

#export PATH			:= $(TOOLCHAIN_DIR)/bin:$(shell echo $$PATH)
MODULES :=
hide := @
err := -
include mkfile/pathmap.mk
include mkfile/rootfs.mk
include mkfile/toolchain.mk
export PATH := $(shell find $(PRJROOT)/$(call path-for,toolchain) -maxdepth 2 -name "bin" -type d):$(shell echo $$PATH)
include mkfile/kernel.mk
include mkfile/busybox.mk
include mkfile/version.mk
include mkfile/mkfs-jffs2.mk
#ROOTFS_DIR			:= $(PRJROOT)/rootfs

FS :=
ifeq "$(JFFS2)" "y"
	FS += jffs2
endif
ifeq "$(YAFFS2)" "y"
	FS += yaffs2
endif


modules:=rootfs toolchain kernel busybox version

.PHONY: all install clean distclean menuconfig
.PHONY: jffs2 yaffs2

### should build toolchain first
all:
	$(MAKE) build
	$(MAKE) install
ifneq "$(FS)" ""
	$(MAKE) $(FS)
endif


build: $(addprefix build_,$(MODULES))

install: $(addprefix install_,$(MODULES))

clean: distclean
distclean: $(addprefix clean_, $(filter-out toolchain,$(MODULES)) toolchain)
	-rm -rf $(PRJROOT)/$(call path-for,target)
	$(MAKE) clean_menuconfig
	-rm -f .config



menuconfig:
	@if [ ! -e $(PRJROOT)/scripts/config/mconf ] ; then \
		cd $(PRJROOT)/scripts/config/ && $(MAKE); \
	fi
	#@./scripts/kconfig/mconf ./scripts/Config.in
	@$(PRJROOT)/scripts/config/mconf $(PRJROOT)/scripts/Config.in

clean_menuconfig:
	-cd $(PRJROOT)/scripts/config && $(MAKE) clean

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

### remember to strip android rootfs later, maybe it can NOT be strip
strip_rootfs:
	-find $(PRJROOT)/$(call path-for,target-rootfs) -type l -prune -o -name "*.ko" -prune -o -print -exec $(STRIP) {} \;
	-find $(PRJROOT)/$(call path-for,target-rootfs) -name "*.ko" -exec $(STRIP) -g -S -d --strip-debug {} \;


jffs2: strip_rootfs build_mkfs_jffs2
	$(PRJROOT)/$(call path-for,mkfs-jffs2)/mkfs.jffs2 -v -e 131072 --pad=0x500000 -r $(PRJROOT)/$(call path-for,target-rootfs) -o $(PRJROOT)/$(call path-for,target-bin)/rootfs.jffs2


yaffs2: strip_rootfs build_mkyaffs2image
	$(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image $(PRJROOT)/$(call path-for,target-rootfs) $(PRJROOT)/$(call path-for,target-bin)/rootfs.yaffs2

#strace:
#	cd app && $(MAKE) strace
#	#cd $(PRJROOT)/scripts/bin && $(MAKE) strace
