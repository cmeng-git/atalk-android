/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.chatroomslist;

import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.util.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.util.*;

import timber.log.Timber;

/**
 * The chatRoom Bookmarks dialog is the one shown when the user clicks on the Bookmarks option in the main menu.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomBookmarksDialog extends Dialog implements OnItemSelectedListener, DialogActivity.DialogListener
{
    private final MainMenuActivity mParent;
    private final MUCServiceImpl mucService;

    /**
     * The account list view.
     */
    private Spinner accountsSpinner;
    private Spinner chatRoomSpinner;

    private EditText mucNameField;
    private EditText nicknameField;
    private CheckBox mAutoJoin;
    private CheckBox mBookmark;

    private EditText mPasswordField;
    private ImageView mShowPasswordImage;

    private boolean hasChanges = false;

    /**
     * current bookmark view in focus that the user see
     */
    private BookmarkConference mBookmarkFocus = null;

    /**
     * A map of <account Jid, List<BookmarkConference>>
     */
    private Map<String, List<BookmarkConference>> mAccountBookmarkConferencesList = new LinkedHashMap<>();

    /**
     * A map of <RoomJid, BookmarkConference> retrieved from mAccountBookmarkConferencesList
     */
    private Map<String, BookmarkConference> mBookmarkConferenceList = new LinkedHashMap<>();

    /**
     * A map of <JID, ChatRoomProviderWrapper>
     */
    private Map<String, ChatRoomProviderWrapper> mucRoomWrapperList = new LinkedHashMap<>();

    private List<String> mChatRoomList;

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * @param mContext the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is invited.
     */
    public ChatRoomBookmarksDialog(Context mContext)
    {
        super(mContext);
        mParent = (MainMenuActivity) mContext;
        mucService = MUCActivator.getMUCService();
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle(R.string.service_gui_CHATROOM_BOOKMARK_TITLE);
        this.setContentView(R.layout.chatroom_bookmarks);

        accountsSpinner = this.findViewById(R.id.jid_Accounts_Spinner);
        initAccountSpinner();

        mucNameField = this.findViewById(R.id.mucName_Edit);
        mucNameField.setText("");
        nicknameField = this.findViewById(R.id.nickName_Edit);
        mAutoJoin = this.findViewById(R.id.cb_autojoin);
        mBookmark = this.findViewById(R.id.cb_bookmark);

        mPasswordField = this.findViewById(R.id.passwordField);
        mShowPasswordImage = this.findViewById(R.id.pwdviewImage);
        CheckBox mShowPasswordCheckBox = this.findViewById(R.id.show_password);
        mShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> showPassword(isChecked));

        chatRoomSpinner = this.findViewById(R.id.chatRoom_Spinner);
        // chatRoomSpinner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        new initBookmarkedConference().execute();

        Button mApplyButton = this.findViewById(R.id.button_Apply);
        mApplyButton.setOnClickListener(v -> {
            if (updateBookmarkedConference())
                closeDialog();
        });

        Button mCancelButton = this.findViewById(R.id.button_Cancel);
        mCancelButton.setOnClickListener(v -> {
            if (hasChanges) {
                DialogActivity.showConfirmDialog(mParent,
                        aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_TITLE),
                        aTalkApp.getResString(R.string.service_gui_UNSAVED_CHANGES),
                        aTalkApp.getResString(R.string.service_gui_EXIT), this);
            }
            else
                closeDialog();
        });
        setCancelable(false);
    }

    @Override
    public boolean onConfirmClicked(DialogActivity dialog)
    {
        closeDialog();
        return true;
    }

    @Override
    public void onDialogCancelled(DialogActivity dialog)
    {
    }

    // add items into accountsSpinner dynamically
    private void initAccountSpinner()
    {
        String mAccount;
        List<String> ppsList = new ArrayList<>();

        List<ChatRoomProviderWrapper> providers = mucService.getChatRoomProviders();
        for (ChatRoomProviderWrapper provider : providers) {
            mAccount = provider.getProtocolProvider().getAccountID().getAccountJid();
            mucRoomWrapperList.put(mAccount, provider);
            ppsList.add(mAccount);
        }

        // Create an ArrayAdapter using the string array and aTalk default spinner layout
        ArrayAdapter<String> mAdapter = new ArrayAdapter<>(mParent, R.layout.simple_spinner_item, ppsList);
        // Specify the layout to use when the list of choices appears
        mAdapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        accountsSpinner.setAdapter(mAdapter);
        accountsSpinner.setOnItemSelectedListener(this);
    }


    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms
     * Add available server chatRooms to the chatRoomList when providers changes
     */
    private class initBookmarkedConference extends AsyncTask<Void, Void, Void>
    {
        List<BookmarkedConference> bookmarkedList = new ArrayList<>();
        List<BookmarkConference> bookmarkList = new ArrayList<>();
        BookmarkConference bookmarkConference;

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            List<ChatRoomProviderWrapper> crpWrappers = mucService.getChatRoomProviders();

            for (ChatRoomProviderWrapper crpWrapper : crpWrappers) {
                if (crpWrapper != null) {
                    ProtocolProviderService pps = crpWrapper.getProtocolProvider();
                    BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());
                    String mAccount = pps.getAccountID().getAccountJid();

                    // local chatRooms
                    List<String> chatRoomList = mucService.getExistingChatRooms(pps);

                    // server chatRooms
                    List<String> sChatRoomList = mucService.getExistingChatRooms(crpWrapper);
                    for (String sRoom : sChatRoomList) {
                        if (!chatRoomList.contains(sRoom))
                            chatRoomList.add(sRoom);
                    }

                    try {
                        // Fetch all the bookmarks from server
                        bookmarkedList = bookmarkManager.getBookmarkedConferences();

                        // Remove bookmarked chat rooms from chatRoomList
                        for (BookmarkedConference bookmarkedConference : bookmarkedList) {
                            chatRoomList.remove(bookmarkedConference.getJid().toString());

                            bookmarkConference = new BookmarkConference(bookmarkedConference);
                            bookmarkConference.setBookmark(true);
                            bookmarkList.add(bookmarkConference);
                        }

                    } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                            | XMPPException.XMPPErrorException | InterruptedException e) {
                        Timber.w("Failed to fetch Bookmarks: %s", e.getMessage());
                    }

                    if (chatRoomList.size() > 0) {
                        String mNickName = getDefaultNickname(pps);

                        for (String chatRoom : chatRoomList) {
                            ChatRoomWrapper chatRoomWrapper = mucService.findChatRoomWrapperFromChatRoomID(chatRoom, pps);
                            boolean isAutoJoin = (chatRoomWrapper != null) && chatRoomWrapper.isAutoJoin();
                            String nickName = (chatRoomWrapper != null) ? chatRoomWrapper.getNickName() : mNickName;
                            String name = (chatRoomWrapper != null) ? chatRoomWrapper.getBookmarkName() : "";

                            try {
                                EntityBareJid entityBareJid = JidCreate.entityBareFrom(chatRoom);

                                bookmarkConference = new BookmarkConference(name, entityBareJid, isAutoJoin,
                                        Resourcepart.from(nickName), "");
                                bookmarkConference.setBookmark(false);
                                bookmarkList.add(bookmarkConference);
                            } catch (XmppStringprepException e) {
                                Timber.w("Failed to add Bookmark for %s: %s", chatRoom, e.getMessage());
                            }
                        }
                    }
                    mAccountBookmarkConferencesList.put(mAccount, bookmarkList);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            if ((mAccountBookmarkConferencesList != null) && (mAccountBookmarkConferencesList.size() > 0)) {
                Object[] keySet = mAccountBookmarkConferencesList.keySet().toArray();

                if ((keySet != null) && (keySet.length > 0)) {
                    String accountId = (String) keySet[0];
                    if (!StringUtils.isNullOrEmpty(accountId))
                        initChatRoomSpinner(accountId);
                }
            }
        }
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms
     * Add available server chatRooms to the chatRoomList when providers changes
     */
    private void initChatRoomSpinner(String accountId)
    {
        mChatRoomList = new ArrayList<>();
        List<BookmarkConference> mBookmarkConferences = mAccountBookmarkConferencesList.get(accountId);

        if (mBookmarkConferences != null) {
            for (BookmarkConference bookmarkConference : mBookmarkConferences) {
                String chatRoom = bookmarkConference.getJid().toString();
                mChatRoomList.add(chatRoom);
                mBookmarkConferenceList.put(chatRoom, bookmarkConference);
            }
        }

        // Create an ArrayAdapter using the string array and aTalk default spinner layout
        ArrayAdapter<String> mAdapter = new ArrayAdapter<>(mParent, R.layout.simple_spinner_item, mChatRoomList);
        // Apply the adapter to the spinner
        chatRoomSpinner.setAdapter(mAdapter);
        chatRoomSpinner.setOnItemSelectedListener(this);

        if (mChatRoomList.size() > 0) {
            String chatRoom = mChatRoomList.get(0);
            initBookMarkForm(chatRoom);
        }
    }

    private void closeDialog()
    {
        this.cancel();
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id)
    {
        switch (adapter.getId()) {
            case R.id.jid_Accounts_Spinner:
                String userId = (String) adapter.getItemAtPosition(pos);
                ChatRoomProviderWrapper protocol = mucRoomWrapperList.get(userId);

                ProtocolProviderService pps = (protocol == null) ? null : protocol.getProtocolProvider();
                if (pps != null) {
                    mBookmarkFocus = null;
                    String accountId = pps.getAccountID().getAccountJid();
                    initChatRoomSpinner(accountId);
                }
                break;

            case R.id.chatRoom_Spinner:
                String oldChatRoom = (mBookmarkFocus != null) ? mBookmarkFocus.getJid().toString() : "";
                String chatRoom = (String) adapter.getItemAtPosition(pos);
                if (!initBookMarkForm(chatRoom)) {
                    chatRoomSpinner.setSelection(mChatRoomList.indexOf(oldChatRoom));
                }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {
        // Another interface callback
    }

    /**
     * Sets the default value in the nickname field based on pps.
     *
     * @param pps the ProtocolProviderService
     */
    private String getDefaultNickname(ProtocolProviderService pps)
    {
        String nickName = AndroidGUIActivator.getGlobalDisplayDetailsService().getDisplayName(pps);
        if ((nickName == null) || nickName.contains("@"))
            nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());

        return nickName;
    }

    /**
     * Sets the value of chat room name field.
     *
     * @param chatRoom the chat room name.
     */
    private boolean initBookMarkForm(String chatRoom)
    {
        if (!updateBookmarkFocus()) {
            return false;
        }

        mBookmarkFocus = mBookmarkConferenceList.get(chatRoom);
        mucNameField.setText(mBookmarkFocus.getName());
        nicknameField.setText(mBookmarkFocus.getNickname());
        mPasswordField.setText(mBookmarkFocus.getPassword());
        mAutoJoin.setChecked(mBookmarkFocus.isAutoJoin());
        mBookmark.setChecked(mBookmarkFocus.isBookmark());
        return true;
    }

    private boolean updateBookmarkFocus()
    {
        if (mBookmarkFocus != null) {
            hasChanges = !(mBookmarkFocus.getName().equals(mucNameField.getText().toString())
                    && mBookmarkFocus.getNickname().toString().equals(nicknameField.getText().toString())
                    && mBookmarkFocus.getPassword().equals(mPasswordField.getText().toString())
                    && mBookmarkFocus.isAutoJoin() == mAutoJoin.isChecked()
                    && mBookmarkFocus.isBookmark() == mBookmark.isChecked());

            // Timber.w("Fields have changes: %s", hasChanges);
            if (hasChanges) {
                mBookmarkFocus.setName(mucNameField.getText().toString());
                mBookmarkFocus.setPassword(mPasswordField.getText().toString());
                mBookmarkFocus.setAutoJoin(mAutoJoin.isChecked());
                mBookmarkFocus.setBookmark(mBookmark.isChecked());

                try {
                    // nickName cannot be null => exception
                    mBookmarkFocus.setNickname(Resourcepart.from(nicknameField.getText().toString()));
                } catch (XmppStringprepException e) {
                    aTalkApp.showToastMessage(R.string.service_gui_CHANGE_NICKNAME_ERROR,
                            mBookmarkFocus.getJid(), e.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Update the bookmarks on server.
     */
    private boolean updateBookmarkedConference()
    {
        boolean success = true;
        List<BookmarkedConference> bookmarkedList;
        List<EntityBareJid> bookmarkedEntityList = new ArrayList<>();

        // Update the last user change bookmarkFocus
        if (!updateBookmarkFocus())
            return false;

        List<ChatRoomProviderWrapper> crpWrappers = mucService.getChatRoomProviders();
        for (ChatRoomProviderWrapper crpWrapper : crpWrappers) {
            if (crpWrapper != null) {
                ProtocolProviderService pps = crpWrapper.getProtocolProvider();
                String accountId = pps.getAccountID().getAccountJid();
                List<BookmarkConference> mBookmarkConferences = mAccountBookmarkConferencesList.get(accountId);

                BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());
                ChatRoomWrapper chatRoomWrapper = null;
                try {
                    bookmarkedList = bookmarkManager.getBookmarkedConferences();
                    for (BookmarkedConference bookmarkedConference : bookmarkedList) {
                        bookmarkedEntityList.add(bookmarkedConference.getJid());
                    }

                    if (mBookmarkConferences != null) {
                        for (BookmarkConference bookmarkConference : mBookmarkConferences) {
                            boolean autoJoin = bookmarkConference.isAutoJoin();
                            boolean bookmark = bookmarkConference.isBookmark();
                            String name = bookmarkConference.getName();
                            String password = bookmarkConference.getPassword();
                            Resourcepart nick = bookmarkConference.getNickname();
                            EntityBareJid chatRoomEntity = bookmarkConference.getJid();

                            // Update server bookmark
                            if (bookmark) {
                                bookmarkManager.addBookmarkedConference(name, chatRoomEntity, autoJoin, nick, password);
                            }
                            else if (bookmarkedEntityList.contains(chatRoomEntity)) {
                                bookmarkManager.removeBookmarkedConference(chatRoomEntity);
                            }

                            if (autoJoin) {
                                mucService.joinChatRoom(chatRoomEntity.toString(), crpWrapper);
                                // nick???
                            }

                            // save info to local chatRoomWrapper if present
                            chatRoomWrapper = crpWrapper.findChatRoomWrapperForChatRoomID(chatRoomEntity.toString());

                            // Create new chatRoom if none and user enabled autoJoin
//                            if (autoJoin) {
//                                mucService.joinChatRoom(chatRoomWrapper);
//
//                                chatRoomWrapper = createChatRoom(pps, bookmarkConference);
//                                if (chatRoomWrapper != null)
//                                    crpWrapper.addChatRoom(chatRoomWrapper);
//                            }

                            if (chatRoomWrapper != null) {
                                chatRoomWrapper.setBookmarkName(name);
                                chatRoomWrapper.savePassword(password);
                                chatRoomWrapper.setNickName(nick.toString());
                                chatRoomWrapper.setBookmark(bookmark);
                                chatRoomWrapper.setAutoJoin(autoJoin);

                                // cmeng - testing for clearing unwanted values i.e. MUCService.OPEN_ON_ACTIVITY
                                // MUCService.setChatRoomAutoOpenOption(pps, chatRoomEntity.toString(), null);
                            }
                        }
                    }
                } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                        | XMPPException.XMPPErrorException | InterruptedException e) {
                    String errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                            chatRoomWrapper, e.getMessage());
                    Timber.w(errMag);
                    aTalkApp.showToastMessage(errMag);
                    success = false;
                }
            }
        }
        return success;
    }

//    private ChatRoomWrapper createChatRoom(ProtocolProviderService pps, BookmarkConference bookmarkConference)
//    {
//        String chatRoomID = bookmarkConference.getJid().toString();
//        String nickName = bookmarkConference.getNickname().toString();
//        String password = bookmarkConference.getPassword();
//        Collection<String> contacts = new ArrayList<>();
//        String reason = "Let's chat";
//        boolean createNew = false;
//
//        // create new if chatRoom does not exist
//        ChatRoomWrapper chatRoomWrapper = mucService.findChatRoomWrapperFromChatRoomID(chatRoomID, pps);
//        if (chatRoomWrapper == null) {
//            createNew = true;
//            chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
//                    reason, true, false, false);
//            // In case the protocol failed to create a chat room (null), then return without open the chat room.
//            if (chatRoomWrapper == null) {
//                aTalkApp.showToastMessage(R.string.service_gui_CREATE_CHAT_ROOM_ERROR, chatRoomID);
//                return null;
//            }
//
//            ConfigurationUtils.saveChatRoom(pps, chatRoomID, chatRoomID);
//
//            // Allow to remove new chatRoom if join failed
//            if (AndroidGUIActivator.getConfigurationService()
//                    .getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
//                final ChatRoomWrapper crWrapper = chatRoomWrapper;
//
//                chatRoomWrapper.addPropertyChangeListener(evt -> {
//                    if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP)) {
//                        return;
//                    }
//
//                    // if we failed for some reason, then close and remove the room
//                    AndroidGUIActivator.getUIService().closeChatRoomWindow(crWrapper);
//                    AndroidGUIActivator.getMUCService().removeChatRoom(crWrapper);
//                });
//            }
//        }
//        if (!createNew) {
//            // Set chatRoom openAutomatically on_activity
//            // MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);
//            mucService.joinChatRoom(chatRoomWrapper, nickName, password.getBytes());
//            Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
//            mParent.startActivity(chatIntent);
//        }
//        return chatRoomWrapper;
//    }

    private void showPassword(boolean show)
    {
        int cursorPosition = mPasswordField.getSelectionStart();
        if (show) {
            mShowPasswordImage.setAlpha(1.0f);
            mPasswordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        else {
            mShowPasswordImage.setAlpha(0.3f);
            mPasswordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        mPasswordField.setSelection(cursorPosition);
    }
}
