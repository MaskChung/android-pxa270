
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

