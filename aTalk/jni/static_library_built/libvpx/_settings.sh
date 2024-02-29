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
# export ANDROID_NDK=/opt/android/android-ndk-r18b
export ANDROID_NDK=/opt/android/android-sdk/ndk/22.1.7171670/

# strip is missing after 22.1.7171670
# export ANDROID_NDK=/opt/android/android-sdk/ndk/25.2.9519653/
# export ANDROID_NDK=/opt/android/android-sdk/ndk/26.1.10909125/

if [[ -z $ANDROID_NDK ]] || [[ ! -d $ANDROID_NDK ]] ; then
	echo "You need to set ANDROID_NDK environment variable, exiting"
	echo "Use: export ANDROID_NDK='your_path_to_ndk'"
	echo "e.g.: export ANDROID_NDK=/opt/android/android-sdk/ndk/22.1.7171670"
	exit 1
fi

set -u

# Never mix two api level to build static library for use on the same apk.
# Set to API:21 for aTalk 64-bit architecture support and minSdk support
# Does not build 64-bit arch if ANDROID_API is less than 21 i.e. the minimum supported API level for 64-bit.
ANDROID_API=24

# Do not change naming convention of the ABIS; see:
# https://developer.android.com/ndk/guides/abis.html#Native code in app packages
# Android recommended architecture support; others are deprecated
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

BASEDIR=`pwd`
NDK=${ANDROID_NDK}
HOST_NUM_CORES=$(nproc)

# https://gcc.gnu.org/onlinedocs/gcc-4.9.1/gcc/Optimize-Options.html
# Note: vpx with ABIs x86 and x86_64 build has error with option -fstack-protector-all
# Note: final libraries built is 20~33% bigger in size when below additional options are specified
# CFLAGS_="-DANDROID -fpic -fpie -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing -fno-strict-overflow -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2"
CFLAGS_="-DANDROID -fpic -fpie"
# Enable report-all for earlier detection of errors instead at later stage
# /home/cmeng/workspace/ndk/vpx-android/armeabi-v7a-android-toolchain/bin/arm-linux-androideabi-ld: -Wl,-z,defs -Wl,--unresolved-symbols=report-all: unknown option
# Not compatible with libvpx v.1.8.2+
# LDFLAGS_="-Wl,-z,defs -Wl,--unresolved-symbols=report-all"
LDFLAGS_=""

# Do not modify any of the NDK_ARCH, CPU and -march unless you are very sure.
# The settings are used by <ARCH>-linux-android-gcc and submodule configure
# https://en.wikipedia.org/wiki/List_of_ARM_microarchitectures
# $NDK/toolchains/llvm/prebuilt/...../includellvm/ARMTargetParser.def etc
# ARCH - should be one from $ANDROID_NDK/platforms/android-$API/arch-* [arm / arm64 / mips / mips64 / x86 / x86_64]"
# https://gcc.gnu.org/onlinedocs/gcc/AArch64-Options.html

configure() {
  ABI=$1;

  case $ABI in
    # Standalone toolchains error.
    # /home/cmeng/workspace/ndk/vpx-android/armeabi-v7a-android-toolchain/bin/arm-linux-androideabi-ld: -Wl,--fix-cortex-a8: unknown option
    armeabi-v7a)
      NDK_ARCH="arm"
      NDK_ABIARCH="armv7a-linux-androideabi"
      # clang70: warning: -Wl,--fix-cortex-a8: 'linker' input unused [-Wunused-command-line-argument]
      # CFLAGS="${CFLAGS_} -Wl,--fix-cortex-a8 -march=${CPU} -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
      # CFLAGS="${CFLAGS_} -Os -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mthumb -D__thumb__"
      CFLAGS="${CFLAGS_} -Os -march=armv7-a"
      LDFLAGS="${LDFLAGS_} -march=armv7-a" # -Wl,--fix-cortex-a8" not valid option
      ASFLAGS="-c"

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
    ;;
    arm64-v8a)
      # Valid cpu = armv8-a cortex-a35, cortex-a53, cortec-a57 etc. but -march=armv8-a is required
      # x264 build has own undefined references e.g. x264_8_pixel_sad_16x16_neon - show up when build ffmpeg 
      NDK_ARCH="arm64"
      NDK_ABIARCH="aarch64-linux-android"
      CFLAGS="${CFLAGS_} -O3-march=armv8-a"
      # Supported emulations: aarch64linux aarch64elf aarch64elf32 aarch64elf32b aarch64elfb armelf armelfb
      # aarch64linuxb aarch64linux32 aarch64linux32b armelfb_linux_eabi armelf_linux_eabi
	  #-march=armv8-a or arch64linux: all are not valid for libvpx v1.8.2 build with standalone toolchains
      LDFLAGS="${LDFLAGS_}" # -march=arch64linux" not valid also
      ASFLAGS=""
    ;;
    x86)
      NDK_ARCH="x86"
      NDK_ABIARCH="i686-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=i686 -msse3 -mfpmath=sse -m32 -fPIC" # -mtune=intel
      LDFLAGS="-m32"
      ASFLAGS="-D__ANDROID__"

    ;;
    x86_64)
      NDK_ARCH="x86_64"
      NDK_ABIARCH="x86_64-linux-android"
      CFLAGS="${CFLAGS_} -O3 -march=x86-64 -msse4.2 -mpopcnt -m64 -fPIC" # -mtune=intel
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
    ;;
  esac

  # Use the prebuilt toolchain instead of using make_standalone_toolchain.py.
  TOOLCHAIN_PREFIX=$NDK/toolchains/llvm/prebuilt/linux-x86_64/

  # Define the install-directory of the libs and include files etc
  # Directly install to aTalk ./jni/vpx
  PREFIX=${BASEDIR}/../../vpx/android/${ABI}

  # Add the standalone toolchain to the search path.
  export PATH=${TOOLCHAIN_PREFIX}/bin:$PATH
  export CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/${NDK_ABIARCH}-
  export CROSS_PREFIX_API=${TOOLCHAIN_PREFIX}/bin/${NDK_ABIARCH}${ANDROID_API}-

  if [[ ($1 == "armeabi-v7a") ]]; then
    export CROSS_PREFIX="${TOOLCHAIN_PREFIX}/bin/arm-linux-androideabi-"
  else
    export CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/${NDK_ABIARCH}-
  fi

  export CFLAGS="${CFLAGS}"
  export CPPFLAGS="${CFLAGS}"
  export CXXFLAGS="${CFLAGS} -std=c++11"
  export ASFLAGS="${ASFLAGS}"

  export CC="${CROSS_PREFIX_API}clang"
  export CXX="${CROSS_PREFIX_API}clang++"

  export AS="${CROSS_PREFIX}as"
  export LD="${CROSS_PREFIX}ld.gold"
  export STRIP="${CROSS_PREFIX}strip"

  echo "**********************************************"
  echo "### Use NDK=${NDK}"
  echo "### Use ANDROID_API=${ANDROID_API}"
  echo "### Install directory: PREFIX=${PREFIX}"
  echo "**********************************************"
}
