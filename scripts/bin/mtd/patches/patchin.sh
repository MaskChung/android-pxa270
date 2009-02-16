#!/bin/sh
#
# Patch mtd into kernel
#
# usage:patch [-j] kernelpath
# 	kernelpath must be given
#	-j includes filesystems (jffs, jffs2)
#	
# Works for Kernels >= 2.4.11 full functional
# Works for Kernels >= 2.4 and <= 2.4.10 partly (JFFS2 support is missing)
# For 2.2 Kernels it's actually disabled, as I have none to test it.
#
# You can use it for pristine kernels and for already patches kernels too.
#
# Detects Kernelversion and applies neccecary modifications
# For Kernelversions < 2.4.20 ZLIB-Patch is applied, if 
# filesystem option is set and ZLIB-Patch is not already there 
#
# Maybe some sed/awk experts would make it better, but I'm not
# one of them. Feel free to make it better
# 
# Thomas (tglx@linutronix.de)
#
# $Id: patchin.sh,v 1.40 2005/03/03 11:58:03 lavinen Exp $
#
# 09-12-2004 dedekind add JFFS3 installing capability, reformat, clean-up
# 24-05-2004 havasi Patch fs/Kconfig
# 05-05-2004 tglx Include include/mtd
# 12-06-2003 dwmw2 Leave out JFFS1, do Makefile.common only if it exists.
# 27-05-2003 dwmw2 Link Makefile to Makefile.common since we moved them around
# 02-10-2003 tglx replaced grep -m by head -n 1, as older grep versions don't support -m	
# 03-08-2003 tglx -c option for copying files to kernel tree instead of linking
#		  moved file selection to variables


# Finds the first and the last line numbers of nonempty lines of
# Kconfig subsystem $2. $1 contains the file name where to search for.
#
# Returns the result in FIRSTLINE and LASTLINE variables.
#
function find_lines () {
	# find the line number of the first and the last $2 entries
	FIRSTLINE=`awk '/'"$2"'/ { print NR; exit }' <$1`

	if [ "x$FIRSTLINE" = "x" ]; then
		FIRSTLINE=0;
		LASTLINE=0;
		return 0;
	fi;

	LASTLINE=`awk '
            BEGIN{ accept=0 }
            /'"$2"'/                { accept=1; last=NR; next }
            /^(choice|config|menu)/ { accept=0; } # Possible subsystem end
            accept && ! /^[ \t]*$/  { last=NR }   # Matches nonempty lines
            END{ print last } # print the last nonempty line of the susystem
            '<$1 `
}

#
# Patch the Kconfig file. Helper function for patch_Kconfig().
# 
# Function requires the following positional parameters:
# 
# $1:	the path of the input Kconfig file from which the function
#	should fetch new the Kconfig entries;
# $2:	the path of the target linux Kconfig file which should be
#	patched; if the parameter value is '', just insert to the end of
#	the target Kconfig file;
# $3:	the line number of the first entry in the in the input file;
# $4:	the line number of the last line in the input file;
# $5:	the menu name in target Kconfig file where the new entries should
#	be added.
#
function do_patch_Kconfig () {
	local INFILE=$1;
	local OUTFILE=$2;
	local FIRSTLINE=$3;
	local LASTLINE=$4;
	local MENUNAME=$5;

	# read the entries from the input file
	local ENTRIES=`sed -n "$INFILE" -e "$FIRSTLINE,$LASTLINE p"`;

	# determine the menu line number
	local MENULINE='';
	local TOTALOUT=`cat $OUTFILE | wc -l`;
	if [ "x$MENUNAME" != "x" ]; then
		MENULINE=`grep menu $OUTFILE | grep -m1 -n "$MENUNAME" $OUTFILE | sed -e 's/:.*//'`;
	else
		let MENULINE=$TOTALOUT;
	fi;
	
	# copy lines 1-MENULINE from the target file to the temporary file
	TMPFILE="Kconfig.$$";
	sed -n "$OUTFILE" -e "1,$MENULINE p" > $TMPFILE;
	echo >> $TMPFILE;
	# append our lines
	sed -n "$INFILE" -e "$FIRSTLINE,$LASTLINE p" >> $TMPFILE;
	# append lines MENULINE+1-TOTALOUT from the target files to the temporary file
	if [ $MENULINE -ne $TOTALOUT ]; then
		let MENULINE=$MENULINE+1;
		sed -n "$OUTFILE" -e "$MENULINE,$TOTALOUT p" >> $TMPFILE;
	fi;
	# replace the target file by the temporary which now contains our entries
	mv $TMPFILE $OUTFILE;

	return 0;
}

