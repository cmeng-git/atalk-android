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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.RoomInfo;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.util.XmppStringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This fragment that show the chatRoom information retrieve from the server
 *
 * @author Eng Chong Meng
 */
public class ChatRoomInfoFragment extends OSGiFragment
{
    /**
     * Logger of this class
     */
    private static final Logger logger = Logger.getLogger(ChatRoomInfoFragment.class);

    private View mContent;
    private static ChatRoomWrapper mChatRoomWrapper;

    public ChatRoomInfoFragment()
    {
    }

    public static ChatRoomInfoFragment newInstance(ChatRoomWrapper chatRoomWrapper)
    {
        mChatRoomWrapper = chatRoomWrapper;
        return new ChatRoomInfoFragment();
    }

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        mContent = inflater.inflate(R.layout.chatroom_info, container, false);

        final Button buttonOk = mContent.findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(v -> closeFragment());

        new getRoomInfo().execute();
        return mContent;
    }

    private void closeFragment()
    {
        Fragment chatRoomInfoFragment = getFragmentManager().findFragmentById(android.R.id.content);
        getActivity().getSupportFragmentManager().beginTransaction().remove(chatRoomInfoFragment).commit();
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
                } catch (SmackException.NoResponseException e) {
                    errMsg = e.getMessage();
                } catch (XMPPException.XMPPErrorException e) {
                    String descriptiveText = e.getStanzaError().getDescriptiveText() + "\n";
                    errMsg = descriptiveText + e.getMessage();
                } catch (SmackException.NotConnectedException e) {
                    errMsg = e.getMessage();
                } catch (InterruptedException e) {
                    errMsg = e.getMessage();
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
                TextView textView = mContent.findViewById(R.id.roominfo_name);
                textView.setText(chatRoomInfo.getName());

                textView = mContent.findViewById(R.id.roominfo_subject);
                textView.setText(chatRoomInfo.getSubject());

                textView = mContent.findViewById(R.id.roominfo_description);
                textView.setText(chatRoomInfo.getDescription());

                textView = mContent.findViewById(R.id.roominfo_occupants);
                int value = chatRoomInfo.getOccupantsCount();
                if (value < 0)
                    textValue = "not specified";
                else
                    textValue = Integer.toString(value);
                textView.setText(textValue);

                textView = mContent.findViewById(R.id.maxhistoryfetch);
                value = chatRoomInfo.getMaxHistoryFetch();
                if (value < 0)
                    textValue = "not specified";
                else
                    textValue = Integer.toString(value);
                textView.setText(textValue);

                textView = mContent.findViewById(R.id.roominfo_contactjid);
                // getContactJids() may throw NPE if contact == nul
                List<EntityBareJid> contactJids = new ArrayList<>();
                try {
                    contactJids = chatRoomInfo.getContactJids();
                } catch (NullPointerException e) {
                    logger.error("Contact Jids excepiton: " + e.getMessage());
                }
                textValue = contactJids.toString();
                textView.setText(textValue);

                textView = mContent.findViewById(R.id.roominfo_lang);
                textValue = chatRoomInfo.getLang();
                textValue = (textValue == null) ? "" : textValue;
                textView.setText(textValue);

                textView = mContent.findViewById(R.id.roominfo_ldapgroup);
                textValue = chatRoomInfo.getLdapGroup();
                textValue = (textValue == null) ? "" : textValue;
                textView.setText(textValue);

                CheckBox cbox = mContent.findViewById(R.id.muc_membersonly);
                cbox.setChecked(chatRoomInfo.isMembersOnly());

                cbox = mContent.findViewById(R.id.muc_nonanonymous);
                cbox.setChecked(chatRoomInfo.isNonanonymous());

                cbox = mContent.findViewById(R.id.muc_persistent);
                cbox.setChecked(chatRoomInfo.isPersistent());

                cbox = mContent.findViewById(R.id.muc_passwordprotected);
                cbox.setChecked(chatRoomInfo.isPasswordProtected());

                cbox = mContent.findViewById(R.id.muc_moderated);
                cbox.setChecked(chatRoomInfo.isModerated());

                cbox = mContent.findViewById(R.id.room_subject_modifiable);
                Boolean state = chatRoomInfo.isSubjectModifiable();
                cbox.setChecked((state != null) ? state : false);
            }
            else {
                TextView textView = mContent.findViewById(R.id.roominfo_name);
                textView.setText(XmppStringUtils.parseLocalpart(mChatRoomWrapper.getChatRoomID()));

                textView = mContent.findViewById(R.id.roominfo_subject);
                textView.setTextColor(getResources().getColor(R.color.red));
                textView.setText(errMsg);
            }
        }
    }
}
