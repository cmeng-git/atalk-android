#!/bin/bash
. x264_build_settings.sh

# arch64-bit built need api-21
export PLATFORM="android-21"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-x86_64
TOOLCHAIN=$ANDROID_NDK/toolchains/x86_64-4.9/prebuilt/linux-x86_64

function build_target
{
./configure \
  $COMMON \
  --prefix=$PREFIX \
  --includedir=$PREFIX/include/x264 \
  --cross-prefix=$CROSS_PREFIX \
  --sysroot=$SYSROOT \
  --host=x86_64-linux \
  --disable-thread

make clean
make -j4
make install
}

export CPU=x86_64
PREFIX=../android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/x86_64-linux-android-

pushd x264
build_target
popd

pushd ./android/$CPU/lib
mv libx264.so.$X264_VERSION libx264_$X264_VERSION.so
sed -i 's/libx264.so.147/libx264_147.so/g' libx264_$X264_VERSION.so
rm libx264.so
ln -f -s libx264_$X264_VERSION.so libx264.so
popd

echo "=== Android $CPU builds completed ==="
