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
export COMMON="--disable-doc --enable-shared --enable-protocol=file --enable-pic --enable-small"
./build_armeabi.sh
./build_armeabi-v7a.sh
#./build_arm64-v8a.sh
#./build_mips.sh
./build_x86.sh
#./build_x86_64.sh
