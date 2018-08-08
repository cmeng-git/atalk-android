#!/bin/bash
# https://software.intel.com/en-us/android/blogs/2013/12/06/building-ffmpeg-for-android-on-x86
. ffmpeg-x264_build_settings.sh

export PLATFORM="android-15"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-x86
TOOLCHAIN=$ANDROID_NDK/toolchains/x86-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_target
{
./configure \
  $COMMON $CONFIGURATION \
  --prefix=$PREFIX \
  --cross-prefix=$CROSS_PREFIX \
  --sysroot=$SYSROOT \
  --nm=${CROSS_PREFIX}nm \
  --cc=${CROSS_PREFIX}gcc \
  --extra-libs="-lgcc" \
  --target-os=linux \
  --arch=x86 \
  --cpu=i686 \
  --enable-yasm \
  --disable-amd3dnow \
  --disable-amd3dnowext \
  --extra-cflags="-std=c99 -O3 -Wall -fPIC -pipe -DANDROID -DNDEBUG -march=atom -msse3 -ffast-math -mfpmath=sse -I../android/$CPU/include/x264" \
  --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack $ADDI_LDFLAGS -L../android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=x86
PREFIX=../android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/i686-linux-android-

pushd ffmpeg
build_target
popd
echo "=== Android ffmpeg for $CPU builds completed ==="
