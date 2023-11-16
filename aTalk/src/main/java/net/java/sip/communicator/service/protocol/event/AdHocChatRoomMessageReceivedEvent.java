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

import net.java.sip.communicator.service.protocol.AdHocChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;

import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;

import java.util.Date;
import java.util.EventObject;

/**
 * <code>MessageReceivedEvent</code>s indicate reception of an instant message. (for an ad-hoc chat
 * room; see <code>AdHocChatRoom</code>)
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
@SuppressWarnings("serial")
public class AdHocChatRoomMessageReceivedEvent extends EventObject
{
    /**
     * An event type indicating that the message being received is a standard conversation message
     * sent by another member of the chatRoom to all current participants.
     */
    public static final int CONVERSATION_MESSAGE_RECEIVED = ChatMessage.MESSAGE_MUC_IN;

    /**
     * An event type indicating that the message being received is a special message that sent by
     * either another member or the server itself, indicating that some kind of action (other than
     * the delivery of a conversation message) has occurred. Action messages are widely used in IRC
     * through the /action and /me commands
     */
    public static final int ACTION_MESSAGE_RECEIVED = ChatMessage.MESSAGE_ACTION;

    /**
     * An event type indicting that the message being received is a system message being sent by the
     * server or a system administrator, possibly notifying us of something important such as
     * ongoing maintenance activities or server downtime.
     */
    public static final int SYSTEM_MESSAGE_RECEIVED = ChatMessage.MESSAGE_SYSTEM;

    /**
     * The contact that has sent this message.
     */
    private final Contact from;

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private final Date timestamp;

    /**
     * The received <code>IMessage</code>.
     */
    private final IMessage message;

    /**
     * The type of message event that this instance represents.
     */
    private final int eventType;

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
     * received from the specified <code>from</code> contact.
     *
     * @param source the <code>AdHocChatRoom</code> for which the message is received.
     * @param from the <code>Contact</code> that has sent this message.
     * @param timestamp the exact date when the event occurred.
     * @param message the received <code>IMessage</code>.
     * @param eventType the type of message event that this instance represents (one of the
     * XXX_MESSAGE_RECEIVED static fields).
     */
    public AdHocChatRoomMessageReceivedEvent(AdHocChatRoom source, Contact from, Date timestamp,
            IMessage message, int eventType)
    {
        super(source);
        // Convert to MESSAGE_HTTP_FILE_DOWNLOAD if it is http download link
        if (FileBackend.isHttpFileDnLink(message.getContent())) {
            eventType = ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD;
        }

        this.from = from;
        this.timestamp = timestamp;
        this.message = message;
        this.eventType = eventType;
    }

    /**
     * Returns a reference to the <code>Contact</code> that has send the <code>IMessage</code> whose
     * reception this event represents.
     *
     * @return a reference to the <code>Contact</code> that has send the <code>IMessage</code> whose
     * reception this event represents.
     */
    public Contact getSourceChatRoomParticipant()
    {
        return from;
    }

    /**
     * Returns the received message.
     *
     * @return the <code>IMessage</code> that triggered this event.
     */
    public IMessage getMessage()
    {
        return message;
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
     * Returns the <code>AdHocChatRoom</code> that triggered this event.
     *
     * @return the <code>AdHocChatRoom</code> that triggered this event.
     */
    public AdHocChatRoom getSourceChatRoom()
    {
        return (AdHocChatRoom) getSource();
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
}
