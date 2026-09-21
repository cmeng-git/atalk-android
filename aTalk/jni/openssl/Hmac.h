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
 * Method:    EVP_MAC_CTX_new
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1CTX_1new
        (JNIEnv *, jclass);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_CTX_free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1CTX_1free
        (JNIEnv *, jclass, jlong);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_init
 * Signature: (J[BIJJ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1HMAC_1Init
        (JNIEnv *, jclass, jlong, jbyteArray, jint, jlong);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_update
 * Signature: (J[BII)Z
 */
JNIEXPORT jboolean JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1update
        (JNIEnv *, jclass, jlong, jbyteArray, jint, jint);

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_final
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1final
        (JNIEnv *, jclass, jlong, jbyteArray, jint, jlong);

#ifdef __cplusplus
}
#endif
#endif
