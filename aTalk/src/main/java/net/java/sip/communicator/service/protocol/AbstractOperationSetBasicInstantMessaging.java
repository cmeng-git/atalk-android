/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.*;

import java.util.*;

import timber.log.Timber;

/**
 * Represents a default implementation of {@link OperationSetBasicInstantMessaging} in order to make
 * it easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetBasicInstantMessaging implements OperationSetBasicInstantMessaging
{
    /**
     * A list of listeners registered for message events.
     */
    private final List<MessageListener> messageListeners = new LinkedList<>();

    /**
     * Registers a MessageListener with this operation set so that it gets notifications of
     * successful message delivery, failure or reception of incoming messages..
     *
     * @param listener the <tt>MessageListener</tt> to register.
     */
    public void addMessageListener(MessageListener listener)
    {
        synchronized (messageListeners) {
            if (!messageListeners.contains(listener)) {
                messageListeners.add(listener);
            }
        }
    }

    /**
     * Unregisters <tt>listener</tt> so that it won't receive any further notifications upon
     * successful message delivery, failure or reception of incoming messages..
     *
     * @param listener the <tt>MessageListener</tt> to unregister.
     */
    public void removeMessageListener(MessageListener listener)
    {
        synchronized (messageListeners) {
            messageListeners.remove(listener);
        }
    }

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the MIME-type for <tt>content</tt>
     * @param subject a <tt>String</tt> subject or <tt>null</tt> for now subject.
     * @return the newly created message.
     */
    public IMessage createMessage(byte[] content, int encType, String subject)
    {
        String contentAsString;
        contentAsString = new String(content);
        return createMessage(contentAsString, encType, subject);
    }

    /**
     * Create a IMessage instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    public IMessage createMessage(String messageText)
    {
        return createMessage(messageText, IMessage.ENCODE_PLAIN, null);
    }

    public abstract IMessage createMessage(String content, int encType, String subject);

    /**
     * Create a IMessage instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText the string content of the message.
     * @param encType the mime and encryption type for the <tt>content</tt>
     * @param messageUID the unique identifier of this message.
     * @return IMessage the newly created message
     */
    public IMessage createMessageWithUID(String messageText, int encType, String messageUID)
    {
        return createMessage(messageText);
    }

    protected enum MessageEventType
    {
        None, MessageDelivered, MessageReceived, MessageDeliveryFailed, MessageDeliveryPending,
    }

    /**
     * Delivers the specified event to all registered message listeners.
     *
     * @param evt the <tt>EventObject</tt> that we'd like delivered to all registered message listeners.
     */
    protected void fireMessageEvent(EventObject evt)
    {
        Collection<MessageListener> listeners;
        synchronized (this.messageListeners) {
            listeners = new ArrayList<>(this.messageListeners);
        }
        Timber.d("Dispatching Message Listeners = %d evt = %s", listeners.size(), evt);

        /*
         * TODO Create a super class like this MessageEventObject that would contain the
         * MessageEventType. Also we could fire an event for the MessageDeliveryPending event type
         * (modify MessageListener and OperationSetInstantMessageTransform).
         */
        MessageEventType eventType = MessageEventType.None;
        if (evt instanceof MessageDeliveredEvent) {
            eventType = MessageEventType.MessageDelivered;
        }
        else if (evt instanceof MessageReceivedEvent) {
            eventType = MessageEventType.MessageReceived;
        }
        else if (evt instanceof MessageDeliveryFailedEvent) {
            eventType = MessageEventType.MessageDeliveryFailed;
        }

        // Transform the event.
        EventObject[] events = messageTransform(evt, eventType);
        for (EventObject event : events) {
            try {
                if (event == null)
                    return;

                for (MessageListener listener : listeners) {
                    switch (eventType) {
                        case MessageDelivered:
                            listener.messageDelivered((MessageDeliveredEvent) event);
                            break;
                        case MessageDeliveryFailed:
                            listener.messageDeliveryFailed((MessageDeliveryFailedEvent) event);
                            break;
                        case MessageReceived:
                            listener.messageReceived((MessageReceivedEvent) event);
                            break;
                        default:
                            // We either have nothing to do or we do not know what to do. Just silence the compiler.
                            break;
                    }
                }
            } catch (Throwable e) {
                Timber.e(e, "Error delivering message");
            }
        }
    }

    /**
     * Messages pending delivery to be transformed.
     *
     * @param evt the message delivery event
     * @return returns message delivery events
     */
    protected MessageDeliveredEvent[] messageDeliveryPendingTransform(final MessageDeliveredEvent evt)
    {
        EventObject[] transformed = messageTransform(evt, MessageEventType.MessageDeliveryPending);

        final int size = transformed.length;
        MessageDeliveredEvent[] events = new MessageDeliveredEvent[size];
        System.arraycopy(transformed, 0, events, 0, size);
        return events;
    }

    /**
     * Transform provided source event by processing transform layers in sequence.
     *
     * @param evt the source event to transform
     * @param eventType the event type of the source event
     * @return returns the resulting (transformed) events, if any. (I.e. an array of 0 or more size containing events.)
     */
    private EventObject[] messageTransform(final EventObject evt, final MessageEventType eventType)
    {
        if (evt == null) {
            return new EventObject[0];
        }
        ProtocolProviderService protocolProvider;
        switch (eventType) {
            case MessageDelivered:
                protocolProvider = ((MessageDeliveredEvent) evt).getDestinationContact().getProtocolProvider();
                break;
            case MessageDeliveryFailed:
                protocolProvider = ((MessageDeliveryFailedEvent) evt).getDestinationContact().getProtocolProvider();
                break;
            case MessageDeliveryPending:
                protocolProvider = ((MessageDeliveredEvent) evt).getDestinationContact().getProtocolProvider();
                break;
            case MessageReceived:
                protocolProvider = ((MessageReceivedEvent) evt).getSourceContact().getProtocolProvider();
                break;
            default:
                return new EventObject[]{evt};
        }

        OperationSetInstantMessageTransformImpl opSetMessageTransform = (OperationSetInstantMessageTransformImpl)
                protocolProvider.getOperationSet(OperationSetInstantMessageTransform.class);

        if (opSetMessageTransform == null)
            return new EventObject[]{evt};

        // 'current' contains the events that need to be transformed. It should not contain null values.
        final LinkedList<EventObject> current = new LinkedList<>();
        // Add source event as start of transformation.
        current.add(evt);
        // 'next' contains the resulting events after transformation in the current iteration. It
        // should not contain null values.
        final LinkedList<EventObject> next = new LinkedList<>();
        for (Map.Entry<Integer, Vector<TransformLayer>> entry : opSetMessageTransform.transformLayers.entrySet()) {
            for (TransformLayer transformLayer : entry.getValue()) {
                next.clear();
                while (!current.isEmpty()) {
                    final EventObject event = current.remove();
                    switch (eventType) {
                        case MessageDelivered:
                            MessageDeliveredEvent transformedDelivered
                                    = transformLayer.messageDelivered((MessageDeliveredEvent) event);
                            if (transformedDelivered != null) {
                                next.add(transformedDelivered);
                            }
                            break;
                        case MessageDeliveryPending:
                            MessageDeliveredEvent[] evts
                                    = transformLayer.messageDeliveryPending((MessageDeliveredEvent) event);
                            for (MessageDeliveredEvent mde : evts) {
                                if (mde != null) {
                                    next.add(mde);
                                }
                            }
                            break;
                        case MessageDeliveryFailed:
                            MessageDeliveryFailedEvent transformedDeliveryFailed
                                    = transformLayer.messageDeliveryFailed((MessageDeliveryFailedEvent) event);
                            if (transformedDeliveryFailed != null) {
                                next.add(transformedDeliveryFailed);
                            }
                            break;
                        case MessageReceived:
                            MessageReceivedEvent transformedReceived
                                    = transformLayer.messageReceived((MessageReceivedEvent) event);
                            if (transformedReceived != null) {
                                next.add(transformedReceived);
                            }
                            break;
                        default:
                            next.add(event);
                            /* We either have nothing to do or we do not know what to do. */
                            break;
                    }
                }
                // Set events for next round of transformations.
                current.addAll(next);
            }
        }
        return current.toArray(new EventObject[0]);
    }

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the mime type we want to check
     * @param contact contact which is checked for supported encType
     * @return <tt>true</tt> if the contact supports it and <tt>false</tt> otherwise.
     */
    public boolean isContentTypeSupported(int mimeType, Contact contact)
    {
        // by default we support default mime type, for other mime-types method must be overridden
        return (IMessage.ENCODE_PLAIN == mimeType);
    }

    /**
     * Sends the <tt>message</tt> to the destination indicated by the <tt>to</tt>. Provides a
     * default implementation of this method.
     *
     * @param to the <tt>Contact</tt> to send <tt>message</tt> to
     * @param toResource the resource to which the message should be send
     * @param message the <tt>IMessage</tt> to send.
     */
    public void sendInstantMessage(Contact to, ContactResource toResource, IMessage message)
    {
        sendInstantMessage(to, message);
    }

    /**
     * Returns the inactivity timeout in milliseconds.
     *
     * @return The inactivity timeout in milliseconds. Or -1 if undefined
     */
    public long getInactivityTimeout()
    {
        return -1;
    }
}
