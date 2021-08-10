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

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.plugin.otr.*;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.ThemeHelper;
import org.atalk.android.gui.util.ThemeHelper.Theme;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.crypto.omemo.FingerprintStatus;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.CryptoHelper;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoKeyUtil;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.trust.TrustState;
import org.whispersystems.libsignal.IdentityKey;

import java.io.IOException;
import java.util.*;

import timber.log.Timber;

import static org.atalk.android.R.id.fingerprint;

/**
 * Settings screen with known user account and its associated fingerprints
 *
 * @author Eng Chong Meng
 */
public class CryptoDeviceFingerPrints extends OSGiActivity
{
    private static final String OTR = "OTR:";
    private static final String OMEMO = "OMEMO:";

    private SQLiteDatabase mDB;
    private SQLiteOmemoStore mOmemoStore;
    private ScOtrKeyManager keyManager = OtrActivator.scOtrKeyManager;

    /* Fingerprints adapter instance. */
    private FingerprintListAdapter fpListAdapter;

    /* Map contains omemo devices and theirs associated fingerPrint */
    private final Map<String, String> deviceFingerprints = new TreeMap<>();

    /* Map contains userDevice and its associated FingerPrintStatus */
    private final LinkedHashMap<String, FingerprintStatus> omemoDeviceFPStatus = new LinkedHashMap<>();

    /* List contains all the own OmemoDevice */
    private final List<String> ownOmemoDevice = new ArrayList<>();

    /* Map contains bareJid and its associated Contact */
    private final HashMap<String, Contact> contactList = new HashMap<>();

    private Contact contact;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mDB = DatabaseBackend.getReadableDB();
        mOmemoStore = (SQLiteOmemoStore) SignalOmemoService.getInstance().getOmemoStoreBackend();
        setContentView(R.layout.list_layout);

