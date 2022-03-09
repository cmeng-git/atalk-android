/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format;

import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.service.neomedia.format.VideoMediaFormat;
import org.atalk.util.MediaType;

import java.awt.Dimension;
import java.util.Map;

import javax.media.format.VideoFormat;

/**
 * Implements <code>VideoMediaFormat</code> for the JMF <code>VideoFormat</code>.
 *
 * @author Lyubomir Marinov
 */
public class VideoMediaFormatImpl extends MediaFormatImpl<VideoFormat>
        implements VideoMediaFormat
{

    /**
     * The default value of the <code>clockRate</code> property of <code>VideoMediaFormatImpl</code>.
     */
    public static final double DEFAULT_CLOCK_RATE = 90000;

    /**
     * The name of the format parameter which specifies the packetization mode of H.264 RTP payload.
     */
    public static final String H264_PACKETIZATION_MODE_FMTP = "packetization-mode";

    public static final String H264_SPROP_PARAMETER_SETS_FMTP = "sprop-parameter-sets";

    /**
     * The clock rate of this <code>VideoMediaFormat</code>.
     */
    private final double clockRate;

    /**
     * Initializes a new <code>VideoMediaFormatImpl</code> instance with a specific encoding.
     *
     * @param encoding the encoding of the new <code>VideoMediaFormatImpl</code> instance
     */
    VideoMediaFormatImpl(String encoding)
    {
        this(encoding, DEFAULT_CLOCK_RATE);
    }

    /**
     * Initializes a new <code>VideoMediaFormatImpl</code> instance with a specific encoding and a
     * specific clock rate.
     *
     * @param encoding the encoding of the new <code>VideoMediaFormatImpl</code> instance
     * @param clockRate the clock rate of the new <code>VideoMediaFormatImpl</code> instance
     */
    VideoMediaFormatImpl(String encoding, double clockRate)
    {
        this(new VideoFormat(encoding), clockRate);
    }

    /**
     * Initializes a new <code>VideoMediaFormatImpl</code> instance which is to provide an
     * implementation of <code>VideoMediaFormat</code> for a specific JMF <code>VideoFormat</code>.
     *
     * @param format the JMF <code>VideoFormat</code> the new instance is to wrap and provide an implementation
     * of <code>VideoMediaFormat</code> for
     */
    VideoMediaFormatImpl(VideoFormat format)
    {
        this(format, DEFAULT_CLOCK_RATE);
    }

    /**
     * Initializes a new <code>VideoMediaFormatImpl</code> instance which is to provide an
     * implementation of <code>VideoMediaFormat</code> for a specific JMF <code>VideoFormat</code> and to
     * have a specific clock rate.
     *
     * @param format the JMF <code>VideoFormat</code> the new instance is to wrap and provide an implementation
     * of <code>VideoMediaFormat</code> for
     * @param clockRate the clock rate of the new <code>VideoMediaFormatImpl</code> instance
     */
    VideoMediaFormatImpl(VideoFormat format, double clockRate)
    {
        this(format, clockRate, -1, null, null);
    }

    /**
     * Initializes a new <code>VideoMediaFormatImpl</code> instance which is to provide an
     * implementation of <code>VideoMediaFormat</code> for a specific JMF <code>VideoFormat</code> and to
     * have specific clock rate and set of format-specific parameters.
     *
     * @param format the JMF <code>VideoFormat</code> the new instance is to wrap and provide an implementation
     * of <code>VideoMediaFormat</code> for
     * @param clockRate the clock rate of the new <code>VideoMediaFormatImpl</code> instance
     * @param frameRate the frame rate of the new <code>VideoMediaFormatImpl</code> instance
     * @param formatParameters the set of format-specific parameters of the new instance
     * @param advancedParameters set of advanced parameters of the new instance
     */
    VideoMediaFormatImpl(VideoFormat format, double clockRate, float frameRate,
            Map<String, String> formatParameters, Map<String, String> advancedParameters)
    {
        super(new ParameterizedVideoFormat(
                        format.getEncoding(),
                        format.getSize(),
                        format.getMaxDataLength(),
                        format.getDataType(),
                        frameRate,
                        formatParameters),
                formatParameters, advancedParameters);

        this.clockRate = clockRate;
    }

    /**
     * Implements <code>MediaFormat#equals(Object)</code> and actually compares the encapsulated JMF
     * <code>Format</code> instances.
     *
     * @param mediaFormat the object that we'd like to compare <code>this</code> one to
     * @return <code>true</code> if the JMF <code>Format</code> instances encapsulated by this instance and
     * their other characteristics are equal; <code>false</code>, otherwise.
     * @see MediaFormatImpl#equals(Object)
     */
    @Override
    public boolean equals(Object mediaFormat)
    {
        if (this == mediaFormat)
            return true;

        if (!super.equals(mediaFormat))
            return false;

        VideoMediaFormatImpl videoMediaFormatImpl = (VideoMediaFormatImpl) mediaFormat;

        double clockRate = getClockRate();
        double videoMediaFormatImplClockRate = videoMediaFormatImpl.getClockRate();

        if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clockRate)
            clockRate = DEFAULT_CLOCK_RATE;
        if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == videoMediaFormatImplClockRate)
            videoMediaFormatImplClockRate = DEFAULT_CLOCK_RATE;

        return (clockRate == videoMediaFormatImplClockRate);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Takes into account RFC 3984 "RTP Payload Format for H.264 Video" which says that &quot;
     * [w]hen the value of packetization-mode [format parameter] is equal to 0 or packetization-mode is
     * not present, the single NAL mode, as defined in section 6.2 of RFC 3984, MUST be used.&quot;
     * </p>
     *
     * @see MediaFormatImpl#formatParametersAreEqual(Map, Map)
     */
    @Override
    protected boolean formatParametersAreEqual(Map<String, String> fmtps1, Map<String, String> fmtps2)
    {
        return formatParametersAreEqual(getEncoding(), fmtps1, fmtps2);
    }

    /**
     * Determines whether a specific set of format parameters is equal to another set of format
     * parameters in the sense that they define an equal number of parameters and assign them equal
     * values. Since the values are <code>String</code> s, presumes that a value of <code>null</code> is
     * equal to the empty <code>String</code>.
     * <p>
     * The two <code>Map</code> instances of format parameters to be checked for equality are presumed
     * to be modifiable in the sense that if the lack of a format parameter in a given <code>Map</code>
     * is equivalent to it having a specific value, an association of the format parameter to the
     * value in question may be added to or removed from the respective <code>Map</code> instance for
     * the purposes of determining equality.
     * </p>
     *
     * @param encoding the encoding (name) related to the two sets of format parameters to be tested for equality
     * @param fmtps1 the first set of format parameters to be tested for equality
     * @param fmtps2 the second set of format parameters to be tested for equality
     * @return <code>true</code> if the specified sets of format parameters are equal; <code>false</code>, otherwise
     */
    public static boolean formatParametersAreEqual(String encoding, Map<String, String> fmtps1,
            Map<String, String> fmtps2)
    {
        /*
         * RFC 3984 "RTP Payload Format for H.264 Video" says that "[w]hen the value of
         * packetization-mode is equal to 0 or packetization-mode is not present, the single NAL
         * mode, as defined in section 6.2 of RFC 3984, MUST be used."
         */
        if ("H264".equalsIgnoreCase(encoding) || "h264/rtp".equalsIgnoreCase(encoding)) {
            String packetizationMode = H264_PACKETIZATION_MODE_FMTP;
            String pm1 = null;
            String pm2 = null;

            if (fmtps1 != null)
                pm1 = fmtps1.remove(packetizationMode);
            if (fmtps2 != null)
                pm2 = fmtps2.remove(packetizationMode);

            if (pm1 == null)
                pm1 = "0";
            if (pm2 == null)
                pm2 = "0";
            if (!pm1.equals(pm2))
                return false;
        }
        return MediaFormatImpl.formatParametersAreEqual(encoding, fmtps1, fmtps2);
    }

    /**
     * Determines whether the format parameters of this <code>MediaFormat</code> match a specific
     * set of format parameters.
     * <p>
     * <code>VideoMediaFormat</code> reflects the fact that the <code>packetization-mode</code> format
     * parameter distinguishes H.264 payload types.
     * </p>
     *
     * @param fmtps the set of format parameters to match to the format parameters of this <code>MediaFormat</code>
     * @return <code>true</code> if this <code>MediaFormat</code> considers <code>fmtps</code> matching its
     * format parameters; otherwise, <code>false</code>
     */
    @Override
    public boolean formatParametersMatch(Map<String, String> fmtps)
    {
        return formatParametersMatch(getEncoding(), getFormatParameters(), fmtps)
                && super.formatParametersMatch(fmtps);
    }

    /**
     * Determines whether two sets of format parameters match in the context of a specific encoding.
     *
     * @param encoding the encoding (name) related to the two sets of format parameters to be matched.
     * @param fmtps1 the first set of format parameters which is to be matched against <code>fmtps2</code>
     * @param fmtps2 the second set of format parameters which is to be matched against <code>fmtps1</code>
     * @return <code>true</code> if the two sets of format parameters match in the context of the
     * specified <code>encoding</code>; otherwise, <code>false</code>
     */
    public static boolean formatParametersMatch(String encoding, Map<String, String> fmtps1, Map<String, String> fmtps2)
    {
        /*
         * RFC 3984 "RTP Payload Format for H.264 Video" says that "When the value of
         * packetization-mode is equal to 0 or packetization-mode is not present, the single NAL
         * mode, as defined in section 6.2 of RFC 3984, MUST be used."
         */
        if ("H264".equalsIgnoreCase(encoding) || "h264/rtp".equalsIgnoreCase(encoding)) {
            String packetizationMode = H264_PACKETIZATION_MODE_FMTP;
            String pm1 = (fmtps1 == null) ? null : fmtps1.get(packetizationMode);
            String pm2 = (fmtps2 == null) ? null : fmtps2.get(packetizationMode);

            if (pm1 == null)
                pm1 = "0";
            if (pm2 == null)
                pm2 = "0";
            if (!pm1.equals(pm2))
                return false;
        }

        return true;
    }

    /**
     * Gets the clock rate associated with this <code>MediaFormat</code>.
     *
     * @return the clock rate associated with this <code>MediaFormat</code>
     * @see MediaFormat#getClockRate()
     */
    public double getClockRate()
    {
        return clockRate;
    }

    /**
     * Gets the frame rate associated with this <code>MediaFormat</code>.
     *
     * @return the frame rate associated with this <code>MediaFormat</code>
     * @see VideoMediaFormat#getFrameRate()
     */
    public float getFrameRate()
    {
        return format.getFrameRate();
    }

    /**
     * Gets the type of this <code>MediaFormat</code> which is {@link MediaType#VIDEO} for
     * <code>AudioMediaFormatImpl</code> instances.
     *
     * @return the <code>MediaType</code> that this format represents and which is
     * <code>MediaType.VIDEO</code> for <code>AudioMediaFormatImpl</code> instances
     * @see MediaFormat#getMediaType()
     */
    public final MediaType getMediaType()
    {
        return MediaType.VIDEO;
    }

    /**
     * Gets the size of the image that this <code>VideoMediaFormat</code> describes.
     *
     * @return a {@link Dimension} instance indicating the image size (in pixels) of this <code>VideoMediaFormat</code>
     * @see VideoMediaFormat#getSize()
     */
    public Dimension getSize()
    {
        return format.getSize();
    }

    /**
     * Overrides <code>MediaFormatImpl#hashCode()</code> because <code>Object#equals(Object)</code> is overridden.
     *
     * @return a hash code value for this <code>VideoMediaFormatImpl</code>
     * @see MediaFormatImpl#hashCode()
     */
    @Override
    public int hashCode()
    {
        double clockRate = getClockRate();

        /*
         * The implementation of #equals(Object) of this instance assumes that
         * MediaFormatFactory#CLOCK_RATE_NOT_SPECIFIED and #DEFAULT_CLOCK_RATE are equal.
         */
        if (MediaFormatFactory.CLOCK_RATE_NOT_SPECIFIED == clockRate)
            clockRate = DEFAULT_CLOCK_RATE;
        return (super.hashCode() | Double.valueOf(clockRate).hashCode());
    }
}
