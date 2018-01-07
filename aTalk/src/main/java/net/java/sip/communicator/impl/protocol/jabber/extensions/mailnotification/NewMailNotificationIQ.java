/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification;

import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.SimpleIQ;

/**
 * A straightforward IQ extension. A <tt>NewMailNotification</tt> object is created via the
 * <tt>NewMailNotificationProvider</tt>. It contains the information we need in order to determine
 * whether there are new mails waiting for us on the mail server.
 *
 * @author Matthieu Helleringer
 * @author Alain Knaebel
 * @author Emil Ivov
 */
public class NewMailNotificationIQ extends SimpleIQ
{
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(NewMailNotificationIQ.class);

	/**
	 * The name space for new mail notification packets.
	 */
	public static final String NAMESPACE = "google:mail:notify";

	/**
	 * The name of the element that Google use to transport new mail notifications.
	 */
	public static final String ELEMENT_NAME = "new-mail";

	protected NewMailNotificationIQ()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}

	/**
	 * Returns the sub-element XML section of the IQ packet.
	 * 
	 * @return
	 *
	 * @return the child element section of the IQ XML
	 */
	public IQ NewMailNotificationIQ(IQ iq)
	{
		// String return "<iq type='" + "result" + "' " + "from='" + getFrom() + "' " + "to='" +
		// getTo() + "' " + "id='" + getStanzaId() + "' />";
		return IQ.createResultIQ(iq);
	}
}
