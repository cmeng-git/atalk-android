/*
 * Copyright @ 2015 - present 8x8, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdint.h>
#include <stdlib.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include "Hmac.h"

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MD_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MD_1size
  (JNIEnv *env, jclass clazz, jlong md)
{
    return EVP_MD_size((const EVP_MD *)(intptr_t) md);
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_sha1
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1sha1
  (JNIEnv *env, jclass clazz)
{
    return (jlong)(intptr_t) EVP_sha1();
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_CTX_create
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1CTX_1create
  (JNIEnv *env, jclass clazz)
{
/* OpenSSL 1.1.0 made HMAC_CTX an opaque structure, which must be allocated
   using HMAC_CTX_new.  But this function doesn't exist in OpenSSL 1.0.x. */
#if OPENSSL_VERSION_NUMBER < 0x10100000L || LIBRESSL_VERSION_NUMBER
    HMAC_CTX *ctx = malloc(sizeof(HMAC_CTX));

    if (ctx)
        HMAC_CTX_init(ctx);

#else
    HMAC_CTX *ctx = HMAC_CTX_new();

#endif

    return (jlong) (intptr_t) ctx;
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_CTX_destroy
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1CTX_1destroy
  (JNIEnv *env, jclass clazz, jlong ctx)
{
    HMAC_CTX *ctx_ = (HMAC_CTX *) (intptr_t) ctx;

#if OPENSSL_VERSION_NUMBER < 0x10100000L || LIBRESSL_VERSION_NUMBER
    HMAC_CTX_cleanup(ctx_);
    free(ctx_);

#else
    HMAC_CTX_free(ctx_);

#endif
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_Final
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1Final
  (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray md, jint mdOff, jint mdLen)
{
    jbyte *md_ = (*env)->GetPrimitiveArrayCritical(env, md, NULL);
    int i;

    if (md_)
    {
        unsigned int len = mdLen;

        i
            = HMAC_Final(
                    (HMAC_CTX *) (intptr_t) ctx,
                    (unsigned char *) (md_ + mdOff),
                    &len);
        (*env)->ReleasePrimitiveArrayCritical(env, md, md_, 0);
        i = i ? len : -1;
    }
    else
    {
        i = -1;
    }
    return i;
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_Init_ex
 * Signature: (J[BIJJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1Init_1ex
  (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray key, jint keyLen, jlong md, jlong impl)
{
    jbyte *key_;
    jboolean ok;

    if (key)
    {
        key_ = (*env)->GetPrimitiveArrayCritical(env, key, NULL);
        ok = key_ ? JNI_TRUE : JNI_FALSE;
    }
    else
    {
        key_ = NULL;
        ok = JNI_TRUE;
    }
    if (JNI_TRUE == ok)
    {
	    ok = HMAC_Init_ex(
	        (HMAC_CTX *)(intptr_t) ctx,
	        (const void *)  (intptr_t) key_,
	        keyLen,
	        (const EVP_MD *)(intptr_t) md,
	        (ENGINE *)(intptr_t) impl);
	    if (key_)
	    	(*env)->ReleasePrimitiveArrayCritical(env, key, key_, JNI_ABORT);
    }
    return ok;
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    HMAC_Update
 * Signature: (J[BII)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_HMAC_1Update
  (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray data, jint offset, jint len)
{
    jbyte *data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    jboolean ok;

    if (data_)
    {
	    ok = HMAC_Update(
	        (HMAC_CTX *)(intptr_t) ctx,
	        (const unsigned char *) (data_ + offset),
	        len);
	    (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
  	}
    else
    {
        ok = JNI_FALSE;
    }
    return ok;
}
