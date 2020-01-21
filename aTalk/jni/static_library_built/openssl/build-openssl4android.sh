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

# support openssl-1.0.2
LIB_OPENSSL="openssl"
LIB_OPENSSL_GIT="openssl-1.0.2u"

# Auto fetch and unarchive libopenssl from online repository with the given version i.e. LIB_OPENSSL_GIT
[[ -d ${LIB_OPENSSL} ]] || ./init_libopenssl.sh ${LIB_OPENSSL_GIT}

if [[ -f "${LIB_OPENSSL}/Makefile" ]]; then
  version="$(grep '^VERSION=' < ${LIB_OPENSSL}/Makefile | sed 's/^.*=\([1-9]\.[0-9]\.[0-9][a-z]\).*$/v\1/')"
else
  # extract the version from LIB_OPENSSL_GIT e.g. 'v1.0.2u'
  version="$(${LIB_OPENSSL_GIT} | sed 's/^.*-\([1-9]\.[0-9]\.[0-9][a-z]\).*$/v\1/')"
fi

# Applying required patches to openssl-1.0.2
./libopenssl_patch.sh ${LIB_OPENSSL}

# configure and make for specified architectures
configure_make() {
  ABI=$1;
  echo -e "\n** BUILD STARTED: ${LIB_OPENSSL} (${version}) for ${ABI} **"

  pushd "${LIB_OPENSSL}"
  make clean
  configure $1

  #supported by openssl-1.0.2
  case ${ABI} in
    armeabi)
      TARGET="android --disable-neon --disable-neon-asm"
    ;;
    armeabi-v7a)
      TARGET="android-armv7"
    ;;
    arm64-v8a)
      TARGET="android64-aarch64 no-ssl2 no-ssl3 no-hw "
    ;;
    x86)
      TARGET="android-x86"
    ;;
    x86_64)
      TARGET="linux-x86_64 no-ssl2 no-ssl3 no-hw "
    ;;
    mips)
      TARGET="android-mips"
    ;;
    mips64)
      TARGET="android-mips64"
    ;;
  esac


  ./Configure \
      $TARGET \
      --prefix=${PREFIX} \
      --with-zlib-include=$SYSROOT/usr/include \
      --with-zlib-lib=$SYSROOT/usr/lib \
      zlib \
      no-asm \
      no-shared \
      no-unit-test

  if make -j${HOST_NUM_CORES}; then
    make install
  fi;
  popd

}

for ((i=0; i < ${#ABIS[@]}; i++))
do
  if [[ $# -eq 0 ]] || [[ "$1" == "${ABIS[i]}" ]]; then
    # Do not build 64 bit arch if ANDROID_API is less than 21 which is
    # the minimum supported API level for 64 bit.
    [[ ${ANDROID_API} < 21 ]] && ( echo "${ABIS[i]}" | grep 64 > /dev/null ) && continue;
    configure_make "${ABIS[i]}"
    echo -e "** BUILD COMPLETED: ${LIB_OPENSSL} (${version}) for ${ABIS[i]} **\n\n"
  fi
done
