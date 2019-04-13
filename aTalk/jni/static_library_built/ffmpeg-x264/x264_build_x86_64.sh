#!/bin/bash
. x264_build_settings.sh

SYSROOT=${ANDROID_NDK}/platforms/${PLATFORM}/arch-x86_64
TOOLCHAIN=${ANDROID_NDK}/toolchains/x86_64-4.9/prebuilt/linux-x86_64

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
  --host=x86_64-linux

make -j${HOST_NUM_CORES} install
}

export ABI=x86_64
PREFIX=../android/${ABI}
CROSS_PREFIX=${TOOLCHAIN}/bin/x86_64-linux-android-

pushd x264
build_target

pushd ${PREFIX}/lib
update_x264.so
popd

echo -e "*** Android x264-${X264_API} for ${ABI} builds completed ***\n\n"

# Must have --disable-asm for x86 and x64_86 ABIS; otherwise problem in aTalk libjnffmpeg.so build (even with --enable-pic) i.e.

#/i686-linux-android/bin/ld: warning: shared library text segment is not shareable
# error for x264-157; similarly for x264-152
#./x86_64-linux-android/bin/ld: error: ffmpeg/android/x86_64/lib/libx264.a(cabac-a-8.o): requires dynamic R_X86_64_PC32 reloc against 'x264_cabac_range_lps' which may overflow at runtime; recompile with -fPIC
#./x86_64-linux-android/bin/ld: error: ffmpeg/android/x86_64/lib/libx264.a(quant-a-8.o): requires dynamic R_X86_64_PC32 reloc against 'x264_decimate_table4' which may overflow at runtime; recompile with -fPIC
#./x86_64-linux-android/bin/ld: error: ffmpeg/android/x86_64/lib/libx264.a(cabac-a-10.o): requires dynamic R_X86_64_PC32 reloc against 'x264_cabac_range_lps' which may overflow at runtime; recompile with -fPIC
#./x86_64-linux-android/bin/ld: error: ffmpeg/android/x86_64/lib/libx264.a(quant-a-10.o): requires dynamic R_X86_64_PC32 reloc against 'x264_decimate_table4' which may overflow at runtime; recompile with -fPIC
#./x86_64-linux-android/bin/ld: error: ffmpeg/android/x86_64/lib/libx264.a(trellis-64-8.o): requires dynamic R_X86_64_PC32 reloc against 'x264_cabac_entropy' which may overflow at runtime; recompile with -fPIC
#./x86_64-linux-android/bin/ld: error: ffmpeg/android/x86_64/lib/libx264.a(trellis-64-10.o): requires dynamic R_X86_64_PC32 reloc against 'x264_cabac_entropy' which may overflow at runtime; recompile with -fPIC
#./x86_64-linux-android/bin/ld: warning: shared library text segment is not shareable
