#!/bin/bash
. _settings.sh $*

pushd openh264

h264_API="$(grep 'FULL_VERSION :=' < Makefile | sed 's/^.* \([0-9]\.[0-9]\.[0-9]\).*$/\1/')"
echo -e "\n\n** BUILD STARTED: openh264-v${h264_API} for ${1} **"

make clean
CONFIGURE="OS=android NDKROOT=${ANDROID_NDK} TARGET=android-${ANDROID_API} NDKLEVEL=${ANDROID_API} ARCH=${NDK_ARCH} PREFIX=${PREFIX}"
make -j${HOST_NUM_CORES} install ${CONFIGURE} || exit 1

popd
echo -e "** BUILD COMPLETED: openh264-v${h264_API} for ${1} **\n"

# h264 built for ABIS=("armeabi-v7a" "x86" "x86_64") has problem
# androideabi/bin/ld: error: codec/common/src/cpu-features.o: incompatible target
# codec/common/src/cpu.o:cpu.cpp:function WelsCPUFeatureDetect: error: undefined reference to 'wels_getCpuFamily'
# codec/common/src/cpu.o:cpu.cpp:function WelsCPUFeatureDetect: error: undefined reference to 'wels_getCpuFeatures'
# codec/common/src/cpu.o:cpu.cpp:function WelsCPUFeatureDetect: error: undefined reference to 'wels_getCpuCount'
# codec/common/src/WelsThreadLib.o:WelsThreadLib.cpp:function WelsQueryLogicalProcessInfo: error: undefined reference to 'wels_getCpuCount'