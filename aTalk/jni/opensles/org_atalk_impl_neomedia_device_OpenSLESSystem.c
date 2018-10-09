/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
#include "org_atalk_impl_neomedia_device_OpenSLESSystem.h"

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <stdlib.h>
#include <assert.h>
#include <android/log.h>

#define  LOG_TAG    "OpenSLES"
#define  ALOG_INFO(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  ALOG_ERROR(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

static void
OpenSLESSystem_queryAudioInputCapabilities
    (JNIEnv *jniEnv,
        SLAudioIODeviceCapabilitiesItf engine_AudioIODeviceCapabilitiesItf,
        jlong deviceID, SLAudioInputDescriptor descriptor,
        jdoubleArray sampleRates, jintArray sampleSizesInBits, jintArray channels,
        jintArray audioInputCapabilities, jint *audioInputCapabilitiesIndex);

static void
OpenSLESSystem_queryAudioInputCapabilitiesByChannel
    (JNIEnv *jniEnv,
        SLAudioIODeviceCapabilitiesItf engine_AudioIODeviceCapabilitiesItf,
        jlong deviceID, SLAudioInputDescriptor descriptor,
        jdoubleArray sampleRates, jintArray sampleSizesInBits, jint channel, jint channelIndex,
        jintArray audioInputCapabilities, jint *audioInputCapabilitiesIndex);

static void
OpenSLESSystem_queryAudioInputCapabilitiesBySampleRate
    (JNIEnv *jniEnv,
        SLAudioIODeviceCapabilitiesItf engine_AudioIODeviceCapabilitiesItf,
        jlong deviceID, SLAudioInputDescriptor descriptor,
        jdouble sampleRate, jint sampleRateIndex, jintArray sampleSizesInBits, jint channel, jint channelIndex,
        jintArray audioInputCapabilities, jint *audioInputCapabilitiesIndex);

JNIEXPORT jintArray JNICALL
Java_org_atalk_impl_neomedia_device_OpenSLESSystem_queryAudioInputCapabilities
    (JNIEnv *jniEnv, jclass clazz,
        jlong deviceID,
        jdoubleArray sampleRates, jintArray sampleSizesInBits, jintArray channels)
{
    SLInterfaceID interfaceIds[] = { SL_IID_ENGINE };
    SLboolean interfaceRequired[] = { SL_BOOLEAN_TRUE };

    SLObjectItf engineObject_;
    SLEngineItf EngineItf;
    SLAudioIODeviceCapabilitiesItf AudioIODeviceCapabilitiesItf;
    SLAudioInputDescriptor AudioInputDescriptor;
    jintArray audioInputCapabilities = NULL;

    ALOG_INFO("Create Audio Engine\n");
    SLresult result = slCreateEngine(
            &engineObject_, 0, NULL,
            sizeof(interfaceIds) / sizeof(SLInterfaceID),
            interfaceIds,
            interfaceRequired);
    assert(SL_RESULT_SUCCESS == result);

    ALOG_INFO("Realize Audio Engine; engineObject_: %p\n", engineObject_);
    result = (*engineObject_)->Realize(engineObject_, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);

    // Get the Audio IO DEVICE CAPABILITIES interface, implicit
    ALOG_INFO("Get the Audio IO DEVICE CAPABILITIES interface, implicit");
    result = (*engineObject_)->GetInterface(
        engineObject_, SL_IID_AUDIOIODEVICECAPABILITIES, &AudioIODeviceCapabilitiesItf);
    assert(SL_RESULT_SUCCESS == result);

    // ANDROID: obtaining SL_IID_AUDIOIODEVICECAPABILITIES may fail
    if (AudioIODeviceCapabilitiesItf != NULL ) {
        ALOG_INFO("Query Audio Input Capabilities, implicit");
        SLAudioInputDescriptor descriptor;
        result = (*AudioIODeviceCapabilitiesItf)->QueryAudioInputCapabilities(
                AudioIODeviceCapabilitiesItf, (SLuint32) deviceID, &AudioInputDescriptor);
        assert(SL_RESULT_SUCCESS == result);

        ALOG_INFO("Retrieve the audio capabilities\n");
        jsize sampleRateCount = (*jniEnv)->GetArrayLength(jniEnv, sampleRates);
        if (!((*jniEnv)->ExceptionCheck(jniEnv)))
        {
            jsize sampleSizeInBitsCount = (*jniEnv)->GetArrayLength(jniEnv, sampleSizesInBits);
            if (!((*jniEnv)->ExceptionCheck(jniEnv)))
            {
                jsize channelCount = (*jniEnv)->GetArrayLength(jniEnv, channels);
                if (!((*jniEnv)->ExceptionCheck(jniEnv)))
                {
                    audioInputCapabilities = (*jniEnv)->NewIntArray(
                            jniEnv,
                            (sampleRateCount
                                * sampleSizeInBitsCount
                                * channelCount
                                + 1)
                        * 3);
                    if (audioInputCapabilities)
                    {
                        jsize audioInputCapabilitiesIndex = 0;
                        OpenSLESSystem_queryAudioInputCapabilities(
                                jniEnv,
                                AudioIODeviceCapabilitiesItf,
                                deviceID, descriptor,
                                sampleRates, sampleSizesInBits, channels,
                                audioInputCapabilities, &audioInputCapabilitiesIndex);
                        if (!((*jniEnv)->ExceptionCheck(jniEnv)))
                        {
                            jint minus1s[] = { -1, -1, -1 };
                            (*jniEnv)->SetIntArrayRegion(
                                    jniEnv,
                                    audioInputCapabilities,
                                    audioInputCapabilitiesIndex,
                                    sizeof(minus1s) / sizeof(jint),
                                    minus1s);
                        }
                    }
                }
            }
        }
    }
    else
        ALOG_ERROR("Failed: Query Audio Input Capabilities\n");

    // destroy engine object, and invalidate all associated interfaces
    if (engineObject_ != NULL) {
        ALOG_INFO("Shutdown audio engine\n");
        (*engineObject_)->Destroy(engineObject_);
        engineObject_ = NULL;
    }
    return audioInputCapabilities;
}

