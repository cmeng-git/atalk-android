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

X264_VERSION=147

COMMON="\
  --enable-static \
  --enable-shared \
  --enable-pic \
  --enable-strip \
  --disable-thread \
  --disable-asm \
  --disable-gpl \
  --disable-cli \
  --disable-opencl" 
