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

.PHONY: all clean mkfs.jffs2 mkyaffs2image
all: mkfs.jffs2 mkyaffs2image

clean:
	cd mtd/util && $(MAKE) clean
	cd yaffs2/utils && $(MAKE) clean

mkfs.jffs2:
	cd mtd/util && $(MAKE)

mkyaffs2image:
	cd yaffs2/utils && $(MAKE)
