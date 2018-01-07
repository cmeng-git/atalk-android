#include <jni.h>
#include <android/log.h>
#include <opus.h>

/* Header for class net_abcdefgh_opustrial_codec_Opus */
#ifndef _Included_net_abcdefgh_opustrial_codec_Opus
#define _Included_net_abcdefgh_opustrial_codec_Opus

#define TAG "Opus_JNI"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG , TAG,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO  , TAG,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN  , TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR , TAG,__VA_ARGS__)
#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jint JNICALL Java_net_abcdefgh_opustrial_codec_Opus_open
        (JNIEnv *env, jobject thiz){
    ...
    return error;
}
JNIEXPORT jint JNICALL Java_net_abcdefgh_opustrial_codec_Opus_decode
        (JNIEnv * env, jobject thiz, jbyteArray jencoded, jint jencodedOffset, jint jencodedLength, jbyteArray jpcm, jint jpcmOffset, jint jframeSize) {
    ...
    return decodedSize;
}
JNIEXPORT jint JNICALL Java_net_abcdefgh_opustrial_codec_Opus_encode
        (JNIEnv * env, jobject thiz, jbyteArray jpcm, jint jpcmOffset, jint jpcmLength, jbyteArray jencoded, jint jencodedOffset) {
    ...
    return encodedSize;
}
JNIEXPORT void JNICALL Java_net_abcdefgh_opustrial_codec_Opus_close
(JNIEnv *env, jobject thiz){
...
}
#ifdef __cplusplus
}
#endif
#endif