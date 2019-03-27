#!/bin/bash
# set -x
if [ "${ANDROID_NDK}" = "" ]; then
	echo ANDROID_NDK variable not set, exiting
	echo "Use: export ANDROID_NDK=/your/path/to/android-ndk-r15c"
	exit 1
fi

# "/opt/android/android-sdk/ndk-bundle" - not working anymore due to unified-Headers
# "/opt/android/android-ndk-r15c" - use this instead

# arch64-bit built need api-21
export PLATFORM="android-21"
HOST_NUM_CORES=$(nproc)
BASEDIR=`pwd`

set -u
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT="$(dirname "${DIR}")"

export PATH="$PATH:${ANDROID_NDK}"
# export ADDI_LDFLAGS="-Wl,-z,defs -Wl,--unresolved-symbols=ignore-in-shared-libs"
export ADDI_LDFLAGS="-Wl,-z,defs -Wl,--unresolved-symbols=report-all"

# export PKG_CONFIG_PATH=/home/lab223/libpng-1.6.9/_install/lib/pkgconfig

# CONFIGURATION="pass here additional configuration flags if you want to"
CONFIGURATION=""

COMMON="\
  --enable-static \
  --enable-shared \
  --enable-cross-compile \
  --enable-pic \
  --disable-doc \
  --disable-debug \
  --disable-postproc \
  --disable-ffmpeg \
  --disable-ffplay \
  --disable-ffprobe \
  --disable-ffserver \
  --enable-protocol=file \
  --disable-symver \
  --disable-encoder=ac3 \
  --disable-decoder=ac3 \
  --disable-runtime-cpudetect \
  --enable-libx264 \
  --enable-gpl"


# Undefined reference in x264 when linked with ffmpeg for arm64-v8a
# https://stackoverflow.com/questions/5555632/can-gcc-not-complain-about-undefined-references
#
#It is possible to avoid reporting undefined references - using --unresolved-symbols linker option.
#
#g++ mm.cpp -Wl,--unresolved-symbols=ignore-in-object-files
#From man ld
#
#--unresolved-symbols=method
#Determine how to handle unresolved symbols. There are four possible values for method:
#
#       ignore-all
#           Do not report any unresolved symbols.
#
#       report-all
#           Report all unresolved symbols.  This is the default.
#
#       ignore-in-object-files
#           Report unresolved symbols that are contained in shared
#           libraries, but ignore them if they come from regular object
#           files.
#
#       ignore-in-shared-libs
#           Report unresolved symbols that come from regular object
#           files, but ignore them if they come from shared libraries.  This
#           can be useful when creating a dynamic binary and it is known
#           that all the shared libraries that it should be referencing
#           are included on the linker's command line.
#The behaviour for shared libraries on their own can also be controlled by the --[no-]allow-shlib-undefined option.
#
#Normally the linker will generate an error message for each reported unresolved symbol but the option --warn-unresolved-symbols can change this to a warning.