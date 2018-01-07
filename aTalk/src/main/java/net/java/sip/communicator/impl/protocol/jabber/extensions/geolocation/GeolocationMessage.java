/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.geolocation;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.geoloc.packet.GeoLocation;
import org.jxmpp.jid.Jid;

/**
 * This class extends the smack Message class and allows creating a GeolocationMessage automatically
 * setting the geolocation packet extension.
 *
 * @author Guillaume Schreiner
 * @author Eng Chong Meng
 */
public class GeolocationMessage // extends Stanza
{
	/**
	 * Creates a new, "normal" message.
	 *
	 * @param geoloc
	 *        the geolocation packet extension to add to this message.
	 */

	public GeolocationMessage(GeoLocation geoloc)
	{
		Message msg = new Message();
		msg.addExtension(geoloc);
	}

	/**
	 * Creates a new "normal" message to the specified recipient and adds the specified
	 * <tt>geoloc</tt> extension to it.
	 *
	 * @param to
	 *        the recipient of the message.
	 * @param geoloc
	 *        the geolocation packet extension to add to this message.
	 */
	public GeolocationMessage(Jid to, GeoLocation geoloc)
	{
		Message msg = new Message(to);
		msg.addExtension(geoloc);
	}

	/**
	 * Creates a new message with the specified type and recipient and adds the specified
	 * <tt>geoloc</tt> extension to it.
	 *
	 * @param to
	 *        the recipient of the message.
	 * @param geoloc
	 *        the geolocation packet extension to add to this message.
	 * @param type
	 *        the message type.
	 */
	public GeolocationMessage(Jid to, Message.Type type, GeoLocation geoloc)
	{
		Message msg = new Message(to, type);
		msg.addExtension(geoloc);
	}
}
