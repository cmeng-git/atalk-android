/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec;

import java.nio.ByteOrder;

/**
 * Provides the interface to the native FFmpeg library.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class FFmpeg {
    /**
     * No pts value.
     */
    public static final long AV_NOPTS_VALUE = Long.MIN_VALUE; // 0x8000000000000000L;

    public static final int AV_NUM_DATA_POINTERS = 8;

    /**
     * Audio channel masks.
     */
    public static final int AV_CH_LAYOUT_STEREO = 3;
    public static final int AV_CH_LAYOUT_MONO = 4;

    /**
     * The AV sample format for signed 16.
     */
    public static final int AV_SAMPLE_FMT_S16 = 1;

    /**
     * The AV sample format for signed 16 planar.
     */
    public static final int AV_SAMPLE_FMT_S16P = 6;

    /**
     * AC pred flag.
     */
    public static final int CODEC_FLAG_AC_PRED = (1 << 24);

    /**
     * Loop filter flag.
     */
    public static final int CODEC_FLAG_LOOP_FILTER = (1 << 11);

    /**
     * The flag which allows incomplete frames to be passed to a decoder.
     */
    public static final int CODEC_FLAG2_CHUNKS = (1 << 15);

    /**
     * AMR-NB codec ID.
     */
    private static final int CODEC_ID_AMR_NB = 0x12000;

    /**
     * AMR-WB codec ID
     */
    public static final int CODEC_ID_AMR_WB = CODEC_ID_AMR_NB + 1;

    /**
     * H264 codec ID.
     */
    public static final int CODEC_ID_H264 = 27;

    /**
     * MJPEG codec ID.
     */
    public static final int CODEC_ID_MJPEG = 7;

    /**
     * MP3 codec ID.
     */
    public static final int CODEC_ID_MP3 = 0x15000 + 1;

    /**
     * VP8 codec ID
     */
    public static final int CODEC_ID_VP8 = 139;

    /**
     * VP9 codec ID
     */
    public static final int CODEC_ID_VP9 = 167;

    /**
     * Work around bugs in encoders which sometimes cannot be detected automatically.
     */
    public static final int FF_BUG_AUTODETECT = 1;

    public static final int FF_CMP_CHROMA = 256;

    /**
     * Padding size for FFmpeg input buffer.
     */
    public static final int FF_INPUT_BUFFER_PADDING_SIZE = 8;

    public static final int FF_MB_DECISION_SIMPLE = 0;

    /**
     * The minimum encoding buffer size defined by libavcodec.
     */
    public static final int FF_MIN_BUFFER_SIZE = 16384;

    /**
     * The H264 baseline profile.
     */
    public static final int FF_PROFILE_H264_BASELINE = 66;

    /**
     * The H264 high profile.
     */
    public static final int FF_PROFILE_H264_HIGH = 100;

    /**
     * The H264 main profile.
     */
    public static final int FF_PROFILE_H264_MAIN = 77;

    /**
     * ARGB format.
     */
    public static final int PIX_FMT_ARGB = 27;

    /**
     * BGR24 format as of FFmpeg.
     */
    public static final int PIX_FMT_BGR24_1 = 3;

    /**
     * BGR32 format handled in endian specific manner.
     * It is stored as ABGR on big-endian and RGBA on little-endian.
     */
    public static final int PIX_FMT_BGR32;

    /**
     * BGR32_1 format handled in endian specific manner.
     * It is stored as BGRA on big-endian and ARGB on little-endian.
     */
    public static final int PIX_FMT_BGR32_1;

    /**
     * "NONE" format.
     */
    public static final int PIX_FMT_NONE = -1;

    /**
     * NV12 format.
     */
    public static final int PIX_FMT_NV12 = 23;

    /**
     * RGB24 format handled in endian specific manner.
     * It is stored as RGB on big-endian and BGR on little-endian.
     */
    public static final int PIX_FMT_RGB24;

    /**
     * RGB24 format as of FFmpeg.
     */
    public static final int PIX_FMT_RGB24_1 = 2;

    /**
     * RGB32 format handled in endian specific manner.
     * It is stored as ARGB on big-endian and BGRA on little-endian.
     */
    public static final int PIX_FMT_RGB32;

    /**
     * RGB32_1 format handled in endian specific manner.
     * It is stored as RGBA on big-endian and ABGR on little-endian.
     */
    public static final int PIX_FMT_RGB32_1;

    /**
     * UYVY422 format.
     */
    public static final int PIX_FMT_UYVY422 = 15;

    /**
     * UYYVYY411 format.
     */
    public static final int PIX_FMT_UYYVYY411 = 16;

    /**
     * Y41P format
     */
    public static final int PIX_FMT_YUV411P = 7;

    /**
     * YUV420P format.
     */
    public static final int PIX_FMT_YUV420P = 0;

    /**
     * YUVJ422P format.
     */
    public static final int PIX_FMT_YUVJ422P = 13;

    /**
     * YUYV422 format.
     */
    public static final int PIX_FMT_YUYV422 = 1;

    /**
     * BICUBIC type for libswscale conversion.
     */
    public static final int SWS_BICUBIC = 4;

    // public static final int X264_RC_ABR = 2;

    static {
        System.loadLibrary("jnffmpeg");

        if (ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) {
            PIX_FMT_RGB24 = av_get_pix_fmt("rgb24");

            PIX_FMT_RGB32 = av_get_pix_fmt("argb");
            PIX_FMT_RGB32_1 = av_get_pix_fmt("rgba");

            PIX_FMT_BGR32 = av_get_pix_fmt("abgr");
            PIX_FMT_BGR32_1 = av_get_pix_fmt("bgra");
        }
        else {
            PIX_FMT_RGB24 = av_get_pix_fmt("bgr24");

            PIX_FMT_RGB32 = av_get_pix_fmt("bgra");
            PIX_FMT_RGB32_1 = av_get_pix_fmt("abgr");

            PIX_FMT_BGR32 = av_get_pix_fmt("rgba");
            PIX_FMT_BGR32_1 = av_get_pix_fmt("argb");
        }
    }

    public static native String av_strerror(int errnum);

    public static native int av_get_pix_fmt(String name);

    /**
     * Free a native pointer allocated by av_malloc.
     *
     * @param ptr native pointer to free
     */
    public static native void av_free(long ptr);

    /**
     * Allocate memory.
     *
     * @param size size to allocate
     *
     * @return native pointer or 0 if av_malloc failed
     */
    public static native long av_malloc(int size);

    /**
     * Allocates a new <code>AVCodecContext</code>.
     *
     * @param codec
     *
     * @return native pointer to the new <code>AVCodecContext</code>
     */
    public static native long avcodec_alloc_context3(long codec);

    /**
     * Allocates an <code>AVFrame</code> instance and sets its fields to default values. The result must
     * be freed using {@link #avcodec_free_frame(long)}.
     *
     * @return an <code>AVFrame *</code> value which points to an <code>AVFrame</code> instance filled with
     * default values or <code>0</code> on failure
     */
    public static native long avcodec_alloc_frame();

    public static native long avcodec_alloc_packet(int size);

    /**
     * Close an AVCodecContext
     *
     * @param ctx pointer to AVCodecContex
     *
     * @return 0 if success, -1 otherwise
     */
    public static native int avcodec_close(long ctx);

    public static native int avcodec_decode_audio4(long avctx, long frame, boolean[] got_frame, long avpkt);

    /**
     * Decode a video frame.
     *
     * @param ctx codec context
     * @param frame frame decoded
     * @param got_picture if the decoding has produced a valid picture
     * @param buf the input buffer
     * @param buf_size input buffer size
     *
     * @return number of bytes written to buff if success
     */
    public static native int avcodec_decode_video(long ctx, long frame, boolean[] got_picture,
            byte[] buf, int buf_size);

    /**
     * Decode a video frame.
     *
     * @param ctx codec context
     * @param avframe frame decoded
     * @param src input buffer
     * @param src_length input buffer size
     *
     * @return number of bytes written to buff if success
     */
    public static native int avcodec_decode_video(long ctx, long avframe, long src, int src_length);

    /**
     * Encodes an audio frame from <code>samples</code> into <code>buf</code>.
     *
     * @param ctx the codec context
     * @param buf the output buffer
     * @param buf_offset the output buffer offset
     * @param buf_size the output buffer size
     * @param samples the input buffer containing the samples. The number of samples read from this buffer
     * is <code>frame_size</code>*<code>channels</code>, both of which are defined in <code>ctx</code>.
     * For PCM audio the number of samples read from samples is equal to <code>buf_size</code>*
     * <code>input_sample_size</code>/<code>output_sample_size</code>.
     * @param samples_offset the offset in the input buffer containing the samples
     *
     * @return on error a negative value is returned, on success zero or the number of bytes used to
     * encode the data read from the input buffer
     */
    public static native int avcodec_encode_audio(long ctx, byte[] buf, int buf_offset,
            int buf_size, byte[] samples, int samples_offset);

    /**
     * Encode a video frame.
     *
     * @param ctx codec context
     * @param buff the output buffer
     * @param buf_size output buffer size
     * @param frame frame to encode
     *
     * @return number of bytes written to buff if success
     */
    public static native int avcodec_encode_video(long ctx, byte[] buff, int buf_size, long frame);

    /**
     * Find a registered decoder with a matching ID.
     *
     * @param id <code>CodecID</code> of the requested encoder
     *
     * @return an <code>AVCodec</code> encoder if one was found; <code>0</code>, otherwise
     */
    public static native long avcodec_find_decoder(int id);

    /**
     * Finds a registered encoder with a matching codec ID.
     *
     * @param id <code>CodecID</code> of the requested encoder
     *
     * @return an <code>AVCodec</code> encoder if one was found; <code>0</code>, otherwise
     */
    public static native long avcodec_find_encoder(int id);

    /**
     * Frees an <code>AVFrame</code> instance specified as an <code>AVFrame *</code> value and any
     * dynamically allocated objects in it (e.g. <code>extended_data</code>).
     * <p>
     * <b>Warning</b>: The method/function does NOT free the data buffers themselves because it does
     * not know how since they might have been allocated with a custom <code>get_buffer()</code>.
     * </p>
     *
     * @param frame an <code>AVFrame *</code> value which points to the <code>AVFrame</code> instance to be freed
     */
    public static void avcodec_free_frame(long frame) {
        // Invoke the native function avcodec_free_frame(AVFrame **).
        av_free(frame);
    }

    public static native void avcodec_free_packet(long pkt);

    /**
     * Initializes the specified <code>AVCodecContext</code> to use the specified <code>AVCodec</code>.
     *
     * @param ctx the <code>AVCodecContext</code> which will be set up to use the specified <code>AVCodec</code>
     * @param codec the <code>AVCodec</code> to use within the <code>AVCodecContext</code>
     * @param options
     *
     * @return zero on success, a negative value on error
     */
    public static native int avcodec_open2(long ctx, long codec, String... options);

    /**
     * Add specific flags to AVCodecContext's flags member.
     *
     * @param ctx pointer to AVCodecContext
     * @param flags flags to add
     */
    public static native void avcodeccontext_add_flags(long ctx, int flags);

    /**
     * Add specific flags to AVCodecContext's flags2 member.
     *
     * @param ctx pointer to AVCodecContext
     * @param flags2 flags to add
     */
    public static native void avcodeccontext_add_flags2(long ctx, int flags2);

    /**
     * Gets the samples per packet of the specified <code>AVCodecContext</code>. The property is set by
     * libavcodec upon {@link #avcodec_open(long, long)}.
     *
     * @param ctx the <code>AVCodecContext</code> to get the samples per packet of
     *
     * @return the samples per packet of the specified <code>AVCodecContext</code>
     */
    public static native int avcodeccontext_get_frame_size(long ctx);

    /**
     * Get height of the video.
     *
     * @param ctx pointer to AVCodecContext
     *
     * @return video height
     */
    public static native int avcodeccontext_get_height(long ctx);

    /**
     * Get pixel format.
     *
     * @param ctx pointer to AVCodecContext
     *
     * @return pixel format
     */
    public static native int avcodeccontext_get_pix_fmt(long ctx);

    /**
     * Get width of the video.
     *
     * @param ctx pointer to AVCodecContext
     *
     * @return video width
     */
    public static native int avcodeccontext_get_width(long ctx);

    /**
     * Set the B-Frame strategy.
     *
     * @param ctx AVCodecContext pointer
     * @param b_frame_strategy strategy
     */
    public static native void avcodeccontext_set_b_frame_strategy(long ctx, int b_frame_strategy);

    /**
     * Sets the average bit rate of the specified <code>AVCodecContext</code>. The property is to be set
     * by the user when encoding and is unused for the constant quantizer encoding. It is set by
     * the libavcodec when decoding and its value is <code>0</code> or some bitrate if this info is
     * available in the stream.
     *
     * @param ctx the <code>AVCodecContext</code> to set the average bit rate of
     * @param bit_rate the average bit rate to be set to the specified <code>AVCodecContext</code>
     */
    public static native void avcodeccontext_set_bit_rate(long ctx, int bit_rate);

    /**
     * Set the bit rate tolerance
     *
     * @param ctx the <code>AVCodecContext</code> to set the bit rate of
     * @param bit_rate_tolerance bit rate tolerance
     */
    public static native void avcodeccontext_set_bit_rate_tolerance(long ctx, int bit_rate_tolerance);

