#!/bin/bash
# Author: Dmitry Dzakhov (based on Guo Mingyu's script)

# Creating conf.sh in ffmpeg directory
NDK=/opt/android/android-sdk/ndk-bundle
PLATFORM=$NDK/platforms/android-23/arch-arm
PREBUILT=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
output="conf.sh"

[ -f conf.sh ] && echo "Old $output has been removed."
echo '#!/bin/bash' > $output
echo "PREBUILT=$PREBUILT" >> $output
echo "PLATFORM=$PLATFORM" >> $output
echo './configure --target-os=linux \
    --prefix=./android/armv7-a \
    --enable-cross-compile \
    --extra-libs="-lgcc" \
    --arch=arm \
    --cc=$PREBUILT/bin/arm-linux-androideabi-gcc \
    --cross-prefix=$PREBUILT/bin/arm-linux-androideabi- \
    --nm=$PREBUILT/bin/arm-linux-androideabi-nm \
    --sysroot=$PLATFORM \
    --extra-cflags=" -O3 -fpic -DANDROID -DHAVE_SYS_UIO_H=1 -Dipv6mr_interface=ipv6mr_ifindex -fasm -Wno-psabi -fno-short-enums -fno-strict-aliasing -finline-limit=300 -mfloat-abi=softfp -mfpu=neon -marm -march=armv7-a -mtune=cortex-a8 " \
    --disable-shared \
    --enable-static \
    --extra-ldflags="-Wl,-rpath-link=$PLATFORM/usr/lib -L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl -llog" \
    --enable-parsers \
    --enable-encoders \
    --enable-decoders \
    --enable-muxers \
    --enable-demuxers \
    --enable-swscale \
    --enable-swscale-alpha \
    --disable-ffplay \
    --disable-ffprobe \
    --enable-ffserver \
    --enable-network \
    --enable-indevs \
    --disable-bsfs \
    --enable-filters \
    --enable-avfilter \
    --enable-protocols \
    --disable-asm \
    --enable-neon' >> $output

# start configure
sudo chmod +x $output
echo "configuring..."
./$output || (echo configure failed && exit 1)

# modify the config.h
echo "modifying the config.h..."
sed -i "s/#define restrict restrict/#define restrict/g" config.h

# remove static functions in libavutil/libm.h
echo "removing static functions in libavutil/libm.h..."
sed -i "/static/,/}/d" libavutil/libm.h

# modify Makefiles
echo "modifying Makefiles..."
sed -i "/include \$(SUBDIR)..\/subdir.mak/d" libavcodec/Makefile
sed -i "/include \$(SUBDIR)..\/config.mak/d" libavcodec/Makefile
sed -i "/include \$(SUBDIR)..\/subdir.mak/d" libavfilter/Makefile
sed -i "/include \$(SUBDIR)..\/config.mak/d" libavfilter/Makefile
sed -i "/include \$(SUBDIR)..\/subdir.mak/d" libavformat/Makefile
sed -i "/include \$(SUBDIR)..\/config.mak/d" libavformat/Makefile
sed -i "/include \$(SUBDIR)..\/subdir.mak/d" libavutil/Makefile
sed -i "/include \$(SUBDIR)..\/config.mak/d" libavutil/Makefile
sed -i "/include \$(SUBDIR)..\/subdir.mak/d" libpostproc/Makefile
sed -i "/include \$(SUBDIR)..\/config.mak/d" libpostproc/Makefile
sed -i "/include \$(SUBDIR)..\/subdir.mak/d" libswscale/Makefile
sed -i "/include \$(SUBDIR)..\/config.mak/d" libswscale/Makefile

# generate av.mk in ffmpeg
echo "generating av.mk in ffmpeg..."
echo '# LOCAL_PATH is one of libavutil, libavcodec, libavformat, or libswscale

#include $(LOCAL_PATH)/../config-$(TARGET_ARCH).mak
include $(LOCAL_PATH)/../config.mak

OBJS :=
OBJS-yes :=
MMX-OBJS-yes :=
include $(LOCAL_PATH)/Makefile

# collect objects
OBJS-$(HAVE_MMX) += $(MMX-OBJS-yes)
OBJS += $(OBJS-yes)

FFNAME := lib$(BARE_JID)
FFLIBS := $(foreach,BARE_JID,$(FFLIBS),lib$(BARE_JID))
FFCFLAGS  = -DHAVE_AV_CONFIG_H -Wno-sign-compare -Wno-switch -Wno-pointer-sign
FFCFLAGS += -DTARGET_CONFIG=\"config-$(TARGET_ARCH).h\"

