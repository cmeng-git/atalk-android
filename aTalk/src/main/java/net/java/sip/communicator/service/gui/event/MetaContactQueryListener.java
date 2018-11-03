/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.event;

/**
 * The <tt>MetaContactQueryListener</tt> listens for events coming from a <tt>MetaContactListService</tt> filtering.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface MetaContactQueryListener
{
    /**
     * Indicates that a <tt>MetaContact</tt> has been received for a search in the <tt>MetaContactListService</tt>.
     *
     * @param event the received <tt>MetaContactQueryEvent</tt>
     */
    void metaContactReceived(MetaContactQueryEvent event);

    /**
     * Indicates that a <tt>MetaGroup</tt> has been received from a search in the <tt>MetaContactListService</tt>.
     *
     * @param event the <tt>MetaGroupQueryEvent</tt> that has been received
     */
    void metaGroupReceived(MetaGroupQueryEvent event);

    /**
     * Indicates that a query has changed its status.
     *
     * @param event the <tt>MetaContactQueryStatusEvent</tt> that notified us
     */
    void metaContactQueryStatusChanged(MetaContactQueryStatusEvent event);
}
