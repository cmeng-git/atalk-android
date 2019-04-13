#!/bin/bash
. x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-x86
TOOLCHAIN=${ANDROID_NDK}/toolchains/x86-4.9/prebuilt/linux-x86_64

function build_target
{
make clean

./configure \
  ${COMMON} \
  --prefix=${PREFIX} \
  --includedir=${PREFIX}/include/x264 \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${SYSROOT} \
  --disable-asm \
  --host=i686-linux

make -j${HOST_NUM_CORES} install
}

export ABI=x86
PREFIX=../android/${ABI}
CROSS_PREFIX=${TOOLCHAIN}/bin/i686-linux-android-

pushd x264
build_target

pushd ${PREFIX}/lib
update_x264.so
popd

echo -e "*** Android x264-${X264_API} for ${ABI} builds completed ***\n\n"
