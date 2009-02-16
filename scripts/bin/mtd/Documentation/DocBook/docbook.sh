#!/bin/sh
#
# 
#

# Display usage of this script
usage () {
	echo "usage:  $0 kernelpath mtdpath htmlpath book"
	exit 1
}

KERNELPATH=$1
MTDPATH=$2
HTMLPATH=$3
BOOK=$4

if [ -z "$KERNELPATH" ] || [ -z "$MTDPATH" ] || [ -z "$HTMLPATH" ] || [ -z "$BOOK" ]
then
	usage;
fi	

# create output directory
mkdir -p $HTMLPATH
# goto the source path
cd $MTDPATH

# link the scripts directory, as this crappy code does not work otherwise
ln -sf $KERNELPATH/scripts .

# create the sgml file
scripts/basic/docproc doc Documentation/DocBook/$BOOK.tmpl >Documentation/DocBook/$BOOK.sgml
# convert to html
db2html -o $HTMLPATH/$BOOK Documentation/DocBook/$BOOK.sgml

# clean up temporary stuff
rm -f scripts
rm -rf Documentation/DocBook/$BOOK
