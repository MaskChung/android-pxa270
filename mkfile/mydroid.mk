
MODULES += mydroid

.PHONY: build_mydroid install_mydroid clean_mydroid
ifdef MYDROID
MYDROID_SRC := $(patsubst "%",%,$(MYDROID_SRC))
ifneq "$(MYDROID_SRC)" ""
export MYDROID_SRC
build_mydroid:
	echo $(MYDROID)
	echo $(MYDROID_SRC)
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid)
endif
endif

install_mydroid:
clean_mydroid:
