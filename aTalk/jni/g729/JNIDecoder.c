/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "JNIDecoder.h"
#include "bcg729/include/bcg729/decoder.h"

JNIEXPORT jlong JNICALL
Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1decoder_1open
    (JNIEnv *jniEnv, jclass clazz)
{
    return (jlong) (intptr_t) initBcg729DecoderChannel();
}

JNIEXPORT void JNICALL
Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1decoder_1close
    (JNIEnv *jniEnv, jclass clazz, jlong decoder)
{
    bcg729DecoderChannelContextStruct *decoderChannelContext = (bcg729DecoderChannelContextStruct *) (intptr_t) decoder;
    closeBcg729DecoderChannel(decoderChannelContext);
}

JNIEXPORT void JNICALL
Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1decoder_1process
    (JNIEnv *jniEnv, jclass clazz, jlong decoder, jbyteArray bitStream, jint bsLength,
     jint frameErasureFlag, jint SIDFrameFlag, jint PayloadFlag, jshortArray output)
{
    bcg729DecoderChannelContextStruct *decoderChannelContext = (bcg729DecoderChannelContextStruct *) (intptr_t) decoder;

    jbyte *bsPtr = (*jniEnv)->GetByteArrayElements(jniEnv, bitStream, NULL);
    jshort *outputPtr = (*jniEnv)->GetShortArrayElements(jniEnv, output, NULL);
    if (outputPtr)
    {
        bcg729Decoder(decoderChannelContext, bsPtr, bsLength, frameErasureFlag, SIDFrameFlag, PayloadFlag, outputPtr);
        (*jniEnv)->ReleaseShortArrayElements(jniEnv, output, outputPtr, 0);
    }
}
