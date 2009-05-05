include $(PRJROOT)/mkfile/unsetenv.mk
export PATH := $(PRJROOT)/$(call path-for,jdk)/bin:$(shell echo $$PATH)

all:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(MYDROID_SRC)
endif

clean:
ifneq "$(MYDROID_SRC)" ""
	$(MAKE) -C $(MYDROID_SRC) clean
endif
