#!/bin/bash
#
# Copyright 2016 cmeng
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
source ./_shared.sh

# Setup architectures, library name and other vars + cleanup from previous runs
# Use LIB_VPX-master i.e. 1.6.1+ (10/12/2017)
# LIB_GIT="v1.6.1"
# aTalk uses the trunk master instead
LIB_GIT="master"
LIB_VPX="libvpx"
LIB_DEST_DIR=${TOOLS_ROOT}/libs

#[ -d ${LIB_DEST_DIR} ] && rm -rf ${LIB_DEST_DIR}
[ -f "${LIB_GIT}.tar.gz" ] || wget https://github.com/webmproject/LIB_VPX/archive/${LIB_GIT}.tar.gz;

# Unarchive library, then configure and make for specified architectures
configure_make() {
  ARCH=$1; ABI=$2;

# rm -rf "${LIB_VPX}"
# tar xfz "${LIB_GIT}.tar.gz" "${LIB_VPX}"
[ -d "${LIB_VPX}" ] || tar xfz "${LIB_GIT}.tar.gz" "${LIB_VPX}";

  pushd "${LIB_VPX}"
  configure $*

    if [ "$ARCH" == "android" ]; then
        TARGET="armv7-android-gcc --disable-runtime-cpu-detect --disable-neon --disable-neon-asm"
    elif [ "$ARCH" == "android-armeabi" ]; then
        TARGET="armv7-android-gcc --disable-runtime-cpu-detect"
    elif [ "$ARCH" == "android64-aarch64" ]; then
        TARGET="arm64-android-gcc"
    elif [ "$ARCH" == "android-x86" ]; then
        TARGET="x86-android-gcc"
    elif [ "$ARCH" == "android64" ]; then
        TARGET="x86_64-android-gcc"
    elif [ "$ARCH" == "android-mips" ]; then
        TARGET="mips32-linux-gcc"
    elif [ "$ARCH" == "android-mips64" ]; then
        TARGET="mips64-linux-gcc"
   fi;

   echo ./configure \
     --target=${TARGET} \
     --disable-runtime-cpu-detect \
     --disable-examples \
     --disable-tools \
     --sdk-path=${NDK_ROOT}/ \
     --prefix=${LIB_DEST_DIR}/${ABI}

   ./configure \
     --target=${TARGET} \
     --disable-runtime-cpu-detect \
     --disable-examples \
     --disable-tools \
     --sdk-path=${NDK_ROOT}/ \
     --prefix=${LIB_DEST_DIR}/${ABI}


  PATH=$TOOLCHAIN_PATH:$PATH
  make clean
  if make -j4; then
    # make install
    # make install_sw
    # make install_ssldirs

    OUTPUT_ROOT=${TOOLS_ROOT}/output/android/${ABI}
    [ -d ${OUTPUT_ROOT}/include ] || mkdir -p ${OUTPUT_ROOT}/include/vpx \
	&& mkdir -p ${OUTPUT_ROOT}/include/common \
	&& mkdir -p ${OUTPUT_ROOT}/include/mkvmuxer \
	&& mkdir -p ${OUTPUT_ROOT}/include/mkvparser \
	&& mkdir -p ${OUTPUT_ROOT}/include/libmkv
    cp -r ./vpx/*.h ${OUTPUT_ROOT}/include/vpx
    cp -r ./third_party/libwebm/common/*.h ${OUTPUT_ROOT}/include/common
    cp -r ./third_party/libwebm/mkvmuxer/*.h ${OUTPUT_ROOT}/include/mkvmuxer
    cp -r ./third_party/libwebm/mkvparser/*.h ${OUTPUT_ROOT}/include/mkvparser
    cp -r ./third_party/libmkv/*.h ${OUTPUT_ROOT}/include/libmkv

    [ -d ${OUTPUT_ROOT}/lib ] || mkdir -p ${OUTPUT_ROOT}/lib
#    cp ${LIB_DEST_DIR}/${ABI}/lib/LIB_VPX.a ${OUTPUT_ROOT}/lib
    cp LIB_VPX.a ${OUTPUT_ROOT}/lib
  fi;
  popd

}

for ((i=0; i < ${#ARCHS[@]}; i++))
do
  if [[ $# -eq 0 ]] || [[ "$1" == "${ARCHS[i]}" ]]; then
    # Do not build 64 bit arch if ANDROID_API is less than 21 which is
    # the minimum supported API level for 64 bit.
    [[ ${ANDROID_API} < 21 ]] && ( echo "${ABIS[i]}" | grep 64 > /dev/null ) && continue;
    configure_make "${ARCHS[i]}" "${ABIS[i]}"
  fi
done
