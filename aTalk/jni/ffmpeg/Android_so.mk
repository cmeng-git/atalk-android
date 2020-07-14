# The shared libraries ffmpeg v1.0.10 are built based on the scripts in the following site (ffmpeg 3.3.3)
# https://github.com/IljaKosynkin/FFmpeg-Development-Kit/tree/master
# see => https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e

LOCAL_PATH:= $(call my-dir)
LOCAL_LIB_PATH = android/$(TARGET_ARCH_ABI)

# ========== libavcodec ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavcodec
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavcodec.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== libavdevice ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavdevice
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavdevice.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== libavfilter ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavfilter
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavfilter.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== libavformat ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libavformat
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavformat.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:= libavutil
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libavutil.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== libswresample ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libswresample
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libswresample.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== libswscale ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libswscale
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libswscale.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# https://trac.ffmpeg.org/wiki/Postprocessing
# Anyway, most of the time it won't help to postprocess h.264, HEVC, VP8, or VP9 video.
# ========== libpostproc ==================
# include $(CLEAR_VARS)
# LOCAL_MODULE:= libpostproc
# LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libpostproc.so
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
# include $(PREBUILT_SHARED_LIBRARY)

# ========== libx264 ==================
# Linking with versioned shared library in Android NDK
include $(CLEAR_VARS)
LOCAL_MODULE:= libx264
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libx264_152.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== jnffmpeg (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnffmpeg
LOCAL_LDLIBS += -llog -lz
LOCAL_SHARED_LIBRARIES := libavcodec libavdevice libavfilter libavutil libavformat  libswresample libswscale libx264
# Must use exact format prefix with $(LOCAL_PATH) below to work - $(LOCAL_LIB_PATH)/include not working
LOCAL_C_INCLUDES := $(LOCAL_PATH)/android/$(TARGET_ARCH_ABI)/include
LOCAL_SRC_FILES := ./FFmpeg.c
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -w

include $(BUILD_SHARED_LIBRARY)

# $(call import-module,ffmpeg/android/$(CPU)) // path to NDK module relative to NDK/sources/
