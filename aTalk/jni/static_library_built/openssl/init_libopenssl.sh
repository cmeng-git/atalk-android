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

if [[ $# -eq 1 ]]; then
  LIB_OPENSSL_GIT=$1
else
  LIB_OPENSSL_GIT="openssl-1.1.1t"
fi

LIB_OPENSSL="openssl"
if [[ -d ${LIB_OPENSSL} ]]; then
  version="$(grep '^# define OPENSSL_VERSION_TEXT' < ${LIB_OPENSSL}/include/openssl/opensslv.h | sed 's/^.*\([1-9]\.[0-9]\.[0-9][a-z]\).*$/\1/')"
  if [[ "${LIB_OPENSSL_GIT}" =~ .*"${version}".* ]]; then
    echo -e "\n========== Current openssl source is: ${LIB_OPENSSL} (${version}) =========="
    exit 0
  fi
fi

rm -rf ${LIB_OPENSSL}
echo -e "\n================ Fetching library source for: ${LIB_OPENSSL} (${LIB_OPENSSL})============================"
wget -O- https://www.openssl.org/source/${LIB_OPENSSL_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_OPENSSL}
echo -e "======== Completed openssl library source update ============================"

