#!/bin/bash
. ffmpeg-x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-x86_64
TOOLCHAIN=${ANDROID_NDK}/toolchains/x86_64-4.9/prebuilt/linux-x86_64
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
  --arch=x86_64 \
  --disable-asm \
  --extra-cflags="-O3 -Wall -pipe -fPIC -DANDROID -DNDEBUG  -march=atom -msse3 -ffast-math -mfpmath=sse -I${PREFIX}/include/x264" \
  --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack ${ADDI_LDFLAGS} -L${PREFIX}/lib -lx264"

make -j${HOST_NUM_CORES} install
}

export CPU=x86_64
PREFIX=../android/${CPU}
CROSS_PREFIX=${TOOLCHAIN}/bin/x86_64-linux-android-

pushd ffmpeg
build_target
popd
echo -e "*** Android ffmpeg for ${CPU} builds completed ***\n\n"
