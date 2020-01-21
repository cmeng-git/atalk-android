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
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.util.ComboBox;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.util.XmppStringUtils;

import java.util.*;

import timber.log.Timber;

/**
 * The invite dialog is the one shown when the user clicks on the conference option in the Contact List toolbar.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomCreateDialog extends Dialog implements OnItemSelectedListener
{
    private final MainMenuActivity mParent;
    private final MUCServiceImpl mucService;

    /**
     * The account list view.
     */
    private Spinner accountsSpinner;
    private ComboBox chatRoomComboBox;

    private EditText subjectField;
    private EditText nicknameField;
    private Button mJoinButton;

    /**
     * A map of <JID, ChatRoomProviderWrapper>
     */
    private Map<String, ChatRoomProviderWrapper> mucRoomWrapperList = new LinkedHashMap<>();

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * @param mContext the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is invited.
     */
    public ChatRoomCreateDialog(Context mContext)
    {
        super(mContext);
        mParent = (MainMenuActivity) mContext;
        mucService = MUCActivator.getMUCService();
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle(R.string.service_gui_CHAT_ROOM_JOIN);
        this.setContentView(R.layout.muc_room_create_dialog);

        accountsSpinner = this.findViewById(R.id.jid_Accounts_Spinner);
        initAccountSpinner();

        nicknameField = this.findViewById(R.id.NickName_Edit);
        subjectField = this.findViewById(R.id.chatRoom_Subject_Edit);
        subjectField.setText("");

        chatRoomComboBox = this.findViewById(R.id.chatRoom_Combo);
        new initComboBox().execute();

        mJoinButton = this.findViewById(R.id.button_Join);
        mJoinButton.setOnClickListener(v -> {
            if (createOrJoinChatRoom())
                closeDialog();
        });

        Button mCancelButton = this.findViewById(R.id.button_Cancel);
        mCancelButton.setOnClickListener(v -> closeDialog());
        setCanceledOnTouchOutside(false);
    }

    // add items into accountsSpinner dynamically
    private void initAccountSpinner()
    {
        String mAccount;
        List<String> ppsList = new ArrayList<>();

        List<ChatRoomProviderWrapper> providers = mucService.getChatRoomProviders();
        for (ChatRoomProviderWrapper provider : providers) {
            mAccount = provider.getProtocolProvider().getAccountID().getDisplayName();
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
    private class initComboBox extends AsyncTask<Void, Void, Void>
    {
        List<String> chatRoomList = new ArrayList<>();

        @Override
        protected void onPreExecute()
        {
            chatRoomComboBox.setText(mParent.getString(R.string.service_gui_ROOM_NAME));
            chatRoomComboBox.setSuggestionSource(chatRoomList);
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            ChatRoomProviderWrapper crpWrapper = getSelectedProvider();
            if (crpWrapper != null) {
                // local chatRooms
                ProtocolProviderService pps = crpWrapper.getProtocolProvider();
                chatRoomList = mucService.getExistingChatRooms(pps);

                // server chatRooms
                List<String> sChatRoomList = mucService.getExistingChatRooms(crpWrapper);
                for (String sRoom : sChatRoomList) {
                    if (!chatRoomList.contains(sRoom))
                        chatRoomList.add(sRoom);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            if (chatRoomList.size() == 0)
                chatRoomList.add(mParent.getString(R.string.service_gui_ROOM_NAME));

            String chatRoomID = chatRoomList.get(0);
            chatRoomComboBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            chatRoomComboBox.setText(chatRoomID);
            chatRoomComboBox.setSuggestionSource(chatRoomList);
        }
    }

    private void closeDialog()
    {
        this.cancel();
    }

    /**
     * Updates the enable/disable state of the OK button.
     */
    private void updateJoinButtonEnableState()
    {
        String nickName = ViewUtil.toString(nicknameField);
        String chatRoomField = chatRoomComboBox.getText();

        boolean mEnable = (!TextUtils.isEmpty(chatRoomField) && (nickName != null));
        if (mEnable) {
            mJoinButton.setEnabled(true);
            mJoinButton.setAlpha(1.0f);
        }
        else {
            mJoinButton.setEnabled(true);
            mJoinButton.setAlpha(1.0f);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id)
    {
        String userId = (String) adapter.getItemAtPosition(pos);
        ChatRoomProviderWrapper protocol = mucRoomWrapperList.get(userId);
        setDefaultNickname(protocol.getProtocolProvider());
        new initComboBox().execute();

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
    private void setDefaultNickname(ProtocolProviderService pps)
    {
        if (pps != null) {
            String nickName = AndroidGUIActivator.getGlobalDisplayDetailsService().getDisplayName(pps);
            if ((nickName == null) || nickName.contains("@"))
                nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());
            nicknameField.setText(nickName);
            updateJoinButtonEnableState();
        }
    }

    /**
     * Sets the (chat room) subject to be displayed in this <tt>ChatRoomSubjectPanel</tt>.
     *
     * @param subject the (chat room) subject to be displayed in this <tt>ChatRoomSubjectPanel</tt>
     */
    public void setSubject(String subject)
    {
        subjectField.setText(subject);
    }

    /**
     * Returns the selected provider in the providers combo box.
     *
     * @return the selected provider
     */
    private ChatRoomProviderWrapper getSelectedProvider()
    {
        String key = (String) accountsSpinner.getSelectedItem();
        return mucRoomWrapperList.get(key);
    }

    /**
     * Sets the value of chat room name field.
     *
     * @param chatRoom the chat room name.
     */
    public void setChatRoomField(String chatRoom)
    {
        this.chatRoomComboBox.setText(chatRoom);
        updateJoinButtonEnableState();
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private boolean createOrJoinChatRoom()
    {
        // allow nickName to contain spaces
        String nickName = ViewUtil.toString(nicknameField);
        String subject = ViewUtil.toString(subjectField);
        String chatRoomID = chatRoomComboBox.getText().replaceAll("\\s", "");
        Collection<String> contacts = new ArrayList<>();
        String reason = "Let's chat";

        if (!TextUtils.isEmpty(chatRoomID) && (nickName != null)) {
            ProtocolProviderService pps = getSelectedProvider().getProtocolProvider();

            // create new if chatRoom does not exist
            ChatRoomWrapper chatRoomWrapper = mucService.findChatRoomWrapperFromChatRoomID(chatRoomID, pps);
            if (chatRoomWrapper == null) {
                // Just create chatRoomWrapper without joining
                chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
                        reason, false, false, false);

                // Return without open the chat room, the protocol failed to create a chat room (null)
                if (chatRoomWrapper == null) {
                    aTalkApp.showToastMessage(R.string.service_gui_CREATE_CHAT_ROOM_ERROR, chatRoomID);
                    return false;
                }

                // retrieve and save the created chatRoom in database -> createChatRoom will save a copy in dB
                // chatRoomID = chatRoomWrapper.getChatRoomID();
                // ConfigurationUtils.saveChatRoom(pps, chatRoomID, chatRoomID);

                /*
                 * Save to server bookmark with autojoin option for newly created chatroom
                 */
                // MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);
                chatRoomWrapper.setAutoJoin(true);
                chatRoomWrapper.setBookmark(true);
                chatRoomWrapper.setNickName(nickName); // save for later ResourcePart retrieval
                EntityBareJid entityBareJid = chatRoomWrapper.getEntityBareJid();

                BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());
                try {
                    // Use subject for bookmark name
                    bookmarkManager.addBookmarkedConference(subject, entityBareJid, true,
                            chatRoomWrapper.getNickResource(), "");
                } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                        | XMPPException.XMPPErrorException | InterruptedException e) {
                    Timber.w("Failed to add new Bookmarks: %s", e.getMessage());
                }

                // Allow to remove new chatRoom if join failed
                if (AndroidGUIActivator.getConfigurationService()
                        .getBoolean(MUCService.REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
                    final ChatRoomWrapper crWrapper = chatRoomWrapper;

                    chatRoomWrapper.addPropertyChangeListener(evt -> {
                        if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP))
                            return;

                        // if we failed for some , then close and remove the room
                        AndroidGUIActivator.getUIService().closeChatRoomWindow(crWrapper);
                        MUCActivator.getMUCService().removeChatRoom(crWrapper);
                    });
                }
            }

            // Set chatRoom openAutomatically on_activity
            // MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);

            chatRoomWrapper.setNickName(nickName);
            mucService.joinChatRoom(chatRoomWrapper, nickName, null, subject);
            Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
            mParent.startActivity(chatIntent);
            return true;
        }
        else if (TextUtils.isEmpty(chatRoomID)) {
            aTalkApp.showToastMessage(R.string.service_gui_JOIN_CHAT_ROOM_NAME);
        }
        else {
            aTalkApp.showToastMessage(R.string.service_gui_FAILED_TO_JOIN_CHAT_ROOM, "null");
        }
        return false;
    }
}
