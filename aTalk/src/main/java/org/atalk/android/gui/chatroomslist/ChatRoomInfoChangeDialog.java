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
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.atalk.android.R;

/**
 * The dialog allows user to change nickName and/or Subject.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomInfoChangeDialog extends Dialog
{
    private ChatRoom mChatRoom;

    private EditText subjectField;
    private EditText nicknameField;

    private String oldNick = "";
    private String oldSubject;

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * @param context the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is invited.
     */
    public ChatRoomInfoChangeDialog(Context context, ChatRoomWrapper chatRoomWrapper)
    {
        super(context);
        mChatRoom = chatRoomWrapper.getChatRoom();
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.muc_room_info_change_dialog);
        setTitle(R.string.service_gui_CHANGE_ROOM_INFO);

        EditText chatRoomName = this.findViewById(R.id.chatRoom_Jid);
        chatRoomName.setText(mChatRoom.getIdentifier());
        chatRoomName.setEnabled(false);
        if (mChatRoom.getUserNickname() != null)
            oldNick = mChatRoom.getUserNickname().toString();
        nicknameField = this.findViewById(R.id.NickName_Edit);
        nicknameField.setText(oldNick);

        oldSubject = mChatRoom.getSubject();
        subjectField = this.findViewById(R.id.chatRoom_Subject_Edit);
        subjectField.setText(oldSubject);

        Button mApplyButton = this.findViewById(R.id.button_Apply);
        mApplyButton.setOnClickListener(v -> {
            applyChatRoomChanges();
            closeDialog();
        });

        Button mCancelButton = this.findViewById(R.id.button_Cancel);
        mCancelButton.setOnClickListener(v -> closeDialog());
        setCanceledOnTouchOutside(false);
    }

    private void closeDialog()
    {
        this.cancel();
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private void applyChatRoomChanges()
    {
        // allow nickName to contain spaces
        String nickName = nicknameField.getText().toString().trim();
        String subject = subjectField.getText().toString().trim();

        if (!TextUtils.isEmpty(nickName) && !nickName.equals(oldNick)) {
            try {
                mChatRoom.setUserNickname(nickName);
            } catch (OperationFailedException e) {
                e.printStackTrace();
            }
        }

        if (!TextUtils.isEmpty(subject) && !subject.equals(oldSubject)) {
            try {
                mChatRoom.setSubject(subject);
            } catch (OperationFailedException e) {
                e.printStackTrace();
            }
        }
    }
}
