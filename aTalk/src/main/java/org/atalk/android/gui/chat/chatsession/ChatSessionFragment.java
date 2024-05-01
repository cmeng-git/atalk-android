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
package org.atalk.android.gui.chat.chatsession;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent;
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.muc.MUCService;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.widgets.UnreadCountCustomView;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiFragment;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.DomainJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;

import timber.log.Timber;

/**
 * The user interface that allows user to have direct access to the previous chat sessions.
 *
 * @author Eng Chong Meng
 */
public class ChatSessionFragment extends OSGiFragment implements View.OnClickListener, View.OnLongClickListener,
        EntityListHelper.TaskCompleteListener, ContactPresenceStatusListener, ChatRoomListChangeListener {
    /**
     * bit-7 of the ChatSession#STATUS is to hide session from UI if set
     *
     * @see ChatFragment#MSGTYPE_MASK
     */
    public static int SESSION_HIDDEN = 0x80;

    /**
     * The list of chat session records
     */
    private final List<ChatSessionRecord> sessionRecords = new ArrayList<>();

    /**
     * The Chat session adapter for user selection
     */
    private static ChatSessionAdapter chatSessionAdapter;

    /**
     * The chat session list view representing the chat session.
     */
    private ListView chatSessionListView;

    /**
     * A map of <Entity Jid, MetaContact>
     */
    private final Map<String, MetaContact> mMetaContacts = new LinkedHashMap<>();

    /**
     * A map of <Entity Jid, ChatRoomWrapper>
     */
    private final Map<String, ChatRoomWrapper> chatRoomWrapperList = new LinkedHashMap<>();

    /**
     * A map of <Account Jid, ChatRoomProviderWrapper>
     */
    private final Map<String, ChatRoomProviderWrapper> mucRCProviderList = new LinkedHashMap<>();

    private List<String> chatRoomList = new ArrayList<>();

    /**
     * A map reference of entity to ChatRecordViewHolder for the unread message count update
     */
    private static final Map<String, ChatRecordViewHolder> crViewHolderMap = new HashMap<>();

    private MUCServiceImpl mucService;

    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    protected final Handler uiHandler = OSGiActivity.uiHandler;

    /**
     * View for room configuration title description from the room configuration form
     */
    private TextView mTitle;
    private static Context mContext = null;
    private MessageHistoryServiceImpl mMHS;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        mMHS = MessageHistoryActivator.getMessageHistoryService();

        mucService = MUCActivator.getMUCService();
        if (mucService != null)
            mucService.addChatRoomListChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView = inflater.inflate(R.layout.chat_session, container, false);
        mTitle = contentView.findViewById(R.id.chat_session);

        chatSessionListView = contentView.findViewById(R.id.chat_sessionListView);
        chatSessionAdapter = new ChatSessionAdapter(inflater);
        chatSessionListView.setAdapter(chatSessionAdapter);
        return contentView;
    }

    /**
     * Adapter displaying all the available chat session for user selection.
     */
    private class ChatSessionAdapter extends BaseAdapter {
        public LayoutInflater mInflater;
        public int CHAT_SESSION_RECORD = 1;

        private ChatSessionAdapter(LayoutInflater inflater) {
            mInflater = inflater;
            new InitChatRoomWrapper().execute();
            new getChatSessionRecords(new Date()).execute();
        }

        @Override
        public int getCount() {
            return sessionRecords.size();
        }

        @Override
        public Object getItem(int position) {
            return sessionRecords.get(position);
        }

        /**
         * Remove the sessionRecord by its sessionUuid
         *
         * @param sessionUuid session Uuid
         */
        public void removeItem(String sessionUuid) {
            int index = 0;
            for (ChatSessionRecord cdRecord : sessionRecords) {
                if (cdRecord.getSessionUuid().equals(sessionUuid))
                    break;
                index++;
            }

            // ConcurrentModificationException if perform within the loop
            if (index < sessionRecords.size())
                removeItem(index);
        }

        /**
         * Remove item in sessionRecords by the given index
         * Note: caller must adjust the index if perform remove in loop
         *
         * @param index of the sessionRecord to be deleted
         */
        public void removeItem(int index) {
            sessionRecords.remove(index);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return CHAT_SESSION_RECORD;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ChatRecordViewHolder chatRecordViewHolder;
            ChatSessionRecord chatSessionRecord = sessionRecords.get(position);

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.chat_session_row, parent, false);

                chatRecordViewHolder = new ChatRecordViewHolder();
                chatRecordViewHolder.avatar = convertView.findViewById(R.id.avatar);
                chatRecordViewHolder.entityJId = convertView.findViewById(R.id.entityJid);
                chatRecordViewHolder.chatType = convertView.findViewById(R.id.chatType);
                chatRecordViewHolder.chatMessage = convertView.findViewById(R.id.chatMessage);

                chatRecordViewHolder.unreadCount = convertView.findViewById(R.id.unread_count);
                chatRecordViewHolder.unreadCount.setTag(chatRecordViewHolder);

                chatRecordViewHolder.callButton = convertView.findViewById(R.id.callButton);
                chatRecordViewHolder.callButton.setOnClickListener(ChatSessionFragment.this);
                chatRecordViewHolder.callButton.setTag(chatRecordViewHolder);

                chatRecordViewHolder.callVideoButton = convertView.findViewById(R.id.callVideoButton);
                chatRecordViewHolder.callVideoButton.setOnClickListener(ChatSessionFragment.this);
                chatRecordViewHolder.callVideoButton.setTag(chatRecordViewHolder);

                convertView.setTag(chatRecordViewHolder);
            }
            else {
                chatRecordViewHolder = (ChatRecordViewHolder) convertView.getTag();
            }

            chatRecordViewHolder.childPosition = position;
            chatRecordViewHolder.sessionUuid = chatSessionRecord.getSessionUuid();
            crViewHolderMap.put(chatSessionRecord.getEntityId(), chatRecordViewHolder);

            convertView.setOnClickListener(ChatSessionFragment.this);
            convertView.setOnLongClickListener(ChatSessionFragment.this);

            int unreadCount = 0;
            MetaContact metaContact = null;
            String entityId = chatSessionRecord.getEntityId();

            if (chatSessionRecord.getChatMode() == ChatSession.MODE_SINGLE) {
                BareJid bareJid = chatSessionRecord.getEntityBareJid();
                byte[] avatar = AvatarManager.getAvatarImageByJid(bareJid);
                if (avatar != null) {
                    chatRecordViewHolder.avatar.setImageBitmap(AndroidImageUtil.bitmapFromBytes(avatar));
                }
                else {
                    chatRecordViewHolder.avatar.setImageResource(R.drawable.person_photo);
                }
                metaContact = mMetaContacts.get(entityId);
                if (metaContact != null)
                    unreadCount = metaContact.getUnreadCount();
            }
            else {
                chatRecordViewHolder.avatar.setImageResource(R.drawable.ic_chatroom);
                ChatRoomWrapper crpWrapper = chatRoomWrapperList.get(entityId);
                if (crpWrapper != null)
                    unreadCount = crpWrapper.getUnreadCount();
            }

            updateUnreadCount(entityId, unreadCount);
            chatRecordViewHolder.callButton.setVisibility(isShowCallBtn(metaContact) ? View.VISIBLE : View.GONE);
            chatRecordViewHolder.callVideoButton.setVisibility(isShowVideoCallBtn(metaContact) ? View.VISIBLE : View.GONE);

            setChatType(chatRecordViewHolder.chatType, chatSessionRecord.getChatType());
            chatRecordViewHolder.entityJId.setText(chatSessionRecord.getEntityId());

            return convertView;
        }

        /**
         * Retrieve all the chat sessions saved locally in the database
         * Populate the fragment with the chat session for each getView()
         */
        private class getChatSessionRecords extends AsyncTask<Void, Void, Void> {
            final Date mEndDate;

            public getChatSessionRecords(Date date) {
                mEndDate = date;
                sessionRecords.clear();
                mMetaContacts.clear();
                chatSessionListView.clearChoices();
            }

            @Override
            protected Void doInBackground(Void... params) {
                initMetaContactList();
                Collection<ChatSessionRecord> csRecordPPS;

                Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
                for (ProtocolProviderService pps : providers) {
                    if ((pps.getConnection() != null) && pps.getConnection().isAuthenticated()) {
                        addContactStatusListener(pps);
                        String userUid = pps.getAccountID().getAccountUid();

                        csRecordPPS = mMHS.findSessionByEndDate(userUid, mEndDate);
                        if (csRecordPPS.size() != 0)
                            sessionRecords.addAll(csRecordPPS);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                if (sessionRecords.size() > 0) {
                    chatSessionAdapter.notifyDataSetChanged();
                }
                setTitle();
            }
        }
    }

    /**
     * Updates the entity unread message count and the last message.
     * Hide widget if (count == 0)
     *
     * @param entityJid the entity Jid of MetaContact or ChatRoom ID
     * @param count the message unread count
     */
    public void updateUnreadCount(final String entityJid, final int count) {
        if ((StringUtils.isNotEmpty(entityJid) && (chatSessionAdapter != null))) {
            final ChatRecordViewHolder chatRecordViewHolder = crViewHolderMap.get(entityJid);
            if (chatRecordViewHolder == null)
                return;

            runOnUiThread(() -> {
                if (count == 0) {
                    chatRecordViewHolder.unreadCount.setVisibility(View.GONE);
                }
                else {
                    chatRecordViewHolder.unreadCount.setVisibility(View.VISIBLE);
                    chatRecordViewHolder.unreadCount.setUnreadCount(count);
                }

                String msgBody = mMHS.getLastMessageForSessionUuid(chatRecordViewHolder.sessionUuid);
                chatRecordViewHolder.chatMessage.setText(msgBody);
            });
        }
    }

    /**
     * Adds the given <code>addContactPresenceStatusListener</code> to listen for contact presence status change.
     *
     * @param pps the <code>ProtocolProviderService</code> for which we add the listener
     */
    private void addContactStatusListener(ProtocolProviderService pps) {
        OperationSetPresence presenceOpSet = pps.getOperationSet(OperationSetPresence.class);
        if (presenceOpSet != null) {
            presenceOpSet.removeContactPresenceStatusListener(this);
            presenceOpSet.addContactPresenceStatusListener(this);
        }
    }

    /**
     * Sets the chat type.
     *
     * @param chatTypeView the chat type state image view
     * @param chatType the chat session Type.
     */
    private void setChatType(ImageView chatTypeView, int chatType) {
        int iconId;

        switch (chatType) {
            case ChatFragment.MSGTYPE_OMEMO:
                iconId = R.drawable.encryption_omemo;
                break;
            case ChatFragment.MSGTYPE_OTR:
            case ChatFragment.MSGTYPE_OTR_UA:
                iconId = R.drawable.encryption_otr;
                break;
            case ChatFragment.MSGTYPE_NORMAL:
            case ChatFragment.MSGTYPE_MUC_NORMAL:
            default:
                iconId = R.drawable.encryption_none;
                break;
        }
        chatTypeView.setImageResource(iconId);
    }

    private void setTitle() {
        String title = aTalkApp.getResString(R.string.recent_messages)
                + " (" + sessionRecords.size() + ")";
        mTitle.setText(title);
    }

    // Handle only if contactImpl instanceof MetaContact;
    private boolean isShowCallBtn(Object contactImpl) {
        return (contactImpl instanceof MetaContact)
                && isShowButton((MetaContact) contactImpl, OperationSetBasicTelephony.class);
    }

    private boolean isShowVideoCallBtn(Object contactImpl) {
        return (contactImpl instanceof MetaContact)
                && isShowButton((MetaContact) contactImpl, OperationSetVideoTelephony.class);
    }

    private boolean isShowButton(MetaContact metaContact, Class<? extends OperationSet> opSetClass) {
        return ((metaContact != null) && metaContact.getOpSetSupportedContact(opSetClass) != null);
    }

    /**
     * Initializes the adapter data.
     */
    public void initMetaContactList() {
        MetaContactListService contactListService = AndroidGUIActivator.getContactListService();
        if (contactListService != null) {
            addContacts(contactListService.getRoot());
        }
    }

    /**
     * Adds all child contacts for the given <code>group</code>. Omit metaGroup of zero child.
     *
     * @param group the group, which child contacts to add
     */
    private void addContacts(MetaContactGroup group) {
        if (group.countChildContacts() > 0) {

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            Iterator<MetaContact> childContacts = group.getChildContacts();
            while (childContacts.hasNext()) {
                MetaContact metaContact = childContacts.next();
                String contactId = metaContact.getDefaultContact().getAddress();
                mMetaContacts.put(contactId, metaContact);
            }
        }

        Iterator<MetaContactGroup> subGroups = group.getSubgroups();
        while (subGroups.hasNext()) {
            addContacts(subGroups.next());
        }
    }

    @Override
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt) {
        uiHandler.post(() -> chatSessionAdapter.notifyDataSetChanged());
    }

    /**
     * Indicates that a change has occurred in the chatRoom List.
     */
    @Override
    public void contentChanged(ChatRoomListChangeEvent evt) {
        uiHandler.post(() -> chatSessionAdapter.notifyDataSetChanged());
    }

    @Override
    public void onTaskComplete(int msgCount, List<String> deletedUUIDs) {
        aTalkApp.showToastMessage(R.string.history_purge_count, msgCount);
        if (msgCount > 0) {
            chatSessionAdapter.new getChatSessionRecords(new Date()).execute();
        }
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms.
     * Add all available server's chatRooms to the chatRoomList when providers changed.
     */
    private class InitChatRoomWrapper extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            chatRoomList.clear();
            chatRoomWrapperList.clear();

            List<ChatRoomProviderWrapper> providers = mucService.getChatRoomProviders();
            for (ChatRoomProviderWrapper crpWrapper : providers) {
                ProtocolProviderService pps = crpWrapper.getProtocolProvider();
                String mAccount = pps.getAccountID().getAccountJid();
                mucRCProviderList.put(mAccount, crpWrapper);

                // local chatRooms
                chatRoomList = mucService.getExistingChatRooms(pps);

                // server chatRooms
                List<String> sChatRoomList = mucService.getExistingChatRooms(crpWrapper);
                for (String sRoom : sChatRoomList) {
                    if (!chatRoomList.contains(sRoom)) {
                        chatRoomList.add(sRoom);
                    }
                }

                // populate the chatRoomWrapperList for all the chatRooms
                for (String room : chatRoomList) {
                    chatRoomWrapperList.put(room, mucService.findChatRoomWrapperFromChatRoomID(room, pps));
                }
            }
            return null;
        }
    }

    @Override
    public void onClick(View view) {
        ChatRecordViewHolder viewHolder;
        ChatSessionRecord chatSessionRecord;
        String accountId;
        String entityJid;

        Object object = view.getTag();
        if (object instanceof ChatRecordViewHolder) {
            viewHolder = (ChatRecordViewHolder) object;
            int childPos = viewHolder.childPosition;
            chatSessionRecord = sessionRecords.get(childPos);
            if (chatSessionRecord == null)
                return;

            accountId = chatSessionRecord.getAccountUserId();
            entityJid = chatSessionRecord.getEntityId();
        }
        else {
            Timber.w("Clicked item is not a valid MetaContact or chatRoom");
            return;
        }

        if (chatSessionRecord.getChatMode() == ChatSession.MODE_SINGLE) {
            MetaContact metaContact = mMetaContacts.get(entityJid);
            if (metaContact == null) {
                aTalkApp.showToastMessage(R.string.contact_invalid, entityJid);
                return;
            }

            Contact contact = metaContact.getDefaultContact();
            if (contact != null) {
                Jid jid = chatSessionRecord.getEntityBareJid();

                switch (view.getId()) {
                    case R.id.chatSessionView:
                        startChat(metaContact);
                        break;

                    case R.id.callButton:
                        if (jid instanceof DomainBareJid) {
                            TelephonyFragment extPhone = TelephonyFragment.newInstance(contact.getAddress());
                            ((FragmentActivity) mContext).getSupportFragmentManager().beginTransaction()
                                    .replace(android.R.id.content, extPhone, TelephonyFragment.TELEPHONY_TAG).commit();
                            break;
                        }

                    case R.id.callVideoButton:
                        boolean isVideoCall = viewHolder.callVideoButton.isPressed();
                        AndroidCallUtil.createAndroidCall(aTalkApp.getInstance(), jid,
                                viewHolder.callVideoButton, isVideoCall);
                        break;

                    default:
                        break;
                }
            }
        }
        else {
            createOrJoinChatRoom(accountId, entityJid);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        ChatRecordViewHolder viewHolder;
        ChatSessionRecord chatSessionRecord;

        Object object = view.getTag();
        if (object instanceof ChatRecordViewHolder) {
            viewHolder = (ChatRecordViewHolder) object;
            int childPos = viewHolder.childPosition;
            chatSessionRecord = sessionRecords.get(childPos);
            if (chatSessionRecord != null)
                showPopupMenu(view, chatSessionRecord);
        }
        return true;
    }

    /**
     * Inflates chatSession Item popup menu.
     * Avoid using android contextMenu (in fragment) - truncated menu list
     *
     * @param holderView click view.
     * @param chatSessionRecord an instance of ChatSessionRecord for this view.
     */
    public void showPopupMenu(View holderView, ChatSessionRecord chatSessionRecord) {
        PopupMenu popup = new PopupMenu(mContext, holderView);
        Menu menu = popup.getMenu();
        popup.getMenuInflater().inflate(R.menu.session_ctx_menu, menu);
        popup.setOnMenuItemClickListener(new PopupMenuItemClick(chatSessionRecord));

        if (ChatSession.MODE_SINGLE == chatSessionRecord.getChatMode())
            menu.findItem(R.id.erase_contact_chat_history).setVisible(true);
        else
            menu.findItem(R.id.erase_chatroom_history).setVisible(true);
        popup.show();
    }

    /**
     * Interface responsible for receiving menu item click events if the items
     * themselves do not have individual item click listeners.
     */
    private class PopupMenuItemClick implements PopupMenu.OnMenuItemClickListener {
        private ChatSessionRecord mSessionRecord;

        PopupMenuItemClick(ChatSessionRecord sessionRecord) {
            mSessionRecord = sessionRecord;
        }

        /**
         * This method will be invoked when a menu item is clicked if the item
         * itself did not already handle the event.
         *
         * @param item the menu item that was clicked
         *
         * @return {@code true} if the event was handled, {@code false} otherwise
         */
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.erase_contact_chat_history:
                case R.id.erase_chatroom_history:
                    EntityListHelper.eraseEntityChatHistory(ChatSessionFragment.this, mSessionRecord, null, null);
                    return true;

                case R.id.ctx_menu_exit:
                    return true;

                default:
                    return false;
            }
        }
    }

    /**
     * cmeng: when metaContact is owned by two different user accounts, the first launched chatSession
     * will take predominant over subsequent metaContact chat session launches by another account
     */
    public void startChat(MetaContact metaContact) {
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.contact_invalid, metaContact.getDisplayName());
            return;
        }

        // Default for domainJid - always show chat session
        if (metaContact.getDefaultContact().getJid() instanceof DomainJid) {
            startChatActivity(metaContact);
            return;
        }

        if (!metaContact.getContactsForOperationSet(OperationSetBasicInstantMessaging.class).isEmpty()) {
            startChatActivity(metaContact);
        }
    }

    /**
     * Starts the chat activity for the given metaContact.
     *
     * @param sessionRecords <code>MetaContact</code> for which chat activity will be started.
     */
    private void startChatActivity(Object sessionRecords) {
        Intent chatIntent = ChatSessionManager.getChatIntent(sessionRecords);
        try {
            startActivity(chatIntent);
        } catch (Exception ex) {
            Timber.w("Failed to start chat with %s: %s", sessionRecords, ex.getMessage());
        }
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private void createOrJoinChatRoom(String userId, String chatRoomID) {
        Collection<String> contacts = new ArrayList<>();
        String reason = "Let's chat";

        String nickName = XmppStringUtils.parseLocalpart(userId);
        String password = null;

        // create new if chatRoom does not exist
        ProtocolProviderService pps = mucRCProviderList.get(userId).getProtocolProvider();
        ChatRoomWrapper chatRoomWrapper = chatRoomWrapperList.get(chatRoomID);

        if (chatRoomWrapper != null) {
            nickName = chatRoomWrapper.getNickName();
            password = chatRoomWrapper.loadPassword();
        }
        else {
            // Just create chatRoomWrapper without joining as nick and password options are not available
            chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
                    reason, false, false, true, chatRoomList.contains(chatRoomID));

            // Return without open the chat room, the protocol failed to create a chat room (null)
            if ((chatRoomWrapper == null) || (chatRoomWrapper.getChatRoom() == null)) {
                aTalkApp.showToastMessage(R.string.chatroom_create_error, chatRoomID);
                return;
            }

            // Allow removal of new chatRoom if join failed
            if (AndroidGUIActivator.getConfigurationService()
                    .getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
                final ChatRoomWrapper crWrapper = chatRoomWrapper;

                chatRoomWrapper.addPropertyChangeListener(evt -> {
                    if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP))
                        return;

                    // if we failed for some reason, then close and remove the room
                    AndroidGUIActivator.getUIService().closeChatRoomWindow(crWrapper);
                    MUCActivator.getMUCService().removeChatRoom(crWrapper);
                });
            }
        }

        chatRoomWrapper.setNickName(nickName);
        byte[] pwdByte = StringUtils.isEmpty(password) ? null : password.getBytes();
        mucService.joinChatRoom(chatRoomWrapper, nickName, pwdByte, null);

        Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
        mContext.startActivity(chatIntent);
    }

    private static class ChatRecordViewHolder {
        ImageView avatar;
        ImageView chatType;
        ImageView callButton;
        ImageView callVideoButton;
        TextView entityJId;
        TextView chatMessage;
        int childPosition;
        String sessionUuid;
        UnreadCountCustomView unreadCount;
    }
}
