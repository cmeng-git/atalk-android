/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

/**
 * The <code>MetaContactQueryListener</code> listens for events coming from a <code>MetaContactListService</code> filtering.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface MetaContactQueryListener
{
    /**
     * Indicates that a <code>MetaContact</code> has been received for a search in the <code>MetaContactListService</code>.
     *
     * @param event the received <code>MetaContactQueryEvent</code>
     */
    void metaContactReceived(MetaContactQueryEvent event);

    /**
     * Indicates that a <code>MetaGroup</code> has been received from a search in the <code>MetaContactListService</code>.
     *
     * @param event the <code>MetaGroupQueryEvent</code> that has been received
     */
    void metaGroupReceived(MetaGroupQueryEvent event);

    /**
     * Indicates that a query has changed its status.
     *
     * @param event the <code>MetaContactQueryStatusEvent</code> that notified us
     */
    void metaContactQueryStatusChanged(MetaContactQueryStatusEvent event);
}
