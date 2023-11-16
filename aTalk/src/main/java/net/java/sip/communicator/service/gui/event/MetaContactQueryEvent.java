/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

import net.java.sip.communicator.service.contactlist.MetaContact;

import java.util.EventObject;

/**
 * The <code>MetaContactQueryEvent</code> is triggered each time a
 * <code>MetaContact</code> is received as a result of a <code>MetaContactQuery</code>.
 *
 * @author Yana Stamcheva
 */
public class MetaContactQueryEvent extends EventObject
{
    /**
     * The <code>MetaContact</code> this event is about.
     */
    private final MetaContact metaContact;

    /**
     * Creates an instance of <code>MetaGroupQueryEvent</code> by specifying the
     * <code>source</code> query this event comes from and the <code>metaContact</code> this event is about.
     *
     * @param source the <code>MetaContactQuery</code> that triggered this event
     * @param metaContact the <code>MetaContact</code> this event is about
     */
    public MetaContactQueryEvent(MetaContactQuery source, MetaContact metaContact)
    {
        super(source);
        this.metaContact = metaContact;
    }

    /**
     * Returns the <code>MetaContactQuery</code> that triggered this event.
     * @return the <code>MetaContactQuery</code> that triggered this event
     */
    public MetaContactQuery getQuerySource()
    {
        return (MetaContactQuery) source;
    }

    /**
     * Returns the <code>MetaContact</code> this event is about.
     * @return the <code>MetaContact</code> this event is about
     */
    public MetaContact getMetaContact()
    {
        return metaContact;
    }
}
