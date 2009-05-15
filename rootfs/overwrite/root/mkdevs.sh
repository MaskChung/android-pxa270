cd /dev

ln -s /dev/graphics/fb0 /dev/fb0

#scsi hard disks
mknod sda b 8 0
for i in `seq 1 9`; do
	mknod sda$i b 8 $i
done

