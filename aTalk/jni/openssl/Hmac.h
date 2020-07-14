#include <jni.h>
/* Header for class _Included_org_atalk_impl_neomedia_transform_srtp_OpenSSLDigest */

#ifndef _Included_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
#define _Included_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_jitsi_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MD_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MD_1size
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_sha1
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1sha1
  (JNIEnv *, jclass);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1CTX_1create
  (JNIEnv *, jclass);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1CTX_1destroy
  (JNIEnv *, jclass, jlong);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_Final
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1Final
  (JNIEnv *, jclass, jlong, jbyteArray, jint, jint);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_Init_ex
 * Signature: (J[BIJJ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1Init_1ex
  (JNIEnv *, jclass, jlong, jbyteArray, jint, jlong, jlong);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_Update
 * Signature: (J[BII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1Update
  (JNIEnv *, jclass, jlong, jbyteArray, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
