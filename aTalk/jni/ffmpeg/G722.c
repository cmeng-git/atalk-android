#include <jni.h>
#include <stdint.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/frame.h>
#include <libavutil/channel_layout.h>
#include <libavutil/common.h>
#include "G722.h"

JNIEXPORT jlong JNICALL Java_org_atalk_impl_neomedia_codec_audio_g722_G722_g722_1decoder_1open
        (JNIEnv *env, jclass clazz) {
    const AVCodec *codec = avcodec_find_decoder(AV_CODEC_ID_ADPCM_G722);
    if (!codec) return 0;

    // Allocate the codec context
    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) return 0;

    // G.722 RTP specific parameters
    codec_ctx->sample_rate = 16000; // FFmpeg expects 16000Hz for G.722, though RTP says 8000Hz
    codec_ctx->ch_layout = (AVChannelLayout) AV_CHANNEL_LAYOUT_MONO;
    codec_ctx->sample_fmt = AV_SAMPLE_FMT_S16; // 16-bit Signed integer (Native Endian)

    // Open the codec
    if (avcodec_open2(codec_ctx, codec, NULL) < 0) {
        avcodec_free_context(&codec_ctx);
        return 0;
    }
    return (jlong) (intptr_t) codec_ctx;
}

JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_codec_audio_g722_G722_g722_1decoder_1close
        (JNIEnv *env, jclass clazz, jlong decoder) {
    AVCodecContext *codec_ctx = (AVCodecContext *) (intptr_t) decoder;
    if (codec_ctx) {
        avcodec_free_context(&codec_ctx);
    }
}

JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_codec_audio_g722_G722_g722_1decoder_1process
        (JNIEnv *env, jclass clazz, jlong decoder, jbyteArray input, jint inputOffset, jbyteArray output,
         jint outputOffset, jint outputLength) {
    AVCodecContext *codec_ctx = (AVCodecContext *) (intptr_t) decoder;
    if (!codec_ctx || !input || !output) return;

    jbyte *inBytes = (*env)->GetByteArrayElements(env, input, NULL);
    jbyte *outBytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!inBytes || !outBytes) {
        if (inBytes) (*env)->ReleaseByteArrayElements(env, input, inBytes, JNI_ABORT);
        if (outBytes) (*env)->ReleaseByteArrayElements(env, output, outBytes, 0);
        return;
    }

    int inLen = (*env)->GetArrayLength(env, input) - inputOffset;
    AVPacket *pkt = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    if (pkt && frame) {
        pkt->data = (uint8_t *) (inBytes + inputOffset);
        pkt->size = inLen;

        // Send the RTP payload packet to the decoder
        if (avcodec_send_packet(codec_ctx, pkt) == 0) {
            // Receive decoded PCM frames from the decoder
            if (avcodec_receive_frame(codec_ctx, frame) == 0) {
                int dataSize = frame->linesize[0];
                // Data is now decoded into frame->data[0]
                // For AV_SAMPLE_FMT_S16 (Mono), data is interleaved in a single channel
                // int dataSize = av_get_bytes_per_sample(codec_ctx->sample_fmt);
                if (dataSize > outputLength) dataSize = outputLength;
                memcpy(outBytes + outputOffset, frame->data[0], dataSize);
            }
        }
        av_packet_free(&pkt);
        av_frame_free(&frame);
    }

    (*env)->ReleaseByteArrayElements(env, input, inBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, outBytes, 0);
}

JNIEXPORT jlong JNICALL Java_org_atalk_impl_neomedia_codec_audio_g722_G722_g722_1encoder_1open
        (JNIEnv *env, jclass clazz) {
    // 1. Find the G.722 encoder
    const AVCodec *codec = avcodec_find_encoder(AV_CODEC_ID_ADPCM_G722);
    if (!codec) return 0;

    // 2. Initialize the Encoder Context
    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) return 0;

    // G.722 natively requires 16000Hz internal sample rate
    codec_ctx->bit_rate = 64000; // Standard G.722 bitrate
    codec_ctx->sample_rate = 16000; // G.722 physical sample rate is always 16kHz
    // av_channel_layout_default(&codec_ctx->ch_layout, 1); // Set to Mono channel layout
    codec_ctx->ch_layout = (AVChannelLayout)AV_CHANNEL_LAYOUT_MONO;
    codec_ctx->sample_fmt = AV_SAMPLE_FMT_S16; // G.722 uses 16-bit signed PCM

    // Set timebase for the encoder (corresponds to its 16kHz internal rate)
    // codec_ctx->time_base = (AVRational){1, 16000};
    // codec_ctx->pkt_timebase = (AVRational){1, 16000};

    // Open the encoder
    if (avcodec_open2(codec_ctx, codec, NULL) < 0) {
        avcodec_free_context(&codec_ctx);
        return 0;
    }
    // printf("G.722 Encoder successfully initialized for Mono at 16kHz (RTP Clock: 8kHz).\n");
    return (jlong) (intptr_t) codec_ctx;
}

JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_codec_audio_g722_G722_g722_1encoder_1close
        (JNIEnv *env, jclass clazz, jlong encoder) {
    AVCodecContext *codec_ctx = (AVCodecContext *) (intptr_t) encoder;
    if (codec_ctx) {
        avcodec_free_context(&codec_ctx);
    }
}

JNIEXPORT void JNICALL Java_org_atalk_impl_neomedia_codec_audio_g722_G722_g722_1encoder_1process
        (JNIEnv *env, jclass clazz, jlong encoder, jbyteArray input, jint inputOffset, jbyteArray output,
         jint outputOffset, jint outputLength) {
    AVCodecContext *codec_ctx = (AVCodecContext *) (intptr_t) encoder;
    if (!codec_ctx || !input || !output) return;

    jbyte *inBytes = (*env)->GetByteArrayElements(env, input, NULL);
    jbyte *outBytes = (*env)->GetByteArrayElements(env, output, NULL);
    if (!inBytes || !outBytes) {
        if (inBytes) (*env)->ReleaseByteArrayElements(env, input, inBytes, JNI_ABORT);
        if (outBytes) (*env)->ReleaseByteArrayElements(env, output, outBytes, 0);
        return;
    }

    int inLen = (*env)->GetArrayLength(env, input) - inputOffset;
    AVFrame *frame = av_frame_alloc();
    AVPacket *pkt = av_packet_alloc();

    if (frame && pkt) {
        frame->nb_samples = codec_ctx->frame_size ? codec_ctx->frame_size : (inLen / 2);
        frame->format = codec_ctx->sample_fmt;
        av_channel_layout_copy(&frame->ch_layout, &codec_ctx->ch_layout);

        if (avcodec_fill_audio_frame(frame, codec_ctx->ch_layout.nb_channels, codec_ctx->sample_fmt,
                                     (uint8_t *) (inBytes + inputOffset), inLen, 0) >= 0) {
            pkt->data = (uint8_t *) (outBytes + outputOffset);
            pkt->size = outputLength;

            // Send raw frame to encoder
            if (avcodec_send_frame(codec_ctx, frame) == 0) {
                // Read encoded packets
                if (avcodec_receive_packet(codec_ctx, pkt) == 0) {
                    memcpy(outBytes + outputOffset, pkt->data, pkt->size);
                }
            }
        }
        av_frame_free(&frame);
        av_packet_free(&pkt);
    }

    (*env)->ReleaseByteArrayElements(env, input, inBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, output, outBytes, 0);
}
