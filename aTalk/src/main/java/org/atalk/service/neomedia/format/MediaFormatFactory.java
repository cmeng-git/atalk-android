/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format;

import org.atalk.util.MediaType;

import java.util.Map;

/**
 * Allows the creation of audio and video <code>MediaFormat</code> instances.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 */
public interface MediaFormatFactory
{

    /**
     * The constant to be used as an argument representing number of channels to denote that a
     * specific number of channels is not specified.
     */
    public static final int CHANNELS_NOT_SPECIFIED = -1;

    /**
     * The constant to be used as an argument representing a clock rate to denote that a specific
     * clock rate is not specified.
     */
    public static final double CLOCK_RATE_NOT_SPECIFIED = -1;

    /**
     * Creates an unknown <code>MediaFormat</code>.
     *
     * @param type <code>MediaType</code>
     * @return unknown <code>MediaFormat</code>
     */
    public MediaFormat createUnknownMediaFormat(MediaType type);

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code> with default clock rate
     * and set of format parameters. If <code>encoding</code> is known to this
     * <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code> ; otherwise,
     * <code>null</code>
     */
    public MediaFormat createMediaFormat(String encoding);

    /**
     * Creates a <code>MediaFormat</code> for the specified RTP payload type with default clock rate and
     * set of format parameters. If <code>rtpPayloadType</code> is known to this
     * <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns <code>null</code>.
     *
     * @param rtpPayloadType the RTP payload type of the <code>MediaFormat</code> to create
     * @return a <code>MediaFormat</code> with the specified <code>rtpPayloadType</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>rtpPayloadType</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(byte rtpPayloadType);

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code> with the specified
     * <code>clockRate</code> and a default set of format parameters. If <code>encoding</code> is known to
     * this <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code> and <code>clockRate</code>
     * which is either an <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance
     * if <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate);

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code>, <code>clockRate</code> and
     * <code>channels</code> and a default set of format parameters. If <code>encoding</code> is known to
     * this <code>MediaFormatFactory</code>, returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. Otherwise, returns <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @param channels the number of available channels (1 for mono, 2 for stereo) if it makes sense for the
     * <code>MediaFormat</code> with the specified <code>encoding</code>; otherwise, ignored
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code> and
     * <code>channels</code> and a default set of format parameters which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate, int channels);

    /**
     * Creates a <code>MediaFormat</code> for the specified <code>encoding</code>, <code>clockRate</code> and
     * set of format parameters. If <code>encoding</code> is known to this <code>MediaFormatFactory</code>,
     * returns a <code>MediaFormat</code> which is either an <code>AudioMediaFormat</code> or a
     * <code>VideoMediaFormat</code> instance. Otherwise, returns <code>null</code>.
     *
     * @param encoding the well-known encoding (name) to create a <code>MediaFormat</code> for
     * @param clockRate the clock rate in Hz to create a <code>MediaFormat</code> for
     * @param formatParams any codec specific parameters which have been received via SIP/SDP or XMPP/Jingle
     * @param advancedAttrs advanced attributes received via SIP/SDP or XMPP/Jingle
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code> and
     * set of format parameters which is either an <code>AudioMediaFormat</code> or a
     * <code>VideoMediaFormat</code> instance if <code>encoding</code> is known to this
     * <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate,
            Map<String, String> formatParams, Map<String, String> advancedAttrs);

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
     * @param advancedAttrs advanced attributes received via SIP/SDP or XMPP/Jingle
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code>,
     * <code>channels</code> and set of format parameters which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(String encoding, double clockRate, int channels,
            float frameRate, Map<String, String> formatParams, Map<String, String> advancedAttrs);

    /**
     * Creates a <code>MediaFormat</code> either for the specified <code>rtpPayloadType</code> or for the
     * specified <code>encoding</code>, <code>clockRate</code>, <code>channels</code> and set of format
     * parameters. If <code>encoding</code> is known to this <code>MediaFormatFactory</code>, ignores
     * <code>rtpPayloadType</code> and returns a <code>MediaFormat</code> which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance. If <code>rtpPayloadType</code>
     * is not {@link MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN} and <code>encoding</code> is <code>null</code>,
     * uses the encoding associated with <code>rtpPayloadType</code>.
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
     * @param advancedAttrs advanced attributes received via SIP/SDP or XMPP/Jingle
     * @return a <code>MediaFormat</code> with the specified <code>encoding</code>, <code>clockRate</code>,
     * <code>channels</code> and set of format parameters which is either an
     * <code>AudioMediaFormat</code> or a <code>VideoMediaFormat</code> instance if
     * <code>encoding</code> is known to this <code>MediaFormatFactory</code>; otherwise, <code>null</code>
     */
    public MediaFormat createMediaFormat(byte rtpPayloadType, String encoding, double clockRate,
            int channels, float frameRate, Map<String, String> formatParams,
            Map<String, String> advancedAttrs);
}
