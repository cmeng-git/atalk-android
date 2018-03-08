#!/bin/bash
source ./build_settings.sh

export PLATFORM="android-15"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-mips/
TOOLCHAIN=$ANDROID_NDK/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_target
{
./configure \
  $COMMON $CONFIGURATION \
  --prefix=$PREFIX \
  --cross-prefix=$CROSS_PREFIX \
  --nm=${CROSS_PREFIX}nm \
  --sysroot=$SYSROOT \
  --cc=${CROSS_PREFIX}gcc \
  --extra-libs="-lgcc" \
  --target-os=linux \
  --arch=mips \
  --disable-mipsdspr1 --disable-mipsdspr2 --disable-mipsfpu \
  --extra-cflags="-Os -fpic $ADDI_CFLAGS" --extra-ldflags="$ADDI_LDFLAGS -I../x264/android/$CPU/include" \
  --extra-ldflags="$ADDI_LDFLAGS -L../x264/android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=mips
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/mipsel-linux-android-

pushd ffmpeg
build_target

# Use AS NDK to build
# cd $PROJECT_JNI
# export ABI=$CPU
# $ANDROID_NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

popd
echo "=== Android ffmpeg for $CPU builds completed ==="
