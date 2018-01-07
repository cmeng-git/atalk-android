See link on instruction.
https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e

android libs are based on ffmpeg-3.3.4 built on ubuntu 16.04 and is not compatible with current atalk ffmpeg API call
Version: ffmpeg-3.3.4

# note: commented out those architure you wish to omit.
#Avoid using individual e.g. build_aremabi-v7a.sh as it does not contains some common options
build with: build_all.sh

1. Export environment for ./configure
export NDK=/opt/android/android-sdk/ndk-bundle
export PLATFORM="android-15"
export SYSROOT=$NDK/platforms/$PLATFORM/arch-arm/

2 issue configure command
//=============== armeabi =================
./configure --prefix=./android/armeabi \
 --cross-prefix=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin/arm-linux-androideabi- \
 --target-os=linux \
 --enable-shared \
 --cpu=armv5te \
 --arch=arm \
 --disable-asm \
 --disable-stripping \
 --extra-cflags="-O3 -Wall -pipe -std=c99 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -DANDROID -DNDEBUG-march=armv5te -mtune=arm9tdmi -msoft-float" \
 --sysroot=$NDK/platforms/android-15/arch-arm/

//=============== armeabi-v7a =================
./configure --prefix=./android/armeabi-v7a \
 --cross-prefix=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin/arm-linux-androideabi- \
 --target-os=linux \
 --enable-shared \
 --cpu=armv7-a \
 --arch=arm \
 --disable-asm \
 --disable-stripping \
 --extra-cflags="-O3 -Wall -pipe -std=c99 -ffast-math -fstrict-aliasing -Werror=strict-aliasing -Wno-psabi -Wa,--noexecstack -DANDROID -DNDEBUG-march=armv7-a -mtune=arm9tdmi -msoft-float" \
 --sysroot=$NDK/platforms/android-15/arch-arm/

//=============== arm64-v8a =================
./configure --prefix=./android/arm64-v8a \
 --cross-prefix=$NDK/toolchains/aarch64-linux-android-4.9/prebuilt/linux-x86_64/bin/aarch64-linux-android- \
 --target-os=linux \
 --enable-shared \
 --arch=aarch64 \
 --disable-asm \
 --disable-stripping \
 --extra-cflags="-O3 -DANDROID -Dipv6mr_interface=ipv6mr_ifindex -fasm -Wno-psabi -fno-short-enums -fno-strict-aliasing" \
 --extra-ldflags="-Wl,-rpath-link=$SYSROOT/usr/lib -L$SYSROOT/usr/lib -nostdlib -lc -lm -ldl -llog" \
 --sysroot=$NDK/platforms/android-24/arch-arm64/

//=============== x86 =================
./configure --prefix=./android/x86-64 \
 --cross-prefix=$NDK/toolchains/x86-4.9/prebuilt/linux-x86_64/bin/i686-linux-android- \
 --target-os=linux --enable-shared --cpu=i686 \
 --arch=x86 \
 --enable-yasm \
 --enable-pic \
 --disable-amd3dnow \
 --disable-amd3dnowext \
 --extra-cflags="-std=c99 -O3 -Wall -fpic -pipe -DANDROID -DNDEBUG  -march=atom -msse3 -ffast-math -mfpmath=sse" \
 --extra-ldflags="-lm -lz -Wl,--no-undefined -Wl,-z,noexecstack" \
 --sysroot=$NDK/platforms/android-24/arch-x86/

3. mak clean
4. make -j4
5. make install
6. all libs *.a and *.so and include files are in the predefined prefix

Note:
current aTalk FFmpeg have many deprecated functions etc for ffmpeg version 0.9.0
need clean up on .jni\ffmpeg\org_atalk_impl_neomedia_codec_FFmpeg.c and
atalk-android\aTalk\src\main\java\org\atalk\impl\neomedia\codec\FFmpeg.java