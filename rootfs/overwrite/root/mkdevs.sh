cd /dev

ln -s /dev/graphics/fb0 /dev/fb0

#ide hard disks
mknod hda b 3 0
for i in `seq 1 19`; do
	mknod hda$i b 3 $i
done

mknod hdb b 3 64
for i in `seq 1 19`; do
	mknod hdb$i b 3 `expr $i + 64`
done

mknod hdc b 22 0
for i in `seq 1 19`; do
	mknod hdc$i b 22 $i
done

#scsi hard disks
mknod sda b 8 0
for i in `seq 1 9`; do
	mknod sda$i b 8 $i
done

mknod sdb b 8 16
for i in `seq 1 9`; do
	mknod sdb$i b 8 `expr $i + 16`
done

mknod sdc b 8 32
for i in `seq 1 9`; do
	mknod sdc$i b 8 `expr $i + 32`
done

# loop devs
for i in `seq 0 7`; do
	mknod loop$i b 7 $i
done

# ram devs
for i in `seq 0 9`; do
	mknod ram$i b 1 $i
done
ln -s ram1 ram
