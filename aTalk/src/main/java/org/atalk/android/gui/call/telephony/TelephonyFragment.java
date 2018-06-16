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
package org.atalk.android.gui.call.telephony;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.account.Account;
import org.atalk.android.gui.account.AccountsListAdapter;
import org.atalk.android.gui.util.AndroidCallUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl.GOOGLE_VOICE_DOMAIN;

/**
 * This activity allows user to make external phone call via a selected provider.
 * The server must support ext phone call via gateway
 * <feature var='urn:xmpp:jingle:apps:rtp:audio'/>
 * <feature var='urn:xmpp:jingle:apps:rtp:video'/>
 *
 * @author Eng Chong Meng
 */
public class TelephonyFragment extends OSGiFragment
{
    /* The logger */
    private final static Logger logger = Logger.getLogger(TelephonyFragment.class);

    private static String mLastJid = null;

    private Activity mActivity;
    private Spinner accountsSpinner;
    private RecipientSelectView vRecipient;
    private TextView vTelephonyDomain;

    private ProtocolProviderServiceJabberImpl mPPS;

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        mActivity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        View content = inflater.inflate(R.layout.telephony, container, false);

        vRecipient = content.findViewById(R.id.address);
        if (mLastJid != null)
            vRecipient.setText(mLastJid);

        vTelephonyDomain = content.findViewById(R.id.telephonyDomain);

        accountsSpinner = content.findViewById(R.id.selectAccountSpinner);
        accountsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id)
            {
                Account selectedAcc = (Account) accountsSpinner.getSelectedItem();
                mPPS = (ProtocolProviderServiceJabberImpl) selectedAcc.getProtocolProvider();
                JabberAccountID accountJID = (JabberAccountID) mPPS.getAccountID();
                String telephonyDomain = accountJID.getOverridePhoneSuffix();
                if (TextUtils.isEmpty(telephonyDomain)) {
                    // StrictMode$AndroidBlockGuardPolicy.onNetwork(StrictMode.java:1448); so a simple check only instead of SRV
                    // boolean isGoogle =  mPPS.isGmailOrGoogleAppsAccount();
                    boolean isGoogle = accountJID.toString().contains("google.com");
                    if (isGoogle) {
                        String bypassDomain = accountJID.getTelephonyDomainBypassCaps();
                        if (!TextUtils.isEmpty(bypassDomain))
                            telephonyDomain = bypassDomain;
                        else
                            telephonyDomain = GOOGLE_VOICE_DOMAIN;
                    }
                    else
                        telephonyDomain = accountJID.getService();
                }
                vTelephonyDomain.setText(telephonyDomain);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView)
            {
                // your code here
            }

        });

        initAccountSpinner();
        initButton(content);
        return content;
    }

    /**
     * Initializes accountIDs spinner selector with existing registered accounts.
     */
    private void initAccountSpinner()
    {
        int idx = 0;
        int selectedIdx = -1;
        List<AccountID> accounts = new ArrayList<>();

        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService provider : providers) {
            OperationSet opSet = provider.getOperationSet(OperationSetPresence.class);
            if (opSet != null) {
                AccountID account = provider.getAccountID();
                accounts.add(account);
                if ((selectedIdx == -1) && account.isPreferredProvider()) {
                    selectedIdx = idx;
                }
                idx++;
            }
        }

        AccountsListAdapter accountsAdapter = new AccountsListAdapter(mActivity,
                R.layout.select_account_row, R.layout.select_account_dropdown, accounts, true);
        accountsSpinner.setAdapter(accountsAdapter);

        // if we have only select account option and only one account select the available account
        if (accounts.size() == 1)
            accountsSpinner.setSelection(0);
        else
            accountsSpinner.setSelection(selectedIdx);
    }

    /**
     * Initializes the button click actions.
     */
    private void initButton(final View content)
    {
        final Button buttonAudio = content.findViewById(R.id.button_audio);
        buttonAudio.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                onCallClicked(content, false);
            }
        });

        final Button buttonVideo = content.findViewById(R.id.button_video);
        buttonVideo.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                onCallClicked(content, true);
            }
        });

        final Button buttonCancel = content.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                closeFragment();
            }
        });
    }

    /**
     * Method fired when one of the call buttons is clicked.
     *
     * @param content <tt>View</tt>
     * @param videoCall vide call is true else audio call
     */
    public void onCallClicked(View content, boolean videoCall)
    {
        String recipient = null;
        if (!vRecipient.isEmpty()) {
            recipient = vRecipient.getAddresses()[0].getAddress();
        }
        else {
            recipient = vRecipient.getText().toString();
        }
        recipient = recipient.replace(" ", "");
        if (TextUtils.isEmpty(recipient)) {
            // aTalkApp.showToastMessage(R.string.service_gui_NO_ONLINE_TELEPHONY_ACCOUNT);
            aTalkApp.showToastMessage(R.string.service_gui_NO_CONTACT_PHONE);
            return;
        }

        if (recipient.contains("@")) {
            try {
                Jid phoneJid = JidCreate.from(recipient);
            } catch (XmppStringprepException e) {
                aTalkApp.showToastMessage(R.string.unknown_recipient);
                return;
            }
        }
        else {
            String telephonyDomain = vTelephonyDomain.getText().toString();
            recipient += "@" + telephonyDomain;
        }

        mLastJid = recipient;
        AndroidCallUtil.createCall(mActivity, recipient, mPPS, videoCall);
        closeFragment();
    }

    private void closeFragment()
    {
        Fragment phoneFragment = getFragmentManager().findFragmentById(android.R.id.content);
        getActivity().getSupportFragmentManager().beginTransaction().remove(phoneFragment).commit();
    }
}
