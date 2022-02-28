/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.chatstates.ChatState;

/**
 * The operation set allows user bundles (e.g. the user interface) to send and receive chatState
 * notifications to and from other <code>Contact</code>s.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface OperationSetChatStateNotifications extends OperationSet
{
    /**
     * Adds <code>l</code> to the list of listeners registered for receiving <code>ChatStateNotificationEvent</code>s
     *
     * @param l the <code>ChatStateNotificationsListener</code> listener that we'd like to add
     */
    void addChatStateNotificationsListener(ChatStateNotificationsListener l);

    /**
     * Removes <code>l</code> from the list of listeners registered for receiving <code>ChatStateNotificationEvent</code>s
     *
     * @param l the <code>ChatStateNotificationsListener</code> listener that we'd like to remove
     */
    void removeChatStateNotificationsListener(ChatStateNotificationsListener l);

    /**
     * Sends a notification to <code>notifiedContact</code> that we have entered <code>chatState</code>.
     *
     * @param chatDescriptor the chatDescriptor to notify
     * @param chatState the chat state as defined in ChatState that we have entered.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <code>notifiedContact</code> is not an instance belonging
     * to the underlying implementation.
     */
    void sendChatStateNotification(Object chatDescriptor, ChatState chatState)
            throws IllegalStateException, IllegalArgumentException,
            SmackException.NotConnectedException, InterruptedException;
}
