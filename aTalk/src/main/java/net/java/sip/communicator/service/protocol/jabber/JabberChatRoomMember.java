/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;

import org.jxmpp.jid.Jid;

/**
 * {@link ChatRoomMember} interface extension that provides additional methods specific to Jabber protocol.
 *
 * @author Pawel Domas
 */
public interface JabberChatRoomMember extends ChatRoomMember
{
	/**
	 * Returns the Jabber ID of the member; can either be BareJid or reserved nick.
	 * 
	 * @return the Jabber ID or <tt>null</tt> if we don't have enough permissions to look up user's JID.
	 */
	public Jid getJabberID();
}
