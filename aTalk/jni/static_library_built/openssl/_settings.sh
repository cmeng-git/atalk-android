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

# cmeng: (20190503) Latest ndk-bundle generated libraries have problem when integrate with aTalk.
# use android-ndk-r15c etc; Otherwise has below problem in aTalk build
# export ANDROID_NDK="/opt/android/android-sdk/ndk-bundle"
# armv7: requires unsupported dynamic reloc R_ARM_REL32;
# x86: relocation R_386_GOTOFF against preemptible symbol ..;

# export ANDROID_NDK="/opt/android/android-ndk-r15c" - working without errors for aTalk
if [[ $ANDROID_NDK = "" ]]; then
	echo "You need to set ANDROID_NDK environment variable, exiting"
	echo "e.g.: export ANDROID_NDK=/opt/android/android-ndk-r15c"
	exit 1
fi
set -u

# Never mix two api level to build static library for use on the same apk.
# Default to API:21 for it is the minimum requirement for 64 bit archs.
# Does not build 64-bit arch if ANDROID_API is less than 21 i.e. the minimum supported API level for 64-bit.
ANDROID_API=21
NDK_ABI_VERSION=4.9

# Do not change naming convention of the ABIS; see:
# https://developer.android.com/ndk/guides/abis.html#Native code in app packages
# ABIS=("armeabi" "armeabi-v7a" "arm64-v8a" "x86" "x86_64" "mips" "mips64")

# Android recommended architecture support; others are deprecated
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

BASEDIR=`pwd`
NDK=${ANDROID_NDK}
HOST_NUM_CORES=$(nproc)

# Note: final libraries built is 20~33% bigger in size when below additional options are specified
# CFLAGS_="-DANDROID -fpic -fpie -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing"
CFLAGS_="-DANDROID -fpic -fpie"
LDFLAGS_=""

configure() {
  ABI=$1;

  case $ABI in
    # Deprecated in r16. Will be removed in r17
    armeabi)
      NDK_ARCH="arm"
      NDK_ABIARCH="arm-linux-androideabi"
      CFLAGS="${CFLAGS_} -march=armv5 -mthumb -finline-limit=64"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
    ;;

    armeabi-v7a)
      NDK_ARCH="arm"
      NDK_ABIARCH="arm-linux-androideabi"
      CFLAGS="${CFLAGS_} -march=armv7-a -mfloat-abi=softfp -mfpu=neon -mtune=cortex-a8 -mfpu=vfpv3-d16 -mthumb"
      LDFLAGS="${LDFLAGS_} -march=armv7-a -Wl,--fix-cortex-a8"
      ASFLAGS=""
    ;;
    arm64-v8a)
      NDK_ARCH="arm64"
      NDK_ABIARCH="aarch64-linux-android"
      CFLAGS="${CFLAGS_} -march=armv8-a"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
    ;;
    x86)
      NDK_ARCH="x86"
      NDK_ABIARCH="i686-linux-android"
      CFLAGS="${CFLAGS_} -O2 -march=i686 -mtune=intel -msse3 -mfpmath=sse -m32"
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
    ;;
    x86_64)
      NDK_ARCH="x86_64"
      NDK_ABIARCH="x86_64-linux-android"
      CFLAGS="${CFLAGS_} -O2 -march=x86-64 -mtune=intel -msse4.2 -mpopcnt -m64"
      LDFLAGS=""
      ASFLAGS="-D__ANDROID__"
    ;;
    mips)
      NDK_ARCH="mips"
      NDK_ABIARCH="mipsel-linux-android"
      CFLAGS="${CFLAGS_} -EL -march=p5600 -mhard-float"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
    ;;
    mips64)
	  # ARCH="linux64-mips64"
      NDK_ARCH="mips64"
      NDK_ABIARCH="mips64el-linux-android"
      CFLAGS="${CFLAGS_} -EL -mfp64 -mhard-float"
      LDFLAGS="${LDFLAGS_}"
      ASFLAGS=""
    ;;
  esac

  TOOLCHAIN_PREFIX=${BASEDIR}/${ABI}-android-toolchain
  NDK_SYSROOT=${TOOLCHAIN_PREFIX}/sysroot

  # rmdir if not the correct toolchains; then create it again; else keep it and do not create again
  if [[ ! -e ${TOOLCHAIN_PREFIX}/${NDK_ABIARCH} ]]; then
      rm -rf ${TOOLCHAIN_PREFIX}
  fi

# cmeng: must ensure AS JNI uses the same STL library or "system"
  [[ -d ${TOOLCHAIN_PREFIX} ]] || python ${NDK}/build/tools/make_standalone_toolchain.py \
     --arch ${NDK_ARCH} \
     --api ${ANDROID_API} \
     --stl libc++ \
     --install-dir=${TOOLCHAIN_PREFIX}

  # Define the install directory of the libs and include files etc
  PREFIX=${BASEDIR}/output/android/${ABI}

  # Add the standalone toolchain to the search path.
  export PATH=${TOOLCHAIN_PREFIX}/bin:$PATH
  export CROSS_PREFIX=${TOOLCHAIN_PREFIX}/bin/${NDK_ABIARCH}-
  export SYSROOT=${TOOLCHAIN_PREFIX}/sysroot
  # export CROSS_SYSROOT="${SYSROOT}"
  export CFLAGS="${CFLAGS}"
  # export CPPFLAGS=${CPPFLAGS:-""}
  export CXXFLAGS="${CFLAGS} -std=c++11 -frtti -fexceptions"
  export ASFLAGS="${ASFLAGS}"
  export LDFLAGS="${LDFLAGS} -L${NDK_SYSROOT}/usr/lib"
  export AR="${CROSS_PREFIX}ar"
  export AS="${CROSS_PREFIX}clang"
  export CC="${CROSS_PREFIX}clang"
  export CXX="${CROSS_PREFIX}clang++"
  export LD="${CROSS_PREFIX}ld"
  export RANLIB="${CROSS_PREFIX}ranlib"
  export STRIP="${CROSS_PREFIX}strip"

  echo "**********************************************"
  echo "### Use NDK=${NDK}"
  echo "### Use ANDROID_API=${ANDROID_API}"
  echo "### Install directory: PREFIX=${PREFIX}"
  echo "**********************************************"
}
