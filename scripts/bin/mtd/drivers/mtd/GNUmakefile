# $Id: GNUmakefile,v 1.16 2003/05/28 10:00:43 dwmw2 Exp $

LINUXDIR=/lib/modules/$(shell uname -r)/build

ifndef VERSION

# Someone just typed 'make'. Invoke the kernel makefiles properly...

modules:
	make -C $(LINUXDIR) SUBDIRS=`pwd` mtd=`pwd` modules

simplemap:
	make -C $(LINUXDIR) SUBDIRS=`pwd` mtd=`pwd` SIMPLEMAP=1 modules

dep:
	make -C $(LINUXDIR) SUBDIRS=`pwd` mtd=`pwd` dep

clean:
	rm -f `find . -name \*.o -o -name \*.mod.c -o -name \*.ko`

veryclean:
	for a in `find . -type f  -a \! -path \*/CVS/\*` ; do  \
		grep -q ^/`basename $$a`/ `dirname $$a`/CVS/Entries ||  \
			rm -v $$a ; \
	done 
else

# 2.5 explicitly states <subdir>/Makefile rather than using recursive make,
# so we handle all that in there...

obj := $(shell pwd)
include Makefile

endif
