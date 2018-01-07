#!/bin/bash
export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-arm/
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64

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
    --host=arm-linux

make clean
make -j4
make install
}

export CPU=armeabi-v7a
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/arm-linux-androideabi-

#export CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang -target armv7-none-linux-androideabi -gcc-toolchain $TOOLCHAIN"
#export CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ -target armv7-none-linux-androideabi -gcc-toolchain $TOOLCHAIN"

build_target

# duplicate built library etc for armeabi
cp -r "$PREFIX" "./android/armeabi" 


cd $PROJECT_JNI
export ABI=$CPU

# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

echo "=== Android $CPU builds completed ==="
