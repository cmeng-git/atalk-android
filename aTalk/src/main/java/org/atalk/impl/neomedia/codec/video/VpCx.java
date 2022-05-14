/*
 *  Copyright (c) 2010 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
/*
 * Algorithm Flags ported from libvpx vp8cx.h for checking only; not use in aTalk code
 * @author: cmeng
 */
package org.atalk.impl.neomedia.codec.video;

public class VpCx
{
  public static final int VP8_EFLAG_NO_REF_LAST = (1 << 16);

  /*!\brief Don't reference the golden frame
   *
   * When this flag is set, the encoder will not use the golden frame as a
   * predictor. When not set, the encoder will choose whether to use the
   * golden frame or not automatically.
   */
  public static final int VP8_EFLAG_NO_REF_GF = (1 << 17);

  /*!\brief Don't reference the alternate reference frame
   *
   * When this flag is set, the encoder will not use the alt ref frame as a
   * predictor. When not set, the encoder will choose whether to use the
   * alt ref frame or not automatically.
   */
  public static final int VP8_EFLAG_NO_REF_ARF = (1 << 21);

  /*!\brief Don't update the last frame
   *
   * When this flag is set, the encoder will not update the last frame with
   * the contents of the current frame.
   */
  public static final int VP8_EFLAG_NO_UPD_LAST = (1 << 18);

  /*!\brief Don't update the golden frame
   *
   * When this flag is set, the encoder will not update the golden frame with
   * the contents of the current frame.
   */
  public static final int VP8_EFLAG_NO_UPD_GF = (1 << 22);

  /*!\brief Don't update the alternate reference frame
   *
   * When this flag is set, the encoder will not update the alt ref frame with
   * the contents of the current frame.
   */
  public static final int VP8_EFLAG_NO_UPD_ARF = (1 << 23);

  /*!\brief Force golden frame update
   *
   * When this flag is set, the encoder copy the contents of the current frame
   * to the golden frame buffer.
   */
  public static final int VP8_EFLAG_FORCE_GF = (1 << 19);

  /*!\brief Force alternate reference frame update
   *
   * When this flag is set, the encoder copy the contents of the current frame
   * to the alternate reference frame buffer.
   */
  public static final int VP8_EFLAG_FORCE_ARF = (1 << 24);

  /*!\brief Disable entropy update
   *
   * When this flag is set, the encoder will not update its internal entropy
   * model based on the entropy of this frame.
   */
  public static final int VP8_EFLAG_NO_UPD_ENTROPY = (1 << 20);

  /*!\brief VPx encoder control functions
   *
   * This set of macros define the control functions available for VPx
   * encoder interface.
   *
   * \sa #vpx_codec_control
   */
  public enum VpCid
  {
    VP_00,
    VP_01,
    VP_02,
    VP_03,
    VP_04,
    VP_05,
    VP_06,
    VP_07,

    /*!\brief Codec control function to pass an ROI map to encoder.
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_ROI_MAP,

    /*!\brief Codec control function to pass an Active map to encoder.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_ACTIVEMAP,

    VP_10,

    /*!\brief Codec control function to set encoder scaling mode.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_SCALEMODE,

    VP_12,

    /*!\brief Codec control function to set encoder internal speed settings.
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
    VP8E_SET_CPUUSED,

    /*!\brief Codec control function to enable automatic use of arf frames.
     *
     * \note Valid range for VP8: 0..1
     * \note Valid range for VP9: 0..6
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_ENABLEAUTOALTREF,

    /*!\brief control function to set noise sensitivity
     *
     * 0: off, 1: OnYOnly, 2: OnYUV,
     * 3: OnYUVAggressive, 4: Adaptive
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_NOISE_SENSITIVITY,

    /*!\brief Codec control function to set higher sharpness at the expense
     * of a lower PSNR.
     *
     * \note Valid range: 0..7
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_SHARPNESS,

    /*!\brief Codec control function to set the threshold for MBs treated static.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_STATIC_THRESHOLD,

    /*!\brief Codec control function to set the number of token partitions.
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_TOKEN_PARTITIONS,

    /*!\brief Codec control function to get last quantizer chosen by the encoder.
     *
     * Return value uses internal quantizer scale defined by the codec.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_GET_LAST_QUANTIZER,

    /*!\brief Codec control function to get last quantizer chosen by the encoder.
     *
     * Return value uses the 0..63 scale as used by the rc_*_quantizer config
     * parameters.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_GET_LAST_QUANTIZER_64,

    /*!\brief Codec control function to set the max no of frames to create arf.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_ARNR_MAXFRAMES,

    /*!\brief Codec control function to set the filter strength for the arf.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_ARNR_STRENGTH,

    /*!\deprecated control function to set the filter type to use for the arf. */
    VP8E_SET_ARNR_TYPE,

