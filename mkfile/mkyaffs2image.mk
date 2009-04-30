
### mkyaffs2image
### export CVSROOT=:pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs cvs logon
### cvs checkout yaffs2
###
### cvs -d :pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs logon
### password: cvs
### cvs -d :pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs co yaffs2
### cvs -d :pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs logout

MODULES += mkyaffs2image

.PHONY: build_mkyaffs2image install_mkyaffs2image clean_mkyaffs2image
build_mkyaffs2image:
	if [ ! -d $(PRJROOT)/$(call path-for,mkyaffs2image) ] ; then \
		echo Missing mkyaffs2image source: $(PRJROOT)/$(call path-for,mkyaffs2image); \
		exit 1; \
	elif [ ! -x $(PRJROOT)/$(call path-for,mkyaffs2image)/mkyaffs2image ] ; then \
		$(MAKE) -C $(PRJROOT)/$(call path-for,mkyaffs2image); \
	fi
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

