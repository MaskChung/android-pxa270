
MODULES += mkyaffs2image

.PHONY: build_mkyaffs2image install_mkyaffs2image clean_mkyaffs2image
build_mkyaffs2image:
	if [ ! -d $(PRJROOT)/$(call path-for,mkyaffs2image) ] ; then \
		echo Missing mkyaffs2image source: $(PRJROOT)/$(call path-for,mkyaffs2image); \
		exit 1; \
	fi
	$(MAKE) clean_mkyaffs2image
	$(MAKE) -C $(PRJROOT)/$(call path-for,mkyaffs2image)
	if [ ! -x $(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image ] ; then \
		echo Generating mkyaffs2image FAIL: $(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image; \
		exit 1; \
	fi

install_mkyaffs2image:
clean_mkyaffs2image:
	if [ ! -d $(PRJROOT)/$(call path-for,mkyaffs2image) ] ; then \
		echo Missing mkyaffs2image source: $(PRJROOT)/$(call path-for,mkyaffs2image); \
		exit 1; \
	fi
	$(MAKE) -C $(PRJROOT)/$(call path-for,mkyaffs2image) clean

