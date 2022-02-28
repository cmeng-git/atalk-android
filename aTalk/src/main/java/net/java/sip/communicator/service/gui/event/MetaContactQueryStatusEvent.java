/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

import java.util.EventObject;

/**
 * The <code>MetaContactQueryStatusEvent</code> is triggered each time a
 * <code>MetaContactQuery</code> changes its status. Possible statuses are:
 * QUERY_COMPLETED, QUERY_CANCELED and QUERY_ERROR.
 *
 * @author Yana Stamcheva
 */
public class MetaContactQueryStatusEvent extends EventObject
{
    /**
     * Indicates that a query has been completed.
     */
    public static final int QUERY_COMPLETED = 0;

    /**
     * Indicates that a query has been canceled.
     */
    public static final int QUERY_CANCELED = 1;

    /**
     * Indicates that a query has been stopped because of an error.
     */
    public static final int QUERY_ERROR = 2;

    /**
     * Indicates the type of this event.
     */
    private final int eventType;

    /**
     * Creates a <code>MetaContactQueryStatusEvent</code> by specifying the source
     * <code>MetaContactQuery</code> and the <code>eventType</code> indicating why initially this event occurred.
     *
     * @param source the initiator of the event
     * @param eventType the type of the event. One of the QUERY_XXX constants defined in this class
     */
    public MetaContactQueryStatusEvent(MetaContactQuery source, int eventType)
    {
        super(source);
        this.eventType = eventType;
    }

    /**
     * Returns the <code>ContactQuery</code> that triggered this event.
     *
     * @return the <code>ContactQuery</code> that triggered this event
     */
    public MetaContactQuery getQuerySource()
    {
        return (MetaContactQuery) source;
    }

    /**
     * Returns the type of this event.
     *
     * @return the type of this event
     */
    public int getEventType()
    {
        return eventType;
    }
}
