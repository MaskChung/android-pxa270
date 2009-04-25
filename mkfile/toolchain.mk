
###TOOLCHAIN_DIR			:= $(PRJROOT)/scripts/toolchain
###TOOLCHAIN_DIR			:= $(PRJROOT)/$(call path-for,toolchain)
TOOLCHAIN := $(PRJROOT)/$(call path-for,toolchain)/$(patsubst "%",%,$(TOOLCHAIN))
#export PATH			:= $(TOOLCHAIN_DIR)/bin:$(shell echo $$PATH)

#.PHONY: build_toolchain install_toolchain clean_toolchain
#build_toolchain:
#	@if [ ! -e $(TOOLCHAIN_DIR)/bin ] ; then \
#		file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - --strip-components=1 -C $(TOOLCHAIN_DIR); \
#	fi
#
#install_toolchain:
#clean_toolchain:
#	-find $(TOOLCHAIN_DIR)/* -maxdepth 0 -type d -exec rm -rf {} \;

.PHONY: build_toolchain install_toolchain clean_toolchain
build_toolchain: clean_toolchain
	file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - -C $(call path-for,toolchain);
	###file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - --strip-components=1 -C $(call path-for,toolchain);
#	fi

install_toolchain:
clean_toolchain:
	find $(call path-for,toolchain)/* -maxdepth 0 -type d -exec rm -rf {} \;
	#-find $(TOOLCHAIN_DIR)/* -maxdepth 0 -type d -exec rm -rf {} \;

