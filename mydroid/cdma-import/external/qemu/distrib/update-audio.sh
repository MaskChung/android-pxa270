#!/bin/bash
#
# this script is used to update the prebuilt libqemu-audio.a file in the Android source tree
# we use a prebuilt package because we don't want to force the installation of the ALSA / EsounD / Whatever
# development packages on every developer machine, or every build server.
#

# assumes this script is located in the 'distrib' sub-directory
cd `dirname $0`
cd ..

locate_depot_files ()
{
    root=$(p4 where $1) || (
        echo "you need to map $1 into your workspace to build an emulator source release package"
        exit 3
    )
    root=$(echo $root | cut -d" " -f3 | sed -e "s%/\.\.\.%%")
    echo $root
}

# find the prebuilt directory
OS=`uname -s`
EXE=""
case "$OS" in
    Darwin)
        CPU=`uname -p`
        if [ "$CPU" == "i386" ] ; then
            OS=darwin-x86
        else
            OS=darwin-ppc
        fi
        ;;
    *_NT-5.1)
        OS=windows
        EXE=.exe
        ;;
esac

PREBUILT=$(locate_depot_files //device/prebuilt/$OS)

# find the GNU Make program
is_gnu_make ()
{
    version=$($1 -v | grep GNU)
    if test -n "$version"; then
        echo "$1"
    else
        echo ""
    fi
}

if test -z "$GNUMAKE"; then
    GNUMAKE=`which make` && GNUMAKE=$(is_gnu_make $GNUMAKE)
fi

if test -z "$GNUMAKE"; then
    GNUMAKE=`which gmake` && GNUMAKE=$(is_gnu_make $GNUMAKE)
fi

if test -z "$GNUMAKE"; then
    echo "could not find GNU Make on this machine. please define GNUMAKE to point to it"
    exit 3
fi

TEST=$(is_gnu_make $GNUMAKE)
if test -z "$TEST"; then
    echo "it seems that '$GNUMAKE' is not a working GNU Make binary. please check the definition of GNUMAKE"
    exit 3
fi

# ensure we have a recent audio library built
#
#echo "GNUMAKE is $GNUMAKE"
source=arm-softmmu/libqemu-audio.a
$GNUMAKE $source || (echo "could not build the audio library. Aborting" && exit 1)

# now do a p4 edit, a copy and ask for submission
#
TARGET=$PREBUILT/qemu/libqemu-audio.a

p4 edit $TARGET || (echo "could not p4 edit $TARGET" && exit 3)
cp -f $source $TARGET
echo "please do: p4 submit $TARGET"

