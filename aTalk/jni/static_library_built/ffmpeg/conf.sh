#!/bin/bash
PREBUILT=/opt/android/android-sdk/ndk-bundle/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
PLATFORM=/opt/android/android-sdk/ndk-bundle/platforms/android-23/arch-arm
./configure --target-os=linux \
    --prefix=./android/armv7-a \
    --enable-cross-compile \
    --extra-libs="-lgcc" \
    --arch=arm \
    --cc=$PREBUILT/bin/arm-linux-androideabi-gcc \
    --cross-prefix=$PREBUILT/bin/arm-linux-androideabi- \
    --nm=$PREBUILT/bin/arm-linux-androideabi-nm \
    --sysroot=$PLATFORM \
    --extra-cflags=" -O3 -fpic -DANDROID -DHAVE_SYS_UIO_H=1 -Dipv6mr_interface=ipv6mr_ifindex -fasm -Wno-psabi -fno-short-enums -fno-strict-aliasing -finline-limit=300 -mfloat-abi=softfp -mfpu=neon -marm -march=armv7-a -mtune=cortex-a8 " \
    --disable-shared \
    --enable-static \
    --extra-ldflags="-Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl -llog" \
    --enable-parsers \
    --enable-encoders \
    --enable-decoders \
    --enable-muxers \
    --enable-demuxers \
    --enable-swscale \
    --enable-swscale-alpha \
    --disable-ffplay \
    --disable-ffprobe \
    --enable-ffserver \
    --enable-network \
    --enable-indevs \
    --disable-bsfs \
    --enable-filters \
    --enable-avfilter \
    --enable-protocols \
    --disable-asm \
    --enable-neon
