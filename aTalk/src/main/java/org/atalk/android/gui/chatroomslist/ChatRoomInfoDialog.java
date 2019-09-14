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

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.protocol.jabber.ChatRoomMemberJabberImpl;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiDialogFragment;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.util.XmppStringUtils;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * This fragment dialog shows the chatRoom information retrieve from the server
 *
 * @author Eng Chong Meng
 */
public class ChatRoomInfoDialog extends OSGiDialogFragment
{
    private View contentView;
    private static ChatRoomWrapper mChatRoomWrapper;

    public ChatRoomInfoDialog()
    {
    }

    public static ChatRoomInfoDialog newInstance(ChatRoomWrapper chatRoomWrapper)
    {
        mChatRoomWrapper = chatRoomWrapper;

        Bundle args = new Bundle();
        ChatRoomInfoDialog dialog = new ChatRoomInfoDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        getDialog().setTitle(R.string.service_gui_CHATROOM_INFO);
        contentView = inflater.inflate(R.layout.chatroom_info, container, false);

        final Button buttonOk = contentView.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(v -> dismiss());

        new getRoomInfo().execute();
        setCancelable(false);
        return contentView;
    }

    /**
     * Retrieve the chatRoom info from server and populate the fragment with the available information
     */
    private class getRoomInfo extends AsyncTask<Void, Void, Void>
    {
        RoomInfo chatRoomInfo = null;
        String errMsg;

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected Void doInBackground(Void... params)
        {
            ChatRoomProviderWrapper crpWrapper = mChatRoomWrapper.getParentProvider();
            if (crpWrapper != null) {
                ProtocolProviderService pps = crpWrapper.getProtocolProvider();
                EntityBareJid entityBareJid = mChatRoomWrapper.getChatRoom().getIdentifier();

                MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(pps.getConnection());
                try {
                    chatRoomInfo = mucManager.getRoomInfo(entityBareJid);
                } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                        | InterruptedException e) {
                    errMsg = e.getMessage();
                } catch (XMPPException.XMPPErrorException e) {
                    String descriptiveText = e.getStanzaError().getDescriptiveText() + "\n";
                    errMsg = descriptiveText + e.getMessage();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result)
        {
            super.onPostExecute(result);
            String textValue;
            if (chatRoomInfo != null) {
                TextView textView = contentView.findViewById(R.id.roominfo_name);
                textView.setText(chatRoomInfo.getName());

                textView = contentView.findViewById(R.id.roominfo_subject);
                textView.setText(chatRoomInfo.getSubject());

                textView = contentView.findViewById(R.id.roominfo_description);
                textValue = chatRoomInfo.getDescription();
                if (TextUtils.isEmpty(textValue))
                    textValue = mChatRoomWrapper.getBookmarkName();
                textView.setText(textValue);

                textView = contentView.findViewById(R.id.roominfo_occupants);
                StringBuilder memberList = new StringBuilder();
                List<ChatRoomMember> occupants = mChatRoomWrapper.getChatRoom().getMembers();
                for (ChatRoomMember member : occupants) {
                    ChatRoomMemberJabberImpl occupant = (ChatRoomMemberJabberImpl) member;
                    memberList.append(occupant.getNickName())
                            .append(" - ")
                            .append(occupant.getJabberID())
                            .append("; ");
                }
                textView.setText(memberList);

                textView = contentView.findViewById(R.id.maxhistoryfetch);
                int value = chatRoomInfo.getMaxHistoryFetch();
                if (value < 0)
                    textValue = "not specified";
                else
                    textValue = Integer.toString(value);
                textView.setText(textValue);

                textView = contentView.findViewById(R.id.roominfo_contactjid);
                // getContactJids() may throw NPE if contact == null
                List<EntityBareJid> contactJids = new ArrayList<>();
                try {
                    contactJids = chatRoomInfo.getContactJids();
                    textValue = contactJids.get(0).toString();
                    textView.setText(textValue);
                } catch (NullPointerException e) {
                    Timber.e("Contact Jids exception: %s", e.getMessage());
                }

                textView = contentView.findViewById(R.id.roominfo_lang);
                textValue = chatRoomInfo.getLang();
                textValue = (textValue == null) ? "" : textValue;
                textView.setText(textValue);

                textView = contentView.findViewById(R.id.roominfo_ldapgroup);
                textValue = chatRoomInfo.getLdapGroup();
                textValue = (textValue == null) ? "" : textValue;
                textView.setText(textValue);

                CheckBox cbox = contentView.findViewById(R.id.muc_membersonly);
                cbox.setChecked(chatRoomInfo.isMembersOnly());

                cbox = contentView.findViewById(R.id.muc_nonanonymous);
                cbox.setChecked(chatRoomInfo.isNonanonymous());

                cbox = contentView.findViewById(R.id.muc_persistent);
                cbox.setChecked(chatRoomInfo.isPersistent());

                cbox = contentView.findViewById(R.id.muc_passwordprotected);
                cbox.setChecked(chatRoomInfo.isPasswordProtected());

                cbox = contentView.findViewById(R.id.muc_moderated);
                cbox.setChecked(chatRoomInfo.isModerated());

                cbox = contentView.findViewById(R.id.room_subject_modifiable);
                Boolean state = chatRoomInfo.isSubjectModifiable();
                cbox.setChecked((state != null) ? state : false);
            }
            else {
                TextView textView = contentView.findViewById(R.id.roominfo_name);
                textView.setText(XmppStringUtils.parseLocalpart(mChatRoomWrapper.getChatRoomID()));

                textView = contentView.findViewById(R.id.roominfo_subject);
                textView.setTextColor(getResources().getColor(R.color.red));
                textView.setText(errMsg);
            }
        }
    }
}