function appendrange() {
	local INFILE=$1;
	local OUTFILE=$2;
	local FIRSTLINE=$3;
	local LASTLINE=$4;

	if [ "$FIRSTLINE" -le "$LASTLINE" ]; then
	    sed -n "$INFILE" -e "$FIRSTLINE,$LASTLINE p" >> $OUTFILE
	fi
}


# Replaces a range in file by another range in another file.  Meant to
# for example replace old JFFS2 entris Kconfig by new entries in a
# template file.
#
# Function requires the following positional parameters:
# 
# $1:	The input file containing the replacing range,
# $2:	The output file containing the range to be replaced.
# $3:	The first line of replacing range in input file
# $4:	The last line of replacing range in input file
# $5:   The first line of the range to be replaced in output file
# $6:   The last line of the range to be replaced in output file
#
function replace_range () {
	local INFILE=$1;
	local OUTFILE=$2;
	local FIRSTLINEIN=$3;
	local LASTLINEIN=$4;
	local FIRSTLINEOUT=$5;
	local LASTLINEOUT=$6;

	local TMPFILE="${OUTFILE}.$$";
	rm -f $TMPFILE
	touch $TMPFILE

	let FIRSTLINEOUT=FIRSTLINEOUT-1
	appendrange $OUTFILE $TMPFILE 1 $FIRSTLINEOUT

	appendrange $INFILE $TMPFILE $FIRSTLINEIN $LASTLINEIN       

	let LASTLINEOUT=LASTLINEOUT+1
	local LINES=`wc -l < $OUTFILE`
	appendrange $OUTFILE $TMPFILE $LASTLINEOUT $LINES

	mv -f $TMPFILE $OUTFILE
}

#
# Patch the Kconfig file. This function assumes that there is some input Kconfig file
# (one placed in mtd) containing (among other) a block of continguous entries for
# some subsystem (e.g, JFFS2). Function reads this block and inserts it to the
# target Kconfig file (in Linux sources). Before inserting it removes older entries
# from the target Kconfig.
#
# Input positional parameters:
# 
# $1:	the path of the input Kconfig file from which the function
#	should fetch new the Kconfig entries;
# $2:	the path of the target linux Kconfig file which should be
#	patched; if the parameter value is '', just insert to the end of
#	the target Kconfig file;
# $3:	The patched subsystem name;
# $4:	the pattern in the target Kconfig file after which the new entries should
#	be inserted.
# 
function patch_Kconfig () {
	local INFILE=$1;
	local OUTFILE=$2
	local SUBSYS=$3;
	local MENUNAME=$4;
	
	if [ ! -f $INFILE ] || [ ! -r $INFILE ]; then
		echo -n "Error: the proper input file $INFILE does not exist";
		return 1;
	fi;

	bkeditlock $OUTFILE
	
	if [ ! -f $OUTFILE ] || [ ! -r $OUTFILE ] || [ ! -w $OUTFILE ]; then
		echo -n "Error: the proper output Kconfig file $INFILE does not exist";
		return 1;
	fi;

	find_lines "$INFILE" "$SUBSYS"
	local FIRSTLINEIN=$FIRSTLINE
	local LASTLINEIN=$LASTLINE

	find_lines $OUTFILE "$SUBSYS";
	local FIRSTLINEOUT=$FIRSTLINE
	local LASTLINEOUT=$LASTLINE
	
	if [ "$FIRSTLINEOUT" -gt 0 -a "$LASTLINEOUT" -gt 0 ]; then
	    replace_range $INFILE $OUTFILE $FIRSTLINEIN $LASTLINEIN $FIRSTLINEOUT $LASTLINEOUT
	else
	    # There is no such subsystem in Kconfig. Add new one
	    do_patch_Kconfig $INFILE $OUTFILE $FIRSTLINEIN $LASTLINEIN $MENUNAME;
	fi
}

