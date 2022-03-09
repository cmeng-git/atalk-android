/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format;

import org.atalk.impl.neomedia.MediaUtils;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.util.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Implements <code>MediaFormatFactory</code> for the JMF <code>Format</code> types.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class MediaFormatFactoryImpl implements MediaFormatFactory
{
    /**
     * Creates an unknown <code>MediaFormat</code>.
     *
     * @param type <code>MediaType</code>
     * @return unknown <code>MediaFormat</code>
     */
    public MediaFormat createUnknownMediaFormat(MediaType type)
    {
        Format unknown = null;

        /*
         * FIXME Why is a VideoFormat instance created for MediaType.AUDIO and an AudioFormat
         * instance for MediaType.VIDEO?
         */
        if (type.equals(MediaType.AUDIO))
            unknown = new VideoFormat("unknown");
        else if (type.equals(MediaType.VIDEO))
            unknown = new AudioFormat("unknown");
        return MediaFormatImpl.createInstance(unknown);
    }

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code> with default clock rate
     * and set of format parameters. If <code>encoding</code> is known to this
     * <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns
     * <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code> ; otherwise, <code>null</code>
     * @see MediaFormatFactory#createMediaFormat(String)
     */
    public MediaFormat createMediaFormat(String encoding)
    {
        return createMediaFormat(encoding, CLOCK_RATE_NOT_SPECIFIED);
    }

    /**
     * Creates a <code>MediaFormat</code> for the specified RTP payload type with default clock rate
     * and set of format parameters. If <code>rtpPayloadType</code> is known to this
     * <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns
     * <code>null</code>.
     *
     * @param rtpPayloadType the RTP payload type of the <code>MediaFormat</code> to create
     * @return a <code>MediaFormat</code> with the specified <code>rtpPayloadType</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>rtpPayloadType</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     * @see MediaFormatFactory#createMediaFormat(byte)
     */
    public MediaFormat createMediaFormat(byte rtpPayloadType)
    {
        /*
         * We know which are the MediaFormat instances with the specified rtpPayloadType but we
         * cannot directly return them because they do not reflect the user's configuration with
         * respect to being enabled and disabled.
         */
        for (MediaFormat rtpPayloadTypeMediaFormat : MediaUtils.getMediaFormats(rtpPayloadType)) {
            MediaFormat mediaFormat = createMediaFormat(rtpPayloadTypeMediaFormat.getEncoding(),
                    rtpPayloadTypeMediaFormat.getClockRate());
            if (mediaFormat != null)
                return mediaFormat;
        }
        return null;
    }

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code> with the specified
     * <code>clockRate</code> and a default set of format parameters. If <code>encoding</code> is known to
     * this <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns
     * <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code> and <code>clockRate</code>
     * which is either an <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance
     * if <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     * @see MediaFormatFactory#createMediaFormat(String, double)
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate)
    {
        return createMediaFormat(encoding, clockRate, 1);
    }

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code>, <code>clockRate</code> and
     * <code>channels</code> and a default set of format parameters. If <code>encoding</code> is known to
     * this <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns
     * <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * <code>MediaFormat</code> with the specified <code>encoding</code>; otherwise, ignored
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code> and
     * <code>channels</code> and a default set of format parameters which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise,
     * <code>null</code>
     * @see MediaFormatFactory#createMediaFormat(String, double, int)
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate, int channels)
    {
        return createMediaFormat(encoding, clockRate, channels, null);
    }

    private MediaFormat createMediaFormat(String encoding, double clockRate, int channels,
            Map<String, String> fmtps)
    {
        for (MediaFormat format : getSupportedMediaFormats(encoding, clockRate)) {
            /*
             * The mediaType, encoding and clockRate properties are sure to match because format is
             * the result of the search for encoding and clockRate. We just want to make sure that
             * the channels and the format parameters match.
             */
            if (format.matches(format.getMediaType(), format.getEncoding(), format.getClockRate(),
                    channels, fmtps))
                return format;
        }
        return null;
    }

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code>, <code>clockRate</code> and
     * set of format parameters. If <code>encoding</code> is known to this <code>MediaFormatFactory</code>,
     * returns a <code>MediaFormat</code> which is either an <code>AudioMediaFormat</code> or a
     * <code>VideoMediaFormat</code> instance. Otherwise, returns <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code> and
     * set of format parameters which is either an <code>AudioMediaFormat</code> or a
     * <code>VideoMediaFormat</code> instance if <code>encoding</code> is known to this
     * <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     * @see MediaFormatFactory#createMediaFormat(String, double, Map, Map)
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate,
            Map<String, String> formatParams, Map<String, String> advancedParams)
    {
        return createMediaFormat(encoding, clockRate, 1, -1, formatParams, advancedParams);
    }

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code>, <code>clockRate</code>,
     * <code>channels</code> and set of format parameters. If <code>encoding</code> is known to this
     * <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns
     * <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @param frameRate the frame rate in number of frames per second to create a <code>MediaFormat</code> for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * <code>MediaFormat</code> with the specified <code>encoding</code>; otherwise, ignored
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @param advancedParams any parameters which have been received via SIP/SDP or XMPP/Jingle
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code>,
     * <code>channels</code> and set of format parameters which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise,
     * <code>null</code>
     * @see MediaFormatFactory#createMediaFormat(String, double, int, float, Map, Map)
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate, int channels,
            float frameRate, Map<String, String> formatParams, Map<String, String> advancedParams)
    {
        MediaFormat mediaFormat = createMediaFormat(encoding, clockRate, channels, formatParams);
        if (mediaFormat == null)
            return null;

        /*
         * MediaFormatImpl is immutable so if the caller wants to change the format parameters
         * and/or the advanced attributes, we'll have to create a new MediaFormatImpl.
         */
        Map<String, String> formatParameters = null;
        Map<String, String> advancedParameters = null;

        if ((formatParams != null) && !formatParams.isEmpty())
            formatParameters = formatParams;
        if ((advancedParams != null) && !advancedParams.isEmpty())
            advancedParameters = advancedParams;

        if ((formatParameters != null) || (advancedParameters != null)) {
            switch (mediaFormat.getMediaType()) {
                case AUDIO:
                    mediaFormat = new AudioMediaFormatImpl(
                            ((AudioMediaFormatImpl) mediaFormat).getFormat(), formatParameters,
                            advancedParameters);
                    break;
                case VIDEO:
                    VideoMediaFormatImpl videoMediaFormatImpl = (VideoMediaFormatImpl) mediaFormat;

                    /*
                     * If the format of VideoMediaFormatImpl is a ParameterizedVideoFormat, it's
                     * possible for the format parameters of that ParameterizedVideoFormat and of
                     * the new VideoMediaFormatImpl (to be created) to be out of sync. While it's
                     * not technically perfect, it should be practically safe for the format
                     * parameters which distinguish VideoFormats with the same encoding and clock
                     * rate because mediaFormat has already been created in sync with formatParams
                     * (with respect to the format parameters which distinguish VideoFormats with
                     * the same encoding and clock rate).
                     */
                    mediaFormat = new VideoMediaFormatImpl(videoMediaFormatImpl.getFormat(),
                            videoMediaFormatImpl.getClockRate(), frameRate, formatParameters,
                            advancedParameters);
                    break;
                default:
                    mediaFormat = null;
            }
        }
        return mediaFormat;
    }

    /**
     * Creates a <code>MediaFormat</code> either for the specified <code>rtpPayloadType</code> or for the
     * specified <code>encoding</code>, <code>clockRate</code>, <code>channels</code> and set of format
     * parameters. If <code>encoding</code> is known to this <code>MediaFormatFactory</code> , ignores
     * <code>rtpPayloadType</code> and returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. If
     * <code>rtpPayloadType</code> is not {@link MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN} and
     * <code>encoding</code> is <code>null</code>, uses the encoding associated with
     * <code>rtpPayloadType</code>.
     *
     * @param rtpPayloadType the RTP payload type to create a <code>MediaFormat</code> for;
     * {@link MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN} if <code>encoding</code> is not <code>null</code>
     * . If <code>rtpPayloadType</code> is not <code>MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN</code> and
     * <code>encoding</code> is not <code>null</code>, <code>rtpPayloadType</code> is ignored
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for; <code>null</code>
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @param frameRate the frame rate in number of frames per second to create a <code>MediaFormat</code> for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * <code>MediaFormat</code> with the specified <code>encoding</code>; otherwise, ignored
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @param advancedParams any parameters which have been received via SIP/SDP or XMPP/Jingle
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code>,
     * <code>channels</code> and set of format parameters which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(byte rtpPayloadType, String encoding, double clockRate,
            int channels, float frameRate, Map<String, String> formatParams,
            Map<String, String> advancedParams)
    {

        /*
         * If rtpPayloadType is specified, use it only to figure out encoding and/or clockRate in
         * case either one of them is unknown.
         */
        if ((MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN != rtpPayloadType)
                && ((encoding == null) || (CLOCK_RATE_NOT_SPECIFIED == clockRate))) {
            MediaFormat[] rtpPayloadTypeMediaFormats = MediaUtils.getMediaFormats(rtpPayloadType);

            if (rtpPayloadTypeMediaFormats.length > 0) {
                if (encoding == null)
                    encoding = rtpPayloadTypeMediaFormats[0].getEncoding();

                // Assign or check the clock rate.
                if (CLOCK_RATE_NOT_SPECIFIED == clockRate)
                    clockRate = rtpPayloadTypeMediaFormats[0].getClockRate();
                else {
                    boolean clockRateIsValid = false;

                    for (MediaFormat rtpPayloadTypeMediaFormat : rtpPayloadTypeMediaFormats)
                        if (rtpPayloadTypeMediaFormat.getEncoding().equals(encoding)
                                && (rtpPayloadTypeMediaFormat.getClockRate() == clockRate)) {
                            clockRateIsValid = true;
                            break;
                        }
                    if (!clockRateIsValid)
                        return null;
                }
            }
        }
        return createMediaFormat(encoding, clockRate, channels, frameRate, formatParams,
                advancedParams);
    }

    /**
     * Gets the <code>MediaFormat</code>s among the specified <code>mediaFormats</code> which have the
     * specified <code>encoding</code> and, optionally, <code>clockRate</code>.
     *
     * @param mediaFormats the <code>MediaFormat</code>s from which to filter out only the ones which have the
     * specified <code>encoding</code> and, optionally, <code>clockRate</code>
     * @param encoding the well-known encoding (name) of the <code>MediaFormat</code>s to be retrieved
     * @param clockRate the clock rate of the <code>MediaFormat</code>s to be retrieved;
     * {@link #CLOCK_RATE_NOT_SPECIFIED} if any clock rate is acceptable
     * @return a <code>List</code> of the <code>MediaFormat</code>s among <code>mediaFormats</code> which have
     * the specified <code>encoding</code> and, optionally, <code>clockRate</code>
     */
    private List<MediaFormat> getMatchingMediaFormats(MediaFormat[] mediaFormats, String encoding, double clockRate)
    {
        /*
         * XXX Use String#equalsIgnoreCase(String) because some clients transmit some of the codecs
         * starting with capital letters.
         */

        /*
         * As per RFC 3551.4.5.2, because of a mistake in RFC 1890 and for backward compatibility,
         * G.722 should always be announced as 8000 even though it is wideband. So, if someone is
         * looking for G722/16000, then: Forgive them, for they know not what they do!
         */
        if ("G722".equalsIgnoreCase(encoding) && (16000 == clockRate)) {
            clockRate = 8000;
            Timber.i("Suppressing erroneous 16000 announcement for G.722");
        }

        List<MediaFormat> supportedMediaFormats = new ArrayList<>();

        for (MediaFormat mediaFormat : mediaFormats) {
            if (mediaFormat.getEncoding().equalsIgnoreCase(encoding)
                    && ((CLOCK_RATE_NOT_SPECIFIED == clockRate)
                    || (mediaFormat.getClockRate() == clockRate))) {
                supportedMediaFormats.add(mediaFormat);
            }
        }
        return supportedMediaFormats;
    }

    /**
     * Gets the <code>MediaFormat</code>s supported by this <code>MediaFormatFactory</code> and the
     * <code>MediaService</code> associated with it and having the specified <code>encoding</code> and,
     * optionally, <code>clockRate</code>.
     *
     * @param encoding the well-known encoding (name) of the <code>MediaFormat</code>s to be retrieved
     * @param clockRate the clock rate of the <code>MediaFormat</code>s to be retrieved;
     * {@link #CLOCK_RATE_NOT_SPECIFIED} if any clock rate is acceptable
     * @return a <code>List</code> of the <code>MediaFormat</code>s supported by the <code>MediaService</code>
     * associated with this <code>MediaFormatFactory</code> and having the specified encoding
     * and, optionally, clock rate
     */
    private List<MediaFormat> getSupportedMediaFormats(String encoding, double clockRate)
    {
        EncodingConfiguration encodingConfiguration
                = NeomediaServiceUtils.getMediaServiceImpl().getCurrentEncodingConfiguration();
        List<MediaFormat> supportedMediaFormats = getMatchingMediaFormats(
                encodingConfiguration.getAllEncodings(MediaType.AUDIO), encoding, clockRate);

        if (supportedMediaFormats.isEmpty())
            supportedMediaFormats = getMatchingMediaFormats(
                    encodingConfiguration.getAllEncodings(MediaType.VIDEO), encoding, clockRate);
        return supportedMediaFormats;
    }
}
