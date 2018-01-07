#!/bin/bash
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT="$(dirname "${DIR}")"

# export NDK="$(dirname "${PARENT}")"
export NDK="/opt/android/android-sdk/ndk-bundle"
export PATH="$PATH:$NDK"
export PROJECT_JNI="$(dirname "${NDK}")/JNI/app/jni"
export PROJECT_LIBS="$(dirname "${NDK}")/JNI/app/libs"

#export CONFIGURATION="pass here additional configuration flags if you want to"
export ADDI_CFLAGS="-fPIC"
export ADDI_LDFLAGS="-Wl,-z,defs"

export COMMON="\
  --enable-small \
  --enable-cross-compile \
  --disable-doc \
  --enable-shared \
  --enable-protocol=file \
  --enable-pic \
  --disable-symver \
  --enable-libx264 \
  --enable-gpl" 

## Build arm v6 v7a
./build_android_armeabi.sh
./build_android_armeabi-v7a.sh

## Build arm64 v8a
#./build_android_arm64-v8a.sh

## Build x86_64
./build_android_x86.sh

## Build x86_64
#./build_android_x86_64.sh

## Build mips
#./build_android_mips.sh