#
# Patch the fs/Makfile file.
# 
# Function requires the following positional parameters:
# 
# $1:   the path of the Makefile
#
# $2:   pattern of the previous line, after it the third parameter will be inserted
#
# $3:   the line which should be inserted
#
# Note: the $3 will be inserted only if it is absent after $2.
#
function patch_fs_Makefile () {
	local INFILE=$1;
	local PREVLINE=$2;
	local NEWLINE=$3;

	local TOTAL=`cat $INFILE | wc -l`;

	bkeditlock $INFILE
	
	# do not insert pattern if it is already present
	if [ "x`grep "$NEWLINE" $INFILE`" != "x" ]; then
		return 0;
	fi;
	
	# determine the previous line number
	local LINENUM='';
	LINENUM=`grep -m1 -n "$PREVLINE" $INFILE | sed -e 's/:.*//'`;
	
	# copy lines 1-LINENUM from the target file to the temporary file
	TMPFILE="Makefile.$$";
	sed -n "$INFILE" -e "1,$LINENUM p" > $TMPFILE;
	echo "$NEWLINE" >> $TMPFILE;
	let LINENUM=$LINENUM+1;
	
	sed -n "$INFILE" -e "$LINENUM,$TOTAL p" >> $TMPFILE;
	# replace the target file by the temporary which now contains our entries
	mv $TMPFILE $INFILE;

	return 0;
}


#
# Patch old kernels with JFFS/JFFS2 filesystems
# 
function legacy_fs_patch () {
	local PATCHDONE=`grep -s jffs2 fs/Makefile | head -n 1`
	if [ "$PATCHDONE" = "" ]; then
		echo "Add JFFS2 to Makefile and Config.in manually. JFFS2 is included as of 2.4.12"
		return 0;
	fi;
	
	local JFFS=`grep -n JFFS fs/Config.in | grep -v JFFS3 | head -n 1 | sed s/:.*//`
	local CRAMFS=`grep -n CRAMFS fs/Config.in | head -n 1 | sed s/:.*//`
	let JFFS=JFFS-1
	let CRAMFS=CRAMFS-1
	sed "$JFFS"q fs/Config.in >Config.tmp
	cat $TOPDIR/fs/Config.in >>Config.tmp
	sed 1,"$CRAMFS"d fs/Config.in >>Config.tmp
	mv -f Config.tmp fs/Config.in
	
	if [ -f include/linux/crc32.h ] 
	then
		# check, if it is already defined there
		local CRC32=`grep -s 'crc32(' include/linux/crc32.h | head -n 1`
		if [ "$CRC32" = "" ]
		then
			# patch in header from fs/jffs2
			local LASTLINE=`grep -n '#endif' include/linux/crc32.h | head -n 1 | sed s/:.*//`
			let LASTLINE=LASTLINE-1
			sed "$LASTLINE"q include/linux/crc32.h >Crc32.tmp
			cat fs/jffs2/crc32.h >>Crc32.tmp
			echo "#endif" >>Crc32.tmp
			mv -f Crc32.tmp include/linux/crc32.h
		fi
	else
		rm -f include/linux/crc32.h
		$LNCP $TOPDIR/fs/jffs2/crc32.h include/linux
	fi
}

#
# Display usage of this script
#
usage () {
	echo "usage:  $0 [-c] [-j] [-2] [-3] [-b] [-y] kernelpath"
	echo "   -c  -- copy files to kernel tree instead of building links"
	echo "   -j  -- include JFFS2 file system (depricated option)" 
	echo "   -2  -- include JFFS2 file system"
	echo "   -3  -- include JFFS3 file system (experimental, you probably don't want this)"
	echo "   -b  -- check files out for write from BK" 
	echo '   -y  -- assume "Yes" on any question'
	exit 1
}

# Tests if the files is under version control or not
function sfileexists() {
    [ -e "`dirname $1`/SCCS/s.`basename $1`" ]
    return
}

function bkunedit () {
    local file
    [ "$BK" = "yes" ] || return
    for file in $*; do
	sfileexists $file && bk unedit -q "$file"
    done
}

# Obtain an editable lock of a local file in current directory if it is version
# controlled under BK.
function bkeditlock () {
    local file
    [ "$BK" = "yes" ] || return
    for file in $*; do	
	sfileexists $file && bk edit -q "$file"
    done
}

