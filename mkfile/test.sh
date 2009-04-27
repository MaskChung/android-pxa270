#!/bin/sh
ooxx=$(md5sum scripts/toolchain/arm*.bz2 | awk '{print $1}')
aabb=`cat scripts/toolchain/md5`
###ooxx = `md5sum scripts/toolchain/arm*.bz2`
echo $ooxx
echo $aabb

if [ $ooxx != $aabb ] ; then 
echo "shit"
fi
