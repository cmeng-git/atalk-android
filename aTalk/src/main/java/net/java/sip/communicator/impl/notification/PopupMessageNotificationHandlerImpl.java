/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification;

import net.java.sip.communicator.service.notification.*;
import net.java.sip.communicator.service.systray.PopupMessage;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener;

import org.atalk.util.StringUtils;

import timber.log.Timber;

/**
 * An implementation of the <tt>PopupMessageNotificationHandler</tt> interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class PopupMessageNotificationHandlerImpl implements PopupMessageNotificationHandler
{
    /**
     * {@inheritDoc}
     */
    public String getActionType()
    {
        return NotificationAction.ACTION_POPUP_MESSAGE;
    }

    /**
     * Shows the given <tt>PopupMessage</tt>
     *
     * @param action the action to act upon
     * @param title the title of the given message
     * @param message the message to use if and where appropriate (e.g. with sysTray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     * @param tag additional info to be used by the notification handler
     */
    public void popupMessage(PopupMessageNotificationAction action, String title, String message, byte[] icon, Object tag)
    {
        SystrayService sysTray = NotificationActivator.getSystray();
        if (sysTray == null)
            return;

        if (!StringUtils.isNullOrEmpty(message)) {
            PopupMessage popupMsg = new PopupMessage(title, message, icon, tag);
            popupMsg.setTimeout(action.getTimeout());
            popupMsg.setGroup(action.getGroupName());

            sysTray.showPopupMessage(popupMsg);
        }
        else if (message == null) {
            Timber.e("Message is null!");
        }
        // Allow messages to be empty, since some protocols allow empty lines.
    }

    /**
     * Adds a listener for <tt>SystrayPopupMessageEvent</tt>s posted when user clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    public void addPopupMessageListener(SystrayPopupMessageListener listener)
    {
        SystrayService sysTray = NotificationActivator.getSystray();
        if (sysTray == null)
            return;

        sysTray.addPopupMessageListener(listener);
    }

    /**
     * Removes a listener previously added with <tt>addPopupMessageListener</tt>.
     *
     * @param listener the listener to remove
     */
    public void removePopupMessageListener(SystrayPopupMessageListener listener)
    {
        SystrayService sysTray = NotificationActivator.getSystray();
        if (sysTray == null)
            return;

        sysTray.removePopupMessageListener(listener);
    }
}