    /*!\brief Codec control function to set visual tuning.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_TUNING,

    /*!\brief Codec control function to set constrained / constant quality level.
     *
     * \attention For this value to be used vpx_codec_enc_cfg_t::rc_end_usage must
     *            be set to #VPX_CQ or #VPX_Q
     * \note Valid range: 0..63
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_CQ_LEVEL,

    /*!\brief Codec control function to set Max data rate for Intra frames.
     *
     * This value controls additional clamping on the maximum size of a
     * keyframe. It is expressed as a percentage of the average
     * per-frame bitrate, with the special (and default) value 0 meaning
     * unlimited, or no additional clamping beyond the codec's built-in
     * algorithm.
     *
     * For example, to allocate no more than 4.5 frames worth of bitrate
     * to a keyframe, set this to 450.
     *
     * Supported in codecs: VP8, VP9
     */
    VP8E_SET_MAX_INTRA_BITRATE_PCT,

    /*!\brief Codec control function to set reference and update frame flags.
     *
     *  Supported in codecs: VP8
     */
    VP8E_SET_FRAME_FLAGS,

    /*!\brief Codec control function to set max data rate for Inter frames.
     *
     * This value controls additional clamping on the maximum size of an
     * inter frame. It is expressed as a percentage of the average
     * per-frame bitrate, with the special (and default) value 0 meaning
     * unlimited, or no additional clamping beyond the codec's built-in
     * algorithm.
     *
     * For example, to allow no more than 4.5 frames worth of bitrate
     * to an inter frame, set this to 450.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_MAX_INTER_BITRATE_PCT,

    /*!\brief Boost percentage for Golden Frame in CBR mode.
     *
     * This value controls the amount of boost given to Golden Frame in
     * CBR mode. It is expressed as a percentage of the average
     * per-frame bitrate, with the special (and default) value 0 meaning
     * the feature is off, i.e., no golden frame boost in CBR mode and
     * average bitrate target is used.
     *
     * For example, to allow 100% more bits, i.e, 2X, in a golden frame
     * than average frame, set this to 100.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_GF_CBR_BOOST_PCT,

    /*!\brief Codec control function to set the temporal layer id.
     *
     * For temporal scalability: this control allows the application to set the
     * layer id for each frame to be encoded. Note that this control must be set
     * for every frame prior to encoding. The usage of this control function
     * supersedes the internal temporal pattern counter, which is now deprecated.
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_TEMPORAL_LAYER_ID,

    /*!\brief Codec control function to set encoder screen content mode.
     *
     * 0: off, 1: On, 2: On with more aggressive rate control.
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_SCREEN_CONTENT_MODE,

    /*!\brief Codec control function to set lossless encoding mode.
     *
     * VP9 can operate in lossless encoding mode, in which the bitstream
     * produced will be able to decode and reconstruct a perfect copy of
     * input source. This control function provides a mean to switch encoder
     * into lossless coding mode(1) or normal coding mode(0) that may be lossy.
     *                          0 = lossy coding mode
     *                          1 = lossless coding mode
     *
     *  By default, encoder operates in normal coding mode (maybe lossy).
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_LOSSLESS,

    /*!\brief Codec control function to set number of tile columns.
     *
     * In encoding and decoding, VP9 allows an input image frame be partitioned
     * into separated vertical tile columns, which can be encoded or decoded
     * independently. This enables easy implementation of parallel encoding and
     * decoding. This control requests the encoder to use column tiles in
     * encoding an input frame, with number of tile columns (in Log2 unit) as
     * the parameter:
     *             0 = 1 tile column
     *             1 = 2 tile columns
     *             2 = 4 tile columns
     *             .....
     *             n = 2**n tile columns
     * The requested tile columns will be capped by the encoder based on image
     * size limitations (The minimum width of a tile column is 256 pixels, the
     * maximum is 4096).
     *
     * By default, the value is 6, i.e., the maximum number of tiles supported by
     * the resolution.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_TILE_COLUMNS,

    /*!\brief Codec control function to set number of tile rows.
     *
     * In encoding and decoding, VP9 allows an input image frame be partitioned
     * into separated horizontal tile rows. Tile rows are encoded or decoded
     * sequentially. Even though encoding/decoding of later tile rows depends on
     * earlier ones, this allows the encoder to output data packets for tile rows
     * prior to completely processing all tile rows in a frame, thereby reducing
     * the latency in processing between input and output. The parameter
     * for this control describes the number of tile rows, which has a valid
     * range [0, 2]:
     *            0 = 1 tile row
     *            1 = 2 tile rows
     *            2 = 4 tile rows
     *
     * By default, the value is 0, i.e. one single row tile for entire image.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_TILE_ROWS,

    /*!\brief Codec control function to enable frame parallel decoding feature.
     *
     * VP9 has a bitstream feature to reduce decoding dependency between frames
     * by turning off backward update of probability context used in encoding
     * and decoding. This allows staged parallel processing of more than one
     * video frame in the decoder. This control function provides a means to
     * turn this feature on or off for bitstreams produced by encoder.
     *
     * By default, this feature is on.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_FRAME_PARALLEL_DECODING,

    /*!\brief Codec control function to set adaptive quantization mode.
     *
     * VP9 has a segment based feature that allows encoder to adaptively change
     * quantization parameter for each segment within a frame to improve the
     * subjective quality. This control makes encoder operate in one of the
     * several AQ_modes supported.
     *
     * By default, encoder operates with AQ_Mode 0(adaptive quantization off).
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_AQ_MODE,

    /*!\brief Codec control function to enable/disable periodic Q boost.
     *
     * One VP9 encoder speed feature is to enable quality boost by lowering
     * frame level Q periodically. This control function provides a mean to
     * turn on/off this feature.
     *               0 = off
     *               1 = on
     *
     * By default, the encoder is allowed to use this feature for appropriate
     * encoding modes.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_FRAME_PERIODIC_BOOST,

    /*!\brief Codec control function to set noise sensitivity.
     *
     *  0: off, 1: On(YOnly), 2: For SVC only, on top two spatial layers(YOnly)
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_NOISE_SENSITIVITY,

    /*!\brief Codec control function to turn on/off SVC in encoder.
     * \note Return value is VPX_CODEC_INVALID_PARAM if the encoder does not
     *       support SVC in its current encoding mode
     *  0: off, 1: on
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC,

    /*!\brief Codec control function to pass an ROI map to encoder.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_ROI_MAP,

    /*!\brief Codec control function to set parameters for SVC.
     * \note Parameters contain min_q, max_q, scaling factor for each of the
     *       SVC layers.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_PARAMETERS,

    /*!\brief Codec control function to set svc layer for spatial and temporal.
     * \note Valid ranges: 0..#vpx_codec_enc_cfg::ss_number_layers for spatial
     *                     layer and 0..#vpx_codec_enc_cfg::ts_number_layers for
     *                     temporal layer.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_LAYER_ID,

    /*!\brief Codec control function to set content type.
     * \note Valid parameter range:
     *              VP9E_CONTENT_DEFAULT = Regular video content (Default)
     *              VP9E_CONTENT_SCREEN  = Screen capture content
     *              VP9E_CONTENT_FILM    = Film content: improves grain retention
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_TUNE_CONTENT,

    /*!\brief Codec control function to get svc layer ID.
     * \note The layer ID returned is for the data packet from the registered
     *       callback function.
     *
     * Supported in codecs: VP9
     */
    VP9E_GET_SVC_LAYER_ID,