# Function to patch kernel source
function patchit () {
	for DIR in $PATCH_DIRS 
	do
		echo $DIR
		mkdir -p $DIR
		cd $TOPDIR/$DIR
		FILES=`ls $PATCH_FILES 2>/dev/null`

		# Remove excluded files
		if [ "$EXCLUDE_FILES" != "" ]; then
		    local EXLIST=`ls $EXCLUDE_FILES 2>/dev/null`
		    if [ "$EXLIST" != "" ]; then
			local FILELIST=""
			local file
			for file in $FILES; do
			    local exclude=no
			    for exfile in $EXLIST; do
			        [ "$exfile" = "$file" ] && exclude=yes
			    done
			    [ "$exclude" = "no" ] && FILELIST="$FILELIST $file"
			done
			FILES=$FILELIST
		    fi
		    local CRAPLIST=`cd $LINUXDIR/$DIR; ls $EXCLUDE_FILES 2>/dev/null`
		    [ "$CRAPLIST" != "" ] && echo "Obsolete or unneeded files found in $LINUXDIR/$DIR:" $CRAPLIST
		fi

		if [ "$BK" = "yes" -a -d $LINUXDIR/$DIR/SCCS ]; then
		    pushd $LINUXDIR/$DIR
		    bkunedit $FILES
		    bkeditlock $FILES
		    popd
		fi

		for FILE in $FILES 
		do
			# If there's a Makefile.common it goes in place of Makefile
			if [ "$FILE" = "Makefile" -a -r $TOPDIR/$DIR/Makefile.common ]; then
			    if test $PATCHLEVEL -lt 5; then
				rm -f $LINUXDIR/$DIR/Makefile.common 2>/dev/null
				$LNCP $TOPDIR/$DIR/Makefile.common $LINUXDIR/$DIR/Makefile.common
				SRCFILE=Makefile.24
			    else
				SRCFILE=Makefile.common
			    fi
			else
			    SRCFILE=$FILE
			fi
			rm -f $LINUXDIR/$DIR/$FILE 2>/dev/null
			$LNCP $TOPDIR/$DIR/$SRCFILE $LINUXDIR/$DIR/$FILE
		done
		cd $LINUXDIR
	done
	EXCLUDE_FILES=""
}

# Preset variables
JFFS2_FS="no"
JFFS3_FS="no"
BK="no"
VERSION=0
PATCHLEVEL=0
SUBLEVEL=0
ZLIBPATCH="no"
RSLIBPATCH="no"
DOCPATCH="no"
CONFIG="Config.in"
LNCP="ln -sf"
METHOD="Link"
ASSUME_YES="no"

# MTD - files and directories
MTD_DIRS="drivers/mtd drivers/mtd/chips drivers/mtd/devices drivers/mtd/maps drivers/mtd/nand include/linux/mtd include/mtd"
MTD_FILES="*.[ch] Makefile Rules.make"
MTD_FILES_EX26="mtd_blkdevs-24.c blkmtd-2[45].c integrator-flash-v24.c"

# JFFS2 files and directories
JFFS2_DIRS="fs/jffs2"
JFFS2_FILES="*.[ch] Makefile Rules.make"
JFFS2_FILES_EX26="compr_lzari.c compr_lzo.c crc32.[hc] rbtree.c super-v24.c symlink-v24.c"
# kernel version < 2.4.20 needs zlib headers
JFFS2_INC_BEL2420="jffs*.h workqueue.h z*.h rb*.h suspend.h"
# kernel version < 2.5.x
JFFS2_INC_BEL25="jffs2*.h workqueue.h rb*.h suspend.h"
# kernelversion >= 2.5
JFFS2_INC_25="jffs2*.h"
JFFS2_INC_DIR="include/linux"

# shared ZLIB patch
ZLIB_DIRS="lib/zlib_deflate lib/zlib_inflate"
ZLIB_FILES="*.[ch] Makefile"
# shared REED_SOLOMON patch
RSLIB_DIRS="lib/reed_solomon"
RSLIB_FILES="*.[ch]"
RSLIB_INC_DIR="include/linux"
RSLIB_INC="rslib.h"

# Documentation
DOC_DIRS="Documentation/DocBook"
DOC_FILES="*.tmpl"

# Experimental stuff
JFFS3_DIRS="fs/jffs3"
JFFS3_FILES="*.[ch] Makefile Rules.make"
JFFS3_INC="jffs3.h"
JFFS3_INC_DIR="fs/jffs3"

# Make text utils not suck
export LANG=C
export LC_ALL=C

#
# Start of script
#

# Get commandline options
while getopts j23cby opt
do
    case "$opt" in
      j)  JFFS2_FS=yes;;
      2)  JFFS2_FS=yes;;
      3)  JFFS3_FS=yes;;
      c)  LNCP="cp -f"; METHOD="Copy";;
      b)  BK=yes;;
      y)  ASSUME_YES="yes";;
      \?)
	  usage;
    esac
