#!/bin/sh
mkdir -p t
i=1
while [ $i -le 100 ]
do
	make clean
	date --rfc-3339=second > "t/log$i"
	cp config/top.conf .config
	make build_toolchain
	make build_kernel build_mydroid >> "t/log$i" 2>&1
	date --rfc-3339=second >> "t/log$i"
	i=`expr $i + 1`
done
