#!/bin/bash

if [[ $# -eq 1 ]]; then
  LIB_GIT=$1
else
	LIB_GIT=v1.8.2
fi

LIB_VPX="libvpx"

echo -e "\n================ Fetching library source for: ${LIB_VPX} (${LIB_GIT})============================"
rm -rf ${LIB_VPX}
wget -O- https://github.com/webmproject/libvpx/archive/${LIB_GIT}.tar.gz | tar xz --strip-components=1 --one-top-level=${LIB_VPX}
echo -e "======== Completed libvpx library source update ============================"

