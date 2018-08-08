#!/bin/bash
. ffmpeg-x264_build_settings.sh

export PLATFORM="android-21"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-x86_64
TOOLCHAIN=$ANDROID_NDK/toolchains/x86_64-4.9/prebuilt/linux-x86_64
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
  --arch=x86_64 \
  --disable-asm \
  --extra-cflags="-O3 -Wall -pipe -fPIC -DANDROID -DNDEBUG  -march=atom -msse3 -ffast-math -mfpmath=sse -I../android/$CPU/include/x264" \
  --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack $ADDI_LDFLAGS -L../android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=x86_64
PREFIX=../android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/x86_64-linux-android-

pushd ffmpeg
build_target
popd
echo "=== Android ffmpeg for $CPU builds completed ==="
