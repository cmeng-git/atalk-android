/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification;

import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent;
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent;

import java.util.EventListener;

/**
 * The <tt>NotificationChangeListener</tt> is notified any time an action type or an event type is added,
 * removed or changed.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface NotificationChangeListener extends EventListener
{
    /**
     * This method gets called when a new notification action has been defined for a particular event type.
     *
     * @param event the <tt>NotificationActionTypeEvent</tt>, which is dispatched when a new action has been added.
     */
    void actionAdded(NotificationActionTypeEvent event);

    /**
     * This method gets called when a notification action for a particular event type has been removed.
     *
     * @param event the <tt>NotificationActionTypeEvent</tt>, which is dispatched when an action has been removed.
     */
    void actionRemoved(NotificationActionTypeEvent event);

    /**
     * This method gets called when a notification action for a particular event type has been changed
     * (for example the corresponding descriptor has changed).
     *
     * @param event the <tt>NotificationActionTypeEvent</tt>, which is dispatched when an action has been changed.
     */
    void actionChanged(NotificationActionTypeEvent event);

    /**
     * This method gets called when a new event type has been added.
     *
     * @param event the <tt>NotificationEventTypeEvent</tt>, which is dispatched when a new event type has been added
     */
    void eventTypeAdded(NotificationEventTypeEvent event);

    /**
     * This method gets called when an event type has been removed.
     *
     * @param event the <tt>NotificationEventTypeEvent</tt>, which is dispatched when an event type has been removed.
     */
    void eventTypeRemoved(NotificationEventTypeEvent event);
}
