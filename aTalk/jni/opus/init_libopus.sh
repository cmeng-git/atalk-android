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
  LIB_OPUS_GIT=$1
else
  LIB_OPUS_GIT="opus-1.3.1"
fi

LIB_OPUS="opus"
if [[ -d ${LIB_OPUS} ]]; then
  version="$(grep '^PACKAGE_VERSION' < ${LIB_OPUS}/package_version | sed 's/^.*\([1-9]\.[0-9]\.[0-9]\).*$/\1/')"
  if [[ "${LIB_OPUS_GIT}" =~ .*"${version}".* ]]; then
    echo -e "\n========== Current opus source is: ${LIB_OPUS}-${version} =========="
    exit 0
  fi
fi

rm -rf ${LIB_OPUS}
echo -e "\n================ Fetching library source for ${LIB_OPUS}: ${LIB_OPUS_GIT} ============================"
wget -O- https://archive.mozilla.org/pub/opus/${LIB_OPUS_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_OPUS}
echo -e "======== Completed opus library source update ============================"

# For testing only
# wget -O- https://archive.mozilla.org/pub/opus/opus-1.3.1.tar.gz | tar xz --strip-components=1 --one-top-level=opus
# grep '^PACKAGE_VERSION=' < libopus/package_version | sed 's/^.*\([1-9]\.[0-9]\.[0-9]\).*$/\1/'
