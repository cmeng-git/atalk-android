#!/bin/bash
set -x
# Applying required patches for libvpx

if [[ $# -eq 1 ]]; then
  LIB_VPX=$1
else
  LIB_VPX="libvpx-1.7.0"
fi

# ===============================
# libvpx patches
# ===============================
patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/build/make/configure.sh < ./patches/01.${LIB_VPX}_configure.sh.patch 1>/dev/null
if [ $? -eq 0 ]; then
  patch -p0 -f ./${LIB_VPX}/build/make/configure.sh < ./patches/01.${LIB_VPX}_configure.sh.patch
fi

patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vp8/common/x86/filter_x86.c < ./patches/11.libvpx_filter_x86.c.patch 1>/dev/null
if [ $? -eq 0 ]; then
  patch -p0 -f ./${LIB_VPX}/vp8/common/x86/filter_x86.c < ./patches/11.libvpx_filter_x86.c.patch
fi

patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vpx_dsp/deblock.c < ./patches/12.libvpx_deblock.c.patch 1>/dev/null
if [ $? -eq 0 ]; then
  patch -p0 -f ./${LIB_VPX}/vpx_dsp/deblock.c < ./patches/12.libvpx_deblock.c.patch
fi

patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vpx_ports/mem.h < ./patches/13.libvpx_mem.h.patch 1>/dev/null
if [ $? -eq 0 ]; then
  patch -p0 -f ./${LIB_VPX}/vpx_ports/mem.h < ./patches/13.libvpx_mem.h.patch
fi
