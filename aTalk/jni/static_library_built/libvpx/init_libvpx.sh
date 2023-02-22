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

if [[ $# -eq 1 ]]; then
  LIB_GIT=$1
else
  LIB_GIT=v1.13.0
fi

LIB_VPX="libvpx"

if [[ -d ${LIB_VPX} ]] && [[ -f "${LIB_VPX}/build/make/version.sh" ]]; then
  version=`"${LIB_VPX}/build/make/version.sh" --bare "${LIB_VPX}"`
  if [[ (${LIB_GIT} == "${version}") ]]; then
    echo -e "\n========== Current libvpx source is: ${LIB_VPX} (${version}) =========="
    exit 0
  fi
fi

# Delete LIB_VPX and uncomment below to use master main repository with the same LIB_GIT version
# LIB_GIT=f6de5b5
LIB_GIT=main

rm -rf ${LIB_VPX}
echo -e "\n========== Fetching library source for: ${LIB_VPX} (${LIB_GIT}) =========="
## wget -O- https://github.com/webmproject/libvpx/archive/refs/tags/${LIB_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_VPX}
wget -O- https://github.com/webmproject/libvpx/archive/${LIB_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_VPX}

echo -e "========== Completed libvpx library source update =========="
