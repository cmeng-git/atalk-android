# ========== OpenMax (jnopenmax.so library) ==================
### H264 Shared library build
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := jnopenmax
# LOCAL_LDLIBS    := -lOpenSLES -llog
LOCAL_LDLIBS    := -llog
LOCAL_CFLAGS    := -I../android/platform/frameworks/base/include/media/stagefright/openmax
LOCAL_LDFLAGS += "-Wl,-z,max-page-size=16384"
LOCAL_C_INCLUDES :=  ../android/platform/frameworks/native/include/media/OpenMAX/OMX_Component.h
LOCAL_SRC_FILES := H264_OMXDecoder.c
include $(BUILD_SHARED_LIBRARY)