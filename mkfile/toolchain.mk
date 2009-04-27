
TOOLCHAIN := $(PRJROOT)/$(call path-for,toolchain)/$(patsubst "%",%,$(TOOLCHAIN))

MODULES += toolchain

OLD_MD5 := $(PRJROOT)/$(call path-for,toolchain)/md5

checksum = 199
new =
old =

true := 1
false := 0

.PHONY: build_toolchain install_toolchain clean_toolchain check_toolchain
#build_toolchain: clean_toolchain
#	if [ -e $(PRJROOT)/$(call path-for,toolchain)/$(MD5SUM) ] ; then \
#		NEW=`md5sum $(TOOLCHAIN) | awk '{print $$1}'`; \
#	fi
#	file -b $(TOOLCHAIN) | awk '{print $$1 " -d -c -v $(TOOLCHAIN)"}' | sh - | tar xvf - -C $(PRJROOT)/$(call path-for,toolchain)
#
#install_toolchain:
#clean_toolchain:
##	find $(PRJROOT)/$(call path-for,toolchain)/* -maxdepth 0 -type d -exec rm -rf {} \;
#
#check_toolchain:
#	if [ $(call unzip-jar-files,1,2) ] ; then \
#		echo "0"; \
#	fi
#
#hihi:
#	$(call yaya)

hihi:
	@new=`md5sum $(TOOLCHAIN) | awk '{print $$1}'`; \
	old=`cat $(OLD_MD5)`; \
	echo -- "$$new"; \
	echo -- $$old; \
	if [ 1 ] ; then \
		echo shit; \
	fi; \
	if [ "$$new" != "$$old" ] ; then \
		echo $$new; \
		echo $$old; \
		echo 2; \
	else \
		echo $$new; \
		echo $$old; \
		echo 3; \
	fi

.PHONY: if_same_toolchain
if_same_toolchain:
	@if [ ! -e $(TOOLCHAIN) ] ; then \
		echo Missing toolchain $(TOOLCHAIN); \
		exit 1; \
	elif [ ! -e $(OLD_MD5) ] ; then \
		$(checksum)="false"; \
	else \
	$(checksum)="flse"; \
	tt=$(checksum); \
	echo "$$tt"; \
	new=`md5sum $(TOOLCHAIN) | awk '{print $$1}'`; \
	old=`cat $(OLD_MD5)`; \
	echo $$new; \
	echo $$old; \
	fi; \
	if [ "$$new" != "$$old" ] ; then \
		echo oooxx; \
		check=0; \
		echo aabb; \
	else \
		$(checksum)=true; \
	fi; \
	checksum="$(checksum)"; \
	echo $(checksum)


#define if-same-toolchain
#$(if $(0), \
#	new=`md5sum $(TOOLCHAIN) | awk '{print $$1}'`; \
#	old=`cat $(OLD_MD5)`; \
#	echo $$new; \
#	echo $$old; \
#	if [ "$$new" != "$$old" ] ; then \
#		exit 1; \
#	else \
#		exit 0; \
#	fi \
#	, \
#	true
#)
#endef

#define if-same-toolchain
#	new=`md5sum $(TOOLCHAIN) | awk '{print $$1}'`; \
#	old=`cat $(OLD_MD5)`; \
#	echo $$new; \
#	echo $$old; \
#	if [ "$$new" != "$$old" ] ; then \
#		false; \
#	fi
#endef

#define unzip-jar-files
#    @if [ 1 ]; then \
#      echo Missing file shit; \
#      exit 0; \
#    fi
#endef


