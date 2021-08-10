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
import android.view.*;
import android.widget.EditText;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;

import org.atalk.android.R;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;

/**
 * The dialog allows user to change nickName and/or Subject.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomInfoChangeDialog extends OSGiFragment
{
    private static String EXTRA_CHATROOM = "chatRoom";
    private static String EXTRA_NICK = "nick";
    private static String EXTRA_Subject = "subject";

    private Context mContext;
    private ChatRoomWrapper mChatRoomWrapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.muc_room_info_change_dialog, container, false);

        Bundle bundle = getArguments();
        if (bundle != null) {
            EditText chatRoom = view.findViewById(R.id.chatRoom_Jid);
            chatRoom.setText(bundle.getString(EXTRA_CHATROOM));

            EditText nicknameField = view.findViewById(R.id.NickName_Edit);
            nicknameField.setText(bundle.getString(EXTRA_NICK));

            EditText subjectField = view.findViewById(R.id.chatRoom_Subject_Edit);
            subjectField.setText(bundle.getString(EXTRA_Subject));
        }
        return view;
    }

    /**
     * Create chatRoom info change dialog
     *
     * @param context the parent <tt>Context</tt>
     * @param chatRoomWrapper chatRoom wrapper
     */
    public void show(Context context, ChatRoomWrapper chatRoomWrapper)
    {
        mContext = context;
        mChatRoomWrapper = chatRoomWrapper;
        ChatRoom chatRoom = chatRoomWrapper.getChatRoom();

        Bundle fragmentBundle = new Bundle();
        fragmentBundle.putString(EXTRA_CHATROOM, mChatRoomWrapper.getChatRoomName());

        String nick = (chatRoom.getUserNickname() == null) ? null : chatRoom.getUserNickname().toString();
        fragmentBundle.putString(EXTRA_NICK, nick);

        fragmentBundle.putString(EXTRA_Subject, chatRoom.getSubject());

        DialogActivity.showCustomDialog(context,
                context.getString(R.string.service_gui_CHANGE_ROOM_INFO),
                ChatRoomInfoChangeDialog.class.getName(), fragmentBundle,
                context.getString(R.string.service_gui_APPLY),
                new ChatRoomInfoChangeDialog.DialogListenerImpl(), null);
    }

    /**
     * Implements <tt>DialogActivity.DialogListener</tt> interface and handles refresh stores process.
     */
    public class DialogListenerImpl implements DialogActivity.DialogListener
    {
        @Override
        public boolean onConfirmClicked(DialogActivity dialog)
        {
            // allow nickName to contain spaces
            View view = dialog.getContentFragment().getView();
            String nickName = ViewUtil.toString(view.findViewById(R.id.NickName_Edit));
            String subject = ViewUtil.toString(view.findViewById(R.id.chatRoom_Subject_Edit));

            if (nickName == null) {
                AndroidUtils.showAlertDialog(mContext, R.string.service_gui_CHANGE_ROOM_INFO,
                        R.string.service_gui_CHANGE_NICKNAME_NULL);
                return false;
            }

            MUCServiceImpl mucService = MUCActivator.getMUCService();
            mucService.joinChatRoom(mChatRoomWrapper, nickName, null, subject);
            return true;
        }

        @Override
        public void onDialogCancelled(DialogActivity dialog)
        {
        }
    }
}
