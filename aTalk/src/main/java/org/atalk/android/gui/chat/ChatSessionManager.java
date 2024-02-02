/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import net.java.sip.communicator.impl.contactlist.MclStorageManager;
import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.gui.event.ChatListener;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.AdHocChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.conference.AdHocChatRoomProviderWrapper;
import org.atalk.android.gui.chat.conference.AdHocChatRoomWrapper;
import org.atalk.android.gui.chat.conference.AdHocConferenceChatSession;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.chatroomslist.AdHocChatRoomList;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * The <code>ChatSessionManager</code> managing active chat sessions.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ChatSessionManager {
    /**
     * The chat identifier property. It corresponds to chat's meta contact UID.
     */
    public static final String CHAT_IDENTIFIER = "ChatIdentifier";
    public static final String CHAT_MODE = "ChatMode";
    public static final String CHAT_MSGTYPE = "ChatMessageTypeIdentifier";

    // Chat Mode variables
    public static final int MC_CHAT = 0;
    public static final int MUC_CC = 1;
    public static final int MUC_ADHOC = 2;

    /**
     * A map of all active chats. The stored key is unique either be MetaContactID, ChatRoomID.
     * This list should be referred to as master reference for other chat associated classes
     */
    private static final Map<String, ChatPanel> activeChats = new LinkedHashMap<>();

    /**
     * The list of chat listeners.
     */
    private static final List<ChatListener> chatListeners = new ArrayList<>();

    private final static Object chatSyncRoot = new Object();

    /**
     * The list of active CurrentChatListener.
     */
    private static final List<CurrentChatListener> currentChatListeners = new ArrayList<>();

    /**
     * The list of chat link listeners.
     */
    private static final List<ChatLinkClickedListener> chatLinkListeners = new ArrayList<>();

    /**
     * The currently selected chat identifier. It's equal to chat's <code>MetaContact</code> UID in
     * the childContact table.
     */
    private static String currentChatId;

    /**
     * Last ChatTransport of the ChatSession
     */
    private static Object lastDescriptor;

    /**
     * Adds an active chat.
     *
     * @param chatPanel the <code>ChatPanel</code> corresponding to the active chat
     */
    private synchronized static void addActiveChat(ChatPanel chatPanel) {
        String chatId = chatPanel.getChatSession().getChatId();
        activeChats.put(chatId, chatPanel);
        fireChatCreated(chatPanel);
    }

    /**
     * Removes an active chat.
     *
     * @param chatPanel the <code>ChatPanel</code> corresponding to the active chat to remove
     */
    public synchronized static void removeActiveChat(ChatPanel chatPanel) {
        // FFR: v2.1.5 NPE for chatPanel
        if (chatPanel != null) {
            activeChats.remove(chatPanel.getChatSession().getChatId());
            fireChatClosed(chatPanel);
            chatPanel.dispose();
        }
    }

    /**
     * Removes all active chats.
     */
    public synchronized static void removeAllActiveChats() {
        ArrayList<ChatPanel> chatPanels = new ArrayList<>(activeChats.values());
        for (ChatPanel chatPanel : chatPanels) {
            removeActiveChat(chatPanel);
        }
    }

    /**
     * Returns the <code>ChatPanel</code> corresponding to the given chat identifier.
     *
     * @param chatKey the chat identifier
     *
     * @return the <code>ChatPanel</code> corresponding to the given chat identifier
     */
    public synchronized static ChatPanel getActiveChat(String chatKey) {
        return activeChats.get(chatKey);
    }

    /**
     * Returns the <code>ChatPanel</code> corresponding to the given <code>MetaContact</code>.
     *
     * @param metaContact the <code>MetaContact</code> corresponding to the <code>ChatPanel</code> we're looking for
     *
     * @return the <code>ChatPanel</code> corresponding to the given chat identifier
     */
    public synchronized static ChatPanel getActiveChat(MetaContact metaContact) {
        return (metaContact != null) ? activeChats.get(metaContact.getMetaUID()) : null;
    }

    /**
     * Returns the list of active chats' identifiers.
     *
     * @return the list of active chats' identifiers
     */
    public synchronized static List<String> getActiveChatsIDs() {
        return new LinkedList<>(activeChats.keySet());
    }

    /**
     * Returns the list of active chats.
     *
     * @return the list of active chats.
     */
    public synchronized static List<Chat> getActiveChats() {
        return new LinkedList<>(activeChats.values());
    }

    /**
     * Sets the current chat session identifier i.e chat is focused and message pop-up is disabled.
     *
     * @param chatId the identifier of the current chat session
     */
    public synchronized static void setCurrentChatId(String chatId) {
        // cmeng: chatId set to null when chat session end
        currentChatId = null;
        lastDescriptor = null;

        if (chatId != null) {
            currentChatId = chatId;
            ChatPanel currChat = getActiveChat(chatId);
            if (currChat != null) {
                lastDescriptor = currChat.getChatSession().getDescriptor();
                // Timber.d("Current chat descriptor: %s = %s", chatId, lastDescriptor);
            }

            // Notifies about new current chat session
            for (CurrentChatListener l : currentChatListeners) {
                l.onCurrentChatChanged(currentChatId);
            }
        }
    }

    /**
     * Return the current chat session identifier.
     *
     * @return the identifier of the current chat session
     */
    public synchronized static String getCurrentChatId() {
        return currentChatId;
    }

    /**
     * Returns currently active <code>ChatPanel</code>.
     *
     * @return currently active <code>ChatPanel</code>.
     */
    public synchronized static ChatPanel getCurrentChatPanel() {
        return getActiveChat(currentChatId);
    }

    /**
     * Registers new chat listener.
     *
     * @param listener the chat listener to add.
     */
    public synchronized static void addChatListener(ChatListener listener) {
        if (!chatListeners.contains(listener))
            chatListeners.add(listener);
    }

    /**
     * Unregisters chat listener.
     *
     * @param listener the chat listener to remove.
     */
    public synchronized static void removeChatListener(ChatListener listener) {
        chatListeners.remove(listener);
    }

    /**
     * Adds given listener to current chat listeners list.
     *
     * @param l the listener to add to current chat listeners list.
     */
    public synchronized static void addCurrentChatListener(CurrentChatListener l) {
        if (!currentChatListeners.contains(l))
            currentChatListeners.add(l);
    }

    /**
     * Removes given listener form current chat listeners list.
     *
     * @param l the listener to remove from current chat listeners list.
     */
    public synchronized static void removeCurrentChatListener(CurrentChatListener l) {
        currentChatListeners.remove(l);
    }

    /**
     * Adds <code>ChatLinkClickedListener</code>.
     *
     * @param chatLinkClickedListener the <code>ChatLinkClickedListener</code> to add.
     */
    public synchronized static void addChatLinkListener(ChatLinkClickedListener chatLinkClickedListener) {
        if (!chatLinkListeners.contains(chatLinkClickedListener))
            chatLinkListeners.add(chatLinkClickedListener);
    }

    /**
     * Removes given <code>ChatLinkClickedListener</code>.
     *
     * @param chatLinkClickedListener the <code>ChatLinkClickedListener</code> to remove.
     */
    public synchronized static void removeChatLinkListener(ChatLinkClickedListener chatLinkClickedListener) {
        chatLinkListeners.remove(chatLinkClickedListener);
    }

    /**
     * Notifies currently registers <code>ChatLinkClickedListener</code> when the link is clicked.
     *
     * @param uri clicked link <code>URI</code>
     */
    public synchronized static void notifyChatLinkClicked(URI uri) {
        for (ChatLinkClickedListener l : chatLinkListeners) {
            l.chatLinkClicked(uri);
        }
    }

    /**
     * Creates the <code>Intent</code> for starting new chat with given <code>MetaContact</code>.
     *
     * @param descriptor the contact we want to start new chat with.
     *
     * @return the <code>Intent</code> for starting new chat with given <code>MetaContact</code>.
     */
    public static Intent getChatIntent(Object descriptor) {
        // A string identifier that uniquely represents this descriptor in the containing chat session database
        String chatId;
        int chatMode;

        // childContacts table = mcUid
        if (descriptor instanceof MetaContact) {
            chatId = ((MetaContact) descriptor).getMetaUID();
            chatMode = MC_CHAT;
        }
        else if (descriptor instanceof Contact) {
            Contact contact = (Contact) descriptor;
            String accountUuid = contact.getProtocolProvider().getAccountID().getAccountUuid();
            String contactJid = contact.getAddress();
            String metaUuid = MclStorageManager.getMetaUuid(accountUuid, contactJid);
            if (metaUuid != null) {
                chatId = metaUuid;
                chatMode = MC_CHAT;
            }
            else {
                return null;
            }
        }
        else if (descriptor instanceof ChatRoomWrapper) {
            chatId = ((ChatRoomWrapper) descriptor).getChatRoomID();
            chatMode = MUC_CC;
        }
        else if (descriptor instanceof AdHocChatRoomWrapper) {
            chatId = ((AdHocChatRoomWrapper) descriptor).getAdHocChatRoomID();
            chatMode = MUC_ADHOC;
        }
        else {
            return null;
        }

        Intent chatIntent = new Intent(aTalkApp.getInstance(), ChatActivity.class);
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        chatIntent.putExtra(CHAT_IDENTIFIER, chatId);
        chatIntent.putExtra(CHAT_MODE, chatMode);
        return chatIntent;
    }

    /**
     * @return the Intent of the chat creation
     */
    public static Intent getLastChatIntent() {
        if (lastDescriptor == null)
            return null;
        else
            return getChatIntent(lastDescriptor);
    }

    /**
     * Disposes of static resources held by this instance.
     */
    public synchronized static void dispose() {
        chatLinkListeners.clear();
        chatListeners.clear();
        currentChatListeners.clear();
        activeChats.clear();
    }

    /**
     * Removes all active chat sessions for the given <code>protocolProvider</code>.
     *
     * @param protocolProvider protocol provider for which all chat sessions to be removed.
     */
    public synchronized static void removeAllChatsForProvider(ProtocolProviderService protocolProvider) {
        ArrayList<ChatPanel> toBeRemoved = new ArrayList<>();
        for (ChatPanel chat : activeChats.values()) {
            if (protocolProvider == chat.getProtocolProvider()) {
                toBeRemoved.add(chat);
            }
        }
        for (ChatPanel chat : toBeRemoved)
            removeActiveChat(chat);
    }

    /**
     * Interface used to listen for currently visible chat session changes.
     */
    public interface CurrentChatListener {
        /**
         * Fired when currently visible chat session changes
         *
         * @param chatId id of current chat session or <code>null</code> if there is no chat currently
         * displayed.
         */
        void onCurrentChatChanged(String chatId);
    }

    // ###########################################################

    /**
     * Finds the chat for given <code>Contact</code>.
     *
     * @param contact the contact for which active chat will be returned.
     *
     * @return active chat for given contact.
     */
    // public synchronized static ChatPanel findChatForContact(Contact contact, boolean createIfNotExists)
    public synchronized static ChatPanel createChatForContact(Contact contact) {
        ChatPanel newChat;
        MetaContact metaContact;

        if (contact == null) {
            Timber.e("Failed to obtain chat instance for null contact");
            return null;
        }
        else {
            metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);
            if (metaContact == null) {
                Timber.w("No meta contact found for %s", contact);
                return null;
            }
        }
        String chatId = metaContact.getMetaUID();
        newChat = createChatForChatId(chatId, MC_CHAT);
        return newChat;
    }

    /**
     * Return the <code>ChatPanel</code> for the given chatId if exists; Otherwise create and return
     * new and saves it in the list of created <code>ChatPanel</code> by the called routine.
     *
     * @param chatId A string identifier that uniquely represents the caller in the containing chat
     * session database
     * @param chatMode can have one of the value as shown in below code
     *
     * @return An existing {@code ChatPanel} or newly created.
     */
    public synchronized static ChatPanel createChatForChatId(String chatId, int chatMode) {
        if (chatId == null)
            throw new NullPointerException();

        ChatPanel chatPanel = null;
        if (activeChats.containsKey(chatId)) {
            chatPanel = activeChats.get(chatId);
        }
        // Create new chatPanel only if it does not exist.
        else if (chatMode == MC_CHAT) {
            MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByMetaUID(chatId);
            if (metaContact != null) {
                chatPanel = createChat(metaContact);
            }
        }
        else if (chatMode == MUC_CC) {
            ChatRoomWrapper chatRoomWrapper
                    = MUCActivator.getMUCService().findChatRoomWrapperFromChatRoomID(chatId, null);
            if (chatRoomWrapper != null) {
                chatPanel = createChat(chatRoomWrapper);
            }
        }
        else if (chatMode == MUC_ADHOC) {
            ChatRoomWrapper chatRoomWrapper
                    = MUCActivator.getMUCService().findChatRoomWrapperFromChatRoomID(chatId, null);
            if (chatRoomWrapper != null) {
                chatPanel = createChat((AdHocChatRoomWrapper) chatRoomWrapper);
            }
        }
        return chatPanel;
    }

    // ############### Multi-User Chat Methods ############################

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>ChatRoomWrapper</code> and
     * optionally creates it if it does not exist yet. Must be executed on the event dispatch thread.
     *
     * @param chatRoomWrapper the <code>ChatRoomWrapper</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a new <code>ChatPanel</code> for the specified
     * <code>ChatRoomWrapper</code> if no such <code>ChatPanel</code> exists already; otherwise, <code>false</code>
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>ChatRoomWrapper</code> or
     * <code>null</code> if no such <code>ChatPanel</code> exists and <code>create</code> is <code>false</code>
     */
    public static ChatPanel getMultiChat(ChatRoomWrapper chatRoomWrapper, boolean create) {
        return getMultiChatInternal(chatRoomWrapper, create);
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>ChatRoomWrapper</code> and
     * optionally creates it if it does not exist yet.
     *
     * @param chatRoomWrapper the <code>ChatRoomWrapper</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a new <code>ChatPanel</code> for the specified
     * <code>ChatRoomWrapper</code> if no such <code>ChatPanel</code> exists already; otherwise, <code>false</code>
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>ChatRoomWrapper</code> or
     * <code>null</code> if no such <code>ChatPanel</code> exists and <code>create</code> is <code>false</code>
     */
    private static ChatPanel getMultiChatInternal(ChatRoomWrapper chatRoomWrapper, boolean create) {
        synchronized (chatSyncRoot) {
            ChatPanel chatPanel = findChatPanelForDescriptor(chatRoomWrapper);

            if ((chatPanel == null) && create)
                chatPanel = createChat(chatRoomWrapper);
            return chatPanel;
        }
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoomWrapper</code>
     * and optionally creates it if it does not exist yet. Must be executed on the event dispatch thread.
     *
     * @param chatRoomWrapper the <code>AdHocChatRoomWrapper</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a new <code>ChatPanel</code> for the specified
     * <code>AdHocChatRoomWrapper</code> if no such <code>ChatPanel</code> exists already; otherwise, <code>false</code>
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoomWrapper</code>
     * or <code>null</code> if no such <code>ChatPanel</code> exists and <code>create</code> is <code>false</code>
     */
    public static ChatPanel getMultiChat(AdHocChatRoomWrapper chatRoomWrapper, boolean create) {
        return getMultiChatInternal(chatRoomWrapper, create);
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoomWrapper</code>
     * and optionally creates it if it does not exist yet.
     *
     * @param chatRoomWrapper the <code>AdHocChatRoomWrapper</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a new <code>ChatPanel</code> for the specified
     * <code>AdHocChatRoomWrapper</code> if no such <code>ChatPanel</code> exists already; otherwise, <code>false</code>
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoomWrapper</code>
     * or <code>null</code> if no such <code>ChatPanel</code> exists and <code>create</code> is <code>false</code>
     */
    private static ChatPanel getMultiChatInternal(AdHocChatRoomWrapper chatRoomWrapper, boolean create) {
        synchronized (chatSyncRoot) {
            ChatPanel chatPanel = findChatPanelForDescriptor(chatRoomWrapper);

            if ((chatPanel == null) && create)
                chatPanel = createChat(chatRoomWrapper);
            return chatPanel;
        }
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>ChatRoom</code> and optionally
     * creates it if it does not exist.
     *
     * @param chatRoom the <code>ChatRoom</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a <code>ChatPanel</code> corresponding to the specified
     * <code>ChatRoom</code> if such <code>ChatPanel</code> does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>ChatRoom</code>;
     * <code>null</code> if there is no such <code>ChatPanel</code> and <code>create</code> is <code>false</code>
     */
    private static ChatPanel getMultiChatInternal(ChatRoom chatRoom, boolean create, String escapedMessageID) {
        synchronized (chatSyncRoot) {
            ChatPanel chatPanel = null;
            ChatRoomWrapper chatRoomWrapper = MUCActivator.getMUCService().getChatRoomWrapperByChatRoom(chatRoom, create);

            if (chatRoomWrapper != null) {
                chatPanel = findChatPanelForDescriptor(chatRoomWrapper);
                if ((chatPanel == null) && create)
                    chatPanel = createChat(chatRoomWrapper, escapedMessageID);
            }
            return chatPanel;
        }
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>ChatRoom</code> and optionally
     * creates it if it does not exist. Must be executed on the event dispatch thread.
     *
     * @param chatRoom the <code>ChatRoom</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a <code>ChatPanel</code> corresponding to the specified
     * <code>ChatRoom</code> if such <code>ChatPanel</code> does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>ChatRoom</code>;
     * <code>null</code> if there is no such <code>ChatPanel</code> and <code>create</code> is <code>false</code>
     */
    public static ChatPanel getMultiChat(ChatRoom chatRoom, boolean create, String escapedMessageID) {
        return getMultiChatInternal(chatRoom, create, escapedMessageID);
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>ChatRoom</code> and optionally
     * creates it if it does not exist.
     *
     * @param chatRoom the <code>ChatRoom</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a <code>ChatPanel</code> corresponding to the specified
     * <code>ChatRoom</code> if such <code>ChatPanel</code> does not exist yet
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>ChatRoom</code>;
     * <code>null</code> if there is no such <code>ChatPanel</code> and <code>create</code> is <code>false</code>
     */
    public static ChatPanel getMultiChat(ChatRoom chatRoom, boolean create) {
        return getMultiChat(chatRoom, create, null);
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoom</code> and
     * optionally creates it if it does not exist. Must be executed on the event dispatch thread.
     *
     * @param adHocChatRoom the <code>AdHocChatRoom</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a <code>ChatPanel</code> corresponding to the specified
     * <code>AdHocChatRoom</code> if such <code>ChatPanel</code> does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoom</code>;
     * <code>null</code> if there is no such <code>ChatPanel</code> and <code>create</code> is <code>false</code>
     */
    private static ChatPanel getMultiChatInternal(AdHocChatRoom adHocChatRoom, boolean create, String escapedMessageID) {
        synchronized (chatSyncRoot) {
            AdHocChatRoomList chatRoomList
                    = AndroidGUIActivator.getUIService().getConferenceChatManager().getAdHocChatRoomList();

            // Search in the chat room's list for a chat room that correspond to the given one.
            AdHocChatRoomWrapper chatRoomWrapper = chatRoomList.findChatRoomWrapperFromAdHocChatRoom(adHocChatRoom);

            if ((chatRoomWrapper == null) && create) {
                AdHocChatRoomProviderWrapper parentProvider
                        = chatRoomList.findServerWrapperFromProvider(adHocChatRoom.getParentProvider());
                chatRoomWrapper = new AdHocChatRoomWrapper(parentProvider, adHocChatRoom);
                chatRoomList.addAdHocChatRoom(chatRoomWrapper);
            }
            ChatPanel chatPanel = null;
            if (chatRoomWrapper != null) {
                chatPanel = findChatPanelForDescriptor(chatRoomWrapper);
                if ((chatPanel == null) && create)
                    chatPanel = createChat(chatRoomWrapper, escapedMessageID);
            }
            return chatPanel;
        }
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoom</code> and
     * optionally creates it if it does not exist. Must be executed on the event dispatch thread.
     *
     * @param adHocChatRoom the <code>AdHocChatRoom</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a <code>ChatPanel</code> corresponding to the specified
     * <code>AdHocChatRoom</code> if such <code>ChatPanel</code> does not exist yet
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoom</code>;
     * <code>null</code> if there is no such <code>ChatPanel</code> and <code>create</code> is <code>false</code>
     */
    public static ChatPanel getMultiChat(AdHocChatRoom adHocChatRoom, boolean create, String escapedMessageID) {
        return getMultiChatInternal(adHocChatRoom, create, escapedMessageID);
    }

    /**
     * Gets the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoom</code> and
     * optionally creates it if it does not exist.
     *
     * @param adHocChatRoom the <code>AdHocChatRoom</code> to get the corresponding <code>ChatPanel</code> of
     * @param create <code>true</code> to create a <code>ChatPanel</code> corresponding to the specified
     * <code>AdHocChatRoom</code> if such <code>ChatPanel</code> does not exist yet
     *
     * @return the <code>ChatPanel</code> corresponding to the specified <code>AdHocChatRoom</code>;
     * <code>null</code> if there is no such <code>ChatPanel</code> and <code>create</code> is <code>false</code>
     */
    public static ChatPanel getMultiChat(AdHocChatRoom adHocChatRoom, boolean create) {
        return getMultiChat(adHocChatRoom, create, null);
    }

    // ==============================================

    /**
     * Gets the default <code>Contact</code> of the specified <code>MetaContact</code> if it is online;
     * otherwise, gets one of its <code>Contact</code>s which supports offline messaging.
     *
     * @param metaContact the <code>MetaContact</code> to get the default <code>Contact</code> of
     *
     * @return the default <code>Contact</code> of the specified <code>MetaContact</code> if it is online;
     * otherwise, gets one of its <code>Contact</code>s which supports offline messaging
     */
    private static Contact getDefaultContact(MetaContact metaContact) {
        Contact defaultContact = metaContact.getDefaultContact(OperationSetBasicInstantMessaging.class);
        if (defaultContact == null) {
            defaultContact = metaContact.getDefaultContact(OperationSetSmsMessaging.class);
            if (defaultContact == null)
                return null;
        }

        ProtocolProviderService defaultProvider = defaultContact.getProtocolProvider();
        OperationSetBasicInstantMessaging defaultIM
                = defaultProvider.getOperationSet(OperationSetBasicInstantMessaging.class);

        if ((defaultContact.getPresenceStatus().getStatus() < 1)
                && (!defaultIM.isOfflineMessagingSupported() || !defaultProvider.isRegistered())) {
            Iterator<Contact> protoContacts = metaContact.getContacts();

            while (protoContacts.hasNext()) {
                Contact contact = protoContacts.next();
                ProtocolProviderService protoContactProvider = contact.getProtocolProvider();
                OperationSetBasicInstantMessaging protoContactIM
                        = protoContactProvider.getOperationSet(OperationSetBasicInstantMessaging.class);

                if (protoContactIM != null && protoContactIM.isOfflineMessagingSupported()
                        && protoContactProvider.isRegistered()) {
                    defaultContact = contact;
                }
            }
        }
        return defaultContact;
    }

    /**
     * Creates a <code>ChatPanel</code> for the given contact and saves it in the list of created <code>ChatPanel</code>s.
     *
     * @param metaContact the <code>MetaContact</code> to create a <code>ChatPanel</code> for
     *
     * @return The {@code ChatPanel} newly created.
     */
    private static ChatPanel createChat(MetaContact metaContact) {
        /*
         * The Contact, respectively its ChatTransport to be selected in the newly created ChatPanel; select the
         * default Contact of  metaContact if it is online or one of its Contacts which supports offline messaging
         */
        Contact protocolContact = getDefaultContact(metaContact);
        if (protocolContact == null)
            return null;

        ChatPanel chatPanel = new ChatPanel(metaContact);
        // The ContactResource to be selected in the newly created ChatPanel
        ContactResource contactResource = ContactResource.BASE_RESOURCE;
        Collection<ContactResource> resources = metaContact.getDefaultContact().getResources();
        // cmeng: resources == null if user account not registered with server
        if (resources != null) {
            for (ContactResource res : resources) {
                if (res != null) {
                    contactResource = res;
                    break;
                }
            }
        }

        MetaContactChatSession chatSession
                = new MetaContactChatSession(chatPanel, metaContact, protocolContact, contactResource);
        chatPanel.setChatSession(chatSession);

        addActiveChat(chatPanel);
        // chatPanel.loadHistory(escapedMessageID);
        return chatPanel;
    }

    /**
     * Creates a <code>ChatPanel</code> for the given <code>ChatRoom</code> and saves it in the list of
     * created <code>ChatPanel</code>s.
     *
     * @param chatRoomWrapper the <code>ChatRoom</code>, for which the chat will be created
     *
     * @return The {@code ChatPanel} newly created.
     */
    private static ChatPanel createChat(ChatRoomWrapper chatRoomWrapper) {
        return createChat(chatRoomWrapper, null);
    }

    /**
     * Creates a <code>ChatPanel</code> for the given <code>ChatRoom</code> and saves it in the list of
     * created <code>ChatPanel</code>s.
     *
     * @param chatRoomWrapper the <code>ChatRoom</code>, for which the chat will be created
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat.
     *
     * @return The {@code ChatPanel} newly created.
     */
    private static ChatPanel createChat(ChatRoomWrapper chatRoomWrapper, String escapedMessageID) {
        ChatPanel chatPanel = new ChatPanel(chatRoomWrapper);
        ConferenceChatSession chatSession = new ConferenceChatSession(chatPanel, chatRoomWrapper);
        chatPanel.setChatSession(chatSession);

        addActiveChat(chatPanel);
        return chatPanel;
    }

    /**
     * Creates a <code>ChatPanel</code> for the given <code>AdHocChatRoom</code> and saves it in the list
     * of created <code>ChatPanel</code>s.
     *
     * @param chatRoomWrapper the <code>AdHocChatRoom</code>, for which the chat will be created
     *
     * @return The {@code ChatPanel} newly created.
     */
    private static ChatPanel createChat(AdHocChatRoomWrapper chatRoomWrapper) {
        return createChat(chatRoomWrapper, null);
    }

    /**
     * Creates a <code>ChatPanel</code> for the given <code>AdHocChatRoom</code> and saves it in the list
     * of created <code>ChatPanel</code>s.
     *
     * @param chatRoomWrapper the <code>AdHocChatRoom</code>, for which the chat will be created
     * @param escapedMessageID the message ID of the message that should be excluded from the history when the last
     * one is loaded in the chat.
     *
     * @return The {@code ChatPanel} newly created.
     */
    private static ChatPanel createChat(AdHocChatRoomWrapper chatRoomWrapper, String escapedMessageID) {
        ChatPanel chatPanel = new ChatPanel(chatRoomWrapper);
        AdHocConferenceChatSession chatSession = new AdHocConferenceChatSession(chatPanel, chatRoomWrapper);
        chatPanel.setChatSession(chatSession);

        addActiveChat(chatPanel);
        return chatPanel;
    }

    /**
     * Finds the <code>ChatPanel</code> corresponding to the given chat descriptor.
     *
     * @param descriptor the chat descriptor.
     *
     * @return the <code>ChatPanel</code> corresponding to the given chat descriptor if any; otherwise, <code>null</code>
     */
    private static ChatPanel findChatPanelForDescriptor(Object descriptor) {
        for (ChatPanel chatPanel : activeChats.values()) {
            if (chatPanel.getChatSession().getDescriptor().equals(descriptor))
                return chatPanel;
        }
        return null;
    }

    /**
     * Notifies the <code>ChatListener</code>s registered with this instance that a specific <code>Chat</code> has been closed.
     *
     * @param chat the <code>Chat</code> which has been closed and which the <code>ChatListener</code>s
     * registered with this instance are to be notified about
     */
    private static void fireChatClosed(Chat chat) {
        for (ChatListener l : chatListeners) {
            l.chatClosed(chat);
        }
    }

    /**
     * Notifies the <code>ChatListener</code>s registered with this instance that a specific
     * <code>Chat</code> has been created.
     *
     * @param chat the <code>Chat</code> which has been created and which the <code>ChatListener</code>s
     * registered with this instance are to be notified about
     */
    private static void fireChatCreated(Chat chat) {
        for (ChatListener l : chatListeners) {
            l.chatCreated(chat);
        }
    }

    // ******************************************************* //

    /**
     * Runnable used as base for all that creates chat panels.
     */
    private abstract class AbstractChatPanelCreateRunnable {
        /**
         * The result panel.
         */
        private ChatPanel chatPanel;

        /**
         * Returns the result chat panel.
         *
         * @return the result chat panel.
         */
        public ChatPanel getChatPanel() {
            new Handler(Looper.getMainLooper()).post(() -> chatPanel = createChatPanel());
            return chatPanel;
        }

        /**
         * The method that will create the panel.
         *
         * @return the result chat panel.
         */
        protected abstract ChatPanel createChatPanel();
    }

    /**
     * Creates chat room wrapper in event dispatch thread.
     */
    private class CreateChatRoomWrapperRunner extends AbstractChatPanelCreateRunnable {
        /**
         * The source chat room.
         */
        private final ChatRoomWrapper chatRoomWrapper;

        /**
         * Constructs.
         *
         * @param chatRoomWrapper the <code>ChatRoomWrapper</code> to use for creating a panel.
         */
        private CreateChatRoomWrapperRunner(ChatRoomWrapper chatRoomWrapper) {
            this.chatRoomWrapper = chatRoomWrapper;
        }

        /**
         * Runs on event dispatch thread.
         */
        @Override
        protected ChatPanel createChatPanel() {
            return getMultiChatInternal(chatRoomWrapper, true);
        }
    }

    /**
     * Creates chat room wrapper in event dispatch thread.
     */
    private class CreateAdHocChatRoomWrapperRunner extends AbstractChatPanelCreateRunnable {
        /**
         * The source chat room.
         */
        private final AdHocChatRoomWrapper chatRoomWrapper;

        /**
         * Constructs.
         *
         * @param chatRoomWrapper the <code>AdHocChatRoom</code>, for which the chat will be created.
         */
        private CreateAdHocChatRoomWrapperRunner(AdHocChatRoomWrapper chatRoomWrapper) {
            this.chatRoomWrapper = chatRoomWrapper;
        }

        /**
         * Runs on event dispatch thread.
         */
        @Override
        protected ChatPanel createChatPanel() {
            return getMultiChatInternal(chatRoomWrapper, true);
        }
    }

    /**
     * Creates chat room in event dispatch thread.
     */
    private class CreateChatRoomRunner extends AbstractChatPanelCreateRunnable {
        /**
         * The source chat room.
         */
        private final ChatRoom chatRoom;
        private final String escapedMessageID;

        /**
         * Constructs.
         *
         * @param chatRoom the <code>ChatRoom</code> used to create the corresponding <code>ChatPanel</code>.
         */
        private CreateChatRoomRunner(ChatRoom chatRoom, String escapedMessageID) {
            this.chatRoom = chatRoom;
            this.escapedMessageID = escapedMessageID;
        }

        /**
         * Runs on event dispatch thread.
         */
        @Override
        protected ChatPanel createChatPanel() {
            return getMultiChatInternal(chatRoom, true, escapedMessageID);
        }
    }

    /**
     * Creates chat room in event dispatch thread.
     */
    private class CreateAdHocChatRoomRunner extends AbstractChatPanelCreateRunnable {
        /**
         * The source chat room.
         */
        private final AdHocChatRoom adHocChatRoom;
        private final String escapedMessageID;

        /**
         * Constructs.
         *
         * @param adHocChatRoom the <code>AdHocChatRoom</code> used to create the corresponding <code>ChatPanel</code>.
         */
        private CreateAdHocChatRoomRunner(AdHocChatRoom adHocChatRoom, String escapedMessageID) {
            this.adHocChatRoom = adHocChatRoom;
            this.escapedMessageID = escapedMessageID;
        }

        /**
         * Runs on event dispatch thread.
         */
        @Override
        protected ChatPanel createChatPanel() {
            return getMultiChatInternal(adHocChatRoom, true, escapedMessageID);
        }
    }
}
