#Backing up previous LOCAL_PATH so it does not screw with the root Android.mk file
LOCAL_PATH := $(call my-dir)/android

# ========== libavdevice ==================
include $(CLEAR_VARS)
LOCAL_MODULE:= libspeex
LOCAL_SRC_FILES:= lib/libspeex.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

# ========== jnffmpeg (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnspeex
LOCAL_LDLIBS += -llog -lz
LOCAL_SHARED_LIBRARIES :=  libspeex
LOCAL_C_INCLUDES := ./include
LOCAL_SRC_FILES := ./org_atalk_impl_neomedia_codec_audio_speex_Speex.c
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations

include $(BUILD_SHARED_LIBRARY)
# $(call import-module,ffmpeg/android/$(CPU)) // path to NDK module relative to NDK/sources/
# see => https://medium.com/@ilja.kosynkin/building-ffmpeg-for-android-607222677a9e