//    /**
//     * Sets the number of channels of the specified <code>AVCodecContext</code>.
//     * The property is audio only. Deprecated in ffmpeg v5.1.
//     *
//     * @param ctx the <code>AVCodecContext</code> to set the number of channels of
//     * @param channels the number of channels to set to the specified <code>AVCodecContext</code>
//     */
//    public static native void avcodeccontext_set_channels(long ctx, int channels);
//    public static native void avcodeccontext_set_channel_layout(long ctx, int channelLayout);

    /**
     * Set the AVChannelLayout (ch_layout) of the specified <code>AVCodecContext</code> for ffmpeg 5.1.
     * The property is audio only: The number of channels is already defined in ch_layout.
     * Hence avcodeccontext_set_nb_channels in (ch_layout.nb_channels is not required.
     *
     * @param ctx the <code>AVCodecContext</code> to set the number of channels of
     * @param ch_Layout the AVChannelLayout
     */
    public static native void avcodeccontext_set_ch_layout(long ctx, int ch_Layout);

    public static native void avcodeccontext_set_nb_channels(long ctx, int nb_channels);

    public static native void avcodeccontext_set_chromaoffset(long ctx, int chromaoffset);

    /**
     * Sets the maximum number of pictures in a group of pictures i.e. the maximum interval between
     * keyframes.
     *
     * @param ctx the <code>AVCodecContext</code> to set the <code>gop_size</code> of
     * @param gop_size the maximum number of pictures in a group of pictures i.e. the maximum interval between keyframes
     */
    public static native void avcodeccontext_set_gop_size(long ctx, int gop_size);

    public static native void avcodeccontext_set_i_quant_factor(long ctx, float i_quant_factor);

    /**
     * Sets the minimum GOP size.
     *
     * @param ctx the <code>AVCodecContext</code> to set the minimum GOP size of
     * @param keyint_min the minimum GOP size to set on <code>ctx</code>
     */
    public static native void avcodeccontext_set_keyint_min(long ctx, int keyint_min);

    /**
     * Set the maximum B frames.
     *
     * @param ctx the <code>AVCodecContext</code> to set the maximum B frames of
     * @param max_b_frames maximum B frames
     */
    public static native void avcodeccontext_set_max_b_frames(long ctx, int max_b_frames);


    public static native void avcodeccontext_set_mb_decision(long ctx, int mb_decision);

    public static native void avcodeccontext_set_me_cmp(long ctx, int me_cmp);

    public static native void avcodeccontext_set_me_method(long ctx, int me_method);

    public static native void avcodeccontext_set_me_range(long ctx, int me_range);

    public static native void avcodeccontext_set_me_subpel_quality(long ctx, int me_subpel_quality);

    /**
     * Set the pixel format.
     *
     * @param ctx the <code>AVCodecContext</code> to set the pixel format of
     * @param pix_fmt pixel format
     */
    public static native void avcodeccontext_set_pix_fmt(long ctx, int pix_fmt);

    public static native void avcodeccontext_set_profile(long ctx, int profile);


    public static native void avcodeccontext_set_qcompress(long ctx, float qcompress);

    public static native void avcodeccontext_set_quantizer(long ctx, int qmin, int qmax, int max_qdiff);

    public static native void avcodeccontext_set_rc_buffer_size(long ctx, int rc_buffer_size);

    public static native void avcodeccontext_set_rc_eq(long ctx, String rc_eq);

    public static native void avcodeccontext_set_rc_max_rate(long ctx, int rc_max_rate);

    public static native void avcodeccontext_set_refs(long ctx, int refs);

    /**
     * Set the RTP payload size.
     *
     * @param ctx the <code>AVCodecContext</code> to set the RTP payload size of
     * @param rtp_payload_size RTP payload size
     */
    public static native void avcodeccontext_set_rtp_payload_size(long ctx, int rtp_payload_size);


    public static native void avcodeccontext_set_sample_aspect_ratio(long ctx, int num, int den);

    public static native void avcodeccontext_set_sample_fmt(long ctx, int sample_fmt);

    /**
     * Sets the samples per second of the specified <code>AVCodecContext</code>. The property is audio
     * only.
     *
     * @param ctx the <code>AVCodecContext</code> to set the samples per second of
     * @param sample_rate the samples per second to set to the specified <code>AVCodecContext</code>
     */
    public static native void avcodeccontext_set_sample_rate(long ctx, int sample_rate);

    /**
     * Set the scene change threshold (in percent).
     *
     * @param ctx AVCodecContext pointer
     * @param scenechange_threshold value between 0 and 100
     */
    public static native void avcodeccontext_set_scenechange_threshold(long ctx, int scenechange_threshold);

    /**
     * Set the size of the video.
     *
     * @param ctx pointer to AVCodecContext
     * @param width video width
     * @param height video height
     */
    public static native void avcodeccontext_set_size(long ctx, int width, int height);

    /**
     * Set the number of thread.
     *
     * @param ctx the <code>AVCodecContext</code> to set the number of thread of
     * @param thread_count number of thread to set
     */
    public static native void avcodeccontext_set_thread_count(long ctx, int thread_count);

    public static native void avcodeccontext_set_ticks_per_frame(long ctx, int ticks_per_frame);

    public static native void avcodeccontext_set_time_base(long ctx, int num, int den);

    public static native void avcodeccontext_set_trellis(long ctx, int trellis);

    public static native void avcodeccontext_set_workaround_bugs(long ctx, int workaround_bugs);

    /**
     * Allocates a new <code>AVFilterGraph</code> instance.
     *
     * @return a pointer to the newly-allocated <code>AVFilterGraph</code> instance
     */
    public static native long avfilter_graph_alloc();

    /**
     * Checks the validity and configures all the links and formats in a specific
     * <code>AVFilterGraph</code> instance.
     *
     * @param graph a pointer to the <code>AVFilterGraph</code> instance to check the validity of and
     * configure
     * @param log_ctx the <code>AVClass</code> context to be used for logging
     *
     * @return <code>0</code> on success; a negative <code>AVERROR</code> on error
     */
    public static native int avfilter_graph_config(long graph, long log_ctx);

    /**
     * Frees a specific <code>AVFilterGraph</code> instance and destroys its links.
     *
     * @param graph a pointer to the <code>AVFilterGraph</code> instance to free
     */
    public static native void avfilter_graph_free(long graph);

    /**
     * Gets a pointer to an <code>AVFilterContext</code> instance with a specific name in a specific
     * <code>AVFilterGraph</code> instance.
     *
     * @param graph a pointer to the <code>AVFilterGraph</code> instance where the <code>AVFilterContext</code>
     * instance with the specified name is to be found
     * @param name the name of the <code>AVFilterContext</code> instance which is to be found in the
     * specified <code>graph</code>
     *
     * @return the filter graph pointer
     */
    public static native long avfilter_graph_get_filter(long graph, String name);

    /**
     * Adds a filter graph described by a <code>String</code> to a specific <code>AVFilterGraph</code> instance.
     *
     * @param graph a pointer to the <code>AVFilterGraph</code> instance where to link the parsed graph context
     * @param filters the <code>String</code> to be parsed
     * @param inputs a pointer to a linked list to the inputs of the graph if any; otherwise, <code>0</code>
     * @param outputs a pointer to a linked list to the outputs of the graph if any; otherwise, <code>0</code>
     * @param log_ctx the <code>AVClass</code> context to be used for logging
     *
     * @return <code>0</code> on success; a negative <code>AVERROR</code> on error
     */
    public static native int avfilter_graph_parse(long graph, String filters, long inputs,
            long outputs, long log_ctx);

    /**
     * Initializes the <code>libavfilter</code> system and registers all built-in filters.
     */
    public static native long avframe_get_data0(long frame);

    public static native int avframe_get_linesize0(long frame);

    public static native long avframe_get_pts(long frame);

    public static native void avframe_set_properties(long frame, int format, int width, int height);

    public static native void avframe_set_data(long frame, long data0, long offset1, long offset2);

    public static native void avframe_set_key_frame(long frame, boolean key_frame);

    public static native void avframe_set_linesize(long frame, int linesize0, int linesize1, int linesize2);

    public static native void avpacket_set_data(long avpkt, byte[] in, int inOff, int inLen);

    public static native int avpicture_fill(long picture, long ptr, int pix_fmt, int width, int height);

    public static native long get_filtered_video_frame(long input, int width, int height,
            int pixFmt, long buffer, long ffsink, long output);

    public static native void memcpy(byte[] dst, int dst_offset, int dst_length, long src);

    public static native void memcpy(int[] dst, int dst_offset, int dst_length, long src);

    public static native void memcpy(long dst, byte[] src, int src_offset, int src_length);

    /**
     * Get BGR32 pixel format.
     *
     * @return BGR32 pixel format
     */
    private static native int PIX_FMT_BGR32();

    /**
     * Get BGR32_1 pixel format.
     *
     * @return BGR32_1 pixel format
     */
    private static native int PIX_FMT_BGR32_1();

    /**
     * Get RGB24 pixel format.
     *
     * @return RGB24 pixel format
     */
    private static native int PIX_FMT_RGB24();

    /**
     * Get RGB32 pixel format.
     *
     * @return RGB32 pixel format
     */
    private static native int PIX_FMT_RGB32();

    /**
     * Get RGB32_1 pixel format.
     *
     * @return RGB32_1 pixel format
     */
    private static native int PIX_FMT_RGB32_1();

    /**
     * Free an SwsContext.
     *
     * @param ctx SwsContext native pointer
     */
    public static native void sws_freeContext(long ctx);

    /**
     * Get a SwsContext pointer.
     *
     * @param ctx SwsContext
     * @param srcW width of source image
     * @param srcH height of source image
     * @param srcFormat image format
     * @param dstW width of destination image
     * @param dstH height destination image
     * @param dstFormat destination format
     * @param flags flags
     *
     * @return cached SwsContext pointer
     */
    public static native long sws_getCachedContext(long ctx, int srcW, int srcH, int srcFormat,
            int dstW, int dstH, int dstFormat, int flags);

    /**
     * Scale an image.
     *
     * @param ctx SwsContext native pointer
     * @param src source image (native pointer)
     * @param srcSliceY slice Y of source image
     * @param srcSliceH slice H of source image
     * @param dst destination image (java type)
     * @param dstFormat destination format
     * @param dstW width of destination image
     * @param dstH height destination image
     *
     * @return 0 if success, -1 otherwise
     */
    public static native int sws_scale(long ctx, long src, int srcSliceY, int srcSliceH,
            Object dst, int dstFormat, int dstW, int dstH);

    /**
     * Scale image an image.
     *
     * @param ctx SwsContext native pointer
     * @param src source image (java type)
     * @param srcFormat image format
     * @param srcW width of source image
     * @param srcH height of source image
     * @param srcSliceY slice Y of source image
     * @param srcSliceH slice H of source image
     * @param dst destination image (java type)
     * @param dstFormat destination format
     * @param dstW width of destination image
     * @param dstH height destination image
     *
     * @return 0 if success, -1 otherwise
     */
    public static native int sws_scale(long ctx, Object src, int srcFormat, int srcW, int srcH,
            int srcSliceY, int srcSliceH, Object dst, int dstFormat, int dstW, int dstH);
}
