/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

/**
 * A wrapper for the libvpx native library.
 * See {@link "http://www.webmproject.org/docs/"}
 *
 * @author Boris Grozev
 */
public class VPX
{
    /**
     * Operation completed without error.
     * Corresponds to <code>VPX_CODEC_OK</code> from <code>vpx/vpx_codec.h</code>
     */
    public static final int CODEC_OK = 0;

    /**
     * An iterator reached the end of list.
     * Corresponds to <code>VPX_CODEC_LIST_END</code> from <code>vpx/vpx_codec.h</code>
     */
    public static final int CODEC_LIST_END = 9;

    /**
     * Use eXternal Memory Allocation mode flag
     * Corresponds to <code>VPX_CODEC_USE_XMA</code> from <code>vpx/vpx_codec.h</code>
     */
    public static final int CODEC_USE_XMA = 0x00000001;

    /**
     * Output one partition at a time. Each partition is returned in its own <code>VPX_CODEC_CX_FRAME_PKT</code>.
     */
    public static final int CODEC_USE_OUTPUT_PARTITION = 0x20000;

    /**
     * Improve resiliency against losses of whole frames.
     *
     * To set this option for an encoder, enable this bit in the value passed
     * to <code>vpx_enc_cft_set_error_resilient</code> for the encoder's configuration.
     *
     * Corresponds to <code>VPX_ERROR_RESILIENT_DEFAULT</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int ERROR_RESILIENT_DEFAULT = 0x01;

    /**
     * The frame partitions are independently decodable by the bool decoder, meaning that
     * partitions can be decoded even though earlier partitions have been lost. Note that intra
     * prediction is still done over the partition boundary.
     *
     * To set this option for encoder, enable this bit in the value passed
     * to <code>vpx_enc_cft_set_error_resilient</code> for the encoder's configuration.
     *
     * Corresponds to <code>VPX_ERROR_RESILIENT_PARTITIONS</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int ERROR_RESILIENT_PARTITIONS = 0x02;

    /**
     * I420 format constant
     * Corresponds to <code>VPX_IMG_FMT_I420</code> from <code>vpx/vpx_image.h</code>
     */

    /* See vpx_image.h
        define VPX_IMG_FMT_PLANAR 0x100  Image is a planar format.

        brief List of supported image formats
        VPX_IMG_FMT_I420 = VPX_IMG_FMT_PLANAR | 2,
        VPX_IMG_FMT_I422 = VPX_IMG_FMT_PLANAR | 5,
        VPX_IMG_FMT_I444 = VPX_IMG_FMT_PLANAR | 6,
        VPX_IMG_FMT_I440 = VPX_IMG_FMT_PLANAR | 7,
     */
    public static final int IMG_FMT_I420 = 258;
    public static final int IMG_FMT_I422 = 261;
    public static final int IMG_FMT_I444 = 262;
    public static final int IMG_FMT_I440 = 263;

    /**
     * Variable Bitrate mode.
     * Corresponds to <code>VPX_VBR</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int RC_MODE_VBR = 0;

    /**
     * Constant Bitrate mode.
     * Corresponds to <code>VPX_CBR</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int RC_MODE_CBR = 1;

    /**
     * Constant Quality mode.
     * Corresponds to <code>VPX_CQ</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int RC_MODE_CQ = 2;

    /**
     * Encoder determines optimal placement automatically.
     * Corresponds to <code>VPX_KF_AUTO</code> from in <code>vpx/vpx_encoder.h</code>
     */
    public static final int KF_MODE_AUTO = 1;

    /**
     * Encoder does not place keyframes.
     * Corresponds to <code>VPX_KF_DISABLED</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int KF_MODE_DISABLED = 1;

    /**
     * Force this frame to be a keyframe
     * Corresponds to <code>VPX_EFLAG_FORCE_KF</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int EFLAG_FORCE_KF = (1 << 0);

    /**
     * Process and return as soon as possible ('realtime' deadline)
     * Corresponds to <code>VPX_DL_REALTIME</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int DL_REALTIME = 1;

    public static final int DL_GOOD_QUALITY = 1000000;
    public static final int DL_BEST_QUALITY = 0;

    /**
     * Compressed video frame packet type.
     * Corresponds to <code>VPX_CODEC_CX_FRAME_PKT</code> from <code>vpx/vpx_encoder.h</code>
     */
    public static final int CODEC_CX_FRAME_PKT = 0;

