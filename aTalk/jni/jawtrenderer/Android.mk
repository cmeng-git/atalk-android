# ========================= JAWT Renderer ====================================
### JAWT Renderer library build
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -lEGL -lGLESv1_CM -llog
LOCAL_MODULE    := jnawtrenderer
LOCAL_SRC_FILES := \
 JAWTRenderer_Android.c \
 org_atalk_impl_neomedia_jmfext_media_renderer_video_JAWTRenderer.c

include $(BUILD_SHARED_LIBRARY)