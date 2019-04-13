#!/bin/bash
# https://software.intel.com/en-us/android/blogs/2013/12/06/building-ffmpeg-for-android-on-x86
. ffmpeg-x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-x86
TOOLCHAIN=${ANDROID_NDK}/toolchains/x86-4.9/prebuilt/linux-x86_64
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
  --disable-asm \
  --arch=x86 \
  --cpu=i686 \
  --disable-amd3dnow \
  --disable-amd3dnowext \
  --extra-cflags="-std=c99 -O3 -Wall -fPIC -pipe -DANDROID -DNDEBUG -march=atom -msse3 -ffast-math -mfpmath=sse -I${PREFIX}/include/x264" \
  --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack ${ADDI_LDFLAGS} -L${PREFIX}/lib"

make -j${HOST_NUM_CORES} install
}

export ABI=x86
PREFIX=../android/${ABI}
CROSS_PREFIX=${TOOLCHAIN}/bin/i686-linux-android-

pushd ffmpeg
build_target
popd
echo -e "*** Android ffmpeg for ${ABI} builds completed ***\n\n"

# must include  --disable-asm otherwise aTalk build has problem:
# ./i686-linux-android/bin/ld: warning: shared library text segment is not shareable