    /**
     * brief Codec control function to set encoder internal speed settings.
     *
     * Changes in this value influences, among others, the encoder's selection
     * of motion estimation methods. Values greater than 0 will increase encoder
     * speed at the expense of quality.
     *
     * \note Valid range for VP8: -16..16
     * \note Valid range for VP9: -9..9
     *
     * Supported in codecs: VP8, VP9
     */
    public static final int VP8E_SET_CPUUSED = 13;

    public static final int VP9E_SET_LOSSLESS = 32;

    public static final int VP9E_SET_POSTENCODE_DROP = 65;

    /**
     * Constant for VP8 decoder interface
     */
    public static final int INTERFACE_VP8_DEC = 0;

    /**
     * Constant for VP8 encoder interface
     */
    public static final int INTERFACE_VP8_ENC = 1;

    /**
     * Constant for VP9 decoder interface
     */
    public static final int INTERFACE_VP9_DEC = 2;

    /**
     * Constant for VP9 encoder interface
     */
    public static final int INTERFACE_VP9_ENC = 3;

    /**
     * Allocates memory for a <code>vpx_codec_ctx_t</code> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long codec_ctx_malloc();

    /**
     * Initializes a vpx decoder context.
     *
     * @param context Pointer to a pre-allocated <code>vpx_codec_ctx_t</code>.
     * @param iface Interface to be used. Has to be one of the <code>VPX.INTERFACE_*</code> constants.
     * @param cfg Pointer to a pre-allocated <code>vpx_codec_dec_cfg_t</code>, may be 0.
     * @param flags Flags.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_dec_init(long context, int iface, long cfg, long flags);

    /**
     * Decodes the frame in <code>buf</code>, at offset <code>buf_offset</code>.
     *
     * @param context The context to use.
     * @param buf Encoded frame buffer.
     * @param buf_offset Offset into <code>buf</code> where the encoded frame begins.
     * @param buf_size Size of the encoded frame.
     * @param user_priv Application specific data to associate with this frame.
     * @param deadline Soft deadline the decoder should attempt to meet,
     * in microseconds. Set to zero for unlimited.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The
     * error code can be converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_decode(long context,
            byte[] buf,
            int buf_offset,
            int buf_size,
            long user_priv,
            long deadline);

    /**
     * Gets the next frame available to display from the decoder context <code>context</code>.
     * The list of available frames becomes valid upon completion of the <code>codec_decode</code>
     * call, and remains valid until the next call to <code>codec_decode</code>.
     *
     * @param context The decoder context to use.
     * @param iter Iterator storage, initialized by setting its first element to 0.
     * @return Pointer to a <code>vpx_image_t</code> describing the decoded frame, or 0 if no more frames are available
     */
    public static native long codec_get_frame(long context, long[] iter);

