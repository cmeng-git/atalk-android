#!/bin/bash
set -u
source ./build_settings.sh

export PLATFORM="android-15"
SYSROOT=$NDK/platforms/$PLATFORM/arch-x86/
TOOLCHAIN=$NDK/toolchains/x86-4.9/prebuilt/linux-x86_64
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
  --arch=x86 \
  --cpu=i686 \
  --enable-yasm \
  --disable-amd3dnow \
  --disable-amd3dnowext \
  --extra-cflags="-std=c99 -O3 -Wall -fpic -pipe -DANDROID -DNDEBUG -march=atom -msse3 -ffast-math -mfpmath=sse $ADDI_CFLAGS -I../x264/android/$CPU/include" \
  --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack $ADDI_LDFLAGS -L../x264/android/$CPU/lib"

make clean
make -j4
make install
}

export CPU=x86
PREFIX=./android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/i686-linux-android-

# export CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang -target armv7-none-linux-androideabi -gcc-toolchain $TOOLCHAIN"
# export CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ -target armv7-none-linux-androideabi -gcc-toolchain $TOOLCHAIN"
build_target

cd $PROJECT_JNI
export ABI=$CPU
# $NDK/ndk-build
# cp -r "$PROJECT_LIBS/$CPU" "$PROJECT_LIBS/../out" 
# cd $DIR

echo "=== Android ffmpeg for $CPU builds completed ==="
