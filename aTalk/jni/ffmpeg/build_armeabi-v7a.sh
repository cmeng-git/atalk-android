#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-arm/
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_one
{
./configure \
    --prefix=$PREFIX \
    $COMMON $CONFIGURATION \
    --cross-prefix=$TOOLCHAIN/bin/arm-linux-androideabi- \
    --target-os=linux \
    --cpu=armv7-a \
    --arch=arm \
    --enable-cross-compile \
    --sysroot=$SYSROOT \
    --extra-cflags="-Os -march=armv7-a -mfloat-abi=softfp -fPIC -DANDROID -marm $ADDI_CFLAGS" \
    --extra-ldflags="$ADDI_LDFLAGS"

make clean
make -j4
make install
}

export CPU=armeabi-v7a
PREFIX=./android/$CPU 
build_one
# cp Android.mk $PREFIX/Android.mk
cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