    /**
     * Destroys a codec context, freeing any associated memory buffers.
     *
     * @param context Pointer to the <code>vpx_codec_ctx_t</code> context to destroy.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_destroy(long context);

    /**
     * Initializes a vpx encoder context.
     *
     * @param context Pointer to a pre-allocated <code>vpx_codec_ctx_t</code>.
     * @param iface Interface to be used. Has to be one of the <code>VPX.INTERFACE_*</code> constants.
     * @param cfg Pointer to a pre-allocated <code>vpx_codec_enc_cfg_t</code>, may be 0.
     * @param flags Flags.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_enc_init(long context, int iface, long cfg, long flags);

    /**
     * @param context Pointer to the codec context on which to set the control
     * @param ctrl_id control parameter to set.
     * @param arg arg to set to.
     *
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_control(long context, int ctrl_id, int arg);

    /**
     * @param context Pointer to the codec context on which to set the configuration
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code> to set.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_enc_config_set(long context, long cfg);

    /**
     * Encodes the frame described by <code>img</code>, <code>buf</code>,
     * <code>offset0</code>, <code>offset1</code> and <code>offset2</code>.
     *
     * Note that <code>buf</code> and the offsets describe where the frames is stored, but
     * <code>img</code> has to have all of its other parameters (format, dimensions, strides) already set.
     *
     * The reason <code>buf</code> and the offsets are treated differently is to allow for the
     * encoder to operate on java memory and avoid copying the raw frame to native memory.
     *
     * @param context Pointer to the codec context to use.
     * @param img Pointer to a <code>vpx_image_t</code> describing the raw frame
     * @param buf Contains the raw frame
     * @param offset0 Offset of the first plane
     * @param offset1 Offset of the second plane
     * @param offset2 Offset of the third plane
     * @param pts Presentation time stamp, in timebase units.
     * @param duration Duration to show frame, in timebase units.
     * @param flags Flags to use for encoding this frame.
     * @param deadline Time to spend encoding, in microseconds. (0=infinite)
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_encode(long context, long img, byte[] buf, int offset0,
            int offset1, int offset2, long pts, long duration, long flags, long deadline);

    // public static native long codec_encode_frame(long context, long img, byte[] buf, int offset0,
    //       int offset1, int offset2, long pts, long duration, long flags, long deadline, long[] iter);

    /**
     * Encoded data iterator.
     * Iterates over a list of data packets to be passed from the encoder to the application. The
     * kind of a packet can be determined using {@link VPX#codec_cx_pkt_get_kind}
     * Packets of kind <code>CODEC_CX_FRAME_PKT</code> should be passed to the application's muxer.
     *
     * @param context The codec context to use.
     * @param iter Iterator storage, initialized by setting its first element to 0.
     * @return Pointer to a vpx_codec_cx_pkt_t containing the output data
     * packet, or 0 to indicate the end of available packets
     */
    public static native long codec_get_cx_data(long context, long[] iter);

    /**
     * Returns the <code>kind</code> of the <code>vpx_codec_cx_pkt_t</code> pointed to by <code>pkt</code>.
     *
     * @param pkt Pointer to the <code>vpx_codec_cx_pkt_t</code> to return the <code>kind</code> of.
     * @return The kind of <code>pkt</code>.
     */
    public static native int codec_cx_pkt_get_kind(long pkt);

    /**
     * Returns the size of the data in the <code>vpx_codec_cx_pkt_t</code> pointed
     * to by <code>pkt</code>. Can only be used for packets of <code>kind</code> <code>CODEC_CX_FRAME_PKT</code>.
     *
     * @param pkt Pointer to a <code>vpx_codec_cx_pkt_t</code>.
     * @return The size of the data of <code>pkt</code>.
     */
    public static native int codec_cx_pkt_get_size(long pkt);

    /**
     * Returns a pointer to the data in the <code>vpx_codec_cx_pkt_t</code> pointed to by<code>pkt</code>.
     * Can only be used for packets of <code>kind</code> <code>CODEC_CX_FRAME_PKT</code>.
     *
     * @param pkt Pointer to the <code>vpx_codec_cx_pkt_t</code>.
     * @return Pointer to the data of <code>pkt</code>.
     */
    public static native long codec_cx_pkt_get_data(long pkt);

    //============ img ============
    /**
     * Allocates memory for a <code>vpx_image_t</code> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long img_malloc();

    public static native long img_alloc(long img, int img_fmt, int frame_width, int frame_height, int align);

    /**
     * Returns the value of the <code>w</code> (width) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>w</code> (width) field of <code>img</code>.
     */
    public static native int img_get_w(long img);

    /**
     * Returns the value of the <code>h</code> (height) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>h</code> (height) field of <code>img</code>.
     */
    public static native int img_get_h(long img);

    /**
     * Returns the value of the <code>d_w</code> (displayed width) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>d_w</code> (displayed width) field of <code>img</code>.
     */
    public static native int img_get_d_w(long img);

