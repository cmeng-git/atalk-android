/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.net.URI;

/**
 * RTP extensions are defined by RFC 5285 and they allow attaching additional information to some or
 * all RTP packets of an RTP stream. This class describes RTP extensions in a way that makes them
 * convenient for use in SDP generation/parsing.
 *
 * @author Emil Ivov
 */
public class RTPExtension
{
    /**
     * The URN identifying the RTP extension that allows mixers to send to conference participants
     * the audio levels of all contributing sources. Defined in RFC6465.
     */
    public static final String CSRC_AUDIO_LEVEL_URN
            = "urn:ietf:params:rtp-hdrext:csrc-audio-level";

    /**
     * The URN identifying the RTP extension that allows clients to send to conference mixers the
     * audio level of their packet payload. Defined in RFC6464.
     */
    public static final String SSRC_AUDIO_LEVEL_URN
            = "urn:ietf:params:rtp-hdrext:ssrc-audio-level";

    /**
     * The URN identifying the abs-send-time RTP extension. Defined at
     * {@link "https://www.webrtc.org/experiments/rtp-hdrext/abs-send-time"}
     */
    public static final String ABS_SEND_TIME_URN
            = "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time";

    /**
     * The URN which identifies the framemarking RTP extension defined at
     * {@link "https://tools.ietf.org/html/draft-ietf-avtext-framemarking-03"}
     */
    public static final String FRAME_MARKING_URN
            = "http://tools.ietf.org/html/draft-ietf-avtext-framemarking-07";

    /**
     * The URN which identifies the Original Header Block RTP extension defined
     * in {@link "https://tools.ietf.org/html/draft-ietf-perc-double-02"}.
     */
    public static final String ORIGINAL_HEADER_BLOCK_URN
            = "urn:ietf:params:rtp-hdrext:ohb";

    /**
     * The URN which identifies the Transport-Wide Congestion Control RTP
     * extension.
     */
    public static final String TRANSPORT_CC_URN
            = "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01";

    /**
     * The URN which identifies the rtp-stream-id extensions
     * in {@link "https://tools.ietf.org/html/draft-ietf-mmusic-rid-10"}.
     */
    public static final String RTP_STREAM_ID_URN
            = "urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id";

    /**
     * The URN which identifies the transmission time-offset extensions
     * in {@link "https://tools.ietf.org/html/rfc5450"}.
     */
    public static final String TOF_URN = "urn:ietf:params:rtp-hdrext:toffset";

    /**
     * The URN which identifies the RTP Header Extension for Video Content Type.
     */
    public static final String VIDEO_CONTENT_TYPE_URN
            = "http://www.webrtc.org/experiments/rtp-hdrext/video-content-type";

    /**
     * The direction that this extension will be transmitted in.
     */
    private MediaDirection direction;

    /**
     * The <code>URI</code> identifier of this extension.
     */
    private final URI extensionURI;

    /**
     * Extension specific attributes.
     */
    private String extensionAttributes;

    /**
     * Creates an <code>RTPExtension</code> instance for the specified <code>extensionURI</code> using a
     * default <code>SENDRECV</code> direction and no extension attributes.
     *
     * @param extensionURI the <code>URI</code> (possibly a URN) of the RTP extension that we'd like to create.
     */
    public RTPExtension(URI extensionURI)
    {
        this(extensionURI, MediaDirection.SENDRECV);
    }

    /**
     * Creates an <code>RTPExtension</code> instance for the specified <code>extensionURI</code> and <code>direction</code>.
     *
     * @param extensionURI the <code>URI</code> (possibly a URN) of the RTP extension that we'd like to create.
     * @param direction a <code>MediaDirection</code> instance indication how this extension will be transmitted.
     */
    public RTPExtension(URI extensionURI, MediaDirection direction)
    {
        this(extensionURI, direction, null);
    }

