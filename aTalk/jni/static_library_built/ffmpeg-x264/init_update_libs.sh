#!/bin/bash

# aTalk v1.8.1 is compatible with the following module versions:
# a. ffmpeg-v3.4
# c. x264-v157

echo "============================================"
echo "Updating ffmpeg-3.4 and x264-157"
rm -rf ffmpeg
rm -rf x264

wget -O-  https://www.ffmpeg.org/releases/ffmpeg-3.4.6.tar.bz2 | tar xj --strip-components=1 --one-top-level=ffmpeg
wget -O- https://download.videolan.org/pub/videolan/x264/snapshots/x264-snapshot-20190407-2245-stable.tar.bz2 | tar xj --strip-components=1 --one-top-level=x264

echo "======== Completed sub modules update ===================================="

