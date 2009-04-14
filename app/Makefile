
.PHONY: all strace clean

all: strace

strace:
	$(MAKE) -f Makefile.strace
#	cd strace-4.5.18 && ./configure --prefix=`pwd` --host=arm-none-linux-gnueabi && $(MAKE)

clean_strace:
	$(MAKE) -f Makefile.strace clean

clean:
#clean: clean_mkfs.jffs2 clean_mkyaffs2image
#	cd mtd/util && $(MAKE) clean
#	cd yaffs2/utils && $(MAKE) clean

