/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.app.Activity;
import android.content.Intent;
import android.text.method.KeyListener;

import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.gui.event.ChatFocusListener;
import net.java.sip.communicator.service.gui.event.ChatMenuListener;
import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.conference.*;
import org.atalk.android.gui.util.ActionBarUtil;
import org.atalk.android.util.javax.swing.event.CaretListener;
import org.atalk.android.util.javax.swing.event.DocumentListener;
import org.atalk.android.util.javax.swing.text.Highlighter;
import org.atalk.util.Logger;
import org.atalk.util.StringUtils;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.*;

/**
 * The <tt>ChatPanel</tt>, <tt>ChatActivity</tt>, <tt>ChatController</tt> and <tt>ChatFragment</tt>
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
public class ChatPanel implements Chat, MessageListener
{
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(ChatPanel.class);

    /**
     * Number of history messages to be returned from loadHistory call.
     * Limits the amount of messages being loaded at one time.
     */
    private static final int HISTORY_CHUNK_SIZE = 30;

    /**
     * File transfer message types
     */
    private static final String INCOMING_FILE_MESSAGE = "IncomingFileMessage";
    public static final String OUTGOING_FILE_MESSAGE = "OutgoingFileMessage";

    /**
     * The underlying <tt>MetaContact</tt>, we're chatting with.
     */
    private final MetaContact metaContact;

    /**
     * The chatType for which the message will be send for method not using Transform process
     * i.e. OTR. This is also the master copy where other will update or refer to.
     * The state may also be change by the under lying signal protocol based on the current
     * signalling condition.
     */
    private int mChatType = ChatFragment.MSGTYPE_NORMAL;

    /**
     * The current chat transport.
     */
    private ChatTransport mCurrentChatTransport;

    /**
     * The chat history filter for retrieving history messages.
     */
    private final String[] chatHistoryFilter = ChatSession.chatHistoryFilter;

    /**
     * Messages cache used by this session; to cache msg arrived when the chatFragment
     * is not in view e.g. standby, while in contactList view or when scroll out of view.
     */
    private List<ChatMessage> msgCache = new LinkedList<>();

    /**
     * Synchronization root for messages cache.
     */
    private final Object cacheLock = new Object();

    /**
     * Current chat session type: mChatSession can either be one of the following:
     * MetaContactChatSession, ConferenceChatSession or AdHocConferenceChatSession
     */
    private ChatSession mChatSession;

    /**
     * Flag indicates if the history has been loaded (it must be done only once
     * and next messages are cached through the listeners mechanism).
     */
    private boolean historyLoaded = false;

    /**
     * Registered chatFragment to be informed of any messageReceived event
     */
    private final List<ChatSessionListener> msgListeners = new ArrayList<>();

    /**
     * Field used by the <tt>ChatController</tt> to keep track of last edited message content.
     */
    private String editedText;

    /**
     * Field used by the <tt>ChatController</tt> (input text) to remember if user was
     * making correction to the earlier sent message.
     */
    private String correctionUID;

    /**
     * ConferenceChatSession Subject - inform user if changed
     */
    private static String chatSubject = "";

    private boolean hasNewMsg = false;

    /**
     * Creates a chat session with the given <tt>MetaContact</tt>.
     *
     * @param metaContact the <tt>MetaContact</tt> we're chatting with
     */
    public ChatPanel(MetaContact metaContact)
    {
        this.metaContact = metaContact;

        // init default mChatType onCreate
        if (metaContact != null)
            mChatType = ChatFragment.MSGTYPE_NORMAL;
        else // Conference
            mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
    }

    /**
     * Sets the chat session to associate to this chat panel.
     *
     * @param chatSession the chat session to associate to this chat panel
     */
    public void setChatSession(ChatSession chatSession)
    {
        if (mChatSession != null) {
            // remove old listener if any
            mCurrentChatTransport.removeInstantMessageListener(this);
            mCurrentChatTransport.removeSmsMessageListener(this);
        }

        mChatSession = chatSession;
        mCurrentChatTransport = mChatSession.getCurrentChatTransport();
        mCurrentChatTransport.addInstantMessageListener(this);
        mCurrentChatTransport.addSmsMessageListener(this);
    }

    /**
     * Returns the protocolProvider of the user associated with this chat panel.
     *
     * @return the protocolProvider associated with this chat panel
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return mCurrentChatTransport.getProtocolProvider();
    }

    /**
     * Returns the chat session associated with this chat panel.
     *
     * @return the chat session associated with this chat panel
     */
    public ChatSession getChatSession()
    {
        return mChatSession;
    }

    /**
     * Returns the underlying <tt>MetaContact</tt>, we're chatting with in metaContactChatSession
     *
     * @return the underlying <tt>MetaContact</tt>, we're chatting with
     */
    public MetaContact getMetaContact()
    {
        return metaContact;
    }

    /**
     * Stores current chatType.
     *
     * @param chatType selected chatType e.g. MSGTYPE_NORMAL
     **/
    public void setChatType(int chatType)
    {
        // new Exception("ChatType: " + mChatType + "=>" + chatType).printStackTrace();
        mChatType = chatType;
    }

    public int getChatType()
    {
        return mChatType;
    }

    /**
     * Check if current chat is set to OMEMO crypto mode
     *
     * @return return <tt>true</tt> if OMEMO crypto chat is selected
     */
    public boolean isOmemoChat()
    {
        return ((mChatType == ChatFragment.MSGTYPE_OMEMO)
                || (mChatType == ChatFragment.MSGTYPE_OMEMO_UA)
                || (mChatType == ChatFragment.MSGTYPE_OMEMO_UT));
    }

    /**
     * Check if current chat is set to OTR crypto mode
     *
     * @return return <tt>true</tt> if OMEMO crypto chat is selected
     */
    public boolean isOTRChat()
    {
        return ((mChatType == ChatFragment.MSGTYPE_OTR)
                || (mChatType == ChatFragment.MSGTYPE_OTR_UA));
    }

    /**
     * Stores recently edited message text.
     *
     * @param editedText recently edited message text.
     */
    public void setEditedText(String editedText)
    {
        this.editedText = editedText;
    }

    /**
     * Returns recently edited message text.
     *
     * @return recently edited message text.
     */
    public String getEditedText()
    {
        return editedText;
    }

    /**
     * Stores the UID of recently corrected message.
     *
     * @param correctionUID the UID of recently corrected message.
     */
    public void setCorrectionUID(String correctionUID)
    {
        this.correctionUID = correctionUID;
    }

    /**
     * Gets the UID of recently corrected message.
     *
     * @return the UID of recently corrected message.
     */
    public String getCorrectionUID()
    {
        return correctionUID;
    }

    /**
     * Runs clean-up for associated resources which need explicit disposal (e.g.
     * listeners keeping this instance alive because they were added to the
     * model which operationally outlives this instance).
     */
    public void dispose()
    {
        mCurrentChatTransport.removeInstantMessageListener(this);
        mCurrentChatTransport.removeSmsMessageListener(this);
        mChatSession.dispose();
    }

    /**
     * Adds the given <tt>ChatSessionListener</tt> to listen for message events in this chat
     * session.
     *
     * @param msgListener the <tt>ChatSessionListener</tt> to add
     */
    public void addMessageListener(ChatSessionListener msgListener)
    {
        if (!msgListeners.contains(msgListener))
            msgListeners.add(msgListener);
    }

    /**
     * Removes the given <tt>ChatSessionListener</tt> from this chat session.
     *
     * @param msgListener the <tt>ChatSessionListener</tt> to remove
     */
    public void removeMessageListener(ChatSessionListener msgListener)
    {
        msgListeners.remove(msgListener);
    }

    /**
     * Adds the given <tt>ChatStateNotificationsListener</tt> to listen for message events
     * in this metaContact chat session.
     *
     * @param l the <tt>ChatStateNotificationsListener</tt> to add
     */
    public void addChatStateListener(ChatStateNotificationsListener l)
    {
        Iterator<Contact> protoContacts = metaContact.getContacts();

        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetChatStateNotifications chatStateOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetChatStateNotifications.class);

            if (chatStateOpSet != null) {
                chatStateOpSet.addChatStateNotificationsListener(l);
            }
        }
    }

    /**
     * Removes the given <tt>ChatStateNotificationsListener</tt> from this chat session.
     *
     * @param l the <tt>ChatStateNotificationsListener</tt> to remove
     */
    public void removeChatStateListener(ChatStateNotificationsListener l)
    {
        Iterator<Contact> protoContacts = metaContact.getContacts();

        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetChatStateNotifications chatStateOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetChatStateNotifications.class);

            if (chatStateOpSet != null) {
                chatStateOpSet.removeChatStateNotificationsListener(l);
            }
        }
    }

    /**
     * Adds the given <tt>ContactPresenceStatusListener</tt> to listen for message events
     * in this chat session.
     *
     * @param l the <tt>ContactPresenceStatusListener</tt> to add
     */
    public void addContactStatusListener(ContactPresenceStatusListener l)
    {
        Iterator<Contact> protoContacts = metaContact.getContacts();
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
     * Removes the given <tt>ContactPresenceStatusListener</tt> from this chat session.
     *
     * @param l the <tt>ContactPresenceStatusListener</tt> to remove
     */
    public void removeContactStatusListener(ContactPresenceStatusListener l)
    {
        Iterator<Contact> protoContacts = metaContact.getContacts();

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
     * Returns a collection of last messages.
     *
     * @return a collection of last messages.
     */
    public Collection<ChatMessage> getHistory(boolean init)
    {
        // If chatFragment is initializing (initActive) and we have cached messages that include
        // history (historyLoaded == true) then just return the message cache.
        if (init && historyLoaded) {
            return msgCache;
        }

        final MetaHistoryService metaHistory = AndroidGUIActivator.getMetaHistoryService();

        // If the MetaHistoryService is not registered we have nothing to do here. The history
        // could be "disabled" from the user through one of the configuration forms.
        if (metaHistory == null)
            return msgCache;

        Collection<Object> history;
        // descriptor can either be metaContact or chatRoomWrapper (ChatRoom)
        Object descriptor = mChatSession.getDescriptor();

        if (msgCache.size() == 0) {
            // first time fetch, so read in last HISTORY_CHUNK_SIZE of history messages
            history = metaHistory.findLast(chatHistoryFilter, descriptor, HISTORY_CHUNK_SIZE);
        }
        else {
            // read in earlier than the 'last fetch date' of HISTORY_CHUNK_SIZE messages
            ChatMessage oldest;
            synchronized (cacheLock) {
                oldest = msgCache.get(0);
            }
            history = metaHistory.findLastMessagesBefore(chatHistoryFilter, descriptor,
                    oldest.getDate(), HISTORY_CHUNK_SIZE);
        }

        // Convert events into messages
        ArrayList<ChatMessage> historyMsgs = new ArrayList<>();

        for (Object o : history) {
            if (o instanceof MessageDeliveredEvent) {
                historyMsgs.add(ChatMessageImpl.getMsgForEvent((MessageDeliveredEvent) o));
            }
            else if (o instanceof MessageReceivedEvent) {
                historyMsgs.add(ChatMessageImpl.getMsgForEvent((MessageReceivedEvent) o));
            }
            else if (o instanceof ChatRoomMessageDeliveredEvent) {
                historyMsgs.add(ChatMessageImpl.getMsgForEvent((ChatRoomMessageDeliveredEvent) o));
            }
            else if (o instanceof ChatRoomMessageReceivedEvent) {
                historyMsgs.add(ChatMessageImpl.getMsgForEvent((ChatRoomMessageReceivedEvent) o));
            }
            else if (o instanceof FileRecord) {
                historyMsgs.add(ChatMessageImpl.getMsgForEvent((FileRecord) o));
            }
            else {
                logger.error("Unexpected event in history: " + o);
            }
        }

        synchronized (cacheLock) {
            if (!historyLoaded) {
                // We have something cached and we want to merge it with the history.
                // Do it only when we haven't merged it yet (ever).
                msgCache = mergeMsgLists(historyMsgs, msgCache, -1);
                historyLoaded = true;
            }
            else {
                // Otherwise just add in the newly fetched history
                msgCache.addAll(0, historyMsgs);
            }

            if (init)
                return msgCache;
            else
                return historyMsgs;
        }
    }

    /**
     * Merges given lists of messages. Output list is ordered by received date.
     *
     * @param list1 first list to merge.
     * @param list2 the second list to merge.
     * @param msgLimit output list size limit.
     * @return merged list of messages contained in given lists ordered by the date.
     * Output list size is limited to given <tt>msgLimit</tt>.
     */
    private List<ChatMessage> mergeMsgLists(List<ChatMessage> list1, List<ChatMessage> list2, int msgLimit)
    {
        if (msgLimit == -1)
            msgLimit = Integer.MAX_VALUE;

        List<ChatMessage> output = new LinkedList<>();
        int list1Idx = list1.size() - 1;
        int list2Idx = list2.size() - 1;

        while (list1Idx >= 0 && list2Idx >= 0 && output.size() < msgLimit) {
            ChatMessage list1Msg = list1.get(list1Idx);
            ChatMessage list2Msg = list2.get(list2Idx);

            if (list1Msg.getDate().after(list2Msg.getDate())) {
                output.add(0, list1Msg);
                list1Idx--;
            }
            else {
                output.add(0, list2Msg);
                list2Idx--;
            }
        }

        // Input remaining list 1 messages
        while (list1Idx >= 0 && output.size() < msgLimit) {
            output.add(0, list1.get(list1Idx--));
        }

        // Input remaining list 2 messages
        while (list2Idx >= 0 && output.size() < msgLimit) {
            output.add(0, list2.get(list2Idx--));
        }

        return output;
    }

    /**
     * Implements the <tt>Chat.isChatFocused</tt> method. Returns TRUE if this chat is
     * the currently selected and if the chat window, where it's contained is active.
     *
     * @return true if this chat has the focus and false otherwise.
     */
    @Override
    public boolean isChatFocused()
    {
        return mChatSession.getChatId().equals(ChatSessionManager.getCurrentChatId());
    }

    /**
     * Returns the message written by user in the chat write area.
     *
     * @return the message written by user in the chat write area
     */
    @Override
    public String getMessage()
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Bring this chat to front if <tt>b</tt> is true, hide it otherwise.
     *
     * @param isVisible tells if the chat will be made visible or not.
     */
    @Override
    public void setChatVisible(boolean isVisible)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Sets the given message as a message in the chat write area.
     *
     * @param message the text that would be set to the chat write area
     */
    @Override
    public void setMessage(String message)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Adds the given <tt>ChatFocusListener</tt> to this <tt>Chat</tt>. The
     * <tt>ChatFocusListener</tt> is used to inform other bundles when
     * a chat has changed its focus state.
     *
     * @param chatFocusListener the <tt>ChatFocusListener</tt> to add
     */
    @Override
    public void addChatFocusListener(ChatFocusListener chatFocusListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Removes the given <tt>ChatFocusListener</tt> from this <tt>Chat</tt>. The
     * <tt>ChatFocusListener</tt> is used to inform other bundles
     * when a chat has changed its focus state.
     *
     * @param chatFocusListener the <tt>ChatFocusListener</tt> to remove
     */
    @Override
    public void removeChatFocusListener(ChatFocusListener chatFocusListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Adds the given {@link KeyListener} to this <tt>Chat</tt>. The <tt>KeyListener</tt>
     * is used to inform other bundles when a user has typed in the chat editor area.
     *
     * @param keyListener the <tt>KeyListener</tt> to add
     */
    @Override
    public void addChatEditorKeyListener(KeyListener keyListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Removes the given {@link KeyListener} from this <tt>Chat</tt>. The <tt>KeyListener</tt>
     * is used to inform other bundles when a user has typed in the chat editor area.
     *
     * @param keyListener the <tt>ChatFocusListener</tt> to remove
     */

    @Override
    public void removeChatEditorKeyListener(KeyListener keyListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Adds the given {@link ChatMenuListener} to this <tt>Chat</tt>. The
     * <tt>ChatMenuListener</tt> is used to determine menu elements that
     * should be added on right clicks.
     *
     * @param chatMenuListener the <tt>ChatMenuListener</tt> to add
     */

    @Override
    public void addChatEditorMenuListener(ChatMenuListener chatMenuListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Adds the given {@link CaretListener} to this <tt>Chat</tt>. The <tt>CaretListener</tt>
     * is used to inform other bundles when a user has moved the caret in the chat editor area.
     *
     * @param caretListener the <tt>CaretListener</tt> to add
     */
    @Override
    public void addChatEditorCaretListener(CaretListener caretListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Adds the given {@link DocumentListener} to this <tt>Chat</tt>. The <tt>DocumentListener</tt>
     * is used to inform other bundles when a user has modified the document in the chat editor
     * area.
     *
     * @param documentListener the <tt>DocumentListener</tt> to add
     */
    @Override
    public void addChatEditorDocumentListener(DocumentListener documentListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Removes the given {@link ChatMenuListener} to this <tt>Chat</tt>. The
     * <tt>ChatMenuListener</tt> is used to determine menu elements
     * that should be added on right clicks.
     *
     * @param chatMenuListener the <tt>ChatMenuListener</tt> to add
     */

    @Override
    public void removeChatEditorMenuListener(ChatMenuListener chatMenuListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Removes the given {@link CaretListener} from this <tt>Chat</tt>. The <tt>CaretListener</tt>
     * is used to inform other bundles when a user has moved the caret in the chat editor area.
     *
     * @param caretListener the <tt>CaretListener</tt> to remove
     */

    @Override
    public void removeChatEditorCaretListener(CaretListener caretListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Removes the given {@link DocumentListener} from this <tt>Chat</tt>. The
     * <tt>DocumentListener</tt> is used to inform other bundles
     * when a user has modified the document in the chat editor area.
     *
     * @param documentListener the <tt>DocumentListener</tt> to remove
     */

    @Override
    public void removeChatEditorDocumentListener(DocumentListener documentListener)
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Adds a message to this <tt>Chat</tt>.
     *
     * @param contactName the name of the contact sending the message
     * @param date the time at which the message is sent or received
     * @param messageType the type of the message
     * @param mimeType the content encode type i.e plain or html
     * @param message the message text
     */
    @Override
    public void addMessage(String contactName, Date date, String messageType, int mimeType, String message)
    {
        addMessage(contactName, contactName, date, messageType, mimeType, message, ChatMessage.ENCRYPTION_NONE,
                null, null);
    }

    /**
     * Adds a message to this <tt>Chat</tt>.
     *
     * @param contactName the name of the contact sending the message
     * @param displayName the display name of the contact
     * @param date the time at which the message is sent or received
     * @param messageType the type of the message. One of MESSAGE_OUT or MESSAGE_IN
     * @param mimeType the content encode type i.e plain or html
     * @param message the message text
     * @param encryptionType the content encryption type
     */
    public void addMessage(String contactName, String displayName, Date date, String messageType, int mimeType,
            String message, int encryptionType, String messageUID, String correctedMessageUID)
    {
        int chatMsgType = chatTypeToChatMsgType(messageType);
        ChatMessageImpl chatMessage = new ChatMessageImpl(contactName, displayName, date, chatMsgType, mimeType,
                message, encryptionType, messageUID, correctedMessageUID);

        synchronized (cacheLock) {
            // Must always cache the chatMsg as chatFragment has not registered to handle incoming
            // message on first onAttach or when it is not in focus.
            cacheNextMsg(chatMessage);

            for (ChatSessionListener l : msgListeners) {
                l.messageAdded(chatMessage);
            }
        }
    }

    /*
     * ChatMessage for IncomingFileTransferRequest
     *
     * Adds the given <tt>IncomingFileTransferRequest</tt> to the conversation panel in order to
     * notify the user of the incoming file.
     *
     * @param fileTransferOpSet the file transfer operation set
     *
     * @param request the request to display in the conversation panel
     *
     * @param date the date on which the request has been received
     */
    public void addFTRequest(OperationSetFileTransfer opSet, IncomingFileTransferRequest request, Date date)
    {
        Contact sender = request.getSender();
        String senderName = sender.getAddress();
        String message = aTalkApp.getResString(
                R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, date.toString(), senderName);

        int msgType = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;
        int mimeType = ChatMessage.ENCODE_PLAIN;
        ChatMessageImpl chatMsg = new ChatMessageImpl(senderName, date, msgType, mimeType, message, opSet, request);

        synchronized (cacheLock) {
            // Must cache chatMsg as chatFragment has not registered to handle incoming message on
            // first onAttach or when it is not in focus
            cacheNextMsg(chatMsg);

            for (ChatSessionListener l : msgListeners) {
                l.messageAdded(chatMsg);
            }
        }
    }

    /**
     * Adds a new ChatLinkClickedListener. The callback is called for every link whose scheme is
     * <tt>jitsi</tt>. It is the callback's responsibility to filter the action based on the URI.
     * <p>
     * Example:<br>
     * <tt>jitsi://classname/action?query</tt><br>
     * Use the name of the registering class as the host, the action to execute as the path and
     * any parameters as the query.
     *
     * @param chatLinkClickedListener callback that is notified when a link was clicked.
     */
    @Override
    public void addChatLinkClickedListener(ChatLinkClickedListener chatLinkClickedListener)
    {
        ChatSessionManager.addChatLinkListener(chatLinkClickedListener);
    }

    /**
     * Removes an existing ChatLinkClickedListener
     *
     * @param chatLinkClickedListener the already registered listener to remove.
     */

    @Override
    public void removeChatLinkClickedListener(ChatLinkClickedListener chatLinkClickedListener)
    {
        ChatSessionManager.removeChatLinkListener(chatLinkClickedListener);
    }

    /**
     * Provides the {@link Highlighter} used in rendering the chat editor.
     *
     * @return highlighter used to render message being composed
     */
    @Override
    public Highlighter getHighlighter()
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Gets the caret position in the chat editor.
     *
     * @return index of caret in message being composed
     */
    @Override
    public int getCaretPosition()
    {
        throw new RuntimeException("Not supported yet");
    }

    /**
     * Causes the chat to validate its appearance (suggests a repaint operation may be necessary).
     */
    @Override
    public void promptRepaint()
    {
        throw new RuntimeException("Not supported yet");
    }

    public static int chatTypeToChatMsgType(String msgType)
    {
        switch (msgType) {
            case Chat.ACTION_MESSAGE:
                return ChatMessage.MESSAGE_ACTION;
            case Chat.ERROR_MESSAGE:
                return ChatMessage.MESSAGE_ERROR;
            case Chat.HISTORY_INCOMING_MESSAGE:
                return ChatMessage.MESSAGE_HISTORY_IN;
            case Chat.HISTORY_OUTGOING_MESSAGE:
                return ChatMessage.MESSAGE_HISTORY_OUT;
            case Chat.INCOMING_MESSAGE:
                return ChatMessage.MESSAGE_IN;
            case Chat.OUTGOING_MESSAGE:
                return ChatMessage.MESSAGE_OUT;
            case Chat.SMS_MESSAGE:
                return ChatMessage.MESSAGE_SMS_IN;
            case Chat.STATUS_MESSAGE:
                return ChatMessage.MESSAGE_STATUS;
            case Chat.SYSTEM_MESSAGE:
                return ChatMessage.MESSAGE_SYSTEM;
            case INCOMING_FILE_MESSAGE:
                return ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;
            case OUTGOING_FILE_MESSAGE:
                return ChatMessage.MESSAGE_FILE_TRANSFER_SEND;
            default:
                throw new IllegalArgumentException("Not supported msg type: " + msgType);
        }
    }

    /**
     * Returns the shortened display name of this chat.
     *
     * @return the shortened display name of this chat
     */
    public String getShortDisplayName()
    {
        String transportDisplayName = mCurrentChatTransport.getDisplayName().trim();
        int atIndex = transportDisplayName.indexOf("@");

        if (atIndex > -1)
            transportDisplayName = transportDisplayName.substring(0, atIndex);

        int spaceIndex = transportDisplayName.indexOf(" ");
        if (spaceIndex > -1)
            transportDisplayName = transportDisplayName.substring(0, spaceIndex);

        return transportDisplayName;
    }

    @Override
    public void messageReceived(MessageReceivedEvent messageReceivedEvent)
    {
        // cmeng: only handle messageReceivedEvent belongs to this.metaContact
        if ((metaContact != null) && metaContact.containsContact(messageReceivedEvent.getSourceContact())) {
            synchronized (cacheLock) {
                // Must cache chatMsg as chatFragment has not registered to handle incoming
                // message on first onAttach or not in focus
                cacheNextMsg(ChatMessageImpl.getMsgForEvent(messageReceivedEvent));

                for (MessageListener l : msgListeners) {
                    l.messageReceived(messageReceivedEvent);
                }
            }
        }
    }

    /**
     * Caches next message.
     *
     * @param newMsg the next message to cache.
     */
    private void cacheNextMsg(ChatMessageImpl newMsg)
    {
        msgCache.add(newMsg);
    }

    /**
     * Clear the old message message if underlying data changed.
     */
    public void clearMsgCache()
    {
        msgCache.clear();
        this.historyLoaded = false;
    }

    @Override
    public void messageDelivered(MessageDeliveredEvent messageDeliveredEvent)
    {
        /*
         * (metaContact == null) for ConferenceChatTransport. Check just in case the listener is not properly
         * removed when the chat is closed. Only handle messageReceivedEvent belongs to this.metaContact
         */
        if ((metaContact != null) && metaContact.containsContact(messageDeliveredEvent.getDestinationContact())) {
            synchronized (cacheLock) {
                for (MessageListener l : msgListeners) {
                    l.messageDelivered(messageDeliveredEvent);
                }
                cacheNextMsg(ChatMessageImpl.getMsgForEvent(messageDeliveredEvent));
            }
        }
    }

    @Override
    public void messageDeliveryFailed(MessageDeliveryFailedEvent evt)
    {
        for (MessageListener l : msgListeners) {
            l.messageDeliveryFailed(evt);
        }
        // Insert error message
        logger.error(evt.getReason());

        String errorMsg;
        Message sourceMessage = (Message) evt.getSource();
        Contact sourceContact = evt.getDestinationContact();
        MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(sourceContact);

        if (evt.getErrorCode() == MessageDeliveryFailedEvent.OFFLINE_MESSAGES_NOT_SUPPORTED) {
            errorMsg = aTalkApp.getResString(
                    R.string.service_gui_MSG_DELIVERY_NOT_SUPPORTED, sourceContact.getDisplayName());
        }
        else if (evt.getErrorCode() == MessageDeliveryFailedEvent.NETWORK_FAILURE) {
            errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_NOT_DELIVERED);
        }
        else if (evt.getErrorCode() == MessageDeliveryFailedEvent.PROVIDER_NOT_REGISTERED) {
            errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_SEND_CONNECTION_PROBLEM);
        }
        else if (evt.getErrorCode() == MessageDeliveryFailedEvent.INTERNAL_ERROR) {
            errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_INTERNAL_ERROR);
        }
        else {
            errorMsg = aTalkApp.getResString(R.string.service_gui_MSG_DELIVERY_ERROR);
        }

        String reason = evt.getReason();
        if (reason != null)
            errorMsg += " " + aTalkApp.getResString(R.string.service_gui_ERROR_WAS, reason);

        addMessage(metaContact.getDisplayName(), new Date(), Chat.OUTGOING_MESSAGE,
                sourceMessage.getMimeType(), sourceMessage.getContent());
        addMessage(metaContact.getDisplayName(), new Date(), Chat.ERROR_MESSAGE, ChatMessage.ENCODE_PLAIN, errorMsg);
    }

    /**
     * Extends <tt>MessageListener</tt> interface in order to provide notifications about injected
     * messages without the need of event objects.
     *
     * @author Pawel Domas
     */
    interface ChatSessionListener extends MessageListener
    {
        void messageAdded(ChatMessage msg);
    }

    /**
     * Updates the status of the given chat transport in the send via selector box and notifies
     * the user for the status change.
     *
     * @param chatTransport the <tt>chatTransport</tt> to update
     */
    public void updateChatTransportStatus(final ChatTransport chatTransport)
    {
        if (isChatFocused()) {
            final Activity activity = aTalkApp.getCurrentActivity();
            activity.runOnUiThread(new Runnable()
            {
                public void run()
                {
                    PresenceStatus presenceStatus = chatTransport.getStatus();
                    ActionBarUtil.setSubtitle(activity, presenceStatus.getStatusName());
                    ActionBarUtil.setStatus(activity, presenceStatus.getStatusIcon());
                }
            });
        }

        String contactName = ((MetaContactChatTransport) chatTransport).getContact().getAddress();
        if (ConfigurationUtils.isShowStatusChangedInChat()) {
            // Show a status message to the user.
            this.addMessage(contactName, chatTransport.getName(), new Date(), Chat.STATUS_MESSAGE, ChatMessage.ENCODE_PLAIN,
                    aTalkApp.getResString(R.string.service_gui_STATUS_CHANGED_CHAT_MESSAGE, chatTransport.getStatus().getStatusName()),
                    ChatMessage.ENCRYPTION_NONE, null, null);
        }
    }

    /**
     * Renames all occurrences of the given <tt>chatContact</tt> in this chat panel.
     *
     * @param chatContact the contact to rename
     * @param name the new name
     */
    public void setContactName(ChatContact<?> chatContact, final String name)
    {
        if (isChatFocused()) {
            final Activity activity = aTalkApp.getCurrentActivity();
            activity.runOnUiThread(new Runnable()
            {
                public void run()
                {
                    if (mChatSession instanceof MetaContactChatSession) {
                        ActionBarUtil.setTitle(activity, name);
                    }
                }
            });
        }
    }

    /**
     * Sets the given <tt>subject</tt> to this chat.
     *
     * @param subject the subject to set
     */
    public void setChatSubject(final String subject)
    {
        if ((chatSubject.length() != 0) && !chatSubject.equals(subject)) {
            chatSubject = subject;

            this.addMessage(mChatSession.getChatName(), new Date(), Chat.STATUS_MESSAGE, ChatMessage.ENCODE_PLAIN,
                    aTalkApp.getResString(R.string.service_gui_CHAT_ROOM_SUBJECT_CHANGED, mChatSession.getChatName(), subject));
        }
    }

    /**
     * Updates the contact status - call from conference only.
     *
     * @param chatContact the chat contact of the conference to update
     * @param statusMessage the status message to show
     */
    public void updateChatContactStatus(final ChatContact<?> chatContact,
            final String statusMessage)
    {
        if (isChatFocused()) {
            final Activity activity = aTalkApp.getCurrentActivity();
            activity.runOnUiThread(new Runnable()
            {
                public void run()
                {
                    // cmeng: check instanceof just in case
                    if (mChatSession instanceof ConferenceChatSession) {
                        ActionBarUtil.setStatus(activity, mChatSession.getChatStatusIcon());

                        // mSubTitle = ccSession.getChatSubject();
                        String mSubTitle = "";
                        Iterator<ChatContact<?>> mParticipants = mChatSession.getParticipants();
                        while (mParticipants.hasNext()) {
                            mSubTitle += mParticipants.next().getName() + ", ";
                        }
                        ActionBarUtil.setSubtitle(activity, mSubTitle);
                    }
                }
            });
        }
        if (!StringUtils.isNullOrEmpty(statusMessage)) {
            String contactName
                    = ((ChatRoomMemberJabberImpl) chatContact.getDescriptor()).getContactAddress();
            this.addMessage(contactName, chatContact.getName(), new Date(), Chat.STATUS_MESSAGE, ChatMessage.ENCODE_PLAIN,
                    statusMessage, ChatMessage.ENCRYPTION_NONE, null, null);
        }
    }

    /**
     * Returns the first chat transport for the current chat session that supports group chat.
     *
     * @return the first chat transport for the current chat session that supports group chat.
     */
    public ChatTransport findInviteChatTransport()
    {
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
     * Invites the given <tt>chatContacts</tt> to this chat.
     *
     * @param inviteChatTransport the chat transport to use to send the invite
     * @param chatContacts the contacts to invite
     * @param reason the reason of the invitation
     */
    public void inviteContacts(ChatTransport inviteChatTransport, Collection<String> chatContacts, String reason)
    {
        ChatSession conferenceChatSession = null;
        ProtocolProviderService pps = inviteChatTransport.getProtocolProvider();

        if (mChatSession instanceof MetaContactChatSession) {
            ConferenceChatManager conferenceChatManager = AndroidGUIActivator.getUIService().getConferenceChatManager();

            // the chat session is set regarding to which OpSet is used for MUC
            if (pps.getOperationSet(OperationSetMultiUserChat.class) != null) {
                ChatRoomWrapper chatRoomWrapper = AndroidGUIActivator.getMUCService()
                        .createPrivateChatRoom(pps, chatContacts, reason, false);
                // conferenceChatSession = new ConferenceChatSession(this, chatRoomWrapper);
                Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                aTalkApp.getGlobalContext().startActivity(chatIntent);

            }
            else if (pps.getOperationSet(OperationSetAdHocMultiUserChat.class) != null) {
                AdHocChatRoomWrapper chatRoomWrapper = conferenceChatManager.createAdHocChatRoom
                        (pps, chatContacts, reason);
                // conferenceChatSession = new AdHocConferenceChatSession(this, chatRoomWrapper);
                Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                aTalkApp.getGlobalContext().startActivity(chatIntent);
            }
            // if (conferenceChatSession != null) {
            // this.setChatSession(conferenceChatSession);
            // }
        }
        // We're already in a conference chat.
        else {
            for (String contactAddress : chatContacts) {
                try {
                    mCurrentChatTransport.inviteChatContact(JidCreate.entityBareFrom(contactAddress), reason);
                } catch (XmppStringprepException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
