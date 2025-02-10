/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.h264;

import net.iharder.Base64;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.AVFrame;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.KeyFrameControl;

import java.awt.Dimension;
import java.io.ByteArrayOutputStream;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Decodes H.264 NAL units and returns the resulting frames as FFmpeg <code>AVFrame</code>s (i.e. in YUV format).
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class JNIDecoder extends AbstractCodec2 {
    /**
     * The default output <code>VideoFormat</code>.
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS
            = new VideoFormat[]{new AVFrameFormat(FFmpeg.PIX_FMT_YUV420P)};

    /**
     * Plugin name.
     */
    private static final String PLUGIN_NAME = "H.264 Decoder";

    /**
     * The codec context native pointer we will use.
     */
    private long avctx;

    /**
     * The <code>AVFrame</code> in which the video frame decoded from the encoded media data is stored.
     */
    private AVFrame avframe;

    /**
     * If decoder has got a picture. Use array to pass a pointer
     */
    private final boolean[] got_picture = new boolean[1];

    private boolean gotPictureAtLeastOnce;

    /**
     * The <code>KeyFrameControl</code> used by this <code>JNIDecoder</code> to control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * Array of output <code>VideoFormat</code>s.
     */
    private final VideoFormat[] outputFormats;

    /**
     * The last known width of {@link #avctx} i.e. the video output by this <code>JNIDecoder</code>.
     * Used to detect changes in the output size.
     */
    private int mWidth;

    /**
     * The last known height of {@link #avctx} i.e. the video output by this <code>JNIDecoder</code>.
     * Used to detect changes in the output size.
     */
    private int mHeight;

    /**
     * Initializes a new <code>JNIDecoder</code> instance which is to decode H.264 NAL units into frames in YUV format.
     */
    public JNIDecoder() {
        super("H.264 Decoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);

        /*
         * Explicitly state both ParameterizedVideoFormat (to receive any format parameters which
         * may be of concern to this JNIDecoder) and VideoFormat (to make sure that nothing
         * breaks because of equality and/or matching tests involving ParameterizedVideoFormat).
         */
        inputFormats = new VideoFormat[]{
                new ParameterizedVideoFormat(Constants.H264),
                new VideoFormat(Constants.H264)};

        outputFormats = SUPPORTED_OUTPUT_FORMATS;
    }

    /**
     * Check <code>Format</code>.
     *
     * @param format <code>Format</code> to check
     *
     * @return true if <code>Format</code> is H264_RTP
     */
    public boolean checkFormat(Format format) {
        return format.getEncoding().equals(Constants.H264_RTP);
    }

    /**
     * Close <code>Codec</code>.
     */
    @Override
    protected void doClose() {
        Timber.d("Closing decoder");
        FFmpeg.avcodec_close(avctx);
        FFmpeg.av_free(avctx);
        avctx = 0;

        if (avframe != null) {
            avframe.free();
            avframe = null;
        }
        gotPictureAtLeastOnce = false;
    }

    /**
     * Init the codec instances.
     */
    @Override
    protected void doOpen() throws ResourceUnavailableException {
        Timber.d("Opening decoder");
        if (avframe != null) {
            avframe.free();
            avframe = null;
        }
        avframe = new AVFrame();

        long avcodec = FFmpeg.avcodec_find_decoder(FFmpeg.CODEC_ID_H264);
        if (avcodec == 0) {
            throw new ResourceUnavailableException("Could not find H.264 decoder.");
        }

        avctx = FFmpeg.avcodec_alloc_context3(avcodec);
        FFmpeg.avcodeccontext_set_workaround_bugs(avctx, FFmpeg.FF_BUG_AUTODETECT);

        /* allow to pass the incomplete frame to decoder */
        FFmpeg.avcodeccontext_add_flags2(avctx, FFmpeg.CODEC_FLAG2_CHUNKS);

        if (FFmpeg.avcodec_open2(avctx, avcodec) < 0)
            throw new RuntimeException("Could not open H.264 decoder.");

        gotPictureAtLeastOnce = false;

        /*
         * After this JNIDecoder has been opened, handle format parameters such as
         * sprop-parameter-sets which require this JNIDecoder to be in the opened state.
         */
        handleFmtps();
    }

    /**
     * Decodes H.264 media data read from a specific input <code>Buffer</code> into a specific output <code>Buffer</code>.
     *
     * @param inBuf input <code>Buffer</code>
     * @param outBuf output <code>Buffer</code>
     *
     * @return <code>BUFFER_PROCESSED_OK</code> if <code>in</code> has been successfully processed
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf) {
        // Ask FFmpeg to decode.
        got_picture[0] = false;
        // TODO Take into account the offset of the input Buffer.
        FFmpeg.avcodec_decode_video(avctx, avframe.getPtr(), got_picture, (byte[]) inBuf.getData(), inBuf.getLength());

        if (!got_picture[0]) {
            if ((inBuf.getFlags() & Buffer.FLAG_RTP_MARKER) != 0) {
                if (keyFrameControl != null)
                    keyFrameControl.requestKeyFrame(!gotPictureAtLeastOnce);
            }
            outBuf.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }
        gotPictureAtLeastOnce = true;

        // format: cmeng: must get the output dimension to allow auto rotation
        int width = FFmpeg.avcodeccontext_get_width(avctx);
        int height = FFmpeg.avcodeccontext_get_height(avctx);

        // cmeng (20210309) = decoded avframe.width and avframe.height; does not get updated when inBuf video data is rotated.
        // There h264 currently cannot support auto rotation when remote camera is rotated. VP8 is OK
        if ((width > 0) && (height > 0) && ((mWidth != width) || (mHeight != height))) {
            Timber.d("H264 decode video size changed: [width=%s, height=%s]=>[width=%s, height=%s]", mWidth, mHeight, width, height);

            mWidth = width;
            mHeight = height;

            // Output in same size and frame rate as input.
            Dimension outSize = new Dimension(mWidth, mHeight);
            VideoFormat inFormat = (VideoFormat) inBuf.getFormat();
            float outFrameRate = ensureFrameRate(inFormat.getFrameRate());

            outputFormat = new AVFrameFormat(outSize, outFrameRate, FFmpeg.PIX_FMT_YUV420P);
        }
        outBuf.setFormat(outputFormat);

        // data
        if (outBuf.getData() != avframe)
            outBuf.setData(avframe);

        // timeStamp
        long pts = FFmpeg.avframe_get_pts(avframe.getPtr()); //  FFmpeg.AV_NOPTS_VALUE; // TODO avframe_get_pts(avframe);
        if (pts == FFmpeg.AV_NOPTS_VALUE) {
            outBuf.setTimeStamp(Buffer.TIME_UNKNOWN);
        }
        else {
            outBuf.setTimeStamp(pts);
            int outFlags = outBuf.getFlags();

            outFlags |= Buffer.FLAG_RELATIVE_TIME;
            outFlags &= ~(Buffer.FLAG_RTP_TIME | Buffer.FLAG_SYSTEM_TIME);
            outBuf.setFlags(outFlags);
        }
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Ensure frame rate.
     *
     * @param frameRate frame rate
     *
     * @return frame rate
     */
    private float ensureFrameRate(float frameRate) {
        return frameRate;
    }

    /**
     * Get matching outputs for a specified input <code>Format</code>.
     *
     * @param inputFormat input <code>Format</code>
     *
     * @return array of matching outputs or null if there are no matching outputs.
     */
    protected Format[] getMatchingOutputFormats(Format inputFormat) {
        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;

        return new Format[]{new AVFrameFormat(
                inputVideoFormat.getSize(),
                ensureFrameRate(inputVideoFormat.getFrameRate()),
                FFmpeg.PIX_FMT_YUV420P)};
    }

    /**
     * Get plugin name.
     *
     * @return "H.264 Decoder"
     */
    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    /**
     * Get all supported output <code>Format</code>s.
     *
     * @param inputFormat input <code>Format</code> to determine corresponding output <code>Format/code>s
     *
     * @return an array of supported output <code>Format</code>s
     */
    @Override
    public Format[] getSupportedOutputFormats(Format inputFormat) {
        Format[] supportedOutputFormats;

        if (inputFormat == null) {
            supportedOutputFormats = outputFormats;
        }
        else {
            // mismatch input format
            if (!(inputFormat instanceof VideoFormat)
                    || (AbstractCodec2.matches(inputFormat, inputFormats) == null)) {
                supportedOutputFormats = AbstractCodec2.EMPTY_FORMATS;
            }
            else {
                // match input format
                supportedOutputFormats = getMatchingOutputFormats(inputFormat);
            }
        }
        return supportedOutputFormats;
    }

    /**
     * Handles any format parameters of the input and/or output <code>Format</code>s with which this
     * <code>JNIDecoder</code> has been configured. For example, takes into account the format
     * parameter <code>sprop-parameter-sets</code> if it is specified by the input <code>Format</code>.
     */
    private void handleFmtps() {
        try {
            Format f = getInputFormat();
            if (f instanceof ParameterizedVideoFormat) {
                ParameterizedVideoFormat pvf = (ParameterizedVideoFormat) f;
                String spropParameterSets = pvf.getFormatParameter(
                        VideoMediaFormatImpl.H264_SPROP_PARAMETER_SETS_FMTP);

                if (spropParameterSets != null) {
                    ByteArrayOutputStream nals = new ByteArrayOutputStream();

                    for (String s : spropParameterSets.split(",")) {
                        if ((s != null) && (s.length() != 0)) {
                            byte[] nal = Base64.decode(s);

                            if ((nal != null) && (nal.length != 0)) {
                                nals.write(H264.NAL_PREFIX);
                                nals.write(nal);
                            }
                        }
                    }
                    if (nals.size() != 0) {
                        // Add padding because it seems to be required by FFmpeg.
                        for (int i = 0; i < FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE; i++) {
                            nals.write(0);
                        }

                        /*
                         * In accord with RFC 6184 "RTP Payload Format for H.264 Video", place the
                         * NAL units conveyed by sprop-parameter-sets in the NAL unit stream to
                         * precede any other NAL units in decoding order.
                         */
                        FFmpeg.avcodec_decode_video(avctx, avframe.getPtr(), got_picture,
                                nals.toByteArray(), nals.size());
                    }
                }
            }
            /*
             * Because the handling of format parameter is new at the time of this writing and it
             * currently handles only the format parameter sprop-parameter-sets the failed
             * handling of which will be made visible later on anyway, do not let it kill this
             * JNIDecoder.
             */
        } catch (Throwable t) {
            if (t instanceof InterruptedException)
                Thread.currentThread().interrupt();
            else if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                Timber.e(t, "Failed to handle format parameters");
        }
    }

    /**
     * Sets the <code>Format</code> of the media data to be input for processing in this <code>Codec</code>.
     *
     * @param format the <code>Format</code> of the media data to be input for processing in this <code>Codec</code>
     *
     * @return the <code>Format</code> of the media data to be input for processing in this <code>Codec</code> if
     * <code>format</code> is compatible with this <code>Codec</code>; otherwise, <code>null</code>
     */
    @Override
    public Format setInputFormat(Format format) {
        Format setFormat = super.setInputFormat(format);
        if (setFormat != null)
            reset();
        return setFormat;
    }

    /**
     * Sets the <code>KeyFrameControl</code> to be used by this <code>DePacketizer</code> as a means of
     * control over its key frame-related logic.
     *
     * @param keyFrameControl the <code>KeyFrameControl</code> to be used by this <code>DePacketizer</code> as a means of
     * control over its key frame-related logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl) {
        this.keyFrameControl = keyFrameControl;
    }
}