    /**
     * Creates an <code>RTPExtension</code> instance for the specified <code>extensionURI</code> using a
     * default <code>SENDRECV</code> direction and <code>extensionAttributes</code>.
     *
     * @param extensionURI the <code>URI</code> (possibly a URN) of the RTP extension that we'd like to create.
     * @param extensionAttributes any attributes that we'd like to add to this extension.
     */
    public RTPExtension(URI extensionURI, String extensionAttributes)
    {
        this(extensionURI, MediaDirection.SENDRECV, extensionAttributes);
    }

    /**
     * Creates an <code>RTPExtension</code> instance for the specified <code>extensionURI</code> and
     * <code>direction</code> and sets the specified <code>extensionAttributes</code>.
     *
     * @param extensionURI the <code>URI</code> (possibly a URN) of the RTP extension that we'd like to create.
     * @param direction a <code>MediaDirection</code> instance indication how this extension will be transmitted.
     * @param extensionAttributes any attributes that we'd like to add to this extension.
     */
    public RTPExtension(URI extensionURI, MediaDirection direction, String extensionAttributes)
    {
        this.extensionURI = extensionURI;
        this.direction = direction;
        this.extensionAttributes = extensionAttributes;
    }

    /**
     * Returns the direction that the corresponding <code>MediaDevice</code> supports for this
     * extension. By default RTP extension headers inherit the direction of a stream. When
     * explicitly specified <code>SENDONLY</code> direction indicates an ability to attach the extension
     * in outgoing RTP packets; a <code>RECVONLY</code> direction indicates a desire to receive the
     * extension in incoming packets; a <code>SENDRECV</code> direction indicates both. An
     * <code>INACTIVE</code> direction indicates neither, but later re-negotiation may make an extension
     * active.
     *
     * @return the direction that the corresponding <code>MediaDevice</code> supports for this
     * extension.
     */
    public MediaDirection getDirection()
    {
        return direction;
    }

    /**
     * Returns the <code>URI</code> that identifies the format and meaning of this extension.
     *
     * @return the <code>URI</code> (possibly a URN) that identifies the format and meaning of this
     * extension.
     */
    public URI getURI()
    {
        return extensionURI;
    }

    /**
     * Returns the extension attributes associated with this <code>RTPExtension</code> or <code>null</code>
     * if this extension does not have any.
     *
     * @return A <code>String</code> containing the extension attributes associated with this
     * <code>RTPExtension</code> or <code>null</code> if this extension does not have any.
     */
    public String getExtensionAttributes()
    {
        return extensionAttributes;
    }

    /**
     * Returns a <code>String</code> representation of this <code>RTPExtension</code>'s <code>URI</code>.
     *
     * @return a <code>String</code> representation of this <code>RTPExtension</code>'s <code>URI</code>.
     */
    @Override
    public String toString()
    {
        return extensionURI.toString() + ";" + getDirection();
    }

    /**
     * Returns <code>true</code> if and only if <code>o</code> is an instance of <code>RTPExtension</code> and
     * <code>o</code>'s <code>URI</code> is equal to this extension's <code>URI</code>. The method returns
     * <code>false</code> otherwise.
     *
     * @param o the <code>Object</code> that we'd like to compare to this <code>RTPExtension</code>.
     * @return <code>true</code> when <code>o</code>'s <code>URI</code> is equal to this extension's
     * <code>URI</code> and <code>false</code> otherwise.
     */
    @Override
    public boolean equals(Object o)
    {
        return (o instanceof RTPExtension) && ((RTPExtension) o).getURI().equals(getURI());
    }

    /**
     * Returns the hash code of this extension instance which is actually the hash code of the
     * <code>URI</code> that this extension is encapsulating.
     *
     * @return the hash code of this extension instance which is actually the hash code of the
     * <code>URI</code> that this extension is encapsulating.
     */
    @Override
    public int hashCode()
    {
        return getURI().hashCode();
    }
}
