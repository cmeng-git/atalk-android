/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

import net.java.sip.communicator.service.contactlist.MetaContactGroup;

import java.util.EventObject;

/**
 * The <code>MetaGroupQueryEvent</code> is triggered each time a
 * <code>MetaContactGroup</code> is received as a result of a <code>MetaContactQuery</code>.
 *
 * @author Yana Stamcheva
 */
public class MetaGroupQueryEvent extends EventObject
{
    /**
     * The <code>MetaContactGroup</code> this event is about.
     */
    private final MetaContactGroup metaGroup;

    /**
     * Creates an instance of <code>MetaGroupQueryEvent</code> by specifying the
     * <code>source</code> query this event comes from and the <code>metaGroup</code> this event is about.
     *
     * @param source the <code>MetaContactQuery</code> that triggered this event
     * @param metaGroup the <code>MetaContactGroup</code> this event is about
     */
    public MetaGroupQueryEvent(MetaContactQuery source, MetaContactGroup metaGroup)
    {
        super(source);
        this.metaGroup = metaGroup;
    }

    /**
     * Returns the <code>MetaContactQuery</code> that triggered this event.
     *
     * @return the <code>MetaContactQuery</code> that triggered this event
     */
    public MetaContactQuery getQuerySource()
    {
        return (MetaContactQuery) source;
    }

    /**
     * Returns the <code>MetaContactGroup</code> this event is about.
     *
     * @return the <code>MetaContactGroup</code> this event is about
     */
    public MetaContactGroup getMetaGroup()
    {
        return metaGroup;
    }
}
