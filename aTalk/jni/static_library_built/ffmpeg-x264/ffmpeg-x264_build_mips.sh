#!/bin/bash
. ffmpeg-x264_build_settings.sh

export PLATFORM="android-15"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-mips
TOOLCHAIN=$ANDROID_NDK/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64
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
  --arch=mips \
  --disable-mipsdspr1 --disable-mipsdspr2 --disable-mipsfpu \
  --extra-cflags="-Os -fPIC -I../android/$CPU/include/x264" \
  --extra-ldflags="$ADDI_LDFLAGS -L../android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=mips
PREFIX=../android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/mipsel-linux-android-

pushd ffmpeg
build_target
popd
echo "=== Android ffmpeg for $CPU builds completed ==="
