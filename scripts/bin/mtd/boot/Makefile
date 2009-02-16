# -*- make -*-
CC = gcc
AS86 = as86
LOADADDRESS=0x98000
CFLAGS = -O2 -fstrength-reduce -fomit-frame-pointer -m386 -Wall
CPPFLAGS = -DRELOC=$(LOADADDRESS) -DMOVEROM -DSTACKADDR=0xA0000-0x98000 -I ../fs/ffs2

# Enable this if your flash window is 4k
# CPPFLAGS += -DSMALLER

all: flashloader.rom sbc_gxx.rom

# To create the loader we first
flashloader.rom: makerom rloader.bin start32.o components.a
	$(LD) -N -Ttext $(LOADADDRESS) -e _start -nostdlib -o $@.t start32.o components.a
	objcopy -O binary -R .note -R .comment $@.t $@.bin
	@-rm $@.t
	cat rloader.bin $@.bin > $@
	./makerom -i'Flash Loader Rom' $@

# We create a library to help the linker
components.a: misc.o main.o io.o
	@-rm $@ > /dev/null 2>&1
	$(AR) cq $@ $(filter %.o,$^)

sbc_gxx.rom: makerom rloader.bin start32.o sbc_gxx.a
	$(LD) -N -Ttext $(LOADADDRESS) -e _start -nostdlib -o $@.t start32.o \
	sbc_gxx.a
	objcopy -O binary -R .note -R .comment $@.t $@.bin
	@-rm $@.t
	cat rloader.bin $@.bin > $@
	./makerom -i'Flash Loader Rom' $@

# We create a library to help the linker
sbc_gxx.a: misc.o sbc_gxx_boot.o
	@-rm $@ > /dev/null 2>&1
	$(AR) cq $@ $(filter %.o,$^)

# The 16 bit boot code is written using the as86 assembler which is not syntax
# compatible with the AT&T like 'as' assembler. Throwback from when gnu as
# could not output 16 bit code at all.
rloader.bin: loader.asm loader.inc
	$(CC) $(CFLAGS) $(CPPFLAGS) -x assembler-with-cpp -E $< | $(AS86) -0 -b rloader.bin.t -
	mv -f rloader.bin.t rloader.bin

io.o: ../fs/ffs2/io.c
	ln -sf ../fs/ffs2/io.c .
	$(CC) $(CFLAGS) $(CPPFLAGS) -c io.c -o $@

clean:
	-rm -f *.a *.o *.bin *.rom makerom

%.o: %.c
	$(CC) $(CFLAGS) $(CPPFLAGS) -c $< -o $@

%.o: %.S
	$(CC) $(CFLAGS) $(CPPFLAGS) -c $< -o $@

