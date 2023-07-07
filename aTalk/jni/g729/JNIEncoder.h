#include <jni.h>

/* Header for class org_atalk_impl_neomedia_codec_audio_g729_JNIEncoder */
#ifndef _Included_org_atalk_impl_neomedia_codec_audio_g729_JNIEncoder
#define _Included_org_atalk_impl_neomedia_codec_audio_g729_JNIEncoder
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     org_atalk_impl_neomedia_codec_audio_g729_JNIEncoder
 * Method:    g729_encoder_open
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1encoder_1open
  (JNIEnv *, jclass, jint);

/*
 * Class:     org_atalk_impl_neomedia_codec_audio_g729_JNIEncoder
 * Method:    g729_encoder_close
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1encoder_1close
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_atalk_impl_neomedia_codec_audio_g729_JNIEncoder
 * Method:    g729_encoder_process
 * Signature: (J[BI[BII)V
 */
JNIEXPORT jint JNICALL Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1encoder_1process
  (JNIEnv *, jclass, jlong, jshortArray, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
