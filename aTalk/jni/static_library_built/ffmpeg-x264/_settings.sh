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

# Uncomment the line below to see all script echo to terminal
# set -x
if [[ $ANDROID_NDK = "" ]]; then
	echo "You need to set ANDROID_NDK environment variable, exiting"
	echo "Use: export ANDROID_NDK=/your/path/to/android-ndk"
	echo "e.g. export ANDROID_NDK=/opt/android/android-ndk-r15c"
	exit 1
fi
set -u

# Never mix two api level to build static library for use on the same apk.
# Set to API:21 for aTalk 64-bit architecture support
# Does not build 64-bit arch if ANDROID_API is less than 21 i.e. the minimum supported API level for 64-bit.
ANDROID_API=21
NDK_ABI_VERSION=4.9

# set STANDALONE_TOOLCHAINS to 0: SDK toolchains OR 1: standalone toolchains
STANDALONE_TOOLCHAINS=1;

# Built with command i.e. ./ffmpeg-android_build.sh or following with parameter [ABIS(x)]
# Create custom ABIS or uncomment to build all supported abi for ffmpeg.
# Do not change naming convention of the ABIS; see:
# https://developer.android.com/ndk/guides/abis.html#Native code in app packages
# Android recommended architecture support; others are deprecated
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

BASEDIR=`pwd`
# need to define earlier for standalone toolchains option
# if [[ STANDALONE_TOOLCHAINS == 1 ]]; then
#   TOOLCHAIN_PREFIX=${BASEDIR}/toolchain-android
# fi

#===========================================
# Do not proceed further on first call without the required 2 parameters
[[ $# -lt 2 ]] && return

NDK=${ANDROID_NDK}
HOST_NUM_CORES=$(nproc)

# https://gcc.gnu.org/onlinedocs/gcc-4.9.1/gcc/Optimize-Options.html
# Note: vpx with ABIs x86 and x86_64 build has error with option -fstack-protector-all

# Note: final libraries built is 20~33% bigger in size when below additional options are specified
# CFLAGS_="-DANDROID -fpic -fpie -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing -fno-strict-overflow -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2"
CFLAGS_="-DANDROID -fpic -fpie"

# Enable report-all for earlier detection of errors instead at later stage; see below for other options
LDFLAGS_="-Wl,-z,defs -Wl,--unresolved-symbols=report-all"

# Do not modify any of the NDK_ARCH, CPU and -march unless you are very sure.
# The settings are used by <ARCH>-linux-android-gcc and submodule configure
# https://en.wikipedia.org/wiki/List_of_ARM_microarchitectures
# ${NDK}/toolchains/llvm/prebuilt/...../include llvm/ARMTargetParser.def etc
# NDK-ARCH - should be one from $ANDROID_NDK/platforms/android-$API/arch-* [arm / arm64 / mips / mips64 / x86 / x86_64]"
# https://gcc.gnu.org/onlinedocs/gcc/AArch64-Options.html

case $1 in
  # https://gcc.gnu.org/onlinedocs/gcc-4.9.4/gcc/ARM-Options.html#ARM-Options
  armeabi-v7a)
    CPU='armv7-a'
    HOST='arm-linux'
    NDK_ARCH='arm'
    NDK_ABIARCH='arm-linux-androideabi'
    CFLAGS="${CFLAGS_} -Os -march=${CPU} -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
    LDFLAGS="${LDFLAGS_} -march=${CPU}"
    ASFLAGS=""

    # 1. -march=${CPU} flag targets the armv7 architecture.
    # 2. -mfloat-abi=softfp enables hardware-FPU instructions while ensuring that the system passes
    #     floating-point parameters in core registers, which is critical for ABI compatibility
    # 3. -mfpu=neon setting forces the use of VFPv3-D32, per the ARM specifications
    # 4. -mthumb forces the generation of 16-bit Thumb-2 instructions (Thumb-1 for armeabi).
    #     If omitted, the toolchain will emit 32-bit ARM instructions.
    # 5. -Wl,--fix-cortex-a8 is required as a workaround for a CPU bug in some Cortex-A8 implementation
    #     (x264 flags as warning) Standalone toolchains does not accept this option. SDK toolchains (gcc/cg++) is ok.
    #    /home/cmeng/workspace/ndk/ffmpeg-android/toolchain-android/bin/arm-linux-androideabi-ld: -Wl,--fix-cortex-a8: unknown option
    # LDFLAGS="-Wl,--fix-cortex-a8"

    # arm v7vfpv3
    # CFLAGS="${CFLAGS_} -march=${CPU} -mfloat-abi=softfp -mfpu=vfpv3-d16 -mthumb"

    # arm v7 + neon (neon also include vfpv3-32)
    # CFLAGS="${CFLAGS_} -march=${CPU} -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
  ;;
  arm64-v8a)
    # Valid cpu = armv8-a cortex-a35, cortex-a53, cortec-a57 etc. but -march=armv8-a is required
    # -march valid only for ‘armv8-a’, ‘armv8.1-a’, ‘armv8.2-a’, ‘armv8.3-a’ or ‘armv8.4-a’
    #  or native (only armv8-a is valid for lame build).
    CPU='cortex-a57'
    HOST='aarch64-linux'
    NDK_ARCH='arm64'
    NDK_ABIARCH='aarch64-linux-android'
    CFLAGS="${CFLAGS_} -O3 -march=armv8-a"
    LDFLAGS="${LDFLAGS_} -march=armv8-a"
    ASFLAGS=""
  ;;
  x86)
    CPU='i686'
    HOST='i686-linux'
    NDK_ARCH='x86'
    NDK_ABIARCH='i686-linux-android'
    CFLAGS="${CFLAGS_} -O3 -march=${CPU} -mtune=intel -msse3 -mfpmath=sse -m32 -fPIC"
    LDFLAGS="-m32"
    ASFLAGS="-D__ANDROID__"
  ;;
  x86_64)
    CPU='x86-64' 
    HOST='x86_64-linux'
    NDK_ARCH='x86_64'
    NDK_ABIARCH='x86_64-linux-android'
    CFLAGS="${CFLAGS_} -O3 -march=${CPU} -mtune=intel -msse4.2 -mpopcnt -m64 -fPIC"
    LDFLAGS=""
    ASFLAGS="-D__ANDROID__"
  ;;
