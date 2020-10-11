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

# Applying required patches for libopenssl v1.0.2
# set -x

if [[ $# -eq 1 ]]; then
  LIB_OPENSSL=$1
else
  LIB_OPENSSL="openssl"
fi


if [[ -f "${LIB_OPENSSL}/Makefile" ]]; then
  version="$(grep '^VERSION=' < ${LIB_OPENSSL}/Makefile | sed 's/^.*=\([1-9]\.[0-9]\.[0-9][a-z]\).*$/v\1/')"
else
  version='v1.0.2u'
fi

echo -e "\n*** Applying patches for: ${LIB_OPENSSL} (${version}) ***"

# ===============================
# libopenssl patches for version 1.0.2
# ===============================
patch  -p0 -N --dry-run --silent -f ./${LIB_OPENSSL}/Configure < ./patches/openssl_Configure_clang.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./${LIB_OPENSSL}/Configure < ./patches/openssl_Configure_clang.patch
fi

patch  -p0 -N --dry-run --silent -f ./${LIB_OPENSSL}/util/mkbuildinf.pl < ./patches/openssl_util_mkbuildinf.pl.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
patch -p0 -f ./${LIB_OPENSSL}/util/mkbuildinf.pl < ./patches/openssl_util_mkbuildinf.pl.patch
fi
