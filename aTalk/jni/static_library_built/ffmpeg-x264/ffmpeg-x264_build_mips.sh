#!/bin/bash
. ffmpeg-x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-mips
TOOLCHAIN=${ANDROID_NDK}/toolchains/mipsel-linux-android-4.9/prebuilt/linux-x86_64
rm -f $(pwd)/compat/strtod.o

function build_target
{
make clean

./configure \
  ${COMMON} ${CONFIGURATION} \
  --prefix=${PREFIX} \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${SYSROOT} \
  --pkg-config=${BASEDIR}/ffmpeg-pkg-config \
  --nm=${CROSS_PREFIX}nm \
  --cc=${CROSS_PREFIX}gcc \
  --extra-libs="-lgcc" \
  --arch=mips \
  --disable-mipsdspr1 --disable-mipsdspr2 --disable-mipsfpu \
  --extra-cflags="-Os -fPIC -I${PREFIX}/include/x264" \
  --extra-ldflags="${ADDI_LDFLAGS} -L${PREFIX}/lib"

make -j${HOST_NUM_CORES} install
}

export ABI=mips
PREFIX=../android/${ABI}
CROSS_PREFIX=${TOOLCHAIN}/bin/mipsel-linux-android-

pushd ffmpeg
build_target
popd
echo -e "*** Android ffmpeg for ${ABI} builds completed ***\n\n"
