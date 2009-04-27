
BUSYBOX_SRC := $(PRJROOT)/$(call path-for,busybox)/$(patsubst "%",%,$(BUSYBOX_SRC))

MODULES += busybox

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	if [ ! -d $(BUSYBOX_SRC) ] ; then \
		$(error $(BUSYBOX_SRC) was not found); \
	else [ ! -e $(BUSYBOX_SRC)/.config ] ; then \
		$(error please "svn up $(BUSYBOX_SRC)/.config" first); \
	fi
	$(MAKE) -C $(BUSYBOX_SRC)

install_busybox:
	$(MAKE) -C $(BUSYBOX_SRC) install
	if [ ! -e $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox ] ; then \
		$(error $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox was not be installed); \
	else \
		chmod u+s $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox; \
	fi

#build_busybox:
#	@$(MAKE) check_dir
#	@if [ ! -e $(BUSYBOX_SRC)/.config ] ; then \
#		cp $(CONFIG_DIR)/busybox_config $(BUSYBOX_SRC)/.config; \
#		cd $(BUSYBOX_SRC) && $(MAKE) oldconfig; \
#	fi
#	cd $(BUSYBOX_SRC) && $(MAKE)

#install_busybox:
#	cd $(BUSYBOX_SRC) && $(MAKE) install
#	#@if [ -e $(TARGET_ROOTFS_DIR)/bin/busybox ] ; then
#	if [ ! -e $(call path-for,target-rootfs)/bin/busybox ] ; then \
#		$(error $(call path-for,target-rootfs)/bin/busybox not be installed); \
#	else
#		chmod u+s $(call path-for,target-rootfs)/bin/busybox; \
#	fi

clean_busybox:
	$(MAKE) -C $(BUSYBOX_SRC) distclean
#	cd $(BUSYBOX_SRC) && $(MAKE) distclean


