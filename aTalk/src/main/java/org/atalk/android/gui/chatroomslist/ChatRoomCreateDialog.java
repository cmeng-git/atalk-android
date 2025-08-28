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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.muc.MUCService;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.android.gui.util.ComboBox;
import org.atalk.android.gui.util.ViewUtil;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.util.XmppStringUtils;

import timber.log.Timber;

/**
 * The invite dialog is the one shown when the user clicks on the conference option in the Contact List toolbar.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomCreateDialog extends Dialog implements OnItemSelectedListener, AdapterView.OnItemClickListener {
    private static final String CHATROOM = "chatroom";

    private final MainMenuActivity mParent;
    private final MUCServiceImpl mucService;

    /**
     * The account list view.
     */
    private Spinner accountsSpinner;
    private ComboBox chatRoomComboBox;

    private EditText subjectField;
    private EditText nicknameField;
    private EditText passwordField;
    private CheckBox mSavePasswordCheckBox;
    private Button mJoinButton;

    /**
     * A map of <JID, ChatRoomProviderWrapper>
     */
    private final Map<String, ChatRoomProviderWrapper> mucRCProviderList = new LinkedHashMap<>();

    private List<String> chatRoomList = new ArrayList<>();

    private final Map<String, ChatRoomWrapper> chatRoomWrapperList = new LinkedHashMap<>();

    /**
     * Constructs the <code>ChatInviteDialog</code>.
     *
     * @param mContext the <code>ChatPanel</code> corresponding to the <code>ChatRoom</code>, where the contact is invited.
     */
    public ChatRoomCreateDialog(Context mContext) {
        super(mContext);
        mParent = (MainMenuActivity) mContext;
        mucService = MUCActivator.getMUCService();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.chatroom_create_join);
        this.setContentView(R.layout.muc_room_create_dialog);

        nicknameField = findViewById(R.id.NickName_Edit);
        passwordField = findViewById(R.id.passwordField);
        CheckBox showPasswordCB = findViewById(R.id.show_password);
        showPasswordCB.setOnCheckedChangeListener((buttonView, isChecked)
                -> ViewUtil.showPassword(passwordField, isChecked));
        mSavePasswordCheckBox = findViewById(R.id.store_password);

        subjectField = findViewById(R.id.chatRoom_Subject_Edit);
        subjectField.setText("");
        findViewById(R.id.subject_clear).setOnClickListener(v -> subjectField.setText(""));

        chatRoomComboBox = findViewById(R.id.chatRoom_Combo);
        chatRoomComboBox.setOnItemClickListener(this);
        new InitComboBox().execute();

        accountsSpinner = findViewById(R.id.jid_Accounts_Spinner);
        // Init AccountSpinner only after initComboBox(), else onItemSelected() will get trigger.
        initAccountSpinner();

        mJoinButton = findViewById(R.id.button_Join);
        mJoinButton.setOnClickListener(v -> {
            if (createOrJoinChatRoom())
                closeDialog();
        });

        findViewById(R.id.button_Cancel).setOnClickListener(v -> closeDialog());
        setCanceledOnTouchOutside(false);
    }

    // add items into accountsSpinner dynamically
    private void initAccountSpinner() {
        String mAccount;
        List<String> ppsList = new ArrayList<>();

        List<ChatRoomProviderWrapper> providers = mucService.getChatRoomProviders();
        for (ChatRoomProviderWrapper provider : providers) {
            mAccount = provider.getProtocolProvider().getAccountID().getDisplayName();
            mucRCProviderList.put(mAccount, provider);
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
     * Creates the providers comboBox and filling its content with the current available chatRooms.
     * Add all available server's chatRooms to the chatRoomList when providers changed.
     */
    private class InitComboBox {
        public void execute() {

            try (ExecutorService eService = Executors.newSingleThreadExecutor()) {
                eService.execute(() -> {
                    final List<String> chatRoomList = doInBackground();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (chatRoomList.isEmpty())
                            chatRoomList.add(CHATROOM);

                        chatRoomComboBox.setText(chatRoomList.get(0));
                        // Must do this after setText as it clear the list; otherwise only one item in the list
                        chatRoomComboBox.setSuggestionSource(chatRoomList);

                        // Update the dialog form fields with all the relevant values, for first chatRoomWrapperList entry if available.
                        if (!chatRoomWrapperList.isEmpty())
                            onItemClick(null, chatRoomComboBox, 0, 0);
                    });
                });
            }
        }

        private List<String> doInBackground() {
            chatRoomList.clear();
            chatRoomWrapperList.clear();

            ChatRoomProviderWrapper crpWrapper = getSelectedProvider();
            if (crpWrapper != null) {
                ProtocolProviderService pps = crpWrapper.getProtocolProvider();

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
            return chatRoomList;
        }
    }

    private void closeDialog() {
        this.cancel();
    }

    /**
     * Updates the enable/disable state of the OK button.
     */
    private void updateJoinButtonEnableState() {
        String nickName = ViewUtil.toString(nicknameField);
        String chatRoomField = chatRoomComboBox.getText();

        boolean mEnable = ((chatRoomField != null) && (nickName != null) && (getSelectedProvider() != null));
        if (mEnable) {
            mJoinButton.setEnabled(true);
            mJoinButton.setAlpha(1.0f);
        }
        else {
            mJoinButton.setEnabled(false);
            mJoinButton.setAlpha(0.5f);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id) {
        new InitComboBox().execute();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    /**
     * Callback method to be invoked when an item in this AdapterView i.e. comboBox has been clicked.
     *
     * @param parent The AdapterView where the click happened.
     * @param view The view within the AdapterView that was clicked (this will be a view provided by the adapter)
     * @param position The position of the view in the adapter.
     * @param id The row id of the item that was clicked.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ChatRoomWrapper chatRoomWrapper = chatRoomWrapperList.get(chatRoomList.get(position));
        if (chatRoomWrapper != null) {
            // Timber.d("ComboBox Item clicked: %s; %s", position, chatRoomWrapper.getChatRoomName());

            String pwd = chatRoomWrapper.loadPassword();
            passwordField.setText(pwd);
            mSavePasswordCheckBox.setChecked(!TextUtils.isEmpty(pwd));

            ChatRoom chatroom = chatRoomWrapper.getChatRoom();
            if (chatroom != null) {
                subjectField.setText(chatroom.getSubject());
            }
        }
        // chatRoomWrapper can be null, so always setDefaultNickname()
        setDefaultNickname();
    }

    /**
     * Sets the default value in the nickname field based on selected chatRoomWrapper stored value of PPS
     */
    private void setDefaultNickname() {
        String chatRoom = chatRoomComboBox.getText();
        if (chatRoom != null) {
            chatRoom = chatRoom.replaceAll("\\s", "");
        }
        ChatRoomWrapper chatRoomWrapper = chatRoomWrapperList.get(chatRoom);

        String nickName = null;
        if (chatRoomWrapper != null) {
            nickName = chatRoomWrapper.getNickName();
        }

        if (TextUtils.isEmpty(nickName) && (getSelectedProvider() != null)) {
            ProtocolProviderService pps = getSelectedProvider().getProtocolProvider();
            if (pps != null) {
                nickName = AppGUIActivator.getGlobalDisplayDetailsService().getDisplayName(pps);
                if ((nickName == null) || nickName.contains("@"))
                    nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());
            }
        }
        nicknameField.setText(nickName);
        updateJoinButtonEnableState();
    }

    /**
     * Sets the (chat room) subject to be displayed in this <code>ChatRoomSubjectPanel</code>.
     *
     * @param subject the (chat room) subject to be displayed in this <code>ChatRoomSubjectPanel</code>
     */
    public void setSubject(String subject) {
        subjectField.setText(subject);
    }

    /**
     * Returns the selected provider in the providers combo box.
     *
     * @return the selected provider
     */
    private ChatRoomProviderWrapper getSelectedProvider() {
        String key = (String) accountsSpinner.getSelectedItem();
        return mucRCProviderList.get(key);
    }

    /**
     * Sets the value of chat room name field.
     *
     * @param chatRoom the chat room name.
     */
    public void setChatRoomField(String chatRoom) {
        this.chatRoomComboBox.setText(chatRoom);
        updateJoinButtonEnableState();
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private boolean createOrJoinChatRoom() {
        // allow nickName to contain spaces
        String nickName = ViewUtil.toString(nicknameField);
        String password = ViewUtil.toString(passwordField);
        String subject = ViewUtil.toString(subjectField);

        String chatRoomID = chatRoomComboBox.getText();
        if (chatRoomID != null) {
            chatRoomID = chatRoomID.replaceAll("\\s", "");
        }
        boolean savePassword = mSavePasswordCheckBox.isChecked();

        Collection<String> contacts = new ArrayList<>();
        String reason = "Let's chat";

        if ((chatRoomID != null) && (nickName != null) && (getSelectedProvider() != null)) {
            ProtocolProviderService pps = getSelectedProvider().getProtocolProvider();

            // create new if chatRoom does not exist
            ChatRoomWrapper chatRoomWrapper = mucService.findChatRoomWrapperFromChatRoomID(chatRoomID, pps);
            if (chatRoomWrapper == null) {
                // Just create chatRoomWrapper without joining as nick and password options are not available
                chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
                        reason, false, false, true, chatRoomList.contains(chatRoomID));

                // Return without open the chat room, the protocol failed to create a chat room (null)
                if ((chatRoomWrapper == null) || (chatRoomWrapper.getChatRoom() == null)) {
                    aTalkApp.showToastMessage(R.string.chatroom_create_error, chatRoomID);
                    return false;
                }

                // retrieve and save the created chatRoom in database -> createChatRoom will save a copy in dB
                // chatRoomID = chatRoomWrapper.getChatRoomID();
                // ConfigurationUtils.saveChatRoom(pps, chatRoomID, chatRoomID);

                /*
                 * Save to server bookmark with auto-join option == false only for newly created chatRoom;
                 * Otherwise risk of overridden user previous settings
                 */
                // chatRoomWrapper.setAutoJoin(true);
                chatRoomWrapper.setBookmark(true);
                chatRoomWrapper.setNickName(nickName); // saved for later ResourcePart retrieval in addBookmarkedConference
                EntityBareJid entityBareJid = chatRoomWrapper.getEntityBareJid();

                // Use subject for bookmark name if not null; else use chatRoomID
                String name = TextUtils.isEmpty(subject) ? chatRoomID : subject;
                BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());
                try {
                    bookmarkManager.addBookmarkedConference(name, entityBareJid, false,
                            chatRoomWrapper.getNickResource(), password);
                } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                         | XMPPException.XMPPErrorException | InterruptedException e) {
                    Timber.w("Failed to add new Bookmarks: %s", e.getMessage());
                }

                // Allow removal of new chatRoom if join failed
                if (AppGUIActivator.getConfigurationService()
                        .getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
                    final ChatRoomWrapper crWrapper = chatRoomWrapper;

                    chatRoomWrapper.addPropertyChangeListener(evt -> {
                        if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP))
                            return;

                        // if we failed for some reason, then close and remove the room
                        AppGUIActivator.getUIService().closeChatRoomWindow(crWrapper);
                        MUCActivator.getMUCService().removeChatRoom(crWrapper);
                    });
                }
            }
            // Set chatRoom openAutomatically on_activity
            // MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);

            chatRoomWrapper.setNickName(nickName);
            if (savePassword)
                chatRoomWrapper.savePassword(password);
            else
                chatRoomWrapper.savePassword(null);

            byte[] pwdByte = StringUtils.isEmpty(password) ? null : password.getBytes();
            mucService.joinChatRoom(chatRoomWrapper, nickName, pwdByte, subject);

            Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
            mParent.startActivity(chatIntent);
            return true;
        }
        else if (TextUtils.isEmpty(chatRoomID)) {
            aTalkApp.showToastMessage(R.string.chatroom_join_name);
        }
        else if (nickName == null) {
            aTalkApp.showToastMessage(R.string.change_nickname_null);
        }
        else {
            aTalkApp.showToastMessage(R.string.chatroom_join_failed, nickName, chatRoomID);
        }
        return false;
    }
}
