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

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

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
    private ChatRoomWrapper mChatRoomWrapper;

    public ChatRoomInfoDialog()
    {
    }

    public static ChatRoomInfoDialog newInstance(ChatRoomWrapper chatRoomWrapper)
    {
        ChatRoomInfoDialog dialog = new ChatRoomInfoDialog();
        dialog.mChatRoomWrapper = chatRoomWrapper;
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (getDialog() != null)
            getDialog().setTitle(R.string.service_gui_CHATROOM_INFO);

        contentView = inflater.inflate(R.layout.chatroom_info, container, false);
        final Button buttonOk = contentView.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(v -> dismiss());

        new getRoomInfo().execute();

        // setCancelable(false);
        return contentView;
    }

    /**
     * Retrieve the chatRoom info from server and populate the fragment with the available information
     */
    private class getRoomInfo extends AsyncTask<Void, Void, RoomInfo>
    {
        String errMsg;

        @Override
        protected void onPreExecute()
        {
        }

        @Override
        protected RoomInfo doInBackground(Void... params)
        {
            ChatRoomProviderWrapper crpWrapper = mChatRoomWrapper.getParentProvider();
            if (crpWrapper != null) {
                ProtocolProviderService pps = crpWrapper.getProtocolProvider();
                EntityBareJid entityBareJid = mChatRoomWrapper.getChatRoom().getIdentifier();

                MultiUserChatManager mucManager = MultiUserChatManager.getInstanceFor(pps.getConnection());
                try {
                    return mucManager.getRoomInfo(entityBareJid);
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
        protected void onPostExecute(RoomInfo chatRoomInfo)
        {
            String EMPTY = "";

            super.onPostExecute(chatRoomInfo);
            if (chatRoomInfo != null) {
                TextView textView = contentView.findViewById(R.id.roominfo_name);
                textView.setText(chatRoomInfo.getName());

                textView = contentView.findViewById(R.id.roominfo_subject);
                textView.setText(toString(chatRoomInfo.getSubject(), EMPTY));

                textView = contentView.findViewById(R.id.roominfo_description);
                textView.setText(toString(chatRoomInfo.getDescription(), mChatRoomWrapper.getBookmarkName()));

                textView = contentView.findViewById(R.id.roominfo_occupants);
                int count = chatRoomInfo.getOccupantsCount();
                if (count == -1) {
                    List<ChatRoomMember> occupants = mChatRoomWrapper.getChatRoom().getMembers();
                    count = occupants.size();
                }
                textView.setText(toValue(count, EMPTY));

                textView = contentView.findViewById(R.id.maxhistoryfetch);
                textView.setText(toValue(chatRoomInfo.getMaxHistoryFetch(),
                        getString(R.string.service_gui_INFO_NOT_SPECIFIED)));

                textView = contentView.findViewById(R.id.roominfo_contactjid);
                try {
                    List<EntityBareJid> contactJids = chatRoomInfo.getContactJids();
                    if (!contactJids.isEmpty())
                        textView.setText(contactJids.get(0));
                } catch (NullPointerException e) {
                    Timber.e("Contact Jids exception: %s", e.getMessage());
                }

                textView = contentView.findViewById(R.id.roominfo_lang);
                textView.setText(toString(chatRoomInfo.getLang(), EMPTY));

                textView = contentView.findViewById(R.id.roominfo_ldapgroup);
                textView.setText(toString(chatRoomInfo.getLdapGroup(), EMPTY));

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
                cbox.setChecked(toBoolean(chatRoomInfo.isSubjectModifiable()));
            }
            else {
                TextView textView = contentView.findViewById(R.id.roominfo_name);
                textView.setText(XmppStringUtils.parseLocalpart(mChatRoomWrapper.getChatRoomID()));

                textView = contentView.findViewById(R.id.roominfo_subject);
                // Must not use getResources.getColor()
                textView.setTextColor(Color.RED);
                textView.setText(errMsg);
            }
        }

        /**
         * Return String value of the integer value
         *
         * @param value Integer
         * @param defaultValue return default string if int == -1
         * @return String value of the specified Integer value
         */
        private String toValue(int value, String defaultValue)
        {
            return (value != -1) ? Integer.toString(value) : defaultValue;
        }

        /**
         * Return string if not null or default
         *
         * @param text test String
         * @param defaultValue return default string
         * @return text if not null else defaultValue
         */
        private String toString(String text, String defaultValue)
        {
            return (text != null) ? text : defaultValue;
        }

        /**
         * Return Boolean state if not null else false
         *
         * @param state Boolean state
         * @return Boolean value if not null else false
         */
        private boolean toBoolean(Boolean state)
        {
            return (state != null) ? state : false;
        }
    }
}
