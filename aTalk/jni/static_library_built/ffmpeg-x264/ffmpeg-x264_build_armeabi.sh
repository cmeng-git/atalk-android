#!/bin/bash
. ffmpeg-x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-arm
TOOLCHAIN=${ANDROID_NDK}/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_target
{
make clean

./configure \
  ${COMMON} ${CONFIGURATION} \
  --prefix=${PREFIX} \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${SYSROOT} \
  --nm=${CROSS_PREFIX}nm \
  --cc=${CROSS_PREFIX}gcc \
  --extra-libs="-lgcc" \
  --target-os=linux \
  --arch=arm \
  --cpu=armv5te \
  --disable-asm \
  --disable-stripping \
  --extra-cflags="-O3 -Wall -fPIC -pipe -std=c99 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -DANDROID -DNDEBUG-march=armv5te -mtune=arm9tdmi -msoft-float -I${PREFIX}/include/x264" \
  --extra-ldflags="${ADDI_LDFLAGS} -L${PREFIX}/lib -lx264"

make -j${HOST_NUM_CORES} install
}

export CPU=armeabi
PREFIX=../android/${CPU}
CROSS_PREFIX=${TOOLCHAIN}/bin/arm-linux-androideabi-

pushd ffmpeg
build_target
popd
echo -e "*** Android ffmpeg for ${CPU} builds completed ***\n\n"
