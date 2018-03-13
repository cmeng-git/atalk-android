/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.conference.ChatInviteDialog;
import org.atalk.android.gui.chat.conference.ConferenceChatSession;
import org.atalk.android.gui.contactlist.model.MetaContactRenderer;
import org.atalk.android.gui.dialogs.AttachOptionDialog;
import org.atalk.android.gui.dialogs.AttachOptionItem;
import org.atalk.android.gui.util.ActionBarUtil;
import org.atalk.android.gui.util.AndroidCallUtil;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.EntityListHelper.TaskCompleted;
import org.atalk.android.plugin.audioservice.AudioBgService;
import org.atalk.android.plugin.geolocation.GPSTracker;
import org.atalk.android.plugin.geolocation.GeoLocation;
import org.atalk.android.util.CameraAccess;
import org.atalk.android.util.FileAccess;
import org.atalk.crypto.CryptoFragment;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.StringUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static org.atalk.android.util.FileAccess.getMimeType;

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
     * view hierarchy in an idle state. Pages beyond this limit will be recreated from the
     * adapter when needed.
     * Note: this is not the max fragments that user is allowed to have
     */
    private final int CHAT_PAGER_SIZE = 4;

    /**
     * Caches last index to prevent from propagating too many events.
     */
    private int lastSelectedIdx = -1;

    /**
     * mContactSession is true when it is a MetaContactChatSession
     * Flag to enable/disable certain menu items
     */
    private static boolean mContactSession = false;

    /**
     * ChatActivity menu & menuItem
     */
    private Menu mMenu;
    private MenuItem mHistoryErase;
    private MenuItem mCallContact;
    private MenuItem mSendFile;
    private MenuItem mSendLocation;
    private MenuItem mDestroyChatRoom;
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

    private static String mCameraFilePath = "";

    private static final int SELECT_PHOTO = 100;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 101;
    private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 102;
    private static final int SELECT_VIDEO = 103;
    private static final int CHOOSE_FILE_ACTIVITY_REQUEST_CODE = 104;
    private static final int OPEN_FILE_REQUEST_CODE = 105;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState
     *         If the activity is being re-initialized after previously being shut down then this
     *         Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     *         Note: Otherwise it is null.
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
        getSupportFragmentManager().beginTransaction()
                .add(new CryptoFragment(), CRYPTO_FRAGMENT).commit();

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
            currentChatMode = intent.getIntExtra(ChatSessionManager.CHAT_MODE,
                    ChatSessionManager.MC_CHAT);
            mCurrentChatType = intent.getIntExtra(ChatSessionManager.CHAT_MSGTYPE,
                    ChatFragment.MSGTYPE_NORMAL);
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
     * tied to {@link android.app.Activity#onResume() Activity.onResume} of the containing
     * Activity's lifecycle.
     * <p>
     * Set lastSelectedIdx = -1 so {@link #updateSelectedChatInfo(int)} is always executed on
     * onResume
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
     * Indicates the back button has been pressed. Sets the chat pager current item.
     */
    @Override
    public void onBackPressed()
    {
        if (chatPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first step, allow the system to handle the
            // Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed();
        }
        else {
            // Otherwise, select the previous step.
            chatPager.setCurrentItem(chatPager.getCurrentItem() - 1);
        }
    }

    /**
     * Set current chat id handled for this instance.
     *
     * @param chatId
     *         the id of the chat to set.
     */
    private void setCurrentChatId(String chatId)
    {
        currentChatId = chatId;
        ChatSessionManager.setCurrentChatId(chatId);

        selectedChatPanel = ChatSessionManager.getActiveChat(chatId);
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
     * Invoked when the options menu is created. Creates our own options menu from the
     * corresponding xml.
     *
     * @param menu
     *         the options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        this.mMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.chat_menu, menu);

        mCallContact = mMenu.findItem(R.id.call_contact);
        mSendFile = mMenu.findItem(R.id.send_file);
        mSendLocation = mMenu.findItem(R.id.send_location);
        mHistoryErase = mMenu.findItem(R.id.erase_chat_history);
        mDestroyChatRoom = mMenu.findItem(R.id.destroy_chat_room);
        mChatRoomMember = mMenu.findItem(R.id.show_chat_room_occupant);

        setOptionItem();
        return true;
    }

    // Enable option items only applicable to the specific chatSession
    private void setOptionItem()
    {
        if ((mMenu != null) && (selectedChatPanel != null)) {
            ChatSession chatSession = selectedChatPanel.getChatSession();
            mContactSession = (chatSession instanceof MetaContactChatSession);

            if (mContactSession) {
                mDestroyChatRoom.setVisible(false);
                mHistoryErase.setTitle(R.string.service_gui_HISTORY_ERASE_PER_CONTACT);

                // check if to show call buttons.
                Object metaContact = chatSession.getDescriptor();
                MetaContactRenderer contactRenderer = new MetaContactRenderer();
                boolean isShowCall = contactRenderer.isShowCallBtn(metaContact);
                boolean isShowVideoCall = contactRenderer.isShowVideoCallBtn(metaContact);
                mCallContact.setVisible(isShowVideoCall || isShowCall);

                boolean isShowFileSend = contactRenderer.isShowFileSendBtn(metaContact);
                mSendFile.setVisible(isShowFileSend);
                mSendLocation.setVisible(true);
                mChatRoomMember.setVisible(false);
            }
            else {
                mDestroyChatRoom.setVisible(true);
                mHistoryErase.setTitle(R.string.service_gui_CHATROOM_HISTORY_ERASE_PER);
                mChatRoomMember.setVisible(true);
                mCallContact.setVisible(false);
                mSendFile.setVisible(false);
                mSendLocation.setVisible(false);
            }

            MenuItem mPadlock = mMenu.findItem(R.id.otr_padlock);
            if (mPadlock != null) {
                mPadlock.setVisible(mContactSession);
            }
        }
    }

    /**
     * Invoked when an options item has been selected.
     *
     * @param item
     *         the item that has been selected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        Intent intent;
        String selectedChat = chatPagerAdapter.getChatId(chatPager.getCurrentItem());

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.muc_invite:
                ChatInviteDialog inviteDialog = new ChatInviteDialog(this, selectedChatPanel);
                inviteDialog.show();
                return true;

            case R.id.call_contact: // start with voice call
                if (mRecipient != null)
                    AndroidCallUtil.createCall(this, mRecipient.getAddress(),
                            mRecipient.getProtocolProvider(), false);
                return true;

            case R.id.send_location:
                intent = new Intent(this, GeoLocation.class);
                startActivity(intent);
                return true;

            case R.id.gps_tracker:
                intent = new Intent(this, GPSTracker.class);
                startActivity(intent);
                return true;

            case R.id.erase_chat_history:
                EntityListHelper.eraseEntityChatHistory(ChatActivity.this,
                        selectedChatPanel.getChatSession().getDescriptor(), null);
                return true;

            case R.id.destroy_chat_room:
                Object object = selectedChatPanel.getChatSession().getDescriptor();
                if (object instanceof ChatRoomWrapper) {
                    ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
                    ChatRoom chatRoom = chatRoomWrapper.getChatRoom();

                    // Not necessary as destroyChatRoom will also remove muc chatSession
                    // MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
                    // mhs.eraseLocallyStoredHistory(chatRoom, null);

                    AndroidGUIActivator.getMUCService().destroyChatRoom(chatRoomWrapper,
                            "User requested", chatRoom.getIdentifier());
                    ChatSessionManager.removeActiveChat(selectedChatPanel);
                    // It is safer to just finish. see case R.id.close_chat:
                    finish();
                }
                return true;

            case R.id.send_file:
                AttachOptionDialog attachOptionDialog = new AttachOptionDialog(this, mRecipient);
                attachOptionDialog.show();
                return true;

            case R.id.show_chat_room_occupant:
                object = selectedChatPanel.getChatSession().getDescriptor();
                if (object instanceof ChatRoomWrapper) {
                    ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;
                    String memberList = "";
                    List<ChatRoomMember> occupants = chatRoomWrapper.getChatRoom().getMembers();
                    for (ChatRoomMember member : occupants) {
                        ChatRoomMemberJabberImpl occupant = (ChatRoomMemberJabberImpl) member;
                        memberList += occupant.getNickName() + " - "
                                + occupant.getJabberID() + "<br/>";
                    }
                    String user = chatRoomWrapper.getParentProvider().getProtocolProvider()
                            .getAccountID().getUserID();
                    selectedChatPanel.addMessage(user, new Date(), Chat.SYSTEM_MESSAGE,
                            memberList, ChatMessage.ENCODE_HTML);
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
     * @param pos
     *         the new selected position
     * @param posOffset
     *         the offset of the newly selected position
     * @param posOffsetPixels
     *         the offset of the newly selected position in pixels
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
                String mSubTitle = "";
                Iterator<ChatContact<?>> mParticipants = ccSession.getParticipants();
                while (mParticipants.hasNext()) {
                    mSubTitle += mParticipants.next().getName() + ", ";
                }
                ActionBarUtil.setSubtitle(this, mSubTitle);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            String filePath = "";

            switch (requestCode) {
                case SELECT_PHOTO:
                case SELECT_VIDEO:
                    Uri selectedImage = data.getData();
                    try {
                        filePath = FileAccess.getPath(this, selectedImage);
                        if (!StringUtils.isNullOrEmpty(filePath))
                            sendFile(filePath);
                    } catch (URISyntaxException e) {
                        logger.error("Choose attachment error: " + e.getMessage());
                    }
                    break;

                case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
                case CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE:
                    if (mCameraFilePath.length() > 0 && FileAccess.IsFileExist(mCameraFilePath)) {
                        filePath = mCameraFilePath;
                        if (!StringUtils.isNullOrEmpty(filePath)) {
                            mCameraFilePath = "";
                            sendFile(filePath);
                        }
                    }
                    break;

                case CHOOSE_FILE_ACTIVITY_REQUEST_CODE:
                    if (data != null) {
                        Uri uri = data.getData();
                        try {
                            filePath = FileAccess.getPath(this, uri);
                            if (!StringUtils.isNullOrEmpty(filePath))
                                sendFile(filePath);
                        } catch (URISyntaxException e) {
                            logger.error("Choose file error: " + e.getMessage());
                        }
                    }
                    break;

                case OPEN_FILE_REQUEST_CODE:
                    if (data != null) {
                        Uri uri = data.getData();
                        try {
                            filePath = FileAccess.getPath(this, uri);
                            if (!StringUtils.isNullOrEmpty(filePath))
                                openFile(new File(filePath));
                        } catch (URISyntaxException e) {
                            logger.error("File open error: " + e.getMessage());
                        }
                    }
                    break;
            }
        }
    }

    public void sendFile(String filePath)
    {
        final ChatPanel chatPanel;
        Date date = Calendar.getInstance().getTime();
        UIService uiService = AndroidGUIActivator.getUIService();
        String sendTo = mRecipient.getAddress();

        if ((mRecipient != null) && mRecipient instanceof Contact) {
            if (uiService != null) {
                chatPanel = (ChatPanel) uiService.getChat(mRecipient);
                if (chatPanel != null) {
                    chatPanel.addMessage(sendTo, date, ChatPanel.OUTGOING_FILE_MESSAGE,
                            filePath, ChatMessage.ENCODE_PLAIN);
                }
            }
        }
    }

    public static void sendLocation(String location)
    {
        final ChatPanel chatPanel;
        UIService uiService = AndroidGUIActivator.getUIService();
        String sendTo = mRecipient.getAddress();

        if (mRecipient instanceof Contact) {
            if (uiService != null) {
                chatPanel = (ChatPanel) uiService.getChat(mRecipient);
                if (chatPanel != null) {
                    ChatTransport mChatTransport = chatPanel.getChatSession().getCurrentChatTransport();
                    try {
                        mChatTransport.sendInstantMessage(location, ChatMessage.ENCODE_PLAIN);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void sendAttachment(AttachOptionItem attachOptionItem, final Contact contact)
    {
        mRecipient = contact;
        Intent intent;
        Uri fileUri;

        switch (attachOptionItem) {
            case pic:
                intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"),
                        SELECT_PHOTO);
                break;

            case camera:
                // create Intent to take a picture and return control to the calling application
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                // create a file to save the image
                mCameraFilePath = CameraAccess.getOutputMediaFilePath(
                        CameraAccess.MEDIA_TYPE_IMAGE);
                fileUri = Uri.fromFile(new File(mCameraFilePath));
                // set the image file name for camera service to save file
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                // start the image capture Intent
                startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
                break;

            case video:
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(intent, SELECT_VIDEO);
                break;

            case video_record:
                // create Intent to take a picture and return control to the calling application
                intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                // create a file to save the image
                mCameraFilePath = CameraAccess.getOutputMediaFilePath(
                        CameraAccess.MEDIA_TYPE_VIDEO);
                fileUri = Uri.fromFile(new File(mCameraFilePath));
                // set the image file name
                intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                // start the image capture Intent
                startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
                break;

            case share_file:
                Intent chooseFile;
                chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
                chooseFile.setType("*/*");
                chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    intent = Intent.createChooser(chooseFile,
                            getString(R.string.choose_file_activity_title));
                    startActivityForResult(intent, CHOOSE_FILE_ACTIVITY_REQUEST_CODE);
                } catch (android.content.ActivityNotFoundException ex) {
                    // Potentially direct the user to the Market with a Dialog
                    showToastMessage("Please install a File Manager.");
                }
                break;
        }
    }

    public void openFolder(File dirFolder)
    {
        try {
            Intent pickFile = new Intent(Intent.ACTION_GET_CONTENT);
            Uri uri = Uri.parse(dirFolder.toString());
            pickFile.setDataAndType(uri, "*/*");
            pickFile.addCategory(Intent.CATEGORY_OPENABLE);

            Intent intent = Intent.createChooser(pickFile, "Open folder");
            startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);

        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            showToastMessage("Please install a File Manager.");
        } catch (IllegalArgumentException | NullPointerException e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open folder.", e);

            showToastMessage(R.string.service_gui_FOLDER_DOES_NOT_EXIST);
        } catch (UnsupportedOperationException e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open folder.", e);

            showToastMessage(R.string.service_gui_FILE_OPEN_NOT_SUPPORTED);
        } catch (SecurityException e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open folder.", e);

            showToastMessage(R.string.service_gui_FOLDER_OPEN_NO_PERMISSION);
        } catch (Exception e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open file.", e);

            showToastMessage(R.string.service_gui_FOLDER_OPEN_FAILED);
        }
    }

    /**
     * Opens the given file through the <tt>DesktopService</tt>.
     *
     * @param file
     *         the file to open
     */
    public void openFile(File file)
    {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.fromFile(file);

            // use uri.toString to take care of filename with space
            String mimeType = getMimeType(uri.toString());
            if (mimeType != null && file != null) {
                if (mimeType.contains("3gp")) {
                    intent = new Intent(this, AudioBgService.class);
                    intent.setAction(AudioBgService.ACTION_PLAYBACK);
                    intent.putExtra(AudioBgService.URI, uri.toString());
                    startService(intent);
                    // if (filename.startsWith("voice-"))
                }
                else {
                    intent.setDataAndType(uri, mimeType);
                    startActivity(intent);
                }
            }
            else {
                showToastMessage(R.string.service_gui_FILE_OPEN_NO_APPLICATION);
            }
        } catch (IllegalStateException e) {
            if (logger.isDebugEnabled())
                logger.debug("Fragment ReceiveFileConversation not attached to Activity.", e);
        } catch (IllegalArgumentException | NullPointerException e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open file.", e);

            showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
        } catch (UnsupportedOperationException e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open file.", e);

            showToastMessage(R.string.service_gui_FILE_OPEN_NOT_SUPPORTED);
        } catch (SecurityException e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open file.", e);

            showToastMessage(R.string.service_gui_FILE_OPEN_NO_PERMISSION);
        } catch (Exception e) {
            if (logger.isDebugEnabled())
                logger.debug("Unable to open file.", e);

            // showErrorMessage(R.string.service_gui_FILE_OPEN_FAILED);
            showToastMessage(R.string.service_gui_FILE_OPEN_FAILED);
        }
    }

    /**
     * Shows the given error message in the error area of this component.
     *
     * @param resId
     *         the Id of the message to show
     */
    private void showToastMessage(int resId)
    {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void showToastMessage(String message)
    {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
