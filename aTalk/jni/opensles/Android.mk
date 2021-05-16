# ========== OpenSLES (jnopensles.so library) ==================
### OpenSLES shared library build
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -lOpenSLES -llog
LOCAL_MODULE    := jnopensles
LOCAL_SRC_FILES := OpenSLESSystem.c DataSource.c OpenSLESRenderer.c
include $(BUILD_SHARED_LIBRARY)
