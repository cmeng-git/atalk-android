#!/bin/bash
# set -x

# aTalk v2.6.1 is compatible with the following module versions:
# a. ffmpeg-v4.4
# b. x264-v161.3049 #define X264_VERSION "r3049 55d517b"

if [[ $# -eq 2 ]]; then
  VERSION_FFMPEG=$1
  VERSION_X264=$2
else
  VERSION_FFMPEG=5.1.2
  VERSION_X264=164
fi

LIB_FFMPEG=ffmpeg
LIB_X264=x264

echo -e "\n========== init libraries for: $LIB_FFMPEG ($VERSION_FFMPEG) and $LIB_X264 ($VERSION_X264) =========="

if [[ -d $LIB_FFMPEG ]]; then
  version_ffmpeg=$(cat ${LIB_FFMPEG}/RELEASE)
  if [[ $VERSION_FFMPEG == "$version_ffmpeg" ]]; then
    echo -e "\n========== Current ffmpeg source is: $LIB_FFMPEG ($version_ffmpeg) =========="
  else
    rm -rf $LIB_FFMPEG
  fi
fi

if [[ -d $LIB_X264 ]]; then
  version_x264="$(grep '#define X264_BUILD' < ${LIB_X264}/x264.h | sed 's/^.* \([1-9][0-9]*\).*$/\1/')"
  if [[ $VERSION_X264 == "$version_x264" ]]; then
    echo -e "\n========== Current x264 source is: $LIB_X264 ($version_x264) =========="
  else
    rm -rf $LIB_X264
  fi
fi

if [[ ! -d $LIB_FFMPEG ]]; then
  echo -e "\n========== Updating ffmpeg source: $LIB_FFMPEG ($VERSION_FFMPEG) =========="
  wget -O- https://www.ffmpeg.org/releases/ffmpeg-${VERSION_FFMPEG}.tar.bz2 | tar xj --strip-components=1 --one-top-level=ffmpeg
fi

if [[ ! -d $LIB_X264 ]]; then
  echo -e "\n========== Updating x264 source: $LIB_X264 ($VERSION_X264) =========="
  git clone https://code.videolan.org/videolan/x264.git --branch stable
fi

echo "========== Completed sub modules update =========="