    /**
     * Returns the value of the <code>d_h</code> (displayed height) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>d_h</code> (displayed height) field of <code>img</code>.
     */
    public static native int img_get_d_h(long img);

    /**
     * Returns the value of the <code>planes[0]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>planes[0]</code> field of <code>img</code>.
     */
    public static native long img_get_plane0(long img);

    /**
     * Returns the value of the <code>planes[1]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>planes[1]</code> field of <code>img</code>.
     */
    public static native long img_get_plane1(long img);

    /**
     * Returns the value of the <code>planes[2]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>planes[2]</code> field of <code>img</code>.
     */
    public static native long img_get_plane2(long img);

    /**
     * Returns the value of the <code>stride[0]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>stride[0]</code> field of <code>img</code>.
     */
    public static native int img_get_stride0(long img);

    /**
     * Returns the value of the <code>stride[1]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>stride[1]</code> field of <code>img</code>.
     */
    public static native int img_get_stride1(long img);

    /**
     * Returns the value of the <code>stride[2]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>stride[2]</code> field of <code>img</code>.
     */
    public static native int img_get_stride2(long img);

    /**
     * Returns the value of the <code>fmt</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @return The <code>fmt</code> field of <code>img</code>.
     */
    public static native int img_get_fmt(long img);

    /**
     * Sets the <code>w</code> (width) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_w(long img, int value);

    /**
     * Sets the <code>h</code> (height) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_h(long img, int value);

    /**
     * Sets the <code>d_w</code> (displayed width) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_d_w(long img, int value);

    /**
     * Sets the <code>d_h</code> (displayed height) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_d_h(long img, int value);

    /**
     * Sets the <code>stride[0]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_stride0(long img, int value);

    /**
     * Sets the <code>stride[1]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_stride1(long img, int value);

    /**
     * Sets the <code>stride[2]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_stride2(long img, int value);

    /**
     * Sets the <code>stride[3]</code> field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_stride3(long img, int value);

    /**
     * Sets the <code>fmt</code> (format) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_fmt(long img, int value);

    /**
     * Sets the <code>bps</code> (bits per sample) field of a <code>vpx_image_t</code>.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param value The value to set.
     */
    public static native void img_set_bps(long img, int value);

    /**
     * Open a descriptor, using existing storage for the underlying image.
     *
     * Returns a descriptor for storing an image of the given format. The storage for descriptor
     * has been allocated elsewhere, and a descriptor is desired to "wrap" that storage.
     *
     * @param img Pointer to a <code>vpx_image_t</code>.
     * @param fmt Format of the image.
     * @param d_w Width of the image.
     * @param d_h Height of the image.
     * @param align Alignment, in bytes, of each row in the image.
     * @param data Storage to use for the image
     */
    public static native void img_wrap(long img, int fmt, int d_w, int d_h, int align, long data);

