ifdef TOPDIR

obj-m	:= zlib.o

zlib.o: zlib_inflate/zlib_inflate.o zlib_deflate/zlib_deflate.o
	$(LD) -r -o $@ $<

include $(TOPDIR)/Rules.make

else

LINUXDIR=/lib/modules/$(shell uname -r)/build

zlib.o:
	make -C $(LINUXDIR) EXTRA_CFLAGS="-I$(shell pwd)/../include" SUBDIRS=$(shell pwd)/zlib_inflate modules
	make -C $(LINUXDIR) EXTRA_CFLAGS="-I$(shell pwd)/../include" SUBDIRS=$(shell pwd)/zlib_deflate modules
	make -C $(LINUXDIR) SUBDIRS=$(shell pwd) modules

clean:
	rm -f *.o */*.o


endif
