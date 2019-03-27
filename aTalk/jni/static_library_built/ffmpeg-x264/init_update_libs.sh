#!/bin/bash

# aTalk v1.7.3 is only compatible with the following module versions:
# a. ffmpeg v1.0.10
# c. x264 - 152

echo "============================================"
echo "Updating ffmpeg-1.0.10 and x264-152"
rm -rf ffmpeg
rm -rf x264

wget -O-  http://ffmpeg.org/releases/ffmpeg-1.0.10.tar.gz | tar xz --strip-components=1 --one-top-level=ffmpeg
wget -O- https://download.videolan.org/pub/videolan/x264/snapshots/x264-snapshot-20180806-2245-stable.tar.bz2 | tar xj --strip-components=1 --one-top-level=x264

echo "======== Completed sub modules update ===================================="

