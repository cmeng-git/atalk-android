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

## ==================== libspeex ================= ##
if [[ $# -ge 1 ]]; then
  LIB_SPEEX_GIT=$1
else
  LIB_SPEEX_GIT="speex-1.2.1"
fi

# LIB_SPEEX_VERSION="1.2beta3"
LIB_SPEEX="libspeex"

if [[ -d ${LIB_SPEEX} ]]; then
  # version="$(grep '^#define SPEEX_VERSION' < ${LIB_SPEEX}/arch.h | sed 's/^.*\([1-9]\.[0-9][a-z]*[0-9]\).*$/\1/')"
  version="$(grep '^#define SPEEX_VERSION' < ${LIB_SPEEX}/arch.h | sed 's/^.*\([1-9]\.[0-9]\.[0-9]\).*$/\1/')"
  if [[ "${LIB_SPEEX_GIT}" =~ .*"${version}".* ]]; then
    echo -e "\n========== Current speex source is: ${LIB_SPEEX}-${version} =========="
    exit 0
  fi
fi

rm -rf ${LIB_SPEEX}
echo -e "\n================ Fetching library source for ${LIB_SPEEX}: ${LIB_SPEEX_GIT} ============================"
wget -O- http://downloads.us.xiph.org/releases/speex/speex-1.2rc1.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_SPEEX}
wget -O- http://downloads.us.xiph.org/releases/speex/${LIB_SPEEX_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_SPEEX}
echo -e "======== Completed speex library source update ============================"


## ==================== libogg ================= ##
if [[ $# -eq 2 ]]; then
  LIB_OGG_GIT=$2
else
  LIB_OGG_GIT="v1.3.5"
fi

LIB_OGG="libogg"

#if [[ -d ${LIB_OGG} ]]; then
#  version="$(grep '^PACKAGE_VERSION' < ${LIB_OGG}/package_version | sed 's/^.*\([1-9]\.[0-9]\.[0-9]\).*$/\1/')"
#  if [[ "${LIB_OGG_GIT}" =~ .*"${version}".* ]]; then
#    echo -e "\n========== Current ogg source is: ${LIB_OGG}-${version} =========="
#    exit 0
#  fi
#fi

rm -rf ${LIB_OGG}
echo -e "\n================ Fetching library source for ${LIB_OGG}: ${LIB_OGG_GIT} ============================"
wget -O- https://github.com/xiph/ogg/archive/refs/tags/${LIB_OGG_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_OGG}
echo -e "======== Completed ogg library source update ============================"

# For testing only
# wget -O- http://downloads.us.xiph.org/releases/speex/speex-1.2rc1.tar.gz | tar xz --strip-components=1 --one-top-level=libspeex
# wget -O- https://github.com/xiph/ogg/archive/refs/tags/v1.3.5.tar.gz | tar xz --strip-components=1 --one-top-level=libogg
# grep '^#define SPEEX_VERSION' < libspeex/arch.h | sed 's/^.*\([1-9]\.[0-9][a-z]*[0-9]\).*$/\1/'
