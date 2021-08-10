/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification;

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener;

/**
 * The <tt>PopupMessageNotificationHandler</tt> interface is meant to be implemented by the notification
 * bundle in order to provide handling of popup message actions.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface PopupMessageNotificationHandler extends NotificationHandler
{
    /**
     * Shows the given <tt>PopupMessage</tt>
     *
     * @param action the action to act upon
     * @param data <tt>NotificationData</tt> that contains the name/key, icon and extra info for popup notification
     */
    void popupMessage(PopupMessageNotificationAction action, NotificationData data);

    /**
     * Adds a listener for <tt>SystrayPopupMessageEvent</tt>s posted when user clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    void addPopupMessageListener(SystrayPopupMessageListener listener);

    /**
     * Removes a listener previously added with <tt>addPopupMessageListener</tt>.
     *
     * @param listener the listener to remove
     */
    void removePopupMessageListener(SystrayPopupMessageListener listener);
}
