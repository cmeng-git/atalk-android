/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import static org.atalk.impl.neomedia.codec.video.CodecInfo.MEDIA_CODEC_TYPE_VP9;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;
import javax.media.protocol.DataSource;

import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.VideoMediaStreamImpl;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.codec.Constants;

import timber.log.Timber;

/**
 * Video encoder based on <code>MediaCodec</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidEncoder extends AndroidCodec {
    /**
     * Name of configuration property that enables this encoder.
     */
    public static final String HW_ENCODING_ENABLE_PROPERTY = "neomedia.android.hw_encode";

    /**
     * Name of configuration property that enables usage of <code>Surface</code> object as a source of video data.
     */
    public static final String DIRECT_SURFACE_ENCODE_PROPERTY = "neomedia.android.surface_encode";

    /**
     * Indicates if this instance is using <code>Surface</code> for data source.
     */
    private final boolean useInputSurface;

    /**
     * Input <code>Surface</code> object.
     */
    private Surface mInputSurface;

    /**
     * Default output formats supported by this android encoder
     * see: <a href="https://developer.android.com/guide/topics/media/media-formats#video-formats">...</a>
     */
    private static final VideoFormat[] SUPPORTED_OUTPUT_FORMATS;

    /**
     * List of vFormats supported by this android device. VP9 encoder only supported on certain android device.
     */
    private static final List<VideoFormat> vFormats = new ArrayList<>();

    static {
        if (CodecInfo.getCodecForType(MEDIA_CODEC_TYPE_VP9, true) != null) {
            vFormats.add(new VideoFormat(Constants.VP9));
        }
        vFormats.add(new VideoFormat(Constants.VP8));
        vFormats.add(new ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0"));
        vFormats.add(new ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1"));
        SUPPORTED_OUTPUT_FORMATS = vFormats.toArray(vFormats.toArray(new VideoFormat[0]));
    }

    /**
     * Creates new instance of <code>AndroidEncoder</code>.
     */
    public AndroidEncoder() {
        super("AndroidEncoder", VideoFormat.class, isHwEncodingEnabled()
                ? SUPPORTED_OUTPUT_FORMATS : EMPTY_FORMATS, true);

        useInputSurface = isDirectSurfaceEnabled();
        if (useInputSurface) {
            inputFormats = new VideoFormat[]{new VideoFormat(
                    Constants.ANDROID_SURFACE,
                    null,
                    Format.NOT_SPECIFIED,
                    Surface.class,
                    Format.NOT_SPECIFIED)};
        }
        else {
            inputFormats = new VideoFormat[]{new YUVFormat(
                    null, /* size */
                    Format.NOT_SPECIFIED,
                    Format.byteArray, /* maxDataLength */
                    Format.NOT_SPECIFIED, /* frameRate */
                    YUVFormat.YUV_420,
                    Format.NOT_SPECIFIED, /* strideY */
                    Format.NOT_SPECIFIED, /* strideUV */
                    Format.NOT_SPECIFIED, /* offsetY */
                    Format.NOT_SPECIFIED, /* offsetU */
                    Format.NOT_SPECIFIED /* offsetV */)
            };
        }
        inputFormat = null;
        outputFormat = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureMediaCodec(MediaCodec codec, String codecType)
            throws ResourceUnavailableException {
        if (inputFormat == null)
            throw new ResourceUnavailableException("Output format not set");

        Dimension size = ((VideoFormat) inputFormat).getSize();
        if (size == null)
            throw new ResourceUnavailableException("Size not set");

        if (aTalkApp.isPortrait) {
            size = new Dimension(size.height, size.width);
        }
        Timber.d("Encoder video input format: %s => %s", inputFormat, size);

        // Setup encoder properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        MediaFormat format = MediaFormat.createVideoFormat(codecType, size.width, size.height);
        int colorFormat = getColorFormat();
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

        int bitrate = ((MediaServiceImpl) LibJitsi.getMediaService()).getDeviceConfiguration().getVideoBitrate() * 1024;
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (useInputSurface) {
            mInputSurface = codec.createInputSurface();
        }
    }

    /**
     * Returns <code>true</code> if hardware encoding is enabled.
     *
     * @return <code>true</code> if hardware encoding is enabled.
     */
    private static boolean isHwEncodingEnabled() {
        // boolean supported = AppUtils.hasAPI(16);
        return LibJitsi.getConfigurationService().getBoolean(HW_ENCODING_ENABLE_PROPERTY, true);
    }

    /**
     * Returns <code>true</code> if input <code>Surface</code> mode is enabled.
     * aTalk uses Surface always tied to hardware encode option.
     *
     * @return <code>true</code> if input <code>Surface</code> mode is enabled.
     */
    public static boolean isDirectSurfaceEnabled() {
        return isHwEncodingEnabled()
                && LibJitsi.getConfigurationService().getBoolean(DIRECT_SURFACE_ENCODE_PROPERTY, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean useSurface() {
        return useInputSurface;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Surface getSurface() {
        return mInputSurface;
    }

    /**
     * Check if the specified hardware encoder is supported on this android device.
     *
     * @param codec Encoder name
     *
     * @return true if supported
     *
     * @see VideoMediaStreamImpl#selectVideoSize(DataSource, int, int)
     */
    public static boolean isCodecSupported(String codec) {
        return isDirectSurfaceEnabled() && vFormats.toString().contains(codec);
    }

    /**
     * Gets the matching output formats for a specific format.
     *
     * @param inputFormat input format
     *
     * @return array of formats matching input format
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat) {
        if (!(inputFormat instanceof VideoFormat) || !isHwEncodingEnabled())
            return EMPTY_FORMATS;

        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;
        Dimension size = inputVideoFormat.getSize();
        float frameRate = inputVideoFormat.getFrameRate();

        return new VideoFormat[]{
                new VideoFormat(Constants.VP9, size, /* maxDataLength */
                        Format.NOT_SPECIFIED, Format.byteArray, frameRate),
                new VideoFormat(Constants.VP8, size, /* maxDataLength */
                        Format.NOT_SPECIFIED, Format.byteArray, frameRate),
                new ParameterizedVideoFormat(Constants.H264, size, Format.NOT_SPECIFIED,
                        Format.byteArray, frameRate, ParameterizedVideoFormat.toMap(
                        VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0")),
                new ParameterizedVideoFormat(Constants.H264, size, Format.NOT_SPECIFIED,
                        Format.byteArray, frameRate, ParameterizedVideoFormat.toMap(
                        VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1"))
        };
    }

    /**
     * Sets the input format.
     *
     * @param format format to set
     *
     * @return the selected inputFormat
     */
    @Override
    public Format setInputFormat(Format format) {
        if (!(format instanceof VideoFormat) || (matches(format, inputFormats) == null))
            return null;

        inputFormat = format;
        // Timber.d(new Exception(),"Encoder video input format set: %s", inputFormat);

        // Return the selected inputFormat
        return inputFormat;
    }

    /**
     * Sets the <code>Format</code> in which this <code>Codec</code> is to output media data.
     *
     * @param format the <code>Format</code> in which this <code>Codec</code> is to output media data
     *
     * @return the <code>Format</code> in which this <code>Codec</code> is currently configured to output
     * media data or <code>null</code> if <code>format</code> was found to be incompatible with this <code>Codec</code>
     */
    @Override
    public Format setOutputFormat(Format format) {
        if (!(format instanceof VideoFormat)
                || (matches(format, getMatchingOutputFormats(inputFormat)) == null))
            return null;

        // Timber.d(new Exception(),"Encoder video output format set: %s", inputFormat);
        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * An Encoder translates raw media data in (en)coded media data. Consequently, the size of
         * the output is equal to the size of the input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        outputFormat = new VideoFormat(videoFormat.getEncoding(), size,
                /* maxDataLength */ Format.NOT_SPECIFIED, videoFormat.getDataType(), videoFormat.getFrameRate());

        // Return the selected outputFormat
        return outputFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose() {
        super.doClose();

        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
    }
}