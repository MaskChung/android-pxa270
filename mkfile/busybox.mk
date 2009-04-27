
BUSYBOX_SRC := $(PRJROOT)/$(call path-for,busybox)/$(patsubst "%",%,$(BUSYBOX_SRC))

MODULES += busybox

.PHONY: build_busybox install_busybox clean_busybox
build_busybox:
	if [ ! -d $(BUSYBOX_SRC) ] ; then \
		echo Missing busybox: $(BUSYBOX_SRC); \
		exit 1; \
	elif [ ! -e $(BUSYBOX_SRC)/.config ] ; then \
		echo please svn up $(BUSYBOX_SRC)/.config first; \
		exit 1; \
	fi
	$(MAKE) -C $(BUSYBOX_SRC)

install_busybox:
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-bin)
	$(MAKE) -C $(BUSYBOX_SRC) CONFIG_PREFIX=$(PRJROOT)/$(call path-for,target-rootfs) install
	if [ ! -e $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox ] ; then \
		echo $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox was not be installed; \
		exit 1; \
	else \
		chmod u+s $(PRJROOT)/$(call path-for,target-rootfs)/bin/busybox; \
	fi

clean_busybox:
	$(MAKE) -C $(BUSYBOX_SRC) distclean

