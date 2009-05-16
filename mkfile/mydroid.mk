ifdef MYDROID_SRC

MODULES += mydroid

export MYDROID_SRC := $(patsubst "%",%,$(MYDROID_SRC))
MYDROID_BIN := $(PRJROOT)/$(call path-for,mydroid)/$(MYDROID_SRC)/out/target/product/generic

.PHONY: build_mydroid install_mydroid clean_mydroid
build_mydroid:
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid)

install_mydroid:
	-rm -rf $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
ifneq "$(BASE_ROOTFS)" ""
	file -b $(BASE_ROOTFS) | awk '{print $$1 " -d -c -v $(BASE_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	rsync -r --exclude='.svn' $(PRJROOT)/$(call path-for,rootfs-overwrite)/* $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
endif
	cp -af $(MYDROID_BIN)/system $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	cp -af $(MYDROID_BIN)/data $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	cp -af $(MYDROID_BIN)/root/* $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	cd $(PRJROOT)/$(call path-for,target-mydroid-rootfs)/etc && ln -s ../system/etc/* .
	rsync -r --exclude='.svn' $(PRJROOT)/$(call path-for,rootfs-overwrite-android)/* $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	chmod -R a+rwx $(PRJROOT)/$(call path-for,target-mydroid-rootfs)

clean_mydroid:
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid) clean

endif
