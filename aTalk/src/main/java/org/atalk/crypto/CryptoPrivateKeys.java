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
package org.atalk.crypto;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import net.java.sip.communicator.plugin.otr.OtrActivator;
import net.java.sip.communicator.plugin.otr.ScOtrKeyManager;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.CryptoHelper;
import org.atalk.util.Logger;
import org.atalk.util.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings screen displays local private keys. Allows user to generate new or regenerate
 * if one exists.
 *
 * @author Eng Chong Meng
 */
public class CryptoPrivateKeys extends OSGiActivity
{
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(CryptoPrivateKeys.class);

    private static final String OTR = "OTR:";
    private static final String OMEMO = "OMEMO:";

    private ScOtrKeyManager keyManager = OtrActivator.scOtrKeyManager;

    /**
     * Adapter used to displays private keys for all accounts.
     */
    private PrivateKeyListAdapter accountsAdapter;

    /**
     * Map to store bareJId to accountID sorted in ascending order
     */
    private final Map<String, AccountID> accountList = new TreeMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        ListView accountsKeysList = (ListView) findViewById(R.id.list);
        this.accountsAdapter = new PrivateKeyListAdapter(getAccountList());
        accountsKeysList.setAdapter(accountsAdapter);
        registerForContextMenu(accountsKeysList);
    }

    /**
     * Get the list of all registered accounts in ascending order
     *
     * @return the map of all known accounts with bareJid as key.
     */
    Map<String, AccountID> getAccountList()
    {
        // Get all the registered protocolProviders
        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : providers) {
            AccountID accountId = pps.getAccountID();
            String bareJid = accountId.getAccountJid();

            accountList.put(OMEMO + bareJid, accountId);
            accountList.put(OTR + bareJid, accountId);
        }
        return accountList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.crypto_key_ctx_menu, menu);

        ListView.AdapterContextMenuInfo ctxInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int pos = ctxInfo.position;
        String privateKey = accountsAdapter.getOwnKeyFromRow(pos);
        boolean isKeyExist = !StringUtils.isNullOrEmpty(privateKey);

        menu.findItem(R.id.generate).setEnabled(!isKeyExist);
        menu.findItem(R.id.regenerate).setEnabled(isKeyExist);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info
                = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int pos = info.position;
        String bareJid = accountsAdapter.getBareJidFromRow(pos);

        int id = item.getItemId();
        switch (id) {
            case R.id.generate:
                showGenerateKeyAlert(bareJid, false);
                accountsAdapter.notifyDataSetChanged();
                return true;

            case R.id.regenerate:
                showGenerateKeyAlert(bareJid, true);
                accountsAdapter.notifyDataSetChanged();
                return true;

            case R.id.copy:
                String privateKey = accountsAdapter.getOwnKeyFromRow(pos);
                ClipboardManager cbManager
                        = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cbManager.setPrimaryClip(ClipData.newPlainText(null,
                        CryptoHelper.prettifyFingerprint(privateKey)));
                Toast.makeText(this, R.string.crypto_toast_FINGERPRINT_COPY, Toast.LENGTH_SHORT)
                        .show();
                return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Adapter which displays privateKeys for the given list of accounts.
     */
    private class PrivateKeyListAdapter extends BaseAdapter
    {
        /**
         * List of <tt>AccountID</tt> for which the private keys are being displayed.
         */
        private final Map<String, AccountID> accountIDs;

        /**
         * Creates new instance of <tt>PrivateKeyListAdapter</tt>.
         *
         * @param accountList
         *         the list of <tt>AccountID</tt>s for which private keys will be displayed by
         *         this adapter.
         */
        PrivateKeyListAdapter(Map<String, AccountID> accountList)
        {
            accountIDs = accountList;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount()
        {
            return accountIDs.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getItem(int position)
        {
            String bareJid = getBareJidFromRow(position);
            return accountIDs.get(bareJid);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getItemId(int position)
        {
            return position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View rowView, ViewGroup parent)
        {
            if (rowView == null)
                rowView = getLayoutInflater().inflate(R.layout.crypto_privkey_list_row, parent,
                        false);
            String bareJid = getBareJidFromRow(position);
            ViewUtil.setTextViewValue(rowView, R.id.protocolProvider, bareJid);

            String fingerprint = getOwnKeyFromRow(position);
            String fingerprintStr;
            if (StringUtils.isNullOrEmpty(fingerprint)) {
                fingerprintStr = getString(R.string.crypto_text_NO_KEY_PRESENT);
            }
            else {
                fingerprintStr = fingerprint;
            }
            ViewUtil.setTextViewValue(rowView, R.id.fingerprint,
                    CryptoHelper.prettifyFingerprint(fingerprintStr));
            return rowView;
        }

        String getBareJidFromRow(int row)
        {
            int index = -1;
            for (String bareJid : accountIDs.keySet()) {
                index++;
                if (index == row) {
                    return bareJid;
                }
            }
            return null;
        }

        String getOwnKeyFromRow(int row)
        {
            String bareJid = getBareJidFromRow(row);
            AccountID accountId = accountIDs.get(bareJid);

            String fingerprint = null;
            if (bareJid.startsWith(OMEMO))
                fingerprint = getOwnFingerprint(accountId);
            else if (bareJid.startsWith(OTR)) {
                fingerprint = keyManager.getLocalFingerprint(accountId);
                if (!StringUtils.isNullOrEmpty(fingerprint))
                    fingerprint = fingerprint.toLowerCase();
            }
            return fingerprint;
        }
    }

    /**
     * Displays alert asking user if he wants to regenerate or generate new privateKey.
     *
     * @param bareJid
     *         the account bareJid
     * @param isKeyExist
     *         <tt>true</tt>if key exist
     */
    private void showGenerateKeyAlert(final String bareJid, boolean isKeyExist)
    {
        final AccountID accountId = accountList.get(bareJid);
        int getResStrId = isKeyExist ? R.string.crypto_dialog_KEY_REGENERATE_QUESTION
                : R.string.crypto_dialog_KEY_GENERATE_QUESTION;

        String warnMsg = bareJid.startsWith(OMEMO)
                ? getString(R.string.pref_omemo_regenerate_identities_summary) : "";
        String message = getString(getResStrId, bareJid, warnMsg);

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(getString(R.string.crypto_dialog_KEY_GENERATE_TITLE))
                .setMessage(message)
                .setPositiveButton(R.string.service_gui_YES, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        if (accountId != null) {
                            if (bareJid.startsWith(OMEMO))
                                regenerate(accountId);
                            else if (bareJid.startsWith(OTR))
                                keyManager.generateKeyPair(accountId);
                        }
                        accountsAdapter.notifyDataSetChanged();
                    }
                }).setNegativeButton(R.string.service_gui_NO, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        }).show();
    }

    /**
     * Regenerate the OMEMO key parameters for the given accountId
     *
     * @param accountId
     *         the accountID
     */
    private void regenerate(AccountID accountId)
    {
        ProtocolProviderService pps = accountId.getProtocolProvider();
        if (pps != null) {
            XMPPTCPConnection connection = pps.getConnection();
            if ((connection != null) && connection.isAuthenticated()) {
                OmemoManager omemoManager = OmemoManager.getInstanceFor(connection);
                try {
                    omemoManager.regenerate();
                } catch (SmackException | InterruptedException
                        | XMPPException.XMPPErrorException
                        | CorruptedOmemoKeyException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param accountId
     *         the accountID
     * @return the owner fingerPrint for the given accountID
     */
    private String getOwnFingerprint(AccountID accountId)
    {
        String ownFingerprint = null;

        ProtocolProviderService pps = accountId.getProtocolProvider();
        if (pps != null) {
            XMPPTCPConnection connection = pps.getConnection();
            if ((connection != null) && connection.isAuthenticated()) {
                OmemoManager omemoManager = OmemoManager.getInstanceFor(connection);
                ownFingerprint = omemoManager.getOurFingerprint().toString();
            }
        }
        return ownFingerprint;
    }
}
