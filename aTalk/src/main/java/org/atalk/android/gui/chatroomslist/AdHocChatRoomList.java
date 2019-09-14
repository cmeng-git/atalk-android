/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chatroomslist;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.conference.AdHocChatRoomProviderWrapper;
import org.atalk.android.gui.chat.conference.AdHocChatRoomWrapper;
import org.atalk.persistance.DatabaseBackend;
import org.osgi.framework.*;

import java.util.List;
import java.util.Vector;

/**
 * The <tt>AdHocChatRoomsList</tt> is the list containing all ad-hoc chat rooms.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
public class AdHocChatRoomList
{
    /**
     * The list containing all chat servers and ad-hoc rooms.
     */
    private final List<AdHocChatRoomProviderWrapper> providersList = new Vector<>();

    private SQLiteDatabase mDB;

    /**
     * Initializes the list of ad-hoc chat rooms.
     */
    public void loadList()
    {
        BundleContext bundleContext = AndroidGUIActivator.bundleContext;
        mDB = DatabaseBackend.getWritableDB();

        ServiceReference[] serRefs = null;
        try {
            serRefs = bundleContext.getServiceReferences(
                    ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }

        if (serRefs != null) {
            for (ServiceReference<ProtocolProviderService> serRef : serRefs) {
                ProtocolProviderService protocolProvider
                        = AndroidGUIActivator.bundleContext.getService(serRef);
                OperationSetAdHocMultiUserChat adHocMultiUserChatOpSet
                        = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat.class);
                if (adHocMultiUserChatOpSet != null) {
                    addChatProvider(protocolProvider);
                }
            }
        }
    }

    /**
     * Adds a chat server and all its existing ad-hoc chat rooms.
     *
     * @param pps the <tt>ProtocolProviderService</tt> corresponding to the chat server
     */
    public void addChatProvider(ProtocolProviderService pps)
    {
        AdHocChatRoomProviderWrapper chatRoomProvider = new AdHocChatRoomProviderWrapper(pps);
        providersList.add(chatRoomProvider);

        String accountUid = pps.getAccountID().getAccountUniqueID();
        String[] args = {accountUid, String.valueOf(ChatSession.MODE_MULTI)};
        String[] columns = {ChatSession.ENTITY_JID};

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.MODE + "=?", args, null, null, null);

        while (cursor.moveToNext()) {
            String chatRoomID = cursor.getString(0);
            AdHocChatRoomWrapper chatRoomWrapper
                    = new AdHocChatRoomWrapper(chatRoomProvider, chatRoomID);
            chatRoomProvider.addAdHocChatRoom(chatRoomWrapper);
        }
        cursor.close();
    }

    /**
     * Removes the corresponding server and all related ad-hoc chat rooms from this list.
     *
     * @param pps the <tt>ProtocolProviderService</tt> corresponding to the server to remove
     */
    public void removeChatProvider(ProtocolProviderService pps)
    {
        AdHocChatRoomProviderWrapper wrapper = findServerWrapperFromProvider(pps);
        if (wrapper != null)
            removeChatProvider(wrapper);
    }

    /**
     * Removes the corresponding server and all related ad-hoc chat rooms from this list.
     *
     * @param adHocChatRoomProvider the <tt>AdHocChatRoomProviderWrapper</tt> corresponding to the server to remove
     */
    private void removeChatProvider(AdHocChatRoomProviderWrapper adHocChatRoomProvider)
    {
        providersList.remove(adHocChatRoomProvider);

        AccountID accountID = adHocChatRoomProvider.getProtocolProvider().getAccountID();
        String accountUid = accountID.getAccountUniqueID();
        String[] args = {accountUid, String.valueOf(ChatSession.MODE_MULTI)};

        mDB.delete(ChatSession.TABLE_NAME, ChatSession.ACCOUNT_UID + "=? AND "
                + ChatSession.MODE + "=?", args);
    }

    /**
     * Adds a chat room to this list.
     *
     * @param adHocChatRoomWrapper the <tt>AdHocChatRoom</tt> to add
     */
    public void addAdHocChatRoom(AdHocChatRoomWrapper adHocChatRoomWrapper)
    {
        AdHocChatRoomProviderWrapper adHocChatRoomProvider
                = adHocChatRoomWrapper.getParentProvider();

        if (!adHocChatRoomProvider.containsAdHocChatRoom(adHocChatRoomWrapper))
            adHocChatRoomProvider.addAdHocChatRoom(adHocChatRoomWrapper);
    }

    /**
     * Removes the given <tt>AdHocChatRoom</tt> from the list of all ad-hoc chat rooms.
     *
     * @param adHocChatRoomWrapper the <tt>AdHocChatRoomWrapper</tt> to remove
     */
    public void removeChatRoom(AdHocChatRoomWrapper adHocChatRoomWrapper)
    {
        AdHocChatRoomProviderWrapper adHocChatRoomProvider
                = adHocChatRoomWrapper.getParentProvider();

        if (providersList.contains(adHocChatRoomProvider)) {
            adHocChatRoomProvider.removeChatRoom(adHocChatRoomWrapper);
        }
    }

    /**
     * Returns the <tt>AdHocChatRoomWrapper</tt> that correspond to the given <tt>AdHocChatRoom
     * </tt>. If the list of ad-hoc chat rooms
     * doesn't contain a corresponding wrapper - returns null.
     *
     * @param adHocChatRoom the <tt>ChatRoom</tt> that we're looking for
     * @return the <tt>ChatRoomWrapper</tt> object corresponding to the given <tt>ChatRoom</tt>
     */
    public AdHocChatRoomWrapper findChatRoomWrapperFromAdHocChatRoom(AdHocChatRoom adHocChatRoom)
    {
        for (AdHocChatRoomProviderWrapper provider : providersList) {
            AdHocChatRoomWrapper chatRoomWrapper = provider.findChatRoomWrapperForAdHocChatRoom(
                    adHocChatRoom);

            if (chatRoomWrapper != null) {
                // stored chatRooms has no chatRoom, but their id is the same as the chatRoom
                // we are searching wrapper for
                if (chatRoomWrapper.getAdHocChatRoom() == null) {
                    chatRoomWrapper.setAdHocChatRoom(adHocChatRoom);
                }
                return chatRoomWrapper;
            }
        }
        return null;
    }

    /**
     * Returns the <tt>AdHocChatRoomProviderWrapper</tt> that correspond to the given
     * <tt>ProtocolProviderService</tt>. If the list doesn't
     * contain a corresponding wrapper - returns null.
     *
     * @param protocolProvider the protocol provider that we're looking for
     * @return the <tt>AdHocChatRoomProvider</tt> object corresponding to the given
     * <tt>ProtocolProviderService</tt>
     */
    public AdHocChatRoomProviderWrapper findServerWrapperFromProvider(
            ProtocolProviderService protocolProvider)
    {
        for (AdHocChatRoomProviderWrapper chatRoomProvider : providersList) {
            if (chatRoomProvider.getProtocolProvider().equals(protocolProvider)) {
                return chatRoomProvider;
            }
        }
        return null;
    }
}
