#!/bin/bash

NDK=/opt/android/android-sdk/ndk-bundle
NDK_ABI=arm

# SYSROOT=$NDK/platforms/android-24/arch-arm/
SYSROOT=$NDK/platforms/android-24/arch-$NDK_ABI/

NDK_UNAME=`uname -s | tr '[A-Z]' '[a-z]'` # Convert Linux -> linux
HOST=$NDK_ABI-linux-androideabi

# TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
TOOLCHAIN=$NDK/toolchains/$HOST-4.9/prebuilt/$NDK_UNAME-x86_64

CC="$TOOLCHAIN/bin/$HOST-gcc --sysroot=$SYSROOT"
LD=$TOOLCHAIN/bin/$HOST-ld
CPU=armeabi-v7a
PREFIX=./build/android/$CPU 

BUILD_PATH=build/ffmpeg

./configure \
    $DEBUG_FLAG \
    --prefix=$PREFIX \
    --arch=arm \
    --target-os=linux \
    --enable-runtime-cpudetect \
    --enable-pic \
    --enable-ffmpeg \
    --disable-shared \
    --enable-static \
    --disable-doc \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-ffserver \
    --disable-network \
    --disable-avdevice \
    --disable-symver \
    --cross-prefix=$TOOLCHAIN/bin/$NDK_ABI-linux-androideabi- \
    --enable-cross-compile \
    --sysroot=$SYSROOT \
    --extra-cflags="-march=armv7-a -mfloat-abi=softfp -fPIC -DANDROID" \
    --extra-ldflags="" \
    $ADDITIONAL_CONFIGURE_FLAG
make clean
make -j4
make install
