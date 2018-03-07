#!/bin/bash
export DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PARENT="$(dirname "${DIR}")"
# export NDK="$(dirname "${PARENT}")"

export NDK="/opt/android/android-sdk/ndk-bundle"
export PATH="$PATH:$NDK"
export ADDI_CFLAGS="-fPIC"
export ADDI_LDFLAGS="-Wl,-z,defs"

PROJECT_JNI="$(dirname "${NDK}")/JNI/app/jni"
PROJECT_LIBS="$(dirname "${NDK}")/JNI/app/libs"

# CONFIGURATION="pass here additional configuration flags if you want to"
CONFIGURATION=""

COMMON="\
  --enable-small \
  --enable-cross-compile \
  --disable-doc \
  --enable-pic \
  --disable-ffplay \
  --disable-ffprobe \
  --disable-ffserver \
  --enable-protocol=file \
  --disable-symver \
  --enable-libx264 \
  --enable-gpl" 


