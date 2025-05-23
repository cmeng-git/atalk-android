/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.muc;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent;
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.gui.chat.ChatSession;
import org.atalk.persistance.DatabaseBackend;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jxmpp.jid.EntityBareJid;
import org.osgi.framework.Bundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * The <code>ChatRoomsList</code> is the list containing all chat rooms.
 *
 * @author Yana Stamcheva
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class ChatRoomListImpl implements RegistrationStateChangeListener, ServiceListener {
    /**
     * The list containing all chat servers and rooms.
     */
    private final List<ChatRoomProviderWrapper> providersList = new Vector<>();

    /**
     * All ChatRoomProviderWrapperListener change listeners registered so far.
     */
    private final List<ChatRoomProviderWrapperListener> providerChangeListeners = new ArrayList<>();

    /**
     * A list of all <code>ChatRoomListChangeListener</code>-s.
     */
    private final Vector<ChatRoomListChangeListener> listChangeListeners = new Vector<>();

    private SQLiteDatabase mDB;

    /**
     * Constructs and initializes new <code>ChatRoomListImpl</code> objects. Adds the created object
     * as service lister to the bundle context.
     */
    public ChatRoomListImpl() {
        mDB = DatabaseBackend.getWritableDB();
        loadList();
        MUCActivator.bundleContext.addServiceListener(this);
    }

    /**
     * Initializes the list of chat rooms.
     */
    private void loadList() {
        try {
            ServiceReference[] serRefs
                    = MUCActivator.bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);

            // If we don't have providers at this stage we just return.
            if (serRefs == null)
                return;

            for (ServiceReference serRef : serRefs) {
                ProtocolProviderService pps = (ProtocolProviderService) MUCActivator.bundleContext.getService(serRef);
                Object multiUserChatOpSet = pps.getOperationSet(OperationSetMultiUserChat.class);
                if (multiUserChatOpSet != null) {
                    this.addChatProvider(pps);
                }
            }
        } catch (InvalidSyntaxException e) {
            Timber.e(e, "Failed to obtain service references.");
        }
    }

    /**
     * Adds the given <code>ChatRoomListChangeListener</code> that will listen for all changes of the
     * chat room list data model.
     *
     * @param l the listener to add.
     */
    public void addChatRoomListChangeListener(ChatRoomListChangeListener l) {
        synchronized (listChangeListeners) {
            listChangeListeners.add(l);
        }
    }

    /**
     * Removes the given <code>ChatRoomListChangeListener</code>.
     *
     * @param l the listener to remove.
     */
    public void removeChatRoomListChangeListener(ChatRoomListChangeListener l) {
        synchronized (listChangeListeners) {
            listChangeListeners.remove(l);
        }
    }

    /**
     * Notifies all interested listeners that a change in the chat room list model has occurred.
     *
     * @param chatRoomWrapper the chat room wrapper that identifies the chat room
     * @param eventID the identifier of the event
     */
    public void fireChatRoomListChangedEvent(ChatRoomWrapper chatRoomWrapper, int eventID) {
        ChatRoomListChangeEvent evt = new ChatRoomListChangeEvent(chatRoomWrapper, eventID);
        for (ChatRoomListChangeListener l : listChangeListeners) {
            l.contentChanged(evt);
        }
    }

    /**
     * Adds a chat server which is registered and all its existing chat rooms (local & on server)
     *
     * @param pps the <code>ProtocolProviderService</code> corresponding to the chat server
     */
    ChatRoomProviderWrapper addRegisteredChatProvider(ProtocolProviderService pps) {
        ChatRoomProviderWrapper chatRoomProvider = new ChatRoomProviderWrapperImpl(pps);
        providersList.add(chatRoomProvider);

        // local stored chatRooms that the user have sessions before
        List<String> chatRoomList = getExistingChatRooms(pps);

        // cmeng: should not include non-joined server chatRooms in user chatRoom window
//        MUCServiceImpl mucService = MUCActivator.getMUCService();
//        List<String> sChatRoomList = mucService.getExistingChatRooms(chatRoomProvider);
//        for (String sRoom : sChatRoomList) {
//            if (!chatRoomList.contains(sRoom))
//                chatRoomList.add(sRoom);
//        }

        for (String chatRoomID : chatRoomList) {
            ChatRoomWrapper chatRoomWrapper = new ChatRoomWrapperImpl(chatRoomProvider, chatRoomID);
            chatRoomProvider.addChatRoom(chatRoomWrapper);
        }
        fireProviderWrapperAdded(chatRoomProvider);
        return chatRoomProvider;
    }

    /**
     * Adds a listener to wait for provider to be registered or unregistered.
     * Only take action on unregistered to remove chatRoomWrapperProvider
     *
     * @param pps the <code>ProtocolProviderService</code> corresponding to the chat server
     */
    private void addChatProvider(ProtocolProviderService pps) {
        if (pps.isRegistered())
            addRegisteredChatProvider(pps);
        else
            pps.addRegistrationStateChangeListener(this);
    }

    /**
     * Removes the corresponding server and all related chat rooms from this list.
     *
     * @param pps the <code>ProtocolProviderService</code> corresponding to the server to remove
     */
    private void removeChatProvider(ProtocolProviderService pps) {
        ChatRoomProviderWrapper wrapper = findServerWrapperFromProvider(pps);
        if (wrapper != null)
            removeChatProvider(wrapper, true);
    }

    /**
     * Removes the corresponding server and all related chatRooms from this list.
     *
     * @param chatRoomProvider the <code>ChatRoomProviderWrapper</code> corresponding to the server to remove
     * @param permanently whether to remove any listener and stored configuration
     */
    private void removeChatProvider(ChatRoomProviderWrapper chatRoomProvider, boolean permanently) {
        providersList.remove(chatRoomProvider);
        if (permanently) {
            chatRoomProvider.getProtocolProvider().removeRegistrationStateChangeListener(this);

            AccountID accountID = chatRoomProvider.getProtocolProvider().getAccountID();
            AccountManager accountManager = MUCActivator.getAccountManager();
            if ((accountManager != null)
                    && (!accountManager.getStoredAccounts().contains(accountID))) {

                String accountUid = accountID.getAccountUid();
                String[] args = {accountUid, String.valueOf(ChatSession.MODE_MULTI)};

                mDB.delete(ChatSession.TABLE_NAME, ChatSession.ACCOUNT_UID + "=? AND "
                        + ChatSession.MODE + "=?", args);
            }
        }

        for (int i = 0; i < chatRoomProvider.countChatRooms(); i++) {
            ChatRoomWrapper wrapper = chatRoomProvider.getChatRoom(i);
            MUCActivator.getUIService().closeChatRoomWindow(wrapper);

            // clears listeners added by chat room
            wrapper.removeListeners();
        }
        // clears listeners added by the system chat room
        chatRoomProvider.getSystemRoomWrapper().removeListeners();
        fireProviderWrapperRemoved(chatRoomProvider);
    }

    /**
     * Adds a chat room to this list.
     *
     * @param chatRoomWrapper the <code>ChatRoom</code> to add
     */
    public void addChatRoom(ChatRoomWrapper chatRoomWrapper) {
        ChatRoomProviderWrapper chatRoomProvider = chatRoomWrapper.getParentProvider();
        if (!chatRoomProvider.containsChatRoom(chatRoomWrapper))
            chatRoomProvider.addChatRoom(chatRoomWrapper);

        if (chatRoomWrapper.isPersistent()) {
            ConfigurationUtils.saveChatRoom(chatRoomProvider.getProtocolProvider(),
                    chatRoomWrapper.getChatRoomID(), chatRoomWrapper.getChatRoomID());
        }
        fireChatRoomListChangedEvent(chatRoomWrapper, ChatRoomListChangeEvent.CHAT_ROOM_ADDED);
    }

    /**
     * Removes the given <code>ChatRoom</code> from the list of all chat rooms and bookmark on server
     *
     * @param chatRoomWrapper the <code>ChatRoomWrapper</code> to remove
     */
    public void removeChatRoom(ChatRoomWrapper chatRoomWrapper) {
        ChatRoomProviderWrapper chatRoomProvider = chatRoomWrapper.getParentProvider();

        if (providersList.contains(chatRoomProvider)) {
            /*
             * Remove bookmark from the server when the room is removed.
             */
            ProtocolProviderService pps = chatRoomProvider.getProtocolProvider();
            BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());
            EntityBareJid entityBareJid = chatRoomWrapper.getEntityBareJid();
            try {
                bookmarkManager.removeBookmarkedConference(entityBareJid);
            } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                     | XMPPException.XMPPErrorException | InterruptedException e) {
                Timber.w("Failed to remove Bookmarks: %s", e.getMessage());
            }

            chatRoomProvider.removeChatRoom(chatRoomWrapper);
            ConfigurationUtils.removeChatRoom(pps, chatRoomWrapper.getChatRoomID());

            chatRoomWrapper.removeListeners();
            fireChatRoomListChangedEvent(chatRoomWrapper, ChatRoomListChangeEvent.CHAT_ROOM_REMOVED);
        }
    }

    /**
     * Returns the <code>ChatRoomWrapper</code> that correspond to the given <code>ChatRoom</code>. If the
     * list of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoom the <code>ChatRoom</code> that we're looking for
     *
     * @return the <code>ChatRoomWrapper</code> object corresponding to the given <code>ChatRoom</code>
     */
    public ChatRoomWrapper findChatRoomWrapperFromChatRoom(ChatRoom chatRoom) {
        for (ChatRoomProviderWrapper provider : providersList) {
            // check only for the right PP
            if (!chatRoom.getParentProvider().equals(provider.getProtocolProvider()))
                continue;

            ChatRoomWrapper systemRoomWrapper = provider.getSystemRoomWrapper();
            ChatRoom systemRoom = systemRoomWrapper.getChatRoom();

            if ((systemRoom != null) && systemRoom.equals(chatRoom)) {
                return systemRoomWrapper;
            }
            else {
                ChatRoomWrapper chatRoomWrapper = provider.findChatRoomWrapperForChatRoom(chatRoom);

                if (chatRoomWrapper != null) {
                    // stored chatRooms has no chatRoom, but their id is the same as the chatRoom
                    // we are searching wrapper for. Also during reconnect we don't have the same
                    // chat id for another chat room object.
                    if (chatRoomWrapper.getChatRoom() == null
                            || !chatRoomWrapper.getChatRoom().equals(chatRoom)) {
                        chatRoomWrapper.setChatRoom(chatRoom);
                    }
                    return chatRoomWrapper;
                }
            }
        }
        return null;
    }

    /**
     * Returns the <code>ChatRoomWrapper</code> that correspond to the given id of chat room and
     * provider. If the list of chat rooms doesn't contain a corresponding wrapper - returns null.
     *
     * @param chatRoomID the id of <code>ChatRoom</code> that we're looking for
     * @param pps the protocol provider associated with the chat room.
     *
     * @return the <code>ChatRoomWrapper</code> object corresponding to the given id of the chat room
     */
    public ChatRoomWrapper findChatRoomWrapperFromChatRoomID(String chatRoomID, ProtocolProviderService pps) {
        for (ChatRoomProviderWrapper provider : providersList) {
            // check all pps OR only for the right pps if provided (cmeng)
            if ((pps != null) && !pps.equals(provider.getProtocolProvider()))
                continue;

            ChatRoomWrapper systemRoomWrapper = provider.getSystemRoomWrapper();
            ChatRoom systemRoom = systemRoomWrapper.getChatRoom();
            if ((systemRoom != null) && systemRoom.getIdentifier().equals(chatRoomID)) {
                return systemRoomWrapper;
            }
            else {
                ChatRoomWrapper chatRoomWrapper = provider.findChatRoomWrapperForChatRoomID(chatRoomID);
                if ((chatRoomWrapper != null) || (pps != null)) {
                    return chatRoomWrapper;
                }
            }
        }
        return null;
    }

    /**
     * Returns the <code>ChatRoomProviderWrapper</code> that correspond to the given
     * <code>ProtocolProviderService</code>. If the list doesn't contain a corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     *
     * @return the <code>ChatRoomProvider</code> object corresponding to the given <code>ProtocolProviderService</code>
     */
    public ChatRoomProviderWrapper findServerWrapperFromProvider(ProtocolProviderService protocolProvider) {
        for (ChatRoomProviderWrapper chatRoomProvider : providersList) {
            if ((chatRoomProvider != null) && chatRoomProvider.getProtocolProvider().equals(protocolProvider)) {
                return chatRoomProvider;
            }
        }
        return null;
    }

    /**
     * Returns an iterator to the list of chat room providers.
     *
     * @return an iterator to the list of chat room providers.
     */
    public List<ChatRoomProviderWrapper> getChatRoomProviders() {
        return providersList;
    }

    /**
     * Adds a ChatRoomProviderWrapperListener to the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be added
     */
    public synchronized void addChatRoomProviderWrapperListener(ChatRoomProviderWrapperListener listener) {
        providerChangeListeners.add(listener);
    }

    /**
     * Removes a ChatRoomProviderWrapperListener from the listener list.
     *
     * @param listener the ChatRoomProviderWrapperListener to be removed
     */
    public synchronized void removeChatRoomProviderWrapperListener(ChatRoomProviderWrapperListener listener) {
        providerChangeListeners.remove(listener);
    }

    /**
     * Fire that chat room provider wrapper was added.
     *
     * @param provider which was added.
     */
    private void fireProviderWrapperAdded(ChatRoomProviderWrapper provider) {
        if (providerChangeListeners != null) {
            for (ChatRoomProviderWrapperListener target : providerChangeListeners) {
                target.chatRoomProviderWrapperAdded(provider);
            }
        }
    }

    /**
     * Fire that chat room provider wrapper was removed.
     *
     * @param provider which was removed.
     */
    private void fireProviderWrapperRemoved(ChatRoomProviderWrapper provider) {
        if (providerChangeListeners != null) {
            for (ChatRoomProviderWrapperListener target : providerChangeListeners) {
                target.chatRoomProviderWrapperRemoved(provider);
            }
        }
    }

    /**
     * Listens for changes of providers registration state, as we can use only registered providers.
     *
     * @param evt a <code>RegistrationStateChangeEvent</code> which describes the event that occurred.
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt) {
        ProtocolProviderService pps = evt.getProvider();
        if (evt.getNewState() == RegistrationState.REGISTERED) {
            // Must use MUCServiceImpl#synchronizeOpSetWithLocalContactList to avoid duplication entry
        }
        else if (evt.getNewState() == RegistrationState.UNREGISTERED
                || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED
                || evt.getNewState() == RegistrationState.CONNECTION_FAILED) {
            ChatRoomProviderWrapper wrapper = findServerWrapperFromProvider(pps);
            if (wrapper != null) {
                removeChatProvider(wrapper, false);
            }
        }
    }

    @Override
    public void serviceChanged(ServiceEvent event) {
        // if the event is caused by a bundle being stopped, we don't want to know
        if (event.getServiceReference().getBundle().getState() == Bundle.STOPPING)
            return;

        Object service = MUCActivator.bundleContext.getService(event.getServiceReference());
        // we don't care if the source service is not a protocol provider
        if (!(service instanceof ProtocolProviderService))
            return;

        ProtocolProviderService pps = (ProtocolProviderService) service;
        Object multiUserChatOpSet = pps.getOperationSet(OperationSetMultiUserChat.class);
        if (multiUserChatOpSet != null) {
            if (event.getType() == ServiceEvent.REGISTERED) {
                addChatProvider(pps);
            }
            else if (event.getType() == ServiceEvent.UNREGISTERING) {
                removeChatProvider(pps);
            }
        }
    }

    /**
     * Returns existing chatRooms in store for the given <code>ProtocolProviderService</code>.
     *
     * @param pps the <code>ProtocolProviderService</code>, whom chatRooms we're looking for
     *
     * @return existing chatRooms in store for the given <code>ProtocolProviderService</code>
     */
    public List<String> getExistingChatRooms(ProtocolProviderService pps) {
        List<String> chatRooms = new ArrayList<>(0);

        String accountUid = pps.getAccountID().getAccountUid();
        String[] args = {accountUid, String.valueOf(ChatSession.MODE_MULTI)};
        String[] columns = {ChatSession.ENTITY_JID};
        String ORDER_ASC = ChatSession.ENTITY_JID + " ASC";

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.MODE + "=?", args, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            chatRooms.add(cursor.getString(0));
        }
        cursor.close();
        return chatRooms;
    }
}
