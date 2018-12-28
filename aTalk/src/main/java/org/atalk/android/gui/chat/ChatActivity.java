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
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.*;
import android.widget.Toast;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.conference.ChatInviteDialog;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.chatroomslist.ChatRoomInfoFragment;
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

import java.io.File;
import java.util.*;

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
     * The logger
     */
    private final static Logger logger = Logger.getLogger(ChatActivity.class);

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
    private final int CHAT_PAGER_SIZE = 4;

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
    private MenuItem mLeaveChatRoom;
    private MenuItem mDestroyChatRoom;
    private MenuItem mChatRoomInfo;
    private MenuItem mChatRoomMember;

    /**
     * Holds chat id that is currently handled by this Activity.
     */
    private String currentChatId;
    private ChatPanel selectedChatPanel;
    private static Contact mRecipient;
    private int currentChatMode;

    // currently not implemented
    private int mCurrentChatType;

    private static File mCameraFilePath = null;
    final private List<Uri> mPendingImageUris = new ArrayList<>();
    final private List<Uri> mPendingFileUris = new ArrayList<>();

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

        if (savedInstanceState != null) {
            chatId = savedInstanceState.getString(ChatSessionManager.CHAT_IDENTIFIER);
            currentChatMode = savedInstanceState.getInt(ChatSessionManager.CHAT_MODE);
            mCurrentChatType = savedInstanceState.getInt(ChatSessionManager.CHAT_MSGTYPE);
        }
        else {
            chatId = intent.getStringExtra(ChatSessionManager.CHAT_IDENTIFIER);
            currentChatMode = intent.getIntExtra(ChatSessionManager.CHAT_MODE, ChatSessionManager.MC_CHAT);
            mCurrentChatType = intent.getIntExtra(ChatSessionManager.CHAT_MSGTYPE, ChatFragment.MSGTYPE_NORMAL);
        }
        if (chatId == null)
            throw new RuntimeException("Missing chat identifier extra");

        ChatPanel chatPanel = ChatSessionManager.createChatForChatId(chatId, currentChatMode);
        if (chatPanel == null) {
            logger.error("Failed to create chat session for " + currentChatMode + ": " + chatId);
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
            logger.warn("ChatId can't be null - finishing & exist ChatActivity");
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
        mLeaveChatRoom = mMenu.findItem(R.id.leave_chat_room);
        mDestroyChatRoom = mMenu.findItem(R.id.destroy_chat_room);
        mChatRoomInfo = mMenu.findItem(R.id.chatroom_info);
        mChatRoomMember = mMenu.findItem(R.id.show_chat_room_occupant);

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
            ChatSession chatSession = selectedChatPanel.getChatSession();

            // Enable/disable certain menu items based on current transport type
            boolean contactSession = (chatSession instanceof MetaContactChatSession);
            if (contactSession) {
                mLeaveChatRoom.setVisible(false);
                mDestroyChatRoom.setVisible(false);
                mHistoryErase.setTitle(R.string.service_gui_HISTORY_ERASE_PER_CONTACT);

                // check if to show call buttons.
                Object metaContact = chatSession.getDescriptor();
                MetaContactRenderer contactRenderer = new MetaContactRenderer();

                boolean isShowCall = contactRenderer.isShowCallBtn(metaContact);
                boolean isShowVideoCall = contactRenderer.isShowVideoCallBtn(metaContact);
                mCallAudioContact.setVisible(isShowCall);
                mCallVideoContact.setVisible(isShowVideoCall);

                boolean isShowFileSend = contactRenderer.isShowFileSendBtn(metaContact);
                mSendFile.setVisible(isShowFileSend);
                mSendLocation.setVisible(true);
                mChatRoomInfo.setVisible(false);
                mChatRoomMember.setVisible(false);
            }
            else {
                mLeaveChatRoom.setVisible(true);
                mDestroyChatRoom.setVisible(true);
                mHistoryErase.setTitle(R.string.service_gui_CHATROOM_HISTORY_ERASE_PER);
                mChatRoomInfo.setVisible(true);
                mChatRoomMember.setVisible(true);
                mCallAudioContact.setVisible(false);
                mCallVideoContact.setVisible(false);
                mSendFile.setVisible(false);
                mSendLocation.setVisible(false);
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
        Intent intent;
        Object object;
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.muc_invite:
                ChatInviteDialog inviteDialog = new ChatInviteDialog(this, selectedChatPanel);
                inviteDialog.show();
                return true;

            case R.id.call_contact_audio: // start voice call
                if (mRecipient != null)
                    AndroidCallUtil.createCall(this, mRecipient.getAddress(),
                            mRecipient.getProtocolProvider(), false);
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

            case R.id.leave_chat_room:
                object = selectedChatPanel.getChatSession().getDescriptor();
                if (object instanceof ChatRoomWrapper) {
                    ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
                    ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
                    if (chatRoom != null) {
                        ChatRoomWrapper leavedRoomWrapped = MUCActivator.getMUCService().leaveChatRoom(chatRoomWrapper);
                        if (leavedRoomWrapped != null) {
                            MUCActivator.getUIService().closeChatRoomWindow(leavedRoomWrapped);
                        }
                    }
                    // MUCActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
                    ChatSessionManager.removeActiveChat(selectedChatPanel);
                    MUCActivator.getMUCService().removeChatRoom(chatRoomWrapper);
                    finish();
                }
                return true;

            case R.id.destroy_chat_room:
                object = selectedChatPanel.getChatSession().getDescriptor();
                if (object instanceof ChatRoomWrapper) {
                    ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
                    ChatRoom chatRoom = chatRoomWrapper.getChatRoom();

                    // Not necessary as destroyChatRoom will also remove muc chatSession
                    // MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
                    // mhs.eraseLocallyStoredHistory(chatRoom, null);

                    MUCActivator.getMUCService().destroyChatRoom(chatRoomWrapper, "User requested",
                            chatRoom.getIdentifier());
                    ChatSessionManager.removeActiveChat(selectedChatPanel);
                    // It is safer to just finish. see case R.id.close_chat:
                    finish();
                }
                return true;

            case R.id.send_file:
                AttachOptionDialog attachOptionDialog = new AttachOptionDialog(this, mRecipient);
                attachOptionDialog.show();
                return true;

            case R.id.chatroom_info:
                object = selectedChatPanel.getChatSession().getDescriptor();
                if (object instanceof ChatRoomWrapper) {
                    ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
                    ChatRoomInfoFragment chatRoomInfoFragment = ChatRoomInfoFragment.newInstance(chatRoomWrapper);
                    getSupportFragmentManager().beginTransaction()
                            .replace(android.R.id.content, chatRoomInfoFragment).commit();
                }
                return true;

            case R.id.show_chat_room_occupant:
                object = selectedChatPanel.getChatSession().getDescriptor();
                if (object instanceof ChatRoomWrapper) {
                    ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
                    StringBuilder memberList = new StringBuilder();
                    List<ChatRoomMember> occupants = chatRoomWrapper.getChatRoom().getMembers();
                    for (ChatRoomMember member : occupants) {
                        ChatRoomMemberJabberImpl occupant = (ChatRoomMemberJabberImpl) member;
                        memberList.append(occupant.getNickName())
                                .append(" - ")
                                .append(occupant.getJabberID())
                                .append("<br/>");
                    }
                    String user = chatRoomWrapper.getParentProvider().getProtocolProvider().getAccountID().getUserID();
                    selectedChatPanel.addMessage(user, new Date(), Chat.SYSTEM_MESSAGE, ChatMessage.ENCODE_HTML,
                            memberList.toString());
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
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
     * Sets the selected chat.
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

            if (chatSession == null) {
                logger.error("Cannot continue without the default chatSession");
                return;
            }

            ActionBarUtil.setTitle(this, chatSession.getCurrentChatTransport().getDisplayName());
            if (chatSession instanceof MetaContactChatSession) {
                ActionBarUtil.setAvatar(this, chatSession.getChatAvatar());
                PresenceStatus status = chatSession.getCurrentChatTransport().getStatus();
                if (status != null) {
                    // ActionBarUtil.setStatus(this, status.getStatusIcon());
                    ActionBarUtil.setStatus(this, status.getStatusIcon());
                    ActionBarUtil.setSubtitle(this, status.getStatusName());
                }
            }
            else if (chatSession instanceof ConferenceChatSession) {
                ConferenceChatSession ccSession = (ConferenceChatSession) chatSession;
                ActionBarUtil.setAvatar(this, AndroidImageUtil.convertToBytes(BitmapFactory
                        .decodeResource(this.getResources(), R.drawable.ic_chatroom), 100));
                ActionBarUtil.setStatus(this, ccSession.getChatStatusIcon());

                // mSubTitle = ccSession.getChatSubject();
                StringBuilder mSubTitle = new StringBuilder();
                Iterator<ChatContact<?>> mParticipants = ccSession.getParticipants();
                while (mParticipants.hasNext()) {
                    mSubTitle.append(mParticipants.next().getName()).append(", ");
                }
                ActionBarUtil.setSubtitle(this, mSubTitle.toString());
            }
        }
    }

    public void sendFile(String filePath)
    {
        final ChatPanel chatPanel;
        Date date = Calendar.getInstance().getTime();
        UIService uiService = AndroidGUIActivator.getUIService();
        if (uiService != null) {
            String sendTo = mRecipient.getAddress();
            if ((mRecipient != null) && mRecipient instanceof Contact) {
                chatPanel = (ChatPanel) uiService.getChat(mRecipient);
                if (chatPanel != null) {
                    chatPanel.addMessage(sendTo, date, ChatPanel.OUTGOING_FILE_MESSAGE, ChatMessage.ENCODE_PLAIN, filePath);
                }
            }
        }
    }

    public static void sendLocation(String location)
    {
        final ChatPanel chatPanel;
        UIService uiService = AndroidGUIActivator.getUIService();
        if (uiService != null) {
            if (mRecipient != null) {
                chatPanel = (ChatPanel) uiService.getChat(mRecipient);
                if (chatPanel != null) {
                    int encryption = ChatMessage.ENCRYPTION_NONE;
                    if (chatPanel.isOmemoChat())
                        encryption = ChatMessage.ENCRYPTION_OMEMO;
                    else if (chatPanel.isOTRChat())
                        encryption = ChatMessage.ENCRYPTION_OTR;

                    ChatTransport mChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
                    try {
                        mChatTransport.sendInstantMessage(location, encryption, ChatMessage.ENCODE_PLAIN);
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent, SELECT_PHOTO);
                break;

            case camera:
                // create a file to save the image
                mCameraFilePath = FileBackend.getOutputMediaFile(FileBackend.MEDIA_TYPE_IMAGE);
                fileUri = FileBackend.getUriForFile(this, mCameraFilePath);

                // create Intent to take a picture and return control to the calling application
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
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
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) && uri == null) {
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
            logger.debug("No permission to access " + file.getAbsolutePath(), e);
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
            openIntent.setDataAndType(uri, mimeType);
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startService(openIntent);
            return;
        }

        openIntent.setDataAndType(uri, mimeType);
        openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
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
