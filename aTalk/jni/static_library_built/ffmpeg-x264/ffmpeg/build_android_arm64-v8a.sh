#!/bin/bash
source ./build_settings.sh

export PLATFORM="android-15"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-arm64/
TOOLCHAIN=$ANDROID_NDK/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64

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
  --arch=aarch64 \
  --extra-cflags="-O3 -DANDROID -Dipv6mr_interface=ipv6mr_ifindex -fasm -Wno-psabi -fno-short-enums -fno-strict-aliasing $ADDI_CFLAGS -I../x264/android/$CPU/include" \
  --extra-ldflags="-Wl,-rpath-link=$SYSROOT/usr/lib -L$SYSROOT/usr/lib -nostdlib -lc -lm -ldl -llog $ADDI_LDFLAGS -L../x264/android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=arm64-v8a
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/arm-linux-androideabi-

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
