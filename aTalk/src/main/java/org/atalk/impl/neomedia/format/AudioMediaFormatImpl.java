/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.format;

import org.atalk.service.neomedia.format.AudioMediaFormat;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;

import java.util.Map;

import javax.media.Format;
import javax.media.format.AudioFormat;

/**
 * Implements <code>AudioMediaFormat</code> for the JMF <code>AudioFormat</code>.
 *
 * @author Lubomir Marinov
 */
public class AudioMediaFormatImpl extends MediaFormatImpl<AudioFormat>
        implements AudioMediaFormat
{

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance which is to provide an
     * implementation of <code>AudioMediaFormat</code> for a specific JMF <code>AudioFormat</code>.
     *
     * @param format the JMF <code>AudioFormat</code> the new instance is to wrap and provide an implementation
     * of <code>AudioMediaFormat</code> for
     */
    AudioMediaFormatImpl(AudioFormat format)
    {
        this(format, null, null);
    }

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance which is to provide an
     * implementation of <code>AudioMediaFormat</code> for a specific JMF <code>AudioFormat</code> and to
     * have a specific set of format-specific parameters.
     *
     * @param format the JMF <code>AudioFormat</code> the new instance is to wrap and provide an implementation
     * of <code>AudioMediaFormat</code> for
     * @param formatParameters the set of format-specific parameters of the new instance
     * @param advancedParameters the set of format-specific parameters of the new instance
     */
    AudioMediaFormatImpl(
            AudioFormat format,
            Map<String, String> formatParameters,
            Map<String, String> advancedParameters)
    {
        super(fixChannels(format), formatParameters, advancedParameters);
    }

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance with the specified encoding and a
     * single audio channel.
     *
     * @param encoding the encoding of the new <code>AudioMediaFormatImpl</code> instance
     */
    public AudioMediaFormatImpl(String encoding)
    {
        this(new AudioFormat(encoding));
    }

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance with the specified encoding and
     * clock rate and a single audio channel.
     *
     * @param encoding the encoding of the new <code>AudioMediaFormatImpl</code> instance
     * @param clockRate the clock (i.e. sample) rate of the new <code>AudioMediaFormatImpl</code> instance
     */
    AudioMediaFormatImpl(String encoding, double clockRate)
    {
        this(encoding, clockRate, 1);
    }

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance with the specified encoding, clock
     * rate and number of audio channels.
     *
     * @param encoding the encoding of the new <code>AudioMediaFormatImpl</code> instance
     * @param clockRate the clock (i.e. sample) rate of the new <code>AudioMediaFormatImpl</code> instance
     * @param channels the number of available channels (1 for mono, 2 for stereo)
     */
    AudioMediaFormatImpl(String encoding, double clockRate, int channels)
    {
        this(encoding, clockRate, channels, null, null);
    }

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance with the specified encoding, clock
     * rate and format parameters and a single audio channel.
     *
     * @param encoding the encoding of the new <code>AudioMediaFormatImpl</code> instance
     * @param clockRate the clock (i.e. sample) rate of the new <code>AudioMediaFormatImpl</code> instance
     * @param formatParameters any codec-specific parameters that have been received via SIP/SDP or XMPP/Jingle.
     * @param advancedParameters set of advanced parameters that have been received by SIP/SDP or XMPP/Jingle
     */
    AudioMediaFormatImpl(String encoding, double clockRate, Map<String, String> formatParameters,
            Map<String, String> advancedParameters)
    {
        this(encoding, clockRate, 1, formatParameters, advancedParameters);
    }

    /**
     * Initializes a new <code>AudioMediaFormatImpl</code> instance with the specified encoding, clock
     * rate, number of audio channels and format parameters.
     *
     * @param encoding the encoding of the new <code>AudioMediaFormatImpl</code> instance
     * @param clockRate the clock (i.e. sample) rate of the new <code>AudioMediaFormatImpl</code> instance
     * @param channels the number of available channels (1 for mono, 2 for stereo)
     * @param formatParameters any codec-specific parameters that have been received via SIP/SDP or XMPP/Jingle
     * @param advancedParameters any parameters that have been received via SIP/SDP or XMPP/Jingle
     */
    AudioMediaFormatImpl(String encoding, double clockRate, int channels,
            Map<String, String> formatParameters,
            Map<String, String> advancedParameters)
    {
        this(new AudioFormat(
                        encoding,
                        clockRate,
                        AudioFormat.NOT_SPECIFIED,
                        channels),
                formatParameters, advancedParameters);
    }

    /**
     * Gets an <code>AudioFormat</code> instance which matches a specific <code>AudioFormat</code> and
     * has 1 channel if the specified <code>AudioFormat</code> has its number of channels not
     * specified.
     *
     * @param format the <code>AudioFormat</code> to get a match of
     * @return if the specified <code>format</code> has a specific number of channels, <code>format</code>;
     * otherwise, a new <code>AudioFormat</code> instance which matches <code>format</code> and has
     * 1 channel
     */
    private static AudioFormat fixChannels(AudioFormat format)
    {
        if (Format.NOT_SPECIFIED == format.getChannels())
            format = (AudioFormat) format.intersects(new AudioFormat(
                    format.getEncoding(),
                    format.getSampleRate(),
                    format.getSampleSizeInBits(),
                    1)
            );
        return format;
    }

    /**
     * Gets the number of audio channels associated with this <code>AudioMediaFormat</code>.
     *
     * @return the number of audio channels associated with this <code>AudioMediaFormat</code>
     * @see AudioMediaFormat#getChannels()
     */
    public int getChannels()
    {
        int channels = format.getChannels();
        return (Format.NOT_SPECIFIED == channels) ? 1 : channels;
    }

    /**
     * Gets the clock rate associated with this <code>MediaFormat</code>.
     *
     * @return the clock rate associated with this <code>MediaFormat</code>
     * @see MediaFormat#getClockRate()
     */
    public double getClockRate()
    {
        return format.getSampleRate();
    }

    /**
     * Gets the type of this <code>MediaFormat</code> which is {@link MediaType#AUDIO} for
     * <code>AudioMediaFormatImpl</code> instances.
     *
     * @return the <code>MediaType</code> that this format represents and which is
     * <code>MediaType.AUDIO</code> for <code>AudioMediaFormatImpl</code> instances
     * @see MediaFormat#getMediaType()
     */
    public final MediaType getMediaType()
    {
        return MediaType.AUDIO;
    }
}
