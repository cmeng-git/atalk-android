#!/bin/bash

# ABIS=("armeabi" "armeabi-v7a" "arm64-v8a" "x86" "x86_64" "mips" "mips64")
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

for abi in "${ABIS[@]}"
do
  case $abi in
    armeabi-v7a)
      ./x264_build_armeabi-v7a.sh || exit 1
    ;;
    arm64-v8a)
      ./x264_build_arm64-v8a.sh || exit 1
    ;;
    x86)
      ./x264_build_x86.sh || exit 1
    ;;
    x86_64)
      ./x264_build_x86_64.sh || exit 1
    ;;
  esac


done

# deprecated - not further supported by android
#./x264_build_armeabi.sh
#./x264_build_mips.sh
#./x264_build_mips64.sh