    /*!\brief Codec control function to register callback to get per layer packet.
     * \note Parameter for this control function is a structure with a callback
     *       function and a pointer to private data used by the callback.
     *
     * Supported in codecs: VP9
     */
    VP9E_REGISTER_CX_CALLBACK,

    /*!\brief Codec control function to set color space info.
     * \note Valid ranges: 0..7, default is "UNKNOWN".
     *                     0 = UNKNOWN,
     *                     1 = BT_601
     *                     2 = BT_709
     *                     3 = SMPTE_170
     *                     4 = SMPTE_240
     *                     5 = BT_2020
     *                     6 = RESERVED
     *                     7 = SRGB
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_COLOR_SPACE,

    /*!\brief Codec control function to set minimum interval between GF/ARF frames
     *
     * By default the value is set as 4.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_MIN_GF_INTERVAL,

    /*!\brief Codec control function to set minimum interval between GF/ARF frames
     *
     * By default the value is set as 16.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_MAX_GF_INTERVAL,

    /*!\brief Codec control function to get an Active map back from the encoder.
     *
     * Supported in codecs: VP9
     */
    VP9E_GET_ACTIVEMAP,

    /*!\brief Codec control function to set color range bit.
     * \note Valid ranges: 0..1, default is 0
     *                     0 = Limited range (16..235 or HBD equivalent)
     *                     1 = Full range (0..255 or HBD equivalent)
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_COLOR_RANGE,

    /*!\brief Codec control function to set the frame flags and buffer indices
     * for spatial layers. The frame flags and buffer indices are set using the
     * struct #vpx_svc_ref_frame_config defined below.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_REF_FRAME_CONFIG,

    /*!\brief Codec control function to set intended rendering image size.
     *
     * By default, this is identical to the image size in pixels.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_RENDER_SIZE,

    /*!\brief Codec control function to set target level.
     *
     * 255: off (default); 0: only keep level stats; 10: target for level 1.0;
     * 11: target for level 1.1; ... 62: target for level 6.2
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_TARGET_LEVEL,

    /*!\brief Codec control function to set row level multi-threading.
     *
     * 0 : off, 1 : on
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_ROW_MT,

    /*!\brief Codec control function to get bitstream level.
     *
     * Supported in codecs: VP9
     */
    VP9E_GET_LEVEL,

