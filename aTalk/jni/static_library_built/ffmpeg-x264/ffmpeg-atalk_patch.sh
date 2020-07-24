#!/bin/bash
# set -x
# Applying required patches for all the codec modules

# ===============================
# ffmpeg patches
# ===============================
if [[ -f "ffmpeg/ffbuild/version.sh" ]]; then
  VERSION=$(ffmpeg/ffbuild/version.sh ./ffmpeg)
else
  VERSION=$(ffmpeg/version.sh ./ffmpeg)
fi

echo -e "### Applying patches for ffmpeg-v${VERSION} modules"

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/aaccoder.c < ./patches/01.ffmpeg_aacoder.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavcodec/aaccoder.c < ./patches/01.ffmpeg_aacoder.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/hevc_mvs.c < ./patches/02.ffmpeg_hevc_mvs.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavcodec/hevc_mvs.c < ./patches/02.ffmpeg_hevc_mvs.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libavcodec/opus_pvq.c < ./patches/03.ffmpeg_opus_pvq.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libavcodec/opus_pvq.c < ./patches/03.ffmpeg_opus_pvq.patch
fi

patch  -p0 -N --dry-run --silent -f ./ffmpeg/libaudevice/v4l2.c < ./patches/04.ffmpeg_v4l2.patch 1>/dev/null
if [[ $? -eq 0 ]]; then
  patch -p0 -f ./ffmpeg/libaudevice/v4l2.c < ./patches/03.ffmpeg_v4l2.patch
fi

# ===============================
# x264 patches
# ===============================
if [[ " ${MODULES[@]} " =~ " x264 " ]]; then
    X264_API="$(grep '#define X264_BUILD' < x264/x264.h | sed 's/^.* \([1-9][0-9]*\).*$/\1/')"
    echo -e "### Applying patches for x264-v${X264_API} modules"

    patch  -p0 -N --dry-run --silent -f ./x264/configure < ./patches/21.x264_configure.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./x264/configure < ./patches/21.x264_configure.patch
    fi
fi

# ===============================
# lame patches
# ===============================
if [[ " ${MODULES[@]} " =~ " lame " ]]; then
    LAME_VER="$(grep 'PACKAGE_VERSION =' < lame/configure | sed 's/^.*\([1-9]\.[0-9]*\).*$/\1/')"
    echo -e "### Applying patches for lame-v${LAME_VER} modules"

    patch  -p0 -N --dry-run --silent -f ./lame/configure < ./patches/31.lame_configure.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./lame/configure < ./patches/31.lame_configure.patch
    fi
fi