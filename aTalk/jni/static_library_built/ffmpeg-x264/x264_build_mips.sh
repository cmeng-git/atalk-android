#!/bin/bash
. x264_build_settings.sh

export PLATFORM="android-15"
SYSROOT=$ANDROID_NDK/platforms/$PLATFORM/arch-mips
TOOLCHAIN=$ANDROID_NDK/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64

function build_target
{
./configure \
  $COMMON \
  --prefix=$PREFIX \
  --includedir=$PREFIX/include/x264 \
  --cross-prefix=$CROSS_PREFIX \
  --sysroot=$SYSROOT \
  --host=mipsel-linux

make clean
make -j4
make install
}

export CPU=mips
PREFIX=../android/$CPU 
CROSS_PREFIX=$TOOLCHAIN/bin/mipsel-linux-android-

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