        fpListAdapter = new FingerprintListAdapter(getDeviceFingerPrints());
        ListView fingerprintsList = findViewById(R.id.list);
        fingerprintsList.setAdapter(fpListAdapter);
        registerForContextMenu(fingerprintsList);
    }

    /**
     * Gets the list of all known fingerPrints for both OMEMO and OTR.
     *
     * @return a map of all known Map<bareJid, fingerPrints>.
     */
    Map<String, String> getDeviceFingerPrints()
    {
        // Get the protocol providers and meta-contactList service
        Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
        MetaContactListService mclService = AndroidGUIActivator.getContactListService();
        List<String> fpList;

        // Get all the omemoDevices' fingerPrints from database
        getOmemoDeviceFingerprintStatus();

        for (ProtocolProviderService pps : providers) {
            if (pps.getConnection() == null)
                continue;

            // Generate a list of own omemoDevices
            OmemoManager omemoManager = OmemoManager.getInstanceFor(pps.getConnection());
            String userDevice = OMEMO + omemoManager.getOwnDevice();
            ownOmemoDevice.add(userDevice);

            // Get OTR contacts' fingerPrints
            Iterator<MetaContact> metaContacts = mclService.findAllMetaContactsForProvider(pps);
            while (metaContacts.hasNext()) {
                MetaContact metaContact = metaContacts.next();
                Iterator<Contact> contacts = metaContact.getContacts();
                while (contacts.hasNext()) {
                    contact = contacts.next();
                    String bareJid = OTR + contact.getAddress();
                    if (!contactList.containsKey(bareJid)) {
                        contactList.put(bareJid, contact);
                        fpList = keyManager.getAllRemoteFingerprints(contact);
                        if ((fpList != null) && !fpList.isEmpty()) {
                            for (String fp : fpList) {
                                deviceFingerprints.put(bareJid, fp);
                            }
                        }
                    }
                }
            }
        }
        return deviceFingerprints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        boolean isVerified = false;
        boolean keyExists = true;

        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.fingerprint_ctx_menu, menu);
        MenuItem mTrust = menu.findItem(R.id.trust);
        MenuItem mDistrust = menu.findItem(R.id.distrust);

        ListView.AdapterContextMenuInfo ctxInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
        int pos = ctxInfo.position;

        String remoteFingerprint = fpListAdapter.getFingerprintFromRow(pos);
        String bareJid = fpListAdapter.getBareJidFromRow(pos);
        if (bareJid.startsWith(OMEMO)) {
            isVerified = isOmemoFPVerified(bareJid, remoteFingerprint);
        }
        else if (bareJid.startsWith(OTR)) {
            contact = contactList.get(bareJid);
            isVerified = keyManager.isVerified(contact, remoteFingerprint);
            keyExists = keyManager.getAllRemoteFingerprints(contact) != null;
        }

        // set visibility of trust option menu based on fingerPrint state
        mTrust.setVisible(!isVerified && keyExists);
        mDistrust.setVisible(isVerified);
        if ((bareJid.startsWith(OMEMO))
                && (isOwnOmemoDevice(bareJid) || !isOmemoDeviceActive(bareJid))) {
            mTrust.setVisible(false);
            mDistrust.setVisible(false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        int pos = info.position;
        String bareJid = fpListAdapter.getBareJidFromRow(pos);
        String remoteFingerprint = fpListAdapter.getFingerprintFromRow(pos);
        contact = contactList.get(bareJid);
        OtrContact otrContact = OtrContactManager.getOtrContact(contact, null);

        int id = item.getItemId();
        switch (id) {
            case R.id.trust:
                if (bareJid.startsWith(OMEMO)) {
                    trustOmemoFingerPrint(bareJid, remoteFingerprint);
                    String msg = getString(R.string.crypto_toast_OMEMO_TRUST_MESSAGE_RESUME, bareJid);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                else {
                    keyManager.verify(otrContact, remoteFingerprint);
                }
                fpListAdapter.notifyDataSetChanged();
                return true;

            case R.id.distrust:
                if (bareJid.startsWith(OMEMO)) {
                    distrustOmemoFingerPrint(bareJid, remoteFingerprint);
                    String msg = getString(R.string.crypto_toast_OMEMO_DISTRUST_MESSAGE_STOP, bareJid);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                else {
                    keyManager.unverify(otrContact, remoteFingerprint);
                }
                fpListAdapter.notifyDataSetChanged();
                return true;

            case R.id.copy:
                ClipboardManager cbManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cbManager != null) {
                    cbManager.setPrimaryClip(ClipData.newPlainText(null,
                            CryptoHelper.prettifyFingerprint(remoteFingerprint)));
                    Toast.makeText(this, R.string.crypto_toast_FINGERPRINT_COPY, Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.cancel:
                return true;
        }
        return super.onContextItemSelected(item);
    }

    // ============== OMEMO Device FingerPrintStatus Handlers ================== //

    /**
     * Fetch the OMEMO FingerPrints for all the device
     * Remove all those Devices has null fingerPrints
     */
    private void getOmemoDeviceFingerprintStatus()
    {
        FingerprintStatus fpStatus;
        Cursor cursor = mDB.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null,
                null, null, null, null, null);

        while (cursor.moveToNext()) {
            fpStatus = FingerprintStatus.fromCursor(cursor);
            if (fpStatus != null) {
                String bareJid = OMEMO + fpStatus.getOmemoDevice();
                omemoDeviceFPStatus.put(bareJid, fpStatus);
                deviceFingerprints.put(bareJid, fpStatus.getFingerPrint());
            }
        }
        cursor.close();
    }

    /**
     * Get the trust state of fingerPrint from database. Do not get from local copy of omemoDeviceFPStatus as
     * trust state if not being updated
     *
     * @param userDevice OmemoDevice
     * @param fingerprint OmemoFingerPrint
     * @return boolean trust state
     */
    private boolean isOmemoFPVerified(String userDevice, String fingerprint)
    {
        OmemoDevice omemoDevice = getOmemoDevice(userDevice);
        FingerprintStatus fpStatus = mOmemoStore.getFingerprintStatus(omemoDevice, fingerprint);
        return ((fpStatus != null) && fpStatus.isTrusted());
    }

    private boolean isOmemoDeviceActive(String userDevice)
    {
        FingerprintStatus fpStatus = omemoDeviceFPStatus.get(userDevice);
        return ((fpStatus != null) && fpStatus.isActive());
    }

    private boolean isOwnOmemoDevice(String userDevice)
    {
        return ownOmemoDevice.contains(userDevice);
    }

    private OmemoDevice getOmemoDevice(String userDevice)
    {
        FingerprintStatus fpStatus = omemoDeviceFPStatus.get(userDevice);
        return fpStatus.getOmemoDevice();
    }

    /**
     * Trust an OmemoIdentity. This involves marking the key as trusted.
     *
     * @param bareJid BareJid
     * @param remoteFingerprint fingerprint
     */
    private void trustOmemoFingerPrint(String bareJid, String remoteFingerprint)
    {
        OmemoDevice omemoDevice = getOmemoDevice(bareJid);
        OmemoFingerprint omemoFingerprint = new OmemoFingerprint(remoteFingerprint);
        mOmemoStore.getTrustCallBack().setTrust(omemoDevice, omemoFingerprint, TrustState.trusted);
    }

    /**
     * Distrust an OmemoIdentity. This involved marking the key as distrusted.
     *
     * @param bareJid bareJid
     * @param remoteFingerprint fingerprint
     */
    private void distrustOmemoFingerPrint(String bareJid, String remoteFingerprint)
    {
        OmemoDevice omemoDevice = getOmemoDevice(bareJid);
        OmemoFingerprint omemoFingerprint = new OmemoFingerprint(remoteFingerprint);
        mOmemoStore.getTrustCallBack().setTrust(omemoDevice, omemoFingerprint, TrustState.untrusted);
    }

    //==============================================================

    /**
     * Adapter displays fingerprints for given list of <tt>omemoDevices</tt>s and <tt>contacts</tt>.
     */
    private class FingerprintListAdapter extends BaseAdapter
    {
        /**
         * The list of currently displayed devices and FingerPrints.
         */
        private final List<String> deviceJid;
        private final List<String> deviceFP;

        /**
         * Creates new instance of <tt>FingerprintListAdapter</tt>.
         *
         * @param linkedHashMap list of <tt>device</tt> for which OMEMO/OTR fingerprints will be displayed.
         */
        FingerprintListAdapter(Map<String, String> linkedHashMap)
        {
            deviceJid = new ArrayList<>(linkedHashMap.keySet());
            deviceFP = new ArrayList<>(linkedHashMap.values());
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
                rowView = getLayoutInflater().inflate(R.layout.crypto_fingerprint_row, parent, false);

            boolean isVerified = false;
            String bareJid = getBareJidFromRow(position);
            String remoteFingerprint = getFingerprintFromRow(position);

            ViewUtil.setTextViewValue(rowView, R.id.protocolProvider, bareJid);
            ViewUtil.setTextViewValue(rowView, fingerprint, CryptoHelper.prettifyFingerprint(remoteFingerprint));

            // Color for active fingerPrints
            ViewUtil.setTextViewColor(rowView, fingerprint,
                    ThemeHelper.isAppTheme(Theme.DARK) ? R.color.textColorWhite : R.color.textColorBlack);

            if (bareJid.startsWith(OMEMO)) {
                if (isOwnOmemoDevice(bareJid))
                    ViewUtil.setTextViewColor(rowView, fingerprint, R.color.blue);
                else if (!isOmemoDeviceActive(bareJid))
                    ViewUtil.setTextViewColor(rowView, fingerprint, R.color.grey500);

                isVerified = isOmemoFPVerified(bareJid, remoteFingerprint);
            }
            else if (bareJid.startsWith(OTR)) {
                contact = contactList.get(bareJid);
                isVerified = keyManager.isVerified(contact, remoteFingerprint);
            }

            int status = isVerified ? R.string.crypto_FINGERPRINT_VERIFIED : R.string.crypto_FINGERPRINT_NOT_VERIFIED;
            String verifyStatus = getString(R.string.crypto_FINGERPRINT_STATUS, getString(status));
            ViewUtil.setTextViewValue(rowView, R.id.fingerprint_status, verifyStatus);
            ViewUtil.setTextViewColor(rowView, R.id.fingerprint_status, isVerified ?
                    (ThemeHelper.isAppTheme(Theme.DARK) ? R.color.textColorWhite : R.color.textColorBlack)
                    : R.color.orange500);
            return rowView;
        }

        String getBareJidFromRow(int row)
        {
            return deviceJid.get(row);
        }

        String getFingerprintFromRow(int row)
        {
            return deviceFP.get(row);
        }
    }
}
