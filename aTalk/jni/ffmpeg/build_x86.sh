#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-x86/
TOOLCHAIN=$NDK/toolchains/x86-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_one
{
./configure \
    --prefix=$PREFIX \
    $COMMON $CONFIGURATION \
    --cross-prefix=$TOOLCHAIN/bin/i686-linux-android- \
    --target-os=linux \
    --arch=x86 \
    --cpu=i686 \
    --enable-yasm \
    --enable-pic \
    --disable-amd3dnow \
    --disable-amd3dnowext \
    --sysroot=$SYSROOT \
    --extra-cflags="-std=c99 -O3 -Wall -fpic -pipe -DANDROID -DNDEBUG -march=atom -msse3 -ffast-math -mfpmath=sse $ADDI_CFLAGS" \
    --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack $ADDI_LDFLAGS"

make clean
make -j4
make install
}

export CPU=x86
PREFIX=./android/$CPU 
build_one
# cp Android.mk $PREFIX/Android.mk
cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR
