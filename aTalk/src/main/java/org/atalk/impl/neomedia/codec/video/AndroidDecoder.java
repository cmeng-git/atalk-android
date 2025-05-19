/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.awt.Dimension;

import javax.media.Format;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import org.atalk.android.gui.call.RemoteVideoLayout;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.codec.Constants;

import timber.log.Timber;

/**
 * The video decoder based on <code>MediaCodec</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidDecoder extends AndroidCodec {
    /**
     * Name of configuration property that enables hardware decoding.
     */
    public static final String HW_DECODING_ENABLE_PROPERTY = "neomedia.android.hw_decode";

    /**
     * Name of configuration property that enables decoding directly into provided <code>Surface</code> object.
     */
    public static final String DIRECT_SURFACE_DECODE_PROPERTY = "neomedia.android.surface_decode";

    /**
     * Remembers if this instance is using decoding into the <code>Surface</code>.
     */
    private final boolean useOutputSurface;

    /**
     * Output video size.
     */
    private Dimension outputSize;

    /**
     * Surface provider used to obtain <code>SurfaceView</code> object that will be used for decoded remote video rendering.
     */
    public static PreviewSurfaceProvider renderSurfaceProvider;

    /**
     * Default Input formats  supported by android decoder.
     * <a href="https://developer.android.com/guide/topics/media/media-formats#video-formats">...</a>
     */
    private static final VideoFormat[] INPUT_FORMATS = new VideoFormat[]{
            new VideoFormat(Constants.VP9),
            new VideoFormat(Constants.VP8),
            new VideoFormat(Constants.H264),
            new ParameterizedVideoFormat(Constants.H264, VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "0")
    };

    /**
     * Create a new instance of <code>AndroidDecoder</code>.
     */
    public AndroidDecoder() {
        super("AndroidDecoder", VideoFormat.class, getOutputFormats(), false);
        if (isHwDecodingEnabled())
            inputFormats = INPUT_FORMATS;
        else
            inputFormats = EMPTY_FORMATS;

        useOutputSurface = isDirectSurfaceEnabled();
    }

    /**
     * {@inheritDoc}
     * inputFormat is not used to set video size; as the video dimension is not defined prior to received video
     *
     * @see RemoteVideoLayout#setVideoPreferredSize(Dimension, boolean)
     */
    @Override
    protected void configureMediaCodec(MediaCodec codec, String codecType) {
        // assuming device in portrait mode
        MediaFormat format = MediaFormat.createVideoFormat(codecType,
                DeviceConfiguration.DEFAULT_VIDEO_HEIGHT, DeviceConfiguration.DEFAULT_VIDEO_WIDTH);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 8000000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 30);

        // Select color format
        int colorFormat = getColorFormat();
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);

        // https://github.com/google/grafika/blob/master/app/src/main/java/com/android/grafika/PlayMovieSurfaceActivity.java
        Surface surface = useSurface() ? getSurface() : null;
        codec.configure(format, surface, null, 0);
    }

    /**
     * Returns <code>true</code> if hardware decoding is supported and enabled.
     *
     * @return <code>true</code> if hardware decoding is supported and enabled.
     */
    public static boolean isHwDecodingEnabled() {
        return LibJitsi.getConfigurationService().getBoolean(HW_DECODING_ENABLE_PROPERTY, true);
    }

    /**
     * Returns <code>true</code> if decoding into the <code>Surface</code> is enabled.
     * aTalk uses Surface always tied to hardware decode option.
     *
     * @return <code>true</code> if decoding into the <code>Surface</code> is enabled.
     */
    public static boolean isDirectSurfaceEnabled() {
        return isHwDecodingEnabled()
                && LibJitsi.getConfigurationService().getBoolean(DIRECT_SURFACE_DECODE_PROPERTY, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean useSurface() {
        return useOutputSurface;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Surface getSurface() {
        SurfaceHolder sHolder = renderSurfaceProvider.obtainObject();
        return sHolder.getSurface();
    }

    /**
     * Obtain the video output video format based on user defined option:
     * a. None
     * b. Direct surface
     * c. YUV format
     *
     * @return video format as per User selected options
     */
    static Format[] getOutputFormats() {
        if (!isHwDecodingEnabled())
            return EMPTY_FORMATS;

        if (isDirectSurfaceEnabled()) {
            return new Format[]{
                    new VideoFormat(Constants.ANDROID_SURFACE)
            };
        }
        else {
            return new Format[]{new YUVFormat(
                    null, /* size */
                    Format.NOT_SPECIFIED, /* maxDataLength */
                    Format.byteArray,
                    Format.NOT_SPECIFIED, /* frameRate */
                    YUVFormat.YUV_420,
                    Format.NOT_SPECIFIED, /* strideY */
                    Format.NOT_SPECIFIED, /* strideUV */
                    Format.NOT_SPECIFIED, /* offsetY */
                    Format.NOT_SPECIFIED, /* offsetU */
                    Format.NOT_SPECIFIED /* offsetV */
            )};
        }
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
        if (!(inputFormat instanceof VideoFormat))
            return EMPTY_FORMATS;

        VideoFormat inputVideoFormat = (VideoFormat) inputFormat;
        if (useSurface()) {
            return new VideoFormat[]{
                    // new SurfaceFormat(inputVideoFormat.getSize()),
                    new VideoFormat(
                            Constants.ANDROID_SURFACE,
                            inputVideoFormat.getSize(),
                            Format.NOT_SPECIFIED,
                            Surface.class,
                            Format.NOT_SPECIFIED
                    )
            };
        }
        else {
            return new VideoFormat[]{new YUVFormat(
                    inputVideoFormat.getSize(), /* size */
                    Format.NOT_SPECIFIED, /* maxDataLength */
                    Format.byteArray,
                    Format.NOT_SPECIFIED, /* frameRate */
                    YUVFormat.YUV_420,
                    Format.NOT_SPECIFIED, /* strideY */
                    Format.NOT_SPECIFIED, /* strideUV */
                    Format.NOT_SPECIFIED, /* offsetY */
                    Format.NOT_SPECIFIED, /* offsetU */
                    Format.NOT_SPECIFIED /* offsetV */)
            };
        }
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

        VideoFormat videoFormat = (VideoFormat) format;
        if (format instanceof YUVFormat) {
            YUVFormat yuvFormat = (YUVFormat) videoFormat;
            outputFormat = new YUVFormat(
                    outputSize, /* size */
                    videoFormat.getMaxDataLength(), /* maxDataLength */
                    Format.byteArray,
                    videoFormat.getFrameRate(), /* frameRate */
                    YUVFormat.YUV_420,
                    yuvFormat.getStrideY(), /* strideY */
                    yuvFormat.getStrideUV(), /* strideUV */
                    yuvFormat.getOffsetY(), /* offsetY */
                    yuvFormat.getOffsetU(), /* offsetU */
                    yuvFormat.getOffsetV()) /* offsetV */;
        }
        else {
            outputFormat = new VideoFormat(videoFormat.getEncoding(), outputSize,
                    videoFormat.getMaxDataLength(), videoFormat.getDataType(),
                    videoFormat.getFrameRate());
        }
        // Return the selected outputFormat
        return outputFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose() {
        super.doClose();
        renderSurfaceProvider.onObjectReleased();
    }

    @Override
    protected void onSizeChanged(Dimension dimension) {
        Timber.d("Decode video size change: %s => %s", outputSize, dimension);
        outputSize = dimension;
        setOutputFormat(outputFormat);
    }
}
