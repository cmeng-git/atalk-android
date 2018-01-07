local_cflags :=

local_c_includes := $(log_c_includes)

local_additional_dependencies := $(LOCAL_PATH)/android-config.mk $(LOCAL_PATH)/Ssl.mk

include $(LOCAL_PATH)/Ssl-config.mk

############### Target SSL Static Library ########################
# The static library should be used in only unbundled apps
# target static library
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libssl
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES += $(target_src_files)
LOCAL_C_INCLUDES += $(target_c_includes)
LOCAL_CFLAGS += $(target_cflags)
include $(LOCAL_PATH)/android-config.mk
include $(BUILD_STATIC_LIBRARY)

### //////////////// Not use in android ////////////////// ###
############# Target SSL Shared Library ##########################
# target shared library
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := $(log_shared_libraries)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libssl
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)
LOCAL_SHARED_LIBRARIES += libcrypto $(log_shared_libraries)

LOCAL_SRC_FILES += $(target_src_files)
LOCAL_C_INCLUDES += $(target_c_includes)
LOCAL_CFLAGS += $(target_cflags)
include $(LOCAL_PATH)/android-config.mk
# include $(BUILD_SHARED_LIBRARY)


############### Host library ########################
# host shared library
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES += libcrypto-host $(log_shared_libraries)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libssl-host
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES += $(host_src_files)
LOCAL_C_INCLUDES += $(host_c_includes)
LOCAL_CFLAGS += $(host_cflags) -DPURIFY
include $(LOCAL_PATH)/android-config.mk
# include $(BUILD_HOST_SHARED_LIBRARY)


################ SSLTest Execute #######################
# ssltest
include $(CLEAR_VARS)

LOCAL_SHARED_LIBRARIES := libssl libcrypto $(log_shared_libraries)
LOCAL_MODULE := ssltest
LOCAL_MODULE_TAGS := optional
LOCAL_ADDITIONAL_DEPENDENCIES := $(local_additional_dependencies)

LOCAL_SRC_FILES:= ssl/ssltest.c
LOCAL_C_INCLUDES += $(host_c_includes)
include $(LOCAL_PATH)/android-config.mk
# include $(BUILD_EXECUTABLE)
