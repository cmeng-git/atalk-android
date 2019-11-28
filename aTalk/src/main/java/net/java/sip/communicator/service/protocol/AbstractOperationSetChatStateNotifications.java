/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Represents a default implementation of <tt>OperationSetChatStateNotifications</tt> in order to make
 * it easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @param <T> the type of the <tt>ProtocolProviderService</tt> implementation providing the
 * <tt>AbstractOperationSetChatStateNotifications</tt> implementation
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetChatStateNotifications<T extends ProtocolProviderService>
        implements OperationSetChatStateNotifications
{
    /**
     * The provider that created us.
     */
    protected final T parentProvider;

    /**
     * The list of currently registered <tt>ChatStateNotificationsListener</tt>s.
     */
    private final List<ChatStateNotificationsListener> chatStateNotificationsListeners = new ArrayList<>();

    /**
     * Initializes a new <tt>AbstractOperationSetChatStateNotifications</tt> instance created by a
     * specific <tt>ProtocolProviderService</tt> instance.
     *
     * @param parentProvider the <tt>ProtocolProviderService</tt> which creates the new instance
     */
    protected AbstractOperationSetChatStateNotifications(T parentProvider)
    {
        this.parentProvider = parentProvider;
    }

    /**
     * Adds <tt>listener</tt> to the list of listeners registered for receiving <tt>ChatStateNotificationEvent</tt>s.
     *
     * @param listener the <tt>TypingNotificationsListener</tt> listener that we'd like to add
     * @see OperationSetChatStateNotifications#addChatStateNotificationsListener(ChatStateNotificationsListener)
     */
    public void addChatStateNotificationsListener(ChatStateNotificationsListener listener)
    {
        synchronized (chatStateNotificationsListeners) {
            if (!chatStateNotificationsListeners.contains(listener))
                chatStateNotificationsListeners.add(listener);
        }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws IllegalStateException if the underlying stack is not registered and initialized
     */
    protected void assertConnected()
            throws IllegalStateException
    {
        if (parentProvider == null)
            throw new IllegalStateException("The provider must be non-null before being able to  communicate.");
        if (!parentProvider.isRegistered())
            throw new IllegalStateException("The provider must be signed on the service before being able to communicate.");
    }

    /**
     * Delivers a <tt>ChatStateNotificationEvent</tt> to all registered listeners.
     *
     * @param sourceContact the contact who has sent the notification
     * @param chatState the chat state from event delivery
     */
    public void fireChatStateNotificationsEvent(ChatStateNotificationEvent evt)
    {
        ChatStateNotificationsListener[] listeners;
        synchronized (chatStateNotificationsListeners) {
            listeners = chatStateNotificationsListeners.toArray(new ChatStateNotificationsListener[0]);
        }

        // Timber.d("Dispatching ChatState Event to %d listeners with  chatState: %s", listeners.length, evt.getChatState());
        for (ChatStateNotificationsListener listener : listeners)
            listener.chatStateNotificationReceived(evt);
    }

    /**
     * Delivers a <tt>ChatStateNotificationEvent</tt> to all registered listeners for delivery failed event.
     *
     * @param sourceContact the contact who has sent the notification
     * @param evtCode the code of the event to deliver
     */
    public void fireChatStateNotificationsDeliveryFailedEvent(ChatStateNotificationEvent evt)
    {
        ChatStateNotificationsListener[] listeners;
        synchronized (chatStateNotificationsListeners) {
            listeners = chatStateNotificationsListeners.toArray(new ChatStateNotificationsListener[0]);
        }

        Timber.d("Dispatching Delivery Failure ChatState Event to %d listeners. ChatDescriptor '%s' has chatState: %s",
                listeners.length, evt.getChatDescriptor().toString(), evt.getChatState());

        for (ChatStateNotificationsListener listener : listeners)
            listener.chatStateNotificationDeliveryFailed(evt);
    }

    /**
     * Removes <tt>listener</tt> from the list of listeners registered for receiving
     * <tt>ChatStateNotificationEvent</tt>s.
     *
     * @param listener the <tt>TypingNotificationsListener</tt> listener that we'd like to remove
     * @see OperationSetChatStateNotifications#removeChatStateNotificationsListener(ChatStateNotificationsListener)
     */
    public void removeChatStateNotificationsListener(ChatStateNotificationsListener listener)
    {
        synchronized (chatStateNotificationsListeners) {
            chatStateNotificationsListeners.remove(listener);
        }
    }
}
