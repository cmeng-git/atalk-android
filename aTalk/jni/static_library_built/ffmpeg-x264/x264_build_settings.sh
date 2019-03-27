#!/bin/bash
# set -x
if [ "$ANDROID_NDK" = "" ]; then
	echo ANDROID_NDK variable not set, exiting
	echo "Use: export ANDROID_NDK=/your/path/to/android-ndk-r15c"
	exit 1
fi

# "/opt/android/android-sdk/ndk-bundle" - not working anymore due to unified-Headers
# "/opt/android/android-ndk-r15c" - use this instead

# arch64-bit built need api-21
export PLATFORM="android-21"
HOST_NUM_CORES=$(nproc)

set -u
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export LDFLAGS="-Wl,-z,defs -Wl,--unresolved-symbols=report-all"

PARENT="$(dirname "${DIR}")"

X264_API="$(grep '#define X264_BUILD' < ./x264/x264.h | sed 's/^.* \([1-9][0-9]*\).*$/\1/')"

COMMON="\
  --enable-static \
  --enable-shared \
  --enable-pic \
  --enable-strip \
  --disable-thread \
  --disable-cli \
  --disable-opencl"

# Required for arch 64-bit build –disable-thread
# Needed because linker for arm64 does not support pthreads command passed by FFmpeg and it produces broken libraries

function update_x264.so
{
if [[ -f libx264.so.${X264_API} ]]; then
  mv libx264.so.${X264_API} libx264_${X264_API}.so
  sed -i "s/libx264.so.${X264_API}/libx264_${X264_API}.so/g" libx264_${X264_API}.so
  rm libx264.so
  ln -f -s libx264_${X264_API}.so libx264.so
fi
}