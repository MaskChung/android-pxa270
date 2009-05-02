
export ARCH			:= arm
export CROSS_COMPILE = $(patsubst %gcc,%,$(shell find $(call path-for,toolchain) -maxdepth 3 -name "*gcc" -type f | sed 's/^.*bin\///g'))

export ASM			= $(CROSS_COMPILE)as
export LD			= $(CROSS_COMPILE)ld
export CC			= $(CROSS_COMPILE)gcc
export CPP			= $(CROSS_COMPILE)c++
export AR			= $(CROSS_COMPILE)ar
export STRIP   			= $(CROSS_COMPILE)strip
export OBJCOPY			= $(CROSS_COMPILE)objcopy
export OBJDUMP			= $(CROSS_COMPILE)objdump
export RANLIB  			= $(CROSS_COMPILE)ranlib