    /*!\brief Codec control function to enable/disable special mode for altref
     *        adaptive quantization. You can use it with --aq-mode concurrently.
     *
     * Enable special adaptive quantization for altref frames based on their
     * expected prediction quality for the future frames.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_ALT_REF_AQ,

    /*!\brief Boost percentage for Golden Frame in CBR mode.
     *
     * This value controls the amount of boost given to Golden Frame in
     * CBR mode. It is expressed as a percentage of the average
     * per-frame bitrate, with the special (and default) value 0 meaning
     * the feature is off, i.e., no golden frame boost in CBR mode and
     * average bitrate target is used.
     *
     * For example, to allow 100% more bits, i.e, 2X, in a golden frame
     * than average frame, set this to 100.
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_GF_CBR_BOOST_PCT,

    /*!\brief Codec control function to enable the extreme motion vector unit test
     * in VP9. Please note that this is only used in motion vector unit test.
     *
     * 0 : off, 1 : MAX_EXTREME_MV, 2 : MIN_EXTREME_MV
     *
     * Supported in codecs: VP9
     */
    VP9E_ENABLE_MOTION_VECTOR_UNIT_TEST,

    /*!\brief Codec control function to constrain the inter-layer prediction
     * (prediction of lower spatial resolution) in VP9 SVC.
     *
     * 0 : inter-layer prediction on, 1 : off, 2 : off only on non-key frames
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_INTER_LAYER_PRED,

    /*!\brief Codec control function to set mode and thresholds for frame
     *  dropping in SVC. Drop frame thresholds are set per-layer. Mode is set as:
     * 0 : layer-dependent dropping, 1 : constrained dropping, current layer drop
     * forces drop on all upper layers. Default mode is 0.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_FRAME_DROP_LAYER,

    /*!\brief Codec control function to get the refresh and reference flags and
     * the buffer indices, up to the last encoded spatial layer.
     *
     * Supported in codecs: VP9
     */
    VP9E_GET_SVC_REF_FRAME_CONFIG,

