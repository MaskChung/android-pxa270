
export ARCH			:= arm
export CROSS_COMPILE		:= arm-none-linux-gnueabi-

export ASM			:= $(CROSS_COMPILE)as
export LD			:= $(CROSS_COMPILE)ld
export CC			:= $(CROSS_COMPILE)gcc
export CPP			:= $(CROSS_COMPILE)c++
export AR			:= $(CROSS_COMPILE)ar
export STRIP   			:= $(CROSS_COMPILE)strip
export OBJCOPY			:= $(CROSS_COMPILE)objcopy
export OBJDUMP			:= $(CROSS_COMPILE)objdump
export RANLIB  			:= $(CROSS_COMPILE)ranlib


.SUFFIXES : .o .S .s .cpp .c .i

.S.o :
	$(ASM) $(AFLAGS) -o $@ $<

.s.o :
	$(ASM) $(AFLAGS) -o $@ $<

.cpp.o :
	$(CPP) $(CFLAGS) -c -o $@ $<

.c.o :
	$(CC) $(CFLAGS) -c -o $@ $<

.c.i :
	$(CC) $(CFLAGS) -DDIAG_API_H -C -E -o $@ $<

