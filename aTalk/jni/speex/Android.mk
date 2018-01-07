#Backing up previous LOCAL_PATH so it does not screw with the root Android.mk file
LOCAL_PATH_OLD := $(LOCAL_PATH)
LOCAL_PATH := $(call my-dir)
 
include $(CLEAR_VARS)
 
LOCAL_MODULE := libjnspeex
LOCAL_CFLAGS = -DFIXED_POINT -DUSE_KISS_FFT -DEXPORT="" -UHAVE_CONFIG_H

#LOCAL_CFLAGS := -O2 -DHAVE_CONFIG_H
ifeq ($(TARGET_ARCH_ABI),armeabi)
    LOCAL_CFLAGS += -mfpu=vfp -mfloat-abi=softfp
endif
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
#    LOCAL_CFLAGS += -mfpu=neon -mfloat-abi=softfp -mvectorize-with-neon-quad
    LOCAL_CFLAGS += -mfpu=neon -mfloat-abi=softfp -fslp-vectorize-aggressive
    LOCAL_LDFLAGS := -Wl,--fix-cortex-a8
endif

LOCAL_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_LDLIBS := -llog

LOCAL_SRC_FILES :=  \
./libspeex/bits.c \
./libspeex/buffer.c \
./libspeex/cb_search.c \
./libspeex/exc_10_16_table.c \
./libspeex/exc_10_32_table.c \
./libspeex/exc_20_32_table.c \
./libspeex/exc_5_256_table.c \
./libspeex/exc_5_64_table.c \
./libspeex/exc_8_128_table.c \
./libspeex/fftwrap.c \
./libspeex/filterbank.c \
./libspeex/filters.c \
./libspeex/gain_table.c \
./libspeex/gain_table_lbr.c \
./libspeex/hexc_10_32_table.c \
./libspeex/hexc_table.c \
./libspeex/high_lsp_tables.c \
./libspeex/jitter.c \
./libspeex/kiss_fft.c \
./libspeex/kiss_fftr.c \
./libspeex/lpc.c \
./libspeex/lsp.c \
./libspeex/lsp_tables_nb.c \
./libspeex/ltp.c \
./libspeex/mdf.c \
./libspeex/modes.c \
./libspeex/modes_wb.c \
./libspeex/nb_celp.c \
./libspeex/preprocess.c \
./libspeex/quant_lsp.c \
./libspeex/resample.c \
./libspeex/sb_celp.c \
./libspeex/scal.c \
./libspeex/smallft.c \
./libspeex/speex.c \
./libspeex/speex_callbacks.c \
./libspeex/speex_header.c \
./libspeex/stereo.c \
./libspeex/vbr.c \
./libspeex/vq.c \
./libspeex/window.c \
./libogg/bitwise.c \
./libogg/framing.c \
./org_atalk_impl_neomedia_codec_audio_speex_Speex.c

include $(BUILD_SHARED_LIBRARY)

#Putting previous LOCAL_PATH back here
LOCAL_PATH := $(LOCAL_PATH_OLD)