#
# aTalk, the OpenSource Java VoIP and Instant Messaging client.
#
# Distributable under LGPL license.
# See terms of license at gnu.org.
#
# After build outputs should be moved from libs/armeabi to lib/native/armeabi
# cmeng - build.gradle auto install *.so to libs/$(TARGET_ARCH_ABI)

ROOT := $(call my-dir)
LOCAL_PATH := $(call my-dir)

# ================================= JAWT Renderer =====================================
### JAWT Renderer library build
LOCAL_PATH := $(ROOT)
include $(CLEAR_VARS)
include ./jawtrenderer/Android.mk

# ================================= OpenSLES ==========================================
### OpenSLES library build with android intrinsic openSL_ES library
LOCAL_PATH := $(ROOT)
include $(CLEAR_VARS)
include ./opensles/Android.mk

# ================================= FFMPEG ============================================
LOCAL_PATH := $(ROOT)
include $(CLEAR_VARS)

### FFMPEG library build using single full compiled shared library jnffmpeg.so
#LOCAL_MODULE := jnffmpeg
#LOCAL_SRC_FILES := ./libs/libjnffmpeg.so
#include $(PREBUILT_SHARED_LIBRARY)

### FFMPEG library build using static libraries (v1.0.10) pre-built on ubuntu
include ./ffmpeg/Android_a.mk

### FFMPEG library build using shared libraries (v1.0.10) - multiple .so generated
#include ./ffmpeg/Android_so.mk

### FFMPEG Shared Library built from source (see version.h) - not working
#include ./ffmpeg/Android.mk

# ================================= Openmax-h264 ======================================
### H264 library build using android internal library
LOCAL_PATH := $(ROOT)
include $(CLEAR_VARS)
#include ./h264/Android.mk

# ================================= LibVPX (VP8 / VP9) ================================
### VPX shared library build using static libraries pre-built from source (v1.6.1+) in Ubuntu 16.04
LOCAL_PATH := $(ROOT)
include $(CLEAR_VARS)
include ./vpx/Android.mk

# ================================= Opus ==============================================
### Opus sources is in directory jni/opus
LOCAL_PATH := $(ROOT)
include $(CLEAR_VARS)
OPUS_DIR   := opus
include $(OPUS_DIR)/Android.mk

# LOCAL_MODULE        := jnopus
# LOCAL_SRC_FILES     := $(LOCAL_PATH)/$(OPUS_DIR)/org_atalk_impl_neomedia_codec_audio_opus_Opus.c
# LOCAL_CFLAGS        := -DNULL=0
# LOCAL_LDLIBS        := -lm -llog
# LOCAL_C_INCLUDES    := $(LOCAL_PATH)/$(OPUS_DIR)/include
# LOCAL_SHARED_LIBRARIES := opus
# include $(BUILD_SHARED_LIBRARY)

# ================================= Speex =============================================
### Speex shared library build - using source (speex: v1.2.0rc1 and libogg: v1.3.2)
LOCAL_PATH := $(ROOT)
SPEEX_DIR  := speex
include ./speex/Android.mk

# ================================= G722 ==============================================
### Refer to libjitsi and jitsi-lgpl-dependency for support if so required
### G722 library build (cmeng - removed support for jn722, incomplete libjn722.so provided)
# https://www.itu.int/rec/dologin_pub.asp?lang=e&id=T-REC-G.722-201209-I!!SOFT-ZST-E&type=items
# LOCAL_PATH := $(ROOT)
# include $(CLEAR_VARS)
# LOCAL_MODULE := jng722
# LOCAL_SRC_FILES := libjng722.so
# include $(PREBUILT_SHARED_LIBRARY)

# ================================= OpenSSL ===========================================
### OpenSSL shared library build (source in jni/openssl)
# Contains both static libraries-1.0.2r pre-built on Ubuntu 18.04 and
# Reference only: sources-1.0.1j from site (Android_src.mk - build has error using AS ndk on Windows 7)

LOCAL_PATH  := $(ROOT)
include $(CLEAR_VARS)

# https://github.com/aosp-mirror/platform_external_openssl/tree/android-5.1.1_r38
# Local AS NDK built from source (OPENSSL_VERSION=1.0.1j) - problem but build ok on Ubuntu Android Studio
# include openssl/Android.mk

## Built static library from source (version 1.0.2u) on ubuntu 18.04
# Built from static library (unable to build from source in jni - #TODO)
include ./openssl/Android_a.mk