static void
OpenSLESSystem_queryAudioInputCapabilities
    (JNIEnv *jniEnv,
        SLAudioIODeviceCapabilitiesItf engine_AudioIODeviceCapabilitiesItf,
        jlong deviceID, SLAudioInputDescriptor descriptor,
        jdoubleArray sampleRates, jintArray sampleSizesInBits, jintArray channels,
        jintArray audioInputCapabilities, jint *audioInputCapabilitiesIndex)
{
    jsize channelIndex;
    jsize channelCount = (*jniEnv)->GetArrayLength(jniEnv, channels);

    if ((*jniEnv)->ExceptionCheck(jniEnv))
        channelCount = 0;
    for (channelIndex = 0; channelIndex < channelCount; channelIndex++)
    {
        jint channel;
        (*jniEnv)->GetIntArrayRegion(
                jniEnv, channels, channelIndex, 1, &channel);
        if ((*jniEnv)->ExceptionCheck(jniEnv))
            break;

        if (channel <= descriptor.maxChannels)
        {
            OpenSLESSystem_queryAudioInputCapabilitiesByChannel(
                    jniEnv,
                    engine_AudioIODeviceCapabilitiesItf,
                    deviceID, descriptor,
                    sampleRates, sampleSizesInBits, channel, channelIndex,
                    audioInputCapabilities, audioInputCapabilitiesIndex);
            if ((*jniEnv)->ExceptionCheck(jniEnv))
                break;
        }
    }
}

static void
OpenSLESSystem_queryAudioInputCapabilitiesByChannel
    (JNIEnv *jniEnv,
        SLAudioIODeviceCapabilitiesItf engine_AudioIODeviceCapabilitiesItf,
        jlong deviceID, SLAudioInputDescriptor descriptor,
        jdoubleArray sampleRates, jintArray sampleSizesInBits, jint channel, jint channelIndex,
        jintArray audioInputCapabilities, jint *audioInputCapabilitiesIndex)
{
    jsize sampleRateIndex;
    jsize sampleRateCount = (*jniEnv)->GetArrayLength(jniEnv, sampleRates);

    if ((*jniEnv)->ExceptionCheck(jniEnv))
        sampleRateCount = 0;
    for (sampleRateIndex = 0;
            sampleRateIndex < sampleRateCount;
            sampleRateIndex++)
    {
        jdouble sampleRate;
        jboolean sampleRateIsSupported;

        (*jniEnv)->GetDoubleArrayRegion(
                jniEnv, sampleRates, sampleRateIndex, 1, &sampleRate);
        if ((*jniEnv)->ExceptionCheck(jniEnv))
            break;

        sampleRate *= 1000;
        if (SL_BOOLEAN_TRUE == descriptor.isFreqRangeContinuous)
        {
            sampleRateIsSupported = (descriptor.minSampleRate <= sampleRate)
                    && (sampleRate <= descriptor.maxSampleRate);
        }
        else
        {
            SLint16 supportedSampleRateCount = descriptor.numOfSamplingRatesSupported;
            sampleRateIsSupported = JNI_FALSE;
            if (supportedSampleRateCount)
            {
                SLint16 supportedSampleRateIndex;
                SLmilliHertz *supportedSampleRates = descriptor.samplingRatesSupported;

                for (supportedSampleRateIndex = 0;
                        supportedSampleRateIndex < supportedSampleRateCount;
                        supportedSampleRateIndex++)
                {
                    if (sampleRate == *supportedSampleRates++)
                    {
                        sampleRateIsSupported = JNI_TRUE;
                        break;
                    }
                }
            }
        }

        if (sampleRateIsSupported)
        {
            OpenSLESSystem_queryAudioInputCapabilitiesBySampleRate(
                    jniEnv,
                    engine_AudioIODeviceCapabilitiesItf,
                    deviceID, descriptor,
                    sampleRate, sampleRateIndex, sampleSizesInBits, channel, channelIndex,
                    audioInputCapabilities, audioInputCapabilitiesIndex);
            if ((*jniEnv)->ExceptionCheck(jniEnv))
                break;
        }
    }
}

