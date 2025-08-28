/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification;

import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.PopupMessageNotificationAction;
import net.java.sip.communicator.service.notification.PopupMessageNotificationHandler;
import net.java.sip.communicator.service.systray.PopupMessage;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener;

import org.apache.commons.lang3.StringUtils;

import timber.log.Timber;

/**
 * An implementation of the <code>PopupMessageNotificationHandler</code> interface.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class PopupMessageNotificationHandlerImpl implements PopupMessageNotificationHandler {
    /**
     * {@inheritDoc}
     */
    public String getActionType() {
        return NotificationAction.ACTION_POPUP_MESSAGE;
    }

    /**
     * Shows the given <code>PopupMessage</code>
     *
     * @param action the action to act upon
     * @param data <code>NotificationData</code> that contains the name/key, icon and extra info for popup message
     */
    public void popupMessage(PopupMessageNotificationAction action, NotificationData data) {
        SystrayService sysTray = NotificationActivator.getSystray();
        if (sysTray == null)
            return;

        String message = data.getMessage();
        if (StringUtils.isNotEmpty(message)) {
            PopupMessage popupMsg = new PopupMessage(data.getTitle(), message, data.getIcon(),
                    data.getExtra(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA));
            popupMsg.setEventType(data.getEventType());
            popupMsg.setMessageType(data.getMessageType());
            popupMsg.setTimeout(action.getTimeout());
            popupMsg.setGroup(action.getGroupName());

            sysTray.showPopupMessage(popupMsg);
        }
        // Allow message to be empty? since some protocols allow empty lines.
        else if (message == null) {
            Timber.e("Message is null!");
        }
    }

    /**
     * Adds a listener for <code>SystrayPopupMessageEvent</code>s posted when user clicks on the system tray popup message.
     *
     * @param listener the listener to add
     */
    public void addPopupMessageListener(SystrayPopupMessageListener listener) {
        SystrayService sysTray = NotificationActivator.getSystray();
        if (sysTray == null)
            return;

        sysTray.addPopupMessageListener(listener);
    }

    /**
     * Removes a listener previously added with <code>addPopupMessageListener</code>.
     *
     * @param listener the listener to remove
     */
    public void removePopupMessageListener(SystrayPopupMessageListener listener) {
        SystrayService sysTray = NotificationActivator.getSystray();
        if (sysTray == null)
            return;

        sysTray.removePopupMessageListener(listener);
    }
}