done
shift `expr $OPTIND - 1`
LINUXDIR=$1

if [ -z $LINUXDIR ]; then
    usage;
fi

if [ ! -f $LINUXDIR/Makefile -a "$BK" = "yes" ]; then
    pushd $LINUXDIR
    bk co Makefile
    popd
fi

# Check if kerneldir contains a Makefile
if [ ! -f $LINUXDIR/Makefile ] 
then 
	echo "Directory $LINUXDIR does not exist or is not a kernel source directory";
	exit 1;
fi

# Get kernel version
VERSION=`grep -s VERSION <$LINUXDIR/Makefile | head -n 1 | sed s/'VERSION = '//`
PATCHLEVEL=`grep -s PATCHLEVEL <$LINUXDIR/Makefile | head -n 1 | sed s/'PATCHLEVEL = '//`
SUBLEVEL=`grep -s SUBLEVEL <$LINUXDIR/Makefile | head -n 1 | sed s/'SUBLEVEL = '//`

# Can we handle this ?
if test $VERSION -ne 2 -o $PATCHLEVEL -lt 4
then 
	echo "Cannot patch kernel version $VERSION.$PATCHLEVEL.$SUBLEVEL";
	exit 1;
fi

# Use Kconfig instead of Config.in for Kernels >= 2.5
if test $PATCHLEVEL -gt 4
then
	CONFIG="Kconfig";
fi
MTD_FILES="$MTD_FILES $CONFIG"

# Have we to use ZLIB PATCH ? 
if [ "$JFFS2_FS" = "yes" ]
then
	PATCHDONE=`grep -s zlib_deflate $LINUXDIR/lib/Makefile | head -n 1`
	if test $PATCHLEVEL -eq 4 -a $SUBLEVEL -lt 20 
	then
		if [ "$PATCHDONE" = "" ] 
		then
			ZLIBPATCH=yes;
		fi
	fi
fi

# Have we to use REED_SOLOMON PATCH ?
PATCHDONE=`grep -s reed_solomon $LINUXDIR/lib/Makefile | head -n 1`
if [ "$PATCHDONE" = "" ]
then
	RSLIBPATCH=yes;
fi

# Have we to use DOCUMENTATION PATCH ?
PATCHDONE=`grep -s mtdnand $LINUXDIR/$DOC_DIRS/Makefile | head -n 1`
if [ "$PATCHDONE" = "" ]
then
    if test $PATCHLEVEL -gt 4 
    then
	DOCPATCH=yes;
    fi
fi

# Check which header files we need depending on kernel version
HDIR="include/linux"

if test $JFFS3_FS = 'yes' && test $PATCHLEVEL -lt 6
then
	echo "JFFS3 works only with >= 2.6 kernels"
	exit 1
fi;

if test $PATCHLEVEL -eq 4 
then	
	# 2.4 below 2.4.20 zlib headers are neccecary
	if test $SUBLEVEL -lt 20
	then
		JFFS2_H=$JFFS2_INC_BEL2420
	else
		JFFS2_H=$JFFS2_INC_BEL25
	fi
else
	#	>= 2.5
	JFFS2_H=$JFFS2_INC_25
fi

echo "Patching $LINUXDIR"
echo "Include JFFS2 file system: $JFFS2_FS"
echo "Include JFFS3 file system (experimental): $JFFS3_FS"
echo "Zlib-Patch needed: $ZLIBPATCH"
echo "RS-Lib-Patch needed: $RSLIBPATCH"
echo "Documentation Patch needed: $DOCPATCH"
echo "Method: $METHOD"

if [ $ASSUME_YES != "yes" ]; then
	read -p "Can we start now ? [y/N]" ANSWER
	echo ""
	if [ "$ANSWER" != "y" ]; then
		echo Patching Kernel cancelled
		exit 1;
	fi
fi

# Here we go
cd `dirname $0`
THISDIR=`pwd`
TOPDIR=`dirname $THISDIR`

cd $LINUXDIR

# make directories, if necessary
# remove existing files/links and link/copy the new ones
echo "Patching MTD"
PATCH_DIRS=$MTD_DIRS
PATCH_FILES=$MTD_FILES
if test $PATCHLEVEL -ge 6; then
    EXCLUDE_FILES=$MTD_FILES_EX26;
fi
patchit;

