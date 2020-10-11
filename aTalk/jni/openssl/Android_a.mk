LOCAL_PATH := $(call my-dir)

############### crypto_static ########################
# target static library
include $(CLEAR_VARS)
LOCAL_MODULE := crypto_static
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libcrypto.a
include $(PREBUILT_STATIC_LIBRARY)

############### ssl_static ########################
# target static library
include $(CLEAR_VARS)
LOCAL_MODULE := ssl_static
LOCAL_SRC_FILES := android/$(TARGET_ARCH_ABI)/lib/libssl.a
include $(PREBUILT_STATIC_LIBRARY)

# ========== jnopenssl (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnopenssl
LOCAL_LDLIBS := -llog -lz
LOCAL_STATIC_LIBRARIES := crypto_static ssl_static
LOCAL_SRC_FILES := \
 Hmac.c \
 OpenSslWrapperLoader.c \
 SrtpCipherCtrOpenSsl.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations

include $(BUILD_SHARED_LIBRARY)
