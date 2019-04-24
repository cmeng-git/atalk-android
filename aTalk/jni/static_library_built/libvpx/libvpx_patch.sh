#!/bin/bash
# set -x
# Applying required patches for libvpx

if [[ $# -eq 1 ]]; then
  LIB_VPX=$1
else
  LIB_VPX="libvpx"
fi

if [[ -f "${LIB_VPX}/build/make/version.sh" ]]; then
  version=`"${LIB_VPX}/build/make/version.sh" --bare "${LIB_VPX}"`
else
  version='v1.8.0'
fi

echo -e "\n*** Applying patches for: ${LIB_VPX} (${version}) ***"

# ===============================
# libvpx patches for version 1.8.0, 1.7.0 and 1.6.1+
# ===============================
patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/build/make/configure.sh < ./patches/10.libvpx_configure.sh.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./${LIB_VPX}/build/make/configure.sh < ./patches/10.libvpx_configure.sh.patch
fi

# v1.8.0 does not have filter_x86.c
if [[ "${version}" == v1.7.0 ]] || [[ "${version}" == v1.6.1 ]]; then
  patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vp8/common/x86/filter_x86.c < ./patches/11.libvpx_filter_x86.c.patch 1>/dev/null
  if [[ $? -eq 0 ]]; then
    patch -p0 -f ./${LIB_VPX}/vp8/common/x86/filter_x86.c < ./patches/11.libvpx_filter_x86.c.patch
  fi

  patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vpx_dsp/deblock.c < ./patches/12.libvpx_deblock.c.patch 1>/dev/null
  if [[ $? -eq 0 ]]; then
    patch -p0 -f ./${LIB_VPX}/vpx_dsp/deblock.c < ./patches/12.libvpx_deblock.c.patch
  fi

  patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vpx_ports/mem.h < ./patches/13.libvpx_mem.h.patch 1>/dev/null
  if [[ $? -eq 0 ]]; then
    patch -p0 -f ./${LIB_VPX}/vpx_ports/mem.h < ./patches/13.libvpx_mem.h.patch
  fi
fi