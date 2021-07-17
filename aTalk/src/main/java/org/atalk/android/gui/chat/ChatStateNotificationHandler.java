/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.android.gui.chat;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent;

import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Resourcepart;

import java.util.Timer;
import java.util.TimerTask;

/**
 * The <tt>ChatStateNotificationHandler</tt> is the class that handles chat state notification
 * events and launches the corresponding user interface.
 *
 * @author Eng Chong Meng
 */
public class ChatStateNotificationHandler
{
    private static Timer chatStateTimer = new Timer();

    /**
     * Informs the user what is the chat state of his chat contacts.
     *
     * @param evt the event containing details on the chat state notification
     * @param chatFragment the chat parent fragment
     */
    public static void handleChatStateNotificationReceived(ChatStateNotificationEvent evt, ChatFragment chatFragment)
    {
        chatStateTimer.cancel();
        chatStateTimer = new Timer();

        /*
         * If the given event doesn't concern the chat fragment chatDescriptor we have nothing more to do here.
         */
        Object chatDescriptor = evt.getChatDescriptor();
        if (chatFragment != null) {
            ChatSession chatSession = chatFragment.getChatPanel().getChatSession();
            ChatTransport chatTransport = chatSession.getCurrentChatTransport();
            // return if event is not for the chatDescriptor session
            if ((chatTransport == null) || !chatDescriptor.equals(chatTransport.getDescriptor()))
                return;

            // return if receive own chat state notification
            if (chatDescriptor instanceof ChatRoom) {
                Jid entityJid = evt.getMessage().getFrom();
                Resourcepart fromNick = entityJid.getResourceOrNull();

                MultiUserChat multiUserChat = ((ChatRoom) chatDescriptor).getMultiUserChat();
                Resourcepart nickName = multiUserChat.getNickname();
                if ((nickName != null) && nickName.equals(fromNick))
                    return;
            }

            if ((chatFragment.getChatListView() != null) && (chatFragment.getChatListAdapter() != null)) {
                if (evt.getMessage().getBody() != null) {
                    chatFragment.setChatState(null, null);
                }
                else {
                    EntityFullJid fullFrom = (EntityFullJid) evt.getMessage().getFrom();
                    String sender = fullFrom.getLocalpart().toString();
                    if (chatDescriptor instanceof ChatRoom) {
                        sender = fullFrom.getResourceOrEmpty().toString();
                    }

                    // Display current chatState for a 10-seconds duration
                    ChatState chatState = evt.getChatState();
                    chatFragment.setChatState(chatState, sender);
                    chatStateTimer.schedule(new ChatTimerTask(chatFragment), 10000);
                }
            }
        }
    }

    /**
     * Clear the chat state display message after display timer expired.
     */
    private static class ChatTimerTask extends TimerTask
    {
        private final ChatFragment chatFragment;

        public ChatTimerTask(ChatFragment chatFragment)
        {
            this.chatFragment = chatFragment;
        }

        @Override
        public void run()
        {
            chatFragment.setChatState(null, null);
        }
    }
}
