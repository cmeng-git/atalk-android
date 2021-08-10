/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.muc;

import android.content.Intent;
import android.text.TextUtils;

import net.java.sip.communicator.service.contactsource.ContactSourceService;
import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.gui.AuthenticationWindowService;
import net.java.sip.communicator.service.gui.AuthenticationWindowService.AuthenticationWindow;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.*;

import timber.log.Timber;

import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_AUTHENTICATION_FAILED_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_CAPTCHA_VERIFICATION_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_PROVIDER_NOT_REGISTERED_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_REGISTRATION_REQUIRED_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_SUCCESS_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.JOIN_UNKNOWN_ERROR_PROP;
import static net.java.sip.communicator.service.muc.ChatRoomWrapper.NOT_ENOUGH_PRIVILEGES;

/**
 * The <tt>MUCServiceImpl</tt> class implements the service for the chat rooms.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class MUCServiceImpl extends MUCService
{
    /**
     * The list of persistent chat rooms.
     */
    private final ChatRoomListImpl chatRoomList = new ChatRoomListImpl();

    /**
     * Called to accept an incoming invitation. Adds the invitation chat room to the list of chat rooms and joins it.
     *
     * @param invitation the invitation to accept.
     */
    public void acceptInvitation(ChatRoomInvitation invitation)
    {
        ChatRoom chatRoom = invitation.getTargetChatRoom();
        byte[] password = invitation.getChatRoomPassword();

        String nickName = ConfigurationUtils.getChatRoomProperty(chatRoom.getParentProvider(),
                chatRoom.getName(), ChatRoom.USER_NICK_NAME);
        if (nickName == null) {
            // cmeng - need to add a dialog for Nickname (can also add during invite dialog ?)
            // String[] joinOptions = ChatRoomJoinOptionsDialog.getJoinOptions(true, chatRoom.getParentProvider(), chatRoom.getIdentifier(),
            // MUCActivator.getGlobalDisplayDetailsService().getDisplayName(chatRoom.getParentProvider()));
            // nickName = joinOptions[0];
            nickName = AndroidGUIActivator.getGlobalDisplayDetailsService().getDisplayName(chatRoom.getParentProvider());
        }
        joinChatRoom(chatRoom, nickName, password);
    }

    /**
     * Adds a change listener to the <tt>ChatRoomList</tt>.
     *
     * @param l the listener.
     */
    public void addChatRoomListChangeListener(ChatRoomListChangeListener l)
    {
        chatRoomList.addChatRoomListChangeListener(l);
    }

    /**
     * Removes a change listener to the <tt>ChatRoomList</tt>.
     *
     * @param l the listener.
     */
    public void removeChatRoomListChangeListener(ChatRoomListChangeListener l)
    {
        chatRoomList.removeChatRoomListChangeListener(l);
    }

    /**
     * Fires a <tt>ChatRoomListChangedEvent</tt> event.
     *
     * @param chatRoomWrapper the chat room.
     * @param eventID the id of the event.
     */
    public void fireChatRoomListChangedEvent(ChatRoomWrapper chatRoomWrapper, int eventID)
    {
        chatRoomList.fireChatRoomListChangedEvent(chatRoomWrapper, eventID);
    }

    /**
     * Joins the given chat room with the given password and manages all the exceptions that could
     * occur during the join process.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password the password.
     * @param rememberPassword if true the password should be saved.
     * @param isFirstAttempt is this the first attempt to join room, used to check whether to show some error messages
     * @param subject the subject which will be set to the room after the user join successful.
     */
    private void joinChatRoom(ChatRoomWrapper chatRoomWrapper, String nickName, byte[] password,
            boolean rememberPassword, boolean isFirstAttempt, String subject)
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if (chatRoom == null) {
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_WARNING),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED,
                            chatRoomWrapper.getChatRoomName()));
            return;
        }
        new JoinChatRoomTask((ChatRoomWrapperImpl) chatRoomWrapper, nickName, password,
                rememberPassword, isFirstAttempt, subject).start();
    }

    /**
     * Joins the given chat room with the given password and manages all the exceptions that could
     * occur during the join process.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password the password.
     */
    public void joinChatRoom(ChatRoomWrapper chatRoomWrapper, String nickName, byte[] password)
    {
        if (chatRoomWrapper.getChatRoom() == null) {
            chatRoomWrapper = createChatRoom(chatRoomWrapper, "", false, false, true);
        }

        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if (chatRoom == null) {
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_WARNING),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED,
                            chatRoomWrapper.getChatRoomName()));
            return;
        }
        new JoinChatRoomTask((ChatRoomWrapperImpl) chatRoomWrapper, nickName, password, null).start();
    }

    /**
     * Joins the given chat room with the given password and manages all the exceptions that could
     * occur during the join process.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param nickName the nickname we choose for the given chat room.
     * @param password room password.
     * @param subject which will be set to the room after the user join successful.
     */
    public void joinChatRoom(ChatRoomWrapper chatRoomWrapper, String nickName, byte[] password, String subject)
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if (chatRoom == null) {
            chatRoomWrapper = createChatRoom(chatRoomWrapper, "", false, false, true);

            if (chatRoomWrapper != null)
                chatRoom = chatRoomWrapper.getChatRoom();
        }

        if (chatRoom == null) {
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_WARNING),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoom));
            return;
        }

        // join from add chat room dialog
        new JoinChatRoomTask((ChatRoomWrapperImpl) chatRoomWrapper, nickName, password, subject).start();
    }

    /**
     * Join chat room.
     *
     * @param chatRoomWrapper the chatRoom Wrapper
     */
    public void joinChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        if (chatRoomWrapper.getChatRoom() == null) {
            chatRoomWrapper = createChatRoom(chatRoomWrapper, "", false, false, true);
        }

        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if (chatRoom == null) {
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_WARNING),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED,
                            chatRoomWrapper.getChatRoomName()));
            return;
        }
        new JoinChatRoomTask((ChatRoomWrapperImpl) chatRoomWrapper, null, null, null).start();
    }

    /**
     * Joins the given chat room and manages all the exceptions that could occur during the join process.
     *
     * @param chatRoom the chat room to join
     * @param nickname the nickname we're using to join
     * @param password the password we're using to join
     */
    public void joinChatRoom(ChatRoom chatRoom, String nickname, byte[] password)
    {
        ChatRoomWrapper chatRoomWrapper = getChatRoomWrapperByChatRoom(chatRoom, true);
        if (chatRoomWrapper != null)
            this.joinChatRoom(chatRoomWrapper, nickname, password);
    }

    /**
     * Joins the room with the given room name via the given chat room provider.
     *
     * @param chatRoomName the name of the room to join.
     * @param chatRoomProvider the chat room provider to join through.
     */
    public void joinChatRoom(String chatRoomName, ChatRoomProviderWrapper chatRoomProvider)
    {
        OperationSetMultiUserChat groupChatOpSet
                = chatRoomProvider.getProtocolProvider().getOperationSet(OperationSetMultiUserChat.class);
        ChatRoom chatRoom = null;
        try {
            /* Find chatRoom for <tt>roomName</tt>. If the room doesn't exists in the cache then creates it. */
            chatRoom = groupChatOpSet.findRoom(chatRoomName);
        } catch (Exception e) {
            Timber.log(TimberLog.FINER, e, "Exception occurred while searching for room:%s", chatRoomName);
        }

        if (chatRoom != null) {
            ChatRoomWrapper chatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom);

            if (chatRoomWrapper == null) {
                ChatRoomProviderWrapper parentProvider
                        = chatRoomList.findServerWrapperFromProvider(chatRoom.getParentProvider());

                chatRoomWrapper = new ChatRoomWrapperImpl(parentProvider, chatRoom);
                chatRoomList.addChatRoom(chatRoomWrapper);
                fireChatRoomListChangedEvent(chatRoomWrapper, ChatRoomListChangeEvent.CHAT_ROOM_ADDED);
            }
            joinChatRoom(chatRoomWrapper);
        }
        else
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_ERROR),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_EXIST,
                            chatRoomName, chatRoomProvider.getProtocolProvider().getAccountID().getService()));
    }

    /**
     * Creates a chat room, by specifying the chatRoomWrapper and
     * eventually, the contacts invited to participate in this chat room.
     *
     * @param chatRoomWrapper the chat room to join.
     * @param reason the reason for room creation
     * @param persistent is the room persistent
     * @param isPrivate whether the room will be private or public.
     * @return the <tt>ChatRoomWrapper</tt> corresponding to the created room
     */
    public ChatRoomWrapper createChatRoom(ChatRoomWrapper chatRoomWrapper,
            String reason, boolean join, boolean persistent, boolean isPrivate)
    {
        boolean onServerRoom = (chatRoomWrapper.getChatRoom() != null);
        return createChatRoom(chatRoomWrapper.getChatRoomName(), chatRoomWrapper.getProtocolProvider(),
                new ArrayList<>(), reason, join, persistent, isPrivate, onServerRoom);
    }

    /**
     * Creates a chat room, by specifying the chat room name, the parent protocol provider and
     * eventually, the contacts invited to participate in this chat room.
     *
     * @param roomName the name of the room
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for room creation
     * @param join whether we should join the room after creating it.
     * @param persistent whether the newly created room will be persistent.
     * @param isPrivate whether the room will be private or public.
     * @param onServerRoom whether the room is already in the server room list.
     * @return the <tt>ChatRoomWrapper</tt> corresponding to the created room or <tt>null</tt> if
     * the protocol fails to create the chat room.
     */
    public ChatRoomWrapper createChatRoom(String roomName, ProtocolProviderService protocolProvider,
            Collection<String> contacts, String reason, boolean join, boolean persistent, boolean isPrivate,
            boolean onServerRoom)
    {
        // If there's no group chat operation set we have nothing to do here.
        OperationSetMultiUserChat groupChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat.class);
        if (groupChatOpSet == null)
            return null;

        ChatRoomWrapper chatRoomWrapper = null;
        ChatRoom chatRoom = null;
        try {
            HashMap<String, Object> roomProperties = new HashMap<>();
            roomProperties.put(ChatRoom.IS_PRIVATE, isPrivate);
            roomProperties.put(ChatRoom.ON_SERVER_ROOM, onServerRoom);
            chatRoom = groupChatOpSet.createChatRoom(roomName, roomProperties);

            // server may reject chatRoom creation and timeout on reply
            if ((chatRoom != null) && join) {
                chatRoom.join();
                for (String contact : contacts) {
                    chatRoom.invite(JidCreate.entityBareFrom(contact), reason);
                }
            }
        } catch (OperationFailedException | OperationNotSupportedException | XmppStringprepException
                | SmackException.NotConnectedException | InterruptedException ex) {
            Timber.e(ex, "Failed to create chat room.");
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_ERROR),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_CREATE_ERROR, protocolProvider.getAccountID()), ex);
        }

        if (chatRoom != null) {
            ChatRoomProviderWrapper parentProvider = chatRoomList.findServerWrapperFromProvider(protocolProvider);

            // if there is the same room ids don't add new wrapper as old one maybe already created
            chatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom);
            if (chatRoomWrapper == null) {
                chatRoomWrapper = new ChatRoomWrapperImpl(parentProvider, chatRoom);
                chatRoomWrapper.setPersistent(persistent);
                chatRoomList.addChatRoom(chatRoomWrapper);
            }
        }
        return chatRoomWrapper;
    }

    /**
     * Creates a private chat room, by specifying the parent protocol provider and eventually, the
     * contacts invited to participate in this chat room.
     *
     * @param protocolProvider the parent protocol provider.
     * @param contacts the contacts invited when creating the chat room.
     * @param reason the reason for room creation
     * @param persistent is the room persistent
     * @return the <tt>ChatRoomWrapper</tt> corresponding to the created room
     */
    public ChatRoomWrapper createPrivateChatRoom(ProtocolProviderService protocolProvider,
            Collection<String> contacts, String reason, boolean persistent)
    {
        return this.createChatRoom(null, protocolProvider, contacts, reason, true, persistent, true, false);
    }

    /**
     * Returns existing chat rooms for the given <tt>chatRoomProvider</tt>.
     *
     * @param chatRoomProvider the <tt>ChatRoomProviderWrapper</tt>, which chat rooms we're looking for
     * @return existing chat rooms for the given <tt>chatRoomProvider</tt>
     */
    public List<String> getExistingChatRooms(ChatRoomProviderWrapper chatRoomProvider)
    {
        if (chatRoomProvider == null)
            return null;

        ProtocolProviderService protocolProvider = chatRoomProvider.getProtocolProvider();
        if (protocolProvider == null)
            return null;

        OperationSetMultiUserChat groupChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat.class);
        if (groupChatOpSet == null)
            return null;

        List<String> chatRooms = new ArrayList<>(0);
        try {
            for (EntityBareJid chatRoom : groupChatOpSet.getExistingChatRooms()) {
                chatRooms.add(chatRoom.toString());
            }
        } catch (OperationFailedException | OperationNotSupportedException e) {
            Timber.log(TimberLog.FINER, e, "Failed to obtain existing chat rooms for server: %s",
                    protocolProvider.getAccountID().getService());
        }
        return chatRooms;
    }

    /**
     * Returns existing chatRooms in store for the given <tt>ProtocolProviderService</tt>.
     *
     * @param pps the <tt>ProtocolProviderService</tt>, whom chatRooms we're looking for
     * @return existing chatRooms in store for the given <tt>ProtocolProviderService</tt>
     */
    public List<String> getExistingChatRooms(ProtocolProviderService pps)
    {
        return chatRoomList.getExistingChatRooms(pps);
    }

    /**
     * Rejects the given invitation with the specified reason.
     *
     * @param multiUserChatOpSet the operation set to use for rejecting the invitation
     * @param invitation the invitation to reject
     * @param reason the reason for the rejection
     */
    public void rejectInvitation(OperationSetMultiUserChat multiUserChatOpSet, ChatRoomInvitation invitation, String reason)
            throws OperationFailedException
    {
        multiUserChatOpSet.rejectInvitation(invitation, reason);
    }

    /**
     * Leaves the given chat room.
     *
     * @param chatRoomWrapper the chat room to leave.
     * @return <tt>ChatRoomWrapper</tt> instance associated with the chat room.
     */
    public ChatRoomWrapper leaveChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
        if (chatRoom == null) {
            MUCActivator.getAlertUIService().showAlertDialog(aTalkApp.getResString(R.string.service_gui_WARNING),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_LEAVE_NOT_CONNECTED));
            return null;
        }
        if (chatRoom.isJoined())
            chatRoom.leave();

        ChatRoomWrapper existChatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom);
        if (existChatRoomWrapper == null)
            return null;

        // We save the choice of the user, before the chat room is really joined, because even the
        // join fails we want the next time when we login to join this chat room automatically.
        ConfigurationUtils.updateChatRoomStatus(chatRoomWrapper.getProtocolProvider(),
                chatRoomWrapper.getChatRoomID(), GlobalStatusEnum.OFFLINE_STATUS);
        return existChatRoomWrapper;
    }

    /**
     * Joins a chat room in an asynchronous way.
     */
    private class JoinChatRoomTask extends Thread
    {
        private final ChatRoomWrapperImpl chatRoomWrapper;
        private final String chatRoomId;
        private final String nickName;
        private final byte[] password;
        private final boolean rememberPassword;
        private final boolean isFirstAttempt;
        private final String subject;

        JoinChatRoomTask(ChatRoomWrapperImpl chatRoomWrapper, String nickName, byte[] password,
                boolean rememberPassword, boolean isFirstAttempt, String subject)
        {
            this.chatRoomWrapper = chatRoomWrapper;
            this.chatRoomId = chatRoomWrapper.getChatRoomName();
            this.nickName = nickName;
            this.isFirstAttempt = isFirstAttempt;
            this.subject = subject;

            if (password == null) {
                String passString = chatRoomWrapper.loadPassword();
                if (passString != null) {
                    this.password = passString.getBytes();
                }
                else {
                    this.password = null;
                }
            }
            else {
                this.password = password;
            }
            this.rememberPassword = rememberPassword;
        }

        JoinChatRoomTask(ChatRoomWrapperImpl chatRoomWrapper, String nickName, byte[] password, String subject)
        {
            this(chatRoomWrapper, nickName, password, false, true, subject);
        }

        /**
         * {@link Thread}{run()} to perform all asynchronous tasks.
         */
        @Override
        public void run()
        {
            // Must setup up chatRoom and ready to receive incoming messages before joining/sending presence to server
            // ChatPanel chatPanel = ChatSessionManager.getMultiChat(chatRoomWrapper, true);

            ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
            try {
                if (chatRoom.isJoined()) {
                    if (!TextUtils.isEmpty(nickName) && !nickName.equals(chatRoom.getUserNickname().toString())) {
                        chatRoom.setUserNickname(nickName);
                    }
                    if (!TextUtils.isEmpty(subject) && !subject.equals(chatRoom.getSubject())) {
                        chatRoom.setSubject(subject);
                    }
                }
                else {
                    startChatActivity(chatRoomWrapper);
                    /*
                     * Retry until Exception or cancelled by user; join chatRoom captcha challenge from server
                     * @see ChatRoomJabberImpl#joinAs(),
                     */
                    boolean retry = true;
                    while (retry) {
                        if (password != null && password.length > 0)
                            retry = chatRoom.joinAs(nickName, password);
                        else if (nickName != null)
                            retry = chatRoom.joinAs(nickName);
                        else
                            retry = chatRoom.join();
                    }
                    done(JOIN_SUCCESS_PROP, "");
                }
            } catch (OperationFailedException e) {
                Timber.log(TimberLog.FINER, e, "Failed to join: %s or change chatRoom attributes: %s; %s",
                        chatRoom.getName(), nickName, subject);

                String message = e.getMessage();
                switch (e.getErrorCode()) {
                    case OperationFailedException.CAPTCHA_CHALLENGE:
                        done(JOIN_CAPTCHA_VERIFICATION_PROP, message);
                        break;
                    case OperationFailedException.AUTHENTICATION_FAILED:
                        done(JOIN_AUTHENTICATION_FAILED_PROP, message);
                        break;
                    case OperationFailedException.REGISTRATION_REQUIRED:
                        done(JOIN_REGISTRATION_REQUIRED_PROP, message);
                        break;
                    case OperationFailedException.PROVIDER_NOT_REGISTERED:
                        done(JOIN_PROVIDER_NOT_REGISTERED_PROP, message);
                        break;
                    case OperationFailedException.SUBSCRIPTION_ALREADY_EXISTS:
                        done(JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP, message);
                        break;
                    case OperationFailedException.NOT_ENOUGH_PRIVILEGES:
                        done(NOT_ENOUGH_PRIVILEGES, message);
                        break;
                    default:
                        done(JOIN_UNKNOWN_ERROR_PROP, message);
                }
            } catch (IllegalStateException ex) {
                done(JOIN_PROVIDER_NOT_REGISTERED_PROP, ex.getMessage());
            }
        }

        /**
         * Starts the chat activity for the given metaContact.
         *
         * @param descriptor <tt>MetaContact</tt> for which chat activity will be started.
         */
        private void startChatActivity(Object descriptor)
        {
            Intent chatIntent = ChatSessionManager.getChatIntent(descriptor);
            if (chatIntent != null) {
                aTalkApp.getGlobalContext().startActivity(chatIntent);
            }
            else {
                Timber.w("Failed to start chat with %s", descriptor);
            }
        }

        /**
         * Performs UI changes after the chat room join task has finished.
         *
         * @param returnCode the result code from the chat room join task.
         */
        private void done(String returnCode, String msg)
        {
            ConfigurationUtils.updateChatRoomStatus(chatRoomWrapper.getProtocolProvider(),
                    chatRoomWrapper.getChatRoomID(), GlobalStatusEnum.ONLINE_STATUS);

            String errMsg = null;
            switch (returnCode) {
                case JOIN_AUTHENTICATION_FAILED_PROP:
                    chatRoomWrapper.removePassword();
                    AuthenticationWindowService authWindowsService
                            = ServiceUtils.getService(MUCActivator.bundleContext, AuthenticationWindowService.class);

                    // cmeng - icon not implemented in Android
                    // AuthenticationWindow.getAuthenticationWindowIcon(chatRoomWrapper.getParentProvider().getProtocolProvider()),
                    AuthenticationWindow authWindow = authWindowsService.create(chatRoomWrapper.getNickName(),
                            null, null, false, chatRoomWrapper.isPersistent(), null,
                            aTalkApp.getResString(R.string.service_gui_AUTHENTICATION_WINDOW_TITLE,
                                    chatRoomWrapper.getParentProvider().getName()),
                            aTalkApp.getResString(R.string.service_gui_CHATROOM_REQUIRES_PASSWORD, chatRoomId), "", null,
                            isFirstAttempt ? null
                                    : aTalkApp.getResString(R.string.service_gui_AUTHENTICATION_FAILED, chatRoomId), null);

                    authWindow.setVisible(true);
                    if (!authWindow.isCanceled()) {
                        joinChatRoom(chatRoomWrapper, nickName, new String(authWindow.getPassword()).getBytes(),
                                authWindow.isRememberPassword(), false, subject);
                    }
                    break;

                case JOIN_CAPTCHA_VERIFICATION_PROP:
                    errMsg = msg;
                    break;
                case JOIN_REGISTRATION_REQUIRED_PROP:
                    errMsg = msg + "\n" + aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_JOINED);
                    break;
                case JOIN_PROVIDER_NOT_REGISTERED_PROP:
                    errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_NOT_CONNECTED, chatRoomId);
                    break;
                case JOIN_SUBSCRIPTION_ALREADY_EXISTS_PROP:
                    errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_ALREADY_JOINED, chatRoomId);
                    break;
                case NOT_ENOUGH_PRIVILEGES:
                    errMsg = msg;
                    break;
                case JOIN_UNKNOWN_ERROR_PROP:
                    errMsg = aTalkApp.getResString(R.string.service_gui_CHATROOM_JOIN_FAILED_REASON, chatRoomId, msg);
                    break;
                case JOIN_SUCCESS_PROP:
                    if (rememberPassword) {
                        chatRoomWrapper.savePassword(new String(password));
                    }
                    if (!TextUtils.isEmpty(subject)) {
                        try {
                            chatRoomWrapper.getChatRoom().setSubject(subject);
                        } catch (OperationFailedException ex) {
                            Timber.w("Failed to set subject.");
                        }
                    }
                    break;
            }

            if (errMsg != null) {
                MUCActivator.getAlertUIService().showAlertDialog(
                        aTalkApp.getResString(R.string.service_gui_ERROR), errMsg);
            }
            chatRoomWrapper.firePropertyChange(returnCode);
        }
    }

    /**
     * Finds the <tt>ChatRoomWrapper</tt> instance associated with the source contact.
     *
     * @param contact the source contact.
     * @return the <tt>ChatRoomWrapper</tt> instance.
     */
    public ChatRoomWrapper findChatRoomWrapperFromSourceContact(SourceContact contact)
    {
        if (!(contact instanceof ChatRoomSourceContact))
            return null;

        ChatRoomSourceContact chatRoomContact = (ChatRoomSourceContact) contact;
        return chatRoomList.findChatRoomWrapperFromChatRoomID(chatRoomContact.getChatRoomID(),
                chatRoomContact.getProvider());
    }

    /**
     * Finds the <tt>ChatRoomWrapper</tt> instance associated with the chat room.
     *
     * @param chatRoomID the id of the chat room.
     * @param pps the provider of the chat room.
     * @return the <tt>ChatRoomWrapper</tt> instance.
     */
    public ChatRoomWrapper findChatRoomWrapperFromChatRoomID(String chatRoomID, ProtocolProviderService pps)
    {
        return chatRoomList.findChatRoomWrapperFromChatRoomID(chatRoomID, pps);
    }

    /**
     * Searches for chat room wrapper in chat room list by chat room.
     *
     * @param chatRoom the chat room.
     * @param create if <tt>true</tt> and the chat room wrapper is not found new chatRoomWrapper is created.
     * @return found chat room wrapper or the created chat room wrapper.
     */
    @Override
    public ChatRoomWrapper getChatRoomWrapperByChatRoom(ChatRoom chatRoom, boolean create)
    {
        ChatRoomWrapper chatRoomWrapper = chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom);
        if ((chatRoomWrapper == null) && create) {
            ChatRoomProviderWrapper parentProvider = chatRoomList.findServerWrapperFromProvider(chatRoom.getParentProvider());
            if (parentProvider != null) {
                chatRoomWrapper = new ChatRoomWrapperImpl(parentProvider, chatRoom);
                chatRoomList.addChatRoom(chatRoomWrapper);
            }
        }
        return chatRoomWrapper;
    }

    /**
     * Goes through the locally stored chat rooms list and for each {@link ChatRoomWrapper} tries
     * to find the corresponding server stored {@link ChatRoom} in the specified operation set.
     * Joins automatically all found chat rooms.
     *
     * @param protocolProvider the protocol provider for the account to synchronize
     * @param opSet the multi user chat operation set, which give us access to chat room server
     */
    public void synchronizeOpSetWithLocalContactList(ProtocolProviderService protocolProvider,
            final OperationSetMultiUserChat opSet)
    {
        ChatRoomProviderWrapper chatRoomProvider = findServerWrapperFromProvider(protocolProvider);
        if (chatRoomProvider == null) {
            chatRoomProvider = chatRoomList.addRegisteredChatProvider(protocolProvider);
        }
        if (chatRoomProvider != null) {
            chatRoomProvider.synchronizeProvider();
        }
    }

    /**
     * Returns an iterator to the list of chat room providers.
     *
     * @return an iterator to the list of chat room providers.
     */
    public List<ChatRoomProviderWrapper> getChatRoomProviders()
    {
        return chatRoomList.getChatRoomProviders();
    }

    /**
     * Removes the given <tt>ChatRoom</tt> from the list of all chat rooms.
     *
     * @param chatRoomWrapper the <tt>ChatRoomWrapper</tt> to remove
     */
    public void removeChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        chatRoomList.removeChatRoom(chatRoomWrapper);
    }

    /**
     * Destroys the given <tt>ChatRoom</tt> from the list of all chat rooms.
     *
     * @param chatRoomWrapper the <tt>ChatRoomWrapper</tt> to be destroyed.
     * @param reason the reason for destroying.
     * @param alternateAddress the alternative entityBareJid of the chatRoom to join.
     */
    public void destroyChatRoom(ChatRoomWrapper chatRoomWrapper, String reason, EntityBareJid alternateAddress)
    {
        try {
            if (chatRoomWrapper.getChatRoom().destroy(reason, alternateAddress)) {
                MUCActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
                chatRoomList.removeChatRoom(chatRoomWrapper);
            }
            else {
                // If we leave a chat room which is not persistent, the room cannot be destroyed on the server;
                // and error is returned when we try to destroy it i.e. not-authorized(401)
                if (!chatRoomWrapper.getChatRoom().isPersistent() && !chatRoomWrapper.getChatRoom().isJoined()) {
                    chatRoomList.removeChatRoom(chatRoomWrapper);
                }
            }
            // Allow user to purge local stored chatRoom on XMPPException
        } catch (XMPPException e) {
            AndroidUtils.showAlertConfirmDialog(aTalkApp.getGlobalContext(),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_DESTROY_TITLE),
                    aTalkApp.getResString(R.string.service_gui_CHATROOM_DESTROY_ERROR,
                            chatRoomWrapper.getEntityBareJid(), e.getMessage()),
                    aTalkApp.getResString(R.string.service_gui_PURGE),
                    new DialogActivity.DialogListener()
                    {
                        @Override
                        public boolean onConfirmClicked(DialogActivity dialog)
                        {
                            chatRoomList.removeChatRoom(chatRoomWrapper);
                            return true;
                        }

                        @Override
                        public void onDialogCancelled(DialogActivity dialog)
                        {
                        }
                    }
            );
        }
    }

    /**
     * Adds a ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be added
     */
    public void addChatRoomProviderWrapperListener(ChatRoomProviderWrapperListener listener)
    {
        chatRoomList.addChatRoomProviderWrapperListener(listener);
    }

    /**
     * Removes the ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be removed
     */
    public void removeChatRoomProviderWrapperListener(ChatRoomProviderWrapperListener listener)
    {
        chatRoomList.removeChatRoomProviderWrapperListener(listener);
    }

    /**
     * Returns the <tt>ChatRoomProviderWrapper</tt> that correspond to the given
     * <tt>ProtocolProviderService</tt>. If the list doesn't contain a corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     * @return the <tt>ChatRoomProvider</tt> object corresponding to the given <tt>ProtocolProviderService</tt>
     */
    public ChatRoomProviderWrapper findServerWrapperFromProvider(ProtocolProviderService protocolProvider)
    {
        return chatRoomList.findServerWrapperFromProvider(protocolProvider);
    }

    /**
     * Returns the <tt>ChatRoomWrapper</tt> that correspond to the given <tt>ChatRoom</tt>. If the
     * list of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoom the <tt>ChatRoom</tt> that we're looking for
     * @return the <tt>ChatRoomWrapper</tt> object corresponding to the given <tt>ChatRoom</tt>
     */
    public ChatRoomWrapper findChatRoomWrapperFromChatRoom(ChatRoom chatRoom)
    {
        return chatRoomList.findChatRoomWrapperFromChatRoom(chatRoom);
    }

    /**
     * Opens a chat window for the chat room.
     *
     * @param chatRoomWrapper the chat room.
     */
    public void openChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        if (chatRoomWrapper.getChatRoom() == null) {
            chatRoomWrapper = createChatRoom(chatRoomWrapper, "", false, false, true);

            // leave the chatRoom because getChatRoom().isJoined() returns true otherwise
            if (chatRoomWrapper.getChatRoom().isJoined())
                chatRoomWrapper.getChatRoom().leave();
        }

        if (!chatRoomWrapper.getChatRoom().isJoined()) {
            String savedNick = chatRoomWrapper.getNickName();
            String subject = null;

            if (savedNick == null) {
                // String[] joinOptions = ChatRoomJoinOptionsDialog.getJoinOptions(room.getProtocolProvider(),
                // room.getChatRoomID(), MUCActivator.getGlobalDisplayDetailsService()
                // .getDisplayName(room.getParentProvider().getProtocolProvider()));
                // savedNick = joinOptions[0];
                // subject = joinOptions[1];
            }
            if (savedNick != null) {
                joinChatRoom(chatRoomWrapper, savedNick, null, subject);
            }
            else
                return;
        }
        MUCActivator.getUIService().openChatRoomWindow(chatRoomWrapper);
    }

    /**
     * Returns instance of the <tt>ServerChatRoomContactSourceService</tt> contact source.
     *
     * @return instance of the <tt>ServerChatRoomContactSourceService</tt> contact source.
     */
    public ContactSourceService getServerChatRoomsContactSourceForProvider(ChatRoomProviderWrapper pps)
    {
        return new ServerChatRoomContactSourceService(pps);
    }

    /**
     * Returns <tt>true</tt> if the contact is <tt>ChatRoomSourceContact</tt>
     *
     * @param contact the contact
     * @return <tt>true</tt> if the contact is <tt>ChatRoomSourceContact</tt>
     */
    public boolean isMUCSourceContact(SourceContact contact)
    {
        return (contact instanceof ChatRoomSourceContact);
    }
}
