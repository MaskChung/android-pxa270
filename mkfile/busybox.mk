
BUSYBOX_SRC := $(PRJROOT)/$(call path-for,busybox)/$(patsubst "%",%,$(BUSYBOX_SRC))
BUSYBOX_CONF := $(PRJROOT)/$(call path-for,config)/busybox.conf

MODULES += busybox

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	if [ ! -d $(BUSYBOX_SRC) ] ; then \
		echo Missing busybox: $(BUSYBOX_SRC); \
		exit 1; \
	elif [ ! -e $(BUSYBOX_SRC)/.config ] ; then \
		if [ ! -e $(BUSYBOX_CONF) ] ; then \
		echo please svn up $(BUSYBOX_SRC)/.config first; \
		exit 1; \
		else \
			cp $(BUSYBOX_CONF) $(BUSYBOX_SRC)/.config; \
			$(MAKE) -C $(BUSYBOX_SRC) silentoldconfig; \
		fi; \
	fi
	$(MAKE) -C $(BUSYBOX_SRC)

install_busybox:
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-bin)
	$(MAKE) -C $(BUSYBOX_SRC) CONFIG_PREFIX=$(PRJROOT)/$(call path-for,target-rootfs) install
	if [ ! -x $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox ] ; then \
		echo $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox was not be installed; \
		exit 1; \
	else \
		chmod u+s $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox; \
	fi

clean_busybox:
	$(MAKE) -C $(BUSYBOX_SRC) distclean

