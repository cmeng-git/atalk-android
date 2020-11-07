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
package org.atalk.crypto.omemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.CryptoHelper;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.trust.TrustState;
import org.jxmpp.jid.BareJid;

import java.io.IOException;
import java.util.*;

import timber.log.Timber;

import static org.atalk.android.R.id.fingerprint;

/**
 * OMEMO buddy authenticate dialog.
 *
 * @author Eng Chong Meng
 */
public class OmemoAuthenticateDialog extends OSGiActivity
{
    public final static String Corrupted_OmemoKey = "Corrupted OmemoKey, purge?";

    private static OmemoManager mOmemoManager;
    private static Set<OmemoDevice> mOmemoDevices;

    private static AuthenticateListener mListener;
    private SQLiteOmemoStore mOmemoStore;

    private final HashMap<OmemoDevice, String> buddyFingerprints = new HashMap<>();
    private final LinkedHashMap<OmemoDevice, FingerprintStatus> deviceFPStatus = new LinkedHashMap<>();
    private final HashMap<OmemoDevice, Boolean> fingerprintCheck = new HashMap<>();

    /**
     * Fingerprints adapter instance.
     */
    private FingerprintListAdapter fpListAdapter;

    /**
     * Creates parametrized <tt>Intent</tt> of buddy authenticate dialog.
     *
     * @param omemoManager the UUID of OTR session.
     * @return buddy authenticate dialog parametrized with given OTR session's UUID.
     */
    public static Intent createIntent(Context context, OmemoManager omemoManager, Set<OmemoDevice> omemoDevices,
            AuthenticateListener listener)
    {
        Intent intent = new Intent(context, OmemoAuthenticateDialog.class);

        mOmemoManager = omemoManager;
        mOmemoDevices = omemoDevices;
        mListener = listener;

        // Started not from Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        try {
            mOmemoStore = (SQLiteOmemoStore) SignalOmemoService.getInstance().getOmemoStoreBackend();
            // IllegalStateException from the field?
        } catch (IllegalStateException ex) {
            finish();
        }

        setContentView(R.layout.omemo_authenticate_dialog);
        setTitle(R.string.omemo_authbuddydialog_AUTHENTICATE_BUDDY);

        fpListAdapter = new FingerprintListAdapter(getBuddyFingerPrints());
        ListView fingerprintsList = findViewById(R.id.fp_list);
        fingerprintsList.setAdapter(fpListAdapter);

        // userJid may be null
        BareJid userJid = mOmemoManager.getOwnJid();
        String localFingerprint = null;
        try {
            localFingerprint = mOmemoManager.getOwnFingerprint().toString();
        } catch (SmackException.NotLoggedInException | CorruptedOmemoKeyException | IOException e) {
            Timber.w("Get own fingerprint exception: %s", e.getMessage());
        }

