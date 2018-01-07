LOCAL_PATH := $(call my-dir)

### FFMPEG library build: static libraries are in jni/ffmpeg/android ...
include $(CLEAR_VARS)
LOCAL_PATH := $(ROOT)
FFMPEG_DIR := ffmpeg/android/$(TARGET_ARCH_ABI)/lib/

LOCAL_CFLAGS    := -I${ffmpeg} -D_XOPEN_SOURCE=600
LOCAL_LDLIBS    := -L${ffmpeg}/libavcodec -L${ffmpeg}/libavfilter -L${ffmpeg}/libavformat -L${ffmpeg}/libavutil -L${ffmpeg}/libswscale -L${x264} -lavformat -lavcodec -lavfilter -lavutil -lswscale -lx264
LOCAL_MODULE    := jnffmpeg
LOCAL_SRC_FILES := $(LOCAL_PATH)/$(FFMPEG_DIR)/org_atalk_impl_neomedia_codec_FFmpeg.c
include $(BUILD_SHARED_LIBRARY)