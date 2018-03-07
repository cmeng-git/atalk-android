#!/bin/bash
set -u
source ./build_settings.sh

# arch64-bit built need api-21
export PLATFORM="android-21"
SYSROOT=$NDK/platforms/$PLATFORM/arch-x86_64/
TOOLCHAIN=$NDK/toolchains/x86_64-4.9/prebuilt/linux-x86_64

function build_target
{
./configure \
    --prefix=$PREFIX \
    --enable-static \
    --enable-pic \
    --disable-asm \
    --disable-cli \
    --disable-opencl \
    --cross-prefix=$CROSS_PREFIX \
    --sysroot=$SYSROOT \
    --host=x86_64-linux

make clean
make -j4
make install
}

export CPU=x86_64
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/x86_64-linux-android-

build_target

cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

echo "=== Android $CPU builds completed ==="
