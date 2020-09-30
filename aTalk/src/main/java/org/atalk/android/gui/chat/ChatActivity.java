/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.ImageView;
import android.widget.Toast;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.sf.fmj.utility.IOUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.*;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chat.conference.ChatInviteDialog;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.chatroomslist.*;
import org.atalk.android.gui.contactlist.model.MetaContactRenderer;
import org.atalk.android.gui.dialogs.AttachOptionDialog;
import org.atalk.android.gui.dialogs.AttachOptionItem;
import org.atalk.android.gui.share.Attachment;
import org.atalk.android.gui.share.MediaPreviewAdapter;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.EntityListHelper.TaskCompleted;
import org.atalk.android.plugin.audioservice.AudioBgService;
import org.atalk.android.plugin.geolocation.GeoLocation;
import org.atalk.crypto.CryptoFragment;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.atalk.service.osgi.OSGiActivity;
import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager;
import org.jivesoftware.smackx.iqlast.LastActivityManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.Jid;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.util.*;

import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
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
public class ChatActivity extends OSGiActivity
        implements OnPageChangeListener, TaskCompleted, GeoLocation.LocationListener, ChatRoomConfiguration.ChatRoomConfigListener
{
    private static final int REQUEST_CODE_SELECT_PHOTO = 100;
    private static final int REQUEST_CODE_CAPTURE_IMAGE_ACTIVITY = 101;
    private static final int REQUEST_CODE_CAPTURE_VIDEO_ACTIVITY = 102;
    private static final int REQUEST_CODE_SELECT_VIDEO = 103;
    private static final int REQUEST_CODE_CHOOSE_FILE_ACTIVITY = 104;
    private static final int REQUEST_CODE_OPEN_FILE = 105;

    private static final int REQUEST_CODE_SHARE_WITH = 200;
    private static final int REQUEST_CODE_FORWARD = 201;

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
     * The media preview adapter, which provides views of all attachments.
     */
    private MediaPreviewAdapter mediaPreviewAdapter;

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
    private MenuItem mTtsEnable;
    private MenuItem mStatusEnable;
    private MenuItem mRoomInvite;
    private MenuItem mLeaveChatRoom;
    private MenuItem mDestroyChatRoom;
    private MenuItem mChatRoomInfo;
    private MenuItem mChatRoomMember;
    private MenuItem mChatRoomConfig;
    private MenuItem mChatRoomNickSubject;
    private MenuItem mOtr_Session;
    /**
     * Holds chatId that is currently handled by this Activity.
     */
    private String currentChatId;
    // Current chatMode see ChatSessionManager ChatMode variables
    private int currentChatMode;
    // Not implemented currently
    private int mCurrentChatType;

    private ChatPanel selectedChatPanel;
    private static Contact mRecipient;

    private ChatRoomConfiguration chatRoomConfig;
    private CryptoFragment cryptoFragment;

    /**
     * file for camera picture or video capture
     */
    private static File mCameraFilePath = null;

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
        setContentView(R.layout.chat_main);

        // If chat notification has been clicked and OSGi service has been killed in the meantime
        // then we have to start it and restore this activity
        if (postRestoreIntent()) {
            return;
        }
        // Add fragment for crypto padLock for OTR and OMEMO before start pager
        cryptoFragment = new CryptoFragment();
        getSupportFragmentManager().beginTransaction().add(cryptoFragment, CRYPTO_FRAGMENT).commit();

        // Instantiate a ViewPager and a PagerAdapter.
        chatPager = findViewById(R.id.chatPager);
        chatPagerAdapter = new ChatPagerAdapter(getSupportFragmentManager(), this);
        chatPager.setAdapter(chatPagerAdapter);
        chatPager.setOffscreenPageLimit(CHAT_PAGER_SIZE);
        chatPager.addOnPageChangeListener(this);

        /*
         * Media Preview display area for user confirmation before sending
         */
        ImageView imagePreview = findViewById(R.id.imagePreview);
        RecyclerView mediaPreview = findViewById(R.id.media_preview);
        mediaPreviewAdapter = new MediaPreviewAdapter(this, imagePreview);
        mediaPreview.setAdapter(mediaPreviewAdapter);

        // Registered location listener - only use by playStore version
        GeoLocation.registeredLocationListener(this);
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

        if (intent.getClipData() != null) {
            if (intent.getCategories() != null)
                onActivityResult(REQUEST_CODE_FORWARD, RESULT_OK, intent);
            else
                onActivityResult(REQUEST_CODE_SHARE_WITH, RESULT_OK, intent);

        }
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
        // Must reset unread message counter on chatSession closed
        // Otherwise value not clear when user enter and exit chatSession without page slide
        if (selectedChatPanel != null) {
            Object descriptor = selectedChatPanel.getChatSession().getDescriptor();
            if (descriptor instanceof MetaContact) {
                ((MetaContact) descriptor).setUnreadCount(0);
            }
            else if (descriptor instanceof ChatRoomWrapper) {
                ((ChatRoomWrapper) descriptor).setUnreadCount(0);
            }
        }
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
        if (chatPagerAdapter != null) {
            chatPagerAdapter.dispose();
        }

        // Clear last chat intent
        AndroidUtils.clearGeneralNotification(aTalkApp.getGlobalContext());
    }

    /**
     * Must check chatFragment for non-null before proceed
     * User by ShareUtil to toggle media preview if any
     */
    public void toggleInputMethod()
    {
        ChatFragment chatFragment;
        if ((chatFragment = chatPagerAdapter.getCurrentChatFragment()) != null)
            chatFragment.getChatController().updateSendModeState();
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
            if (chatRoomConfig != null) {
                chatRoomConfig.onBackPressed();
            }
            else {
                finish();
            }
            return true;
        }
        else {
            // Pass to ChatController to handle; reference may be null on event triggered => NPE. so must check
            ChatFragment chatFragment;
            ChatController chatController;

            if ((chatFragment = chatPagerAdapter.getCurrentChatFragment()) != null) {
                if ((chatController = chatFragment.getChatController()) != null) {
                    if (chatController.onKeyUp(keyCode, event))
                        return true;
                }
            }
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
        mTtsEnable = mMenu.findItem(R.id.chat_tts_enable);
        mStatusEnable = mMenu.findItem(R.id.room_status_enable);
        mHistoryErase = mMenu.findItem(R.id.erase_chat_history);
        mRoomInvite = mMenu.findItem(R.id.muc_invite);
        mLeaveChatRoom = mMenu.findItem(R.id.leave_chat_room);
        mDestroyChatRoom = mMenu.findItem(R.id.destroy_chat_room);
        mChatRoomInfo = mMenu.findItem(R.id.chatroom_info);
        mChatRoomMember = mMenu.findItem(R.id.show_chatroom_occupant);
        mChatRoomConfig = mMenu.findItem(R.id.chatroom_config);
        mChatRoomNickSubject = mMenu.findItem(R.id.chatroom_info_change);
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

                mTtsEnable.setVisible(!isDomainJid);
                mTtsEnable.setTitle(mRecipient.isTtsEnable()
                        ? R.string.service_gui_TTS_DISABLE : R.string.service_gui_TTS_ENABLE);

                mStatusEnable.setVisible(false);
                mRoomInvite.setVisible(!isDomainJid);
                mChatRoomInfo.setVisible(false);
                mChatRoomMember.setVisible(false);
                mChatRoomConfig.setVisible(false);
                mChatRoomNickSubject.setVisible(false);
                // Also let CryptoFragment handles this to take care Omemo and OTR
                mOtr_Session.setVisible(!isDomainJid);
            }
            else {
                // Only room owner is allowed to destroy chatRoom - role should not be null for joined room
                ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) chatSession.getDescriptor();
                ChatRoomMemberRole role = chatRoomWrapper.getChatRoom().getUserRole();

                mDestroyChatRoom.setVisible(ChatRoomMemberRole.OWNER.equals(role));
                mChatRoomConfig.setVisible(ChatRoomMemberRole.OWNER.equals(role));

                boolean isJoined = chatRoomWrapper.getChatRoom().isJoined();
                mLeaveChatRoom.setVisible(isJoined);
                mSendFile.setVisible(isJoined && hasUploadService);
                mSendLocation.setVisible(isJoined);

                mTtsEnable.setVisible(isJoined);
                mTtsEnable.setTitle(chatRoomWrapper.isTtsEnable()
                        ? R.string.service_gui_TTS_DISABLE : R.string.service_gui_TTS_ENABLE);

                mStatusEnable.setVisible(true);
                mStatusEnable.setTitle(chatRoomWrapper.isRoomStatusEnable()
                        ? R.string.service_gui_CHATROOM_STATUS_OFF : R.string.service_gui_CHATROOM_STATUS_ON);

                mChatRoomNickSubject.setVisible(isJoined);

                mHistoryErase.setTitle(R.string.service_gui_CHATROOM_HISTORY_ERASE_PER);
                mChatRoomInfo.setVisible(true);
                mChatRoomMember.setVisible(true);

                // not available in chatRoom
                mCallAudioContact.setVisible(false);
                mCallVideoContact.setVisible(false);
                mOtr_Session.setVisible(false);
            }
            // Show the TTS enable option only if global TTS option is enabled.
            mTtsEnable.setVisible(ConfigurationUtils.isTtsEnable());

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

        Object descriptor = selectedChatPanel.getChatSession().getDescriptor();

        // Common handler for both the ChatRoomWrapper and MetaContact
        switch (item.getItemId()) {
            case R.id.send_file:
                AttachOptionDialog attachOptionDialog = new AttachOptionDialog(this);
                attachOptionDialog.show();
                return true;

            case R.id.muc_invite:
                ChatInviteDialog inviteDialog = new ChatInviteDialog(this, selectedChatPanel);
                inviteDialog.show();
                return true;

            case R.id.erase_chat_history:
                EntityListHelper.eraseEntityChatHistory(this, descriptor, null, null);
                return true;

            case R.id.send_location:
                Intent intent = new Intent(this, GeoLocation.class);
                intent.putExtra(GeoLocation.SEND_LOCATION, true);
                startActivity(intent);
                return true;
        }

        if (descriptor instanceof ChatRoomWrapper) {
            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) descriptor;
            ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.addToBackStack(null);

            switch (item.getItemId()) {
                case R.id.chat_tts_enable:
                    if (chatRoomWrapper.isTtsEnable()) {
                        chatRoomWrapper.setTtsEnable(false);
                        mTtsEnable.setTitle(R.string.service_gui_TTS_ENABLE);
                    }
                    else {
                        chatRoomWrapper.setTtsEnable(true);
                        mTtsEnable.setTitle(R.string.service_gui_TTS_DISABLE);
                    }
                    selectedChatPanel.updateChatTtsOption();
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
                    new ChatRoomDestroyDialog().show(this, chatRoomWrapper, selectedChatPanel);
                    // It is safer to just finish. see case R.id.close_chat:
                    finish();
                    return true;

                case R.id.chatroom_info:
                    ChatRoomInfoDialog chatRoomInfoDialog = ChatRoomInfoDialog.newInstance(chatRoomWrapper);
                    chatRoomInfoDialog.show(ft, "infoDialog");
                    return true;

                case R.id.chatroom_info_change:
                    new ChatRoomInfoChangeDialog().show(this, chatRoomWrapper);
                    return true;

                case R.id.chatroom_config:
                    chatRoomConfig = ChatRoomConfiguration.getInstance(chatRoomWrapper, this);
                    ft.replace(android.R.id.content, chatRoomConfig).commit();
                    return true;

                case R.id.room_status_enable:
                    if (chatRoomWrapper.isRoomStatusEnable()) {
                        chatRoomWrapper.setRoomStatusEnable(false);
                        mStatusEnable.setTitle(R.string.service_gui_CHATROOM_STATUS_ON);
                    }
                    else {
                        chatRoomWrapper.setRoomStatusEnable(true);
                        mStatusEnable.setTitle(R.string.service_gui_CHATROOM_STATUS_OFF);
                    }
                    return true;

                case R.id.show_chatroom_occupant:
                    StringBuilder memberList = new StringBuilder();
                    List<ChatRoomMember> occupants = chatRoom.getMembers();
                    if (occupants.size() > 0) {
                        for (ChatRoomMember member : occupants) {
                            ChatRoomMemberJabberImpl occupant = (ChatRoomMemberJabberImpl) member;
                            memberList.append(occupant.getNickName())
                                    .append(" - ")
                                    .append(occupant.getJabberID())
                                    .append(" (")
                                    .append(member.getRole().getRoleName())
                                    .append(")")
                                    .append("<br/>");
                        }
                    }
                    else {
                        memberList.append(getString(R.string.service_gui_LIST_NONE));
                    }
                    String user = chatRoomWrapper.getProtocolProvider().getAccountID().getUserID();
                    selectedChatPanel.addMessage(user, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML,
                            memberList.toString());
                    return true;
            }
        }
        // Handle item selection for mRecipient if non-null
        else if (mRecipient != null) {
            Boolean isAudioCall = null;

            switch (item.getItemId()) {
                case R.id.chat_tts_enable:
                    if (mRecipient.isTtsEnable()) {
                        mRecipient.setTtsEnable(false);
                        mTtsEnable.setTitle(R.string.service_gui_TTS_ENABLE);
                    }
                    else {
                        mRecipient.setTtsEnable(true);
                        mTtsEnable.setTitle(R.string.service_gui_TTS_DISABLE);
                    }
                    selectedChatPanel.updateChatTtsOption();
                    return true;

                case R.id.call_contact_audio: // start audio call
                    Jid jid = mRecipient.getJid();
                    if (jid instanceof DomainBareJid) {
                        TelephonyFragment extPhone = TelephonyFragment.newInstance(jid.toString());
                        getSupportFragmentManager().beginTransaction()
                                .replace(android.R.id.content, extPhone).commit();
                        return true;
                    }
                    isAudioCall = true;  // fall through to start either audio / video call

                case R.id.call_contact_video:
                    // AndroidCallUtil.createCall(this, mRecipient.getAddress(),
                    //        mRecipient.getProtocolProvider(), (isAudioCall == null));

                    AndroidCallUtil.createCall(this, selectedChatPanel.getMetaContact(),
                            (isAudioCall == null), null);
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

            // Update the actionBar Title with the entity name
            ActionBarUtil.setTitle(this, chatSession.getCurrentChatTransport().getDisplayName());

            if (chatSession instanceof MetaContactChatSession) {
                // Reset unread message count when user slides to view this chat session
                ((MetaContact) chatSession.getDescriptor()).setUnreadCount(0);

                ActionBarUtil.setAvatar(this, chatSession.getChatAvatar());
                PresenceStatus status = chatSession.getCurrentChatTransport().getStatus();
                if (status != null) {
                    ActionBarUtil.setStatus(this, status.getStatusIcon());

                    if (!status.isOnline()) {
                        getLastSeen(status);
                    }
                    else {
                        // Reset elapse time to fetch new again when contact goes offline again
                        mRecipient.setLastActiveTime(-1);
                        ActionBarUtil.setSubtitle(this, status.getStatusName());
                    }
                }
            }
            else if (chatSession instanceof ConferenceChatSession) {
                // Reset unread message count when user slides to view this chat session
                ((ChatRoomWrapper) chatSession.getDescriptor()).setUnreadCount(0);

                ConferenceChatSession ccSession = (ConferenceChatSession) chatSession;
                ActionBarUtil.setAvatar(this, R.drawable.ic_chatroom);
                ActionBarUtil.setStatus(this, ccSession.getChatStatusIcon());
                ActionBarUtil.setSubtitle(this, ccSession.getChatSubject());
            }
        }
    }

    /**
     * Fetch and display the contact lastSeen elapsed Time; run in new thread to avoid ANR
     */
    public void getLastSeen(PresenceStatus status)
    {
        // cmeng: this happens if the contact remove presence subscription while still in chat session
        if (mRecipient != null) {
            XMPPConnection connection = mRecipient.getProtocolProvider().getConnection();

            // Proceed only if user is online and registered
            if ((connection != null) && connection.isAuthenticated()) {
                new Thread(() -> {
                    final String lastSeen;
                    Contact mContact = mRecipient;

                    // Retrieve from server if this is the first access
                    long lastActiveTime = mRecipient.getLastActiveTime();
                    if (lastActiveTime == -1) {
                        Jid jid = mRecipient.getJid();
                        LastActivityManager lastActivityManager = LastActivityManager.getInstanceFor(connection);

                        try {
                            long elapseTime = lastActivityManager.getLastActivity(jid).getIdleTime();
                            lastActiveTime = (System.currentTimeMillis() - elapseTime * 1000L);
                            mRecipient.setLastActiveTime(lastActiveTime);
                        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                                | SmackException.NotConnectedException | InterruptedException | IllegalArgumentException e) {
                            Timber.w("Exception in getLastSeen %s", e.getMessage());
                        }
                    }

                    if (lastActiveTime != -1) {
                        if (DateUtils.isToday(lastActiveTime)) {
                            DateFormat df = DateFormat.getTimeInstance(DateFormat.MEDIUM);
                            lastSeen = getString(R.string.service_gui_LAST_SEEN, df.format(new Date(lastActiveTime)));
                        }
                        else {
                            // lastSeen = DateUtils.getRelativeTimeSpanString(dateTime, timeNow, DateUtils.DAY_IN_MILLIS);
                            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                            lastSeen = df.format(new Date(lastActiveTime));
                        }
                    }
                    else {
                        lastSeen = status.getStatusName();
                    }
                    // Update display only if the result is for the intended mContact;
                    // user may have slide to new chatSession if server has slow response
                    if (mContact.equals(mRecipient))
                        runOnUiThread(() -> ActionBarUtil.setSubtitle(ChatActivity.this, lastSeen));
                }).start();
            }
            return;
        }

        // Reset elapse time to fetch new again when contact goes offline again and just update with contact old status
        // mRecipient.setLastActiveTime(-1);
        ActionBarUtil.setSubtitle(this, status.getStatusName());
    }

    public void sendAttachment(AttachOptionItem attachOptionItem)
    {
        Intent intent = new Intent();
        Uri fileUri;

        switch (attachOptionItem) {
            case pic:
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, REQUEST_CODE_SELECT_PHOTO);
                break;

            case video:
                intent.setType("video/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, REQUEST_CODE_SELECT_VIDEO);
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
                try {
                    startActivityForResult(intent, REQUEST_CODE_CAPTURE_IMAGE_ACTIVITY);
                } catch (SecurityException e) {
                    aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback);
                }
                break;

            case video_record:
                // create a file to record video
                mCameraFilePath = FileBackend.getOutputMediaFile(FileBackend.MEDIA_TYPE_VIDEO);
                fileUri = FileBackend.getUriForFile(this, mCameraFilePath);

                // create Intent to record video and return control to the calling application
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                intent.setAction(MediaStore.ACTION_VIDEO_CAPTURE);
                try {
                    startActivityForResult(intent, REQUEST_CODE_CAPTURE_VIDEO_ACTIVITY);
                } catch (SecurityException e) {
                    aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback);
                }
                break;

            case share_file:
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setAction(Intent.ACTION_GET_CONTENT);
                try {
                    Intent chooseFile = Intent.createChooser(intent, getString(R.string.choose_file_activity_title));
                    startActivityForResult(chooseFile, REQUEST_CODE_CHOOSE_FILE_ACTIVITY);
                } catch (android.content.ActivityNotFoundException ex) {
                    showToastMessage(R.string.service_gui_FOLDER_OPEN_NO_APPLICATION);
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK) {
            String filePath;
            List<Attachment> attachments;

            switch (requestCode) {
                case REQUEST_CODE_SELECT_PHOTO:
                case REQUEST_CODE_SELECT_VIDEO:
                case REQUEST_CODE_CHOOSE_FILE_ACTIVITY:
                    attachments = Attachment.extractAttachments(this, intent, Attachment.Type.IMAGE);
                    mediaPreviewAdapter.addMediaPreviews(attachments);
                    break;

                case REQUEST_CODE_CAPTURE_IMAGE_ACTIVITY:
                case REQUEST_CODE_CAPTURE_VIDEO_ACTIVITY:
                    if ((mCameraFilePath != null) && mCameraFilePath.exists()) {
                        Uri uri = FileBackend.getUriForFile(this, mCameraFilePath);
                        attachments = Attachment.of(this, uri, Attachment.Type.IMAGE);
                        mediaPreviewAdapter.addMediaPreviews(attachments);
                    }
                    break;

                case REQUEST_CODE_OPEN_FILE:
                    if (intent != null) {
                        Uri uri = intent.getData();
                        if (uri != null) {
                            filePath = FilePathHelper.getPath(this, uri);
                            if (StringUtils.isNotEmpty(filePath))
                                openDownloadable(new File(filePath));
                            else
                                aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
                        }
                    }
                    break;

                case REQUEST_CODE_SHARE_WITH:
                    Timber.d("Share Intent with: REQUEST_CODE_SHARE_WITH");
                    selectedChatPanel.setEditedText(null);
                    if ("text/plain".equals(intent.getType())) {
                        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                        if (!TextUtils.isEmpty(text)) {
                            if (FileBackend.isHttpFileDnLink(text)) {
                                MediaShareAsynTask msTask = new MediaShareAsynTask();
                                msTask.execute(text);
                                break;
                            }
                            else {
                                selectedChatPanel.setEditedText(text);
                            }
                        }
                    }
                    else {
                        attachments = Attachment.extractAttachments(this, intent, Attachment.Type.IMAGE);
                        mediaPreviewAdapter.addMediaPreviews(attachments);
                    }
                    // Switch to active chat fragment and update the chatController entry
                    chatPagerAdapter.notifyDataSetChanged();
                    toggleInputMethod();
                    break;

                case REQUEST_CODE_FORWARD:
                    Timber.d("Share Intent with: REQUEST_CODE_FORWARD");
                    selectedChatPanel.setEditedText(null);
                    String text = (intent.getCategories() == null) ? null : intent.getCategories().toString();
                    if (!TextUtils.isEmpty(text)) {
                        selectedChatPanel.setEditedText(text);
                    }

                    attachments = Attachment.extractAttachments(this, intent, Attachment.Type.IMAGE);
                    mediaPreviewAdapter.addMediaPreviews(attachments);

                    // Switch to active chat fragment and update the chatController entry
                    chatPagerAdapter.notifyDataSetChanged();
                    toggleInputMethod();
                    break;
            }
        }
    }

    /**
     * callBack for GeoLocation onResult received
     *
     * @param location Geo Location information
     * @param locAddress Geo Location Address
     */
    @Override
    public void onResult(Location location, String locAddress)
    {
        String mLatitude = String.valueOf(location.getLatitude());
        String mLongitude = String.valueOf(location.getLongitude());
        String msg = locAddress + " \nLatLng: " + mLatitude + "," + mLongitude;

        selectedChatPanel.sendMessage(msg, IMessage.ENCODE_PLAIN);
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
        if (mimeType.contains("audio") || mimeType.contains("3gp")) {
            openIntent = new Intent(this, AudioBgService.class);
            openIntent.setAction(AudioBgService.ACTION_PLAYBACK_PLAY);
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
     * Call back from ChatRoomConfiguration when it has completed the task.
     * 1. Stop all future onBackPressed call to ChatRoomConfiguration
     * 2. Re-init OMEMO support option after room properties changed.
     *
     * @param configUpdates room configuration user selected fields for update
     */
    @Override
    public void onConfigComplete(Map<String, Object> configUpdates)
    {
        chatRoomConfig = null;
        cryptoFragment.updateOmemoSupport();
    }

    /**
     * Construct media url share with thumbnail and title via URL_EMBBED which supports with JSONObject:
     *
     * {"width":480,"provider_name":"YouTube","url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
     * "title":"Rick Astley - Never Gonna Give You Up (Video)","author_name":"RickAstleyVEVO",
     * "thumbnail_width":480,"height":270,"thumbnail_url":"https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
     * "author_url":"https://www.youtube.com/user/RickAstleyVEVO","type":"video","provider_url":"https://www.youtube.com/",
     * "thumbnail_height":360,"version":"1.0","html":"\n<iframe width=\" 480\" height=\"270\"
     * src=\"https://www.youtube.com/embed/dQw4w9WgXcQ?feature=oembed\" frameborder=\"0\" allowfullscreen=\"allowfullscreen\"></iframe>\n"}
     */
    private class MediaShareAsynTask extends AsyncTask<String, Void, String>
    {
        private String mUrl;

        @Override
        protected String doInBackground(String... params)
        {
            mUrl = params[0];
            // mUrl = "https://vimeo.com/45196609";  // invalid link
            return getUrlInfo(mUrl);
        }

        @Override
        protected void onPostExecute(String result)
        {
            String urlInfo = null;
            if (!TextUtils.isEmpty(result)) {
                try {
                    final JSONObject attributes = new JSONObject(result);
                    String title = attributes.getString("title");
                    String imageUrl = attributes.getString("thumbnail_url");

                    urlInfo = getString(R.string.service_gui_URL_MEDIA_SHARE, imageUrl, title, mUrl);
                    selectedChatPanel.sendMessage(urlInfo, IMessage.ENCODE_HTML);
                } catch (JSONException e) {
                    Timber.w("Exception in JSONObject access: %s", result);
                }
            }

            // send mUrl instead fetch urlInfo failed
            if (urlInfo == null) {
                // selectedChatPanel.setEditedText(mUrl); too late as controller msgEdit is already initialized
                selectedChatPanel.sendMessage(mUrl, IMessage.ENCODE_PLAIN);
            }
        }

        /***
         * Get the Drawable from the given URL (change to secure https if necessary)
         * aTalk/android supports only secure https connection
         * https://noembed.com/embed?url=https://www.youtube.com/watch?v=dQw4w9WgXcQ
         *
         * @param urlString url string
         * @return Jason String
         */
        private String getUrlInfo(String urlString)
        {
            // Server that provides the media info for the supported services
            String URL_EMBBED = "https://noembed.com/embed?url=";

            try {
                urlString = URL_EMBBED + urlString.replace("http:", "https:");
                URL mUrl = new URL(urlString);
                HttpURLConnection httpConnection = (HttpURLConnection) mUrl.openConnection();
                httpConnection.setRequestMethod("GET");
                httpConnection.setRequestProperty("Content-length", "0");
                httpConnection.setUseCaches(false);
                httpConnection.setAllowUserInteraction(false);
                httpConnection.setConnectTimeout(3000);
                httpConnection.setReadTimeout(3000);
                httpConnection.connect();

                int responseCode = httpConnection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = httpConnection.getInputStream();
                    return IOUtils.readAllToString(inputStream);
                }
            } catch (IOException e) {
                Timber.w("Exception in get URL info: %s", e.getMessage());
            }
            return null;
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
