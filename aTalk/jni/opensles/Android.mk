# ========== OpenSLES (jnopensles.so library) ==================
### OpenSLES shared library build
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_LDLIBS    := -lOpenSLES -llog
LOCAL_MODULE    := jnopensles
LOCAL_SRC_FILES := \
  org_atalk_impl_neomedia_device_OpenSLESSystem.c \
  org_atalk_impl_neomedia_jmfext_media_protocol_opensles_DataSource.c \
  org_atalk_impl_neomedia_jmfext_media_renderer_audio_OpenSLESRenderer.c
include $(BUILD_SHARED_LIBRARY)
