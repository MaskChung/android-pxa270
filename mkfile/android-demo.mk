ifeq "$(ANDROID_DEMO)" "y"

DEMO_ROOTFS := $(PRJROOT)/$(call path-for,rootfs)/demo.tgz

MODULES += android_demo

.PHONY: build_android_demo install_android_demo clean_android_demo
build_android_demo:
install_android_demo:
	-rm -f $(PRJROOT)/$(call path-for,target-bin)/demo.tgz
	-rm -rf $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	mkdir -p $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	file -b $(DEMO_ROOTFS) | awk '{print $$1 " -d -c -v $(DEMO_ROOTFS)"}' | sh - | tar xvf - --strip-components=1 -C $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	chmod -R a+rwx $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
#	tar cvzf $(PRJROOT)/$(call path-for,target-bin)/demo.tgz $(PRJROOT)/$(call path-for,target-android-demo-rootfs)
	cd $(PRJROOT)/$(call path-for,target) && tar cvzf $(PRJROOT)/$(call path-for,target-bin)/demo.tgz $(patsubst $(call path-for,target)/%,%,$(call path-for,target-android-demo-rootfs))

clean_android_demo:

endif
