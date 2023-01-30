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

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;

import java.util.Date;
import java.util.EventObject;

/**
 * <code>MessageReceivedEvent</code>s indicate reception of an instant message.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class ChatRoomMessageReceivedEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The chat room member that has sent this message.
     */
    private final ChatRoomMember from;

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

    private final boolean isAutoJoin;

    /**
     * Indicates whether the message is important or not.
     */
    private boolean isImportantMessage = false;

    /**
     * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code>
     * message received from the specified <code>from</code> contact.
     *
     * @param source the <code>ChatRoom</code> for which the message is received.
     * @param from the <code>ChatRoomMember</code> that has sent this message.
     * @param timestamp the exact date when the event occurred.
     * @param message the received <code>IMessage</code>.
     * @param eventType the type of message event that this instance represents
     * (one of the XXX_MESSAGE_RECEIVED static fields).
     */
    public ChatRoomMessageReceivedEvent(ChatRoom source, ChatRoomMember from, Date timestamp,
            IMessage message, int eventType)
    {
        super(source);
        // Convert to MESSAGE_HTTP_FILE_DOWNLOAD if it is http download link
        if (FileBackend.isHttpFileDnLink(message.getContent())) {
                eventType = ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD;
        }

        this.from = from;
        mTimestamp = timestamp;
        mMessage = message;
        mEventType = eventType;

        MUCServiceImpl mucService = MUCActivator.getMUCService();
        ChatRoomWrapper chatRoomWrapper = mucService.getChatRoomWrapperByChatRoom(source, false);
        isAutoJoin = (chatRoomWrapper != null) && chatRoomWrapper.isAutoJoin();
    }

    /**
     * Returns a reference to the <code>ChatRoomMember</code> that has send the <code>IMessage</code>
     * whose reception this event represents.
     *
     * @return a reference to the <code>ChatRoomMember</code> that has send the <code>IMessage</code>
     * whose reception this event represents.
     */
    public ChatRoomMember getSourceChatRoomMember()
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
     * one of the XXX_MESSAGE_RECEIVED fields of this class.
     *
     * @return one of the XXX_MESSAGE_RECEIVED fields of this class indicating the type of this event.
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
     * Is current chatRoom autoJoined.
     *
     * @return true if current event is from autoJoined chatRoom.
     */
    public boolean isAutoJoin()
    {
        return isAutoJoin;
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

    /**
     * Sets the the important message flag of the event.
     *
     * @param isImportant the value to be set.
     */
    public void setImportantMessage(boolean isImportant)
    {
        isImportantMessage = isImportant;
    }

    /**
     * Returns <code>true</code> if message is important and <code>false</code> if not.
     *
     * @return <code>true</code> if message is important and <code>false</code> if not.
     */
    public boolean isImportantMessage()
    {
        return isImportantMessage;
    }
}
