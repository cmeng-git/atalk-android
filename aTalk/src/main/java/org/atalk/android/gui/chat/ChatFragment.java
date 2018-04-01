/*
 * 
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import net.java.sip.communicator.impl.protocol.jabber.OperationSetPersistentPresenceJabberImpl;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.util.ByteFormat;
import net.java.sip.communicator.util.GuiUtils;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.StatusUtil;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.AndroidLoginRenderer;
import org.atalk.android.gui.chat.filetransfer.FileHistoryConversation;
import org.atalk.android.gui.chat.filetransfer.ReceiveFileConversation;
import org.atalk.android.gui.chat.filetransfer.SendFileConversation;
import org.atalk.android.gui.contactlist.model.MetaContactRenderer;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.HtmlImageGetter;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.plugin.geolocation.SplitStreetViewPanoramaAndMapActivity;
import org.atalk.crypto.CryptoFragment;
import org.atalk.crypto.listener.CryptoModeChangeListener;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.util.XmppStringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * The <tt>ChatFragment</tt> working in conjunction with ChatActivity, ChatPanel, ChatController
 * etc is providing the UI for all the chat messages/info receive and display.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatFragment extends OSGiFragment
        implements ChatSessionManager.CurrentChatListener, FileTransferStatusListener, CryptoModeChangeListener
{
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(ChatFragment.class);

    /**
     * The session adapter for the contained <tt>ChatPanel</tt>.
     */
    private ChatListAdapter chatListAdapter;

    /**
     * The corresponding <tt>ChatPanel</tt>.
     */
    private ChatPanel chatPanel;

    /**
     * chat MetaContact associated with the chatFragment
     */
    MetaContact mChatMetaContact;

    /**
     * The chat list view representing the chat.
     */
    private ListView chatListView;

    /**
     * List header used to display progress bar when history is being loaded.
     */
    private View header;

    /**
     * Remembers first visible view to scroll the list after new portion of history messages is
     * added.
     */
    public int scrollFirstVisible;

    /**
     * Remembers top position to add to the scrolling offset after new portion
     * of history messages is added.
     */
    public int scrollTopOffset;

    /**
     * The chat state view.
     */
    private LinearLayout chatStateView;

    /**
     * The task that loads history.
     */
    private LoadHistoryTask loadHistoryTask;

    /**
     * Stores all active file transfer requests and effective transfers with the
     * identifier of the transfer.
     */
    private final Hashtable<String, Object> activeFileTransfers = new Hashtable<>();

    /**
     * Stores all active file transfer requests and effective DisplayMessage position.
     */
    private final Hashtable<String, Integer> activeMsgTransfers = new Hashtable<>();

    /**
     * Indicates that this fragment is the currently selected primary page. A primary page can
     * have both onShow and onHide (overlay with other dialog) state. This setting is important,
     * because of PagerAdapter is being used on phone layouts.
     *
     * @see #setPrimarySelected(boolean)
     * @see #onResume()
     */
    private boolean primarySelected = false;

    public final static int MSGTYPE_UNKNOWN = 0x0;
    public final static int MSGTYPE_NORMAL = 0x1;

    public final static int MSGTYPE_OMEMO = 0x02;
    public final static int MSGTYPE_OMEMO_UT = 0x42;
    public final static int MSGTYPE_OMEMO_UA = 0x82;

    public final static int MSGTYPE_OTR = 0x03;
    public final static int MSGTYPE_OTR_UA = 0x83;

    public final static int MSGTYPE_MUC_NORMAL = 0x04;

    /*
     * Current chatType that is in use.
     */
    private int mChatType = MSGTYPE_UNKNOWN;

    private View mCFView;
    public boolean clearMsgCache = false;

    /**
     * The chat controller used to handle operations like editing and sending messages associated
     * with this fragment.
     */
    private ChatController chatController;

    /**
     * The current chat transport.
     */
    private ChatTransport currentChatTransport;

    /**
     * The current chatFragment.
     */
    private ChatFragment currentChatFragment;
    private CryptoFragment cryptoFragment;

    private OtrContact otrContact = null;

    private static int MAX_COUNTDOWN_TIME = 20000; // ms
    private static int COUNTDOWN_INTERVAL = 1000; // ms
    // private static int COUNTDOWN_SHOWTIME = 5; // s

    /**
     * Flag indicates that we have loaded the history for the first time.
     */
    private boolean historyLoaded = false;

    private Activity mActivity = null;

    private boolean mSVP_Started;
    private SplitStreetViewPanoramaAndMapActivity mSVP = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        this.chatController = new ChatController(activity, this);
        mActivity = activity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mCFView = inflater.inflate(R.layout.chat_conversation, container, false);
        chatListAdapter = new ChatListAdapter();
        chatListView = mCFView.findViewById(R.id.chatListView);

        // Inflates and adds the header, hidden by default
        this.header = inflater.inflate(R.layout.progressbar, chatListView, false);
        header.setVisibility(View.GONE);
        chatListView.addHeaderView(header);

        chatStateView = mCFView.findViewById(R.id.chatStateView);
        chatListView.setAdapter(chatListAdapter);
        chatListView.setSelector(R.drawable.contact_list_selector);
        initListViewListeners();

        // Chat intent handling
        Bundle arguments = getArguments();
        String chatId = arguments.getString(ChatSessionManager.CHAT_IDENTIFIER);

        if (chatId == null)
            throw new IllegalArgumentException();

        chatPanel = ChatSessionManager.getActiveChat(chatId);
        if (chatPanel == null) {
            logger.error("Chat for given id: " + chatId + " not exists");
            return null;
        }

        // mChatMetaContact remains null for conference
        mChatMetaContact = chatPanel.getMetaContact();

        chatPanel.addMessageListener(chatListAdapter);
        currentChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
        currentChatFragment = this;

        cryptoFragment = (CryptoFragment) getActivity().getSupportFragmentManager()
                .findFragmentByTag(ChatActivity.CRYPTO_FRAGMENT);
        return mCFView;
    }

    private void initListViewListeners()
    {
        chatListView.setOnScrollListener(new AbsListView.OnScrollListener()
        {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState)
            {
                // Proceed only if there is no active file transfer in progress
                // Detects event when user scrolls to the top of the list
                View childFirst = chatListView.getChildAt(0);
                if ((activeFileTransfers.size() == 0) && (scrollState == 0)
                        && childFirst != null) {
                    if (childFirst.getTop() == 0) {
                        // Loads some more history if there's no loading task in progress
                        if (loadHistoryTask == null) {
                            loadHistoryTask = new LoadHistoryTask(false);
                            loadHistoryTask.execute();
                        }
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
            {
                // Remembers scrolling position to restore after new history messages are loaded
                scrollFirstVisible = firstVisibleItem;
                View firstVisible = view.getChildAt(0);
                scrollTopOffset = firstVisible != null ? firstVisible.getTop() : 0;
            }
        });

        chatListView.setOnTouchListener(new AbsListView.OnTouchListener()
        {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    chatController.onTouchAction();
                }
                return false;
            }
        });

        // Using the contextual action mode with multi-selection
        chatListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        chatListView.setMultiChoiceModeListener(mMultiChoiceListener);

        chatListView.setOnItemLongClickListener(new ListView.OnItemLongClickListener()
        {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
            {
                Toast.makeText(getContext(), R.string.chat_message_long_press_hint, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    /**
     * Creates new parametrized instance of <tt>ChatFragment</tt>.
     *
     * @param chatId optional phone number that will be filled.
     * @return new parametrized instance of <tt>ChatFragment</tt>.
     */
    public static ChatFragment newInstance(String chatId)
    {
        if (logger.isDebugEnabled())
            logger.debug("CHAT FRAGMENT NEW INSTANCE: " + chatId);

        ChatFragment chatFragment = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ChatSessionManager.CHAT_IDENTIFIER, chatId);
        chatFragment.setArguments(args);
        return chatFragment;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        /*
         * If this chatFragment is added to the pager adapter for the first time it is required to
		 * check again, because it's marked visible when the Views are not created yet
		 *
		 * cmeng - seeing problem as it includes other non-focus chatFragment causing non-sync
		 * between chatFragment and chatController i.e. msg sent to wrong chatFragment
		 * - resolved with initChatController() passing focus state as parameter, taking
		 * appropriate actions pending the focus state;
		 *
		 *  Perform chatTransport changes only if and only if this chatFragment is the
		 *  selected primary page - notified by chatPager (Check at ChatController)
		 */
        if (primarySelected) {
            initChatController(true);

            // Invoke the listener valid only of metaContactChatSession
            if (chatPanel.getChatSession() instanceof MetaContactChatSession) {
                chatPanel.addContactStatusListener(chatListAdapter);
                chatPanel.addChatStateListener(chatListAdapter);
            }

            ChatSessionManager.addCurrentChatListener(this);
            mSVP_Started = false;
            mSVP = null;
        }
    }

    @Override
    public void onPause()
    {
        // Remove the listener valid only of metaContactChatSession
        if (chatPanel.getChatSession() instanceof MetaContactChatSession) {
            chatPanel.removeContactStatusListener(chatListAdapter);
            chatPanel.removeChatStateListener(chatListAdapter);
        }

        // Not required - implemented as static map
        // cryptoFragment.removeCryptoModeListener(this);
        ChatSessionManager.removeCurrentChatListener(this);

		/*
         * Indicates that this fragment is no longer in focus, because of this call parent
		 * <tt>Activities don't have to call it in onPause().
		 */
        initChatController(false);

		/*
		 * Need to clear msgCache onPause, forcing chatFragment to use FileRecord for view display
		  * on resume. The file transfer status is only reflected in DisplayMessage messages
		 */
        if (clearMsgCache) {
            chatPanel.clearMsgCache();
        }
        super.onPause();
    }

    @Override
    public void onStop()
    {
        super.onStop();
        chatController.onChatCloseAction();
    }

    /**
     *
     */
    public void onDetach()
    {
        if (logger.isDebugEnabled())
            logger.debug("DETACH CHAT FRAGMENT: " + this);

        super.onDetach();
        this.chatController = null;

        if (chatPanel != null) {
            chatPanel.removeMessageListener(chatListAdapter);
        }

        chatListAdapter = null;
        if (loadHistoryTask != null) {
            loadHistoryTask.cancel(true);
            loadHistoryTask = null;
        }
    }

    /**
     * This method must be called by parent <tt>Activity</tt> or <tt>Fragment</tt> in order to
     * register the ChatController. Setting of primarySelected must solely be performed by
     * chatPagerAdapter only to ensure both the chatFragment and chatController are in sync.
     *
     * @param isSelected <tt>true</tt> if the fragment is now the primary selected page.
     * @see ChatController#initChatController()
     */
    public void setPrimarySelected(boolean isSelected)
    {
        logger.debug("Primary page selected: " + hashCode() + " " + isSelected);
        primarySelected = isSelected;
        initChatController(isSelected);
    }

    /**
     * Checks for <tt>ChatController</tt> initialization. To init/activate the controller fragment
     * must be visible and its View must be created.
     * <p>
     * Non-focus chatFragment causing non-sync between chatFragment and chatController i.e.
     * sending & received messages sent to wrong chatFragment - resolved with initChatController()
     * passing in focus state as parameter; taking the appropriate actions pending the focus state;
     * <p>
     */
    private void initChatController(boolean inFocus)
    {
        if (inFocus && chatListView != null) {
            logger.debug("Init controller: " + hashCode());
            chatController.onShow();
            // Also register global status listener
            AndroidGUIActivator.getLoginRenderer().addGlobalStatusListener(globalStatusListener);

            // Init the history & ChatType background color
            chatStateView.setVisibility(View.INVISIBLE);
            initAdapter();

            // Seem mCFView change on rentry into chatFragment, so update the listener
            cryptoFragment.addCryptoModeListener(currentChatTransport.getDescriptor(), this);
            // initBackgroundColor();
            changeBackground(mCFView, chatPanel.getChatType());
        }
        else if (!inFocus && (chatController != null)) {
            chatController.onHide();
            // Also remove global status listener
            AndroidGUIActivator.getLoginRenderer().removeGlobalStatusListener(globalStatusListener);
        }
        else {
            logger.debug("Skipping controller init... " + hashCode());
        }
    }

    /**
     * Initializes the chat list adapter.
     */
    private void initAdapter()
    {
		/*
		 * Initial history load is delayed until the chat is displayed to the user. We previously
		 * relayed on onCreate, but it will be called too early on phone layouts where
		 * ChatPagerAdapter is used. It creates ChatFragment too early that is before the first
		 * message is added to the history and we are unable to retrieve it without hacks.
		 */
        if (!historyLoaded) {
			/*
			 * chatListAdapter.isEmpty() is used as initActive flag, chatPanel.msgCache must be
			 * cleared. Otherwise it will cause chatPanel.getHistory to return old data when the
			 * underlying data changed or adapter has been cleared
			 */
            loadHistoryTask = new LoadHistoryTask(chatListAdapter.isEmpty());
            loadHistoryTask.execute();
            historyLoaded = true;
        }
    }

    /**
     * Tasks that need to be performed for the current chatFragment when chat history are deleted.
     * - Cancel any file transfer in progress
     * - Clean up all the display messages that are in the chatList Adapter
     * - Clear all the message that has been previously stored in the msgCache
     * - Force to reload history by clearing historyLoad = false;
     * - Refresh the display
     */
    public void onClearCurrentEntityChatHistory()
    {
        cancelActiveFileTransfers();
        chatListAdapter.clearMessage();
        chatPanel.clearMsgCache();
        historyLoaded = false;
        initChatController(true);
    }

    /**
     * ActionMode with multi-selection implementation for chatListView
     */
    private AbsListView.MultiChoiceModeListener mMultiChoiceListener = new AbsListView.MultiChoiceModeListener()
    {
        int cPos;
        int headerCount;
        int checkListSize;
        SparseBooleanArray checkedList;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked)
        {
            // Here you can do something when items are selected/de-selected
            checkedList = chatListView.getCheckedItemPositions();
            checkListSize = checkedList.size();

            // Position must be aligned to the number of header views included and
            // ensure the selected view is the last MESSAGE_OUT for edit action
            cPos = position - headerCount;
            boolean visible = false;
            if (chatListAdapter.getItemViewType(cPos) == ChatListAdapter.OUTGOING_MESSAGE_VIEW) {
                visible = true;
                if (cPos != (chatListAdapter.getCount() - 1)) {
                    for (int i = cPos + 1; i < chatListAdapter.getCount(); i++) {
                        if (chatListAdapter.getItemViewType(i) == ChatFragment.ChatListAdapter.OUTGOING_MESSAGE_VIEW) {
                            visible = false;
                            break;
                        }
                    }
                }
            }
            mode.getMenu().findItem(R.id.chat_message_edit).setVisible(visible);
            mode.getMenu().findItem(R.id.select_all).setVisible(chatListAdapter.getCount() > 1);
            mode.invalidate();
            chatListView.setSelection(position);
            mode.setTitle(chatListView.getCheckedItemCount() + " selected");
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            ChatMessage chatMsg;
            switch (item.getItemId()) {
                case R.id.chat_message_edit:
                    chatMsg = chatListAdapter.getMessage(cPos);
                    if (chatController != null) {
                        chatController.editText(chatListView, chatMsg, cPos);
                    }
                    return true;

                case R.id.select_all:
                    int size = chatListAdapter.getCount();
                    if (size < 2)
                        return true;

                    for (int i = 0; i < size; i++) {
                        cPos = i + headerCount;
                        checkedList.put(cPos, true);
                        chatListView.setSelection(cPos);
                    }
                    checkListSize = size;
                    mode.invalidate();
                    mode.setTitle(size + " selected");
                    return true;

                case R.id.copy_to_clipboard:
                    // Get clicked message text and copy it to ClipBoard
                    String text = "";
                    for (int i = 0; i < checkListSize; i++) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount;
                            chatMsg = chatListAdapter.getMessage(cPos);
                            if (chatMsg != null) {
                                if (i > 0)
                                    text += "\n" + chatMsg.getContentForClipboard();
                                else
                                    text += chatMsg.getContentForClipboard();
                            }
                        }
                    }
                    ClipboardManager cmgr = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    cmgr.setPrimaryClip(ClipData.newPlainText(null, text));
                    mode.finish(); // Action picked, so close the CAB
                    return true;

                case R.id.chat_message_del:
                    List<String> msgUid = new ArrayList<>();
                    for (int i = 0; i < checkListSize; i++) {
                        if (checkedList.valueAt(i)) {
                            cPos = checkedList.keyAt(i) - headerCount;
                            int cType = chatListAdapter.getItemViewType(cPos);
                            if ((cType == ChatListAdapter.INCOMING_MESSAGE_VIEW)
                                    || (cType == ChatListAdapter.OUTGOING_MESSAGE_VIEW)
                                    || (cType == ChatListAdapter.FILE_TRANSFER_MESSAGE_VIEW)) {
                                chatMsg = chatListAdapter.getMessage(cPos);
                                if (chatMsg != null) {
                                    if (chatMsg instanceof MergedMessage) {
                                        msgUid.addAll(((MergedMessage) chatMsg).getMessageUIDs());
                                    }
                                    else {
                                        msgUid.add(chatMsg.getMessageUID());
                                    }
                                }
                            }
                        }
                    }
                    EntityListHelper.eraseEntityChatHistory(getActivity(), chatPanel.getChatSession().getDescriptor(), msgUid);
                    mode.finish(); // Action picked, so close the CAB
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
            inflater.inflate(R.menu.chat_msg_ctx_menu, menu);
            headerCount = chatListView.getHeaderViewsCount();
            return true;
        }

        // Called each time the action mActionMode is shown. Always called after
        // onCreateActionMode, but
        // may be called multiple times if the mActionMode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            // Here you can perform updates to the CAB due to an invalidate() request
            return false; // Return false if nothing is done
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

    /**
     * Refresh avatar and globals status display on change.
     */
    private EventListener<PresenceStatus> globalStatusListener = new EventListener<PresenceStatus>()
    {
        @Override
        public void onChangeEvent(PresenceStatus eventObject)
        {
            if (chatListAdapter != null)
                chatListAdapter.localAvatarOrStatusChanged();
        }
    };

    /**
     * Returns the corresponding <tt>ChatPanel</tt>.
     *
     * @return the corresponding <tt>ChatPanel</tt>
     */
    public ChatPanel getChatPanel()
    {
        return chatPanel;
    }

    /**
     * Returns the corresponding <tt>ChatPanel</tt>.
     *
     * @return the corresponding <tt>ChatPanel</tt>
     */
    public ChatFragment getCurrentChatFragment()
    {
        return currentChatFragment;
    }

    /**
     * Returns the underlying chat list view.
     *
     * @return the underlying chat list view
     */
    public ListView getChatListView()
    {
        return chatListView;
    }

    /**
     * Returns the underlying chat list view.
     *
     * @return the underlying chat list view
     */
    public ChatListAdapter getChatListAdapter()
    {
        return chatListAdapter;
    }

    /**
     * The ChatListAdapter is a container with rows of send and received messages, file transfer
     * status information and system information.
     */
    public class ChatListAdapter extends BaseAdapter implements ChatPanel.ChatSessionListener,
            ContactPresenceStatusListener, ChatStateNotificationsListener
    {
        /**
         * The list of chat message displays. All access and modification of this list must be
         * done on the UI thread.
         */
        private final List<MessageDisplay> messages = new ArrayList<>();

        /**
         * The type of the incoming message view.
         */
        static final int INCOMING_MESSAGE_VIEW = 0;

        /**
         * The type of the outgoing message view.
         */
        static final int OUTGOING_MESSAGE_VIEW = 1;

        /**
         * The type of the system message view.
         */
        static final int SYSTEM_MESSAGE_VIEW = 2;

        /**
         * The type of the error message view.
         */
        static final int ERROR_MESSAGE_VIEW = 3;

        /**
         * The type for corrected message view.
         */
        static final int CORRECTED_MESSAGE_VIEW = 4;

        /**
         * The type for Receive File message view.
         */
        public static final int FILE_TRANSFER_MESSAGE_VIEW = 5;

        /**
         * Maximum number of message view types support
         */
        static final int VIEW_TYPE_MAX = 6;

        /**
         * Counter used to generate row ids.
         */
        private long idGenerator = 0;

        /**
         * HTML image getter.
         */
        private final Html.ImageGetter imageGetter = new HtmlImageGetter();

        public ChatTransport getFragmentContact()
        {
            return currentChatTransport;
        }

        /**
         * Pass the message to the <tt>ChatListAdapter</tt> for processing; appends it at the
         * end or merge it with the last consecutive message.
         * {@link #addMessageImpl} method must only be processed on UI thread.
         *
         * @param newMessage the message to add.
         * @param update if set to <tt>true</tt> will notify the UI about update immediately.
         */
        public void addMessage(final ChatMessage newMessage, final boolean update)
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    if (chatListAdapter == null) {
                        logger.warn("Add message handled, when there's no adapter - possibly after onDetach()");
                        return;
                    }
                    addMessageImpl(newMessage, update);
                }
            });
        }

        /**
         * This method (as well as any other that access the {@link #messages} list) must only be
         * called on the UI thread.
         * <p>
         * It creates a new message view holder if this is first message or if this is a new
         * message received i.e. non-consecutive. NotifyDataChanged for update if <tt>true</tt>
         */
        private void addMessageImpl(ChatMessage newMessage, boolean update)
        {
            int msgIdx;
            int lastMsgIdx = getLastMessageIdx(newMessage);
            ChatMessage lastMsg = (lastMsgIdx != -1) ? chatListAdapter.getMessage(lastMsgIdx) : null;

            // Create a new message view holder only if message is non-consecutive
            if ((lastMsg == null) || (!lastMsg.isConsecutiveMessage(newMessage))) {
                messages.add(new MessageDisplay(newMessage));
                msgIdx = messages.size() - 1;
            }
            else {
                // Merge the message and update the object in the list
                messages.get(lastMsgIdx).update(lastMsg.mergeMessage(newMessage));
                msgIdx = lastMsgIdx;
            }

            if (mSVP_Started) {
                if (mSVP == null) {
                    Activity currentActivity = aTalkApp.getCurrentActivity();
                    if (currentActivity != null) {
                        if (currentActivity instanceof SplitStreetViewPanoramaAndMapActivity) {
                            mSVP = (SplitStreetViewPanoramaAndMapActivity) currentActivity;
                        }
                    }
                }
                if (mSVP != null) {
                    MessageDisplay msg = getMessageDisplay(msgIdx);
                    if (msg.hasLatLng())
                        mSVP.onLocationChanged(msg.mLatLng);
                }
            }

            if (update) {
                // List must be scrolled manually, when android:transcriptMode="normal" is set
                chatListAdapter.notifyDataSetChanged();
                chatListView.setSelection(msgIdx + chatListView.getHeaderViewsCount());
            }
        }

        /**
         * Inserts given <tt>Collection</tt> of <tt>ChatMessage</tt> at the beginning of the list.
         *
         * @param chatMessages the collection of <tt>ChatMessage</tt> to prepend.
         */
        public void prependMessages(Collection<ChatMessage> chatMessages)
        {
            List<MessageDisplay> newMsgs = new ArrayList<>();
            MessageDisplay previous = null;
            for (ChatMessage next : chatMessages) {
                if (previous == null || !previous.msg.isConsecutiveMessage(next)) {
                    previous = new MessageDisplay(next);
                    newMsgs.add(previous);
                }
                else {
                    // Merge the message and update the object in the list
                    previous.update(previous.msg.mergeMessage(next));
                }
            }
            messages.addAll(0, newMsgs);
        }

        /**
         * Finds index of the message that will handle <tt>newMessage</tt> merging process (usually just the last one).
         * If the <tt>newMessage</tt> is a correction message, then the last message of the same type will be returned.
         *
         * @param newMessage the next message to be merged into the adapter.
         * @return index of the message that will handle <tt>newMessage</tt> merging process. If
         * <tt>newMessage</tt> is a correction
         * message, then the last message of the same type will be returned.
         */
        private int getLastMessageIdx(ChatMessage newMessage)
        {
            // If it's not a correction message then just return the last one
            if (newMessage.getCorrectedMessageUID() == null)
                return chatListAdapter.getCount() - 1;

            // Search for the same type
            int msgType = newMessage.getMessageType();
            for (int i = (getCount() - 1); i >= 0; i--) {
                ChatMessage candidate = getMessage(i);
                if (candidate.getMessageType() == msgType) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * {@inheritDoc}
         */
        public int getCount()
        {
            return messages.size();
        }

        public void clearMessage()
        {
            messages.clear();
        }

        /**
         * {@inheritDoc}
         */
        public Object getItem(int position)
        {
            return messages.get(position);
        }

        ChatMessage getMessage(int pos)
        {
            return ((MessageDisplay) getItem(pos)).msg;
        }

        MessageDisplay getMessageDisplay(int pos)
        {
            return (MessageDisplay) getItem(pos);
        }

        List<MessageDisplay> getMessageDisplays()
        {
            return messages;
        }

        /**
         * {@inheritDoc}
         */
        public long getItemId(int pos)
        {
            return messages.get(pos).id;
        }

        public void setXferStatus(int pos, int status)
        {
            if (pos < messages.size())
                messages.get(pos).status = status;
        }

        public int getXferStatus(int pos)
        {
            return messages.get(pos).status;
        }

        public void setFileXfer(int pos, Object mFileXfer)
        {
            messages.get(pos).fileXfer = mFileXfer;
        }

        public File getFileName(int pos)
        {
            return messages.get(pos).sFile;
        }

        public void setFileName(int pos, File file)
        {
            messages.get(pos).sFile = file;
        }

        public Object getFileXfer(int pos)
        {
            return messages.get(pos).fileXfer;
        }

        public int getViewTypeCount()
        {
            return VIEW_TYPE_MAX;
        }

        public int getItemViewType(int position)
        {
            ChatMessage message = getMessage(position);
            int messageType = message.getMessageType();

            switch (messageType) {
                case ChatMessage.MESSAGE_IN:
                case ChatMessage.MESSAGE_LOCATION_IN:
                case ChatMessage.MESSAGE_MUC_IN:
                case ChatMessage.MESSAGE_STATUS:
                    return INCOMING_MESSAGE_VIEW;

                case ChatMessage.MESSAGE_OUT:
                case ChatMessage.MESSAGE_LOCATION_OUT:
                case ChatMessage.MESSAGE_MUC_OUT:
                    String sessionCorrUID = chatPanel.getCorrectionUID();
                    String msgCorrUID = message.getUidForCorrection();
                    if (sessionCorrUID != null && sessionCorrUID.equals(msgCorrUID)) {
                        return CORRECTED_MESSAGE_VIEW;
                    }
                    else {
                        return OUTGOING_MESSAGE_VIEW;
                    }

                case ChatMessage.MESSAGE_SYSTEM:
                    return SYSTEM_MESSAGE_VIEW;

                case ChatMessage.MESSAGE_ERROR:
                    return ERROR_MESSAGE_VIEW;

                case ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE:
                case ChatMessage.MESSAGE_FILE_TRANSFER_SEND:
                case ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY:
                    return FILE_TRANSFER_MESSAGE_VIEW;

                default: // Default others to INCOMING_MESSAGE_VIEW
                    return INCOMING_MESSAGE_VIEW;
            }
        }

        /**
         * Hack required to capture TextView(message body) clicks, when
         * <tt>LinkMovementMethod</tt> is set.
         */
        private final View.OnClickListener msgClickAdapter = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (chatController != null && v.getTag() instanceof Integer) {
                    Integer position = (Integer) v.getTag();
                    chatController.onItemClick(chatListView, v, position, -1 /* id not used */);
                }
            }
        };

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            int viewType = getItemViewType(position);
            int clickedPos = position + chatListView.getHeaderViewsCount();
            MessageViewHolder messageViewHolder;
            MessageDisplay message = getMessageDisplay(position);

            switch (viewType) {
                // File Transfer convertView creation
                case FILE_TRANSFER_MESSAGE_VIEW:
                    LayoutInflater inflater = getActivity().getLayoutInflater();
                    ChatMessage msg = message.getChatMessage();
                    int msgType;
                    boolean init = false;
                    View viewTemp = null;

                    // Reuse convertView if available and valid
                    if (convertView == null) {
                        messageViewHolder = new MessageViewHolder();
                        init = true;
                    }
                    else {
                        messageViewHolder = (MessageViewHolder) convertView.getTag();
                        if (messageViewHolder.viewType != viewType) {
                            messageViewHolder = new MessageViewHolder();
                            init = true;
                        }
                    }

                    msgType = message.getMessageType();
                    switch (msgType) {
                        case ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE:
                            clearMsgCache = true;
                            OperationSetFileTransfer opSet = msg.getOpSet();
                            IncomingFileTransferRequest request = msg.getFTRequest();
                            String sendFrom = msg.getContactName();
                            Date date = msg.getDate();

                            ReceiveFileConversation filexferR = (ReceiveFileConversation) getFileXfer(position);
                            if (filexferR == null) {
                                filexferR = ReceiveFileConversation.newInstance
                                        (currentChatFragment, sendFrom, opSet, request, date);
                                setFileXfer(position, filexferR);
                            }
                            viewTemp = filexferR.ReceiveFileConversionForm(inflater,
                                    messageViewHolder, parent, position, init);
                            break;

                        case ChatMessage.MESSAGE_FILE_TRANSFER_SEND:
                            clearMsgCache = true;
                            String fileName = msg.getMessage();
                            String sendTo = msg.getContactName();
                            SendFileConversation filexferS = (SendFileConversation) getFileXfer(position);
                            if (filexferS == null) {
                                filexferS = SendFileConversation.newInstance(currentChatFragment, sendTo, fileName);
                                setFileXfer(position, filexferS);
                            }
                            viewTemp = filexferS.SendFileConversationForm(inflater, messageViewHolder, parent, position, init);
                            break;

                        case ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY:
                            FileRecord fileRecord = message.getFileRecord();
                            FileHistoryConversation filexferH = FileHistoryConversation.newInstance(currentChatFragment,
                                    fileRecord, message.getChatMessage());
                            viewTemp = filexferH.FileHistoryConversationForm(inflater, messageViewHolder, parent, init);
                            break;
                    }

                    if (init) {
                        convertView = viewTemp;
                        convertView.setTag(messageViewHolder);
                    }
                    messageViewHolder.viewType = viewType;
                    // messageViewHolder.timeView.setText(message.getDateStr());
                    return convertView;

                // Normal Chat Message convertView Creation
                default:
                    if (convertView == null) {
                        messageViewHolder = new MessageViewHolder();
                        convertView = inflateViewForType(viewType, messageViewHolder, parent);
                    }
                    else {
                        // Convert between OUTGOING and CORRECTED
                        messageViewHolder = (MessageViewHolder) convertView.getTag();
                        int vType = messageViewHolder.viewType;
                        if ((vType == CORRECTED_MESSAGE_VIEW || vType == OUTGOING_MESSAGE_VIEW)
                                && (vType != viewType)) {
                            messageViewHolder = new MessageViewHolder();
                            convertView = inflateViewForType(viewType, messageViewHolder, parent);
                        }
                    }
                    // Set position used for click handling from click adapter
                    // int clickedPos = position + chatListView.getHeaderViewsCount();
                    messageViewHolder.messageView.setTag(clickedPos);
                    if (messageViewHolder.outgoingMessageHolder != null)
                        messageViewHolder.outgoingMessageHolder.setTag(clickedPos);
                    // MessageDisplay message = getMessageDisplay(position);
                    if (message != null) {
                        if (messageViewHolder.viewType == INCOMING_MESSAGE_VIEW
                                || messageViewHolder.viewType == OUTGOING_MESSAGE_VIEW
                                || messageViewHolder.viewType == CORRECTED_MESSAGE_VIEW) {

                            String jid = message.getChatMessage().getContactName();
                            if (messageViewHolder.viewType == INCOMING_MESSAGE_VIEW)
                                messageViewHolder.jidView.setText(message.getChatMessage().getContactDisplayName() + ":");
                            updateStatusAndAvatarView(messageViewHolder, jid);

                            if (messageViewHolder.showMapButton != null) {
                                if (message.hasLatLng()) {
                                    messageViewHolder.showMapButton.setVisibility(View.VISIBLE);
                                    messageViewHolder.showMapButton.setOnClickListener(message);
                                    messageViewHolder.showMapButton.setOnLongClickListener(message);
                                }
                                else {
                                    messageViewHolder.showMapButton.setVisibility(View.GONE);
                                }
                            }
                            messageViewHolder.timeView.setText(message.getDateStr());
                        }
                        messageViewHolder.messageView.setText(message.getBody());
                    }
                    return convertView;
            }
        }

        private View inflateViewForType(int viewType, MessageViewHolder messageViewHolder, ViewGroup parent)
        {
            messageViewHolder.viewType = viewType;
            LayoutInflater inflater = getActivity().getLayoutInflater();
            View convertView;

            if (viewType == INCOMING_MESSAGE_VIEW) {
                convertView = inflater.inflate(R.layout.chat_incoming_row, parent, false);
                messageViewHolder.avatarView = convertView.findViewById(R.id.incomingAvatarIcon);
                messageViewHolder.statusView = convertView.findViewById(R.id.incomingStatusIcon);
                messageViewHolder.jidView = convertView.findViewById(R.id.incomingJidView);
                messageViewHolder.messageView = convertView.findViewById(R.id.incomingMessageView);
                messageViewHolder.timeView = convertView.findViewById(R.id.incomingTimeView);
                messageViewHolder.chatStateView = convertView.findViewById(R.id.chatStateImageView);
                messageViewHolder.showMapButton = convertView.findViewById(R.id.showMapButton);
            }
            else if (viewType == OUTGOING_MESSAGE_VIEW || viewType == CORRECTED_MESSAGE_VIEW) {
                if (viewType == OUTGOING_MESSAGE_VIEW) {
                    convertView = inflater.inflate(R.layout.chat_outgoing_row, parent, false);
                }
                else {
                    convertView = inflater.inflate(R.layout.chat_corrected_row, parent, false);
                }
                messageViewHolder.avatarView = convertView.findViewById(R.id.outgoingAvatarIcon);
                messageViewHolder.statusView = convertView.findViewById(R.id.outgoingStatusIcon);
                messageViewHolder.messageView = convertView.findViewById(R.id.outgoingMessageView);
                messageViewHolder.timeView = convertView.findViewById(R.id.outgoingTimeView);
                messageViewHolder.outgoingMessageHolder = convertView.findViewById(R.id.outgoingMessageHolder);
                messageViewHolder.showMapButton = convertView.findViewById(R.id.showMapButton);
            }
            else {
                // System or error view
                convertView = inflater.inflate((viewType == SYSTEM_MESSAGE_VIEW)
                        ? R.layout.chat_system_row : R.layout.chat_error_row, parent, false);
                messageViewHolder.messageView = convertView.findViewById(R.id.messageView);
            }

            // Set link movement method
            messageViewHolder.messageView.setMovementMethod(LinkMovementMethod.getInstance());

            // Set clicks adapter for re-edit last outgoing message
            messageViewHolder.messageView.setOnClickListener(msgClickAdapter);
//			if (messageViewHolder.outgoingMessageHolder != null) {
//				messageViewHolder.outgoingMessageHolder.setOnClickListener(msgClickAdapter);
//			}
            convertView.setTag(messageViewHolder);
            return convertView;
        }

        /**
         * Updates status and avatar views on given <tt>MessageViewHolder</tt>.
         *
         * @param viewHolder the <tt>MessageViewHolder</tt> to update.
         */
        private void updateStatusAndAvatarView(MessageViewHolder viewHolder, String jabberID)
        {
            Drawable avatar = null;
            Drawable status = null;
            if (viewHolder.viewType == INCOMING_MESSAGE_VIEW) {
                Object descriptor = chatPanel.getChatSession().getDescriptor();
                if (descriptor instanceof MetaContact) {
                    MetaContact metaContact = (MetaContact) descriptor;
                    avatar = MetaContactRenderer.getAvatarDrawable(metaContact);
                    status = MetaContactRenderer.getStatusDrawable(metaContact);
                }
                else {
                    if (jabberID != null) {
                        ProtocolProviderService provider
                                = chatPanel.getChatSession().getCurrentChatTransport().getProtocolProvider();
                        OperationSetPersistentPresenceJabberImpl presenceOpSet = (OperationSetPersistentPresenceJabberImpl)
                                provider.getOperationSet(OperationSetPersistentPresence.class);
                        Contact contact = presenceOpSet.findContactByID(XmppStringUtils.parseBareJid(jabberID));
                        // If we have found a contact the we set also its avatar and status.
                        if (contact != null) {
                            avatar = MetaContactRenderer.getCachedAvatarFromBytes(contact.getImage());
                            PresenceStatus pStatus = contact.getPresenceStatus();
                            status = MetaContactRenderer.getCachedAvatarFromBytes(StatusUtil.getContactStatusIcon(pStatus));
                        }
                    }
                }
            }
            else if (viewHolder.viewType == OUTGOING_MESSAGE_VIEW
                    || viewHolder.viewType == CORRECTED_MESSAGE_VIEW) {
                ProtocolProviderService pps = currentChatTransport.getProtocolProvider();
                AndroidLoginRenderer loginRenderer = AndroidGUIActivator.getLoginRenderer();
                avatar = loginRenderer.getLocalAvatarDrawable(pps);
                status = loginRenderer.getLocalStatusDrawable();
            }
            else {
                // Avatar and status are present only in outgoing or incoming message views
                return;
            }
            setAvatar(viewHolder.avatarView, avatar);
            setStatus(viewHolder.statusView, status);
        }

        @Override
        public void messageDelivered(final MessageDeliveredEvent evt)
        {
            final Contact contact = evt.getDestinationContact();
            final MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);

            if (logger.isTraceEnabled())
                logger.trace("MESSAGE DELIVERED to contact: " + contact.getAddress());

            if ((metaContact != null) && metaContact.equals(chatPanel.getMetaContact())) {
                final ChatMessageImpl msg = ChatMessageImpl.getMsgForEvent(evt);

                if (logger.isTraceEnabled())
                    logger.trace("MESSAGE DELIVERED: process message to chat for contact: " +
                            contact.getAddress() + " MESSAGE: " + msg.getMessage());
                addMessage(msg, true);
            }
        }

        @Override
        public void messageDeliveryFailed(MessageDeliveryFailedEvent arg0)
        {
            // Do nothing, handled in ChatPanel
        }

        @Override
        public void messageReceived(final MessageReceivedEvent evt)
        {
            // ChatPanel broadcasts all received messages to all listeners. Must filter and display
            // messages only intended for this chatFragment.
            Contact protocolContact = evt.getSourceContact();
            if (mChatMetaContact.containsContact(protocolContact)) {
                final ChatMessageImpl msg = ChatMessageImpl.getMsgForEvent(evt);
                addMessage(msg, true);
            }
            else {
                if (logger.isTraceEnabled())
                    logger.trace("MetaContact not found for protocol contact: " + protocolContact + ".");
            }
        }

        // some messages are added directory without event triggered
        @Override
        public void messageAdded(ChatMessage msg)
        {
            addMessage(msg, true);
        }

        /**
         * Indicates a contact has changed its status.
         */
        @Override
        public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt)
        {
            Contact sourceContact = evt.getSourceContact();
            if (logger.isDebugEnabled())
                logger.debug("Contact presence status changed: " + sourceContact.getAddress());

            if ((chatPanel.getMetaContact() != null) && chatPanel.getMetaContact().containsContact(sourceContact)) {
                new UpdateStatusTask().execute();
            }
        }

        @Override
        public void chatStateNotificationDeliveryFailed(ChatStateNotificationEvent evt)
        {
        }

        @Override
        public void chatStateNotificationReceived(ChatStateNotificationEvent evt)
        {
            if (logger.isDebugEnabled())
                logger.debug("Typing notification received: " + evt.getSourceContact().getAddress());

            ChatStateNotificationHandler.handleChatStateNotificationReceived(evt, ChatFragment.this);
        }

        /**
         * Updates all avatar and status on outgoing messages rows.
         */
        public void localAvatarOrStatusChanged()
        {
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    for (int i = 0; i < chatListView.getChildCount(); i++) {
                        View row = chatListView.getChildAt(i);
                        MessageViewHolder viewHolder = (MessageViewHolder) row.getTag();
                        if (viewHolder != null)
                            updateStatusAndAvatarView(viewHolder, null);
                    }
                }
            });
        }

        /**
         * Class used to cache processed message contents. Prevents from re-processing on each
         * View display.
         */
        class MessageDisplay implements View.OnClickListener, View.OnLongClickListener
        {
            /**
             * Row identifier.
             */
            private final long id;

            /**
             * File Transfer Status.
             */
            private int status;

            /**
             * Incoming or outgoing File Transfer object
             */
            private Object fileXfer;

            /**
             * Save File name</tt>
             */

            private File sFile;

            /**
             * Displayed <tt>ChatMessage</tt>
             */

            private ChatMessage msg;

            /**
             * Date string cache
             */
            private String dateStr;

            /**
             * Message body cache
             */
            private Spanned body;

            /**
             * Incoming message has LatLng info
             */
            private boolean hasLatLng = false;

            /**
             * LatLng info in the incoming message
             */
            private LatLng mLatLng = null;

            /**
             * Creates new instance of <tt>MessageDisplay</tt> that will be used for displaying
             * given <tt>ChatMessage</tt>.
             *
             * @param msg the <tt>ChatMessage</tt> that will be displayed by this instance.
             */
            MessageDisplay(ChatMessage msg)
            {
                this.id = idGenerator++;
                this.status = -1; // Initial state
                this.fileXfer = null;
                this.msg = msg;
                checkLatLng();
            }

            /**
             * check if the incoming message contain geo "LatLng:" information. Only the first
             * LatLng will be returned if there are multiple LatLng in consecutive messages
             */
            private void checkLatLng()
            {
                String str = msg.getMessage();
                if ((str != null) && ((getMessageType() == ChatMessage.MESSAGE_IN)
                        || (getMessageType() == ChatMessage.MESSAGE_OUT))) {
                    int startIndex = str.indexOf("LatLng:");
                    if (startIndex != -1) {
                        try {
                            String mLoc = str.substring(startIndex + 7);
                            String[] location = mLoc.split(",");

                            String sLat = location[0].toLowerCase();
                            if (sLat.contains("s"))
                                sLat = "-" + sLat.replaceAll("[^0-9.]+", "");
                            else
                                sLat = sLat.replaceAll("[^0-9.]+", "");

                            String sLng = location[1].toLowerCase();
                            if (sLng.contains("w"))
                                sLng = "-" + sLng.replaceAll("[^0-9.]+", "");
                            else
                                sLng = sLng.replaceAll("[^0-9.]+", "");

                            mLatLng = new LatLng(Double.parseDouble(sLat),
                                    Double.parseDouble(sLng));
                            hasLatLng = true;
                        } catch (NumberFormatException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }

            /**
             * Perform google street and map view fetch when user click the show map button
             *
             * @param view
             */
            @Override
            public void onClick(View view)
            {
                // You can now create a LatLng Object for use with maps
                mSVP_Started = true;
                Intent intent = new Intent(mActivity, SplitStreetViewPanoramaAndMapActivity.class);
                intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, mLatLng);
                startActivity(intent);
            }

            @Override
            public boolean onLongClick(View v)
            {
                ArrayList<LatLng> xLatLng = new ArrayList<>();
                int smt = getMessageType();
                List<MessageDisplay> displayMessages = getMessageDisplays();
                for (MessageDisplay dm : displayMessages) {
                    if (dm.hasLatLng() && (smt == dm.getMessageType())) {
                        xLatLng.add(dm.mLatLng);
                    }
                }
                if (!xLatLng.isEmpty()) {
                    mSVP_Started = true;
                    Intent intent = new Intent(mActivity, SplitStreetViewPanoramaAndMapActivity.class);
                    intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, mLatLng);
                    intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_LIST, xLatLng);
                    startActivity(intent);
                }
                return true;
            }

            /**
             * @return <tt>true</tt> if the message has LatLng information
             */
            public boolean hasLatLng()
            {
                return hasLatLng;
            }

            /**
             * Returns formatted date string for the <tt>ChatMessage</tt>.
             *
             * @return formatted date string for the <tt>ChatMessage</tt>.
             */
            public String getDateStr()
            {
                if (dateStr == null) {
                    Date date = msg.getDate();
                    dateStr = GuiUtils.formatDate(date) + " " + GuiUtils.formatTime(date);
                }
                return dateStr;
            }

            public int getMessageType()
            {
                return msg.getMessageType();
            }

            public ChatMessage getChatMessage()
            {
                return this.msg;
            }

            public FileRecord getFileRecord()
            {
                return msg.getFileRecord();
            }

            /**
             * Returns <tt>Spanned</tt> message body processed for HTML tags.
             *
             * @return <tt>Spanned</tt> message body.
             */
            public Spanned getBody()
            {
                if (body == null) {
                    try { // cannot assume null body = html message
                        body = Html.fromHtml(msg.getMessage(), imageGetter, null);
                        final int msgType = msg.getMessageType();
                        if ((msgType == ChatMessage.MESSAGE_OUT)
                                || (msgType == ChatMessage.MESSAGE_IN)) {
                            Linkify.addLinks((Spannable) body, Linkify.ALL);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return body;
            }

            /**
             * Updates this display instance with new message causing display contents to be
             * invalidated.
             *
             * @param chatMessage new message content
             */
            public void update(ChatMessage chatMessage)
            {
                dateStr = null;
                body = null;
                msg = chatMessage;
            }
        }
    }

    public static class MessageViewHolder
    {
        ImageView avatarView;
        ImageView statusView;
        TextView jidView;
        TextView messageView;
        public TextView timeView;
        ImageView chatStateView;
        int viewType;
        View outgoingMessageHolder;

        TextView errorView;
        TextView msgTitleView;

        public ImageView arrowDir = null;
        public ImageButton imageLabel = null;
        public ProgressBar mProgressBar = null;

        public Button showMapButton = null;
        public Button cancelButton = null;
        public Button retryButton = null;
        public Button acceptButton = null;
        public Button rejectButton = null;
        public Button openFileButton = null;
        public Button openFolderButton = null;

        public TextView titleLabel = null;
        public TextView fileLabel = null;
        public TextView viewFileXferMessage = null;
        public TextView viewFileXferError = null;
        public TextView progressSpeedLabel = null;
        public TextView estimatedTimeLabel = null;
    }

    class IdRow2 // need to include in MessageViewHolder for stealth support
    {
        public int mId;
        public View mRow;
        public int mCountDownValue;
        public boolean deleteFlag;
        public boolean mStartCountDown;
        public boolean mFileIsOpened;

        public IdRow2(int id, View row, int startValue)
        {
            mId = id;
            mRow = row;
            mCountDownValue = startValue;
            deleteFlag = false;
            mStartCountDown = false;
            mFileIsOpened = false;
        }
    }

    /**
     * Loads the history in an asynchronous thread and then adds the history messages to the user
     * interface.
     */
    private class LoadHistoryTask extends AsyncTask<Void, Void, Collection<ChatMessage>>
    {
        /**
         * Indicates that history is being loaded for the first time.
         */
        private final boolean init;
        /**
         * Remembers adapter size before new messages were added.
         */
        private int preSize;

        LoadHistoryTask(boolean init)
        {
            this.init = init;
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            header.setVisibility(View.VISIBLE);
            this.preSize = chatListAdapter.getCount();
        }

        @Override
        protected Collection<ChatMessage> doInBackground(Void... params)
        {
            return chatPanel.getHistory(init);
        }

        @Override
        protected void onPostExecute(Collection<ChatMessage> result)
        {
            super.onPostExecute(result);
            chatListAdapter.prependMessages(result);

            header.setVisibility(View.GONE);
            chatListAdapter.notifyDataSetChanged();
            int loaded = chatListAdapter.getCount() - preSize;
            int scrollTo = loaded + scrollFirstVisible;
            chatListView.setSelectionFromTop(scrollTo, scrollTopOffset);
            loadHistoryTask = null;
        }
    }

    /**
     * Updates the status user interface.
     */
    private class UpdateStatusTask extends AsyncTask<Void, Void, Void>
    {
        protected Void doInBackground(Void... params)
        {
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            if (chatListView == null || chatPanel == null) {
                return;
            }

            for (int i = 0; i < chatListView.getChildCount(); i++) {
                View chatRowView = chatListView.getChildAt(i);
                MessageViewHolder viewHolder = (MessageViewHolder) chatRowView.getTag();

                if ((viewHolder != null)
                        && (viewHolder.viewType == ChatListAdapter.INCOMING_MESSAGE_VIEW)) {
                    Drawable status = MetaContactRenderer.getStatusDrawable(chatPanel.getMetaContact());
                    ImageView statusView = viewHolder.statusView;
                    setStatus(statusView, status);
                }
            }
        }
    }

    /**
     * Sets the avatar icon for the given avatar view.
     *
     * @param avatarView the avatar image view
     * @param avatarDrawable the avatar drawable to set
     */
    public void setAvatar(ImageView avatarView, Drawable avatarDrawable)
    {
        if (avatarDrawable == null) {
            // avatarDrawable = aTalkApp.getAppResources().getDrawable(R.drawable.avatar);
            avatarDrawable = ContextCompat.getDrawable(aTalkApp.getGlobalContext(), R.drawable.avatar);
        }
        if (avatarView != null) {
            avatarView.setImageDrawable(avatarDrawable);
        }
    }

    /**
     * Sets the status of the given view.
     *
     * @param statusView the status icon view
     * @param statusDrawable the status drawable
     */
    public void setStatus(ImageView statusView, Drawable statusDrawable)
    {
        // File Transfer messageHolder does not have a statusView for update;
        // cmeng - server shut down causing null pointer, why???
        if (statusView != null)
            statusView.setImageDrawable(statusDrawable);
    }

    /**
     * Sets the appropriate chat state notification interface.
     *
     * @param chatState the chat state that should be represented in the view
     */
    public void setChatState(final ChatState chatState)
    {
        if (chatStateView == null) {
            return;
        }

        mActivity.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (chatState != null) {
                    TextView chatStateTextView = chatStateView.findViewById(R.id.chatStateTextView);
                    ImageView chatStateImgView = chatStateView.findViewById(R.id.chatStateImageView);

                    String buddy = chatPanel.getShortDisplayName();
                    switch (chatState) {
                        case composing:
                            Drawable chatStateDrawable = chatStateImgView.getDrawable();
                            if (!(chatStateDrawable instanceof AnimationDrawable)) {
                                chatStateImgView.setImageResource(R.drawable.chat_state_drawable);
                                chatStateDrawable = chatStateImgView.getDrawable();
                            }

                            if (!((AnimationDrawable) chatStateDrawable).isRunning()) {
                                AnimationDrawable animatedDrawable = (AnimationDrawable) chatStateDrawable;
                                animatedDrawable.setOneShot(false);
                                animatedDrawable.start();
                            }
                            chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_COMPOSING, buddy));
                            break;
                        case paused:
                            chatStateImgView.setImageResource(R.drawable.typing1);
                            chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_PAUSED_TYPING, buddy));
                            break;
                        case active:
                            chatStateImgView.setImageResource(R.drawable.global_ffc);
                            chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_ACTIVE, buddy));
                            break;
                        case inactive:
                            chatStateImgView.setImageResource(R.drawable.global_away);
                            chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_INACTIVE, buddy));
                            break;
                        case gone:
                            chatStateImgView.setImageResource(R.drawable.global_extended_away);
                            chatStateTextView.setText(aTalkApp.getResString(R.string.service_gui_CONTACT_GONE, buddy));
                            break;
                    }
                    chatStateImgView.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    chatStateImgView.setPadding(7, 0, 7, 7);
                    chatStateView.setVisibility(View.VISIBLE);
                }
                else {
                    chatStateView.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    // ********************************************************************************************//
    /** Routines supporting File Transfer **/

    /**
     * Returns <code>true</code> if there are active file transfers, otherwise returns
     * <code>false</code>.
     *
     * @return <code>true</code> if there are active file transfers, otherwise returns
     * <code>false</code>
     */
    public boolean containsActiveFileTransfers()
    {
        return !activeFileTransfers.isEmpty();
    }

    /**
     * Cancels all active file transfers.
     */
    public void cancelActiveFileTransfers()
    {
        Enumeration<String> activeKeys = activeFileTransfers.keys();

        while (activeKeys.hasMoreElements()) {
            // catch all so if anything happens we still will close the chatFragment / chatPanel
            try {
                String key = activeKeys.nextElement();
                Object descriptor = activeFileTransfers.get(key);

                if (descriptor instanceof IncomingFileTransferRequest) {
                    ((IncomingFileTransferRequest) descriptor).rejectFile();
                }
                else if (descriptor instanceof FileTransfer) {
                    ((FileTransfer) descriptor).cancel();
                    descriptor = null;
                }
            } catch (Throwable t) {
                logger.error("Cannot initActive file transfer.", t);
            }
        }
    }

    /**
     * Adds the given file transfer <tt>id</tt> to the list of active file transfers.
     *
     * @param id the identifier of the file transfer to add
     * @param fileTransfer the descriptor of the file transfer
     */
    public void addActiveFileTransfer(String id, FileTransfer fileTransfer, int msgId)
    {
        synchronized (activeFileTransfers) {
            // Both must be removed from chatFragment activeFileTransfers components when 'Done!'.
            if (!activeFileTransfers.contains(id)) {
                activeFileTransfers.put(id, fileTransfer);

                // Add status listener for chatFragment to track when the sendFile transfer has
                // completed.
                fileTransfer.addStatusListener(currentChatFragment);
            }
        }

        synchronized (activeMsgTransfers) {
            if (!activeMsgTransfers.contains(id))
                activeMsgTransfers.put(id, msgId);
        }
    }

    /**
     * Removes the given file transfer <tt>id</tt> from the list of active file transfers.
     *
     * @param fileTransfer the identifier of the file transfer to remove
     */
    public void removeActiveFileTransfer(FileTransfer fileTransfer)
    {
        String id = fileTransfer.getID();
        synchronized (activeFileTransfers) {
            activeFileTransfers.remove(id);

            // if (activeFileTransfers.size() == 0) ?? is one per file transfer, so must remove
            fileTransfer.removeStatusListener(currentChatFragment);
            fileTransfer = null;
        }

        synchronized (activeMsgTransfers) {
            int msgId = activeMsgTransfers.get(id);
            chatListAdapter.setFileXfer(msgId, null);
            activeMsgTransfers.remove(id);
        }
    }

    /**
     * Handles file transfer status changed in order to remove completed file transfers from the
     * list of active transfers.
     *
     * @param event the file transfer status change event the notified us for the change
     */
    public void statusChanged(FileTransferStatusChangeEvent event)
    {
        FileTransfer fileTransfer = event.getFileTransfer();
        final int newStatus = event.getNewStatus();

        int msgPos = activeMsgTransfers.get(fileTransfer.getID());
        // if chatFragment is still active
        if (chatListAdapter != null) {
            // Send a initActive message to recipient if file transfer is initActive while in
            // preparing
            // state. Currently protocol did not broadcast status change under this condition.
            if ((newStatus == FileTransferStatusChangeEvent.CANCELED)
                    && (chatListAdapter.getXferStatus(msgPos) == FileTransferStatusChangeEvent.PREPARING)) {
                String msg = AndroidGUIActivator.getResources().getI18NString("service.gui.FILE_TRANSFER_CANCELED");
                // chatPanel.sendMessage(msg);
                try {
                    chatPanel.getChatSession().getCurrentChatTransport().sendInstantMessage(msg, ChatMessage.ENCODE_PLAIN);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            chatListAdapter.setXferStatus(msgPos, newStatus);
            if (newStatus == FileTransferStatusChangeEvent.COMPLETED
                    || newStatus == FileTransferStatusChangeEvent.CANCELED
                    || newStatus == FileTransferStatusChangeEvent.FAILED
                    || newStatus == FileTransferStatusChangeEvent.REFUSED) {
                removeActiveFileTransfer(fileTransfer);
            }
        }
    }

    /**
     * Sends the given file through the currently selected chat transport by using the given
     * fileComponent to visualize the transfer process
     * in the chatFragment view.
     * <p>
     * // @param mFile
     * the file to send
     * // @param SendFileConversation
     * the send file component to use for file transfer & visualization
     * // @param msgId
     * the view position on chatFragment
     */

    public class SendFile extends AsyncTask<Void, Void, Exception>
    {
        private final File file;
        private final SendFileConversation sendFTConversion;
        private final int msgId;
        private boolean chkMaxSize = true;

        public SendFile(File mFile, SendFileConversation sFilexferCon, int mId)
        {
            file = mFile;
            sendFTConversion = sFilexferCon;
            msgId = mId;
        }

        @Override
        public void onPreExecute()
        {
            long maxFileLength = currentChatTransport.getMaximumFileLength();
            if (file.length() > maxFileLength) {
                chatPanel.addMessage(currentChatTransport.getDisplayName(), new Date(), Chat.ERROR_MESSAGE,
                        AndroidGUIActivator.getResources().getI18NString("service.gui.FILE_TOO_BIG",
                                new String[]{ByteFormat.format(maxFileLength)}),
                        ChatMessage.ENCODE_PLAIN);

                // stop background task to proceed and update status
                chkMaxSize = false;
                chatListAdapter.setXferStatus(msgId, FileTransferStatusChangeEvent.CANCELED);
                sendFTConversion.setFailed();
            }
            else {
                // must reset status here as background task cannot catch up with Android redraw
                // request? causing double send requests in slow Android devices.
                chatListAdapter.setXferStatus(msgId, FileTransferStatusChangeEvent.PREPARING);
            }
        }

        @Override
        protected Exception doInBackground(Void... param)
        {
            if (!chkMaxSize)
                return null;

            FileTransfer fileTransfer;
            Exception result = null;
            try {
                fileTransfer = currentChatTransport.sendFile(file);

                // To be removed on file transfer completion
                addActiveFileTransfer(fileTransfer.getID(), fileTransfer, msgId);

                // Trigger SendFileConversation to add statusListener as well
                sendFTConversion.setProtocolFileTransfer(fileTransfer);
            } catch (Exception e) {
                result = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Exception ex)
        {
            if (ex != null) {
                logger.error("Failed to send file.", ex);

                if (ex instanceof IllegalStateException) {
                    chatPanel.addMessage(currentChatTransport.getDisplayName(), new Date(), Chat.ERROR_MESSAGE,
                            AndroidGUIActivator.getResources().getI18NString("service.gui.MSG_SEND_CONNECTION_PROBLEM"),
                            ChatMessage.ENCODE_PLAIN);
                }
                else {
                    chatPanel.addMessage(currentChatTransport.getDisplayName(), new Date(), Chat.ERROR_MESSAGE,
                            AndroidGUIActivator.getResources().getI18NString("service.gui.FILE_DELIVERY_ERROR",
                                    new String[]{ex.getMessage()}), ChatMessage.ENCODE_PLAIN);
                }
            }
        }

        @Override
        protected void onCancelled()
        {
        }
    }

    @Override
    public void onCurrentChatChanged(String chatId)
    {
        chatPanel = ChatSessionManager.getActiveChat(chatId);
    }

    /*********************************************************************************************
     * Routines supporting changing chatFragment background color based on chat state and chatType
     * ChatFragment background colour is being updated when:
     * - User launches a chatSession
     * - User scroll the chatFragment pages
     * - User changes cryptoMode for the current chatSession
     * - Change requests from OTR Listener due to OTR state changes
     */

    @Override
    public void onCryptoModeChange(int chatType)
    {
        changeBackground(mCFView, chatType);
    }

    /**
     * Change chatFragment background in response to initial chat session launch or event
     * triggered from omemoAuthentication and OTR mode changes in cryptoChatFragment
     *
     * @param chatType Change chat fragment view background color based on chatType
     */
    private void changeBackground(final View focusView, final int chatType)
    {
//		logger.info("## Background color Changes: " + mChatType + "==>" + chatType
//				+ " ChatPanel for: " + currentChatTransport.getDisplayName());

        if (mChatType == chatType)
            return;

        mChatType = chatType;
        chatPanel.setChatType(mChatType);

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                switch (chatType) {
                    case MSGTYPE_OMEMO:
                        focusView.setBackgroundResource(R.color.chat_background_omemo);
                        break;
                    case MSGTYPE_OMEMO_UA:
                    case MSGTYPE_OMEMO_UT:
                        focusView.setBackgroundResource(R.color.chat_background_omemo_ua);
                        break;
                    case MSGTYPE_OTR:
                        focusView.setBackgroundResource(R.color.chat_background_otr);
                        break;
                    case MSGTYPE_OTR_UA:
                        focusView.setBackgroundResource(R.color.chat_background_otr_ua);
                        break;
                    case MSGTYPE_NORMAL:
                        focusView.setBackgroundResource(R.color.chat_background_normal);
                        break;
                    case MSGTYPE_MUC_NORMAL:
                        focusView.setBackgroundResource(R.color.chat_background_muc);
                        break;
                    default:
                        focusView.setBackgroundResource(R.color.chat_background_normal);
                }
            }
        });
    }
}