esac

if [[ ${STANDALONE_TOOLCHAINS} == 1 ]]; then
  TOOLCHAIN_PREFIX=${BASEDIR}/toolchain-android
  NDK_SYSROOT=${TOOLCHAIN_PREFIX}/sysroot
  CC_=clang
  CXX_=clang++

  if [[ ! -e ${TOOLCHAIN_PREFIX}/${NDK_ABIARCH} ]]; then
    rm -rf ${TOOLCHAIN_PREFIX}

    # Create standalone toolchains for the specified architecture - use .py instead of the old .sh
  # However for ndk--r19b => Instead use:
  #    $ ${NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang++ src.cpp
  # cmeng: must ensure AS JNI uses the same STL library or "system" if specified
    [[ -d ${TOOLCHAIN_PREFIX} ]] || python ${NDK}/build/tools/make_standalone_toolchain.py \
      --arch ${NDK_ARCH} \
      --api ${ANDROID_API} \
      --stl libc++ \
      --install-dir=${TOOLCHAIN_PREFIX}
  fi
else
  TOOLCHAIN_PREFIX=${ANDROID_NDK}/toolchains/${NDK_ABIARCH}-${NDK_ABI_VERSION}/prebuilt/linux-x86_64
  NDK_SYSROOT=${ANDROID_NDK}/platforms/android-${ANDROID_API}/arch-${NDK_ARCH}
  CC_=gcc
  CXX_=g++
fi

# Define the install directory of the libs and include files etc
# lame needs absolute path
PREFIX=${BASEDIR}/android/$1

# Add the standalone toolchain to the search path.
export PATH=${TOOLCHAIN_PREFIX}/bin:$PATH
export CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/${NDK_ABIARCH}-
export CFLAGS="${CFLAGS}"
export CPPFLAGS="${CFLAGS}"
export CXXFLAGS="${CFLAGS} -std=c++11"
export ASFLAGS="${ASFLAGS}"
export LDFLAGS="${LDFLAGS} -L${NDK_SYSROOT}/usr/lib"

export AR="${CROSS_PREFIX}ar"
export AS="${CROSS_PREFIX}${CC_}"
export CC="${CROSS_PREFIX}${CC_}"
export CXX="${CROSS_PREFIX}${CXX_}"
export LD="${CROSS_PREFIX}ld"
export STRIP="${CROSS_PREFIX}strip"
export RANLIB="${CROSS_PREFIX}ranlib"
export OBJDUMP="${CROSS_PREFIX}objdump"
export CPP="${CROSS_PREFIX}cpp"
export GCONV="${CROSS_PREFIX}gconv"
export NM="${CROSS_PREFIX}nm"
export SIZE="${CROSS_PREFIX}size"
export PKG_CONFIG="${CROSS_PREFIX}pkg-config"
export PKG_CONFIG_LIBDIR=${PREFIX}/lib/pkgconfig
export PKG_CONFIG_PATH=${PREFIX}/lib/pkgconfig

echo "**********************************************"
echo "### Use NDK=${NDK}"
echo "### Use ANDROID_API=${ANDROID_API}"
echo "### Install directory: PREFIX=${PREFIX}"
echo "**********************************************"

# Undefined reference in x264 when linked with ffmpeg for arm64-v8a
# https://stackoverflow.com/questions/5555632/can-gcc-not-complain-about-undefined-references
#
#It is possible to avoid reporting undefined references - using --unresolved-symbols linker option.
#
#g++ mm.cpp -Wl,--unresolved-symbols=ignore-in-object-files
#From man ld
#
#--unresolved-symbols=method
#Determine how to handle unresolved symbols. There are four possible values for method:
#
#       ignore-all
#           Do not report any unresolved symbols.
#
#       report-all
#           Report all unresolved symbols.  This is the default.
#
#       ignore-in-object-files
#           Report unresolved symbols that are contained in shared
#           libraries, but ignore them if they come from regular object
#           files.
#
#       ignore-in-shared-libs
#           Report unresolved symbols that come from regular object
#           files, but ignore them if they come from shared libraries.  This
#           can be useful when creating a dynamic binary and it is known
#           that all the shared libraries that it should be referencing
#           are included on the linker's command line.
#The behaviour for shared libraries on their own can also be controlled by the --[no-]allow-shlib-undefined option.
#
#Normally the linker will generate an error message for each reported unresolved symbol but the option --warn-unresolved-symbols can change this to a warning.