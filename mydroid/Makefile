
include $(PRJROOT)/mkfile/pathmap.mk
include $(PRJROOT)/$(call path-for,mkfile)/unsetenv.mk

define x86_64
$(if $(findstring 64,$(shell uname -m)),true,false)
endef

ifeq (true,$(call x86_64))
export PATH := $(PRJROOT)/$(call path-for,jdk-64)/bin:$(shell echo $$PATH)
else
export PATH := $(PRJROOT)/$(call path-for,jdk)/bin:$(shell echo $$PATH)
endif

all:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(MYDROID_SRC)
endif

clean:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(MYDROID_SRC) clean
endif
