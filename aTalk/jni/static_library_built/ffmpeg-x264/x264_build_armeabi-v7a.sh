#!/bin/bash
. x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-arm/
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
  --host=arm-linux \
  --extra-cflags="-Os -march=armv7-a -mfloat-abi=softfp -fPIC -DANDROID -mfpu=neon -marm -mtune=cortex-a8"

make -j${HOST_NUM_CORES} install
}

export ABI=armeabi-v7a
PREFIX=../android/${ABI}
CROSS_PREFIX=${TOOLCHAIN}/bin/arm-linux-androideabi-

#export CC="${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang -target armv7-none-linux-androideabi -gcc-toolchain ${TOOLCHAIN}"
#export CXX="${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ -target armv7-none-linux-androideabi -gcc-toolchain ${TOOLCHAIN}"

pushd x264
build_target

pushd ${PREFIX}/lib
update_x264.so
popd

echo -e "*** Android x264-${X264_API} for ${ABI} builds completed ***\n\n"
