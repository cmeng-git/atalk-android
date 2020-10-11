/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat.conference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.text.TextUtils;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl;
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.LauncherActivity;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.chatroomslist.*;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.osgi.framework.Bundle;
import org.osgi.framework.*;

import java.util.*;
import java.util.concurrent.ExecutionException;

import timber.log.Timber;


/**
 * The <tt>ConferenceChatManager</tt> is the one that manages both chat room and ad-hoc chat rooms invitations.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Valentin Martinet
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class ConferenceChatManager implements ChatRoomMessageListener, ChatRoomInvitationListener,
        AdHocChatRoomMessageListener, AdHocChatRoomInvitationListener,
        LocalUserChatRoomPresenceListener, LocalUserAdHocChatRoomPresenceListener,
        ServiceListener, ChatRoomLocalUserRoleListener
{
    /**
     * The list of ad-hoc chat rooms.
     */
    private final AdHocChatRoomList adHocChatRoomList = new AdHocChatRoomList();

    private final ConferenceChatManager multiUserChatManager;

    /**
     * A list of all <tt>AdHocChatRoomListChangeListener</tt>-s.
     */
    private final Vector<AdHocChatRoomListChangeListener> adHoclistChangeListeners = new Vector<>();

    /**
     * Creates an instance of <tt>ConferenceChatManager</tt>.
     */
    public ConferenceChatManager()
    {
        multiUserChatManager = this;
        // Loads the chat rooms list in a separate thread.
        new Thread()
        {
            @Override
            public void run()
            {
                adHocChatRoomList.loadList();
            }
        }.start();
        AndroidGUIActivator.bundleContext.addServiceListener(this);
    }

    /**
     * Returns all chat room providers currently contained in the ad-hoc chat room list.
     *
     * @return all chat room providers currently contained in the ad-hoc chat room list.
     */
    public AdHocChatRoomList getAdHocChatRoomList()
    {
        return adHocChatRoomList;
    }

    public void invitationReceived(ChatRoomInvitationReceivedEvent evt)
    {
        final OperationSetMultiUserChat multiUserChatOpSet = evt.getSourceOperationSet();
        final ChatRoomInvitation invitation = evt.getInvitation();

        // Wake aTalk to show invitation dialog
        if (!aTalkApp.isForeground) {
            Context context = aTalkApp.getGlobalContext();
            Intent i = new Intent(context, LauncherActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);

            Timber.d("Receive invitation while aTalk is in background");
            NotificationManager.fireNotification(NotificationManager.INCOMING_INVITATION);
        }

        // Event thread - Must execute in UiThread for dialog
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = aTalkApp.waitForFocus();
            if (activity != null) {
                InvitationReceivedDialog dialog
                        = new InvitationReceivedDialog(activity, multiUserChatManager, multiUserChatOpSet, invitation);
                dialog.show();
            }
            else {
                // cmeng - auto accept and join room.
                // Set setCurrentChatId to null after joined, so incomingMessage pop-message is active
                try {
                    Timber.d("Receive invitation with waitForFocus failed, so auto-joined!");
                    ChatRoomJabberImpl chatRoom = (ChatRoomJabberImpl) invitation.getTargetChatRoom();
                    chatRoom.join();
                    ChatSessionManager.setCurrentChatId(null);
                    chatRoom.addMessage(aTalkApp.getResString(R.string.service_gui_JOIN_AUTOMATICALLY),
                            ChatMessage.MESSAGE_SYSTEM);
                } catch (OperationFailedException e) {
                    Timber.w("Auto join group chat failed!");
                }
            }
        });
    }

    /**
     * Implements the <tt>ChatRoomMessageListener.messageDelivered</tt> method. <br>
     * Shows the message in the conversation area and clears the write message area.
     *
     * @param evt the <tt>ChatRoomMessageDeliveredEvent</tt> that notified us that the message was
     * delivered to its destination
     */
    public void messageDelivered(ChatRoomMessageDeliveredEvent evt)
    {
        ChatRoom sourceChatRoom = evt.getSourceChatRoom();
        Timber.log(TimberLog.FINER, "MESSAGE DELIVERED to chat room: %s", sourceChatRoom.getName());

        ChatPanel chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, false);
        if (chatPanel != null) {
            IMessage message = evt.getMessage();
            // just return if the delivered message is for remote client consumption only
            if (message.isRemoteOnly())
                return;

            int messageType = evt.getEventType();
            chatPanel.addMessage(sourceChatRoom.getUserNickname().toString(), null,
                    evt.getTimestamp(), messageType, message, null);
        }
    }

    /**
     * Implements the <tt>ChatRoomMessageListener.messageReceived</tt> method. <br>
     * Obtains the corresponding <tt>ChatPanel</tt> and process the message there.
     *
     * @param evt the <tt>ChatRoomMessageReceivedEvent</tt> that notified us that a message has been received
     */
    public void messageReceived(ChatRoomMessageReceivedEvent evt)
    {
        ChatRoom sourceChatRoom = evt.getSourceChatRoom();
        ChatRoomMember sourceMember = evt.getSourceChatRoomMember();
        int messageType = evt.getEventType();
        IMessage message = evt.getMessage();
        ChatPanel chatPanel;

        boolean createWindow = false;
        String autoOpenConfig
                = MUCService.getChatRoomAutoOpenOption(sourceChatRoom.getParentProvider(), sourceChatRoom.getName());
        if (autoOpenConfig == null)
            autoOpenConfig = MUCService.DEFAULT_AUTO_OPEN_BEHAVIOUR;

        if (autoOpenConfig.equals(MUCService.OPEN_ON_ACTIVITY)
                || (autoOpenConfig.equals(MUCService.OPEN_ON_MESSAGE) && !evt.isHistoryMessage())
                || evt.isImportantMessage())
            createWindow = true;

        if (sourceChatRoom.isSystem()) {
            ChatRoomProviderWrapper serverWrapper
                    = MUCActivator.getMUCService().findServerWrapperFromProvider(sourceChatRoom.getParentProvider());
            chatPanel = ChatSessionManager.getMultiChat(serverWrapper.getSystemRoomWrapper(), createWindow);
        }
        else {
            chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, createWindow, message.getMessageUID());
        }
        if (chatPanel == null)
            return;

        String messageContent = message.getContent();
        if (evt.isHistoryMessage()) { // cmeng: need to check since it always start with new ?????
            Date timeStamp = new Date();
            // chatPanel.getChatConversationPanel().getLastIncomingMsgTimestamp();
            Collection<Object> c = chatPanel.getChatSession().getHistoryBeforeDate(
                    new Date(timeStamp.equals(new Date(0))
                            ? System.currentTimeMillis() - 10000 : timeStamp.getTime()), 20);
            boolean hasMatch = false;
            for (Object o : c) {
                // cmeng: never match and should be implemented in ChatRoomMessageDeliveredEvent
                if (o instanceof ChatRoomMessageDeliveredEvent) {
                    ChatRoomMessageDeliveredEvent ev = (ChatRoomMessageDeliveredEvent) o;
                    if ((evt.getTimestamp() != null) && evt.getTimestamp().equals(ev.getTimestamp())) {
                        hasMatch = true;
                        break;
                    }
                }
                else if (o instanceof ChatRoomMessageReceivedEvent) {
                    ChatRoomMessageReceivedEvent ev = (ChatRoomMessageReceivedEvent) o;
                    if ((evt.getTimestamp() != null) && evt.getTimestamp().equals(ev.getTimestamp())) {
                        hasMatch = true;
                        break;
                    }
                }
                IMessage m2 = evt.getMessage();
                if (m2 != null && m2.getContent().equals(messageContent)) {
                    hasMatch = true;
                    break;
                }
            }
            // skip if the message is an old history previously received
            if (hasMatch)
                return;
        }

        // contact may be null if message received with nickName only or when contact reject invitation
        // Contact contact = sourceMember.getContact();
        // String jabberID = (contact == null) ? displayName : contact.getAddress();
        String jabberID = sourceMember.getContactAddress();
        String displayName = sourceMember.getNickName();

        chatPanel.addMessage(jabberID, displayName, evt.getTimestamp(), messageType, message, null);
    }

    /**
     * Implements the <tt>ChatRoomMessageListener.messageDeliveryFailed</tt> method. <br>
     * In the conversation area shows an error message, explaining the problem.
     *
     * @param evt the <tt>ChatRoomMessageDeliveryFailedEvent</tt> that notified us of a delivery failure
     */
    public void messageDeliveryFailed(ChatRoomMessageDeliveryFailedEvent evt)
    {
        /*
         * FIXME ChatRoomMessageDeliveryFailedEvent#getSource() is not a IMessage instance at the
         * time of this writing and the attempt "(IMessage) evt.getSource()" seems to be to
         * get the message which failed to be delivered. I'm not sure it's
         * ChatRoomMessageDeliveryFailedEvent#getMessage() but since it's the only message I can
         * get out of ChatRoomMessageDeliveryFailedEvent, I'm using it.
         */

        // Just show the pass in error message if false
        boolean mergeMessage = true;
        boolean resendLastMessage = true;
        String errorMsg;
        String reason = evt.getReason();

        ChatRoom sourceChatRoom = evt.getSourceChatRoom();
        IMessage srcMessage = evt.getMessage();
        ChatRoomMember destMember = evt.getDestinationChatRoomMember();

        switch (evt.getErrorCode()) {
            case MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED, destMember.getNickName());
                break;
            case MessageDeliveryFailedEvent.NETWORK_FAILURE:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED);
                break;
            case MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
                break;
            case MessageDeliveryFailedEvent.INTERNAL_ERROR:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR);
                break;
            case MessageDeliveryFailedEvent.FORBIDDEN:
                errorMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_SEND_MSG_FORBIDDEN);
                break;
            case MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION:
                errorMsg = aTalkApp.getResString(R.string.service_gui_UNSUPPORTED_OPERATION);
                break;
            case MessageDeliveryFailedEvent.OMEMO_SEND_ERROR:
            case MessageDeliveryFailedEvent.NOT_ACCEPTABLE:
                errorMsg = evt.getReason();
                mergeMessage = false;
                break;

            case MessageDeliveryFailedEvent.SYSTEM_ERROR_MESSAGE:
                resendLastMessage = false;
            default:
                if (TextUtils.isEmpty(reason))
                    errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_UNKNOWN_ERROR);
                else {
                    errorMsg = reason;
                    mergeMessage = false;
                }
        }

        if (!TextUtils.isEmpty(reason) && mergeMessage)
            errorMsg += " " + aTalkApp.getResString(R.string.service_gui_ERROR_WAS, reason);

        // Error message sent from conference has no nickName i.e. contains ""
        String sender = ((destMember == null) || TextUtils.isEmpty(destMember.getNickName()))
                ? sourceChatRoom.getName() : destMember.getNickName();
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, true);

        if (resendLastMessage) {
            chatPanel.addMessage(sender, new Date(), ChatMessage.MESSAGE_OUT, srcMessage.getMimeType(),
                    srcMessage.getContent());
        }
        chatPanel.addMessage(sender, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg);

        ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
    }

    /**
     * Implements the <tt>LocalUserAdHocChatRoomPresenceListener.localUserPresenceChanged</tt> method
     *
     * @param evt the <tt>LocalUserAdHocChatRoomPresenceChangeEvent</tt> that notified us of a presence change
     */
    public void localUserAdHocPresenceChanged(LocalUserAdHocChatRoomPresenceChangeEvent evt)
    {
        AdHocChatRoom sourceAdHocChatRoom = evt.getAdHocChatRoom();
        AdHocChatRoomWrapper adHocChatRoomWrapper
                = adHocChatRoomList.findChatRoomWrapperFromAdHocChatRoom(sourceAdHocChatRoom);

        String eventType = evt.getEventType();

        if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(eventType)) {
            if (adHocChatRoomWrapper != null) {
                this.fireAdHocChatRoomListChangedEvent(adHocChatRoomWrapper,
                        AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_CHANGED);

                // Check if we have already opened a chat window for this chat wrapper and load
                // the real chat room corresponding to the wrapper.
                ChatPanel chatPanel = ChatSessionManager.getMultiChat(adHocChatRoomWrapper, true);
                // cmeng - below check is not necessary since above will do them all ???
                if (chatPanel.isChatFocused()) {
                    ((AdHocConferenceChatSession) chatPanel.getChatSession()).loadChatRoom(sourceAdHocChatRoom);
                }
                else {
                    // ChatSessionManager.openChat(chatPanel, true);
                    ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
                }
            }
            sourceAdHocChatRoom.addMessageListener(this);
        }
        else if (evt.getEventType().equals(LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOIN_FAILED)) {
            AndroidGUIActivator.getAlertUIService().showAlertPopup(aTalkApp.getResString(R.string.service_gui_ERROR),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REASON,
                            sourceAdHocChatRoom.getName(), evt.getReason()));
        }
        else if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_LEFT.equals(eventType)
                || LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED.equals
                (eventType)) {
            this.closeAdHocChatRoom(adHocChatRoomWrapper);

            // Need to refresh the chat room's list in order to change the state of the chat room to offline.
            fireAdHocChatRoomListChangedEvent(adHocChatRoomWrapper, AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_CHANGED);
            sourceAdHocChatRoom.removeMessageListener(this);
        }
    }

    /**
     * Implements the <tt>LocalUserChatRoomPresenceListener.localUserPresenceChanged</tt> method.
     *
     * @param evt the <tt>LocalUserChatRoomPresenceChangeEvent</tt> that notified us
     */
    public void localUserPresenceChanged(final LocalUserChatRoomPresenceChangeEvent evt)
    {
        ChatRoom sourceChatRoom = evt.getChatRoom();
        ChatRoomWrapper chatRoomWrapper = MUCActivator.getMUCService().findChatRoomWrapperFromChatRoom(sourceChatRoom);

        String eventType = evt.getEventType();
        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(eventType)) {
            if (chatRoomWrapper != null) {
                MUCActivator.getMUCService().fireChatRoomListChangedEvent(chatRoomWrapper,
                        ChatRoomListChangeEvent.CHAT_ROOM_CHANGED);

                boolean createWindow = false;
                String autoOpenConfig = MUCService.getChatRoomAutoOpenOption(
                        sourceChatRoom.getParentProvider(), sourceChatRoom.getName());

                if (autoOpenConfig != null && autoOpenConfig.equals(MUCService.OPEN_ON_ACTIVITY))
                    createWindow = true;

                ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, createWindow);
                // cmeng - below code may not be required since above code will create and setActive
                if (chatPanel != null) {
                    // chatPanel.setChatIcon(chatPanel.getChatStatusIcon());

                    // Check if we have already opened a chat window for this chat wrapper and
                    // load the real chat room corresponding to the wrapper.
                    if (chatPanel.isChatFocused()) {
                        ((ConferenceChatSession) chatPanel.getChatSession()).loadChatRoom(sourceChatRoom);
                    }
                    else {
                        // ChatSessionManager.openChat(chatPanel, true);
                        ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
                    }
                }
            }

            if (sourceChatRoom.isSystem()) {
                ChatRoomProviderWrapper serverWrapper = MUCActivator.getMUCService()
                        .findServerWrapperFromProvider(sourceChatRoom.getParentProvider());
                serverWrapper.setSystemRoom(sourceChatRoom);
            }
            sourceChatRoom.addMessageListener(this);
            sourceChatRoom.addLocalUserRoleListener(this);
        }
        else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOIN_FAILED.equals(eventType)) {
            AndroidGUIActivator.getAlertUIService().showAlertPopup(aTalkApp.getResString(R.string.service_gui_ERROR),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REASON,
                            sourceChatRoom.getName(), evt.getReason()));
        }
        else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT.equals(eventType)
                || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED.equals(eventType)
                || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED.equals(eventType)) {
            if (chatRoomWrapper != null) {
                if (StringUtils.isEmpty(evt.getReason())) {
                    AndroidGUIActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
                }
                else {
                    // send some system messages informing for the reason of leaving
                    ChatPanel chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, false);

                    if (chatPanel != null) {
                        chatPanel.addMessage(sourceChatRoom.getName(), new Date(), ChatMessage.MESSAGE_SYSTEM,
                                IMessage.ENCODE_PLAIN, evt.getReason());

                        // print and the alternate address
                        if (StringUtils.isNotEmpty(evt.getAlternateAddress())) {
                            chatPanel.addMessage(sourceChatRoom.getName(), new Date(),
                                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                                    aTalkApp.getResString(R.string.service_gui_CHATROOM_ALTERNATE_ADDRESS,
                                            evt.getAlternateAddress()));
                        }
                    }
                }
                // Need to refresh the chat room's list in order to change the state of the chat room to offline.
                MUCActivator.getMUCService().fireChatRoomListChangedEvent(chatRoomWrapper,
                        ChatRoomListChangeEvent.CHAT_ROOM_CHANGED);
            }
            sourceChatRoom.removeMessageListener(this);
            sourceChatRoom.removeLocalUserRoleListener(this);
        }
    }

    /**
     * Called to accept an incoming invitation. Adds the invitation chat room to the list of chat
     * rooms and joins it.
     *
     * @param invitation the invitation to accept
     * @param multiUserChatOpSet the operation set for chat conferencing
     * @throws OperationFailedException if the accept fails
     */
    public void acceptInvitation(AdHocChatRoomInvitation invitation, OperationSetAdHocMultiUserChat multiUserChatOpSet)
            throws OperationFailedException
    {
        AdHocChatRoom chatRoom = invitation.getTargetAdHocChatRoom();
        chatRoom.join();
    }

    /**
     * Rejects the given invitation with the specified reason.
     *
     * @param multiUserChatAdHocOpSet the operation set to use for rejecting the invitation
     * @param invitation the invitation to reject
     * @param reason the reason for the rejection
     */
    public void rejectInvitation(OperationSetAdHocMultiUserChat multiUserChatAdHocOpSet,
            AdHocChatRoomInvitation invitation, String reason)
    {
        multiUserChatAdHocOpSet.rejectInvitation(invitation, reason);
    }

    /**
     * Creates an ad-hoc chat room, by specifying the ad-hoc chat room name, the parent protocol
     * provider and eventually, the contacts invited to participate in this ad-hoc chat room.
     *
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for this invitation
     * @return the <tt>AdHocChatRoomWrapper</tt> corresponding to the created ad hoc chat room
     */
    public AdHocChatRoomWrapper createAdHocChatRoom(ProtocolProviderService protocolProvider,
            Collection<String> contacts, String reason)
    {
        AdHocChatRoomWrapper chatRoomWrapper = null;
        OperationSetAdHocMultiUserChat groupChatOpSet = protocolProvider
                .getOperationSet(OperationSetAdHocMultiUserChat.class);

        // If there's no group chat operation set we have nothing to do here.
        if (groupChatOpSet == null)
            return null;

        AdHocChatRoom chatRoom = null;
        try {
            List<String> members = new LinkedList<>(contacts);
            chatRoom = groupChatOpSet.createAdHocChatRoom("chatroom-" + new Date().getTime(), members, reason);
        } catch (OperationFailedException | OperationNotSupportedException ex) {
            AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                    aTalkApp.getResString(R.string.service_gui_ERROR),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_CREATE_ERROR,
                            protocolProvider.getProtocolDisplayName()));
        }

        if (chatRoom != null) {
            AdHocChatRoomProviderWrapper parentProvider
                    = adHocChatRoomList.findServerWrapperFromProvider(protocolProvider);
            chatRoomWrapper = new AdHocChatRoomWrapper(parentProvider, chatRoom);
            parentProvider.addAdHocChatRoom(chatRoomWrapper);
            adHocChatRoomList.addAdHocChatRoom(chatRoomWrapper);

            fireAdHocChatRoomListChangedEvent(chatRoomWrapper,
                    AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_ADDED);
        }
        return chatRoomWrapper;
    }

    /**
     * Joins the given ad-hoc chat room
     *
     * @param chatRoomWrapper chatRoom Wrapper
     */
    public void joinChatRoom(AdHocChatRoomWrapper chatRoomWrapper)
    {
        AdHocChatRoom chatRoom = chatRoomWrapper.getAdHocChatRoom();

        if (chatRoom == null) {
            AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                    aTalkApp.getResString(R.string.service_gui_WARNING),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED,
                            chatRoomWrapper.getAdHocChatRoomName()));
            return;
        }
        new JoinAdHocChatRoomTask(chatRoomWrapper).execute();
    }

    /**
     * Removes the given chat room from the UI.
     *
     * @param chatRoomWrapper the chat room to remove.
     */
    public void removeChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if (chatRoom != null)
            leaveChatRoom(chatRoomWrapper);

        AndroidGUIActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
        MUCActivator.getMUCService().removeChatRoom(chatRoomWrapper);

    }

    /**
     * Joins the given chat room and manages all the exceptions that could occur during the join process.
     *
     * @param chatRoom the chat room to join
     */
    public void joinChatRoom(AdHocChatRoom chatRoom)
    {
        AdHocChatRoomWrapper chatRoomWrapper = adHocChatRoomList.findChatRoomWrapperFromAdHocChatRoom(chatRoom);

        if (chatRoomWrapper == null) {
            AdHocChatRoomProviderWrapper parentProvider
                    = adHocChatRoomList.findServerWrapperFromProvider(chatRoom.getParentProvider());

            chatRoomWrapper = new AdHocChatRoomWrapper(parentProvider, chatRoom);
            adHocChatRoomList.addAdHocChatRoom(chatRoomWrapper);
            fireAdHocChatRoomListChangedEvent(chatRoomWrapper,
                    AdHocChatRoomListChangeEvent.AD_HOC_CHATROOM_ADDED);
        }
        this.joinChatRoom(chatRoomWrapper);
        // ChatSessionManager.openChat(chatWindowManager.getMultiChat(chatRoomWrapper, true), true);
        ChatSessionManager.getMultiChat(chatRoomWrapper, true);
    }

    /**
     * Leaves the given <tt>ChatRoom</tt>.
     *
     * @param chatRoomWrapper the <tt>ChatRoom</tt> to leave.
     */
    public void leaveChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        ChatRoomWrapper leavedRoomWrapped = MUCActivator.getMUCService().leaveChatRoom(chatRoomWrapper);
        if (leavedRoomWrapped != null) {
            // AndroidGUIActivator.getUIService().closeChatRoomWindow(leavedRoomWrapped);
        }
    }

    /**
     * Leaves the given <tt>ChatRoom</tt>.
     *
     * @param chatRoomWrapper the <tt>ChatRoom</tt> to leave.
     */
    public void leaveChatRoom(AdHocChatRoomWrapper chatRoomWrapper)
    {
        AdHocChatRoom chatRoom = chatRoomWrapper.getAdHocChatRoom();

        if (chatRoom != null) {
            chatRoom.leave();
        }
        else {
            AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                    R.string.service_gui_WARNING, R.string.service_gui_CHATROOM_LEAVE_NOT_CONNECTED);
        }
    }

    /**
     * Adds the given <tt>AdHocChatRoomListChangeListener</tt> that will listen for all changes of
     * the chat room list data model.
     *
     * @param l the listener to add.
     */
    public void addAdHocChatRoomListChangeListener(AdHocChatRoomListChangeListener l)
    {
        synchronized (adHoclistChangeListeners) {
            adHoclistChangeListeners.add(l);
        }
    }

    /**
     * Removes the given <tt>AdHocChatRoomListChangeListener</tt>.
     *
     * @param l the listener to remove.
     */
    public void removeAdHocChatRoomListChangeListener(AdHocChatRoomListChangeListener l)
    {
        synchronized (adHoclistChangeListeners) {
            adHoclistChangeListeners.remove(l);
        }
    }

    /**
     * Notifies all interested listeners that a change in the chat room list model has occurred.
     *
     * @param adHocChatRoomWrapper the chat room wrapper that identifies the chat room
     * @param eventID the identifier of the event
     */
    private void fireAdHocChatRoomListChangedEvent(AdHocChatRoomWrapper adHocChatRoomWrapper,
            int eventID)
    {
        AdHocChatRoomListChangeEvent evt = new AdHocChatRoomListChangeEvent(adHocChatRoomWrapper, eventID);
        for (AdHocChatRoomListChangeListener l : adHoclistChangeListeners) {
            l.contentChanged(evt);
        }
    }

    /**
     * Closes the chat corresponding to the given ad-hoc chat room wrapper, if such exists.
     *
     * @param chatRoomWrapper the ad-hoc chat room wrapper for which we search a chat to close.
     */
    private void closeAdHocChatRoom(AdHocChatRoomWrapper chatRoomWrapper)
    {
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, false);
        if (chatPanel != null) {
            // ChatSessionManager.closeChat(chatPanel);
        }
    }

    /**
     * Handles <tt>ServiceEvent</tt>s triggered by adding or removing a ProtocolProviderService.
     * Updates the list of available chat rooms
     * and chat room servers.
     *
     * @param event The event to handle.
     */
    public void serviceChanged(ServiceEvent event)
    {
        // if the event is caused by a bundle being stopped, we don't want to know
        int eType = event.getServiceReference().getBundle().getState();
        if (eType == Bundle.STOPPING)
            return;

        // AndroidGUIActivator.bundleContext can be null on application exit
        if (AndroidGUIActivator.bundleContext != null) {
            Object service = AndroidGUIActivator.bundleContext.getService(event.getServiceReference());

            // we don't care if the source service is not a protocol provider
            if (service instanceof ProtocolProviderService) {
                ProtocolProviderService protocolProvider = (ProtocolProviderService) service;
                OperationSetAdHocMultiUserChat adHocMultiUserChatOpSet
                        = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat.class);
                if (adHocMultiUserChatOpSet != null) {
                    if (event.getType() == ServiceEvent.REGISTERED) {
                        adHocChatRoomList.addChatProvider(protocolProvider);
                    }
                    else if (event.getType() == ServiceEvent.UNREGISTERING) {
                        adHocChatRoomList.removeChatProvider(protocolProvider);
                    }
                }
            }
        }
    }

    /**
     * Joins an ad-hoc chat room in an asynchronous way.
     */
    private static class JoinAdHocChatRoomTask extends AsyncTask<Void, Void, String>
    {
        private static final String SUCCESS = "Success";
        private static final String AUTHENTICATION_FAILED = "AuthenticationFailed";
        private static final String REGISTRATION_REQUIRED = "RegistrationRequired";
        private static final String PROVIDER_NOT_REGISTERED = "ProviderNotRegistered";
        private static final String SUBSCRIPTION_ALREADY_EXISTS = "SubscriptionAlreadyExists";
        private static final String UNKNOWN_ERROR = "UnknownError";
        private final AdHocChatRoomWrapper adHocChatRoomWrapper;

        JoinAdHocChatRoomTask(AdHocChatRoomWrapper chatRoomWrapper)
        {
            this.adHocChatRoomWrapper = chatRoomWrapper;
        }

        /**
         * @return SUCCESS if success, otherwise the error code
         * {@link AsyncTask #doInBackground(Void... params)} to perform all asynchronous tasks.
         */
        @Override
        protected String doInBackground(Void... params)
        {
            AdHocChatRoom chatRoom = adHocChatRoomWrapper.getAdHocChatRoom();

            try {
                chatRoom.join();
                return SUCCESS;
            } catch (OperationFailedException e) {
                Timber.log(TimberLog.FINER, e, "Failed to join ad-hoc chat room: %s", chatRoom.getName());

                switch (e.getErrorCode()) {
                    case OperationFailedException.AUTHENTICATION_FAILED:
                        return AUTHENTICATION_FAILED;
                    case OperationFailedException.REGISTRATION_REQUIRED:
                        return REGISTRATION_REQUIRED;
                    case OperationFailedException.PROVIDER_NOT_REGISTERED:
                        return PROVIDER_NOT_REGISTERED;
                    case OperationFailedException.SUBSCRIPTION_ALREADY_EXISTS:
                        return SUBSCRIPTION_ALREADY_EXISTS;
                    default:
                        return UNKNOWN_ERROR;
                }
            }
        }

        /**
         * {@link AsyncTask #onPostExecute(String)} onPostExecute()} to perform
         * UI changes after the ad-hoc chat room join task has finished.
         */
        @Override
        protected void onPostExecute(String result)
        {
            String returnCode = null;
            try {
                returnCode = get();
            } catch (InterruptedException | ExecutionException ignore) {
            }

            ConfigurationUtils.updateChatRoomStatus(adHocChatRoomWrapper.getProtocolProvider(),
                    adHocChatRoomWrapper.getAdHocChatRoomID(), GlobalStatusEnum.ONLINE_STATUS);

            String errorMessage;
            if (PROVIDER_NOT_REGISTERED.equals(returnCode)) {
                errorMessage = aTalkApp.getResString(
                        R.string.service_gui_CHATROOM_NOT_CONNECTED, adHocChatRoomWrapper.getAdHocChatRoomName());
            }
            else if (SUBSCRIPTION_ALREADY_EXISTS.equals(returnCode)) {
                errorMessage = aTalkApp.getResString(R.string.service_gui_CHATROOM_ALREADY_JOINED,
                        adHocChatRoomWrapper.getAdHocChatRoomName());
            }
            else {
                errorMessage = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REASON,
                        adHocChatRoomWrapper.getAdHocChatRoomName(), result);
            }

            if (!SUCCESS.equals(returnCode) && !AUTHENTICATION_FAILED.equals(returnCode)) {
                MUCActivator.getAlertUIService().showAlertDialog(
                        aTalkApp.getResString(R.string.service_gui_ERROR), errorMessage);
            }
        }
    }

    /**
     * Indicates that an invitation has been received and opens the invitation dialog to notify the user.
     *
     * @param evt the <tt>AdHocChatRoomInvitationReceivedEvent</tt> that notified us
     */
    public void invitationReceived(AdHocChatRoomInvitationReceivedEvent evt)
    {
        // Timber.i("Invitation received: %s", evt.toString());
        final OperationSetAdHocMultiUserChat multiUserChatOpSet = evt.getSourceOperationSet();
        final AdHocChatRoomInvitation invitationAdHoc = evt.getInvitation();

        // Event thread - Must execute in UiThread for dialog
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = aTalkApp.waitForFocus();
            if (activity != null) {
                InvitationReceivedDialog dialog = new InvitationReceivedDialog(
                        activity, multiUserChatManager, multiUserChatOpSet, invitationAdHoc);
                dialog.show();
            }
        });
    }

    /**
     * Implements the <tt>AdHocChatRoomMessageListener.messageDelivered</tt> method. <br>
     * Shows the message in the conversation area and clears the write message area.
     *
     * @param evt the <tt>AdHocChatRoomMessageDeliveredEvent</tt> that notified us
     */
    public void messageDelivered(AdHocChatRoomMessageDeliveredEvent evt)
    {
        AdHocChatRoom sourceChatRoom = (AdHocChatRoom) evt.getSource();
        // Timber.i("Message delivered to ad-hoc chat room: %s", sourceChatRoom.getName());
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, false);
        if (chatPanel != null) {
            IMessage message = evt.getMessage();

            // just return if the delivered message is for remote client consumption only
            if (message.isRemoteOnly())
                return;

            int messageType = evt.getEventType();
            AccountID accountId = sourceChatRoom.getParentProvider().getAccountID();
            chatPanel.addMessage(accountId.getUserID(), accountId.getDisplayName(),
                    evt.getTimestamp(), messageType, message, null);
        }
        else {
            Timber.e("chat panel is null, message NOT DELIVERED !");
        }
    }

    /**
     * Implements <tt>AdHocChatRoomMessageListener.messageDeliveryFailed</tt> method. <br>
     * In the conversation area shows an error message, explaining the problem.
     *
     * @param evt the <tt>AdHocChatRoomMessageDeliveryFailedEvent</tt> that notified us
     */
    public void messageDeliveryFailed(AdHocChatRoomMessageDeliveryFailedEvent evt)
    {
        AdHocChatRoom sourceChatRoom = evt.getSourceChatRoom();
        IMessage sourceMessage = evt.getMessage();
        Contact destParticipant = evt.getDestinationParticipant();

        String errorMsg;
        switch (evt.getErrorCode()) {
            case MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED,
                        destParticipant.getDisplayName());
                break;
            case MessageDeliveryFailedEvent.NETWORK_FAILURE:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED);
                break;
            case MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
                break;
            case MessageDeliveryFailedEvent.INTERNAL_ERROR:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR);
                break;
            case MessageDeliveryFailedEvent.UNSUPPORTED_OPERATION:
                errorMsg = aTalkApp.getResString(R.string.service_gui_UNSUPPORTED_OPERATION);
                break;
            default:
                errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_UNKNOWN_ERROR);
                break;
        }

        String sender = destParticipant.getDisplayName();
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, true);

        chatPanel.addMessage(sender, new Date(), ChatMessage.MESSAGE_OUT,
                sourceMessage.getMimeType(), sourceMessage.getContent());
        chatPanel.addMessage(sender, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg);
        ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
    }

    /**
     * Implements the <tt>AdHocChatRoomMessageListener.messageReceived</tt> method. <br>
     * Obtains the corresponding <tt>ChatPanel</tt> and process the message there.
     *
     * @param evt the <tt>AdHocChatRoomMessageReceivedEvent</tt> that notified us
     */
    public void messageReceived(AdHocChatRoomMessageReceivedEvent evt)
    {
        AdHocChatRoom sourceChatRoom = evt.getSourceChatRoom();
        String sourceParticipant = evt.getSourceChatRoomParticipant().getAddress();

        int messageType = evt.getEventType();
        Timber.i("Message received from contact: %s", sourceParticipant);

        IMessage message = evt.getMessage();
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(sourceChatRoom, true, message.getMessageUID());

        chatPanel.addMessage(sourceParticipant, sourceParticipant, evt.getTimestamp(),
                messageType, message, null);
        ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
    }

    @Override
    public void localUserRoleChanged(ChatRoomLocalUserRoleChangeEvent evt)
    {
        if (evt.isInitial())
            return;
        ChatRoom sourceChatRoom = evt.getSourceChatRoom();
        ChatRoomWrapper chatRoomWrapper = MUCActivator.getMUCService().findChatRoomWrapperFromChatRoom(sourceChatRoom);
        ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, true);
        ChatSessionManager.setCurrentChatId(chatPanel.getChatSession().getChatId());
    }
}
