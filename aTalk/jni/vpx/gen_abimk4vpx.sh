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

#set -x
set -u
. _settings.sh
HOST_NUM_CORES=$(nproc)

LIB_VPX="libvpx"
LIB_GIT=v1.15.2
echo -e "\n### Fetch and generate libs-xxx.mk files for vpx ${LIB_GIT} ###"

# Auto fetch and unarchive libvpx from online repository
./init_libvpx.sh ${LIB_GIT}

if [[ -f "${LIB_VPX}/build/make/version.sh" ]]; then
  version=$("${LIB_VPX}/build/make/version.sh" --bare "${LIB_VPX}")
fi

# configure and make for the specified ABI architectures
configure_make() {
  ABI=$1;
  configure "$@"

  case ${ABI} in
	armeabi-v7a)
      # TARGET="armv7-android-gcc --enable-neon --disable-neon-asm"
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
  esac

  if [[ -f "config.log" ]]; then
      config_target="$(grep "Configuring for target" < "config.log" | sed "s/^.* '\(.*\)'$/\1/")"
      if [[ ${TARGET} != "${config_target}" ]]; then
        make clean
      fi
  else
    make clean
  fi

  # Directly install to aTalk ./jni/vpx
  # --enable-bitstream-debug \
  # valid only for 1.14.0; Not required for ndk: 22.1.7171670
  # --disable-neon-i8mm \
  # --disable-neon-dotprod \

  ./configure \
    --prefix="${PREFIX}" \
    --target=${TARGET} \
    --as=yasm \
    --enable-pic \
    --disable-docs \
    --enable-static \
    --enable-libyuv \
    --disable-examples \
    --disable-tools \
    --disable-debug \
    --disable-unit-tests \
    --enable-realtime-only \
    --enable-vp8 --enable-vp9 \
    --enable-vp9-postproc \
    --enable-vp9-highbitdepth \
    --enable-better-hw-compatibility \
    --disable-webm-io || exit 1

  make -j"${HOST_NUM_CORES}"
}

pushd "${LIB_VPX}" || exit
for ((i=0; i < ${#ABIS[@]}; i++))
do
  if [[ $# -eq 0 ]] || [[ "$1" == "${ABIS[i]}" ]]; then
    echo -e "\n** BUILD STARTED: ${LIB_VPX} (${version}) for ${ABIS[i]} **"
    configure_make "${ABIS[i]}"
    echo -e "** BUILD COMPLETED: ${LIB_VPX} for ${ABIS[i]} **\n"
  fi
done
# must only move the files outside the loop.
# mv ./libs-*.mk ../
popd || true
