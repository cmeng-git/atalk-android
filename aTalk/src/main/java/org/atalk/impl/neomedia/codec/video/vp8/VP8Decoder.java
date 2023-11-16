/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.vp8;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.AVFrame;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.codec.video.VPX;
import org.atalk.service.neomedia.codec.Constants;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Implements a VP8 decoder.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class VP8Decoder extends AbstractCodec2
{
    /**
     * The decoder interface to use
     */
    private static final int INTERFACE = VPX.INTERFACE_VP8_DEC;

    /**
     * Default output formats
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS
            = new VideoFormat[]{new AVFrameFormat(FFmpeg.PIX_FMT_YUV420P)};

    /**
     * Pointer to a native vpx_codec_dec_cfg structure containing the decoder configuration
     */
    private final long cfg = 0;

    /**
     * Pointer to the libvpx codec context to be used
     */
    private long vpctx = 0;

    /**
     * Pointer to a native vpx_image structure, containing a decoded frame.
     * When doProcess() is called, this is either 0, or it has the address of
     * the next unprocessed image from the decoder.
     */
    private long img = 0;

    /**
     * Iterator for the frames in the decoder context. Can be re-initialized by setting its only element to 0.
     */
    private final long[] iter = new long[1];

    /**
     * Whether there are unprocessed frames left from a previous call to VP8.codec_decode()
     */
    private boolean leftoverFrames = false;

    /**
     * The last known height of the video output by this <code>VPXDecoder</code>. Used to detect changes in the output size.
     */
    private int mWidth;

    /**
     * The last known width of the video output by this <code>VPXDecoder</code>. Used to detect changes in the output size.
     */
    private int mHeight;

    /**
     * Initializes a new <code>VPXDecoder</code> instance.
     */
    public VP8Decoder()
    {
        super("VP8 VPX Decoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = new VideoFormat[]{new VideoFormat(Constants.VP8)};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        Timber.d("Closing decoder");
        if (vpctx != 0) {
            VPX.codec_destroy(vpctx);
            VPX.free(vpctx);
        }
        if (cfg != 0)
            VPX.free(cfg);
    }

    /**
     * {@inheritDoc}
     *
     * @throws ResourceUnavailableException if initialization failed
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException
    {
        vpctx = VPX.codec_ctx_malloc();
        //cfg = VPX.codec_dec_cfg_malloc();
        long flags = 0; //VPX.CODEC_USE_XMA;

        // The cfg NULL pointer is passed to vpx_codec_dec_init(). This is to allow the algorithm
        // to determine the stream configuration (width/height) and allocate memory automatically.
        int ret = VPX.codec_dec_init(vpctx, INTERFACE, 0, flags);
        if (ret != VPX.CODEC_OK)
            throw new RuntimeException("Failed to initialize decoder, libvpx error:\n"
                    + VPX.codec_err_to_string(ret));

        Timber.d("VP8 decoder opened successfully");
    }

    int frameCount = 0;

    /**
     * {@inheritDoc}
     *
     * Decodes a VP8 frame contained in <code>inputBuffer</code> into <code>outputBuffer</code> (in <code>AVFrameFormat</code>)
     *
     * @param inputBuffer input <code>Buffer</code>
     * @param outputBuffer output <code>Buffer</code>
     * @return <code>BUFFER_PROCESSED_OK</code> if <code>inBuffer</code> has been successfully processed
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        if (!leftoverFrames) {
            /*
             * All frames from the decoder context have been processed. Decode the next VP8
             * frame, and fill outputBuffer with the first decoded frame.
             */
            byte[] buf_data = (byte[]) inputBuffer.getData();
            int buf_offset = inputBuffer.getOffset();
            int buf_size = inputBuffer.getLength();

            int ret = VPX.codec_decode(vpctx,
                    buf_data, buf_offset, buf_size,
                    0, VPX.DL_BEST_QUALITY);

            // if ((frameCount++ % 50) == 0 || frameCount < 10)
            //     Timber.w("VP8: Decode a frame: %s %s %s", bytesToHex(buf_data, 32), buf_offset, buf_size);

            if (ret != VPX.CODEC_OK) {
                if ((frameCount % 50) == 1)
                    Timber.w("VP8: Discarding frame with decode error: %s %s %s %s", VPX.codec_err_to_string(ret),
                            buf_data, buf_offset, buf_size);
                outputBuffer.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            //decode has just been called, reset iterator
            iter[0] = 0;
            img = VPX.codec_get_frame(vpctx, iter);
        }

        if (img == 0) {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

         // Fill outputBuffer with the newly decoded or leftover frame data.
        updateOutputFormat(
                VPX.img_get_d_w(img),
                VPX.img_get_d_h(img),
                ((VideoFormat) inputBuffer.getFormat()).getFrameRate());
        outputBuffer.setFormat(outputFormat);

        AVFrame avframe = makeAVFrame(img);
        outputBuffer.setData(avframe);

        // YUV420p format, 12 bits per pixel
        outputBuffer.setLength(mWidth * mHeight * 3 / 2);
        outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());

        /*
         * outputBuffer is all setup now. Check the decoder context for more decoded frames.
         */
        img = VPX.codec_get_frame(vpctx, iter);
        if (img == 0) // no more frames
        {
            leftoverFrames = false;
            return BUFFER_PROCESSED_OK;
        }
        else {
            leftoverFrames = true;
            return INPUT_BUFFER_NOT_CONSUMED;
        }
    }

    /**
     * Get matching outputs for a specified input <code>Format</code>.
     *
     * @param inputFormat input <code>Format</code>
     * @return array of matching outputs or null if there are no matching outputs.
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return new Format[]{
                new AVFrameFormat(
                        inputVideoFormat.getSize(),
                        inputVideoFormat.getFrameRate(),
                        FFmpeg.PIX_FMT_YUV420P)
        };
    }

    /**
     * Allocates a new AVFrame and set its data fields to the data fields from the
     * <code>vpx_image_t</code> pointed to by <code>img</code>. Also set its 'linesize' according to <code>img</code>.
     *
     * @param img pointer to a <code>vpx_image_t</code> whose data will be used
     * @return an AVFrame instance with its data fields set to the fields from <code>img</code>
     */
    private AVFrame makeAVFrame(long img)
    {
        AVFrame avframe = new AVFrame();
        long p0 = VPX.img_get_plane0(img);
        long p1 = VPX.img_get_plane1(img);
        long p2 = VPX.img_get_plane2(img);

        //p0, p1, p2 are pointers, while avframe_set_data uses offsets
        FFmpeg.avframe_set_data(avframe.getPtr(),
                p0,
                p1 - p0,
                p2 - p1);

        FFmpeg.avframe_set_linesize(avframe.getPtr(),
                VPX.img_get_stride0(img),
                VPX.img_get_stride1(img),
                VPX.img_get_stride2(img));

        return avframe;
    }

    /**
     * Sets the <code>Format</code> of the media data to be input for processing in this <code>Codec</code>.
     *
     * @param format the <code>Format</code> of the media data to be input for processing in this <code>Codec</code>
     * @return the <code>Format</code> of the media data to be input for processing in this <code>Codec</code>
     * if <code>format</code> is compatible with this <code>Codec</code>; otherwise, <code>null</code>
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format setFormat = super.setInputFormat(format);
        if (setFormat != null)
            reset();
        return setFormat;
    }

    /**
     * Changes the output format, if necessary, according to the new dimensions given via <code>width</code> and <code>height</code>.
     *
     * @param width new width
     * @param height new height
     * @param frameRate frame rate
     */
    private void updateOutputFormat(int width, int height, float frameRate)
    {
        if ((width > 0) && (height > 0)
                && ((mWidth != width) || (mHeight != height))) {
            mWidth = width;
            mHeight = height;
            outputFormat = new AVFrameFormat(
                    new Dimension(width, height),
                    frameRate,
                    FFmpeg.PIX_FMT_YUV420P);
        }
    }
}
