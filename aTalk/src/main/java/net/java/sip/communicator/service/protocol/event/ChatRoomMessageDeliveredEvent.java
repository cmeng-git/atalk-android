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

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.IMessage;

import java.util.Date;
import java.util.EventObject;

/**
 * <code>MessageDeliveredEvent</code>s confirm successful delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ChatRoomMessageDeliveredEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private final Date mTimestamp;

    /**
     * The received <code>IMessage</code>.
     */
    private final IMessage mMessage;

    /**
     * The type of message event that this instance represents.
     */
    private final int mEventType;

    /**
     * Some services can fill our room with message history.
     */
    private boolean mHistoryMessage = false;

    /**
     * Creates a <code>MessageDeliveredEvent</code> representing delivery of the <code>source</code> message
     * to the specified <code>to</code> contact.
     *
     * @param source the <code>ChatRoom</code> which triggered this event.
     * @param timestamp a date indicating the exact moment when the event occurred
     * @param message the message that triggered this event.
     * @param eventType indicating the type of the delivered event.
     * It is either an ACTION_MESSAGE_DELIVERED or a CONVERSATION_MESSAGE_DELIVERED.
     */
    public ChatRoomMessageDeliveredEvent(ChatRoom source, Date timestamp, IMessage message, int eventType)
    {
        super(source);
        mTimestamp = timestamp;
        mMessage = message;
        mEventType = eventType;
    }

    /**
     * Returns the received message.
     *
     * @return the <code>IMessage</code> that triggered this event.
     */
    public IMessage getMessage()
    {
        return mMessage;
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    public Date getTimestamp()
    {
        return mTimestamp;
    }

    /**
     * Returns the <code>ChatRoom</code> that triggered this event.
     *
     * @return the <code>ChatRoom</code> that triggered this event.
     */
    public ChatRoom getSourceChatRoom()
    {
        return (ChatRoom) getSource();
    }

    /**
     * Returns the type of message event represented by this event instance. IMessage event type is
     * one of the XXX_MESSAGE_DELIVERED fields of this class.
     *
     * @return one of the XXX_MESSAGE_DELIVERED fields of this class indicating the type of this event.
     */
    public int getEventType()
    {
        return mEventType;
    }

    /**
     * Is current event for history message.
     *
     * @return is current event for history message.
     */
    public boolean isHistoryMessage()
    {
        return mHistoryMessage;
    }

    /**
     * Changes property, whether this event is for a history message.
     *
     * @param historyMessage whether its event for history message.
     */
    public void setHistoryMessage(boolean historyMessage)
    {
        mHistoryMessage = historyMessage;
    }
}
