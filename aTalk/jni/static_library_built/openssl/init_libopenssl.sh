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
  LIB_OPENSSL_GIT=$1
else
  LIB_OPENSSL_GIT="openssl-1.0.2u"
fi

LIB_OPENSSL="openssl"

echo -e "\n================ Fetching library source for: ${LIB_OPENSSL} (${LIB_OPENSSL})============================"
rm -rf ${LIB_OPENSSL}
wget -O- https://www.openssl.org/source/${LIB_OPENSSL_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_OPENSSL}
echo -e "======== Completed openssl library source update ============================"

