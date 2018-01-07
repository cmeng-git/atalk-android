/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * Specifies the names of some of the most popular instant messaging protocols. These names should
 * be used when registering an implementation of a protocol with the osgi framework. These names
 * must be set in the properties dictionary that one specifies when registering an OSGI service.
 * When setting one of these names, a protocol implementor must map it against the
 * ProtocolProviderFactory.PROTOCOL_PROPERTY_NAME key.
 * 
 * @author Emil Ivov
 */
public interface ProtocolNames
{
	/**
	 * The SIP (and SIMPLE) protocols.
	 */
	public static final String SIP = "SIP";

	/**
	 * The Jabber protocol.
	 */
	public static final String JABBER = "Jabber";

	/**
	 * The IRC protocol.
	 */
	public static final String IRC = "IRC";

	/**
	 * The ICQ service protocol.
	 */
	public static final String ICQ = "ICQ";

	/**
	 * The AIM service protocol.
	 */
	public static final String AIM = "AIM";

	/**
	 * The SIP Communicator MOCK protocol.
	 */
	public static final String SIP_COMMUNICATOR_MOCK = "sip-communicator-mock";

	/**
	 * The SSH protocol.
	 */
	public static final String SSH = "SSH";

	/**
	 * The Gibberish protocol.
	 */
	public static final String GIBBERISH = "Gibberish";
}
