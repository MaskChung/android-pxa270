### mkfs.jffs2
### cvs -d :pserver:anoncvs@cvs.infradead.org:/home/cvs login
### password: anoncvs
### cvs -d :pserver:anoncvs@cvs.infradead.org:/home/cvs co mtd
### cvs -d :pserver:anoncvs@cvs.infradead.org:/home/cvs logout

### mkyaffs2image
### export CVSROOT=:pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs cvs logon
### cvs checkout yaffs2
###
### cvs -d :pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs logon
### password: cvs
### cvs -d :pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs co yaffs2
### cvs -d :pserver:anonymous@cvs.aleph1.co.uk:/home/aleph1/cvs logout

.PHONY: all mkfs.jffs2 clean_mkfs.jffs2 mkyaffs2image clean_mkyaffs2image clean
.PHONY: strace

all: mkfs.jffs2 mkyaffs2image

mkfs.jffs2:
	cd mtd/util && $(MAKE)

clean_mkfs.jffs2:
	cd mtd/util && $(MAKE) clean

mkyaffs2image:
	cd yaffs2/utils && $(MAKE)

clean_mkyaffs2image:
	cd yaffs2/utils && $(MAKE) clean

strace:
	$(MAKE) -f Makefile.strace
#	cd strace-4.5.18 && ./configure --prefix=`pwd` --host=arm-none-linux-gnueabi && $(MAKE)

clean: clean_mkfs.jffs2 clean_mkyaffs2image
#	cd mtd/util && $(MAKE) clean
#	cd yaffs2/utils && $(MAKE) clean

