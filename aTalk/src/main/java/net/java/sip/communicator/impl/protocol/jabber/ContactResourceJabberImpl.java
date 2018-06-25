/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;

import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;

/**
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ContactResourceJabberImpl extends ContactResource
{
    private final FullJid fullJid;

    /**
     * Creates a <tt>ContactResource</tt> by specifying the <tt>resourceName</tt>, the
     * <tt>presenceStatus</tt> and the <tt>priority</tt>.
     *
     * @param fullJid the full jid corresponding to this contact resource
     * @param contact
     * @param presenceStatus
     * @param priority
     */
    public ContactResourceJabberImpl(FullJid fullJid, Contact contact,
            PresenceStatus presenceStatus, int priority, boolean isMobile)
    {
        super(contact, fullJid.getResourceOrEmpty().toString(), presenceStatus, priority, isMobile);
        this.fullJid = fullJid;
    }

    /**
     * Returns the full jid corresponding to this contact resource.
     *
     * @return the full jid corresponding to this contact resource
     */
    public FullJid getFullJid()
    {
        return fullJid;
    }

    /**
     * Sets the new <tt>PresenceStatus</tt> of this resource.
     *
     * @param newStatus the new <tt>PresenceStatus</tt> to set
     */
    protected void setPresenceStatus(PresenceStatus newStatus)
    {
        this.presenceStatus = newStatus;
    }

    /**
     * Changed whether contact is mobile one. Logged in only from mobile device.
     *
     * @param isMobile whether contact is mobile one.
     */
    public void setMobile(boolean isMobile)
    {
        this.mobile = isMobile;
    }

    /**
     * Changes resource priority.
     *
     * @param priority the new priority
     */
    public void setPriority(int priority)
    {
        this.priority = priority;
    }
}
