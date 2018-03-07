#!/bin/bash

unset ANDROID_API
unset CROSS_COMPILE
export NDK_ROOT="/opt/android/android-sdk/ndk-bundle"

TOOLS_ROOT=`pwd`

# cmeng: Never mix two api level to build static library for use on the same apk.
# Set to API:-15 for aTalk minimun support for platform API-15

ANDROID_API=${ANDROID_API:-15}

# ARCHS=("android" "android-armeabi" "android64-aarch64" "android-x86" "android64" "android-mips" "android-mips64")
# ABIS=("armeabi" "armeabi-v7a" "arm64-v8a" "x86" "x86_64" "mips" "mips64")
ARCHS=("android" "android-armeabi" "android-x86" "android-mips")
ABIS=("armeabi" "armeabi-v7a" "x86" "mips")

NDK=${NDK_ROOT}
configure() {
  ARCH=$1; OUT=$2;
  TOOLCHAIN_ROOT=${TOOLS_ROOT}/${OUT}-android-toolchain

  if [ "$ARCH" == "android" ]; then
    export ARCH_FLAGS="-mthumb -finline-limit=64"
    export ARCH_LINK=""
    export TOOL="arm-linux-androideabi"
    export ASFLAGS=""
    NDK_FLAGS="--arch=arm"
  elif [ "$ARCH" == "android-armeabi" ]; then
    export ARCH_FLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -mthumb -mfpu=neon"
    export ARCH_LINK="-march=armv7-a -Wl,--fix-cortex-a8"
    export TOOL="arm-linux-androideabi"
    export ASFLAGS=""
    NDK_FLAGS="--arch=arm"
  elif [ "$ARCH" == "android64-aarch64" ]; then
    export ARCH_FLAGS=""
    export ARCH_LINK=""
    export TOOL="aarch64-linux-android"
    export ASFLAGS=""
    NDK_FLAGS="--arch=arm64"
  elif [ "$ARCH" == "android-x86" ]; then
    export ARCH_FLAGS="-O2 -march=i686 -mtune=intel -msse3 -mfpmath=sse -m32"
    export ARCH_LINK=""
    export TOOL="i686-linux-android"
    export ASFLAGS="-D__ANDROID__"
    NDK_FLAGS="--arch=x86"
  elif [ "$ARCH" == "android64" ]; then
    export ARCH_FLAGS="-O2 -march=x86-64 -msse4.2 -mpopcnt -m64 -mtune=intel"
    export ARCH_LINK=""
    export TOOL="x86_64-linux-android"
    export ASFLAGS="-D__ANDROID__"
    NDK_FLAGS="--arch=x86_64"
  elif [ "$ARCH" == "android-mips" ]; then
    export ARCH_FLAGS=""
    export ARCH_LINK=""
    export TOOL="mipsel-linux-android"
    export ASFLAGS=""
    NDK_FLAGS="--arch=mips"
  elif [ "$ARCH" == "android-mips64" ]; then
    export ARCH="linux64-mips64"
    export ARCH_FLAGS=""
    export ARCH_LINK=""
    export TOOL="mips64el-linux-android"
    export ASFLAGS=""
    NDK_FLAGS="--arch=mips64"
  fi;

# cmeng: must ensure AS JNI uses the same STL library or "system"
  [ -d ${TOOLCHAIN_ROOT} ] || python $NDK/build/tools/make_standalone_toolchain.py \
                                     --api ${ANDROID_API} \
                                     --stl libc++ \
                                     --install-dir=${TOOLCHAIN_ROOT} \
                                     $NDK_FLAGS

  export TOOLCHAIN_PATH=${TOOLCHAIN_ROOT}/bin
  export NDK_TOOLCHAIN_BASENAME=${TOOLCHAIN_PATH}/${TOOL}
  export SYSROOT=${TOOLCHAIN_ROOT}/sysroot
  export CROSS_SYSROOT=$SYSROOT
  export CC=${NDK_TOOLCHAIN_BASENAME}-clang
  export CXX=${NDK_TOOLCHAIN_BASENAME}-clang++
  export LINK=${CXX}
  export LD=${NDK_TOOLCHAIN_BASENAME}-ld
  export AR=${NDK_TOOLCHAIN_BASENAME}-ar
  export RANLIB=${NDK_TOOLCHAIN_BASENAME}-ranlib
  export STRIP=${NDK_TOOLCHAIN_BASENAME}-strip
# export CPPFLAGS=${CPPFLAGS:-""}
  export LIBS=${LIBS:-""}
  export CFLAGS="${ARCH_FLAGS} -fpic -ffunction-sections -funwind-tables -fstack-protector -fno-strict-aliasing"
  export CXXFLAGS="${CFLAGS} -std=c++11 -frtti -fexceptions"
  export LDFLAGS="${ARCH_LINK}"

  echo "**********************************************"
  echo "use ANDROID_API=${ANDROID_API}"
  echo "use NDK=${NDK}"
  echo "export ARCH=${ARCH}"
  echo "export NDK_TOOLCHAIN_BASENAME=${NDK_TOOLCHAIN_BASENAME}"
  echo "export SYSROOT=${SYSROOT}"
  echo "export CC=${CC}"
  echo "export CXX=${CXX}"
  echo "export LINK=${LINK}"
  echo "export LD=${LD}"
  echo "export AR=${AR}"
  echo "export RANLIB=${RANLIB}"
  echo "export STRIP=${STRIP}"
# echo "export CPPFLAGS=${CPPFLAGS}"
  echo "export CFLAGS=${CFLAGS}"
  echo "export CXXFLAGS=${CXXFLAGS}"
  echo "export LDFLAGS=${LDFLAGS}"
  echo "export LIBS=${LIBS}"
  echo "export ASFLAGS=${ASFLAGS}"
  echo "**********************************************"
}
