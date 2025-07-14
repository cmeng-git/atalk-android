# ========================= JAWT Renderer ====================================
### JAWT Renderer library build
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -lEGL -lGLESv1_CM -llog
LOCAL_LDFLAGS += "-Wl,-z,max-page-size=16384"
LOCAL_MODULE    := jnawtrenderer
LOCAL_SRC_FILES := \
 JAWTRenderer_Android.c \
 JAWTRenderer.c

include $(BUILD_SHARED_LIBRARY)