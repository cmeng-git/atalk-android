#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-arm/
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
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
    --arch=arm \
    --cpu=armv7-a \
    --enable-neon \
    --extra-cflags="-Os -march=armv7-a -mfloat-abi=softfp -fPIC -DANDROID -marm $ADDI_CFLAGS -I../x264/android/$CPU/include" \
    --extra-ldflags="$ADDI_LDFLAGS -L../x264/android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=armeabi-v7a
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/arm-linux-androideabi-
build_target

cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

echo "=== Android ffmpeg for $CPU builds completed ==="
