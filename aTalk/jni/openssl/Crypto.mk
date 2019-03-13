local_cflags :=

local_c_includes := $(log_c_includes)

local_additional_dependencies := $(LOCAL_PATH)/android-config.mk $(LOCAL_PATH)/Crypto.mk

include $(LOCAL_PATH)/Crypto-config.mk

############ Target Static Library ###########################
# The static library should be used in only unbundled apps
# target static library
include $(CLEAR_VARS)
# LOCAL_SHORT_COMMANDS := true

LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libcrypto
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)
LOCAL_SRC_FILES += $(target_src_files)
LOCAL_C_INCLUDES += $(target_c_includes) 
LOCAL_CFLAGS += $(target_cflags)
include $(LOCAL_PATH)/android-config.mk

# $(info cIncludes: $(LOCAL_C_INCLUDES))
# $(info cFlags: $(LOCAL_CFLAGS))
include $(BUILD_STATIC_LIBRARY)

### //////////////// Not use in android ////////////////// ###
############ Target Shared Library ###########################
# target shared library
include $(CLEAR_VARS)
# /usr/local/src/androidLOCAL_SHORT_COMMANDS := true

# If we're building an unbundled build, don't try to use clang since it's not in the NDK yet.
# This can be removed when a clang version that is fast enough in the NDK.
ifeq (,$(TARGET_BUILD_APPS))
LOCAL_CLANG := true
else
LOCAL_SDK_VERSION := 21
endif
LOCAL_CLANG := true

LOCAL_LDFLAGS += -ldl

LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libcrypto
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES += $(target_src_files)
LOCAL_C_INCLUDES += $(target_c_includes)
LOCAL_CFLAGS += $(target_cflags)
include $(LOCAL_PATH)/android-config.mk
# include $(BUILD_SHARED_LIBRARY)


############## Host Static Library ##########################
# host static library, which is used by some SDK tools.
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libcrypto_static
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES += $(host_src_files)
LOCAL_C_INCLUDES += $(host_c_includes)
LOCAL_CFLAGS += $(host_cflags) -DPURIFY
LOCAL_LDLIBS += -ldl
include $(LOCAL_PATH)/android-config.mk
# include $(BUILD_HOST_STATIC_LIBRARY)

################# Host Shared Library ######################
# host shared library
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libcrypto-host
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES += $(host_src_files)
LOCAL_C_INCLUDES += $(host_c_includes)
LOCAL_CFLAGS += $(host_cflags) -DPURIFY
LOCAL_LDLIBS += -ldl
include $(LOCAL_PATH)/android-config.mk
# include $(BUILD_HOST_SHARED_LIBRARY)

