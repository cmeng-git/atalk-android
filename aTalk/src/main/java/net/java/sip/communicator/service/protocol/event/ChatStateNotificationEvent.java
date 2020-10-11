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
import net.java.sip.communicator.service.protocol.Contact;

import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.chatstates.ChatState;

import java.util.EventObject;

/**
 * <tt>ChatStateNotificationEvent</tt>s are delivered upon reception of a corresponding message
 * from a remote contact. <tt>ChatStateNotificationEvent</tt>s contain a state id, identifying
 * the exact chat state event that has occurred (a user has started or stopped composing), the
 * source <tt>Contact</tt> that generated the event and others.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class ChatStateNotificationEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    // private int mChatState = OperationSetChatStateNotifications.STATE_UNKNOWN;
    private ChatState mChatState;
    private Message message;

    /**
     * Creates a ChatStateNotificationEvent with the specified parameters.
     *
     * @param chatDescriptor the Chat Descriptor that has sent the notification.
     * @param state the <tt>Contact</tt>'s current chat state
     * @param msg the message received
     */
    public ChatStateNotificationEvent(Object chatDescriptor, ChatState state, Message msg)
    {
        super(chatDescriptor);
        this.mChatState = state;
        this.message = msg;
    }

    /**
     * Returns the chat state that this <tt>ChatStateNotificationEvent</tt> is carrying.
     *
     * @return one of the <tt>ChatState</tt>s indicating the chat state that this notification is about.
     */
    public ChatState getChatState()
    {
        return mChatState;
    }

    /**
     * Returns a reference to the <tt>Contact</tt> that has sent this event.
     *
     * @return a reference to the <tt>Contact</tt> whose chat state we're being notified about.
     */
    public Object getChatDescriptor()
    {
        return getSource();
    }

    public Message getMessage()
    {
        return this.message;
    }

    /**
     * Returns a String representation of this EventObject.
     *
     * @return A a String representation of this EventObject.
     */
    @NotNull
    @Override
    public String toString()
    {
        Object chatDescriptor = getChatDescriptor();
        String from = (chatDescriptor instanceof Contact)
                ? ((Contact) chatDescriptor).getAddress()
                : ((ChatRoom) chatDescriptor).getName();

        return "ChatStateNotificationEvent[from = " + from + "; state = " + getChatState();
    }
}
