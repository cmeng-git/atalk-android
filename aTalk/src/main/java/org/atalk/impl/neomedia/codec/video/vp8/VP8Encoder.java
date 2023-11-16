/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.vp8;

import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.codec.video.VPX;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.service.neomedia.codec.Constants;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import timber.log.Timber;

/**
 * Implements a VP8 encoder.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class VP8Encoder extends AbstractCodec2
{
    /**
     * VPX interface to use
     */
    private static final int INTERFACE = VPX.INTERFACE_VP8_ENC;

    /**
     * Default output formats
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS = new VideoFormat[]{new VideoFormat(Constants.VP8)};

    /**
     * Pointer to a native vpx_codec_dec_cfg structure containing encoder configuration
     */
    private long cfg = 0;

    /**
     * Pointer to the libvpx codec context to be used
     */
    private long vpctx = 0;

    /**
     * Flags passed when (re-)initializing the encoder context on first and when orientation change
     */
    private long flags = 0;

    /**
     * Number of encoder frames so far. Used as pst (presentation time stamp)
     */
    private long frameCount = 0;

    /**
     * Pointer to a native vpx_image instance used to feed frames to the encoder
     */
    private long img = 0;

    /**
     * Iterator for the compressed frames in the encoder context.
     * Can be re-initialized by setting its only element to 0.
     */
    private final long[] iter = new long[1];

    /**
     * Whether there are unprocessed packets left from a previous call to VP8.codec_encode()
     */
    private boolean leftoverPackets = false;

    /**
     * Pointer to a vpx_codec_cx_pkt_t
     */
    private long pkt = 0;

    /**
     * Current width and height of the input and output frames
     * Assume the device is always started in portrait mode with weight and height swap for use in the codec;
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private int mWidth = DeviceConfiguration.DEFAULT_VIDEO_HEIGHT;
    @SuppressWarnings("SuspiciousNameCombination")
    private int mHeight = DeviceConfiguration.DEFAULT_VIDEO_WIDTH;

    /**
     * Initializes a new <code>VP8Encoder</code> instance.
     */
    public VP8Encoder()
    {
        super("VP8 Encoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = new VideoFormat[]{new YUVFormat(
                null, /* size */
                Format.NOT_SPECIFIED, /* maxDataLength */
                Format.byteArray,
                Format.NOT_SPECIFIED, /* frameRate */
                YUVFormat.YUV_420,
                Format.NOT_SPECIFIED, /* strideY */
                Format.NOT_SPECIFIED, /* strideUV */
                Format.NOT_SPECIFIED, /* offsetY */
                Format.NOT_SPECIFIED, /* offsetU */
                Format.NOT_SPECIFIED) /* offsetV */
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        Timber.d("Closing encoder");
        if (vpctx != 0) {
            VPX.codec_destroy(vpctx);
            VPX.free(vpctx);
            vpctx = 0;
        }
        if (img != 0) {
            VPX.free(img);
            img = 0;
        }
        if (cfg != 0) {
            VPX.free(cfg);
            cfg = 0;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException
    {
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the input.
         */
        VideoFormat ipFormat = (VideoFormat) inputFormat;
        VideoFormat opFormat = (VideoFormat) outputFormat;

        Dimension size = null;
        if (ipFormat != null)
            size = ipFormat.getSize();
        if ((size == null) && (opFormat != null))
            size = opFormat.getSize();

        // Use the default if format size is null
        if (size != null) {
            Timber.d("VP8 encode video size: %s", size);
            mWidth = size.width;
            mHeight = size.height;
        }

        img = VPX.img_alloc(img, VPX.IMG_FMT_I420, mWidth, mHeight, 1);
        if (img == 0) {
            throw new RuntimeException("Failed to allocate image.");
        }

        cfg = VPX.codec_enc_cfg_malloc();
        if (cfg == 0) {
            throw new RuntimeException("Could not codec_enc_cfg_malloc()");
        }
        VPX.codec_enc_config_default(INTERFACE, cfg, 0);

        // setup the decoder required parameter settings
        int bitRate = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration().getVideoBitrate();
        VPX.codec_enc_cfg_set_w(cfg, mWidth);
        VPX.codec_enc_cfg_set_h(cfg, mHeight);
        VPX.codec_enc_cfg_set_rc_target_bitrate(cfg, bitRate);
        VPX.codec_enc_cfg_set_rc_resize_allowed(cfg, 1);
        VPX.codec_enc_cfg_set_rc_end_usage(cfg, VPX.RC_MODE_CBR);
        VPX.codec_enc_cfg_set_kf_mode(cfg, VPX.KF_MODE_AUTO);

        // cfg.g_lag_in_frames should be set to 0 for realtime and allow dynamic size change after init
        VPX.codec_enc_cfg_set_lag_in_frames(cfg, 0);

        VPX.codec_enc_cfg_set_error_resilient(cfg, VPX.ERROR_RESILIENT_DEFAULT | VPX.ERROR_RESILIENT_PARTITIONS);

        vpctx = VPX.codec_ctx_malloc();
        flags = 0;
        int ret = VPX.codec_enc_init(vpctx, INTERFACE, cfg, flags);
        if (ret != VPX.CODEC_OK)
            throw new RuntimeException("Failed to initialize encoder, libvpx error:\n"
                    + VPX.codec_err_to_string(ret));

        if (inputFormat == null)
            throw new ResourceUnavailableException("No input format selected");
        if (outputFormat == null)
            throw new ResourceUnavailableException("No output format selected");

        Timber.d("VP8 encoder opened successfully");
    }

    /**
     * {@inheritDoc}
     *
     * Encode the frame in <code>inputBuffer</code> (in <code>YUVFormat</code>) into a VP8 frame (in <code>outputBuffer</code>)
     *
     * @param inputBuffer  input <code>Buffer</code>
     * @param outputBuffer output <code>Buffer</code>
     * @return <code>BUFFER_PROCESSED_OK</code> if <code>inBuffer</code> has been successfully processed
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        int ret = BUFFER_PROCESSED_OK;
        byte[] output;

        if (!leftoverPackets) {
            YUVFormat format = (YUVFormat) inputBuffer.getFormat();
            Dimension formatSize = format.getSize();
            int width = formatSize.width;
            int height = formatSize.height;

            flags = 0;
            if (width > 0 && height > 0
                    && (width != mWidth || height != mHeight)) {
                Timber.d("VP8 encode video size changed: [width=%s, height=%s]=>%s", mWidth, mHeight, formatSize);
                updateSize(width, height);
            }

            int offsetY = format.getOffsetY();
            if (offsetY == Format.NOT_SPECIFIED)
                offsetY = 0;
            int offsetU = format.getOffsetU();
            if (offsetU == Format.NOT_SPECIFIED)
                offsetU = offsetY + width * height;
            int offsetV = format.getOffsetV();
            if (offsetV == Format.NOT_SPECIFIED)
                offsetV = offsetU + (width * height) / 4;

            // if (frameCount < 5)
            //  Timber.d("VP8: Encoding a frame #%s: %s %s", frameCount, bytesToHex((byte[]) inputBuffer.getData(), 32), inputBuffer.getLength());

            int result = VPX.codec_encode(vpctx, img, (byte[]) inputBuffer.getData(),
                    offsetY, offsetU, offsetV,
                    frameCount++, 1, flags, VPX.DL_REALTIME);

            if (result != VPX.CODEC_OK) {
                Timber.w("Failed to encode a frame: %s", VPX.codec_err_to_string(result));
                outputBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }
            iter[0] = 0;
            pkt = VPX.codec_get_cx_data(vpctx, iter);
        }

        if (pkt != 0
                && VPX.codec_cx_pkt_get_kind(pkt) == VPX.CODEC_CX_FRAME_PKT) {
            int size = VPX.codec_cx_pkt_get_size(pkt);
            long data = VPX.codec_cx_pkt_get_data(pkt);
            output = validateByteArraySize(outputBuffer, size, false);
            VPX.memcpy(output, data, size);
            outputBuffer.setOffset(0);
            outputBuffer.setLength(size);
            outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
        }
        else {
            // not a compressed frame, skip this packet
            Timber.w("Skip partial compressed frame packet: %s: %s", pkt, frameCount);
            ret |= OUTPUT_BUFFER_NOT_FILLED;
        }

        // Check for more encoded frame
        pkt = VPX.codec_get_cx_data(vpctx, iter);
        leftoverPackets = (pkt != 0);
        if (leftoverPackets)
            return ret | INPUT_BUFFER_NOT_CONSUMED;
        else {
            return ret;
        }
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     * @return array of formats matching input format
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return new VideoFormat[]{new VideoFormat(
                Constants.VP8,
                inputVideoFormat.getSize(),
                Format.NOT_SPECIFIED, /* maxDataLength */
                Format.byteArray,
                inputVideoFormat.getFrameRate())
        };
    }

    /**
     * Updates the input width and height the encoder should expect.
     * Force keyframe generation; needed when the input size changes.
     *
     * @param w new width
     * @param h new height
     */
    private void updateSize(int w, int h)
    {
        mWidth = w;
        mHeight = h;
        img = VPX.img_alloc(img, VPX.IMG_FMT_I420, mWidth, mHeight, 1);
        if (img == 0)
            throw new RuntimeException("Failed to re-initialize VP8 encoder");

        if (cfg != 0) {
            VPX.codec_enc_cfg_set_w(cfg, w);
            VPX.codec_enc_cfg_set_h(cfg, h);
        }

        // Dynamic video resolution change is not implemented in vp8 encoder
        // vp8_cx_iface.vp8e_set_config throws ERROR("Cannot increase width or height larger than their initial values");
        // VPX.codec_enc_config_set(vpctx, cfg);
        flags |= VPX.EFLAG_FORCE_KF;

         if (vpctx != 0) {
             // VPX.codec_destroy(vpctx);

             int ret = VPX.codec_enc_init(vpctx, INTERFACE, cfg, flags);
             if (ret != VPX.CODEC_OK)
                 throw new RuntimeException("Failed to re-initialize VP8 encoder, libvpx error:\n"
                         + VPX.codec_err_to_string(ret));
         }
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     * @return format
     */
    @Override
    public Format setInputFormat(Format format)
    {
        if (!(format instanceof VideoFormat)
                || (matches(format, inputFormats) == null))
            return null;

        YUVFormat yuvFormat = (YUVFormat) format;
        if (yuvFormat.getOffsetU() > yuvFormat.getOffsetV())
            return null;

        // Return the selected inputFormat
        inputFormat = specialize(yuvFormat, Format.byteArray);
        return inputFormat;
    }

    /**
     * Sets the <code>Format</code> in which this <code>Codec</code> is to output media data.
     *
     * @param format the <code>Format</code> in which this <code>Codec</code> is to output media data
     * @return the <code>Format</code> in which this <code>Codec</code> is currently configured to output
     * media data or <code>null</code> if <code>format</code> was found to be incompatible with this <code>Codec</code>
     */
    @Override
    public Format setOutputFormat(Format format)
    {
        if (!(format instanceof VideoFormat)
                || (matches(format, getMatchingOutputFormats(inputFormat)) == null))
            return null;

        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * An Encoder translates raw media data in (en)coded media data.
         * Consequently, the size of the output is equal to the size of the input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        outputFormat = new VideoFormat(
                videoFormat.getEncoding(),
                size,
                Format.NOT_SPECIFIED, /* maxDataLength */
                Format.byteArray,
                videoFormat.getFrameRate()
        );

        // Return the selected outputFormat
        return outputFormat;
    }
}
