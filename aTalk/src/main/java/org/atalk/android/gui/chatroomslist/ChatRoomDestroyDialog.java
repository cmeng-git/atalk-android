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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.ViewUtil;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * ChatRoom destroy dialog allowing user to provide a reason and an alternate venue.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomDestroyDialog extends BaseFragment {
    private ChatRoomWrapper chatRoomWrapper;
    private ChatPanel chatPanel;

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.muc_room_destroy_dialog, container, false);

        Bundle bundle = getArguments();
        String message = bundle.getString(DialogActivity.EXTRA_MESSAGE);
        TextView msgWarn = view.findViewById(R.id.textAlert);
        msgWarn.setText(message);

        return view;
    }

    /**
     * Create chatRoom destroy dialog
     *
     * @param context the parent <code>Context</code>
     * @param crWrapper chatRoom wrapper
     * @param cPanel the chatPanel to send message
     */
    public void show(Context context, ChatRoomWrapper crWrapper, ChatPanel cPanel) {
        chatRoomWrapper = crWrapper;
        chatPanel = cPanel;

        String msgWarn = context.getString(R.string.chatroom_destroy_prompt,
                chatRoomWrapper.getUser(), chatRoomWrapper.getChatRoomID());
        Bundle fragmentBundle = new Bundle();
        fragmentBundle.putString(DialogActivity.EXTRA_MESSAGE, msgWarn);

        DialogActivity.showCustomDialog(context,
                context.getString(R.string.chatroom_destroy_title),
                ChatRoomDestroyDialog.class.getName(), fragmentBundle,
                context.getString(R.string.remove),
                new DialogListenerImpl(), null);
    }

    /**
     * Implements <code>DialogActivity.DialogListener</code> interface and handles refresh stores process.
     */
    public class DialogListenerImpl implements DialogActivity.DialogListener {
        @Override
        public boolean onConfirmClicked(DialogActivity dialog) {
            View view = dialog.getContentFragment().getView();
            String reason = ViewUtil.toString(view.findViewById(R.id.ReasonDestroy));
            String venue = ViewUtil.toString(view.findViewById(R.id.VenueAlternate));

            EntityBareJid entityBareJid = null;
            if (venue != null) {
                try {
                    entityBareJid = JidCreate.entityBareFrom(venue);
                } catch (XmppStringprepException ex) {
                    aTalkApp.showToastMessage(R.string.invalid_address, venue);
                    return false;
                }
            }

            // When a room is destroyed, purge all the chat messages and room chat session from the database.
            ChatRoom chatRoom = chatRoomWrapper.getChatRoom();
            MessageHistoryServiceImpl MHS = MessageHistoryActivator.getMessageHistoryService();
            MHS.eraseLocallyStoredChatHistory(chatRoom, null);

            MUCActivator.getMUCService().destroyChatRoom(chatRoomWrapper, reason, entityBareJid);
            if (chatPanel != null) {
                ChatSessionManager.removeActiveChat(chatPanel);
            }
            return true;
        }

        @Override
        public void onDialogCancelled(DialogActivity dialog) {
        }
    }

}
