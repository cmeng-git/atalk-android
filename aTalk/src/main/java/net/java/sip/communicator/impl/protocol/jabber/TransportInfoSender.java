/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.xmpp.extensions.jingle.element.JingleContent;
import org.xmpp.extensions.jingle.element.Jingle;

/**
 * Represents functionality which allows a <tt>TransportManagerJabberImpl</tt> implementation to
 * send <tt>transport-info</tt> {@link Jingle}s for the purposes of expediting candidate negotiation.
 *
 * @author Lyubomir Marinov
 */
public interface TransportInfoSender
{
    /**
     * Sends specific {@link JingleContent}s in a <tt>transport-info</tt> {@link Jingle}
     * from the local peer to the remote peer.
     *
     * @param contents the <tt>JingleContent</tt>s to be sent in a <tt>transport-info</tt>
     * <tt>Jingle</tt> from the local peer to the remote peer
     */
    void sendTransportInfo(Iterable<JingleContent> contents);
}
