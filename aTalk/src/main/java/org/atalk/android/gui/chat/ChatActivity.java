/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.annotation.SuppressLint;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.Toast;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chat.conference.ChatInviteDialog;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.chatroomslist.ChatRoomInfoChangeDialog;
import org.atalk.android.gui.chatroomslist.ChatRoomInfoDialog;
import org.atalk.android.gui.contactlist.model.MetaContactRenderer;
import org.atalk.android.gui.dialogs.AttachOptionDialog;
import org.atalk.android.gui.dialogs.AttachOptionItem;
import org.atalk.android.gui.util.*;
import org.atalk.android.gui.util.EntityListHelper.TaskCompleted;
import org.atalk.android.plugin.audioservice.AudioBgService;
import org.atalk.android.plugin.geolocation.GeoLocation;
import org.atalk.crypto.CryptoFragment;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.StringUtils;
import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;

import java.io.File;
import java.text.DateFormat;
import java.util.*;

import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import timber.log.Timber;

import static org.atalk.persistance.FileBackend.getMimeType;

/**
 * The <tt>ChatActivity</tt> is a singleTask activity containing chat related interface.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatActivity extends OSGiActivity implements OnPageChangeListener, TaskCompleted
{
    public final static String CRYPTO_FRAGMENT = "crypto_fragment";
    /**
     * The pager widget, which handles animation and allows swiping horizontally to access
     * previous and next wizard steps.
     */
    private ViewPager chatPager;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private ChatPagerAdapter chatPagerAdapter;

    /**
     * Set the number of pages that should be retained to either side of the current page in the
     * view hierarchy in an idle state. Pages beyond this limit will be recreated from the adapter when needed.
     * Note: this is not the max fragments that user is allowed to have
     */
    private final static int CHAT_PAGER_SIZE = 4;

    /**
     * Caches last index to prevent from propagating too many events.
     */
    private int lastSelectedIdx = -1;

    /**
     * ChatActivity menu & menuItem
     */
    private Menu mMenu;
    private MenuItem mHistoryErase;
    private MenuItem mCallAudioContact;
    private MenuItem mCallVideoContact;
    private MenuItem mSendFile;
    private MenuItem mSendLocation;
    private MenuItem mRoomInvite;
    private MenuItem mLeaveChatRoom;
    private MenuItem mDestroyChatRoom;
    private MenuItem mChatRoomInfo;
    private MenuItem mChatRoomMember;
    private MenuItem mChatRoomSubject;
    private MenuItem mOtr_Session;

    /**
     * Holds chatId that is currently handled by this Activity.
     */
    private String currentChatId;
    // Current chatMode see ChatSessionManager ChatMode variables
    private int currentChatMode;
    // currently not implemented
    private int mCurrentChatType;

    private ChatPanel selectedChatPanel;
    private static Contact mRecipient;

    private static File mCameraFilePath = null;
    final private List<Uri> mPendingImageUris = new ArrayList<>();

    private static final int SELECT_PHOTO = 100;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 101;
    private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 102;
    private static final int SELECT_VIDEO = 103;
    private static final int CHOOSE_FILE_ACTIVITY_REQUEST_CODE = 104;
    private static final int OPEN_FILE_REQUEST_CODE = 105;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Use SOFT_INPUT_ADJUST_PAN mode only in horizontal orientation, which doesn't provide
        // enough space to write messages comfortably. Adjust pan is causing copy-paste options
        // not being displayed as well as the action bar which contains few useful options.
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        // If chat notification has been clicked and OSGi service has been killed in the meantime
        // then we have to start it and restore this activity
        if (postRestoreIntent()) {
            return;
        }
        // Add fragment for crypto padLock for OTR and OMEMO before start pager
        getSupportFragmentManager().beginTransaction().add(new CryptoFragment(), CRYPTO_FRAGMENT).commit();

        // Instantiate a ViewPager and a PagerAdapter.
        chatPager = findViewById(R.id.chatPager);
        chatPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager(), this);
        chatPager.setAdapter(chatPagerAdapter);
        chatPager.setOffscreenPageLimit(CHAT_PAGER_SIZE);
        chatPager.addOnPageChangeListener(this);

        handleIntent(getIntent(), savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        handleIntent(intent, null);
    }

    private void handleIntent(Intent intent, Bundle savedInstanceState)
    {
        String chatId;

        // resume chat using previous setup conditions
        if (savedInstanceState != null) {
            chatId = savedInstanceState.getString(ChatSessionManager.CHAT_IDENTIFIER);
            currentChatMode = savedInstanceState.getInt(ChatSessionManager.CHAT_MODE);
            mCurrentChatType = savedInstanceState.getInt(ChatSessionManager.CHAT_MSGTYPE);
        }
        // else start chat in metaContact chat with OMEMO encryption
        else {
            chatId = intent.getStringExtra(ChatSessionManager.CHAT_IDENTIFIER);
            currentChatMode = intent.getIntExtra(ChatSessionManager.CHAT_MODE, ChatSessionManager.MC_CHAT);
            mCurrentChatType = intent.getIntExtra(ChatSessionManager.CHAT_MSGTYPE, ChatFragment.MSGTYPE_OMEMO);
        }
        if (chatId == null)
            throw new RuntimeException("Missing chat identifier extra");

        ChatPanel chatPanel = ChatSessionManager.createChatForChatId(chatId, currentChatMode);
        if (chatPanel == null) {
            Timber.e("Failed to create chat session for %s: %s", currentChatMode, chatId);
            return;
        }
        // Synchronize ChatActivity & ChatPager
        // setCurrentChatId(chatPanel.getChatSession().getChatId());
        setCurrentChatId(chatId);
        chatPager.setCurrentItem(chatPagerAdapter.getChatIdx(currentChatId));
    }

    /**
     * Called when the fragment is visible to the user and actively running. This is generally
     * tied to {@link android.app.Activity#onResume() Activity.onResume} of the containing Activity's lifecycle.
     *
     * Set lastSelectedIdx = -1 so {@link #updateSelectedChatInfo(int)} is always executed on onResume
     */
    @Override
    public void onResume()
    {
        super.onResume();
        if (currentChatId != null) {
            lastSelectedIdx = -1; // always force to update on resume
            updateSelectedChatInfo(chatPager.getCurrentItem());
        }
        else {
            Timber.w("ChatId can't be null - finishing & exist ChatActivity");
            finish();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause()
    {
        ChatSessionManager.setCurrentChatId(null);
        super.onPause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putString(ChatSessionManager.CHAT_IDENTIFIER, currentChatId);
        outState.putInt(ChatSessionManager.CHAT_MODE, currentChatMode);
        outState.putInt(ChatSessionManager.CHAT_MSGTYPE, mCurrentChatType);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        if (chatPagerAdapter != null)
            chatPagerAdapter.dispose();

        // Clear last chat intent
        AndroidUtils.clearGeneralNotification(aTalkApp.getGlobalContext());
    }

    /**
     * Set current chat id handled for this instance.
     *
     * @param chatId the id of the chat to set.
     */
    private void setCurrentChatId(String chatId)
    {
        currentChatId = chatId;
        ChatSessionManager.setCurrentChatId(chatId);

        selectedChatPanel = ChatSessionManager.getActiveChat(chatId);
        // field feeback = can have null?
        if (selectedChatPanel == null)
            return;

        ChatSession chatSession = selectedChatPanel.getChatSession();
        if (chatSession instanceof MetaContactChatSession) {
            mRecipient = selectedChatPanel.getMetaContact().getDefaultContact();
        }

        // Leave last chat intent by updating general notification
        AndroidUtils.clearGeneralNotification(aTalkApp.getGlobalContext());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // Close the activity when back button is pressed
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        this.mMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_menu, menu);

        mCallAudioContact = mMenu.findItem(R.id.call_contact_audio);
        mCallVideoContact = mMenu.findItem(R.id.call_contact_video);
        mSendFile = mMenu.findItem(R.id.send_file);
        mSendLocation = mMenu.findItem(R.id.send_location);
        mHistoryErase = mMenu.findItem(R.id.erase_chat_history);
        mRoomInvite = mMenu.findItem(R.id.muc_invite);
        mLeaveChatRoom = mMenu.findItem(R.id.leave_chat_room);
        mDestroyChatRoom = mMenu.findItem(R.id.destroy_chat_room);
        mChatRoomInfo = mMenu.findItem(R.id.chatroom_info);
        mChatRoomMember = mMenu.findItem(R.id.show_chatroom_occupant);
        mChatRoomSubject = mMenu.findItem(R.id.change_chatroom_attr);
        mOtr_Session = menu.findItem(R.id.otr_session);

        if (BuildConfig.FLAVOR.equals("fdroid") && (mSendLocation != null)) {
            menu.removeItem(R.id.send_location);
        }

        setOptionItem();
        return true;
    }

    // Enable option items only applicable to the specific chatSession
    private void setOptionItem()
    {
        if ((mMenu != null) && (selectedChatPanel != null)) {
            boolean hasUploadService = false;
            ChatSession chatSession = selectedChatPanel.getChatSession();
            XMPPConnection connection = selectedChatPanel.getProtocolProvider().getConnection();
            if (connection != null) {
                HttpFileUploadManager httpFileUploadManager = HttpFileUploadManager.getInstanceFor(connection);
                hasUploadService = httpFileUploadManager.isUploadServiceDiscovered();
            }

            // Enable/disable certain menu items based on current transport type
            boolean contactSession = (chatSession instanceof MetaContactChatSession);
            if (contactSession) {
                mLeaveChatRoom.setVisible(false);
                mDestroyChatRoom.setVisible(false);
                mHistoryErase.setTitle(R.string.service_gui_HISTORY_ERASE_PER_CONTACT);
                boolean isDomainJid = mRecipient.getJid() instanceof DomainBareJid;

                // check if to show call buttons.
                Object metaContact = chatSession.getDescriptor();
                MetaContactRenderer contactRenderer = new MetaContactRenderer();

                boolean isShowCall = contactRenderer.isShowCallBtn(metaContact);
                boolean isShowVideoCall = contactRenderer.isShowVideoCallBtn(metaContact);
                mCallAudioContact.setVisible(isShowCall);
                mCallVideoContact.setVisible(isShowVideoCall);

                boolean isShowFileSend = !isDomainJid
                        && (contactRenderer.isShowFileSendBtn(metaContact) || hasUploadService);
                mSendFile.setVisible(isShowFileSend);
                mSendLocation.setVisible(!isDomainJid);
                mRoomInvite.setVisible(!isDomainJid);
                mChatRoomInfo.setVisible(false);
                mChatRoomMember.setVisible(false);
                mChatRoomSubject.setVisible(false);
                // Also let CryptoFragment handles this to take care Omemo and OTR
                mOtr_Session.setVisible(!isDomainJid);
            }
            else {
                mLeaveChatRoom.setVisible(true);
                mDestroyChatRoom.setVisible(true);
                mHistoryErase.setTitle(R.string.service_gui_CHATROOM_HISTORY_ERASE_PER);
                mSendFile.setVisible(hasUploadService);
                mSendLocation.setVisible(false);
                mChatRoomInfo.setVisible(true);
                mChatRoomMember.setVisible(true);
                mChatRoomSubject.setVisible(true);
                mCallAudioContact.setVisible(false);
                mCallVideoContact.setVisible(false);
                mOtr_Session.setVisible(false);
            }

            MenuItem mPadlock = mMenu.findItem(R.id.otr_padlock);
            if (mPadlock != null) {
                mPadlock.setVisible(contactSession);
            }
        }
    }

    /**
     * Invoked when an options item has been selected.
     *
     * @param item the item that has been selected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // NPE from field
        if ((selectedChatPanel == null) || (selectedChatPanel.getChatSession() == null))
            return super.onOptionsItemSelected(item);

        Object object = selectedChatPanel.getChatSession().getDescriptor();

        if (object instanceof ChatRoomWrapper) {
            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
            ChatRoom chatRoom = chatRoomWrapper.getChatRoom();

            switch (item.getItemId()) {
                case R.id.muc_invite:
                    ChatInviteDialog inviteDialog = new ChatInviteDialog(this, selectedChatPanel);
                    inviteDialog.show();
                    return true;

                case R.id.leave_chat_room:
                    if (chatRoom != null) {
                        ChatRoomWrapper leavedRoomWrapped = MUCActivator.getMUCService().leaveChatRoom(chatRoomWrapper);
                        if (leavedRoomWrapped != null) {
                            MUCActivator.getUIService().closeChatRoomWindow(leavedRoomWrapped);
                        }
                    }
                    ChatSessionManager.removeActiveChat(selectedChatPanel);
                    MUCActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
                    MUCActivator.getMUCService().removeChatRoom(chatRoomWrapper);
                    finish();
                    return true;

                case R.id.destroy_chat_room:
                    EntityListHelper.removeEntity(chatRoomWrapper, selectedChatPanel);
                    // It is safer to just finish. see case R.id.close_chat:
                    finish();
                    return true;

                case R.id.chatroom_info:
                    ChatRoomInfoDialog chatRoomInfoDialog = ChatRoomInfoDialog.newInstance(chatRoomWrapper);
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    ft.addToBackStack(null);
                    chatRoomInfoDialog.show(ft, "infoDialog");
                    return true;

                case R.id.change_chatroom_attr:
                    ChatRoomInfoChangeDialog chatRoomInfoChangeDialog = new ChatRoomInfoChangeDialog(this, chatRoomWrapper);
                    chatRoomInfoChangeDialog.show();
                    return true;

                case R.id.show_chatroom_occupant:
                    StringBuilder memberList = new StringBuilder();
                    List<ChatRoomMember> occupants = chatRoomWrapper.getChatRoom().getMembers();
                    for (ChatRoomMember member : occupants) {
                        ChatRoomMemberJabberImpl occupant = (ChatRoomMemberJabberImpl) member;
                        memberList.append(occupant.getNickName())
                                .append(" - ")
                                .append(occupant.getJabberID())
                                .append(" (" + member.getRole().getRoleName() + ")")
                                .append("<br/>");
                    }
                    String user = chatRoomWrapper.getParentProvider().getProtocolProvider().getAccountID().getUserID();
                    selectedChatPanel.addMessage(user, new Date(), ChatMessage.MESSAGE_SYSTEM, Message.ENCODE_HTML,
                            memberList.toString());
                    return true;

                case R.id.send_file:
                    // Note: mRecipient is not used
                    AttachOptionDialog attachOptionDialog = new AttachOptionDialog(this, mRecipient);
                    attachOptionDialog.show();
                    return true;
            }
        }
        else {
            Intent intent;

            // Handle item selection
            switch (item.getItemId()) {
                case R.id.muc_invite:
                    ChatInviteDialog inviteDialog = new ChatInviteDialog(this, selectedChatPanel);
                    inviteDialog.show();
                    return true;

                case R.id.call_contact_audio: // start audio call
                    if (mRecipient != null) {
                        Jid jid = mRecipient.getJid();
                        if (jid instanceof DomainBareJid) {
                            TelephonyFragment extPhone = TelephonyFragment.newInstance(jid.toString());
                            getSupportFragmentManager().beginTransaction()
                                    .replace(android.R.id.content, extPhone).commit();
                        }
                        else
                            AndroidCallUtil.createCall(this, mRecipient.getAddress(),
                                    mRecipient.getProtocolProvider(), false);
                    }
                    return true;

                case R.id.call_contact_video: // start video call
                    if (mRecipient != null)
                        AndroidCallUtil.createCall(this, mRecipient.getAddress(),
                                mRecipient.getProtocolProvider(), true);
                    return true;

                case R.id.send_location:
                    if (!BuildConfig.FLAVOR.equals("fdroid")) {
                        intent = new Intent(this, GeoLocation.class);
                        intent.putExtra(GeoLocation.SEND_LOCATION, true);
                        startActivity(intent);
                    }
                    return true;

                case R.id.erase_chat_history:
                    EntityListHelper.eraseEntityChatHistory(ChatActivity.this,
                            selectedChatPanel.getChatSession().getDescriptor(), null, null);
                    return true;

                case R.id.send_file:
                    AttachOptionDialog attachOptionDialog = new AttachOptionDialog(this, mRecipient);
                    attachOptionDialog.show();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTaskComplete(Integer result)
    {
        if (result == EntityListHelper.CURRENT_ENTITY) {
            chatPagerAdapter.getCurrentChatFragment().onClearCurrentEntityChatHistory();
        }
        else if (result == EntityListHelper.ALL_ENTITIES) {
            onOptionsItemSelected(this.mMenu.findItem(R.id.close_all_chatrooms));
            // selectedSession.msgListeners.notifyDataSetChanged(); // all registered contact chart
        }
        else {
            showToastMessage(R.string.service_gui_HISTORY_REMOVE_ERROR);
        }
    }

    @Override
    public void onPageScrollStateChanged(int state)
    {
    }

    /**
     * Indicates a page has been scrolled. Sets the current chat.
     *
     * @param pos the new selected position
     * @param posOffset the offset of the newly selected position
     * @param posOffsetPixels the offset of the newly selected position in pixels
     */
    @Override
    public void onPageScrolled(int pos, float posOffset, int posOffsetPixels)
    {
    }

    @Override
    public void onPageSelected(int pos)
    {
        updateSelectedChatInfo(pos);
    }

    /**
     * Update the selected chat fragment actionBar info when user changes chat session.
     */
    private void updateSelectedChatInfo(int newIdx)
    {
        // Updates only when newIdx value changes, as there are too many notifications fired when
        // the page is scrolled
        if (lastSelectedIdx != newIdx) {
            lastSelectedIdx = newIdx;
            setCurrentChatId(chatPagerAdapter.getChatId(newIdx));
            setOptionItem();

            ChatSession chatSession = null;
            ChatPanel chatPanel = ChatSessionManager.getCurrentChatPanel();
            if (chatPanel != null) {
                chatSession = chatPanel.getChatSession();
            }

            if ((chatSession == null) || (chatSession.getCurrentChatTransport() == null)) {
                Timber.e("Cannot continue without the default chatSession");
                return;
            }

            ActionBarUtil.setTitle(this, chatSession.getCurrentChatTransport().getDisplayName());
            if (chatSession instanceof MetaContactChatSession) {
                ActionBarUtil.setAvatar(this, chatSession.getChatAvatar());

                PresenceStatus status = chatSession.getCurrentChatTransport().getStatus();
                if (status != null) {
                    ActionBarUtil.setStatus(this, status.getStatusIcon());

                    String presenceStatus = status.getStatusName();
                    if (!status.isOnline()) {
                        String lastSeen = getLastSeen();
                        if (lastSeen != null)
                            presenceStatus = lastSeen;
                    }
                    ActionBarUtil.setSubtitle(this, presenceStatus);
                }
            }
            else if (chatSession instanceof ConferenceChatSession) {
                ConferenceChatSession ccSession = (ConferenceChatSession) chatSession;
                ActionBarUtil.setAvatar(this, AndroidImageUtil.convertToBytes(BitmapFactory
                        .decodeResource(getResources(), R.drawable.ic_chatroom), 100));
                ActionBarUtil.setStatus(this, ccSession.getChatStatusIcon());
                ActionBarUtil.setSubtitle(this, ccSession.getChatSubject());
            }
        }
    }

    /**
     * Convert to string of the lastSeen elapsed Time
     *
     * @return lastSeen string for display; null if exception
     */
    public String getLastSeen()
    {
        // cmeng: this happen if the contact remove presence subscription while still in chat session
        if (mRecipient == null)
            return null;

        XMPPConnection connection = mRecipient.getProtocolProvider().getConnection();
        if ((connection == null) || !connection.isAuthenticated())
            return null;

        Jid jid = mRecipient.getJid();
        LastActivityManager lastActivityManager = LastActivityManager.getInstanceFor(connection);

        long timeNow = System.currentTimeMillis();
        long elapseTime = -1;
        try {
            elapseTime = lastActivityManager.getLastActivity(jid).getIdleTime();
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                | SmackException.NotConnectedException | InterruptedException | IllegalArgumentException ignore) {
        }

        if (elapseTime == -1) {
            return null;
        }
        else {
            long dateTime = (timeNow - elapseTime * 1000L);

            if (DateUtils.isToday(dateTime)) {
                DateFormat df = DateFormat.getTimeInstance(DateFormat.MEDIUM);
                return getString(R.string.service_gui_LAST_SEEN, df.format(new Date(dateTime)));
            }
            else {
                // lastSeen = DateUtils.getRelativeTimeSpanString(dateTime, timeNow, DateUtils.DAY_IN_MILLIS);
                DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                return df.format(new Date(dateTime));
            }
        }
    }

    /**
     * Send an outgoing file message to chatFragment for it to start the file send process
     * The recipient can be contact or chatRoom
     *
     * @param filePath of the file to be sent
     */
    public void sendFile(String filePath)
    {
        Date date = Calendar.getInstance().getTime();
        String sendTo = selectedChatPanel.getChatSession().getCurrentChatTransport().getName();
        selectedChatPanel.addMessage(sendTo, date, ChatMessage.MESSAGE_FILE_TRANSFER_SEND, Message.ENCODE_PLAIN, filePath);
    }

    public static void sendLocation(String location)
    {
        final ChatPanel chatPanel;
        UIService uiService = AndroidGUIActivator.getUIService();
        if (uiService != null) {
            if (mRecipient != null) {
                chatPanel = (ChatPanel) uiService.getChat(mRecipient);
                if (chatPanel != null) {
                    int encryption = Message.ENCRYPTION_NONE;
                    if (chatPanel.isOmemoChat())
                        encryption = Message.ENCRYPTION_OMEMO;
                    else if (chatPanel.isOTRChat())
                        encryption = Message.ENCRYPTION_OTR;

                    ChatTransport mChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
                    try {
                        mChatTransport.sendInstantMessage(location, encryption | Message.ENCODE_PLAIN);
                    } catch (Exception e) {
                        aTalkApp.showToastMessage(e.getMessage());
                    }
                }
            }
        }
    }

    public void sendAttachment(AttachOptionItem attachOptionItem, final Contact contact)
    {
        mRecipient = contact;
        Intent intent = new Intent();
        Uri fileUri;

        switch (attachOptionItem) {
            case pic:
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, SELECT_PHOTO);
                break;

            case camera:
                // create a file to save the image
                mCameraFilePath = FileBackend.getOutputMediaFile(FileBackend.MEDIA_TYPE_IMAGE);
                fileUri = FileBackend.getUriForFile(this, mCameraFilePath);

                // create Intent to take a picture and return control to the calling application
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                break;

            case video:
                intent.setType("video/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, SELECT_VIDEO);
                break;

            case video_record:
                // create a file to record video
                mCameraFilePath = FileBackend.getOutputMediaFile(FileBackend.MEDIA_TYPE_VIDEO);
                fileUri = FileBackend.getUriForFile(this, mCameraFilePath);

                // create Intent to record video and return control to the calling application
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
                startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
                break;

            case share_file:
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                try {
                    Intent chooseFile = Intent.createChooser(intent, getString(R.string.choose_file_activity_title));
                    startActivityForResult(chooseFile, CHOOSE_FILE_ACTIVITY_REQUEST_CODE);
                } catch (android.content.ActivityNotFoundException ex) {
                    showToastMessage(R.string.service_gui_FOLDER_OPEN_NO_APPLICATION);
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String filePath;

            switch (requestCode) {
                case SELECT_PHOTO:
                case CHOOSE_FILE_ACTIVITY_REQUEST_CODE:
                    mPendingImageUris.clear();
                    mPendingImageUris.addAll(extractUriFromIntent(data));
                    for (Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
                        filePath = FilePathHelper.getPath(this, i.next());
                        if (!StringUtils.isNullOrEmpty(filePath))
                            sendFile(filePath);
                        else
                            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
                    }
                    break;

                case SELECT_VIDEO:
                    Uri selectedVideo = data.getData();
                    if (selectedVideo != null) {
                        filePath = FilePathHelper.getPath(this, selectedVideo);
                        if (!StringUtils.isNullOrEmpty(filePath))
                            sendFile(filePath);
                        else
                            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
                    }
                    break;

                case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
                case CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE:
                    if ((mCameraFilePath != null) && mCameraFilePath.exists()) {
                        filePath = mCameraFilePath.getPath();
                        if (!StringUtils.isNullOrEmpty(filePath)) {
                            mCameraFilePath = null;
                            sendFile(filePath);
                        }
                        else
                            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
                    }
                    else
                        aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
                    break;

                case OPEN_FILE_REQUEST_CODE:
                    if (data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            filePath = FilePathHelper.getPath(this, uri);
                            if (!StringUtils.isNullOrEmpty(filePath))
                                openDownloadable(new File(filePath));
                            else
                                aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
                        }
                    }
                    break;
            }
        }
    }

    @SuppressLint("NewApi")
    private static List<Uri> extractUriFromIntent(final Intent intent)
    {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) {
            return uris;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            final ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); ++i) {
                    uris.add(clipData.getItemAt(i).getUri());
                }
            }
        }
        else {
            uris.add(uri);
        }
        return uris;
    }

    /**
     * Opens the given file through the <tt>DesktopService</tt>.
     * TargetSdkVersion 24 (or higher) and you’re passing a file:/// URI outside your package domain
     * through an Intent, then what you’ll get FileUriExposedException
     *
     * @param file the file to open
     */
    public void openDownloadable(File file)
    {
        if (!file.exists()) {
            showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
            return;
        }

        Uri uri;
        try {
            uri = FileBackend.getUriForFile(this, file);
        } catch (SecurityException e) {
            Timber.i("No permission to access %s: %s", file.getAbsolutePath(), e.getMessage());
            showToastMessage(R.string.service_gui_FILE_OPEN_NO_PERMISSION);
            return;
        }

        String mimeType = getMimeType(this, uri);
        if ((mimeType == null) || mimeType.contains("application")) {
            mimeType = "*/*";
        }

        Intent openIntent = new Intent(Intent.ACTION_VIEW);
        if (mimeType.contains("3gp")) {
            openIntent = new Intent(this, AudioBgService.class);
            openIntent.setAction(AudioBgService.ACTION_PLAYBACK);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openIntent.setDataAndType(uri, mimeType);
            startService(openIntent);
            return;
        }

        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        openIntent.setDataAndType(uri, mimeType);
        PackageManager manager = getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(openIntent, 0);
        if (info.size() == 0) {
            openIntent.setDataAndType(uri, "*/*");
        }
        try {
            startActivity(openIntent);
        } catch (ActivityNotFoundException e) {
            showToastMessage(R.string.service_gui_FILE_OPEN_NO_APPLICATION);
        }
    }

    /**
     * Shows the given error message in the error area of this component.
     *
     * @param resId the Id of the message to show
     */
    private void showToastMessage(int resId)
    {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
