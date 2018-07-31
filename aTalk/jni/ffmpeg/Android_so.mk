# The shared libraries are built based on the scripts in the following site but with ffmpeg 3.3.3
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

# ========== libpostproc ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libpostproc
LOCAL_SRC_FILES:= $(LOCAL_LIB_PATH)/lib/libpostproc.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== jnffmpeg (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnffmpeg
LOCAL_LDLIBS += -llog -lz
LOCAL_SHARED_LIBRARIES := libavcodec libavdevice libavfilter libavformat libavutil libswresample libswscale
LOCAL_C_INCLUDES := \
 $(LOCAL_LIB_PATH)/include \
 $(LOCAL_LIB_PATH)/include/libavcodec \
 $(LOCAL_LIB_PATH)/include/libavdevice \
 $(LOCAL_LIB_PATH)/include/libavfilter \
 $(LOCAL_LIB_PATH)/include/libavutil \
 $(LOCAL_LIB_PATH)/include/libswresample \
 $(LOCAL_LIB_PATH)/include/libswscale
LOCAL_SRC_FILES := ./org_atalk_impl_neomedia_codec_FFmpeg.c
#LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -w

include $(BUILD_SHARED_LIBRARY)

# $(call import-module,ffmpeg/android/$(CPU)) // path to NDK module relative to NDK/sources/
