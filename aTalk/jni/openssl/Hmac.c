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
#include <openssl/core_names.h>
#include "Hmac.h"

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MD_size
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MD_1size
        (JNIEnv *env, jclass clazz, jlong md) {
    return EVP_MD_size((const EVP_MD *) (intptr_t) md);
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_sha1
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1sha1
        (JNIEnv *env, jclass clazz) {
    return (jlong) (intptr_t) EVP_sha1();
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_CTX_new
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1CTX_1new
        (JNIEnv *env, jclass clazz) {
    EVP_MAC *mac = EVP_MAC_fetch(NULL, OSSL_MAC_NAME_HMAC, NULL);
    EVP_MAC_CTX *ctx = EVP_MAC_CTX_new(mac);

    // The algorithm structure itself can be safely freed once context is linked
    EVP_MAC_free(mac);
    return (jlong) (intptr_t) ctx;
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_CTX_free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1CTX_1free
        (JNIEnv *env, jclass clazz, jlong ctx) {
    EVP_MAC_CTX *ctx_ = (EVP_MAC_CTX *) (intptr_t) ctx;
    EVP_MAC_CTX_free(ctx_);

    // No need, already free in EVP_MAC_CTX_new
    // EVP_MAC *mac = EVP_MAC_fetch(NULL,OSSL_MAC_NAME_HMAC, NULL);
    // EVP_MAC_free(EVP_MAC_CTX_get0_mac(ctx_));
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_init
 * Signature: (J[BIJJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1init
        (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray key, jint keyLen, jlong md) {
    jbyte *key_;
    jboolean ok;

    // RESET CTX KEEPING KEY & MD
    // Passing NULL as key and NULL (or empty) as params tells OpenSSL to
    // clear data states but reuse the previously loaded key and configuration.
    if (key == NULL) {
        return EVP_MAC_init((EVP_MAC_CTX *) (intptr_t) ctx, NULL, 0, NULL);
    }

    if (key) {
        key_ = (*env)->GetPrimitiveArrayCritical(env, key, NULL);
        ok = key_ ? JNI_TRUE : JNI_FALSE;
    } else {
        key_ = NULL;
        ok = JNI_TRUE;
    }

    if (JNI_TRUE == ok) {
        const char *md_name = EVP_MD_name((const EVP_MD *) (intptr_t) md);
        size_t kye_len = keyLen;

        // Set the underlying digest sing OSSL_MAC_PARAM_DIGEST ("digest") as md_name
        OSSL_PARAM params[] = {
                OSSL_PARAM_utf8_string(OSSL_MAC_PARAM_DIGEST, (char *) md_name, 0),
                OSSL_PARAM_END
        };

        // Initialize
        ok = EVP_MAC_init(
                (EVP_MAC_CTX *) (intptr_t) ctx,
                (const void *) (intptr_t) key_,
                kye_len,
                params);
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
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1update
        (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray data, jint offset, jint len) {
    jbyte *data_ = (*env)->GetPrimitiveArrayCritical(env, data, NULL);
    jboolean ok;

    if (data_) {
        size_t kye_len = len;

        ok = EVP_MAC_update(
                (EVP_MAC_CTX *) (intptr_t) ctx,
                (const unsigned char *) (data_ + offset),
                kye_len);
        (*env)->ReleasePrimitiveArrayCritical(env, data, data_, JNI_ABORT);
    } else {
        ok = JNI_FALSE;
    }
    return ok;
}

/*
 * Class:     org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac
 * Method:    EVP_MAC_final
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL
Java_org_atalk_impl_neomedia_transform_srtp_crypto_OpenSslHmac_EVP_1MAC_1final
        (JNIEnv *env, jclass clazz, jlong ctx, jbyteArray md, jint mdOff, jlong mdLen) {
    jbyte *md_ = (*env)->GetPrimitiveArrayCritical(env, md, NULL);
    unsigned char res[EVP_MAX_MD_SIZE];
    int i;

    if (md_) {
        size_t md_len = mdLen;
        size_t out_len = 0;

        int ok = EVP_MAC_final(
                (EVP_MAC_CTX *) (intptr_t) ctx,
                (unsigned char *) (md_ + mdOff),
                &out_len,
                md_len);
        (*env)->ReleasePrimitiveArrayCritical(env, md, md_, 0);
        i = ok ? (int) out_len : -1;
    } else {
        i = -1;
    }
    return i;
}

