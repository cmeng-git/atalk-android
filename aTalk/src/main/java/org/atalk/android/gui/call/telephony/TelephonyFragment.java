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

import android.content.Context;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.account.Account;
import org.atalk.android.gui.account.AccountsListAdapter;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.*;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import static net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl.GOOGLE_VOICE_DOMAIN;

/**
 * This activity allows user to make pbx phone call via the selected service gateway.
 * The server must support pbx phone call via gateway i.e.
 * <feature var='urn:xmpp:jingle:apps:rtp:audio'/>
 * <feature var='urn:xmpp:jingle:apps:rtp:video'/>
 *
 * @author Eng Chong Meng
 */
public class TelephonyFragment extends OSGiFragment
{
    private static String mLastJid = null;
    private static String mDomainJid;

    private Context mContext;
    FragmentActivity fragmentActivity;
    private Spinner accountsSpinner;
    private RecipientSelectView vRecipient;
    private TextView vTelephonyDomain;

    private ProtocolProviderService mPPS;

    public TelephonyFragment()
    {
        mDomainJid = null;
    }

    public static TelephonyFragment newInstance(String domainJid)
    {
        TelephonyFragment telephonyFragment = new TelephonyFragment();
        mDomainJid = domainJid;
        return telephonyFragment;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        mContext = context;
        fragmentActivity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        View content = inflater.inflate(R.layout.telephony, container, false);

        vRecipient = content.findViewById(R.id.address);
        vRecipient.addTextChangedListener(new TextWatcher()
        {
            public void afterTextChanged(Editable s)
            {
                if (!vRecipient.isEmpty()) {
                    mLastJid = vRecipient.getAddresses()[0].getAddress();
                }
                // to prevent device rotate from changing it to null - not working
                else {
                    mLastJid = ViewUtil.toString(vRecipient);
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
            }
        });
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
                mPPS = selectedAcc.getProtocolProvider();
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
                mDomainJid = telephonyDomain;
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
                AccountID accountID = provider.getAccountID();
                accounts.add(accountID);
                if ((selectedIdx == -1) && (mDomainJid != null)) {
                    if (mDomainJid.contains(accountID.getService()))
                        selectedIdx = idx;
                }
                idx++;
            }
        }
        AccountsListAdapter accountsAdapter = new AccountsListAdapter(getActivity(),
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
        buttonAudio.setOnClickListener(v -> onCallClicked(false));

        final Button buttonVideo = content.findViewById(R.id.button_video);
        buttonVideo.setOnClickListener(v -> onCallClicked(true));

        final Button buttonCancel = content.findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener(v -> closeFragment());
    }

    /**
     * Method fired when one of the call buttons is clicked.
     *
     * @param videoCall vide call is true else audio call
     */
    private void onCallClicked(boolean videoCall)
    {
        String recipient;
        if (!vRecipient.isEmpty()) {
            recipient = vRecipient.getAddresses()[0].getAddress();
        }
        else {
            recipient = ViewUtil.toString(vRecipient);
        }
        if (recipient == null) {
            // aTalkApp.showToastMessage(R.string.service_gui_NO_ONLINE_TELEPHONY_ACCOUNT);
            aTalkApp.showToastMessage(R.string.service_gui_NO_CONTACT_PHONE);
            return;
        }

        recipient = recipient.replace(" ", "");
        mLastJid = recipient;

        if (!recipient.contains("@")) {
            String telephonyDomain = ViewUtil.toString(vTelephonyDomain);
            recipient += "@" + telephonyDomain;
        }

        Jid phoneJid;
        try {
            phoneJid = JidCreate.from(recipient);
        } catch (XmppStringprepException | IllegalArgumentException e) {
            aTalkApp.showToastMessage(R.string.unknown_recipient);
            return;
        }

        AndroidCallUtil.createCall(mContext, mPPS, phoneJid, videoCall);
        closeFragment();
    }

    private void closeFragment()
    {
        Fragment phoneFragment = getParentFragmentManager().findFragmentById(android.R.id.content);
        if (phoneFragment != null)
            fragmentActivity.getSupportFragmentManager().beginTransaction().remove(phoneFragment).commit();
    }
}
