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
 * notifications to and from other <tt>Contact</tt>s.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface OperationSetChatStateNotifications extends OperationSet
{
    /**
     * Adds <tt>l</tt> to the list of listeners registered for receiving <tt>ChatStateNotificationEvent</tt>s
     *
     * @param l the <tt>ChatStateNotificationsListener</tt> listener that we'd like to add
     */
    void addChatStateNotificationsListener(ChatStateNotificationsListener l);

    /**
     * Removes <tt>l</tt> from the list of listeners registered for receiving <tt>ChatStateNotificationEvent</tt>s
     *
     * @param l the <tt>ChatStateNotificationsListener</tt> listener that we'd like to remove
     */
    void removeChatStateNotificationsListener(ChatStateNotificationsListener l);

    /**
     * Sends a notification to <tt>notifiedContact</tt> that we have entered <tt>chatState</tt>.
     *
     * @param chatDescriptor the chatDescriptor to notify
     * @param chatState the chat state as defined in ChatState that we have entered.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <tt>notifiedContact</tt> is not an instance belonging
     * to the underlying implementation.
     */
    void sendChatStateNotification(Object chatDescriptor, ChatState chatState)
            throws IllegalStateException, IllegalArgumentException,
            SmackException.NotConnectedException, InterruptedException;
}
