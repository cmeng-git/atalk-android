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

set -u
. _settings.sh

LIB_OPENSSL="openssl"
LIB_OPENSSL_GIT="openssl-1.1.1t"

# Auto fetch and unarchive libopenssl from online repository with the given version i.e. LIB_OPENSSL_GIT
./init_libopenssl.sh ${LIB_OPENSSL_GIT}
version="$(grep '^# define OPENSSL_VERSION_TEXT' < ${LIB_OPENSSL}/include/openssl/opensslv.h | sed 's/^.*\([1-9]\.[0-9]\.[0-9][a-z]\).*$/\1/')"

# configure and make for specified architectures
configure_make() {
  ABI=$1;
  echo -e "\n** BUILD STARTED: ${LIB_OPENSSL} (${version}) for ${ABI} **"

  pushd "${LIB_OPENSSL}" || exit
  configure "$1"

  #supported by openssl-1.1.1
  case ${ABI} in
    armeabi-v7a)
      TARGET="android-arm"
    ;;
    arm64-v8a)
      TARGET="android-arm64 no-ssl2 no-ssl3 no-hw "
    ;;
    x86)
      TARGET="android-x86"
    ;;
    x86_64)
      TARGET="android-x86_64 no-ssl2 no-ssl3 no-hw "
    ;;
  esac

  make clean
  ./Configure \
      $TARGET \
      -D__ANDROID_API__="${ANDROID_API}" \
      --prefix="${PREFIX}" \
      no-shared \
      no-unit-test

  if make -j"${HOST_NUM_CORES}"; then
    make install_sw
  fi;
  popd || return
}

for ((i=0; i < ${#ABIS[@]}; i++))
do
  if [[ $# -eq 0 ]] || [[ "$1" == "${ABIS[i]}" ]]; then
    # Do not build 64 bit arch if ANDROID_API is less than 21 which is
    # the minimum supported API level for 64 bit.
    [[ ${ANDROID_API} -lt 21 ]] && ( echo "${ABIS[i]}" | grep 64 > /dev/null ) && continue;
    configure_make "${ABIS[i]}"
    echo -e "** BUILD COMPLETED: ${LIB_OPENSSL} (${version}) for ${ABIS[i]} **\n\n"
  fi
done
