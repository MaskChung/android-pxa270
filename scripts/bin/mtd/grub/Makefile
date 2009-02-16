# Default to using the new FSF grub
STAGE2FILE := pre_stage2
# Comment out the above, and uncomment the following two lines, if you
# are using Erich Boleyn's old grub (<= 0.5)
#STDEFINE := -DOLDGRUB
#STAGE2FILE := stage2


grub_firmware: doc_stage1 makecsum pre_stage2 Makefile
	./makecsum doc_stage1 $(STAGE2FILE) grub_firmware

doc_stage1.exec: doc_stage1.S doc_stage1.h stage2_size.h Makefile
	$(CC) -O2 $(STDEFINE) -fno-builtin -nostdinc -nostdlib -Wl,-N,-Ttext,0 -o doc_stage1.exec doc_stage1.S

doc_stage1b-256.o: doc_stage1b.S doc_stage1.h stage2_size.h Makefile
	$(CC) -o doc_stage1b-256.o -O2 $(STDEFINE) -DPAGE256 -fno-builtin -nostdinc -c doc_stage1b.S

doc_stage1b-512.o: doc_stage1b.S doc_stage1.h stage2_size.h Makefile
	$(CC) -o doc_stage1b-512.o -O2 $(STDEFINE) -DPAGE512 -fno-builtin -nostdinc -c doc_stage1b.S

doc_stage1.o: doc_stage1.S doc_stage1.h stage2_size.h Makefile
	$(CC) -O2 $(STDEFINE) -fno-builtin -nostdinc -c doc_stage1.S

doc_stage1-256.o: doc_stage1.o doc_stage1b-256.o
	ld -N -Ttext 0 -o doc_stage1-256.o doc_stage1.o doc_stage1b-256.o

doc_stage1-512.o: doc_stage1.o doc_stage1b-512.o
	ld -N -Ttext 0 -o doc_stage1-512.o doc_stage1.o doc_stage1b-512.o

doc_stage1-256.bin: doc_stage1-256.o
	objcopy -O binary doc_stage1-256.o doc_stage1-256.bin

doc_stage1-512.bin: doc_stage1-512.o
	objcopy -O binary doc_stage1-512.o doc_stage1-512.bin

doc_stage1: doc_stage1-256.bin doc_stage1-512.bin
	cp -f doc_stage1-256.bin doc_stage1
	dd bs=256 if=doc_stage1-512.bin skip=1 seek=2 count=1 of=doc_stage1

stage2_size.h: $(STAGE2FILE)
	-rm -f stage2_size.h
	set dummy `ls -l $(STAGE2FILE)`; \
	echo "#define STAGE2_SIZE $$6" > stage2_size.h

clean:
	rm -f doc_stage1 *.o *.bin *~ grub_firmware doc_stage1.exec makecsum stage2_size.h
