#!/bin/bash
. x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-mips
TOOLCHAIN=${ANDROID_NDK}/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64

function build_target
{
make clean

./configure \
  ${COMMON} \
  --prefix=${PREFIX} \
  --includedir=${PREFIX}/include/x264 \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${SYSROOT} \
  --host=mipsel-linux

make -j${HOST_NUM_CORES} install
}

export CPU=mips
PREFIX=../android/${CPU}
CROSS_PREFIX=${TOOLCHAIN}/bin/mipsel-linux-android-

pushd x264
build_target

pushd ${PREFIX}/lib
update_x264.so
popd

echo -e "*** Android x264-${X264_API} for ${CPU} builds completed ***\n\n"
