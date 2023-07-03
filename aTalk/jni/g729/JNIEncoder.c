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

#include "JNIEncoder.h"
#include "bcg729/include/bcg729/encoder.h"

JNIEXPORT jlong JNICALL
Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1encoder_1open
    (JNIEnv *jniEnv, jclass clazz, jint enableVAD)
{
    return (jlong) (intptr_t) initBcg729EncoderChannel(enableVAD);
}

JNIEXPORT void JNICALL
Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1encoder_1close
    (JNIEnv *jniEnv, jclass clazz, jlong encoder)
{
    bcg729EncoderChannelContextStruct *encoderChannelContext = (bcg729EncoderChannelContextStruct *) (intptr_t) encoder;
    closeBcg729EncoderChannel(encoderChannelContext);
}

JNIEXPORT jint JNICALL
Java_org_atalk_impl_neomedia_codec_audio_g729_G729_g729_1encoder_1process
    (JNIEnv *jniEnv, jclass clazz, jlong encoder, jshortArray inputFrame, jbyteArray bitStream)
{
    bcg729EncoderChannelContextStruct *encoderChannelContext = (bcg729EncoderChannelContextStruct *) (intptr_t) encoder;

    jbyte *bsPtr = (*jniEnv)->GetByteArrayElements(jniEnv, bitStream, NULL);
    jshort *inFramePtr = (*jniEnv)->GetShortArrayElements(jniEnv, inputFrame, NULL);
    jint bitStreamLength = 0;

    bcg729Encoder(encoderChannelContext, inFramePtr, bsPtr, &bitStreamLength);
    (*jniEnv)->ReleaseByteArrayElements(jniEnv, bitStream, bsPtr, 0);

    return bitStreamLength;
}
