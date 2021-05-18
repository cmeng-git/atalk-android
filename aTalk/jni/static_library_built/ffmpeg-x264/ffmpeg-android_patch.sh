#!/bin/bash
# set -x
# Applying required patches for all the codec modules

# ===============================
# ffmpeg patches
# ===============================
if [[ -f "ffmpeg/ffbuild/version.sh" ]]; then
  ffmpeg_VER=$(ffmpeg/ffbuild/version.sh ./ffmpeg)
else
  ffmpeg_VER=$(ffmpeg/version.sh ./ffmpeg)
fi

if [[ ! (${ffmpeg_VER} > 3.4.8) ]]; then
  echo -e "### Applying patches for ffmpeg-v${ffmpeg_VER} modules"

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
fi

# ===============================
# x264 patches - seems no require for the latest source x264-snapshot-20191217-2245.tar.bz2 or v161
# ===============================
 if [[ " ${MODULES[@]} " =~ " x264 " ]]; then
     ./x264/version.sh > x264_version
     X264_REV="$(grep '#define X264_REV ' < ./x264_version | sed 's/^.* \([1-9][0-9]*\)$/\1/')"

     if [[ ! (${X264_REV} == 3049) ]]; then
       X264_API="$(grep '#define X264_BUILD' < x264/x264.h | sed 's/^.* \([1-9][0-9]*\)$/\1/')"
       echo -e "### Applying patches for x264-v${X264_API}.${X264_REV} modules"

       patch  -p0 -N --dry-run --silent -f ./x264/configure < ./patches/21.x264_configure.patch 1>/dev/null
       if [[ $? -eq 0 ]]; then
         patch -p0 -f ./x264/configure < ./patches/21.x264_configure.patch
       fi
     fi
 fi

# ===============================
# lame patches
# ===============================
if [[ " ${MODULES[@]} " =~ " lame " ]]; then
    LAME_VER="$(grep 'PACKAGE_VERSION=' < lame/configure | sed 's/^.*\([1-9]\.[0-9]*\).*$/\1/')"
    echo -e "### Applying patches for lame-v${LAME_VER} modules"

    patch  -p0 -N --dry-run --silent -f ./lame/configure < ./patches/31.lame_configure.patch 1>/dev/null
    if [[ $? -eq 0 ]]; then
      patch -p0 -f ./lame/configure < ./patches/31.lame_configure.patch
    fi
fi