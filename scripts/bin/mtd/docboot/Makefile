# $Id: Makefile,v 1.4 2005/01/03 18:31:10 dbrown Exp $

ifneq ($(KERN),)
 KERN_ARG = $(KERN)
else
 KERN_ARG = ./bzImage
endif

ifneq ($(INITRD),)
 INITRD_ARG = -i $(INITRD)
 ifneq ($(INITRD_OUT),)
  INITRD_OUT_ARG = -o $(INITRD_OUT)
 endif
endif

nonbios: doc_bootstub makespl
	cat cmdline | ./makespl doc_bootstub $(KERN_ARG) doc_spl $(INITRD_ARG) $(INITRD_OUT_ARG)

bios: doc_bootstub makespl
	cat cmdline | ./makespl doc_bootstub $(KERN_ARG) doc_spl -b bios_ext $(INITRD_ARG) $(INITRD_OUT_ARG)

doc_bootstub.o: doc_bootstub.S doc_bootstub.h Makefile
	$(CC) -O2 $(STDEFINE) -fno-builtin -nostdinc -c doc_bootstub.S

doc_bootstub.elf: doc_bootstub.o
	ld -N -Ttext 0 -o doc_bootstub.elf doc_bootstub.o

doc_bootstub: doc_bootstub.elf
	objcopy -O binary doc_bootstub.elf doc_bootstub

makespl: makespl.c doc_bootstub.elf
	cc makespl.c -o makespl -DCHECKSUM_LOCATION=0x`nm doc_bootstub.elf | grep checksum | cut -d " " -f 1`

clean:
	rm -f doc_spl *.o *.elf doc_bootstub makespl bios_ext
