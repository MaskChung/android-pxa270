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
	cp -af $(PRJROOT)/$(call path-for,target-rootfs) $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
endif
	cp -af $(MYDROID_BIN)/system $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	cp -af $(MYDROID_BIN)/data $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	cp -af $(MYDROID_BIN)/root/* $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	cd $(PRJROOT)/$(call path-for,target-mydroid-rootfs)/etc && ln -s ../system/etc/* .
	rsync -r --exclude='.svn' $(PRJROOT)/$(call path-for,rootfs-overwrite-android)/* $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	chmod -R a+rwx $(PRJROOT)/$(call path-for,target-mydroid-rootfs)
	tar cvzf $(PRJROOT)/$(call path-for,target-bin)/mydroid-rootfs.tgz $(PRJROOT)/$(call path-for,target-mydroid-rootfs)

clean_mydroid:
	$(MAKE) -C $(PRJROOT)/$(call path-for,mydroid) clean

endif
