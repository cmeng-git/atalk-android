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
LIB_BCG729="bcg729"

if [[ $# -eq 1 ]]; then
  LIB_BCG729_GIT=$1
else
  LIB_BCG729_VER="1.1.1"
  LIB_BCG729_GIT="${LIB_BCG729_VER}/${LIB_BCG729}-release-${LIB_BCG729_VER}"
fi

if [[ -d ${LIB_BCG729} ]]; then
  version="$(grep '^project' < ${LIB_BCG729}/CMakeLists.txt | sed 's/^.*\([1-9]\.[0-9]\.[0-9]\).*$/\1/')"
  if [[ "${LIB_BCG729_VER}" =~ .*"${version}".* ]]; then
    echo -e "\n========== Current ${LIB_BCG729} source is: ${LIB_BCG729}-${version} =========="
    exit 0
  fi
fi

rm -rf ${LIB_BCG729}
echo -e "\n================ Fetching library source for ${LIB_BCG729}: ${LIB_BCG729}-${LIB_BCG729_VER} ============================"
# git clone https://gitlab.linphone.org/BC/public/bcg729.git
wget -O- https://gitlab.linphone.org/BC/public/bcg729/-/archive/release/${LIB_BCG729_GIT}.tar.gz  | tar xz --strip-components=1 --one-top-level=${LIB_BCG729}
# wget -O- https://codeload.github.com/BelledonneCommunications/bcg729/tar.gz/refs/tags/${LIB_BCG729_VER}  | tar xz --strip-components=1 --one-top-level=${LIB_BCG729}

pushd ${LIB_BCG729} || exit
./autogen.sh
echo -e "======== Completed opus library source update ============================"


