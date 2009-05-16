
DEMO_ROOTFS := $(PRJROOT)/$(call path-for,rootfs)/demo.tgz

MODULES += android_demo

.PHONY: build_android_demo install_android_demo clean_android_demo
build_android_demo:
install_android_demo:
ifeq "$(ANDROID_DEMO)" "y"
	rm -rf $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	file -b $(DEMO_ROOTFS) | awk '{print $$1 " -d -c -v $(DEMO_ROOTFS)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	chmod -R a+rwx $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
endif

clean_android_demo:
