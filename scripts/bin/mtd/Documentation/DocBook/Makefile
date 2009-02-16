#
# $Id: Makefile,v 1.1 2004/06/01 21:10:16 gleixner Exp $
#

# Path to kernel source and html base directory
KERNELPATH ?= /usr/src/linux
HTMLPATH ?= /var/www/mtd/tech

# Dependencies for mtdnand.sgml
MTDNANDDEP = ../../include/linux/mtd/nand.h ../../drivers/mtd/nand/nand_base.c \
	../../drivers/mtd/nand/nand_bbt.c ../../drivers/mtd/nand/nand_ecc.c

# Books to build
all: mtdnand.sgml

mtdnand.sgml:	mtdnand.tmpl $(MTDNANDDEP)
	./docbook.sh $(KERNELPATH) ../../ $(HTMLPATH) mtdnand

clean:
	rm -f *.sgml
