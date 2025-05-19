/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.Intent;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageListener;
import net.java.sip.communicator.service.protocol.event.MessageReceiptListener;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.chat.conference.AdHocChatRoomWrapper;
import org.atalk.android.gui.chat.conference.ConferenceChatManager;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.chat.filetransfer.FileTransferActivator;
import org.atalk.android.plugin.textspeech.TTSService;
import org.atalk.impl.timberlog.TimberLog;
import org.atalk.persistance.FileBackend;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.mam.MamManager;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import timber.log.Timber;

/**
 * The <code>ChatPanel</code>, <code>ChatActivity</code>, <code>ChatController</code> and <code>ChatFragment</code>
 * together formed the frontend interface to the user, where users can write and send messages
 * (ChatController), view received and sent messages (ChatFragment). A ChatPanel is created for a
 * contact or for a group of contacts in case of a chat conference. There is always one default
 * contact for the chat, which is the first contact which was added to the chat. Each "Chat GUI'
 * constitutes a fragment page access/scroll to view via the ChatPagerAdapter.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatPanel implements Chat, MessageListener, MessageReceiptListener {
    /**
     * Number of history messages to be returned from loadHistory call.
     * Limits the amount of messages being loaded at one time.
     */
    private static final int HISTORY_CHUNK_SIZE = 30;
    private static final int MAM_PAGE_SIZE = 50;
    /**
     * The underlying <code>MetaContact</code>, we're chatting with.
     */
    private final MetaContact mMetaContact;

    // An object reference containing either a MetaContact or ChatRoomWrapper
    private final Object mDescriptor;

    /**
     * The chatType for which the message will be send for method not using Transform process
     * i.e. OTR. This is also the master copy where other will update or refer to.
     * The state may also be change by the under lying signal protocol based on the current
     * signalling condition.
     */
    private int mChatType;

    /**
     * The current chat transport.
     */
    private ChatTransport mCurrentChatTransport;

    /**
     * The chat history filter for retrieving history messages.
     */
    private final String[] chatHistoryFilter = ChatSession.chatHistoryFilter;

    /**
     * msgCache: Messages cache used by this session; to cache any received message when the session
     * chatFragment has not yet opened once. msgCache is a mirror image of the DisplayMessages show
     * in ChatSession UI, and get updated with history messages retrieved by user. This msgCache is
     * always return when user resume the chatSession (chatListAdapter is empty). There the contents
     * must kept up to date with the ChatSession UI messages.
     * <p>
     * Important: when historyLog is disabled i.e. all messages exchanges are only saved in msgCache.
     * <p>
     * Use CopyOnWriteArrayList instead to avoid ChatFragment#prependMessages ConcurrentModificationException
     */
    private List<ChatMessage> msgCache = new CopyOnWriteArrayList<>();

    /**
     * Synchronization root for messages cache.
     */
    private final Object cacheLock = new Object();

    private Date mLastMsgFetchDate = null;

    /**
     * Current chat session type: mChatSession can either be one of the following:
     * MetaContactChatSession, ConferenceChatSession or AdHocConferenceChatSession
     */
    private ChatSession mChatSession;

    /**
     * Chat identifier uniquely identify this chat session.
     * The Id either be MetaContactID, ChatRoomID or AdHocChatRoomID
     * e.g. mcUID: 1567990106229240922678;  ChatRoomID: chatroom@conference.example.org
     */
    private String mChatId;

    /**
     * Flag indicates if the history has been loaded (it must be done only once;
     * and all the next messages are cached through the listeners mechanism).
     */
    private boolean historyLoaded = false;

    private final MessageHistoryServiceImpl mMHS;

    /**
     * Last message received timestamp set in mamQuery access, to block process of delayed messages
     * if mamQuery occurs before delayed messages are received on user login.
     */
    private long mamDateTS = -1L;

    /**
     * Flag indicates that mam access has been attempted when chat session if first launched.
     * Flag is set to true when user is registered and mam retrieval is attempted.
     */
    private boolean mamChecked = false;

    /**
     * Blocked caching of the next new message if sent via normal sendMessage().
     * Otherwise there will have duplicated display messages
     */
    private boolean cacheBlocked = false;

    private Boolean cacheUpdated = null;

    /**
     * Registered chatFragment to be informed of any messageReceived event
     */
    private final List<ChatSessionListener> msgListeners = new ArrayList<>();

    /**
     * Current chatSession TTS is active if true
     */
    private boolean isChatTtsEnable = false;

    private int ttsDelay = 1200;

    /**
     * Field used by the <code>ChatController</code> to keep track of last edited message content.
     */
    private String editedText;

    /**
     * Field used by the <code>ChatController</code> (input text) to remember if user was
     * making correction to the earlier sent message.
     */
    private String correctionUID;

    /**
     * ConferenceChatSession Subject - inform user if changed
     */
    private static String chatSubject = "";

    /**
     * Creates a chat session with the given MetaContact or ChatRoomWrapper.
     *
     * @param descriptor the transport object we're chatting with
     */
    public ChatPanel(Object descriptor) {
        mDescriptor = descriptor;

        if (descriptor instanceof MetaContact) {
            mMetaContact = (MetaContact) descriptor;
        }
        // Conference
        else {
            mMetaContact = null;
        }
        mMHS = MessageHistoryActivator.getMessageHistoryService();
    }

    /**
     * Sets the chat session to associate to this chat panel.
     *
     * @param chatSession the chat session to associate to this chat panel
     */
    public void setChatSession(ChatSession chatSession) {
        if (mChatSession != null) {
            // remove any old listener if present.
            mCurrentChatTransport.removeInstantMessageListener(this);
            mCurrentChatTransport.removeSmsMessageListener(this);
            mMHS.removeMessageReceiptListener(this);
        }

        mChatSession = chatSession;
        mChatId = chatSession.getChatId();
        mCurrentChatTransport = mChatSession.getCurrentChatTransport();
        mCurrentChatTransport.addInstantMessageListener(this);
        mCurrentChatTransport.addSmsMessageListener(this);

        // only metaContact chatSession supports Receipt status
        // && mCurrentChatTransport.allowsMessageDeliveryReceipt()) is true only a few ms later; so cannot check
        if (mMetaContact != null) {
            mMHS.addMessageReceiptListener(this);
        }
        updateChatTtsOption();
    }

    /**
     * Returns the protocolProvider of the user associated with this chat panel.
     *
     * @return the protocolProvider associated with this chat panel.
     */
    public ProtocolProviderService getProtocolProvider() {
        return mCurrentChatTransport.getProtocolProvider();
    }

    /**
     * Returns the chat session associated with this chat panel.
     *
     * @return the chat session associated with this chat panel.
     */
    public ChatSession getChatSession() {
        return mChatSession;
    }

    /**
     * Returns the underlying <code>MetaContact</code>, we're chatting with in metaContactChatSession
     *
     * @return the underlying <code>MetaContact</code>, we're chatting with
     */
    public MetaContact getMetaContact() {
        return mMetaContact;
    }

    public Object getDescriptor() {
        return mDescriptor;
    }

    /**
     * Stores current chatType.
     *
     * @param chatType selected chatType e.g. MSGTYPE_NORMAL.
     **/
    public void setChatType(int chatType) {
        mChatType = chatType;
    }

    public int getChatType() {
        return mChatType;
    }

    /**
     * Check if current chat is set to OMEMO crypto mode
     *
     * @return return <code>true</code> if OMEMO crypto chat is selected.
     */
    public boolean isOmemoChat() {
        return ((mChatType == ChatFragment.MSGTYPE_OMEMO)
                || (mChatType == ChatFragment.MSGTYPE_OMEMO_UA)
                || (mChatType == ChatFragment.MSGTYPE_OMEMO_UT));
    }

    /**
     * Check if current chat is set to OTR crypto mode
     *
     * @return return <code>true</code> if OMEMO crypto chat is selected
     */
    public boolean isOTRChat() {
        return ((mChatType == ChatFragment.MSGTYPE_OTR)
                || (mChatType == ChatFragment.MSGTYPE_OTR_UA));
    }

    /**
     * Stores recently edited message text.
     *
     * @param editedText recently edited message text.
     */
    public void setEditedText(String editedText) {
        this.editedText = editedText;
    }

    /**
     * Returns recently edited message text.
     *
     * @return recently edited message text.
     */
    public String getEditedText() {
        return editedText;
    }

    /**
     * Stores the UID of recently corrected message.
     *
     * @param correctionUID the UID of recently corrected message.
     */
    public void setCorrectionUID(String correctionUID) {
        this.correctionUID = correctionUID;
    }

    /**
     * Gets the UID of recently corrected message.
     *
     * @return the UID of recently corrected message.
     */
    public String getCorrectionUID() {
        return correctionUID;
    }

    /**
     * Runs clean-up for associated resources which need explicit disposal (e.g.
     * listeners keeping this instance alive because they were added to the
     * model which operationally outlives this instance).
     */
    public void dispose() {
        mCurrentChatTransport.removeInstantMessageListener(this);
        mCurrentChatTransport.removeSmsMessageListener(this);
        mMHS.removeMessageReceiptListener(this);

        mChatSession.dispose();
    }

    /**
     * Adds the given <code>ChatSessionListener</code> to listen for message events in this chat session.
     *
     * @param msgListener the <code>ChatSessionListener</code> to add
     */
    public void addMessageListener(ChatSessionListener msgListener) {
        if (!msgListeners.contains(msgListener))
            msgListeners.add(msgListener);
    }

    /**
     * Removes the given <code>ChatSessionListener</code> from this chat session.
     *
     * @param msgListener the <code>ChatSessionListener</code> to remove
     */
    public void removeMessageListener(ChatSessionListener msgListener) {
        msgListeners.remove(msgListener);
    }

    /**
     * Adds the given <code>ChatStateNotificationsListener</code> to listen for chat state events
     * in this chat session (Contact or ChatRoom).
     *
     * @param l the <code>ChatStateNotificationsListener</code> to add
     */
    public void addChatStateListener(ChatStateNotificationsListener l) {
        OperationSetChatStateNotifications chatStateOpSet
                = mCurrentChatTransport.getProtocolProvider().getOperationSet(OperationSetChatStateNotifications.class);

        if (chatStateOpSet != null) {
            chatStateOpSet.addChatStateNotificationsListener(l);
        }
    }

    /**
     * Removes the given <code>ChatStateNotificationsListener</code> from this chat session (Contact or ChatRoom)..
     *
     * @param l the <code>ChatStateNotificationsListener</code> to remove
     */
    public void removeChatStateListener(ChatStateNotificationsListener l) {
        OperationSetChatStateNotifications chatStateOpSet
                = mCurrentChatTransport.getProtocolProvider().getOperationSet(OperationSetChatStateNotifications.class);

        if (chatStateOpSet != null) {
            chatStateOpSet.removeChatStateNotificationsListener(l);
        }
    }

    /**
     * Adds the given <code>ContactPresenceStatusListener</code> to listen for message events
     * in this chat session.
     *
     * @param l the <code>ContactPresenceStatusListener</code> to add
     */
    public void addContactStatusListener(ContactPresenceStatusListener l) {
        if (mMetaContact == null)
            return;

        Iterator<Contact> protoContacts = mMetaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetPresence presenceOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

            if (presenceOpSet != null) {
                presenceOpSet.addContactPresenceStatusListener(l);
            }
        }
    }

    /**
     * Removes the given <code>ContactPresenceStatusListener</code> from this chat session.
     *
     * @param l the <code>ContactPresenceStatusListener</code> to remove
     */
    public void removeContactStatusListener(ContactPresenceStatusListener l) {
        if (mMetaContact == null)
            return;

        Iterator<Contact> protoContacts = mMetaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetPresence presenceOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

            if (presenceOpSet != null) {
                presenceOpSet.removeContactPresenceStatusListener(l);
            }
        }
    }

    /**
     * Returns a collection of newly fetched last messages from store; merged with msgCache.
     *
     * @return a collection of last messages.
     */
    public List<ChatMessage> getHistory(boolean init) {
        /*
         * If chatFragment is initializing (or onResume) AND we have already cached the messages
         * i.e. (historyLoaded == true), then just return the current msgCache content.
         * Always perform msgCache.sort; more delayed normal/encrypted messages may have been added on user re-login,
         */
        if (init && historyLoaded) {
            msgCache.sort(new ChatMessageComparator<>());
            return msgCache;
        }

        // If the MetaHistoryService is not registered we have nothing to do here.
        // The history store could be "disabled" by the user via Chat History Logging option.
        final MetaHistoryService metaHistory = AppGUIActivator.getMetaHistoryService();
        if (metaHistory == null)
            return msgCache;

        // descriptor can either be metaContact or chatRoomWrapper=>ChatRoom, from whom the history to be loaded
        Object descriptor = mDescriptor;
        if (descriptor instanceof ChatRoomWrapper) {
            descriptor = ((ChatRoomWrapper) descriptor).getChatRoom();

        }

        Collection<Object> history;
        /*
         * If there is no message in msgCache to process, then mamQuery the server for any history messages and
         * update the messages db; Only then read in last HISTORY_CHUNK_SIZE of history messages from database
         */
        if (msgCache.isEmpty()) {
            mamChecked = mamQuery(descriptor);
            history = metaHistory.findLast(chatHistoryFilter, descriptor, HISTORY_CHUNK_SIZE);
        }
        /*
         * Perform mamQuery if not done before. Received messages in msgCache may not in its timestamp order;
         * Due to process time varies, this does happen for delayed encrypted messages received when user is offline. .
         * So must sort them before using the timestamp of the first cached message as reference date.
         * Read in HISTORY_CHUNK_SIZE records earlier than the mLastMsgFetchDate i.e. top of the msgCache
         */
        else {
            // Note: this only update the DB but not the chat session UI display messages.
            if (!mamChecked) {
                mamChecked = mamQuery(descriptor);
            }

            if (mLastMsgFetchDate == null) {
                msgCache.sort(new ChatMessageComparator<>());
                mLastMsgFetchDate = msgCache.get(0).getDate();
            }
            history = metaHistory.findLastMessagesBefore(chatHistoryFilter, descriptor, mLastMsgFetchDate, HISTORY_CHUNK_SIZE);
        }

        // Convert history into actual chatMessages, add them to msgHistory for process.
        List<ChatMessage> msgHistory = new ArrayList<>();
        if (!history.isEmpty()) {
            // Convert events into messages for display in chat
            for (Object o : history) {
                if (o instanceof MessageDeliveredEvent) {
                    msgHistory.add(ChatMessageImpl.getMsgForEvent((MessageDeliveredEvent) o));
                }
                else if (o instanceof MessageReceivedEvent) {
                    msgHistory.add(ChatMessageImpl.getMsgForEvent((MessageReceivedEvent) o));
                }
                else if (o instanceof ChatRoomMessageDeliveredEvent) {
                    msgHistory.add(ChatMessageImpl.getMsgForEvent((ChatRoomMessageDeliveredEvent) o));
                }
                else if (o instanceof ChatRoomMessageReceivedEvent) {
                    msgHistory.add(ChatMessageImpl.getMsgForEvent((ChatRoomMessageReceivedEvent) o));
                }
                else if (o instanceof FileRecord) {
                    msgHistory.add(ChatMessageImpl.getMsgForEvent((FileRecord) o));
                }
                else {
                    Timber.e("Unexpected event in history: %s", o);
                }
            }
        }

        // Must re-process msgHistory to merged with msgCache if this first getHistory.
        if (init) {
            synchronized (cacheLock) {
                // We have something cached and we need to merge them with the msgHistory.
                // Do this only when we haven't merged it yet (ever).
                if (!historyLoaded) {
                    msgCache = mergeMsgLists(msgHistory, msgCache);
                    historyLoaded = true;
                }
                // Otherwise just prepend the history records.
                else {
                    msgCache.addAll(0, msgHistory);
                }
            }

            if (!msgCache.isEmpty()) {
                mLastMsgFetchDate = msgCache.get(0).getDate();
            }
            return msgCache;
        }
        else {
            if (!msgHistory.isEmpty()) {
                mLastMsgFetchDate = msgHistory.get(0).getDate();
            }
            return msgHistory;
        }
    }

    /**
     * Merges given lists of messages. Output list is ordered by received timestamp.
     *
     * @param msgHistory first list to merge.
     * @param msgCache the second list to merge.
     *
     * @return merged list of messages contained in the given lists ordered by the date.
     */
    private List<ChatMessage> mergeMsgLists(List<ChatMessage> msgHistory, List<ChatMessage> msgCache) {

        List<ChatMessage> mergedList = new LinkedList<>();
        int historyIdx = msgHistory.size() - 1;
        int cacheIdx = msgCache.size() - 1;

        while (historyIdx >= 0 && cacheIdx >= 0) {
            ChatMessage historyMsg = msgHistory.get(historyIdx);
            ChatMessage cacheMsg = msgCache.get(cacheIdx);

            if (historyMsg.getDate().after(cacheMsg.getDate())) {
                mergedList.add(0, historyMsg);
                historyIdx--;
            }
            else {
                mergedList.add(0, cacheMsg);
                cacheIdx--;
            }
        }

        // Input remaining history messages
        while (historyIdx >= 0)
            mergedList.add(0, msgHistory.get(historyIdx--));

        // Input remaining cache messages
        while (cacheIdx >= 0)
            mergedList.add(0, msgCache.get(cacheIdx--));

        return mergedList;
    }

    /**
     * Fetch the server mam message and merged into the history database if new;
     * This method is accessed only after the user has registered with the network,
     *
     * @param descriptor can either be metaContact or chatRoomWrapper=>ChatRoom, from whom the mam are to be loaded
     */
    private boolean mamQuery(Object descriptor) {
        if (!getProtocolProvider().isRegistered()) {
            return false;
        }

        XMPPConnection connection = getProtocolProvider().getConnection();
        MamManager mamManager;
        Jid jid;
        String sessionUuid;
        if (descriptor instanceof ChatRoom) {
            jid = ((ChatRoom) descriptor).getIdentifier();
            mamManager = MamManager.getInstanceFor(((ChatRoom) descriptor).getMultiUserChat());
            sessionUuid = mMHS.getSessionUuidByJid((ChatRoom) descriptor);
        }
        else {
            Contact contact = ((MetaContact) descriptor).getDefaultContact();
            jid = contact.getJid().asBareJid();
            mamManager = MamManager.getInstanceFor(connection, null);
            sessionUuid = mMHS.getSessionUuidByJid(contact);
        }

        // Retrieve the mamData from the last message received in this chatSession
        MessageHistoryServiceImpl mMHS = MessageHistoryActivator.getMessageHistoryService();
        Date lmrDate = mMHS.getLastMessageDateForSessionUuid(sessionUuid);
        Date mamDate = mMHS.getMamDate(sessionUuid);

        if ((lmrDate != null) && (mamDate != null) && mamDate.before(lmrDate)) {
            mamDate = lmrDate;
        }
        // Must use a valid mamDate in memQuery; default to fetch last 7 days if none found
        if (mamDate == null) {
            Calendar c = Calendar.getInstance(TimeZone.getDefault());
            c.set(Calendar.DAY_OF_MONTH, -7);
            mamDate = c.getTime();
        }
        // set mamDate to a valid date in chatSession record.
        mMHS.setMamDate(sessionUuid, mamDate);

        try {
            if (mamManager.isSupported()) {
                // Prevent omemoManager from automatically decrypting MAM messages.
                OmemoManager omemoManager = OmemoManager.getInstanceFor(connection);
                omemoManager.stopStanzaAndPEPListeners();

                MamManager.MamQueryArgs mamQueryArgs = MamManager.MamQueryArgs.builder()
                        .limitResultsToJid(jid)
                        .limitResultsSince(mamDate)
                        .setResultPageSizeTo(MAM_PAGE_SIZE)
                        .build();

                MamManager.MamQuery query = mamManager.queryArchive(mamQueryArgs);
                List<Forwarded<Message>> forwardedList = query.getPage().getForwarded();
                if (!forwardedList.isEmpty()) {
                    mMHS.saveMamIfNotExit(omemoManager, this, forwardedList);
                }
                omemoManager.resumeStanzaAndPEPListeners();
            }
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException // | IOException
                 | SmackException.NotConnectedException | InterruptedException |
                 SmackException.NotLoggedInException e) {
            Timber.e("MamQuery: %s", e.getMessage());
        }

        // Update to last processed mam message
        mamDate = mMHS.getMamDate(sessionUuid);
        mamDateTS = mamDate.getTime();
        return true;
    }

    /**
     * Update the file transfer status in the msgCache; must do this else file transfer will be
     * reactivated onResume chat. Also important if historyLog is disabled.
     *
     * @param msgUuid ChatMessage uuid
     * @param status File transfer status
     * @param fileName the downloaded fileName
     * @param recordType File record type see ChatMessage MESSAGE_FILE_
     */
    public void updateCacheFTRecord(String msgUuid, int status, String fileName, int encType, int recordType) {
        int cacheIdx = msgCache.size() - 1;
        while (cacheIdx >= 0) {
            ChatMessageImpl cacheMsg = (ChatMessageImpl) msgCache.get(cacheIdx);
            // 20220709: cacheMsg.getMessageUID() can be null
            if (msgUuid.equals(cacheMsg.getMessageUID())) {
                cacheMsg.updateFTStatus(mDescriptor, msgUuid, status, fileName, encType, recordType, cacheMsg.getMessageDir());
                // Timber.d("updateCacheFTRecord msgUid: %s => %s (%s)", msgUuid, status, recordType );
                break;
            }
            cacheIdx--;
        }
    }

    /**
     * Remove user deleted messages from msgCache if receiptStatus is null;
     * or update receiptStatus cached message of the given msgUuid
     *
     * @param msgUuid ChatMessage uuid
     * @param receiptStatus message receipt status to update; null is to delete message
     */
    public void updateCacheMessage(String msgUuid, Integer receiptStatus) {
        int cacheIdx = msgCache.size() - 1;
        while (cacheIdx >= 0) {
            ChatMessageImpl cacheMsg = (ChatMessageImpl) msgCache.get(cacheIdx);
            if (msgUuid.equals(cacheMsg.getMessageUID())) {
                // Timber.d("updateCacheMessage msgUid: %s => %s", msgUuid, receiptStatus);
                if (receiptStatus == null) {
                    msgCache.remove(cacheIdx);
                }
                else {
                    cacheMsg.setReceiptStatus(receiptStatus);
                }
                break;
            }
            cacheIdx--;
        }
    }

    public void msgCacheClear() {
        msgCache.clear();
    }

    /**
     * Implements the <code>Chat.isChatFocused</code> method. Returns TRUE if this chat is
     * the currently selected and if the chat window, where it's contained is active.
     *
     * @return true if this chat has the focus and false otherwise.
     */
    @Override
    public boolean isChatFocused() {
        return (mChatId != null)
                && mChatId.equals(ChatSessionManager.getCurrentChatId());
    }

    /**
     * Returns the message written by user in the chat write area.
     *
     * @return the message written by user in the chat write area
     */
    @Override
    public String getMessage() {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Bring this chat to front if <code>b</code> is true, hide it otherwise.
     *
     * @param isVisible tells if the chat will be made visible or not.
     */
    @Override
    public void setChatVisible(boolean isVisible) {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Sets the given message as a message in the chat write area.
     *
     * @param message the text that would be set to the chat write area
     */
    @Override
    public void setMessage(String message) {
        throw new RuntimeException("Not supported yet");
        //??? chatController.msgEdit.setText(message);
    }

    /**
     * Sends the message and blocked message caching for this message; otherwise the single send message
     * will appear twice in the chat fragment i.e. inserted and cached e.g. from share link
     *
     * @param message the text string to be sent
     * @param encType The encType of the message to be sent: RemoteOnly | 1=text/html or 0=text/plain.
     */
    public void sendMessage(String message, int encType) {
        cacheBlocked = true;

        int encryption = IMessage.ENCRYPTION_NONE;
        if (isOmemoChat())
            encryption = IMessage.ENCRYPTION_OMEMO;
        else if (isOTRChat())
            encryption = IMessage.ENCRYPTION_OTR;

        try {
            mCurrentChatTransport.sendInstantMessage(message, encryption | encType);
        } catch (Exception ex) {
            aTalkApp.showToastMessage(ex.getMessage());
            cacheBlocked = false;
        }
    }

    /**
     * Direct add a message to this Chat. Mainly use for System messages or internal generated messages.
     *
     * @param contactName the name of the contact sending the message
     * @param date the time at which the message is sent or received
     * @param messageType the type of the message
     * @param encType the content encode type i.e plain or html
     * @param content the message text
     */
    @Override
    public void addMessage(String contactName, Date date, int messageType, int encType, String content) {
        addMessage(new ChatMessageImpl(contactName, contactName, date, messageType, encType, content, null, ChatMessage.DIR_IN));
    }

    /**
     * Add a message to this <code>Chat</code> by conference session.
     *
     * @param contactName the name of the contact sending the message
     * @param displayName the display name of the contact
     * @param date the time at which the message is sent or received
     * @param chatMsgType the type of the message. See ChatMessage
     * @param message the IMessage.
     */
    public void addMessage(String contactName, String displayName, Date date, int chatMsgType,
            IMessage message, String correctedMessageUID) {
        addMessage(new ChatMessageImpl(contactName, displayName, date, chatMsgType, message, correctedMessageUID, ChatMessage.DIR_IN));
    }

    /**
     * A direct call function to add a chat message to this <code>Chat</code> panel. Must always cache
     * the chatMessage, as chatFragment may not have registered to handle messages on first onAttach.
     *
     * @param chatMessage the ChatMessage.
     */
    public void addMessage(ChatMessageImpl chatMessage) {
        // Do nothing if the MESSAGE_STATUS is disabled
        if (ChatMessage.MESSAGE_STATUS == chatMessage.getMessageType()) {
            Object descriptor = mChatSession.getDescriptor();
            if ((descriptor instanceof ChatRoomWrapper) &&
                    !((ChatRoomWrapper) descriptor).isRoomStatusEnable())
                return;
        }

        if (!(cacheNextMsg(chatMessage))) {
            Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, chatMessage.getMessageUID());
        }
        messageSpeak(chatMessage, 2 * ttsDelay);  // for chatRoom

        // Just show a ToastMessage if no ChatSessionListener to receive the messages i.e. chatFragment has not started
        if (msgListeners.isEmpty()) {
            aTalkApp.showToastMessage(chatMessage.getMessage());
        }
        else {
            for (ChatSessionListener l : msgListeners) {
                l.sessionMessageAdded(chatMessage);
            }
        }
    }

    /**
     * Caches next message when chat is not in focus and it is not being blocked via sendMessage().
     * Otherwise duplicated messages when share link
     *
     * @param newMsg the next message to cache.
     *
     * @return true if newMsg added successfully to the msgCache
     */
    public boolean cacheNextMsg(ChatMessageImpl newMsg) {
        // Timber.d("Cache blocked is %s for: %s", cacheBlocked, newMsg.getMessage());
        if (!cacheBlocked) {
            // FFR: ANR synchronized (cacheLock); fixed with new msgCache merging optimization (20221229)
            synchronized (cacheLock) {
                return (cacheUpdated = msgCache.add(newMsg));
            }
        }
        else {
            cacheBlocked = false;
            cacheUpdated = null;
        }
        return false;
    }

    public boolean isChatTtsEnable() {
        return isChatTtsEnable;
    }

    public void updateChatTtsOption() {
        isChatTtsEnable = ConfigurationUtils.isTtsEnable();
        if (isChatTtsEnable) {
            // Object mDescriptor = mChatSession.getDescriptor();
            if (mDescriptor instanceof MetaContact) {
                isChatTtsEnable = ((MetaContact) mDescriptor).getDefaultContact().isTtsEnable();
            }
            else {
                isChatTtsEnable = ((ChatRoomWrapper) mDescriptor).isTtsEnable();
            }
        }
        // refresh the tts delay time
        ttsDelay = ConfigurationUtils.getTtsDelay();
    }

    private void messageSpeak(ChatMessage msg, int delay) {
        // Timber.d("Chat TTS message speak: %s = %s", isChatTtsEnable, msg.getMessage());
        if (!isChatTtsEnable)
            return;

        // ChatRoomMessageReceivedEvent from conference room
        if (ChatMessage.MESSAGE_IN == msg.getMessageType()
                || ChatMessage.MESSAGE_MUC_IN == msg.getMessageType()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Timber.w("TTS speak wait exception: %s", e.getMessage());
            }
            ttsSpeak(msg);
        }
    }

    /**
     * Call TTS to speak the text given in chatMessage if it is not HttpDownloadLink.
     *
     * @param chatMessage ChatMessage for TTS
     */
    public void ttsSpeak(ChatMessage chatMessage) {
        String textBody = chatMessage.getMessage();
        if (!TextUtils.isEmpty(textBody) && !FileBackend.isHttpFileDnLink(textBody)) {
            Intent spkIntent = new Intent(aTalkApp.getInstance(), TTSService.class);
            spkIntent.putExtra(TTSService.EXTRA_MESSAGE, textBody);
            spkIntent.putExtra(TTSService.EXTRA_QMODE, false);
            aTalkApp.getInstance().startService(spkIntent);
        }
    }

    /**
     * Send an outgoing file message to chatFragment for it to start the file send process
     * The recipient can be contact or chatRoom
     *
     * @param filePath as message content of the file to be sent
     * @param messageType indicate which File transfer message is for
     */
    public void addFTSendRequest(String filePath, int messageType) {
        String sendTo;
        Date date = Calendar.getInstance().getTime();

        // Create the new msg Uuid for record saved in dB
        String msgUuid = String.valueOf(System.currentTimeMillis()) + hashCode();

        Object sender = mCurrentChatTransport.getDescriptor();
        if (sender instanceof Contact) {
            sendTo = ((Contact) sender).getAddress();
        }
        else {
            sendTo = ((ChatRoom) sender).getName();
        }

        // Do not use addMessage to avoid TTS activation for outgoing file message
        ChatMessageImpl chatMsg = new ChatMessageImpl(sendTo, sendTo, date, messageType,
                IMessage.ENCODE_PLAIN, filePath, msgUuid, ChatMessage.DIR_OUT);
        if (!cacheNextMsg(chatMsg)) {
            Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, msgUuid);
        }
        for (ChatSessionListener l : msgListeners) {
            l.sessionMessageAdded(chatMsg);
        }
    }

    /**
     * ChatMessage for IncomingFileTransferRequest.
     * <p>
     * Adds the given <code>IncomingFileTransferRequest</code> to the conversation panel in order to
     * notify the user of an incoming file transfer request.
     *
     * @param opSet the file transfer operation set
     * @param request the request to display in the conversation panel
     * @param date the date on which the request has been received
     *
     * @see FileTransferActivator#fileTransferRequestReceived(FileTransferRequestEvent)
     */
    public void addFTReceiveRequest(OperationSetFileTransfer opSet, IncomingFileTransferRequest request, Date date) {
        Contact sender = request.getSender();
        String senderName = sender.getAddress();
        String msgUuid = request.getId();
        String msgContent = aTalkApp.getResString(R.string.file_transfer_request_received, date.toString(), senderName);

        int msgType = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;
        int encType = IMessage.ENCODE_PLAIN;
        ChatMessageImpl chatMsg = new ChatMessageImpl(senderName, date, msgType, encType,
                msgContent, msgUuid, ChatMessage.DIR_IN, opSet, request, null);

        // Do not use addMessage to avoid TTS activation for incoming file message
        if (!cacheNextMsg(chatMsg)) {
            Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, msgUuid);
        }
        for (ChatSessionListener l : msgListeners) {
            l.sessionMessageAdded(chatMsg);
        }
    }

    /**
     * Adds a new ChatLinkClickedListener. The callback is called for every link whose scheme is
     * <code>jitsi</code>. It is the callback's responsibility to filter the action based on the URI.
     * <p>
     * Example:<br>
     * <code>jitsi://classname/action?query</code><br>
     * Use the name of the registering class as the host, the action to execute as the path and
     * any parameters as the query.
     *
     * @param chatLinkClickedListener callback that is notified when a link was clicked.
     */
    @Override
    public void addChatLinkClickedListener(ChatLinkClickedListener chatLinkClickedListener) {
        ChatSessionManager.addChatLinkListener(chatLinkClickedListener);
    }

    /**
     * Check if the chatMessage is later than the mamQuery access timestamp.
     *
     * @param chatMessage new message received.
     *
     * @return true is new message TS is later than mamDateTS
     */
    public boolean isMessageNew(ChatMessageImpl chatMessage) {
        long messageTS = chatMessage.getDate().getTime();
        return messageTS > mamDateTS;
    }

    /**
     * Message received via AbstractOperationSetBasicInstantMessaging callback.
     * Must cache chatMsg as chatFragment has not registered to handle incoming
     * message on first onAttach or not in focus
     *
     * @param messageReceivedEvent the <code>MessageReceivedEvent</code> containing the
     * newly received message, its sender and other details.
     */
    @Override
    public void messageReceived(MessageReceivedEvent messageReceivedEvent) {
        // cmeng: only handle messageReceivedEvent belongs to this.metaContact
        Contact protocolContact = messageReceivedEvent.getSourceContact();
        if ((mMetaContact != null) && mMetaContact.containsContact(protocolContact)) {
            ChatMessageImpl chatMessage = ChatMessageImpl.getMsgForEvent(messageReceivedEvent);
            // Timber.e("New message received (cp): %s %s", mamChecked, chatMessage.getMessage());

            if (!mamChecked || isMessageNew(chatMessage)) {
                if (!cacheNextMsg(chatMessage)) {
                    Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, chatMessage.getMessageUID());
                }
                for (ChatSessionListener l : msgListeners) {
                    l.messageReceived(messageReceivedEvent);
                }
                messageSpeak(chatMessage, ttsDelay);
            }
        }
        else {
            Timber.log(TimberLog.FINER, "MetaContact not found for protocol contact: %s", protocolContact);
        }
    }

    @Override
    public void messageDelivered(MessageDeliveredEvent messageDeliveredEvent) {
        /*
         * (metaContact == null) for ConferenceChatTransport. Check just in case the listener is not properly
         * removed when the chat is closed. Only handle messageReceivedEvent belongs to this.metaContact
         */
        if ((mMetaContact != null) && mMetaContact.containsContact(messageDeliveredEvent.getContact())) {

            // return if delivered message does not required local display in chatWindow nor cached
            if (messageDeliveredEvent.getSourceMessage().isRemoteOnly())
                return;

            ChatMessageImpl chatMessage = ChatMessageImpl.getMsgForEvent(messageDeliveredEvent);
            if (!cacheNextMsg(chatMessage)) {
                Timber.e("Failed adding to msgCache (updated: %s): %s", cacheUpdated, chatMessage.getMessageUID());
            }
            for (ChatSessionListener l : msgListeners) {
                l.messageDelivered(messageDeliveredEvent);
            }
        }
    }

    @Override
    public void messageDeliveryFailed(MessageDeliveryFailedEvent evt) {
        for (ChatSessionListener l : msgListeners) {
            l.messageDeliveryFailed(evt);
        }

        // Insert error message
        Timber.d("%s", evt.getReason());

        // Just show the pass in error message if false
        boolean mergeMessage = true;
        String errorMsg;
        IMessage srcMessage = (IMessage) evt.getSource();

        // contactJid cannot be nick name, otherwise message will not be displayed
        String contactJid = evt.getDestinationContact().getAddress();

        switch (evt.getErrorCode()) {
            case MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED:
                errorMsg = aTalkApp.getResString(R.string.message_delivery_not_supported, contactJid);
                break;
            case MessageDeliveryFailedEvent.NETWORK_FAILURE:
                errorMsg = aTalkApp.getResString(R.string.message_delivery_network_error);
                break;
            case MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED:
                errorMsg = aTalkApp.getResString(R.string.message_delivery_not_registered);
                break;
            case MessageDeliveryFailedEvent.INTERNAL_ERROR:
                errorMsg = aTalkApp.getResString(R.string.message_delivery_internal_error);
                break;
            case MessageDeliveryFailedEvent.OMEMO_SEND_ERROR:
                errorMsg = evt.getReason();
                mergeMessage = false;
                break;
            default:
                errorMsg = aTalkApp.getResString(R.string.message_delivery_error);
        }

        String reason = evt.getReason();
        if (!TextUtils.isEmpty(reason) && mergeMessage) {
            errorMsg += " " + aTalkApp.getResString(R.string.error_was_, reason);
        }
        addMessage(contactJid, new Date(), ChatMessage.MESSAGE_OUT, srcMessage.getMimeType(), srcMessage.getContent());
        addMessage(contactJid, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, errorMsg);
    }

    @Override
    public void receiptReceived(Jid fromJid, Jid toJid, String receiptId, Stanza receipt) {
        if ((mMetaContact != null) && mMetaContact.getDefaultContact().getJid().isParentOf(fromJid)) {
            // Update ChatMessage receipt status in msgCache in background
            updateCacheMessage(receiptId, ChatMessage.MESSAGE_DELIVERY_RECEIPT);
            for (ChatSessionListener l : msgListeners) {
                l.receiptReceived(fromJid, toJid, receiptId, receipt);
            }
        }
    }

    /**
     * Extends <code>MessageListener</code> interface in order to provide notifications about injected
     * messages without the need of event objects.
     *
     * @author Pawel Domas
     */
    interface ChatSessionListener extends MessageListener {
        void sessionMessageAdded(ChatMessage msg);

        void receiptReceived(Jid fromJid, Jid toJid, final String receiptId, Stanza receipt);
    }

    /**
     * Updates the status of the given chat transport in the send via selector box and notifies
     * the user for the status change.
     *
     * @param chatTransport the <code>chatTransport</code> to update
     */
    public void updateChatTransportStatus(final ChatTransport chatTransport) {
        if (isChatFocused()) {
            final AppCompatActivity activity = aTalkApp.getCurrentActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    PresenceStatus presenceStatus = chatTransport.getStatus();
                    ActionBarUtil.setSubtitle(activity, presenceStatus.getStatusName());
                    ActionBarUtil.setStatusIcon(activity, presenceStatus.getStatusIcon());
                });
            }
        }

        String contactName = ((MetaContactChatTransport) chatTransport).getContact().getAddress();
        if (ConfigurationUtils.isShowStatusChangedInChat()) {
            // Show a status message to the user.
            // addMessage(contactName, chatTransport.getName(), new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
            //        aTalkApp.getResString(R.string.service_gui_STATUS_CHANGED_CHAT_MESSAGE, chatTransport.getStatus().getStatusName()),
            //        IMessage.ENCRYPTION_NONE, null, null);
            addMessage(contactName, new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
                    aTalkApp.getResString(R.string.status_change_message, chatTransport.getStatus().getStatusName()));
        }
    }

    /**
     * Renames all occurrences of the given <code>chatContact</code> in this chat panel.
     *
     * @param chatContact the contact to rename
     * @param name the new name
     */
    public void setContactName(ChatContact<?> chatContact, final String name) {
        if (isChatFocused()) {
            final AppCompatActivity activity = aTalkApp.getCurrentActivity();
            activity.runOnUiThread(() -> {
                if (mChatSession instanceof MetaContactChatSession) {
                    ActionBarUtil.setTitle(activity, name);
                }
            });
        }
    }

    /**
     * Sets the given <code>subject</code> to this chat.
     *
     * @param subject the subject to set
     */
    public void setChatSubject(final String subject, String oldSubject) {
        if ((subject != null) && !subject.equals(chatSubject)) {
            chatSubject = subject;

            if (isChatFocused()) {
                final AppCompatActivity activity = aTalkApp.getCurrentActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        // cmeng: check instanceof just in case user change chat session
                        if (mChatSession instanceof ConferenceChatSession) {
                            ActionBarUtil.setSubtitle(activity, subject);
                        }
                    });
                }
            }
            // Do not display change subject message if this is the original subject
            if (!TextUtils.isEmpty(oldSubject))
                this.addMessage(mChatSession.getChatEntity(), new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN,
                        aTalkApp.getResString(R.string.chatroom_subject_changed, oldSubject, subject));
        }
    }

    /**
     * Updates the contact status - call from conference only.
     *
     * @param chatContact the chat contact of the conference to update
     * @param statusMessage the status message to show
     */
    public void updateChatContactStatus(final ChatContact<?> chatContact, final String statusMessage) {
        if (StringUtils.isNotEmpty(statusMessage)) {
            String contactName = ((ChatRoomMemberJabberImpl) chatContact.getDescriptor()).getContactAddress();
            addMessage(contactName, new Date(), ChatMessage.MESSAGE_STATUS, IMessage.ENCODE_PLAIN, statusMessage);
        }
    }

    /**
     * Returns the first chat transport for the current chat session that supports group chat.
     *
     * @return the first chat transport for the current chat session that supports group chat.
     */
    public ChatTransport findInviteChatTransport() {
        ProtocolProviderService protocolProvider = mCurrentChatTransport.getProtocolProvider();

        // We choose between OpSets for multi user chat...
        if (protocolProvider.getOperationSet(OperationSetMultiUserChat.class) != null
                || protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat.class) != null) {
            return mCurrentChatTransport;
        }
        else {
            Iterator<ChatTransport> chatTransportsIter = mChatSession.getChatTransports();
            while (chatTransportsIter.hasNext()) {
                ChatTransport chatTransport = chatTransportsIter.next();

                Object groupChatOpSet = chatTransport.getProtocolProvider().getOperationSet
                        (OperationSetMultiUserChat.class);
                if (groupChatOpSet != null)
                    return chatTransport;
            }
        }
        return null;
    }

    /**
     * Invites the given <code>chatContacts</code> to this chat.
     *
     * @param inviteChatTransport the chat transport to use to send the invite
     * @param chatContacts the contacts to invite
     * @param reason the reason of the invitation
     */
    public void inviteContacts(ChatTransport inviteChatTransport, Collection<String> chatContacts, String reason) {
        ProtocolProviderService pps = inviteChatTransport.getProtocolProvider();

        if (mChatSession instanceof MetaContactChatSession) {
            ConferenceChatManager conferenceChatManager = AppGUIActivator.getUIService().getConferenceChatManager();

            // the chat session is set regarding to which OpSet is used for MUC
            if (pps.getOperationSet(OperationSetMultiUserChat.class) != null) {
                ChatRoomWrapper chatRoomWrapper
                        = MUCActivator.getMUCService().createPrivateChatRoom(pps, chatContacts, reason, false);
                if (chatRoomWrapper != null) {
                    // conferenceChatSession = new ConferenceChatSession(this, chatRoomWrapper);
                    Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                    aTalkApp.getInstance().startActivity(chatIntent);
                }
                else {
                    Timber.e("Failed to create chatroom");
                }
            }
            else if (pps.getOperationSet(OperationSetAdHocMultiUserChat.class) != null) {
                AdHocChatRoomWrapper chatRoomWrapper
                        = conferenceChatManager.createAdHocChatRoom(pps, chatContacts, reason);
                // conferenceChatSession = new AdHocConferenceChatSession(this, chatRoomWrapper);
                Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                aTalkApp.getInstance().startActivity(chatIntent);
            }
            // if (conferenceChatSession != null) {
            //   this.setChatSession(conferenceChatSession);
            // }
        }
        // We're already in a conference chat.
        else {
            for (String contactAddress : chatContacts) {
                try {
                    mCurrentChatTransport.inviteChatContact(JidCreate.entityBareFrom(contactAddress), reason);
                } catch (XmppStringprepException e) {
                    Timber.w("Group chat invitees Jid create error: %s, %s", contactAddress, e.getMessage());
                }
            }
        }
    }

    /**
     * Used to compare ChatMessage and to be ordered in according their timestamp.
     */
    private static class ChatMessageComparator<T> implements Comparator<T> {
        private final boolean reverseOrder;

        ChatMessageComparator(boolean reverseOrder) {
            this.reverseOrder = reverseOrder;
        }

        ChatMessageComparator() {
            this(false);
        }

        public int compare(T o1, T o2) {
            if (o1 instanceof ChatMessage && o2 instanceof ChatMessage) {
                Date date1 = ((ChatMessage) o1).getDate();
                Date date2 = ((ChatMessage) o2).getDate();

                if (reverseOrder)
                    return date2.compareTo(date1);
                else
                    return date1.compareTo(date2);
            }
            return 0;
        }
    }
}
