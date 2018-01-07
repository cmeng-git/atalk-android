#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-x86_64/
TOOLCHAIN=$NDK/toolchains/x86_64-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_one
{
./configure \
    --prefix=$PREFIX \
	$COMMON $CONFIGURATION \
	--cross-prefix=$TOOLCHAIN/bin/x86_64-linux-android- \
	--target-os=linux \
	--arch=x86_64 \
	--disable-asm \
	--sysroot=$SYSROOT \
	--extra-cflags="-O3 -Wall -pipe -DANDROID -DNDEBUG  -march=atom -msse3 -ffast-math -mfpmath=sse $ADDI_CFLAGS" \
	--extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack $ADDI_LDFLAGS"

make clean
make -j4
make install
}

export CPU=x86_64
PREFIX=./android/$CPU 
build_one
# cp Android.mk $PREFIX/Android.mk
cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR
