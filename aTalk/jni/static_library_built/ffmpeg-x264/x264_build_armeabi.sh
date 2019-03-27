#!/bin/bash
. x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-arm
TOOLCHAIN=${ANDROID_NDK}/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64

function build_target
{
make clean

./configure \
  ${COMMON} \
  --prefix=${PREFIX} \
  --includedir=${PREFIX}/include/x264 \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${SYSROOT} \
  --host=arm-linux

make -j${HOST_NUM_CORES} install
}

export CPU=armeabi
PREFIX=../android/${CPU}
CROSS_PREFIX=${TOOLCHAIN}/bin/arm-linux-androideabi-

pushd x264
build_target

pushd ${PREFIX}/lib
update_x264.so
popd

echo -e "*** Android x264-${X264_API} for ${CPU} builds completed ***\n\n"