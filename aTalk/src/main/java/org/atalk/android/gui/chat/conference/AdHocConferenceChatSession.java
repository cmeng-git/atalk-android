/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.chat.conference;

import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.*;

import java.util.*;

/**
 * An implementation of <tt>ChatSession</tt> for ad-hoc conference chatting.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
public class AdHocConferenceChatSession extends ChatSession implements AdHocChatRoomParticipantPresenceListener
{
    private ChatTransport currentChatTransport;
    private final AdHocChatRoomWrapper chatRoomWrapper;
    private final ChatPanel sessionRenderer;

    /**
     * Creates an instance of <tt>AdHocConferenceChatSession</tt>, by specifying the
     * sessionRenderer to be used for communication with the UI and the ad-hoc chat room
     * corresponding to this conference session.
     *
     * @param sessionRenderer the renderer to be used for communication with the UI.
     * @param chatRoomWrapper the ad-hoc chat room corresponding to this conference session.
     */
    public AdHocConferenceChatSession(ChatPanel sessionRenderer, AdHocChatRoomWrapper chatRoomWrapper)
    {
        this.sessionRenderer = sessionRenderer;
        this.chatRoomWrapper = chatRoomWrapper;

        this.currentChatTransport = new AdHocConferenceChatTransport(this, chatRoomWrapper.getAdHocChatRoom());
        chatTransports.add(currentChatTransport);
        this.initChatParticipants();

        AdHocChatRoom chatRoom = chatRoomWrapper.getAdHocChatRoom();
        chatRoom.addParticipantPresenceListener(this);
    }

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor of this chat session.
     */
    @Override
    public Object getDescriptor()
    {
        return chatRoomWrapper;
    }

    /**
     * Returns the chat identifier.
     *
     * @return the chat identifier
     */
    public String getChatId()
    {
        return chatRoomWrapper.getAdHocChatRoomID();
    }

    /**
     * Disposes this chat session.
     */
    @Override
    public void dispose()
    {
        AdHocChatRoom chatRoom = chatRoomWrapper.getAdHocChatRoom();
        chatRoom.removeParticipantPresenceListener(this);
    }

    /**
     * Returns the entityJid of the ad-hoc chat room.
     *
     * @return the entityJid of the ad-hoc chat room.
     */
    @Override
    public String getChatEntity()
    {
        return chatRoomWrapper.getAdHocChatRoomName();
    }

    /**
     * Returns the configuration form corresponding to the chat room.
     *
     * @return the configuration form corresponding to the chat room.
     * @throws OperationFailedException if no configuration form is available for the chat room.
     */
    public ChatRoomConfigurationForm getChatConfigurationForm()
            throws OperationFailedException
    {
        return null;
    }

    /**
     * Returns the currently used transport for all operation within this chat session.
     *
     * @return the currently used transport for all operation within this chat session.
     */
    @Override
    public ChatTransport getCurrentChatTransport()
    {
        return currentChatTransport;
    }

    /**
     * Returns the default mobile number used to send sms-es in this session. In the case of
     * conference this is for now null.
     *
     * @return the default mobile number used to send sms-es in this session.
     */
    @Override
    public String getDefaultSmsNumber()
    {
        return null;
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    @Override
    public Collection<Object> getHistory(int count)
    {
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return null;

        return metaHistory.findLast(chatHistoryFilter, chatRoomWrapper.getAdHocChatRoom(),
                ConfigurationUtils.getChatHistorySize());
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param date The date up to which we're looking for messages.
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    @Override
    public Collection<Object> getHistoryBeforeDate(Date date, int count)
    {
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return null;

        return metaHistory.findLastMessagesBefore(chatHistoryFilter,
                chatRoomWrapper.getAdHocChatRoom(), date, ConfigurationUtils.getChatHistorySize());
    }

    /**
     * Returns a collection of the last N number of messages given by count.
     *
     * @param date The date from which we're looking for messages.
     * @param count The number of messages from history to return.
     * @return a collection of the last N number of messages given by count.
     */
    @Override
    public Collection<Object> getHistoryAfterDate(Date date, int count)
    {
        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return null;

        return metaHistory.findFirstMessagesAfter(chatHistoryFilter,
                chatRoomWrapper.getAdHocChatRoom(), date, ConfigurationUtils.getChatHistorySize());
    }

    /**
     * Returns the start date of the history of this chat session.
     *
     * @return the start date of the history of this chat session.
     */
    @Override
    public Date getHistoryStartDate()
    {
        MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return new Date(0);

        Date startHistoryDate = new Date(0);
        Collection<Object> firstMessage = metaHistory.findFirstMessagesAfter(chatHistoryFilter,
                chatRoomWrapper.getAdHocChatRoom(), new Date(0), 1);
        if (firstMessage.size() > 0) {
            Iterator<Object> i = firstMessage.iterator();

            Object o = i.next();
            if (o instanceof MessageDeliveredEvent) {
                MessageDeliveredEvent evt = (MessageDeliveredEvent) o;
                startHistoryDate = evt.getTimestamp();
            }
            else if (o instanceof MessageReceivedEvent) {
                MessageReceivedEvent evt = (MessageReceivedEvent) o;
                startHistoryDate = evt.getTimestamp();
            }
        }
        return startHistoryDate;
    }

    /**
     * Returns the end date of the history of this chat session.
     *
     * @return the end date of the history of this chat session.
     */
    @Override
    public Date getHistoryEndDate()
    {
        MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return new Date(0);

        Date endHistoryDate = new Date(0);
        Collection<Object> lastMessage = metaHistory.findLastMessagesBefore(chatHistoryFilter,
                chatRoomWrapper.getAdHocChatRoom(), new Date(Long.MAX_VALUE), 1);

        if (lastMessage.size() > 0) {
            Iterator<Object> i1 = lastMessage.iterator();

            Object o1 = i1.next();
            if (o1 instanceof MessageDeliveredEvent) {
                MessageDeliveredEvent evt = (MessageDeliveredEvent) o1;
                endHistoryDate = evt.getTimestamp();
            }
            else if (o1 instanceof MessageReceivedEvent) {
                MessageReceivedEvent evt = (MessageReceivedEvent) o1;
                endHistoryDate = evt.getTimestamp();
            }
        }
        return endHistoryDate;
    }

    /**
     * Sets the transport that will be used for all operations within this chat session.
     *
     * @param chatTransport The transport to set as a default transport for this session.
     */
    @Override
    public void setCurrentChatTransport(ChatTransport chatTransport)
    {
        this.currentChatTransport = chatTransport;
    }

    /**
     * Sets the default mobile number used to send sms-es in this session.
     *
     * @param smsPhoneNumber The default mobile number used to send sms-es in this session.
     */
    @Override
    public void setDefaultSmsNumber(String smsPhoneNumber)
    {
    }

    /**
     * Returns the <tt>ChatSessionRenderer</tt> that provides the connection between this chat
     * session and its UI.
     *
     * @return The <tt>ChatSessionRenderer</tt>.
     */
    @Override
    public ChatPanel getChatSessionRenderer()
    {
        return sessionRenderer;
    }

    /**
     * Returns {@code true} if this contact is persistent, otherwise returns
     * {@code false}.
     *
     * @return {@code true} if this contact is persistent, otherwise returns
     * {@code false}.
     */
    @Override
    public boolean isDescriptorPersistent()
    {
        return false;
    }

    /**
     * Loads the given chat room in the this chat conference panel. Loads all members and adds all
     * corresponding listeners.
     *
     * @param chatRoom the <tt>ChatRoom</tt> to load
     */
    public void loadChatRoom(AdHocChatRoom chatRoom)
    {
        for (Contact contact : chatRoom.getParticipants())
            // sessionRenderer.addChatContact(new AdHocConferenceChatContact(contact));
            chatRoom.addParticipantPresenceListener(this);
    }

    /**
     * Implements the <tt>ChatPanel.getChatStatusIcon</tt> method.
     *
     * @return the status icon corresponding to this ad-hoc chat room
     */
    @Override
    public byte[] getChatStatusIcon()
    {
        PresenceStatus status = GlobalStatusEnum.OFFLINE;
        if (chatRoomWrapper.getAdHocChatRoom() != null)
            status = GlobalStatusEnum.ONLINE;

        return status.getStatusIcon();
    }

    /**
     * Returns the avatar icon of this chat session.
     *
     * @return the avatar icon of this chat session.
     */
    @Override
    public byte[] getChatAvatar()
    {
        return null;
    }

    /**
     * Initializes the list of participants.
     */
    private void initChatParticipants()
    {
        AdHocChatRoom chatRoom = chatRoomWrapper.getAdHocChatRoom();

        if (chatRoom != null) {
            for (Contact contact : chatRoom.getParticipants()) {
                chatParticipants.add(new AdHocConferenceChatContact(contact));
            }
        }
    }

    /* Implements ChatSession#isContactListSupported(). */
    @Override
    public boolean isContactListSupported()
    {
        return true;
    }

    /**
     * Invoked when <tt>AdHocChatRoomParticipantPresenceChangeEvent</tt> are received. When a new
     * participant (<tt>Contact</tt>) has joined the chat adds it to the list of chat
     * participants on the right of the chat window. When a participant has left or quit it's
     * removed from the chat window.
     */
    public void participantPresenceChanged(AdHocChatRoomParticipantPresenceChangeEvent evt)
    {
        AdHocChatRoom sourceChatRoom = evt.getAdHocChatRoom();
        if (!sourceChatRoom.equals(chatRoomWrapper.getAdHocChatRoom()))
            return;

        String eventType = evt.getEventType();
        Contact participant = evt.getParticipant();
        String statusMessage = null;

        if (eventType.equals(AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_JOINED)) {
            AdHocConferenceChatContact chatContact = new AdHocConferenceChatContact(participant);
            chatParticipants.add(chatContact);

            /*
             * When the whole list of members of a given chat room is reported, it doesn't make
             * sense to see "ChatContact has joined #ChatRoom" for all of them one after the
             * other. Such an event occurs not because the ChatContact has joined after us but
             * rather she was there before us.
             */
            if (!evt.isReasonUserList()) {
                statusMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_USER_JOINED, sourceChatRoom.getName());
                sessionRenderer.updateChatContactStatus(chatContact, statusMessage);
            }
        }
        else if (eventType.equals(AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_LEFT)
                || eventType.equals(AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_QUIT)) {
            if (eventType.equals(AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_LEFT)) {
                statusMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_USER_LEFT, sourceChatRoom.getName());
            }
            else if (eventType.equals(AdHocChatRoomParticipantPresenceChangeEvent.CONTACT_QUIT)) {
                statusMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_USER_QUIT, sourceChatRoom.getName());
            }

            for (ChatContact<?> chatContact : chatParticipants) {
                if (chatContact.getDescriptor().equals(participant)) {
                    sessionRenderer.updateChatContactStatus(chatContact, statusMessage);
                    break;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     */
    @Override
    public void addChatTransportChangeListener(ChatSessionChangeListener l)
    {
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     */
    @Override
    public void removeChatTransportChangeListener(ChatSessionChangeListener l)
    {
    }
}
