#Backing up previous LOCAL_PATH so it does not screw with the root Android.mk file
LOCAL_PATH_OLD := $(LOCAL_PATH)
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := jng722

LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CFLAGS += -mfpu=neon -mfloat-abi=softfp -fslp-vectorize-aggressive
    LOCAL_LDFLAGS := -Wl,--fix-cortex-a8
endif

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/g722

LOCAL_LDLIBS := -lm -llog

LOCAL_SRC_FILES := \
    g722/g722_enc_dec.c \
    g722/vector_int.c \
    JNIDecoder.c \
    JNIEncoder.c

include $(BUILD_SHARED_LIBRARY)

#Putting previous LOCAL_PATH back here
LOCAL_PATH := $(LOCAL_PATH_OLD)