ALL_S_FILES := $(wildcard $(LOCAL_PATH)/$(TARGET_ARCH)/*.S)
ALL_S_FILES := $(addprefix $(TARGET_ARCH)/, $(notdir $(ALL_S_FILES)))

ifneq ($(ALL_S_FILES),)
ALL_S_OBJS := $(patsubst %.S,%.o,$(ALL_S_FILES))
C_OBJS := $(filter-out $(ALL_S_OBJS),$(OBJS))
S_OBJS := $(filter $(ALL_S_OBJS),$(OBJS))
else
C_OBJS := $(OBJS)
S_OBJS :=
endif

C_FILES := $(patsubst %.o,%.c,$(C_OBJS))
S_FILES := $(patsubst %.o,%.S,$(S_OBJS))

FFFILES := $(sort $(S_FILES)) $(sort $(C_FILES))' > av.mk

echo 'include $(all-subdir-makefiles)' > ../Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_STATIC_LIBRARIES := libavformat libavcodec libavutil libpostproc libswscale
LOCAL_MODULE := ffmpeg
include $(BUILD_SHARED_LIBRARY)
include $(call all-makefiles-under,$(LOCAL_PATH))' > Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../av.mk
LOCAL_SRC_FILES := $(FFFILES)
LOCAL_C_INCLUDES :=        \
    $(LOCAL_PATH)        \
    $(LOCAL_PATH)/..
LOCAL_CFLAGS += $(FFCFLAGS)
LOCAL_CFLAGS += -include "string.h" -Dipv6mr_interface=ipv6mr_ifindex
LOCAL_LDLIBS := -lz
LOCAL_STATIC_LIBRARIES := $(FFLIBS)
LOCAL_MODULE := $(FFNAME)
include $(BUILD_STATIC_LIBRARY)' > libavformat/Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../av.mk
LOCAL_SRC_FILES := $(FFFILES)
LOCAL_C_INCLUDES :=        \
    $(LOCAL_PATH)        \
    $(LOCAL_PATH)/..
LOCAL_CFLAGS += $(FFCFLAGS)
LOCAL_CFLAGS += -std=c99
LOCAL_LDLIBS := -lz
LOCAL_STATIC_LIBRARIES := $(FFLIBS)
LOCAL_MODULE := $(FFNAME)
include $(BUILD_STATIC_LIBRARY)' > libavcodec/Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../av.mk
LOCAL_SRC_FILES := $(FFFILES)
LOCAL_C_INCLUDES :=        \
    $(LOCAL_PATH)        \
    $(LOCAL_PATH)/..
LOCAL_CFLAGS += $(FFCFLAGS)
LOCAL_STATIC_LIBRARIES := $(FFLIBS)
LOCAL_MODULE := $(FFNAME)
include $(BUILD_STATIC_LIBRARY)' > libavfilter/Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../av.mk
LOCAL_SRC_FILES := $(FFFILES)
LOCAL_C_INCLUDES :=        \
    $(LOCAL_PATH)        \
    $(LOCAL_PATH)/..
LOCAL_CFLAGS += $(FFCFLAGS)
LOCAL_STATIC_LIBRARIES := $(FFLIBS)
LOCAL_MODULE := $(FFNAME)
include $(BUILD_STATIC_LIBRARY)' > libavutil/Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../av.mk
LOCAL_SRC_FILES := $(FFFILES)
LOCAL_C_INCLUDES :=        \
    $(LOCAL_PATH)        \
    $(LOCAL_PATH)/..
LOCAL_CFLAGS += $(FFCFLAGS)
LOCAL_STATIC_LIBRARIES := $(FFLIBS)
LOCAL_MODULE := $(FFNAME)
include $(BUILD_STATIC_LIBRARY)' > libpostproc/Android.mk

echo 'LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
include $(LOCAL_PATH)/../av.mk
LOCAL_SRC_FILES := $(FFFILES)
LOCAL_C_INCLUDES :=        \
    $(LOCAL_PATH)        \
    $(LOCAL_PATH)/..
LOCAL_CFLAGS += $(FFCFLAGS)
LOCAL_STATIC_LIBRARIES := $(FFLIBS)
LOCAL_MODULE := $(FFNAME)
include $(BUILD_STATIC_LIBRARY)' > libswscale/Android.mk

# start build!
echo "start ndk-building..."
cd ../..
$NDK/ndk-build
# Change previous line to "$NDK/ndk-build V=1" if you'll get errors. This will give some more information.
