/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat.conference;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.muc.MUCService;
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
 * An implementation of <tt>ChatSession</tt> for conference chatting.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Valentin Martinet
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class ConferenceChatSession extends ChatSession implements ChatRoomMemberPresenceListener,
        ChatRoomPropertyChangeListener, ChatRoomConferencePublishedListener
{
    /**
     * The current chat transport used for messaging.
     */
    private ChatTransport currentChatTransport;

    /**
     * The chat room wrapper, which is the descriptor of this chat session.
     */
    private final ChatRoomWrapper chatRoomWrapper;

    /**
     * The object used for rendering.
     */
    private final ChatPanel sessionRenderer;

    /**
     * Creates an instance of <tt>ConferenceChatSession</tt>, by specifying the sessionRenderer to
     * be used for communication with the UI and the chatRoom corresponding to this
     * conference session.
     *
     * @param chatPanel the renderer to be used for communication with the UI.
     * @param chatRoomWrapper the chat room corresponding to this conference session.
     */

    public ConferenceChatSession(ChatPanel chatPanel, ChatRoomWrapper chatRoomWrapper)
    {
        this.sessionRenderer = chatPanel;
        this.chatRoomWrapper = chatRoomWrapper;

        currentChatTransport = new ConferenceChatTransport(this, chatRoomWrapper.getChatRoom());
        chatTransports.add(currentChatTransport);
        synchronized (this.chatParticipants) {
            this.initChatParticipants();
        }

        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        chatRoom.addMemberPresenceListener(this);
        chatRoom.addPropertyChangeListener(this);
        chatRoom.addConferencePublishedListener(this);
    }

    /**
     * Returns the descriptor of this chat session.
     *
     * @return the descriptor i.e. ChatRoomWrapper of this chat session.
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
        return chatRoomWrapper.getChatRoomID();
    }

    /**
     * Disposes this chat session.
     */
    @Override
    public void dispose()
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        chatRoom.removeMemberPresenceListener(this);
        chatRoom.removePropertyChangeListener(this);
        chatRoom.removeConferencePublishedListener(this);

        if (ConfigurationUtils.isLeaveChatRoomOnWindowCloseEnabled()) {
            chatRoom.leave();
        }
    }

    /**
     * Returns the entityJid of the chat room.
     *
     * @return the entityJid of the chat room.
     */
    @Override
    public String getChatEntity()
    {
        return chatRoomWrapper.getChatRoomName();
    }

    /**
     * Returns the subject of the chat room.
     *
     * @return the subject of the chat room.
     */
    public String getChatSubject()
    {
        return chatRoomWrapper.getChatRoom().getSubject();
    }

    /**
     * Returns the configuration form corresponding to the chat room.
     *
     * @return the configuration form corresponding to the chat room.
     * @throws OperationFailedException if no configuration form is available for the chat room.
     */
    public ChatRoomConfigurationForm getChatConfigurationForm()
            throws OperationFailedException, InterruptedException
    {
        return chatRoomWrapper.getChatRoom().getConfigurationForm();
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

        return metaHistory.findLast(chatHistoryFilter, chatRoomWrapper.getChatRoom(),
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

        return metaHistory.findLastMessagesBefore(chatHistoryFilter, chatRoomWrapper.getChatRoom(), date, count);
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

        return metaHistory.findFirstMessagesAfter(chatHistoryFilter, chatRoomWrapper.getChatRoom(),
                date, ConfigurationUtils.getChatHistorySize());
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
                chatRoomWrapper.getChatRoom(), new Date(0), 1);
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
                chatRoomWrapper.getChatRoom(), new Date(Long.MAX_VALUE), 1);
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
        fireCurrentChatTransportChange();
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
     * Invoked when <tt>ChatRoomMemberPresenceChangeEvent</tt> is received. When a new
     * <tt>ChatRoomMember</tt> has joined the chat, adds it to the list of chat participants.
     * When a <tt>ChatRoomMember</tt> has left or quit, or has being kicked it's removed from the
     * chat window.
     *
     * @param evt the <tt>ChatRoomMemberPresenceChangeEvent</tt> that notified us
     */
    public void memberPresenceChanged(final ChatRoomMemberPresenceChangeEvent evt)
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            ChatRoom sourceChatRoom = evt.getChatRoom();
            ChatRoom room = chatRoomWrapper.getChatRoom();
            // return if this is not for this chatRoomWrapper session
            if (!sourceChatRoom.equals(room))
                return;

            String eventType = evt.getEventType();
            ChatRoomMember chatRoomMember = evt.getChatRoomMember();
            // Timber.d("ChatRoom member: %s (%s)", chatRoomMember.getContactAddress(), chatRoomMember.getNickName());

            String statusMessage = null;
            if (eventType.equals(ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED)) {
                ConferenceChatContact chatContact = new ConferenceChatContact(chatRoomMember);

                // Check if not ever present in the chat room. In some cases, the considered
                // chatRoom member may appear twice in the chat contact list panel.
                synchronized (chatParticipants) {
                    if (!chatParticipants.contains(chatContact))
                        chatParticipants.add(chatContact);
                }
                // cmeng: seems to do nothing for private contact presence in chatRoom
                room.updatePrivateContactPresenceStatus(chatRoomMember);

                /*
                 * When the whole list of members of a given chat room is reported, it doesn't
                 * make sense to see "ChatContact has joined #ChatRoom" for all of them one
                 * after the other. Such an event occurs not because the ChatContact has
                 * joined after us but rather she was there before us.
                 */
                if (!evt.isReasonUserList()) {
                    statusMessage = aTalkApp.getResString(
                            R.string.service_gui_CHATROOM_USER_JOINED, sourceChatRoom.getName());
                    sessionRenderer.updateChatContactStatus(chatContact, statusMessage);
                }
            }
            else if (eventType.equals(ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT)
                    || eventType.equals(ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED)
                    || eventType.equals(ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT)) {
                switch (eventType) {
                    case ChatRoomMemberPresenceChangeEvent.MEMBER_LEFT:
                        statusMessage = aTalkApp.getResString(
                                R.string.service_gui_CHATROOM_USER_LEFT, sourceChatRoom.getName());
                        break;
                    case ChatRoomMemberPresenceChangeEvent.MEMBER_KICKED:
                        statusMessage = aTalkApp.getResString(
                                R.string.service_gui_CHATROOM_USER_KICKED, sourceChatRoom.getName());
                        break;
                    case ChatRoomMemberPresenceChangeEvent.MEMBER_QUIT:
                        statusMessage = aTalkApp.getResString(
                                R.string.service_gui_CHATROOM_USER_QUIT, sourceChatRoom.getName());
                        break;
                }

                ChatContact<?> contact = null;
                for (ChatContact<?> chatContact : chatParticipants) {
                    if (chatContact.getDescriptor().equals(chatRoomMember)) {
                        room.updatePrivateContactPresenceStatus(chatRoomMember);
                        contact = chatContact;
                        break;
                    }
                }

                // If contact found, remove from chat participants.
                if (contact != null) {
                    synchronized (chatParticipants) {
                        chatParticipants.remove(contact);
                        sessionRenderer.updateChatContactStatus(contact, statusMessage);
                    }
                }
            }
        });
    }

    public void chatRoomPropertyChangeFailed(ChatRoomPropertyChangeFailedEvent event)
    {
    }

    /**
     * Updates the chat panel when a property of the chat room has been modified.
     *
     * @param evt the event containing information about the property change
     */
    public void chatRoomPropertyChanged(ChatRoomPropertyChangeEvent evt)
    {
        if (evt.getPropertyName().equals(ChatRoomPropertyChangeEvent.CHAT_ROOM_SUBJECT)) {
            sessionRenderer.setChatSubject((String) evt.getNewValue(), (String) evt.getOldValue());
        }
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
        return true;
    }

    /**
     * Loads the given chat room in the this chat conference panel. Loads all members and adds all
     * corresponding listeners.
     *
     * @param chatRoom the <tt>ChatRoom</tt> to load
     */
    public void loadChatRoom(ChatRoom chatRoom)
    {
        // Re-init the chat transport, as we have a new chat room object.
        currentChatTransport = new ConferenceChatTransport(this, chatRoomWrapper.getChatRoom());
        chatTransports.clear();
        chatTransports.add(currentChatTransport);

        synchronized (this.chatParticipants) {
            // Remove all existing contacts.
            this.chatParticipants.clear();
            // Add the new list of members.
            for (ChatRoomMember member : chatRoom.getMembers()) {
                ConferenceChatContact contact = new ConferenceChatContact(member);
                chatParticipants.add(contact);
            }
        }
        // Add all listeners to the new chat room.
        chatRoom.addPropertyChangeListener(this);
        chatRoom.addMemberPresenceListener(this);

        // Load the subject of the chat room.
        sessionRenderer.setChatSubject(chatRoom.getSubject(), null);
    }

    /**
     * Implements the <tt>ChatPanel.getChatStatusIcon</tt> method.
     *
     * @return the status icon corresponding to this chat room
     */
    @Override
    public byte[] getChatStatusIcon()
    {
        PresenceStatus status = GlobalStatusEnum.OFFLINE;
        if (chatRoomWrapper.getChatRoom() != null && chatRoomWrapper.getChatRoom().isJoined())
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
     * Initializes the list of participants.(It is assumed that <tt>chatParticipants</tt> is
     * locked.)
     */
    private void initChatParticipants()
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if ((chatRoom != null) && chatRoom.isJoined()) {
            for (ChatRoomMember member : chatRoom.getMembers())
                chatParticipants.add(new ConferenceChatContact(member));
        }
    }

    /**
     * Indicates if the contact list is supported by this session. The contact list would be
     * supported for all non system and non private
     * sessions.
     *
     * @return <tt>true</tt> to indicate that the contact list is supported, <tt>false</tt>
     * otherwise.
     */
    @Override
    public boolean isContactListSupported()
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        return !chatRoom.isSystem() && !MUCService.isPrivate(chatRoom);
    }

    /**
     * Adds the given <tt>ChatRoomMemberRoleListener</tt> to the contained chat room role
     * listeners.
     *
     * @param l the listener to add
     */
    public void addMemberRoleListener(ChatRoomMemberRoleListener l)
    {
        chatRoomWrapper.getChatRoom().addMemberRoleListener(l);
    }

    /**
     * Adds the given <tt>ChatRoomLocalUserRoleListener</tt> to the contained chat room role
     * listeners.
     *
     * @param l the listener to add
     */
    public void addLocalUserRoleListener(ChatRoomLocalUserRoleListener l)
    {
        chatRoomWrapper.getChatRoom().addLocalUserRoleListener(l);
    }

    /**
     * Removes the given <tt>ChatRoomMemberRoleListener</tt> from the contained chat room role
     * listeners.
     *
     * @param l the listener to remove
     */
    public void removeMemberRoleListener(ChatRoomMemberRoleListener l)
    {
        chatRoomWrapper.getChatRoom().removeMemberRoleListener(l);
    }

    /**
     * Removes the given <tt>ChatRoomLocalUserRoleListener</tt> from the contained chat room role
     * listeners.
     *
     * @param l the listener to remove
     */
    public void removeLocalUserRoleListener(ChatRoomLocalUserRoleListener l)
    {
        chatRoomWrapper.getChatRoom().removeLocalUserRoleListener(l);
    }

    /**
     * Acts upon a <tt>ChatRoomConferencePublishedEvent</tt>, dispatched when a member of a chat
     * room publishes a <tt>ConferenceDescription</tt>.
     *
     * @param evt the event received, which contains the <tt>ChatRoom</tt>, <tt>ChatRoomMember</tt> and
     * <tt>ConferenceDescription</tt> involved.
     */
    public void conferencePublished(final ChatRoomConferencePublishedEvent evt)
    {
        (new Activity()).runOnUiThread(() -> {
            ChatRoom room = evt.getChatRoom();
            if (!room.equals(chatRoomWrapper.getChatRoom()))
                return;

            ConferenceDescription cd = evt.getConferenceDescription();
            if (evt.getType() == ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_SENT) {
                // sessionRenderer.chatConferenceDescriptionSent(cd);
            }
            else if (evt.getType() == ChatRoomConferencePublishedEvent.CONFERENCE_DESCRIPTION_RECEIVED) {
                updateChatConferences(room, evt.getMember(), cd, room.getCachedConferenceDescriptionSize());
            }
        });
    }

    /**
     * Adds/Removes the announced conference to the interface.
     *
     * @param chatRoom the chat room where the conference is announced.
     * @param chatRoomMember the chat room member who announced the conference.
     * @param cd the <tt>ConferenceDescription</tt> instance which represents the conference.
     */
    private void updateChatConferences(ChatRoom chatRoom, ChatRoomMember chatRoomMember,
            ConferenceDescription cd, int activeConferencesCount)
    {
        boolean isAvailable = cd.isAvailable();
        for (ChatContact<?> chatContact : chatParticipants) {
            if (chatContact.getDescriptor().equals(chatRoomMember)) {
                /*
                 * TODO: we want more things to happen, e.g. the ConferenceDescription being added to a list in the GUI
                 * TODO: i13ze the string, if we decide to keep it at all
                 */
                sessionRenderer.updateChatContactStatus(chatContact,
                        (isAvailable ? "published" : "removed") + " a conference " + cd);
                break;
            }
        }

        if (isAvailable) {
            // sessionRenderer.addChatConferenceCall(cd);
            if (activeConferencesCount == 1) {
                // sessionRenderer.setConferencesPanelVisible(true);
            }
        }
        else {
            // sessionRenderer.removeChatConferenceCall(cd);
            if (activeConferencesCount == 0) {
                // sessionRenderer.setConferencesPanelVisible(false);
            }
        }
    }
}
