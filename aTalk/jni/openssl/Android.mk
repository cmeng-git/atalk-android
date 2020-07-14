LOCAL_PATH := $(call my-dir)

# Enable to be able to use ALOG* with #include "cutils/log.h"
#log_c_includes += system/core/include
#log_shared_libraries := liblog

# These makefiles are here instead of being Android.mk files in the
# respective crypto, ssl, and apps directories so
# that import_openssl.sh import won't remove them.

HOST_OS := linux

include $(LOCAL_PATH)/build-config.mk
include $(LOCAL_PATH)/Crypto.mk
include $(LOCAL_PATH)/Ssl.mk

# ========== jnopenssl (.so library) ==================
$(info ### Building Shared Library jnopnessl from source: $(TARGET_ARCH_ABI) ###)

include $(CLEAR_VARS)
LOCAL_MODULE := jnopenssl
LOCAL_LDLIBS := -llog -lz
LOCAL_STATIC_LIBRARIES := libcrypto libssl
LOCAL_SRC_FILES := \
 Hmac.c \
 OpenSslWrapperLoader.c \
 SrtpCipherCtrOpenSsl.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H -Wdeprecated-declarations

include $(BUILD_SHARED_LIBRARY)
