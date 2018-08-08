#!/bin/bash
set -x
if [ "$ANDROID_NDK" = "" ]; then
	echo ANDROID_NDK variable not set, exiting
	echo "Use: export ANDROID_NDK=/your/path/to/android-ndk-r15c"
	exit 1
fi

# "/opt/android/android-sdk/ndk-bundle" - not working anymore due to unified-Headers
# "/opt/android/android-ndk-r15c" - use this instead

set -u
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT="$(dirname "${DIR}")"

export PATH="$PATH:$ANDROID_NDK"
export ADDI_LDFLAGS="-Wl,-z,defs"
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
  --disable-ffplay \
  --disable-ffprobe \
  --disable-ffserver \
  --enable-protocol=file \
  --disable-symver \
  --disable-encoder=ac3 --disable-decoder=ac3 \
  --disable-runtime-cpudetect \
  --enable-libx264 \
  --enable-gpl" 


