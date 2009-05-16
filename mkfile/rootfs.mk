ifdef BASE_ROOTFS

BASE_ROOTFS := $(PRJROOT)/$(call path-for,rootfs)/$(patsubst "%",%,$(BASE_ROOTFS))

MODULES += rootfs

.PHONY: build_rootfs install_rootfs clean_rootfs
build_rootfs:
install_rootfs:
	rm -rf $(PRJROOT)/$(call path-for,target-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)
	file -b $(BASE_ROOTFS) | awk '{print $$1 " -d -c -v $(BASE_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(PRJROOT)/$(call path-for,target-rootfs)
	rsync -r --exclude='.svn' $(PRJROOT)/$(call path-for,rootfs-overwrite)/* $(PRJROOT)/$(call path-for,target-rootfs)

clean_rootfs:

endif
