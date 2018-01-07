# Copyright 2006 The Android Open Source Project

LOCAL_PATH := $(call my-dir)

local_c_includes :=
local_cflags :=

local_additional_dependencies := $(LOCAL_PATH)/android-config.mk $(LOCAL_PATH)/Apps.mk

include $(LOCAL_PATH)/Apps-config.mk

#######################################
include $(CLEAR_VARS)
LOCAL_SHARED_LIBRARIES := libssl libcrypto
LOCAL_CLANG := true
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := openssl
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES  := $(target_src_files)
LOCAL_C_INCLUDES := $(target_c_includes)
LOCAL_CFLAGS := $(target_cflags)
include $(LOCAL_PATH)/android-config.mk
include $(BUILD_EXECUTABLE)

#######################################
include $(CLEAR_VARS)
LOCAL_SHARED_LIBRARIES := libssl-host libcrypto-host
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := openssl
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES  := $(host_src_files)
LOCAL_C_INCLUDES := $(host_c_includes)
LOCAL_CFLAGS := $(host_cflags)
include $(LOCAL_PATH)/android-config.mk
include $(BUILD_HOST_EXECUTABLE)
