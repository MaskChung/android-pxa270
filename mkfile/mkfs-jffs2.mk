
MODULES += mkfs_jffs2

.PHONY: build_mkfs_jffs2 install_mkfs_jffs2 clean_mkfs_jffs2
build_mkfs_jffs2:
	if [ ! -d $(PRJROOT)/$(call path-for,mkfs-jffs2) ] ; then \
		echo Missing mkfs.jffs2 source: $(PRJROOT)/$(call path-for,mkfs-jffs2); \
		exit 1; \
	fi
	$(MAKE) clean_mkfs_jffs2
	$(MAKE) -C $(PRJROOT)/$(call path-for,mkfs-jffs2)
	if [ ! -x $(PRJROOT)/$(call path-for,mkfs-jffs2)/mkfs.jffs2 ] ; then \
		echo Generating mkfs.jffs2 FAIL: $(PRJROOT)/$(call path-for,mkfs-jffs2)/mkfs.jffs2; \
		exit 1; \
	fi

install_mkfs_jffs2:
clean_mkfs_jffs2:
	if [ ! -d $(PRJROOT)/$(call path-for,mkfs-jffs2) ] ; then \
		echo Missing mkfs.jffs2 source: $(PRJROOT)/$(call path-for,mkfs-jffs2); \
		exit 1; \
	fi
	$(MAKE) -C $(PRJROOT)/$(call path-for,mkfs-jffs2) clean
