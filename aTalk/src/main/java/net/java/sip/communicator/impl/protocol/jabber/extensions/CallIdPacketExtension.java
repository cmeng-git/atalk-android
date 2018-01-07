/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions;

/**
 * A <tt>ExtensionElement</tt> that represents a "callid" element within the
 * <tt>ConferenceDescriptionPacketExtension.NAMESPACE</tt> namespace.
 *
 * @author Boris Grozev
 */
public class CallIdPacketExtension extends AbstractPacketExtension
{
	/**
	 * Creates a new instance setting the text to <tt>callid</tt>.
	 *
	 * @param callid
	 */
	public CallIdPacketExtension(String callid)
	{
		this();

		setText(callid);
	}

	/**
	 * Creates a new instance.
	 */
	public CallIdPacketExtension()
	{
		super(ConferenceDescriptionPacketExtension.CALLID_ELEM_NAME,
			ConferenceDescriptionPacketExtension.NAMESPACE);
	}
}
