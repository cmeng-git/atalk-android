#!/bin/bash

# ABIS=("armeabi" "armeabi-v7a" "arm64-v8a" "x86" "x86_64" "mips" "mips64")
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

# disable auto exit on error i.e. exit 0
for abi in "${ABIS[@]}"
do
  case $abi in
    armeabi-v7a)
      ./ffmpeg-x264_build_armeabi-v7a.sh || exit 0
    ;;
    arm64-v8a)
      # has problem building for arm64-v8a with undefined references in libx264
      ./ffmpeg-x264_build_arm64-v8a.sh || exit 0
    ;;
    x86)
      ./ffmpeg-x264_build_x86.sh || exit 0
    ;;
    x86_64)
      ./ffmpeg-x264_build_x86_64.sh || exit 0
    ;;
  esac
done

# deprecated - not further supported by android
#./ffmpeg-x264_build_armeabi.sh
#./ffmpeg-x264_build_mips.sh
#./ffmpeg-x264_build_mips64.sh
