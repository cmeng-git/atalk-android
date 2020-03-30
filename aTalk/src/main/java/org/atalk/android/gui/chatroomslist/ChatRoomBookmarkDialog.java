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
package org.atalk.android.gui.chatroomslist;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiDialogFragment;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * The chatRoom Bookmarks dialog is the one shown when the user clicks on the Bookmarks option in the main menu.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomBookmarkDialog extends OSGiDialogFragment
{
    private MUCServiceImpl mucService;
    private ChatRoomWrapper mChatRoomWrapper;
    private OnFinishedCallback finishedCallback = null;

    /**
     * The account list view.
     */
    private TextView mAccount;
    private TextView mChatRoom;

    private EditText mucNameField;
    private EditText nicknameField;
    private CheckBox mAutoJoin;
    private CheckBox mBookmark;

    private EditText mPasswordField;

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * @param chatRoomWrapper the <tt>ChatRoomWrapper</tt> whom attributes are to be modified.
     * @param callback to be call on dialog closed.
     */
    public static ChatRoomBookmarkDialog getInstance(ChatRoomWrapper chatRoomWrapper, OnFinishedCallback callback)
    {
        ChatRoomBookmarkDialog dialog = new ChatRoomBookmarkDialog();
        dialog.mucService = MUCActivator.getMUCService();
        dialog.mChatRoomWrapper = chatRoomWrapper;
        dialog.finishedCallback = callback;

        Bundle args = new Bundle();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (getDialog() != null)
            getDialog().setTitle(R.string.service_gui_CHATROOM_BOOKMARK_TITLE);

        View contentView = inflater.inflate(R.layout.chatroom_bookmark, container, false);

        mAccount = contentView.findViewById(R.id.jid_account);
        mucNameField = contentView.findViewById(R.id.mucName_Edit);

        nicknameField = contentView.findViewById(R.id.nickName_Edit);
        mAutoJoin = contentView.findViewById(R.id.cb_autojoin);
        mBookmark = contentView.findViewById(R.id.cb_bookmark);

        mPasswordField = contentView.findViewById(R.id.passwordField);
        CheckBox mShowPasswordCheckBox = contentView.findViewById(R.id.show_password);
        mShowPasswordCheckBox.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(mPasswordField, isChecked));

        mChatRoom = contentView.findViewById(R.id.jid_chatroom);
        initBookmarkedConference();

        Button mApplyButton = contentView.findViewById(R.id.button_Apply);
        mApplyButton.setOnClickListener(v -> {
            if (updateBookmarkedConference())
                closeDialog();
        });

        Button mCancelButton = contentView.findViewById(R.id.button_Cancel);
        mCancelButton.setOnClickListener(v -> closeDialog());

        setCancelable(false);
        return contentView;
    }

    /**
     * Creates the providers comboBox and filling its content with the current available chatRooms
     * Add available server chatRooms to the chatRoomList when providers changes
     */
    private void initBookmarkedConference()
    {
        ProtocolProviderService pps = mChatRoomWrapper.getProtocolProvider();
        String accountId = pps.getAccountID().getAccountJid();
        // getNickName() always returns a valid or default nickname string
        String nickName = mChatRoomWrapper.getNickName();

        mAccount.setText(accountId);
        mucNameField.setText(mChatRoomWrapper.getBookmarkName());
        nicknameField.setText(nickName);
        mPasswordField.setText(mChatRoomWrapper.loadPassword());
        mChatRoom.setText(mChatRoomWrapper.getEntityBareJid().toString());

        mAutoJoin.setChecked(mChatRoomWrapper.isAutoJoin());
        mBookmark.setChecked(mChatRoomWrapper.isBookmarked());
    }

    private void closeDialog()
    {
        if (finishedCallback != null)
            finishedCallback.onCloseDialog();
        dismiss();
    }

    /**
     * Update the bookmarks on server.
     */
    private boolean updateBookmarkedConference()
    {
        List<EntityBareJid> bookmarkedEntityList = new ArrayList<>();
        boolean success = true;

        ProtocolProviderService pps = mChatRoomWrapper.getProtocolProvider();
        BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());

        try {
            List<BookmarkedConference> bookmarkedList = bookmarkManager.getBookmarkedConferences();
            for (BookmarkedConference bookmarkedConference : bookmarkedList) {
                bookmarkedEntityList.add(bookmarkedConference.getJid());
            }

            String name = ViewUtil.toString(mucNameField);
            String nickStr = ViewUtil.toString(nicknameField);
            Resourcepart nickName = (nickStr == null) ? null : Resourcepart.from(nickStr);
            String password = ViewUtil.toString(mPasswordField);

            boolean autoJoin = mAutoJoin.isChecked();
            boolean bookmark = mBookmark.isChecked();
            EntityBareJid chatRoomEntity = mChatRoomWrapper.getEntityBareJid();

            // Update server bookmark
            if (bookmark) {
                bookmarkManager.addBookmarkedConference(name, chatRoomEntity, autoJoin, nickName, password);
            }
            else if (bookmarkedEntityList.contains(chatRoomEntity)) {
                bookmarkManager.removeBookmarkedConference(chatRoomEntity);
            }

            mChatRoomWrapper.setBookmarkName(name);
            mChatRoomWrapper.savePassword(password);
            mChatRoomWrapper.setNickName(nickStr);
            mChatRoomWrapper.setBookmark(bookmark);
            mChatRoomWrapper.setAutoJoin(autoJoin);

            // save info to local chatRoomWrapper
            byte[] pwd = (password == null) ? null : password.getBytes();
            if (autoJoin) {
                mucService.joinChatRoom(mChatRoomWrapper, nickStr, pwd);
            }
        } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                | XMPPException.XMPPErrorException | InterruptedException | XmppStringprepException e) {
            String errMag = aTalkApp.getResString(R.string.service_gui_CHATROOM_BOOKMARK_UPDATE_FAILED,
                    mChatRoomWrapper, e.getMessage());
            Timber.w(errMag);
            aTalkApp.showToastMessage(errMag);
            success = false;
        }
        return success;
    }

    public interface OnFinishedCallback
    {
        void onCloseDialog();
    }
}