    /**
     * Allocates memory for a <code>vpx_codec_dec_cfg_t</code> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long codec_dec_cfg_malloc();

    /**
     * Sets the <code>w</code> field of a <code>vpx_codec_dec_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_dec_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_dec_cfg_set_w(long cfg, int value);

    /**
     * Sets the <code>h</code> field of a <code>vpx_codec_dec_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_dec_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_dec_cfg_set_h(long cfg, int value);

    /**
     * Allocates memory for a <code>vpx_codec_enc_cfg_t</code> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long codec_enc_cfg_malloc();

    /**
     * Initializes a encoder configuration structure with default values.
     *
     * @param iface Interface. Should be one of the <code>INTERFACE_*</code> constants
     * @param cfg Pointer to the vpx_codec_enc_cfg_t to initialize
     * @param usage End usage. Set to 0 or use codec specific values.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The
     * error code can be converted to a <code>String</code> with
     * {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_enc_config_default(int iface, long cfg, int usage);

    /**
     * Sets the <code>g_profile</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_profile(long cfg, int value);

    /**
     * Sets the <code>g_threads</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_threads(long cfg, int value);

    /**
     * Sets the <code>g_w</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_w(long cfg, int value);

    /**
     * Sets the <code>g_h</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_h(long cfg, int value);

    public static native void codec_enc_cfg_set_tbnum(long cfg, int value);
    public static native void codec_enc_cfg_set_tbden(long cfg, int value);

    /**
     * Sets the <code>g_error_resilient</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_error_resilient(long cfg, int value);

    /**
     * Sets the <code>g_lag_in_frames</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     * https://chromium.googlesource.com/webm/libvpx/+/refs/tags/v1.10.0/vpx/vpx_encoder.h#362
     * Set to allow lagged encoding
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_lag_in_frames(long cfg, int value);

    /**
     * Sets the <code>rc_target_bitrate</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_target_bitrate(long cfg, int value);

    /**
     * Sets the <code>rc_dropframe_thresh</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_dropframe_thresh(long cfg, int value);

    /**
     * Sets the <code>rc_resize_allowed</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_resize_allowed(long cfg, int value);

    /**
     * Sets the <code>rc_resize_up_thresh</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_resize_up_thresh(long cfg, int value);

    /**
     * Sets the <code>rc_resize_down_thresh</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_resize_down_thresh(long cfg, int value);

    /**
     * Sets the <code>rc_end_usage</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_end_usage(long cfg, int value);

    /**
     * Sets the <code>rc_min_quantizer</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_min_quantizer(long cfg, int value);

    /**
     * Sets the <code>rc_max_quantizer</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_max_quantizer(long cfg, int value);

    /**
     * Sets the <code>rc_undershoot_pct</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_undershoot_pct(long cfg, int value);

    /**
     * Sets the <code>rc_overshoot_pct</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_overshoot_pct(long cfg, int value);

    /**
     * Sets the <code>rc_buf_sz</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_buf_sz(long cfg, int value);

    /**
     * Sets the <code>rc_buf_initial_sz</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_buf_initial_sz(long cfg, int value);

    /**
     * Sets the <code>rc_buf_optimal_sz</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_rc_buf_optimal_sz(long cfg, int value);

    /**
     * Sets the <code>kf_mode</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_kf_mode(long cfg, int value);

    /**
     * Sets the <code>kf_min_dist</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_kf_min_dist(long cfg, int value);

    /**
     * Sets the <code>kf_max_dist</code> field of a <code>vpx_codec_enc_cfg_t</code>.
     *
     * @param cfg Pointer to a <code>vpx_codec_enc_cfg_t</code>.
     * @param value The value to set.
     */
    public static native void codec_enc_cfg_set_kf_max_dist(long cfg, int value);

    /**
     * Allocates memory for a <code>vpx_codec_stream_info_t</code> on the heap.
     *
     * @return A pointer to the allocated memory.
     */
    public static native long stream_info_malloc();

    /**
     * Returns the <code>w</code> field of a <code>vpx_codec_stream_info_t</code>.
     *
     * @param stream_info Pointer to a <code>vpx_codec_stream_info_t</code>.
     * @return The <code>w</code> field of a <code>stream_info</code>.
     */
    public static native int stream_info_get_w(long stream_info);

    /**
     * Returns the <code>h</code> field of a <code>vpx_codec_stream_info_t</code>.
     *
     * @param stream_info Pointer to a <code>vpx_codec_stream_info_t</code>.
     * @return The <code>h</code> field of a <code>stream_info</code>.
     */
    public static native int stream_info_get_h(long stream_info);


    /**
     * Returns the <code>is_kf</code> field of a <code>vpx_codec_stream_info_t</code>.
     *
     * @param stream_info Pointer to a <code>vpx_codec_stream_info_t</code>.
     * @return The <code>w</code> field of a <code>stream_info</code>.
     */
    public static native int stream_info_get_is_kf(long stream_info);

