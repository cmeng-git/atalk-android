#!/bin/bash
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT="$(dirname "${DIR}")"
# export NDK="$(dirname "${PARENT}")"

export NDK="/opt/android/android-sdk/ndk-bundle"
export PATH="$PATH:$NDK"

PROJECT_JNI="$(dirname "${NDK}")/JNI/app/jni"
PROJECT_LIBS="$(dirname "${NDK}")/JNI/app/libs"
