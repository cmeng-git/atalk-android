#!/bin/bash
set -x
# Applying required patches

# ===============================
# ffmpeg patches
# ===============================

patch  -p0 -N --dry-run --silent -f ./ffmpeg/configure < ./patches/10.ffmpeg-configure.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/configure < ./patches/10.ffmpeg-configure.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/libx264.c < ./patches/11.ffmpeg-libx264.c.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavcodec/libx264.c < ./patches/11.ffmpeg-libx264.c.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavutil/pixdesc.c < ./patches/12.ffmpeg-pixdesc.c.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavutil/pixdesc.c < ./patches/12.ffmpeg-pixdesc.c.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavutil/pixdesc.h < ./patches/13.ffmpeg-pixdesc.h.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavutil/pixdesc.h < ./patches/13.ffmpeg-pixdesc.h.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/svq3.c < ./patches/14.ffmpeg-svq3.c.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavcodec/svq3.c < ./patches/14.ffmpeg-svq3.c.patch
fi
