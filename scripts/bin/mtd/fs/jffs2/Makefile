#
# Out of tree fs/jffs2/Makefile
#
# $Id: Makefile,v 1.43 2003/10/06 12:54:49 dwmw2 Exp $

ifdef OUT_OF_TREE_BUILD
include $(mtd)/defconfig

# This must be first in the include path, so it goes in $(CC) rather
# then $(EXTRA_CFLAGS)

CC += -I$(mtd)/../../include
EXTRA_CFLAGS := -g -Werror

ifndef CONFIG_MTD
EXTRA_CFLAGS += -DMTD_OUT_OF_TREE
endif

ifdef NONAND
EXTRA_CFLAGS += -DNONAND
endif
endif
include $(obj)/Makefile.common
