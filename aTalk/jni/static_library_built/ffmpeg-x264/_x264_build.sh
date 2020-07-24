#!/bin/bash
. _settings.sh $*

pushd x264
X264_API="$(grep '#define X264_BUILD' < x264.h | sed 's/^.* \([1-9][0-9]*\).*$/\1/')"
echo -e "\n\n** BUILD STARTED: x264-v${X264_API} for ${1} **"

# --disable-asm disable
# Must not include the option for arm64-v8a.
# The option is used by configure, config.mak and Makefile files to define AS and to compile required *.S assembly files;
# Otherwise will have undefined references e.g. x264_8_pixel_sad_16x16_neon if --disable-asm is specified
# However must include the option for x86 and x86_64;
# Otherwise have relocate text, requires dynamic R_X86_64_PC32 etc when use in aTalk

# for ndk-r16b and above must have
# --extra-cflags="-isystem ${NDK_SYSROOT}/usr/include/${NDK_ABIARCH} -isystem ${NDK_SYSROOT}/usr/include"

# Must include --disable-asm for x86 and x64_86 ABIS; otherwise problem in aTalk libjnffmpeg.so build i.e.
# x86: ./i686-linux-android/bin/ld: warning: shared library text segment is not shareable
# x86_64: e.g libswresample, libswscale, libavcodec:  requires dynamic R_X86_64_PC32 reloc against ...
DISASM=""
if [[ $1 =~ x86.* ]]; then
   DISASM="--disable-asm"
fi

make clean

./configure \
  --prefix=${PREFIX} \
  --includedir=${PREFIX}/include/x264 \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${NDK_SYSROOT} \
  --host=${HOST} \
  --extra-cflags="-isystem ${NDK_SYSROOT}/usr/include/${NDK_ABIARCH} -isystem ${NDK_SYSROOT}/usr/include" \
  ${DISASM} \
  --enable-static \
  --enable-pic \
  --enable-strip \
  --disable-thread \
  --disable-opencl \
  --disable-cli || exit 1

make -j${HOST_NUM_CORES} install || exit 1

pushd ${PREFIX}/lib
if [[ -f libx264.so.$X264_API ]]; then
  mv libx264.so.${X264_API} libx264_${X264_API}.so
  sed -i "s/libx264.so.${X264_API}/libx264_${X264_API}.so/g" libx264_${X264_API}.so
  rm libx264.so
  ln -f -s libx264_${X264_API}.so libx264.so
fi
popd

echo -e "** BUILD COMPLETED: x264-v${X264_API} for ${1} **\n"
