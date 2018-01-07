#!/bin/bash
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT="$(dirname "${DIR}")"
# export NDK="$(dirname "${PARENT}")"
export NDK="/opt/android/android-sdk/ndk-bundle"
export PATH="$PATH:$NDK"
export PROJECT_JNI="$(dirname "${NDK}")/JNI/app/jni"
export PROJECT_LIBS="$(dirname "${NDK}")/JNI/app/libs"

## Build arm v6 v7a
./build_android_arm.sh

## Build arm64 v8a
#./build_android_arm64-v8a.sh

## Build x86
./build_android_x86.sh

## Build x86_64
#./build_android_x86_64.sh

## Build mips
#./build_android_mips.sh
