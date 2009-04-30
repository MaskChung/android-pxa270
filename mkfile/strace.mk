
strace:
	$(MAKE) -f Makefile.strace
#	cd strace-4.5.18 && ./configure --prefix=`pwd` --host=arm-none-linux-gnueabi && $(MAKE)


strace:
	$(MAKE) -f Makefile.strace
#	cd strace-4.5.18 && ./configure --prefix=`pwd` --host=arm-none-linux-gnueabi && $(MAKE)

clean_strace:
	$(MAKE) -f Makefile.strace clean


#strace:
#	cd app && $(MAKE) strace
#	#cd $(PRJROOT)/scripts/bin && $(MAKE) strace
