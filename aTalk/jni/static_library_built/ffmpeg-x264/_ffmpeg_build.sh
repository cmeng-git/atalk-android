#!/bin/bash
#
# Copyright 2016 Eng Chong Meng
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# set -x

# https://gcc.gnu.org/onlinedocs/gcc-4.9.4/gcc/Link-Options.html#Link-Options
# Data relocation and protection (RELRO): LDLFAGS="-z relro -z now"
# i686-linux-android-ld: -Wl,-z,relro -Wl,-z,now: unknown options
# lame checks stdlib for linkage! omit -nostdlib

# LDFLAGS='-pie -fuse-ld=gold -Wl,-z,relro -Wl,-z,now -nostdlib -lc -lm -ldl -llog'
# -fuse-ld=gold: use ld.gold linker but unavailable for ABI mips and mips64
# LDFLAGS='-pie -lc -lm -ldl -llog'

. _settings.sh "$@"

LIB_FFMPEG=ffmpeg

pushd ${LIB_FFMPEG} || return
VERSION=$(cat RELEASE)

echo -e "\n\n** BUILD STARTED: ${LIB_FFMPEG}-v${VERSION} for ${1} **"

# Must include option --disable-asm; otherwise ffmpeg-3.4.6 has problem and crash system:
# armeabi-v7a: A/libc: Fatal signal 7 (SIGBUS), code 1, fault addr 0x9335c00c in tid 20032 (Loop thread: ne)
# x86: ./i686-linux-android/bin/ld: warning: shared library text segment is not shareable
# x86_64: e.g libswresample, libswscale, libavcodec:  requires dynamic R_X86_64_PC32 reloc against ...
# libavcodec/x86/cabac.h:193:9: error: inline assembly requires more registers than available
# TA_OPTIONS="--disable-ffserver --disable-asm"

# Note: Use the following option for ffmpeg 4.0+ build
# Not valid for ffmpeg 4.0+: Removed the ffserver program
#  --disable-ffserver \
# Must include option --disable-asm for v4.1.6+ for x86 and x86_64 build;
# libavcodec/x86/cabac.h:193:9: error: inline assembly requires more registers than available
TA_OPTIONS=""

case $1 in
  armeabi-v7a)
    LDFLAGS="${LDFLAGS} -Wl,-z,relro -Wl,-z,now -Wl,--fix-cortex-a8"
  ;;
  arm64-v8a)
    #  -Wl,--unresolved-symbols=ignore-in-shared-libs fixes x264 undefined references for arm64-v8a
    LDFLAGS="${LDFLAGS} -Wl,-z,relro -Wl,-z,now"
  ;;
  x86)
    # required for x86 in ffmpeg v4.0+ (see note above)
    TA_OPTIONS="--disable-asm"
    LDFLAGS="${LDFLAGS}"
  ;;
  x86_64)
    TA_OPTIONS="--disable-asm"
    LDFLAGS="${LDFLAGS}"
  ;;
esac
# export LDFLAGS="-Wl,-rpath-link=${NDK_SYSROOT}/usr/lib -L${NDK_SYSROOT}/usr/lib ${LDFLAGS}"

INCLUDES=""
LIBS=""
MODULES=""

for m in "$@"
  do
    [[ -d ${PREFIX}/lib/pkgconfig ]] || mkdir -p ${PREFIX}/lib/pkgconfig

    case $m in
      x264)
        INCLUDES="${INCLUDES} -I${PREFIX}/include/$m"
        LIBS="${LIBS} -L${PREFIX}/lib"
        # cp -r ${PREFIX}/lib/pkgconfig ${PREFIX}/lib
        MODULES="${MODULES} --enable-libx264 --enable-version3"
      ;;
      h264)
        INCLUDES="${INCLUDES} -I${PREFIX}/include"
        LIBS="${LIBS} -L${PREFIX}/lib"
        # cp -r ${PREFIX}/lib/pkgconfig ${PREFIX}/lib
        MODULES="${MODULES} --enable-libopenh264"
      ;;
      lame)
        INCLUDES="${INCLUDES} -I${PREFIX}/include"
        LIBS="${LIBS} -L${PREFIX}/lib"
        # cp -r ${PREFIX}/lib/pkgconfig ${PREFIX}/lib/pkgconfig
        MODULES="${MODULES} --enable-libmp3lame"
      ;;
    esac
 done


# --enable-gpl required for libpostproc build
# --disable-postproc: https://trac.ffmpeg.org/wiki/Postprocessing
# Anyway, most of the time it won't help to postprocess h.264, HEVC, VP8, or VP9 video.

# FFmpeg must be compiled with the --enable-libvpx option for VP9 to work (but seems not helping in aTalk vp9 encode/decode)
# see https://developers.google.com/media/vp9/get-started
# see ffmpeg/doc/general.texi etc for more info

# -lstdc++ requires by openh264

# do no set ld option and use as=gcc for clang
TC_OPTIONS="--nm=${NM} --ar=${AR} --as=${AS} --strip=${STRIP} --cc=${CC} --cxx=${CXX}"

FFMPEG_PKG_CONFIG=${BASEDIR}/ffmpeg-pkg-config

make clean

./configure \
  --prefix=${PREFIX} \
  --cross-prefix=${CROSS_PREFIX} \
  --sysroot=${NDK_SYSROOT} \
  --arch=${NDK_ARCH} \
  --cpu=${CPU} \
  ${TC_OPTIONS} \
  ${TA_OPTIONS} \
  --enable-cross-compile \
  --target-os=android \
  --enable-pic \
  --disable-doc \
  --disable-debug \
  --disable-runtime-cpudetect \
  --enable-hardcoded-tables \
  --disable-avdevice \
  --disable-postproc \
  --disable-programs \
  --disable-ffmpeg \
  --disable-ffplay \
  --disable-ffprobe \
  --disable-symver \
  --disable-network \
  --disable-iconv \
  --disable-everything \
  --disable-v4l2_m2m \
  --enable-decoder=h264 \
  --enable-encoder=libx264 \
  --enable-libvpx \
  --enable-decoder=mjpeg \
  --enable-parser=mjpeg \
  --enable-filter=format \
  --enable-filter=hflip \
  --enable-filter=scale \
  --enable-filter=nullsink \
  --enable-filter=vflip \
  ${MODULES} \
  --enable-gpl \
  --extra-cflags="${INCLUDES}" \
  --extra-ldflags="${LIBS} ${LDFLAGS}" \
  --extra-cxxflags="$CXXFLAGS" \
  --extra-libs="-lgcc -lstdc++" \
  --pkg-config=${FFMPEG_PKG_CONFIG} || exit 1

# need libopencore_amrwb
#  --enable-libopencore-amrwb \
#  --enable-libvo-amrwbenc \

make -j${HOST_NUM_CORES} install || exit 1
popd || exit

echo -e "** BUILD COMPLETED: ${LIB_FFMPEG}-v${VERSION} for ${1} **\n"
