#
# linux/drivers/nand/Makefile.24
# Makefile for obsolete kernels.
#
# $Id: Makefile.24,v 1.1 2004/07/12 16:08:17 dwmw2 Exp $

O_TARGET	:= nandlink.o
export-objs	:= nand_base.o nand_bbt.o nand_ecc.o nand_ids.o
list-multi	:= nand.o

include Makefile.common

include $(TOPDIR)/Rules.make

nand.o: $(nand-objs)
	$(LD) -r -o $@ $(nand-objs)
