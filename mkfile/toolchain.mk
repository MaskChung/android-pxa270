ifdef TOOLCHAIN

TOOLCHAIN := $(PRJROOT)/$(call path-for,toolchain)/$(patsubst "%",%,$(TOOLCHAIN))

MODULES += toolchain

OLD_CHKSUM := $(PRJROOT)/$(call path-for,toolchain)/.old_chksum

.PHONY: build_toolchain install_toolchain clean_toolchain
install_toolchain:
clean_toolchain:
	find $(PRJROOT)/$(call path-for,toolchain)/* -maxdepth 0 -type d -exec rm -rf {} \;
	rm -f $(OLD_CHKSUM)

build_toolchain:
	@if [ ! -e $(TOOLCHAIN) ] ; then \
		echo Missing toolchain: $(TOOLCHAIN); \
		exit 1; \
	fi; \
	new=`md5sum $(TOOLCHAIN) | awk '{print $$1}'`; \
	if [ -e $(OLD_CHKSUM) ] ; then \
		old=`cat $(OLD_CHKSUM)`; \
		if [ "$$new" != "$$old" ] ; then \
			$(MAKE) clean_toolchain; \
			file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,toolchain); \
			echo "$$new" > $(OLD_CHKSUM); \
		else \
			exist=`find $(PRJROOT)/$(call path-for,toolchain) -maxdepth 2 -name "bin" -type d`; \
			if [ "$$exist" == "" ] ; then \
				file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,toolchain); \
			fi; \
		fi; \
	else \
		file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,toolchain); \
		echo "$$new" > $(OLD_CHKSUM); \
	fi

endif
