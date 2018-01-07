#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-mips/
TOOLCHAIN=$NDK/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_one
{
./configure \
    --prefix=$PREFIX \
    $COMMON $CONFIGURATION \
    --cross-prefix=$TOOLCHAIN/bin/mipsel-linux-android- \
    --target-os=linux \
    --arch=mips \
    --sysroot=$SYSROOT \
    --extra-cflags="-Os -fpic $ADDI_CFLAGS" --extra-ldflags="$ADDI_LDFLAGS"

make clean
make -j4
make install
}

export CPU=mips
PREFIX=./android/$CPU 
build_one
# cp Android.mk $PREFIX/Android.mk
cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR
