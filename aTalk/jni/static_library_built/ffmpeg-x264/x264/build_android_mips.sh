#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-mips/
TOOLCHAIN=$NDK/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64

function build_target
{
./configure \
    --prefix=$PREFIX \
    --enable-static \
    --enable-pic \
    --disable-cli \
    --disable-opencl \
    --cross-prefix=$CROSS_PREFIX \
    --sysroot=$SYSROOT \
    --host=mipsel-linux

make clean
make -j4
make install
}

export CPU=mips
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/mipsel-linux-android-

build_target

cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

echo "=== Android $CPU builds completed ==="
