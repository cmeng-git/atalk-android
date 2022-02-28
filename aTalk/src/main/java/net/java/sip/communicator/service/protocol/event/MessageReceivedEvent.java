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

import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;

import java.util.Date;
import java.util.EventObject;

/**
 * <code>MessageReceivedEvent</code>s indicate reception of an instant message.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class MessageReceivedEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The contact that has sent this message.
     */
    private Contact from = null;

    /**
     * The <code>ContactResource</code>, from which the message was sent.
     */
    private ContactResource fromResource = null;

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private final Date timestamp;

    /**
     * The type of message event that this instance represents.
     */
    private int eventType = -1;

    /**
     * The ID of the message being corrected, or null if this is a new message and not a correction.
     */
    private String correctedMessageUID = null;

    /**
     * Indicates whether this is private messaging event or not.
     */
    private boolean isPrivateMessaging = false;

    /**
     * The room associated with the contact which sent the message.
     */
    private ChatRoom privateMessagingContactRoom = null;

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param timestamp the exact date when the event occurred.
     */
    public MessageReceivedEvent(IMessage source, Contact from, Date timestamp)
    {
        this(source, from, timestamp, ChatMessage.MESSAGE_IN);
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param correctedMessageUID The ID of the message being corrected, or null if this is a new message and not a
     * correction.
     */
    public MessageReceivedEvent(IMessage source, Contact from, String correctedMessageUID)
    {
        this(source, from, new Date(), ChatMessage.MESSAGE_IN);
        this.correctedMessageUID = correctedMessageUID;
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param timestamp the exact date when the event occurred.
     * @param correctedMessageUID The ID of the message being corrected, or null if this is a new message and not a
     * correction.
     */
    public MessageReceivedEvent(IMessage source, Contact from, Date timestamp, String correctedMessageUID)
    {
        this(source, from, null, timestamp, correctedMessageUID);
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param fromResource the <code>ContactResource</code>, from which this message was sent.
     * @param timestamp the exact date when the event occurred.
     * @param correctedMessageUID The ID of the message being corrected, or null if this is a new message and not a
     * correction.
     */
    public MessageReceivedEvent(IMessage source, Contact from, ContactResource fromResource, Date timestamp,
            String correctedMessageUID)
    {
        this(source, from, fromResource, timestamp, ChatMessage.MESSAGE_IN);
        this.correctedMessageUID = correctedMessageUID;
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param fromResource the <code>ContactResource</code>, from which this message was sent
     * @param timestamp the exact date when the event occurred.
     * @param correctedMessageUID The ID of the message being corrected, or null if this is a new message and not a
     * correction.
     * @param isPrivateMessaging indicates whether the this is private messaging event or not.
     * @param privateContactRoom the chat room associated with the contact.
     */
    public MessageReceivedEvent(IMessage source, Contact from, ContactResource fromResource,
            Date timestamp, String correctedMessageUID, boolean isPrivateMessaging,
            ChatRoom privateContactRoom)
    {
        this(source, from, fromResource, timestamp, ChatMessage.MESSAGE_IN, isPrivateMessaging, privateContactRoom);
        this.correctedMessageUID = correctedMessageUID;
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param timestamp the exact date when the event occurred.
     * @param eventType the type of message event that this instance represents (one of the
     * XXX_MESSAGE_RECEIVED static fields).
     */
    public MessageReceivedEvent(IMessage source, Contact from, Date timestamp, int eventType)
    {
        this(source, from, null, timestamp, eventType);
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param fromResource the <code>ContactResource</code>, from which this message was sent
     * @param timestamp the exact date when the event occurred.
     * @param eventType the type of message event that this instance represents (one of the
     * XXX_MESSAGE_RECEIVED static fields).
     */
    public MessageReceivedEvent(IMessage source, Contact from, ContactResource fromResource, Date timestamp, int eventType)
    {
        this(source, from, fromResource, timestamp, eventType, false, null);
    }

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>IMessage</code> whose reception this event represents.
     * @param from the <code>Contact</code> that has sent this message.
     * @param fromResource the <code>ContactResource</code>, from which this message was sent
     * @param timestamp the exact date when the event occurred.
     * @param eventType the type of message event that this instance represents (one of the
     * XXX_MESSAGE_RECEIVED static fields).
     * @param isPrivateMessaging indicates whether the this is private messaging event or not.
     * @param privateContactRoom the chat room associated with the contact.
     */
    public MessageReceivedEvent(IMessage source, Contact from, ContactResource fromResource,
            Date timestamp, int eventType, boolean isPrivateMessaging, ChatRoom privateContactRoom)
    {
        super(source);

        // Convert to MESSAGE_HTTP_FILE_DOWNLOAD if it is http download link
        // source.getContent() may be null (Omemo key message contains no body content)
        if (FileBackend.isHttpFileDnLink(source.getContent())) {
                eventType = ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD;
        }

        this.from = from;
        this.fromResource = fromResource;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.isPrivateMessaging = isPrivateMessaging;
        this.privateMessagingContactRoom = privateContactRoom;
    }

    /**
     * Returns a reference to the <code>Contact</code> that has sent the <code>IMessage</code> whose
     * reception this event represents.
     *
     * @return a reference to the <code>Contact</code> that has sent the <code>IMessage</code> whose
     * reception this event represents.
     */
    public Contact getSourceContact()
    {
        return from;
    }

    /**
     * Returns a reference to the <code>ContactResource</code> that has sent the <code>IMessage</code> whose
     * reception this event represents.
     *
     * @return a reference to the <code>ContactResource</code> that has sent the <code>IMessage</code> whose
     * reception this event represents.
     */
    public ContactResource getContactResource()
    {
        return fromResource;
    }

    /**
     * Returns the message that triggered this event
     *
     * @return the <code>IMessage</code> that triggered this event.
     */
    public IMessage getSourceMessage()
    {
        return (IMessage) getSource();
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    public Date getTimestamp()
    {
        return timestamp;
    }

    /**
     * Returns the type of message event represented by this event instance. IMessage event type is
     * one of the XXX_MESSAGE_RECEIVED fields of this class.
     *
     * @return one of the XXX_MESSAGE_RECEIVED fields of this class indicating the type of this
     * event.
     */
    public int getEventType()
    {
        return eventType;
    }

    /**
     * Returns the correctedMessageUID The ID of the message being corrected, or null if this is a
     * new message and not a correction.
     *
     * @return the correctedMessageUID The ID of the message being corrected, or null if this is a
     * new message and not a correction.
     */
    public String getCorrectedMessageUID()
    {
        return correctedMessageUID;
    }

    /**
     * Returns the chat room of the private messaging contact associated with the event and null if
     * the contact is not private messaging contact.
     *
     * @return the chat room associated with the contact or null if no chat room is associated with
     * the contact.
     */
    public ChatRoom getPrivateMessagingContactRoom()
    {
        return privateMessagingContactRoom;
    }

    /**
     * Returns <code>true</true> if this is private messaging event and
     * <code>false</code> if not.
     *
     * @return <code>true</true> if this is private messaging event and
     * <code>false</code> if not.
     */
    public boolean isPrivateMessaging()
    {
        return isPrivateMessaging;
    }

}