        View content = findViewById(android.R.id.content);
        ViewUtil.setTextViewValue(content, R.id.localFingerprintLbl,
                getString(R.string.omemo_authbuddydialog_LOCAL_FINGERPRINT, userJid,
                        CryptoHelper.prettifyFingerprint(localFingerprint)));
    }

    /**
     * Gets the list of all known buddyFPs.
     *
     * @return the list of all known buddyFPs.
     */
    Map<OmemoDevice, String> getBuddyFingerPrints()
    {
        String fingerprint;
        FingerprintStatus fpStatus;

        if (mOmemoDevices != null) {
            for (OmemoDevice device : mOmemoDevices) {
                // Default all devices' trust to false
                fingerprintCheck.put(device, false);
                try {
                    fingerprint = mOmemoManager.getFingerprint(device).toString();
                    buddyFingerprints.put(device, fingerprint);

                    fpStatus = mOmemoStore.getFingerprintStatus(device, fingerprint);
                    deviceFPStatus.put(device, fpStatus);
                } catch (CorruptedOmemoKeyException | CannotEstablishOmemoSessionException e) {
                    buddyFingerprints.put(device, Corrupted_OmemoKey);
                    deviceFPStatus.put(device, null);
                } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException
                        | SmackException.NoResponseException | InterruptedException | IOException e) {
                    Timber.w("Smack exception in fingerPrint fetch for omemo device: %s", device);
                }
            }
        }
        return buddyFingerprints;
    }

    /**
     * Method fired when the ok button is clicked.
     *
     * @param v ok button's <tt>View</tt>.
     */
    public void onOkClicked(View v)
    {
        boolean allTrusted = true;
        String fingerprint;

        for (Map.Entry<OmemoDevice, Boolean> entry : fingerprintCheck.entrySet()) {
            OmemoDevice omemoDevice = entry.getKey();
            Boolean fpCheck = entry.getValue();
            allTrusted = fpCheck && allTrusted;
            if (fpCheck) {
                fingerprint = buddyFingerprints.get(omemoDevice);
                if (Corrupted_OmemoKey.equals(fingerprint)) {
                    mOmemoStore.purgeOwnDeviceKeys(omemoDevice);
                    mOmemoDevices.remove(omemoDevice);
                }
                else {
                    trustOmemoFingerPrint(omemoDevice, fingerprint);
                    mOmemoDevices.remove(omemoDevice);
                }
            }
            else {
                /* Do not change original fingerprint trust state */
                Timber.w("Leaving the fingerprintStatus as it: %s", omemoDevice);
            }
        }
        if (mListener != null)
            mListener.onAuthenticate(allTrusted, mOmemoDevices);
        finish();
    }

    /**
     * Method fired when the cancel button is clicked.
     *
     * @param v the cancel button's <tt>View</tt>
     */
    public void onCancelClicked(View v)
    {
        if (mListener != null)
            mListener.onAuthenticate(false, mOmemoDevices);
        finish();
    }

    // ============== OMEMO Buddy FingerPrints Handlers ================== //
    private boolean isOmemoFPVerified(OmemoDevice omemoDevice, String fingerprint)
    {
        FingerprintStatus fpStatus = mOmemoStore.getFingerprintStatus(omemoDevice, fingerprint);
        return ((fpStatus != null) && fpStatus.isTrusted());
    }

    /**
     * Trust an OmemoIdentity. This involves marking the key as trusted.
     *
     * @param omemoDevice OmemoDevice
     * @param remoteFingerprint fingerprint.
     */
    private void trustOmemoFingerPrint(OmemoDevice omemoDevice, String remoteFingerprint)
    {
        OmemoFingerprint omemoFingerprint = new OmemoFingerprint(remoteFingerprint);
        mOmemoStore.getTrustCallBack().setTrust(omemoDevice, omemoFingerprint, TrustState.trusted);
    }

    /**
     * Adapter displays fingerprints for given list of <tt>Contact</tt>s.
     */
    private class FingerprintListAdapter extends BaseAdapter
    {
        /**
         * The list of currently displayed buddy FingerPrints.
         */
        private final Map<OmemoDevice, String> buddyFPs;

        /**
         * Creates new instance of <tt>FingerprintListAdapter</tt>.
         *
         * @param linkedHashMap list of <tt>Contact</tt> for which OTR fingerprints will be displayed.
         */
        FingerprintListAdapter(Map<OmemoDevice, String> linkedHashMap)
        {
            buddyFPs = linkedHashMap;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount()
        {
            return buddyFPs.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getItem(int position)
        {
            return getOmemoDeviceFromRow(position);
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
                rowView = getLayoutInflater().inflate(R.layout.omemo_fingerprint_row, parent, false);

            final OmemoDevice device = getOmemoDeviceFromRow(position);
            String remoteFingerprint = getFingerprintFromRow(position);

            ViewUtil.setTextViewValue(rowView, R.id.protocolProvider, device.toString());
            ViewUtil.setTextViewValue(rowView, fingerprint, CryptoHelper.prettifyFingerprint(remoteFingerprint));

            boolean isVerified = isOmemoFPVerified(device, remoteFingerprint);
            final CheckBox cb_fingerprint = rowView.findViewById(R.id.fingerprint);
            cb_fingerprint.setChecked(isVerified);

            cb_fingerprint.setOnClickListener(v -> fingerprintCheck.put(device, cb_fingerprint.isChecked()));
            return rowView;
        }

        OmemoDevice getOmemoDeviceFromRow(int row)
        {
            int index = -1;
            for (OmemoDevice device : buddyFingerprints.keySet()) {
                index++;
                if (index == row) {
                    return device;
                }
            }
            return null;
        }

        String getFingerprintFromRow(int row)
        {
            int index = -1;
            for (String fingerprint : buddyFingerprints.values()) {
                index++;
                if (index == row) {
                    return fingerprint;
                }
            }
            return null;
        }
    }

    /**
     * The listener that will be notified when user clicks the confirm button or dismisses the dialog.
     */
    public interface AuthenticateListener
    {
        /**
         * Fired when user clicks the dialog's confirm/cancel button.
         *
         * @param allTrusted allTrusted state.
         * @param omemoDevices set of unTrusted devices
         */
        void onAuthenticate(boolean allTrusted, Set<OmemoDevice> omemoDevices);
    }
}