static void
OpenSLESSystem_queryAudioInputCapabilitiesBySampleRate
    (JNIEnv *jniEnv,
        SLAudioIODeviceCapabilitiesItf engine_AudioIODeviceCapabilitiesItf,
        jlong deviceID, SLAudioInputDescriptor descriptor,
        jdouble sampleRate, jint sampleRateIndex, jintArray sampleSizesInBits, jint channel, jint channelIndex,
        jintArray audioInputCapabilities, jint *audioInputCapabilitiesIndex)
{
    SLint32 sampleFormatCount;
    SLresult result
        = (*engine_AudioIODeviceCapabilitiesItf)->QuerySampleFormatsSupported(
                engine_AudioIODeviceCapabilitiesItf,
                deviceID,
                sampleRate,
                NULL, &sampleFormatCount);
    if ((SL_RESULT_SUCCESS == result) && sampleFormatCount)
    {
        SLint32 *sampleFormats = malloc(sizeof(SLint32) * sampleFormatCount);
        if (sampleFormats)
        {
            result = (*engine_AudioIODeviceCapabilitiesItf)->QuerySampleFormatsSupported(
                    engine_AudioIODeviceCapabilitiesItf,
                    deviceID,
                    sampleRate,
                    sampleFormats, &sampleFormatCount);
            if (SL_RESULT_SUCCESS == result)
            {
                int sampleSizeInBitsIndex;
                jsize sampleSizeInBitsCount = (*jniEnv)->GetArrayLength(jniEnv, sampleSizesInBits);

                if ((*jniEnv)->ExceptionCheck(jniEnv))
                    sampleSizeInBitsCount = 0;
                for (sampleSizeInBitsIndex = 0;
                        sampleSizeInBitsIndex < sampleSizeInBitsCount;
                        sampleSizeInBitsIndex++)
                {
                    jint sampleSizeInBits;
                    jboolean sampleSizeInBitsIsSupported;
                    SLint32 sampleFormatIndex;

                    (*jniEnv)->GetIntArrayRegion(
                            jniEnv,
                            sampleSizesInBits, sampleSizeInBitsIndex, 1,
                            &sampleSizeInBits);
                    if ((*jniEnv)->ExceptionCheck(jniEnv))
                        break;

                    sampleSizeInBitsIsSupported = JNI_FALSE;
                    for (sampleFormatIndex = 0;
                            sampleFormatIndex < sampleFormatCount;
                            sampleFormatIndex++)
                    {
                        switch (*(sampleFormats + sampleFormatIndex))
                        {
                        case SL_PCMSAMPLEFORMAT_FIXED_8:
                            if (8 == sampleSizeInBits)
                                sampleSizeInBitsIsSupported = JNI_TRUE;
                            break;
                        case SL_PCMSAMPLEFORMAT_FIXED_16:
                            if (16 == sampleSizeInBits)
                                sampleSizeInBitsIsSupported = JNI_TRUE;
                            break;
                        default:
                            break;
                        }
                        if (sampleSizeInBitsIsSupported)
                            break;
                    }

                    if (sampleSizeInBitsIsSupported)
                    {
                        jint audioInputCapability[] = {
                            sampleRateIndex,
                            sampleSizeInBitsIndex,
                            channelIndex
                        };
                        jint _audioInputCapabilitiesIndex = *audioInputCapabilitiesIndex;
                        jsize audioInputCapabilityLength = sizeof(audioInputCapability) / sizeof(jint);

                        (*jniEnv)->SetIntArrayRegion(
                                jniEnv,
                                audioInputCapabilities,
                                _audioInputCapabilitiesIndex,
                                audioInputCapabilityLength,
                                audioInputCapability);
                        if ((*jniEnv)->ExceptionCheck(jniEnv))
                            break;
                        *audioInputCapabilitiesIndex
                            = _audioInputCapabilitiesIndex + audioInputCapabilityLength;
                    }
                }
            }
            free(sampleFormats);
        }
    }
}
