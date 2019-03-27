LOCAL_PATH := $(call my-dir)

############### libvpx_static ########################
# target static library
include $(CLEAR_VARS)
LOCAL_MODULE := vpx_static
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libvpx.a
# LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_STATIC_LIBRARY)

# ========== jnvpx (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnvpx
LOCAL_LDLIBS := -llog -lz
# for x-86 shared library built warning
# LOCAL_LDLIBS += -Wl,--no-warn-shared-textrel
LOCAL_STATIC_LIBRARIES := vpx_static
LOCAL_SRC_FILES := \
 org_atalk_impl_neomedia_codec_video_VPX.c
# org_atalk_impl_neomedia_recording_WebmWriter.cc
LOCAL_C_INCLUDES := $(LOCAL_PATH)/android/$(TARGET_ARCH_ABI)/include
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations

include $(BUILD_SHARED_LIBRARY)
