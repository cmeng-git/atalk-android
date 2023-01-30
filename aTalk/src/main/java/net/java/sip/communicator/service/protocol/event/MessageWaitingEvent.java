/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.NotificationMessage;
import net.java.sip.communicator.service.protocol.OperationSetMessageWaiting;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

/**
 * <code>MessageWaitingEvent<code> indicates a message waiting event is received.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MessageWaitingEvent extends EventObject {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The URI we can use to reach messages from provider that is firing the event.
     */
    private final String account;

    /**
     * Number of new/unread messages.
     */
    private final int unreadMessages;

    /**
     * Number of old/read messages.
     */
    private final int readMessages;

    /**
     * Number of new/unread urgent messages.
     */
    private final int unreadUrgentMessages;

    /**
     * Number of old/read messages.
     */
    private final int readUrgentMessages;

    /**
     * The message type for this event.
     */
    private final OperationSetMessageWaiting.MessageType messageType;

    /**
     * The list of notification messages concerned by this event.
     */
    private final List<NotificationMessage> messageList;

    /**
     * Constructs the Event with the given source, typically the provider and number of messages.
     *
     * @param messageType the message type for this event.
     * @param source the protocol provider from which this event is coming.
     * @param account the account URI we can use to reach the messages.
     * @param unreadMessages the unread messages.
     * @param readMessages the read messages.
     * @param unreadUrgentMessages the unread urgent messages.
     * @param readUrgentMessages the read urgent messages.
     */
    public MessageWaitingEvent(ProtocolProviderService source,
            OperationSetMessageWaiting.MessageType messageType, String account, int unreadMessages,
            int readMessages, int unreadUrgentMessages, int readUrgentMessages) {
        this(source, messageType, account, unreadMessages, readMessages, unreadUrgentMessages,
                readUrgentMessages, null);
    }

    /**
     * Constructs the Event with the given source, typically the provider and number of messages.
     *
     * @param messageType the message type for this event.
     * @param source the protocol provider from which this event is coming.
     * @param account the account URI we can use to reach the messages.
     * @param unreadMessages the unread messages.
     * @param readMessages the read messages.
     * @param unreadUrgentMessages the unread urgent messages.
     * @param readUrgentMessages the read urgent messages.
     * @param messages the list of messages that this event is about.
     */
    public MessageWaitingEvent(ProtocolProviderService source,
            OperationSetMessageWaiting.MessageType messageType, String account, int unreadMessages,
            int readMessages, int unreadUrgentMessages, int readUrgentMessages,
            List<NotificationMessage> messages) {
        super(source);

        this.messageType = messageType;
        this.account = account;
        this.unreadMessages = unreadMessages;
        this.readMessages = readMessages;
        this.unreadUrgentMessages = unreadUrgentMessages;
        this.readUrgentMessages = readUrgentMessages;
        this.messageList = messages;
    }

    /**
     * Returns the <code>ProtocolProviderService</code> which originated this event.
     *
     * @return the source <code>ProtocolProviderService</code>
     */
    public ProtocolProviderService getSourceProvider() {
        return (ProtocolProviderService) getSource();
    }

    /**
     * The URI we can use to reach messages from provider that is firing the event.
     *
     * @return account URI.
     */
    public String getAccount() {
        return account;
    }

    /**
     * Number of new/unread messages.
     *
     * @return Number of new/unread messages.
     */
    public int getUnreadMessages() {
        return unreadMessages;
    }

    /**
     * Number of old/read messages.
     *
     * @return Number of old/read messages.
     */
    public int getReadMessages() {
        return readMessages;
    }

    /**
     * Number of new/unread urgent messages.
     *
     * @return Number of new/unread urgent messages.
     */
    public int getUnreadUrgentMessages() {
        return unreadUrgentMessages;
    }

    /**
     * Number of old/read messages.
     *
     * @return Number of old/read messages.
     */
    public int getReadUrgentMessages() {
        return readUrgentMessages;
    }

    /**
     * The message type for this event.
     *
     * @return the message type.
     */
    public OperationSetMessageWaiting.MessageType getMessageType() {
        return messageType;
    }

    public Iterator<NotificationMessage> getMessages() {
        if (messageList != null)
            return messageList.iterator();

        return null;
    }
}
