#Backing up previous LOCAL_PATH so it does not screw with the root Android.mk file
LOCAL_PATH_OLD := $(LOCAL_PATH)
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := jnbcg729

LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H

ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
    LOCAL_CFLAGS += -mfpu=neon -mfloat-abi=softfp -fslp-vectorize-aggressive
    LOCAL_LDFLAGS := -Wl,--fix-cortex-a8
endif

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/bcg729/include

LOCAL_LDLIBS := -lm -llog

LOCAL_SRC_FILES := \
    bcg729/src/LP2LSPConversion.c \
    bcg729/src/LPSynthesisFilter.c \
    bcg729/src/LSPQuantization.c \
    bcg729/src/adaptativeCodebookSearch.c \
    bcg729/src/codebooks.c \
    bcg729/src/computeAdaptativeCodebookGain.c \
    bcg729/src/computeLP.c \
    bcg729/src/computeWeightedSpeech.c \
    bcg729/src/decodeAdaptativeCodeVector.c \
    bcg729/src/decodeFixedCodeVector.c \
    bcg729/src/decodeGains.c \
    bcg729/src/decodeLSP.c \
    bcg729/src/decoder.c \
    bcg729/src/encoder.c \
    bcg729/src/findOpenLoopPitchDelay.c \
    bcg729/src/fixedCodebookSearch.c \
    bcg729/src/gainQuantization.c \
    bcg729/src/interpolateqLSP.c \
    bcg729/src/postFilter.c \
    bcg729/src/postProcessing.c \
    bcg729/src/preProcessing.c \
    bcg729/src/qLSP2LP.c \
    bcg729/src/utils.c \
    bcg729/src/cng.c \
    bcg729/src/vad.c \
    bcg729/src/dtx.c \
    JNIDecoder.c \
    JNIEncoder.c

include $(BUILD_SHARED_LIBRARY)

#Putting previous LOCAL_PATH back here
LOCAL_PATH := $(LOCAL_PATH_OLD)
