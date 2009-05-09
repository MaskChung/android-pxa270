
MODULES += mydroid

ifneq "$(MYDROID_SRC)" ""
export MYDROID_SRC := $(patsubst "%",%,$(MYDROID_SRC))
$(PRJROOT)/$(call path-for,mydroid)/$(MYDROID_SRC)/out/target/product/generic
endif

.PHONY: build_mydroid install_mydroid clean_mydroid
build_mydroid:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid)
endif

install_mydroid:
ifneq "$(MYDROID_SRC)" ""
	$(PRJROOT)/$(call path-for,mydroid)/$(MYDROID_SRC)/out/target/product/generic
mydroid/cdma-import/out/target/product/generic/
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid) clean
endif

clean_mydroid:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid) clean
endif
