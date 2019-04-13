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

set -u
. _settings.sh

# aTalk v1.7.3 and above uses libvpx-master i.e. 1.6.1+ (10/13/2017)
# aTalk v1.8.1 is not compatible with libvpx-1.7.0

# LIB_VPX="libvpx-master"
LIB_VPX="libvpx-1.6.1+"
# LIB_VPX="libvpx-1.7.0"

## Both problems #1 & #2 below have been fixed by patches from: https://android.googlesource.com/platform/external/libvpx/+/ca30a60d2d6fbab4ac07c63bfbf7bbbd1fe6a583
## However the compiled libjnvpx.so has problem when exec on x86_64 android platform:
## i.e. org.atalk.android A/libc: Fatal signal 31 (SIGSYS), code 1 in tid 5833 (Loop thread: ne), pid 4781 (g.atalk.android)

# 1. libvpx >v1.6.1 has the following errors when build with aTalk; v1.6.1 failed with --enable-pic
# ./i686-linux-android/bin/ld: error: vpx/android/x86/lib/libvpx.a(deblock_sse2.asm.o): relocation R_386_GOTOFF against preemptible symbol vpx_rv cannot be used when making a shared object
# ./i686-linux-android/4.9.x/../../../../i686-linux-android/bin/ld: error: vpx/android/x86/lib/libvpx.a(subpixel_mmx.asm.o): relocation R_386_GOTOFF against preemptible symbol # vp8_bilinear_filters_x86_8 cannot be used when making a shared object
# ./i686-linux-android/bin/ld: error: vpx/android/x86/lib/libvpx.a(subpixel_mmx.asm.o): relocation R_386_GOTOFF against preemptible symbol vp8_bilinear_filters_x86_8 cannot be used when making a shared object
# ./i686-linux-android/bin/ld: error: vpx/android/x86/lib/libvpx.a(subpixel_sse2.asm.o): relocation R_386_GOTOFF against preemptible symbol vp8_bilinear_filters_x86_8 cannot be used when making a shared object
# ./i686-linux-android/bin/ld: error: vpx/android/x86/lib/libvpx.a(subpixel_sse2.asm.o): relocation R_386_GOTOFF against preemptible symbol vp8_bilinear_filters_x86_8 cannot be used when making a shared object

# 2. However libvpx-1.6.1 x86_64 has the same error
# ./x86_64-linux-android/bin/ld: error: vpx/android/x86_64/lib/libvpx.a(deblock_sse2.asm.o): requires dynamic R_X86_64_PC32 reloc against 'vpx_rv' which may overflow at runtime; recompile with -fPIC


# Uncomment below for fetech libvpx from online repository
# ./init_libvpx.sh ${LIB_VPX}

# Applying required patches to libvpx-1.7.0 or libvpx-1.6.1+
echo -e "\n*** Applying patches for: ${LIB_VPX} ***"
./libvpx_patch.sh ${LIB_VPX}

# Unarchive library, then configure and make for specified architectures
configure_make() {
  pushd "${LIB_VPX}"
  make clean

  ABI=$1;
  configure $*

  case $ABI in
    # libvpx does not provide armv5 build option
    armeabi)
      TARGET="armv7-android-gcc --disable-neon --disable-neon-asm"
    ;;
    armeabi-v7a)
      TARGET="armv7-android-gcc"
    ;;
    arm64-v8a)
      TARGET="arm64-android-gcc"
    ;;
    x86)
      TARGET="x86-android-gcc"
    ;;
    x86_64)
      TARGET="x86_64-android-gcc"
    ;;
    mips)
      TARGET="mips32-linux-gcc"
    ;;
    mips64)
      TARGET="mips64-linux-gcc"
    ;;
  esac

  # --sdk-path=${TOOLCHAIN_PREFIX} must use ${NDK} actual path else cannot find CC for arm64-android-gcc
  # https://bugs.chromium.org/p/webm/issues/detail?id=1476
  # --extra-cflags fix for >= ndk-r16b; but essentially NOP for NDK below ndk-r16 (need patch for arm64-android-gcc)
  # --as=yasm requires by x86 and x86-64 instead of clang

  ./configure \
    --sdk-path=${NDK} \
    --extra-cflags="-isystem ${NDK}/sysroot/usr/include/${NDK_ABIARCH} -isystem ${NDK}/sysroot/usr/include" \
    --prefix=${PREFIX} \
    --target=${TARGET} \
    --as=yasm \
    --enable-pic \
    --disable-runtime-cpu-detect \
    --disable-docs \
    --enable-static \
    --disable-shared \
    --enable-libyuv \
    --disable-examples \
    --disable-tools \
    --disable-debug \
    --disable-unit-tests \
    --enable-realtime-only \
    --disable-webm-io || exit 1

  make -j${HOST_NUM_CORES} install
  popd
}

for ((i=0; i < ${#ABIS[@]}; i++))
do
  if [[ $# -eq 0 ]] || [[ "$1" == "${ABIS[i]}" ]]; then
    # Do not build 64 bit arch if ANDROID_API is less than 21 which is
    # the minimum supported API level for 64 bit.
    [[ ${ANDROID_API} < 21 ]] && ( echo "${ABIS[i]}" | grep 64 > /dev/null ) && continue;
    configure_make "${ABIS[i]}"
    echo -e "** BUILD COMPLETED: ${LIB_VPX} for ${ABIS[i]} **\n\n"
  fi
done
