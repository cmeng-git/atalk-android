/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.caps;

import org.jxmpp.jid.Jid;

/**
 * Represents a listener of events notifying about changes in the list of user caps nodes of
 * <tt>EntityCapsManager</tt>.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public interface UserCapsNodeListener
{
    /**
     * Notifies this listener that an <tt>EntityCapsManager</tt>'s caps node record for a specific
     * user may have changed. The listener will take the appropriate actions that deems necessary
     *
     * @param userJid the contact FullJid
     * @param online indicates if the user for which we're notified is online
     */
    void userCapsNodeNotify(Jid userJid, boolean online);
}
