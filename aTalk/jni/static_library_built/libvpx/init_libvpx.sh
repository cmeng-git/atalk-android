#!/bin/bash

if [[ $# -eq 1 ]]; then
  LIB_VPX=$1
else
  LIB_VPX="libvpx-1.7.0"
fi
LIB_GIT=$(echo ${LIB_VPX[@]//libvpx-/v})

echo -e "\n================ Fetching library source for: ${LIB_VPX} ============================"

[ -f "${LIB_GIT}.tar.gz" ] || wget https://github.com/webmproject/libvpx/archive/${LIB_GIT}.tar.gz;
rm -rf "${LIB_VPX}"

echo "Extract ${LIB_VPX} files"
tar -xf "${LIB_GIT}.tar.gz" "${LIB_VPX}"

echo -e "======== Completed libvpx library source update ============================"

