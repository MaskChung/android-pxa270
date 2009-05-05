
MODULES += mydroid

ifneq "$(MYDROID_SRC)" ""
export MYDROID_SRC := $(patsubst "%",%,$(MYDROID_SRC))
endif

.PHONY: build_mydroid install_mydroid clean_mydroid
build_mydroid:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid)
endif

install_mydroid:
clean_mydroid:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid) clean
endif
