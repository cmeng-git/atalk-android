# see https://developer.android.com/ndk/guides/application_mk.html
# http://mobilepearls.com/labs/native-android-api/ndk/docs/APPLICATION-MK.html

# The setting can also ne defined in build.gradle ndk 'APP_PLATFORM=android-15' which takes priority over this value
# APP_PLATFORM=android-15 for aTalk minimum support SDK platform i.e. api-15
# see https://github.com/android-ndk/ndk/issues/543
# https://android.googlesource.com/platform/ndk/+/master/docs/user/common_problems.md#using-mismatched-prebuilt-libraries
APP_PLATFORM := android-21

# https://developer.android.com/ndk/guides/abis.html
# We recommend setting APP_ABI := all for all targets. For specific target explicitly, use
# armeabi: ARMv5/ARMv6  (deprecated acchitecture support from google 2017/12/01)
# armeabi-v7a: ARMv7,
# arm64-v8a: ARMv8 AArch64
# x86 x86_64: Intel Atom
# mips mips64: MIPS.
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64

# An application should not use more than one C++ runtime. The various STLs are not compatible with one another.
# Note: The exception to this rule is that "no STL" does not count as an STL.
# You can safely use C only libraries (or even the system runtime, since it is not an STL)
# in the same application as an STL. This rule only applies to libc++, gnustl, and stlport.
# cmeng: Use system for smaller shared library generation and safely compatible with any pre-built libraries

# APP_STL := gnustl_static |  c++_static | c++_shared
APP_STL := c++_shared

# Enforced the support for Exceptions and RTTI in all generated machine code.
APP_CPPFLAGS := -frtti -fexceptions


# https://developer.android.com/ndk/guides/ndk-build.html #Debuggable versus Release builds
# Automatically set by sdk build - SDK r8 (or higher)
# Table 1. Results of NDK_DEBUG (command line) and android:debuggable (manifest) combinations.
# Manifest Setting        	NDK_DEBUG=0 	NDK_DEBUG=1	    NDK_DEBUG not specified
#android:debuggable="true" 	Debug; Symbols; Optimized*1 	Debug; Symbols; Not optimized*2	(same as NDK_DEBUG=1)
#android:debuggable="false"	Release; Symbols; Optimized 	Release; Symbols; Not optimized	Release; No symbols; Optimized*3
NDK_DEBUG := false

NDK_TOOLCHAIN_VERSION := clang
