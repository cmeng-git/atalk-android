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
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.plugin.otr.OtrActivator;
import net.java.sip.communicator.plugin.otr.ScOtrKeyManager;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.CryptoHelper;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.omemo.*;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;

import java.io.IOException;
import java.util.*;

import timber.log.Timber;

/**
 * Settings screen displays local private keys. Allows user to generate new or regenerate
 * if one exists.
 *
 * @author Eng Chong Meng
 */
public class CryptoPrivateKeys extends OSGiActivity
{
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

    /* Map contains omemo devices and theirs associated fingerPrint */
    private final Map<String, String> deviceFingerprints = new TreeMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        ListView accountsKeysList = findViewById(R.id.list);
        this.accountsAdapter = new PrivateKeyListAdapter(getDeviceFingerPrints());
        accountsKeysList.setAdapter(accountsAdapter);
        registerForContextMenu(accountsKeysList);
    }

    /**
     * Get the list of all registered accounts in ascending order
     *
     * @return the map of all known accounts with bareJid as key.
     */
    Map<String, String> getDeviceFingerPrints()
    {
        String deviceJid;

        // Get all the registered protocolProviders
        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : providers) {
            if (pps.getConnection() == null)
                continue;

            OmemoManager omemoManager = OmemoManager.getInstanceFor(pps.getConnection());
            OmemoDevice userDevice = omemoManager.getOwnDevice();
            AccountID accountId = pps.getAccountID();
            String bareJid = accountId.getAccountJid();

            // Get OmemoDevice fingerprint
            String fingerprint = "";
            deviceJid = OMEMO + userDevice;
            try {
                OmemoFingerprint omemoFingerprint = omemoManager.getOwnFingerprint();
                if (omemoFingerprint != null)
                    fingerprint = omemoFingerprint.toString();
            } catch (SmackException.NotLoggedInException | CorruptedOmemoKeyException | IOException e) {
                Timber.w("Get own fingerprint Exception: %s", e.getMessage());
            }
            deviceFingerprints.put(deviceJid, fingerprint);
            accountList.put(deviceJid, accountId);

            // Get OTRDevice fingerprint - can be null for new generation
            deviceJid = OTR + bareJid;
            fingerprint = keyManager.getLocalFingerprint(accountId);
            if (StringUtils.isNotEmpty(fingerprint)) {
                fingerprint = fingerprint.toLowerCase();
            }
            deviceFingerprints.put(deviceJid, fingerprint);
            accountList.put(deviceJid, accountId);
        }
        if (deviceFingerprints.isEmpty())
            deviceFingerprints.put(aTalkApp.getResString(R.string.service_gui_settings_CRYPTO_PRIV_KEYS_EMPTY), "");
        return deviceFingerprints;
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
        boolean isKeyExist = StringUtils.isNotEmpty(privateKey);

        menu.findItem(R.id.generate).setEnabled(!isKeyExist);
        menu.findItem(R.id.regenerate).setEnabled(isKeyExist);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
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
                ClipboardManager cbManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cbManager.setPrimaryClip(ClipData.newPlainText(null, CryptoHelper.prettifyFingerprint(privateKey)));
                Toast.makeText(this, R.string.crypto_toast_FINGERPRINT_COPY, Toast.LENGTH_SHORT).show();
                return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Displays alert asking user if he wants to regenerate or generate new privateKey.
     *
     * @param bareJid the account bareJid
     * @param isKeyExist <tt>true</tt>if key exist
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
        b.setTitle(R.string.crypto_dialog_KEY_GENERATE_TITLE)
                .setMessage(message)
                .setPositiveButton(R.string.service_gui_PROCEED, (dialog, which) -> {
                    if (accountId != null) {
                        if (bareJid.startsWith(OMEMO))
                            regenerate(accountId);
                        else if (bareJid.startsWith(OTR))
                            keyManager.generateKeyPair(accountId);
                    }
                    accountsAdapter.notifyDataSetChanged();
                })
                .setNegativeButton(R.string.service_gui_CANCEL, (dialog, which) -> dialog.dismiss()).show();
    }

    /**
     * Regenerate the OMEMO keyPair parameters for the given accountId
     *
     * @param accountId the accountID
     */
    private void regenerate(AccountID accountId)
    {
        OmemoStore omemoStore = OmemoService.getInstance().getOmemoStoreBackend();
        ((SQLiteOmemoStore) omemoStore).regenerate(accountId);
    }

    /**
     * Adapter which displays privateKeys for the given list of accounts.
     */
    private class PrivateKeyListAdapter extends BaseAdapter
    {
        /**
         * The list of currently displayed devices and FingerPrints.
         */
        private final List<String> deviceJid;
        private final List<String> deviceFP;

        /**
         * Creates new instance of <tt>FingerprintListAdapter</tt>.
         *
         * @param fingerprintList list of <tt>device</tt> for which OMEMO/OTR fingerprints will be displayed.
         */
        PrivateKeyListAdapter(Map<String, String> fingerprintList)
        {
            deviceJid = new ArrayList<>(fingerprintList.keySet());
            deviceFP = new ArrayList<>(fingerprintList.values());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount()
        {
            return deviceFP.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getItem(int position)
        {
            return getBareJidFromRow(position);
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
                rowView = getLayoutInflater().inflate(R.layout.crypto_privkey_list_row, parent, false);

            String bareJid = getBareJidFromRow(position);
            ViewUtil.setTextViewValue(rowView, R.id.protocolProvider, bareJid);

            String fingerprint = getOwnKeyFromRow(position);
            String fingerprintStr = fingerprint;
            if (StringUtils.isEmpty(fingerprint)) {
                fingerprintStr = getString(R.string.crypto_NO_KEY_PRESENT);
            }
            ViewUtil.setTextViewValue(rowView, R.id.fingerprint, CryptoHelper.prettifyFingerprint(fingerprintStr));
            return rowView;
        }

        String getBareJidFromRow(int row)
        {
            return deviceJid.get(row);
        }

        String getOwnKeyFromRow(int row)
        {
            return deviceFP.get(row);
        }
    }
}
