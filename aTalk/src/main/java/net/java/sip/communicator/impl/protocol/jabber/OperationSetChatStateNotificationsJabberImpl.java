/**
 * aTalk (jitsi-android fork), the OpenSource Java VoIP and Instant Messaging client.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;

import org.atalk.android.gui.chat.MetaContactChatTransport;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.chatstates.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.xevent.*;
import org.jxmpp.jid.*;

import java.util.List;

import timber.log.Timber;

/**
 * OperationSet that handle chat state notifications
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class OperationSetChatStateNotificationsJabberImpl extends
        AbstractOperationSetChatStateNotifications<ProtocolProviderServiceJabberImpl> implements ChatStateListener
{
    /**
     * An active instance of the opSetPeersPresence operation set. We're using it to map incoming
     * events to contacts in our contact list.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPeersPresence = null;

    /**
     * An active instance of the opSetBasicIM operation set.
     */
    private OperationSetBasicInstantMessagingJabberImpl opSetBasicIM = null;

    /**
     * An instant of the smack ChatManager for the current PPS
     */
    private ChatManager mChatManager = null;
    /**
     * An instant of the smack MultiUserChatManager for the current PPS
     */
    private MultiUserChatManager multiUserChatManager = null;
    /**
     * The manger which send us the chat state info and through which we send info
     */
    private MessageEventManager messageEventManager = null;

    private ChatStateManager chatStateManager = null;

    /**
     * @param provider a ref to the <tt>ProtocolProviderServiceImpl</tt> that created us and that we'll use
     * for retrieving the underlying aim connection.
     */
    OperationSetChatStateNotificationsJabberImpl(ProtocolProviderServiceJabberImpl provider)
    {
        super(provider);
        // We use this listener to seize the moment when the protocol provider has been successfully registered.
        provider.addRegistrationStateChangeListener(new ProviderRegListener());
    }

    /**
     * Sends a chat state notification to the chatDescriptor that chat state we are in
     * XEP-0085 chat state sending
     *
     * @param chatDescriptor the <tt>chatDescriptor</tt> i.e. Contact or ChatRoom to notify
     * @param chatState the chat state that we have entered.
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <tt>notifiedContact</tt> is not
     * an instance belonging to the underlying implementation.
     */
    public void sendChatStateNotification(Object chatDescriptor, ChatState chatState)
            throws IllegalStateException, IllegalArgumentException, NotConnectedException, InterruptedException
    {
        if (mChatManager == null)
            return;

        EntityBareJid entityBareJid;
        if (chatDescriptor instanceof Contact) {
            entityBareJid = (EntityBareJid) ((Contact) chatDescriptor).getJid();
            if (chatState == ChatState.gone)
                opSetBasicIM.purgeGoneJidThreads(entityBareJid);

            Chat chat = mChatManager.chatWith(entityBareJid);
            chatStateManager.setCurrentState(chatState, chat);
        }
        else if (chatDescriptor instanceof ChatRoom) {
            // XEP-0085: A client SHOULD NOT generate <gone/> notifications in group chat.
            if (ChatState.gone.equals(chatState))
                return;

            entityBareJid = ((ChatRoom) chatDescriptor).getIdentifier();
            MultiUserChat mucChat = multiUserChatManager.getMultiUserChat(entityBareJid);
            chatStateManager.setCurrentState(chatState, mucChat);
        }
        else {
            throw new IllegalArgumentException("The specified chatDescriptor is not valid." + chatDescriptor);
        }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    @Override
    protected void assertConnected()
            throws IllegalStateException
    {
        if (parentProvider != null && !parentProvider.isRegistered()
                && opSetPeersPresence.getPresenceStatus().isOnline()) {
            // if we are not registered but the current status is online; change the current status
            opSetPeersPresence.fireProviderStatusChangeEvent(opSetPeersPresence.getPresenceStatus(),
                    parentProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE));
        }
        super.assertConnected();
    }

    /**
     * Our listener that will tell us when we're registered and ready to accept us as a listener.
     */
    private class ProviderRegListener implements RegistrationStateChangeListener
    {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        public void registrationStateChanged(RegistrationStateChangeEvent evt)
        {
            if (evt.getNewState() == RegistrationState.REGISTERED) {
                /* XMPPTCPConnection connection for chat session. */
                XMPPConnection connection = parentProvider.getConnection();

                opSetPeersPresence = (OperationSetPersistentPresenceJabberImpl) parentProvider
                        .getOperationSet(OperationSetPersistentPresence.class);
                opSetBasicIM = (OperationSetBasicInstantMessagingJabberImpl) parentProvider
                        .getOperationSet(OperationSetBasicInstantMessaging.class);

                messageEventManager = MessageEventManager.getInstanceFor(parentProvider.getConnection());
                messageEventManager.addMessageEventRequestListener(new JabberMessageEventRequestListener());
                messageEventManager.addMessageEventNotificationListener(new IncomingMessageEventsListener());

                // smack ChatStatManager#ChatStateListener 4.4.3 does not support group chat state notifications (enhaced to support)
                mChatManager = ChatManager.getInstanceFor(connection);
                multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);

                chatStateManager = ChatStateManager.getInstance(connection);
                chatStateManager.addChatStateListener(OperationSetChatStateNotificationsJabberImpl.this);

                // if (smackChatStateListener == null) {
                //     smackChatStateListener = new SmackChatStateListener();
                //     mConnection.addAsyncStanzaListener(smackChatStateListener, CHATSTATE);
                // }
            }
            else if (evt.getNewState() == RegistrationState.UNREGISTERED
                    || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED
                    || evt.getNewState() == RegistrationState.CONNECTION_FAILED) {
                // if (parentProvider.getConnection() != null) {
                //       parentProvider.getConnection().removeAsyncStanzaListener(smackChatStateListener);
                //  }
                //  smackChatStateListener = null;

                mChatManager = null;
                multiUserChatManager = null;

                if (chatStateManager != null) {
                    chatStateManager.removeChatStateListener(OperationSetChatStateNotificationsJabberImpl.this);
                    chatStateManager = null;
                }

                if (messageEventManager != null) {
                    messageEventManager = null;
                }
            }
        }
    }

    /**
     * Listens for incoming request for chat state info
     */
    private class JabberMessageEventRequestListener implements MessageEventRequestListener
    {
        public void deliveredNotificationRequested(Jid from, String packetID, MessageEventManager messageEventManager)
        {
            try {
                messageEventManager.sendDeliveredNotification(from, packetID);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void displayedNotificationRequested(Jid from, String packetID,
                MessageEventManager messageEventManager)
        {
            try {
                messageEventManager.sendDisplayedNotification(from, packetID);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void composingNotificationRequested(Jid from, String packetID,
                MessageEventManager messageEventManager)
        {
            try {
                messageEventManager.sendComposingNotification(from, packetID);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void offlineNotificationRequested(Jid from, String packetID,
                MessageEventManager messageEventManager)
        {
            try {
                messageEventManager.sendCancelledNotification(from, packetID);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Receives incoming chat state info, Jid from is always a buddy (currently not implemented in aTalk)
     * #TODO - to use for message delivery
     *
     * @see <a href="http://xmpp.org/extensions/xep-0022.html">XEP-22: Message Events</a>
     * Note: This specification has been obsoleted in favor of XEP-0085 and XEP-0184.
     */
    private class IncomingMessageEventsListener implements MessageEventNotificationListener
    {
        /**
         * Called when a notification of message delivered is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void deliveredNotification(Jid from, String packetID)
        {
        }

        /**
         * Called when a notification of message displayed is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void displayedNotification(Jid from, String packetID)
        {
        }

        /**
         * Called when a notification that the receiver of the message is composing a reply is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void composingNotification(Jid from, String packetID)
        {
            BareJid bareFrom = from.asBareJid();
            Contact sourceContact = opSetPeersPresence.findContactByJid(bareFrom);

            // create the volatile contact if not found
            if (sourceContact == null) {
                sourceContact = opSetPeersPresence.createVolatileContact(bareFrom);
            }

            ChatStateNotificationEvent event = new ChatStateNotificationEvent(sourceContact, ChatState.composing, null);
            fireChatStateNotificationsEvent(event);
        }

        /**
         * Called when a notification that the receiver of the message is offline is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void offlineNotification(Jid from, String packetID)
        {
        }

        /**
         * Called when a notification that the receiver of the message cancelled the reply is received.
         *
         * @param from the user that sent the notification.
         * @param packetID the id of the message that was sent.
         */
        public void cancelledNotification(Jid from, String packetID)
        {
            BareJid bareFrom = from.asBareJid();
            Contact sourceContact = opSetPeersPresence.findContactByJid(bareFrom);

            // create the volatile contact if not found
            if (sourceContact == null) {
                sourceContact = opSetPeersPresence.createVolatileContact(bareFrom);
            }

            ChatStateNotificationEvent event = new ChatStateNotificationEvent(sourceContact, ChatState.inactive, null);
            fireChatStateNotificationsEvent(event);
        }
    }

    private Object getChatDescriptor(BareJid bareJid)
    {
        Object chatDescriptor = null;

        OperationSetMultiUserChat mucOpSet = parentProvider.getOperationSet(OperationSetMultiUserChat.class);
        if (mucOpSet != null) {
            List<ChatRoom> chatRooms = mucOpSet.getCurrentlyJoinedChatRooms();
            for (ChatRoom chatRoom : chatRooms) {
                if (chatRoom.getIdentifier().equals(bareJid)) {
                    chatDescriptor = chatRoom;
                    break;
                }
            }
        }
        if (chatDescriptor == null) {
            chatDescriptor = opSetPeersPresence.findContactByJid(bareJid);
        }
        return chatDescriptor;
    }

    /**
     * The listener that we use to track chat state notifications according to XEP-0085.
     * Called by smack when the state of a chat changes.
     * Fired when the state of a chat with another user changes.
     *
     * @param state the new state of the participant.
     * @param message the message carrying the chat state.
     */
    @Override
    public void stateChanged(Chat chat, ChatState state, Message message)
    {
        Jid fromJid = message.getFrom();
        BareJid bareJid = fromJid.asBareJid();
        Timber.d("ChatState Event: %s is in '%s'", fromJid, state.name());

        Object chatDescriptor = getChatDescriptor(bareJid);

        // In private messaging we can receive errors when we left room while
        // trying to send some message (isPrivateMessagingAddress == false)
        if ((chatDescriptor == null) && (message.getError() != null)) {
            // create the volatile contact from new source contact
            if (message.getType() != Message.Type.groupchat) {
                chatDescriptor = opSetPeersPresence.createVolatileContact(bareJid, false);
            }
        }

        // Must not pass in a null descriptor to ChatStateNotificationEvent() => IllegalArgumentException (FFR)
        if (chatDescriptor == null) {
            chatDescriptor = bareJid;
        }

        ChatStateNotificationEvent event = new ChatStateNotificationEvent(chatDescriptor, state, message);
        if (message.getError() != null)
            fireChatStateNotificationsDeliveryFailedEvent(event);
        else {
            // Invalid the last thread associated with the contact when he is gone
            if (state == ChatState.gone)
                opSetBasicIM.purgeGoneJidThreads(bareJid);
            else if (state == ChatState.active)
                MetaContactChatTransport.setChatStateSupport(true);

            fireChatStateNotificationsEvent(event);
        }
    }
}
