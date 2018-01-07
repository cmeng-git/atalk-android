/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * The <tt>SrtpControlType</tt> enumeration contains all currently known <tt>SrtpControl</tt>
 * implementations.
 *
 * @author Ingo Bauersachs
 * @author Lyubomir Marinov
 */
public enum SrtpControlType {
	/**
	 * Datagram Transport Layer Security (DTLS) Extension to Establish Keys for the Secure Real-time
	 * Transport Protocol (SRTP). The key points of DTLS-SRTP are that:
	 * 
	 * o application data is protected using SRTP,
	 * 
	 * o the DTLS handshake is used to establish keying material, algorithms, and parameters for SRTP,
	 * 
	 * o a DTLS extension is used to negotiate SRTP algorithms, and
	 * 
	 * o other DTLS record-layer content types are protected using the ordinary DTLS record format.
	 */
	DTLS_SRTP("DTLS_SRTP"),

	/**
	 * Multimedia Internet KEYing (RFC 3830)
	 */
	MIKEY("MIKEY"),

	/**
	 * Session Description Protocol (SDP) Security Descriptions for Media Streams (RFC 4568)
	 */
	SDES("SDES"),

	/**
	 * ZRTP: Media Path Key Agreement for Unicast Secure RTP (RFC 6189)
	 */
    ZRTP("ZRTP"),

    /**
     * A no-op implementation.
     */
    NULL("NULL");

	/**
	 * The human-readable non-localized name of the (S)RTP transport protocol represented by this
	 * <tt>SrtpControlType</tt> and its respective <tt>SrtpControl</tt> class.
	 */
	private final String protoName;

	/**
	 * Initializes a new <tt>SrtpControlType</tt> instance with a specific human-readable
	 * non-localized (S)RTP transport protocol name.
	 *
	 * @param protoName
	 *        the human-readable non-localized name of the (S)RTP transport protocol represented by
	 *        the new instance and its respective <tt>SrtpControl</tt> class
	 */
	private SrtpControlType(String protoName)
	{
		this.protoName = protoName;
	}

	@Override
	public String toString()
	{
		return protoName;
	}

	/**
	 * @see SrtpControlType#valueOf(String)
	 */
	public static SrtpControlType fromString(String protoName)
	{
		if (protoName.equals(SrtpControlType.DTLS_SRTP.toString())) {
			return SrtpControlType.DTLS_SRTP;
		}
		else {
			return SrtpControlType.valueOf(protoName);
		}
	}
}
