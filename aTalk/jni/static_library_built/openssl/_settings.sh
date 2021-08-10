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

export ANDROID_NDK_HOME=/opt/android/android-sdk/ndk/20.0.5594570
if [[ -z $ANDROID_NDK_HOME ]] || [[ ! -d $ANDROID_NDK_HOME ]] ; then
	echo "You need to set ANDROID_NDK_HOME environment variable, exiting"
	echo "e.g.: export ANDROID_NDK_HOME=/opt/android/android-sdk/ndk/20.0.5594570"
	exit 1
fi

set -u

# Never mix two api level to build static library for use on the same apk.
# Default to API:21 for it is the minimum requirement for 64 bit arch.
# Does not build 64-bit arch if ANDROID_API is less than 21 i.e. the minimum supported API level for 64-bit.
ANDROID_API=21

# Do not change naming convention of the ABIS; see:
# https://developer.android.com/ndk/guides/abis.html#Native code in app packages
# ABIS=("armeabi" "armeabi-v7a" "arm64-v8a" "x86" "x86_64" "mips" "mips64")

# Android recommended architecture support; others are deprecated
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

BASEDIR=`pwd`
HOST_NUM_CORES=$(nproc)

configure() {
  ABI=$1;

  case $ABI in
    armeabi-v7a)
      NDK_ARCH="arm-linux-android-4.9"
      NDK_ABIARCH="arm-linux-androideabi"
    ;;
    arm64-v8a)
      NDK_ARCH="aarch64-linux-android-4.9"
      NDK_ABIARCH="aarch64-linux-android"
    ;;
    x86)
      NDK_ARCH="arm-linux-android-4.9"
      NDK_ABIARCH="i686-linux-android"
    ;;
    x86_64)
      NDK_ARCH="aarch64-linux-android-4.9"
      NDK_ABIARCH="x86_64-linux-android"
    ;;
  esac

  # Define the install directory of the libs and include files etc
  # PREFIX=${BASEDIR}/android/${ABI}
  PREFIX=${BASEDIR}/../../openssl/android/$1

  # Add the prebuilt toolchain and clang to the search path. (See NOTES.ANDROID)
	# PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:\
	# $ANDROID_NDK_HOME/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin:$PATH
  NDK_LIBC=${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin
  NDK_TOOLCHAIN=${ANDROID_NDK_HOME}/toolchains/${NDK_ARCH}/prebuilt/linux-x86_64/bin
  export PATH=${NDK_LIBC}:${NDK_TOOLCHAIN}:$PATH

  echo "**********************************************"
  echo "### Use NDK=${ANDROID_NDK_HOME}"
  echo "### Use ANDROID_API=${ANDROID_API}"
  echo "### Install directory: PREFIX=${PREFIX}"
  echo "### PATH=$PATH"
  echo "**********************************************"
}
