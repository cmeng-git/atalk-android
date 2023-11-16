/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleContent;

/**
 * Represents functionality which allows a <code>TransportManagerJabberImpl</code> implementation to
 * send <code>transport-info</code> {@link Jingle}s for the purposes of expediting candidate negotiation.
 *
 * @author Lyubomir Marinov
 */
public interface TransportInfoSender
{
    /**
     * Sends specific {@link JingleContent}s in a <code>transport-info</code> {@link Jingle}
     * from the local peer to the remote peer.
     *
     * @param contents the <code>JingleContent</code>s to be sent in a <code>transport-info</code>
     * <code>Jingle</code> from the local peer to the remote peer
     */
    void sendTransportInfo(Iterable<JingleContent> contents);
}
