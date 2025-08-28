LOCAL_PATH_OLD := $(LOCAL_PATH)
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_LDFLAGS += "-Wl,-z,max-page-size=16384"
LOCAL_C_INCLUDES    := $(LOCAL_PATH)
# LOCAL_C_INCLUDES := $(LOCAL_PATH)/android/$(TARGET_ARCH_ABI)/include

LOCAL_SRC_FILES := vpx_jni.c

# Build shared library
ENABLE_SHARED := 1
include $(LOCAL_PATH)/libvpx/build/make/Android.mk

$(info Build Local_C_src: $(LOCAL_SRC_FILES))

# Putting previous LOCAL_PATH back here
LOCAL_PATH := $(LOCAL_PATH_OLD)