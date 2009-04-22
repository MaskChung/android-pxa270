#!/usr/bin/bash

# Copyright (C) 2007 Apple Inc.  All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
# 1. Redistributions of source code must retain the above copyright
#    notice, this list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright
#    notice, this list of conditions and the following disclaimer in the
#    documentation and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY APPLE COMPUTER, INC. ``AS IS'' AND ANY
# EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
# PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL APPLE COMPUTER, INC. OR
# CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
# OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. 

SRCPATH=`cygpath -u "$WEBKITLIBRARIESDIR\Tools\Scripts"`
VERSIONPATH=`cygpath -u "$1"`
VERSIONPATH=$VERSIONPATH/include
VERSIONFILE=$VERSIONPATH/autoversion.h
mkdir -p "$VERSIONPATH"

PRODUCTVERSION=`cat "$SRCPATH/PRODUCTVERSION"`
MAJORVERSION=`sed 's/\([^\.]*\)\.\([^.]*\)\(\.\([^.]*\)\)\?/\1/' "$SRCPATH/PRODUCTVERSION"`
MINORVERSION=`sed 's/\([^\.]*\)\.\([^.]*\)\(\.\([^.]*\)\)\?/\2/' "$SRCPATH/PRODUCTVERSION"`
TINYVERSION=`sed 's/\([^\.]*\)\.\([^.]*\)\(\.\([^.]*\)\)\?/\4/' "$SRCPATH/PRODUCTVERSION"`
if [ "$TINYVERSION" == "" ]; then
    TINYVERSION=0
fi

if [ "$RC_PROJECTSOURCEVERSION" == "" ]; then
    echo -n `cat "$SRCPATH/VERSION"` > "$VERSIONFILE"
else
    echo -n $RC_PROJECTSOURCEVERSION > "$VERSIONFILE"
fi

if [ `grep -c -E "4\.|4$" "$VERSIONFILE"` -ne 0 ]; then
    echo "Can't set WebKit's version to something that ends in a 4!"
    echo "If we do, someone's going to think it's Netscape 4."
    echo "Radar bug 3787996 has some details."
    exit 1
fi

BLDMAJORVERSION=`sed 's/\([^\.]*\)\(\.\([^.]*\)\(\.\([^.]*\)\)\?\)\?/\1/' "$VERSIONFILE"`
BLDMINORVERSION=`sed 's/\([^\.]*\)\(\.\([^.]*\)\(\.\([^.]*\)\)\?\)\?/\3/' "$VERSIONFILE"`
BLDVARIANTVERSION=`sed 's/\([^\.]*\)\(\.\([^.]*\)\(\.\([^.]*\)\)\?\)\?/\5/' "$VERSIONFILE"`
if [ "$BLDMINORVERSION" == "" ]; then
    BLDMINORVERSION=0
fi
if [ "$BLDVARIANTVERSION" == "" ]; then
    BLDVARIANTVERSION=0
fi
SVNOPENSOURCEREVISION=`svn info | grep '^Revision' | sed 's/^Revision: \(.*\)/\1/'`

BLDNMBR=`cat "$VERSIONFILE"`
BLDNMBRSHORT=`cat "$VERSIONFILE"`
BUILDER=""

if [ "$RC_PROJECTSOURCEVERSION" == "" ]; then
    echo -n "+" >> "$VERSIONFILE"
    BLDNMBRSHORT=`cat "$VERSIONFILE"`
    echo -n " " >> "$VERSIONFILE"
    echo -n `whoami` >> "$VERSIONFILE"
    echo -n " - " >> "$VERSIONFILE"
    echo -n `date` >> "$VERSIONFILE"
    echo -n " - r$SVNOPENSOURCEREVISION" >> "$VERSIONFILE"
    BLDNMBR=`cat "$VERSIONFILE"`
fi

echo -n '#define __VERSION_TEXT__ "' > "$VERSIONFILE"
echo -n $PRODUCTVERSION >> "$VERSIONFILE"
echo -n " (" >> "$VERSIONFILE"
echo -n $BLDNMBR >> "$VERSIONFILE"
echo ')"' >> "$VERSIONFILE"
echo -n '#define __BUILD_NUMBER_SHORT__ "' >> "$VERSIONFILE"
echo -n $BLDNMBRSHORT >> "$VERSIONFILE"
echo '"' >> "$VERSIONFILE"

echo -n '#define __VERSION_MAJOR__ ' >> "$VERSIONFILE"
echo $MAJORVERSION >> "$VERSIONFILE"
echo -n '#define __VERSION_MINOR__ ' >> "$VERSIONFILE"
echo $MINORVERSION >> "$VERSIONFILE"
echo -n '#define __VERSION_TINY__ ' >> "$VERSIONFILE"
echo $TINYVERSION >> "$VERSIONFILE"

echo -n '#define __BUILD_NUMBER_MAJOR__ ' >> "$VERSIONFILE"
echo $BLDMAJORVERSION >> "$VERSIONFILE"
echo -n '#define __BUILD_NUMBER_MINOR__ ' >> "$VERSIONFILE"
echo $BLDMINORVERSION >> "$VERSIONFILE"
echo -n '#define __BUILD_NUMBER_VARIANT__ ' >> "$VERSIONFILE"
echo $BLDVARIANTVERSION >> "$VERSIONFILE"

echo -n '#define __SVN_REVISION__ ' >> "$VERSIONFILE"
echo $SVNREVISION >> "$VERSIONFILE"
