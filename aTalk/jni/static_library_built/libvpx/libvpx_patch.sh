#!/bin/bash
# set -x
# Applying required patches for libvpx

if [[ $# -eq 1 ]]; then
  LIB_VPX=$1
else
  LIB_VPX="libvpx"
fi

if [[ -f ${LIB_VPX}/build/make/version.sh ]]; then
  version=$(${LIB_VPX}/build/make/version.sh --bare ${LIB_VPX})
else
  version='v1.13.1'
fi


# ===============================
# Patches for libvpx version 1.10.0
# v1.10.0 need below patch for vp9 encode to work properly; master copy has been fixed
# ===============================

if [[ ${version} == v1.10.0 ]]; then
  echo -e "\n*** Applying patches for: ${LIB_VPX} (${version}) ***"

  patch  -p0 -N --dry-run --silent -f ./${LIB_VPX}/vpx/vpx_encoder.h < ./patches/10.vpx_encoder_h.patch 1>/dev/null
  if [[ $? -eq 0 ]]; then
    patch -p0 -f ./${LIB_VPX}/vpx/vpx_encoder.h < ./patches/10.vpx_encoder_h.patch
  fi
fi