    /**
     * Performs high level parsing of the bitstream. Construction of a decoder
     * context is not necessary. Can be used to determine if the bitstream is
     * of the proper format, and to extract information from the stream.
     *
     * @param iface Interface, should be one of the <code>INTERFACE_*</code> constants.
     * @param buf Buffer containing a compressed frame.
     * @param buf_offset Offset into <code>buf</code> where the compressed frame begins.
     * @param buf_size Size of the compressed frame.
     * @param si_ptr Pointer to a <code>vpx_codec_stream_info_t</code> which will
     * be filled with information about the compressed frame.
     * @return <code>CODEC_OK</code> on success, or an error code otherwise. The error code can be
     * converted to a <code>String</code> with {@link VPX#codec_err_to_string(int)}
     */
    public static native int codec_peek_stream_info(int iface,
            byte[] buf,
            int buf_offset,
            int buf_size,
            long si_ptr);

    /**
     * Allocates memory on the heap (a simple wrapped around the native <code>malloc()</code>)
     *
     * @param s Number of bytes to allocate
     * @return Pointer to the memory allocated.
     */
    public static native long malloc(long s);

    /**
     * Frees memory, which has been allocated with {@link VPX#malloc(long)} or
     * one of the <code>*_malloc()</code> functions.
     *
     * @param ptr Pointer to the memory to free.
     */
    public static native void free(long ptr);

    /**
     * Copies <code>n</code> bytes from <code>src</code> to <code>dst</code>. Simple wrapper
     * around the native <code>memcpy()</code> function.
     *
     * @param dst Destination.
     * @param src Source.
     * @param n Number of bytes to copy.
     */
    public static native void memcpy(byte[] dst, long src, int n);

    /**
     * Fills in <code>buf</code> with a string description of the error code
     * <code>err</code>. Fills at most <code>buf_size</code> bytes of <code>buf</code>
     *
     * @param err Error code
     * @param buf Buffer to copy the string into
     * @param buf_size Buffer size
     * @return The number of bytes written to <code>buf</code>
     */
    public static native int codec_err_to_string(int err,
            byte[] buf, int buf_size);

    /**
     * Returns a <code>String</code> describing the error code <code>err</code>.
     *
     * @param err Error code
     * @return A <code>String</code> describing the error code <code>err</code>.
     */
    public static String codec_err_to_string(int err)
    {
        byte[] buf = new byte[100];
        codec_err_to_string(err, buf, buf.length);

        return new String(buf).trim(); // Remove all null characters i.e. '0'
    }

    static {
        System.loadLibrary("jnvpx");
    }

    /**
     * Java wrapper around vpx_codec_stream_info_t. Contains basic information,
     * obtainable from an encoded frame without a decoder context.
     */
    static class StreamInfo
    {
        /**
         * Width
         */
        int w;

        /**
         * Height
         */
        int h;

        /**
         * Is keyframe
         */
        boolean is_kf;

        /**
         * Initializes this instance by parsing <code>buf</code>
         *
         * @param iface Interface, should be one of the <code>INTERFACE_*</code> constants.
         * @param buf Buffer containing a compressed frame to parse.
         * @param buf_offset Offset into buffer where the compressed frame begins.
         * @param buf_size Size of the compressed frame.
         */
        StreamInfo(int iface, byte[] buf, int buf_offset, int buf_size)
        {
            long si = stream_info_malloc();

            if (codec_peek_stream_info(iface, buf, buf_offset, buf_size, si) != CODEC_OK)
                return;

            w = stream_info_get_w(si);
            h = stream_info_get_h(si);
            is_kf = stream_info_get_is_kf(si) != 0;

            if (si != 0)
                free(si);
        }

        /**
         * Gets the <code>w</code> (width) field of this instance.
         *
         * @return the <code>w</code> (width) field of this instance.
         */
        public int getW()
        {
            return w;
        }

        /**
         * Gets the <code>h</code> (height) field of this instance.
         *
         * @return the <code>h</code> (height) field of this instance.
         */
        public int getH()
        {
            return h;
        }

        /**
         * Gets the <code>is_kf</code> (is keyframe) field of this instance.
         *
         * @return the <code>is_kf</code> (is keyframe) field of this instance.
         */
        public boolean isKf()
        {
            return is_kf;
        }
    }
}
