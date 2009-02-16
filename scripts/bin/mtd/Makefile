# $Id: Makefile,v 1.15 2005/11/07 11:14:16 gleixner Exp $
#

TARGETS = jffs kernel
#TARGETS = ffs2 kernel
SUBDIRS = util # boot
LINUXDIR = /usr/src/linux-arm-2.4-ds
all:
	make -C $(LINUXDIR) SUBDIRS=$(shell pwd)/kernel modules
	for a in $(SUBDIRS); do \
		make -C $$a; \
	done

jffs:
	make -C $(LINUXDIR) SUBDIRS=$(shell pwd)/fs/jffs modules

jffs2:
	make -C $(LINUXDIR) SUBDIRS=$(shell pwd)/fs/jffs2 modules

ffs2:
	make -C $(LINUXDIR) SUBDIRS=$(shell pwd)/fs/ffs2 modules

util:
	make -C util

boot:
	make -C boot

clean:
	find kernel/ -name '*.[oa]' -type f | xargs rm -f
	find fs/ -name '*.[oa]' -type f | xargs rm -f
	make -C util clean
	make -C boot clean