    /*!\brief Codec control function to enable/disable use of golden reference as
     * a second temporal reference for SVC. Only used when inter-layer prediction
     * is disabled on INTER frames.
     *
     * 0: Off, 1: Enabled (default)
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_GF_TEMPORAL_REF,

    /*!\brief Codec control function to enable spatial layer sync frame, for any
     * spatial layer. Enabling it for layer k means spatial layer k will disable
     * all temporal prediction, but keep the inter-layer prediction. It will
     * refresh any temporal reference buffer for that layer, and reset the
     * temporal layer for the superframe to 0. Setting the layer sync for base
     * spatial layer forces a key frame. Default is off (0) for all spatial
     * layers. Spatial layer sync flag is reset to 0 after each encoded layer,
     * so when control is invoked it is only used for the current superframe.
     *
     * 0: Off (default), 1: Enabled
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_SVC_SPATIAL_LAYER_SYNC,

    /*!\brief Codec control function to enable temporal dependency model.
     *
     * Vp9 allows the encoder to run temporal dependency model and use it to
     * improve the compression performance. To enable, set this parameter to be
     * 1. The default value is set to be 1.
     */
    VP9E_SET_TPL,

    /*!\brief Codec control function to enable postencode frame drop.
     *
     * This will allow encoder to drop frame after it's encoded.
     *
     * 0: Off (default), 1: Enabled
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_POSTENCODE_DROP,

    /*!\brief Codec control function to set delta q for uv.
     *
     * Cap it at +/-15.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_DELTA_Q_UV,

    /*!\brief Codec control function to disable increase Q on overshoot in CBR.
     *
     * 0: On (default), 1: Disable.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_DISABLE_OVERSHOOT_MAXQ_CBR,

    /*!\brief Codec control function to disable loopfilter.
     *
     * 0: Loopfilter on all frames, 1: Disable on non reference frames.
     * 2: Disable on all frames.
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_DISABLE_LOOPFILTER,

    /*!\brief Codec control function to enable external rate control library.
     *
     * args[0]: path of the rate control library
     *
     * args[1]: private config of the rate control library
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_EXTERNAL_RATE_CONTROL,

    /*!\brief Codec control to disable internal features in rate control.
     *
     * This will do 3 things, only for 1 pass:
     *  - Turn off low motion computation
     *  - Turn off gf update constraint on key frame frequency
     *  - Turn off content mode for cyclic refresh
     *
     * With those, the rate control is expected to work exactly the same as the
     * interface provided in ratectrl_rtc.cc/h
     *
     * Supported in codecs: VP9
     */
    VP9E_SET_RTC_EXTERNAL_RATECTRL,

    /*!\brief Codec control function to get loopfilter level in the encoder.
     *
     * Supported in codecs: VP9
     */
    VP9E_GET_LOOPFILTER_LEVEL,

    /*!\brief Codec control to get last quantizers for all spatial layers.
     *
     * Return value uses an array of internal quantizers scale defined by the
     * codec, for all spatial layers.
     * The size of the array passed in should be #VPX_SS_MAX_LAYERS.
     *
     * Supported in codecs: VP9
     */
    VP9E_GET_LAST_QUANTIZER_SVC_LAYERS,

    /*!\brief Codec control to disable internal features in rate control.
     *
     * This will turn off cyclic refresh for vp8.
     *
     * With this, the rate control is expected to work exactly the same as the
     * interface provided in vp8_ratectrl_rtc.cc/h
     *
     * Supported in codecs: VP8
     */
    VP8E_SET_RTC_EXTERNAL_RATECTRL,
  }

  public enum vpx_scaling_mode_1d
  {
    VP8E_NORMAL,
    VP8E_FOURFIVE,
    VP8E_THREEFIVE,
    VP8E_ONETWO
  }

  /*!\brief Temporal layering mode enum for VP9 SVC.
   *
   * This set of macros define the different temporal layering modes.
   * Supported codecs: VP9 (in SVC mode)
   *
   */
  public enum vp9e_temporal_layering_mode
  {
    /*!\brief No temporal layering.
     * Used when only spatial layering is used.
     */
    VP9E_TEMPORAL_LAYERING_MODE_NOLAYERING,

    /*!\brief Bypass mode.
     * Used when application needs to control temporal layering.
     * This will only work when the number of spatial layers equals 1.
     */
    VP9E_TEMPORAL_LAYERING_MODE_BYPASS,

    /*!\brief 0-1-0-1... temporal layering scheme with two temporal layers.
     */
    VP9E_TEMPORAL_LAYERING_MODE_0101,

    /*!\brief 0-2-1-2... temporal layering scheme with three temporal layers.
     */
    VP9E_TEMPORAL_LAYERING_MODE_0212
  }
}