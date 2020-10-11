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
import android.os.*;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.widgets.UnreadCountCustomView;
import org.atalk.crypto.CryptoFragment;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiFragment;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.*;
import org.jxmpp.util.XmppStringUtils;

import java.util.*;

import androidx.fragment.app.FragmentActivity;
import timber.log.Timber;

/**
 * The user interface that allows user to have direct access the previous chat sessions.
 *
 * @author Eng Chong Meng
 */
public class ChatSessionFragment extends OSGiFragment
        implements View.OnClickListener, ContactPresenceStatusListener, ChatRoomListChangeListener,
        EntityListHelper.TaskCompleted
{
    /**
     * bit-7 of the ChatSession#STATUS is to hide session from UI if set
     *
     * @see ChatFragment#MSGTYPE_MASK
     */
    public static int SESSION_HIDDEN = 0x80;

    /**
     * The list of chat session records
     */
    private List<ChatSessionRecord> sessionRecords = new ArrayList<>();

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
    private Map<String, MetaContact> mMetaContacts = new LinkedHashMap<>();

    /**
     * A map of <Entity Jid, ChatRoomWrapper>
     */
    private Map<String, ChatRoomWrapper> chatRoomWrapperList = new LinkedHashMap<>();

    /**
     * A map of <Account Jid, ChatRoomProviderWrapper>
     */
    private Map<String, ChatRoomProviderWrapper> mucRCProviderList = new LinkedHashMap<>();

    private List<String> chatRoomList = new ArrayList<>();

    /**
     * A map reference of entity to ChatRecordViewHolder for the unread message count update
     */
    private static Map<String, ChatRecordViewHolder> crViewHolderMap = new HashMap<>();

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
    public void onAttach(Context context)
    {
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
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View contentView = inflater.inflate(R.layout.chat_session, container, false);
        mTitle = contentView.findViewById(R.id.chat_session);

        chatSessionListView = contentView.findViewById(R.id.chat_sessionListView);
        chatSessionAdapter = new ChatSessionAdapter(inflater);
        chatSessionListView.setAdapter(chatSessionAdapter);

        chatSessionListView.setOnItemClickListener(listItemClickListener);

        // Using the contextual action mode with multi-selection
        chatSessionListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        chatSessionListView.setMultiChoiceModeListener(mMultiChoiceListener);

        return contentView;
    }

    /**
     * Adapter displaying all the available chat session for user selection.
     */
    private class ChatSessionAdapter extends BaseAdapter
    {
        public LayoutInflater mInflater;
        public int CHAT_SESSION_RECORD = 1;

        private ChatSessionAdapter(LayoutInflater inflater)
        {
            sessionRecords.clear();
            mMetaContacts.clear();
            mInflater = inflater;

            new InitChatRoomWrapper().execute();
            new getChatSessionRecords().execute();
        }

        @Override
        public int getCount()
        {
            return sessionRecords.size();
        }

        @Override
        public Object getItem(int position)
        {
            return sessionRecords.get(position);
        }

        /**
         * Remove the sessionRecord by its sessionUuid
         *
         * @param ssesionUuid session Uuid
         */
        public void removeItem(String ssesionUuid)
        {
            int index = 0;
            for (ChatSessionRecord cdRecord : sessionRecords) {
                if (cdRecord.getSessionUuid().equals(ssesionUuid))
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
        public void removeItem(int index)
        {
            sessionRecords.remove(index);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public int getItemViewType(int position)
        {
            return CHAT_SESSION_RECORD;
        }

        @Override
        public int getViewTypeCount()
        {
            return 1;
        }

        @Override
        public boolean isEmpty()
        {
            return getCount() == 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
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

            // Must init child Tag here as reused convertView may not necessary contains the correct reference
            View chatSessionView = convertView.findViewById(R.id.chatSessionView);
            chatSessionView.setTag(chatRecordViewHolder);

            // setOnClickListener interfere with setMultiChoiceModeListener; use AdapterView.OnItemClickListener()
            // chatSessionView.setOnClickListener(ChatSessionFragment.this);

            crViewHolderMap.put(chatSessionRecord.getEntityId(), chatRecordViewHolder);

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
        private class getChatSessionRecords extends AsyncTask<Void, Void, Void>
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                initMetaContactList();
                Collection<ChatSessionRecord> csRecordPPS;

                Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
                for (ProtocolProviderService pps : providers) {
                    if ((pps.getConnection() != null) && pps.getConnection().isAuthenticated()) {
                        addContactStatusListener(pps);
                        String userUid = pps.getAccountID().getAccountUniqueID();

                        csRecordPPS = mMHS.findSessionByEndDate(userUid, new Date());
                        if (csRecordPPS.size() != 0)
                            sessionRecords.addAll(csRecordPPS);
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result)
            {
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
    public void updateUnreadCount(final String entityJid, final int count)
    {
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
     * Adds the given <tt>addContactPresenceStatusListener</tt> to listen for contact presence status change.
     *
     * @param pps the <tt>ProtocolProviderService</tt> for which we add the listener
     */
    private void addContactStatusListener(ProtocolProviderService pps)
    {
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
    private void setChatType(ImageView chatTypeView, int chatType)
    {
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

    private void setTitle()
    {
        String title = aTalkApp.getResString(R.string.service_gui_RECENT_MESSAGES)
                + " (" + sessionRecords.size() + ")";
        mTitle.setText(title);
    }

    // Handle only if contactImpl instanceof MetaContact;
    private boolean isShowCallBtn(Object contactImpl)
    {
        return (contactImpl instanceof MetaContact)
                && isShowButton((MetaContact) contactImpl, OperationSetBasicTelephony.class);
    }

    private boolean isShowVideoCallBtn(Object contactImpl)
    {
        return (contactImpl instanceof MetaContact)
                && isShowButton((MetaContact) contactImpl, OperationSetVideoTelephony.class);
    }

    private boolean isShowButton(MetaContact metaContact, Class<? extends OperationSet> opSetClass)
    {
        return ((metaContact != null) && metaContact.getOpSetSupportedContact(opSetClass) != null);
    }

    /**
     * Initializes the adapter data.
     */
    public void initMetaContactList()
    {
        MetaContactListService contactListService = AndroidGUIActivator.getContactListService();
        if (contactListService != null) {
            addContacts(contactListService.getRoot());
        }
    }

    /**
     * Adds all child contacts for the given <tt>group</tt>. Omit metaGroup of zero child.
     *
     * @param group the group, which child contacts to add
     */
    private void addContacts(MetaContactGroup group)
    {
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
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt)
    {
        uiHandler.post(() -> chatSessionAdapter.notifyDataSetChanged());
    }

    /**
     * Indicates that a change has occurred in the chatRoom List.
     */
    @Override
    public void contentChanged(ChatRoomListChangeEvent evt)
    {
        uiHandler.post(() -> chatSessionAdapter.notifyDataSetChanged());
    }

    @Override
    public void onTaskComplete(Integer result)
    {
        if (result > 0) {
            sessionRecords.clear();
            mMetaContacts.clear();
            chatSessionAdapter.new getChatSessionRecords().execute();
        }
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms.
     * Add all available server's chatRooms to the chatRoomList when providers changed.
     */
    private class InitChatRoomWrapper extends AsyncTask<Void, Void, Void>
    {
        @Override
        protected Void doInBackground(Void... params)
        {
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

    /**
     * Use OnItemClickListener to startChat; otherwise onClickListener interfere with MultiChoiceModeListener
     */
    private AdapterView.OnItemClickListener listItemClickListener = (parent, view, position, id)
            -> onClick(view.findViewById(R.id.chatSessionView));

    @Override
    public void onClick(View view)
    {
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
                aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, entityJid);
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
                                    .replace(android.R.id.content, extPhone).commit();
                            break;
                        }

                    case R.id.callVideoButton:
                        boolean isVideoCall = viewHolder.callVideoButton.isPressed();
                        AndroidCallUtil.createAndroidCall(aTalkApp.getGlobalContext(), jid,
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

    /**
     * cmeng: when metaContact is owned by two different user accounts, the first launched chatSession
     * will take predominant over subsequent metaContact chat session launches by another account
     */
    public void startChat(MetaContact metaContact)
    {
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, metaContact.getDisplayName());
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
     * @param descriptor <tt>MetaContact</tt> for which chat activity will be started.
     */
    private void startChatActivity(Object descriptor)
    {
        Intent chatIntent = ChatSessionManager.getChatIntent(descriptor);
        try {
            startActivity(chatIntent);
        } catch (Exception ex) {
            Timber.w("Failed to start chat with %s: %s", descriptor, ex.getMessage());
        }
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private void createOrJoinChatRoom(String userId, String chatRoomID)
    {
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
                aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_CREATE_ERROR, chatRoomID);
                return;
            }

            // Allow removal of new chatRoom if join failed
            if (AndroidGUIActivator.getConfigurationService()
                    .getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
                final ChatRoomWrapper crWrapper = chatRoomWrapper;

                chatRoomWrapper.addPropertyChangeListener(evt -> {
                    if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP))
                        return;

                    // if we failed for some , then close and remove the room
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

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private AbsListView.MultiChoiceModeListener mMultiChoiceListener = new AbsListView.MultiChoiceModeListener()
    {
        int cPos;
        int headerCount;
        int checkListSize;

        MenuItem mDelete;
        MenuItem mSelectAll;

        SparseBooleanArray checkedList;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked)
        {
            // Here you can do something when items are selected/de-selected
            checkedList = chatSessionListView.getCheckedItemPositions();
            checkListSize = checkedList.size();
            int checkedItemCount = chatSessionListView.getCheckedItemCount();

            // Position must be aligned to the number of header views included
            cPos = position - headerCount;

            mode.invalidate();
            chatSessionListView.setSelection(position);
            mode.setTitle(String.valueOf(checkedItemCount));
        }

        // Called when the user selects a menu item. On action picked, close the CAB i.e. mode.finish();
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            int cType;
            ChatSessionRecord sessionRecord;

            switch (item.getItemId()) {
                case R.id.cr_select_all:
                    int size = chatSessionAdapter.getCount();
                    if (size < 2)
                        return true;

                    for (int i = 0; i < size; i++) {
                        cPos = i + headerCount;
                        checkedList.put(cPos, true);
                        chatSessionListView.setSelection(cPos);
                    }
                    checkListSize = size;
                    mode.invalidate();
                    mode.setTitle(String.valueOf(size));
                    return true;

                case R.id.cr_delete:
                    // List of records with sessionUuids in chatSessionAdapter to be deleted.
                    List<String> sessionUuidDel = new ArrayList<>();

                    for (int i = 0; i < checkListSize; i++) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount;
                            cType = chatSessionAdapter.getItemViewType(cPos);
                            if (cType == chatSessionAdapter.CHAT_SESSION_RECORD) {
                                sessionRecord = (ChatSessionRecord) chatSessionAdapter.getItem(cPos);
                                if (sessionRecord != null) {
                                    String sessionUuid = sessionRecord.getSessionUuid();
                                    sessionUuidDel.add(sessionUuid);

                                    /*
                                     * Hide the session record if it is still a valid session record
                                     * otherwise purge both the session record and its associated messages from DB
                                     */
                                    String entityJid = sessionRecord.getEntityId();
                                    if (mMetaContacts.containsKey(entityJid)
                                            || chatRoomWrapperList.containsKey(entityJid)) {
                                        mMHS.setSessionChatType(sessionUuid, sessionRecord.getChatType() | SESSION_HIDDEN);
                                        Timber.d("Hide chatSession for entityJid: %s (%s)", entityJid, sessionUuid);
                                    }
                                    else {
                                        int msgCount = mMHS.getMessageCountForSessionUuid(sessionUuid);
                                        mMHS.purgeLocallyStoredHistory(Collections.singletonList(sessionUuid), true);
                                        Timber.w("Purged (%s) messages for invalid entityJid: %s (%s)",
                                                msgCount, entityJid, sessionUuid);
                                    }
                                }
                            }
                        }
                    }
                    if (!sessionUuidDel.isEmpty()) {
                        // Must do this inorder for notifyDataSetChanged to have effect;
                        // Also outside the checkListSize loop so it does not affect the cPos for record fetch.
                        for (String sessionUuid : sessionUuidDel) {
                            chatSessionAdapter.removeItem(sessionUuid);
                        }

                        // reset the value so CryptoFragment reload and re-init chatType when use open the chatSession again
                        CryptoFragment.resetEncryptionChoice(null);
                        chatSessionAdapter.notifyDataSetChanged();
                    }
                    mode.finish();
                    return true;

                default:
                    return false;
            }
        }

        // Called when the action mActionMode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            // Inflate the menu for the CAB
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.call_history_menu, menu);
            headerCount = chatSessionListView.getHeaderViewsCount();

            mDelete = menu.findItem(R.id.cr_delete);
            mSelectAll = menu.findItem(R.id.cr_select_all);

            return true;
        }

        // Called each time the action mActionMode is shown. Always called after onCreateActionMode,
        // but may be called multiple times if the mActionMode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            // Here you can perform updates to the CAB due to an invalidate() request
            // Return false if nothing is done.
            return false;
        }

        // Called when the user exits the action mActionMode
        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            // Here you can make any necessary updates to the activity when
            // the CAB is removed. By default, selected items are deselected/unchecked.
            ActionMode mActionMode = null;
        }
    };

    private static class ChatRecordViewHolder
    {
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
