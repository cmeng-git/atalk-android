#!/bin/bash
. ffmpeg-x264_build_settings.sh

export PLATFORM="android-15"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-arm
TOOLCHAIN=$ANDROID_NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
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
  --arch=arm \
  --cpu=armv7-a \
  --enable-neon \
  --extra-cflags="-Os -march=armv7-a -mfloat-abi=softfp -fPIC -DANDROID-marm -mtune=cortex-a8 -I../android/$CPU/include/x264" \
  --extra-ldflags="$ADDI_LDFLAGS -L../android/$CPU/lib"

# do not include "-mfpu=neon" option, it causes libavcodec to contain text relocation and hence rejected by android API-23 

make clean
make -j4
make install
}

export CPU=armeabi-v7a
PREFIX=../android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/arm-linux-androideabi-

pushd ffmpeg
build_target
popd
echo "=== Android ffmpeg for $CPU builds completed ==="
