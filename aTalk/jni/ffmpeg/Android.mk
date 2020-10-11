LOCAL_PATH := $(call my-dir)

# ========== libavcodec ==================
include $(CLEAR_VARS)
LOCAL_MODULE := libavcodec
LOCAL_SRC_FILES := ./local/armeabi/libavcodec.a
LOCAL_LDFLAGS := -Wl,-rpath-link=/home/dmitrydzz/android-ndk/platforms/android-14/arch-arm/usr/lib/ -rpath-link=/home/dmitrydzz/android-ndk/platforms/android-14/arch-arm/usr/lib/
#LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
LOCAL_LDLIBS := -lz -lm -llog -lc -L$(call host-path, $(LOCAL_PATH))/$(TARGET_ARCH_ABI) -landprof
include $(PREBUILT_STATIC_LIBRARY)

# ========== libavformat ==================
include $(CLEAR_VARS)
LOCAL_MODULE := libavformat
LOCAL_LDFLAGS := -Wl,-rpath-link=/home/dmitrydzz/android-ndk/platforms/android-14/arch-arm/usr/lib/ -rpath-link=/home/dmitrydzz/android-ndk/platforms/android-14/arch-arm/usr/lib/
LOCAL_SRC_FILES := ./local/armeabi/libavformat.a
#LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
LOCAL_LDLIBS := -lz -lm -llog -lc -L$(call host-path, $(LOCAL_PATH))/$(TARGET_ARCH_ABI) -landprof
include $(PREBUILT_STATIC_LIBRARY)

# ========== libavutil ==================+
include $(CLEAR_VARS)
LOCAL_MODULE := libavutil
LOCAL_SRC_FILES := ./local/armeabi/libavutil.a
#LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
include $(PREBUILT_STATIC_LIBRARY)

# ========== libpostproc ==================
# include $(CLEAR_VARS)
# LOCAL_MODULE := libpostproc
# LOCAL_SRC_FILES := ./local/armeabi/libpostproc.a
# LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
# include $(PREBUILT_STATIC_LIBRARY)

# ========== libswscale ==================
include $(CLEAR_VARS)
LOCAL_MODULE := libswscale
LOCAL_SRC_FILES := ./local/armeabi/libswscale.a
#LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
include $(PREBUILT_STATIC_LIBRARY)

# ========== ffmpeg (.so library) ==================
include $(CLEAR_VARS)
LOCAL_MODULE := jnffmpeg

LOCAL_LDLIBS += -llog -lz
# LOCAL_STATIC_LIBRARIES := libavformat libavcodec libpostproc libswscale libavutil
LOCAL_STATIC_LIBRARIES := libavcodec libavutil libavformat libpostproc libswscale 
LOCAL_C_INCLUDES += $(LOCAL_PATH)/ffmpeg
LOCAL_SRC_FILES := FFmpegc
#LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
include $(BUILD_SHARED_LIBRARY)

LOCAL_PATH := $(call my-dir)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/ffmpeg
include $(all-subdir-makefiles)
