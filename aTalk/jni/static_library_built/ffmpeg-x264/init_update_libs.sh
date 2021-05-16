#!/bin/bash

# aTalk v2.6.1 is compatible with the following module versions:
# a. ffmpeg-v4.4
# b. x264-v161.3049 #define X264_VERSION "r3049 55d517b"

echo "============================================"
echo "Updating ffmpeg-4.4 and x264-161"
rm -rf ffmpeg
rm -rf x264

wget -O- https://www.ffmpeg.org/releases/ffmpeg-4.4.tar.bz2 | tar xj --strip-components=1 --one-top-level=ffmpeg
# wget -O- https://download.videolan.org/pub/videolan/x264/snapshots/x264-snapshot-20191217-2245.tar.bz2 | tar xj --strip-components=1 --one-top-level=x264
git clone https://code.videolan.org/videolan/x264.git --branch stable

# pre-run configure for ffmpeg to create some script file
pushd ffmpeg || return
./configure
popd || exit

echo "======== Completed sub modules update ===================================="