# some BUG() definitions were moved to asm/bug.h in the 2.5 kernels
# so fake having one to avoid build errors.
if test $PATCHLEVEL -lt 5; then
	if [ ! -r $LINUXDIR/include/asm/bug.h ]; then
		touch $LINUXDIR/include/asm/bug.h
	fi
fi

if test $PATCHLEVEL -lt 5
then 
	# FIXME: SED/AWK experts should know how to do it automagic
	echo "Please update Documentation/Configure.help from $TOPDIR/Documentation/Configure.help"
fi

if [ "$ZLIBPATCH" = "yes" ]
then
	echo "Patching ZLIB"
	
	PATCH_DIRS=$ZLIB_DIRS
	PATCH_FILES=$ZLIB_FILES
	patchit;

	bkeditlock lib/Makefile
	patch -p1 -i $TOPDIR/lib/patch-Makefile
fi

echo "Patching RS Lib"
if [ "$RSLIBPATCH" = "yes" ]
then
	if test $PATCHLEVEL -eq 4 
	then
		patch -p1 -i $TOPDIR/lib/Makefile24-rs.diff
		patch -p1 -i $TOPDIR/lib/Config.in-rs.diff
	else
	        bkeditlock lib/Makefile lib/Kconfig
		patch -p1 -i $TOPDIR/lib/Makefile26-rs.diff
		patch -p1 -i $TOPDIR/lib/Kconfig-rs.diff
	fi
	
	mkdir -p lib/reed_solomon
	
fi

	PATCH_DIRS=$RSLIB_DIRS
	PATCH_FILES=$RSLIB_FILES
	patchit;

	PATCH_DIRS=$RSLIB_INC_DIR
	PATCH_FILES=$RSLIB_INC
	patchit;
	if test $PATCHLEVEL -eq 6 
	then
		mv -f lib/reed_solomon/rslib.c lib/reed_solomon/reed_solomon.c
	fi

if test $PATCHLEVEL -eq 4 
then
	PATCH_DIRS=$RSLIB_DIRS
	PATCH_FILES="Makefile24"
	patchit;
	rm -f $LINUXDIR/lib/reed_solomon/Makefile 2>/dev/null
	mv -f $LINUXDIR/lib/reed_solomon/Makefile24 $LINUXDIR/lib/reed_solomon/Makefile
else	
	PATCH_DIRS=$RSLIB_DIRS
	PATCH_FILES="Makefile26"
	patchit;
	rm -f $LINUXDIR/lib/reed_solomon/Makefile 2>/dev/null
	mv -f $LINUXDIR/lib/reed_solomon/Makefile26 $LINUXDIR/lib/reed_solomon/Makefile
fi

echo "Patching Documentation"
if [ "$DOCPATCH" = "yes" ]
then
    bkeditlock $DOC_DIRS/Makefile
    patch -p1 -i $TOPDIR/$DOC_DIRS/Makefile.diff
fi

PATCH_DIRS=$DOC_DIRS
PATCH_FILES=$DOC_FILES
patchit;

# check, if we have to include JFFS2
if [ "$JFFS2_FS" = "yes" ]
then
	echo "Patching JFFS2"
	
	PATCH_DIRS=$JFFS2_DIRS
	PATCH_FILES=$JFFS2_FILES
	if test $PATCHLEVEL -ge 6; then
	    EXCLUDE_FILES=$JFFS2_FILES_EX26
	fi
	patchit;

	PATCH_DIRS=$JFFS2_INC_DIR
	PATCH_FILES=$JFFS2_H
	patchit;

	if test $PATCHLEVEL -lt 5; then 
		legacy_fs_patch;
	else
		patch_Kconfig "$TOPDIR/fs/Kconfig" "fs/Kconfig" "JFFS2" "Miscellaneous filesystems";
	fi;
fi

# check, if we have to include experimental stuff
if [ "$JFFS3_FS" = "yes" ]
then
	echo "Patching JFFS3 (experimental)"
	
	PATCH_DIRS=$JFFS3_DIRS;
	PATCH_FILES=$JFFS3_FILES;
	patchit;

	PATCH_DIRS=$JFFS3_INC_DIR;
	PATCH_FILES=$JFFS3_INC;
	patchit;
	
	patch_Kconfig "$TOPDIR/fs/Kconfig" "fs/Kconfig" "JFFS3" "Miscellaneous filesystems";
	patch_fs_Makefile "fs/Makefile" 'CONFIG_JFFS2_FS' 'obj-$(CONFIG_JFFS3_FS)		+= jffs3/'
fi

echo "Patching done"

