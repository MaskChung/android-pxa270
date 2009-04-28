
BUILT_VERSION := $(PRJROOT)/$(call path-for,target-bin)/built_version

MODULES += version

.PHONY: build_version install_version clean_version
build_version:
install_version:
	mkdir -p $(PRJROOT)/$(call path-for,target-bin)
	@echo "EPS Android Build" > $(BUILT_VERSION)
	@echo "---" >> $(BUILT_VERSION)
	@echo -n "Built date: " >> $(BUILT_VERSION)
	@echo "$(shell date --rfc-3339=second)" >> $(BUILT_VERSION)
	@echo "Builder: $(USER)" >> $(BUILT_VERSION)
	@echo -n "SVN revision: " >> $(BUILT_VERSION)
	@echo "$(shell LANG=C ; svn info $(PRJROOT) | grep -i "revision" | awk '{print $$2}')" >> $(BUILT_VERSION)
	@echo "---" >> $(BUILT_VERSION)
	mkdir -p $(PRJROOT)/$(call path-for,target-rootfs)/etc
	cp $(BUILT_VERSION) $(PRJROOT)/$(call path-for,target-rootfs)/etc/

clean_version:
	-rm -f $(BUILT_VERSION)

