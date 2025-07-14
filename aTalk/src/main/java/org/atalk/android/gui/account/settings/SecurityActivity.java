/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.core.content.IntentCompat;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.service.protocol.SecurityAccountRegistration;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.settings.BasePreferenceFragment;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.service.neomedia.SDesControl;

import ch.imvs.sdes4j.srtp.SrtpCryptoSuite;

/**
 * The activity allows user to edit security part of account settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class SecurityActivity extends BaseActivity implements SecurityProtocolsDialogFragment.DialogClosedListener {
    /**
     * The intent's extra key for passing the {@link SecurityAccountRegistration}
     */
    public static final String EXTR_KEY_SEC_REGISTRATION = "secRegObj";

    /**
     * The intent's extra key of boolean indicating if any changes have been made by this activity
     */
    public static final String EXTR_KEY_HAS_CHANGES = "hasChanges";

    /**
     * Default value for cipher suites string property
     */
    private static final String defaultCiphers = UtilActivator.getResources().getSettingsString(SDesControl.SDES_CIPHER_SUITES);

    private static final String PREF_KEY_SEC_ENABLED = "pref_key_enable_encryption";

    private static final String PREF_KEY_SEC_PROTO_DIALOG = "pref_key_enc_protos_dialog";

    private static final String PREF_KEY_SEC_SIPZRTP_ATTR = "pref_key_enc_sipzrtp_attr";

    private static final String PREF_KEY_SEC_CIPHER_SUITES = "pref_key_ecn_cipher_suites";

    private static final String PREF_KEY_SEC_SAVP_OPTION = "pref_key_enc_savp_option";

    private static final String PREF_KEY_SEC_RESET_ZID = "pref.key.zid.reset";

    private static final String PREF_KEY_SEC_DTLS_CERT_SA = "pref_key_enc_dtls_cert_signature_algorithm";

    private static final String[] cryptoSuiteEntries = {
            SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_80,
            SrtpCryptoSuite.AES_256_CM_HMAC_SHA1_32,
            SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_80,
            SrtpCryptoSuite.AES_192_CM_HMAC_SHA1_32,
            SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_80,
            SrtpCryptoSuite.AES_CM_128_HMAC_SHA1_32,
            SrtpCryptoSuite.F8_128_HMAC_SHA1_80
    };

    /**
     * Fragment implementing {@link Preference} support in this activity.
     */
    private SecurityPreferenceFragment securityFragment;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            securityFragment = new SecurityPreferenceFragment();

            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction().replace(android.R.id.content, securityFragment).commit();
        }
        else {
            securityFragment = (SecurityPreferenceFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
        }
    }

    public void onDialogClosed(SecurityProtocolsDialogFragment dialog) {
        securityFragment.onDialogClosed(dialog);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent result = new Intent();
            result.putExtra(EXTR_KEY_SEC_REGISTRATION, securityFragment.securityReg);
            result.putExtra(EXTR_KEY_HAS_CHANGES, securityFragment.hasChanges);
            setResult(Activity.RESULT_OK, result);
            finish();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Fragment handles {@link Preference}s used for manipulating security settings.
     */
    public static class SecurityPreferenceFragment extends BasePreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final String STATE_SEC_REG = "security_reg";

        private final SummaryMapper summaryMapper = new SummaryMapper();

        /**
         * Flag indicating if any changes have been made in this activity
         */
        protected boolean hasChanges = false;

        protected SecurityAccountRegistration securityReg;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPrefTitle(R.string.settings_messaging_security);
            if (savedInstanceState == null) {
                Intent intent = ((Activity) mContext).getIntent();
                securityReg = IntentCompat.getSerializableExtra(intent,
                        EXTR_KEY_SEC_REGISTRATION, SecurityAccountRegistration.class);
            }
            else {
                securityReg = BundleCompat.getSerializable(savedInstanceState, STATE_SEC_REG, SecurityAccountRegistration.class);
            }

            // Load the preferences from an XML resource - findPreference() to work properly
            addPreferencesFromResource(R.xml.acc_call_encryption_preferences);

            CheckBoxPreference encEnable = findPreference(PREF_KEY_SEC_ENABLED);
            encEnable.setChecked(securityReg.isCallEncryption());

            // ZRTP
            Preference secProtocolsPref = findPreference(PREF_KEY_SEC_PROTO_DIALOG);
            secProtocolsPref.setOnPreferenceClickListener(preference -> {
                showEditSecurityProtocolsDialog();
                return true;
            });

            CheckBoxPreference zrtpAttr = findPreference(PREF_KEY_SEC_SIPZRTP_ATTR);
            zrtpAttr.setChecked(securityReg.isSipZrtpAttribute());
            initResetZID();

            // DTLS_SRTP
            ListPreference dtlsPreference = findPreference(PREF_KEY_SEC_DTLS_CERT_SA);
            String tlsCertSA = securityReg.getDtlsCertSa();
            dtlsPreference.setValue(tlsCertSA);
            dtlsPreference.setSummary(tlsCertSA);

            // SDES
            ListPreference savpPreference = findPreference(PREF_KEY_SEC_SAVP_OPTION);
            savpPreference.setValueIndex(securityReg.getSavpOption());
            summaryMapper.includePreference(savpPreference, "");
            loadCipherSuites();
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putSerializable(STATE_SEC_REG, securityReg);
        }

        @Override
        public void onResume() {
            super.onResume();
            updatePreferences();
            SharedPreferences shPrefs = getPreferenceScreen().getSharedPreferences();
            shPrefs.registerOnSharedPreferenceChangeListener(this);
            shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);
        }

        @Override
        public void onPause() {
            SharedPreferences shPrefs = getPreferenceScreen().getSharedPreferences();
            shPrefs.unregisterOnSharedPreferenceChangeListener(this);
            shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper);
            super.onPause();
        }

        private void initResetZID() {
            findPreference(PREF_KEY_SEC_RESET_ZID).setOnPreferenceClickListener(
                    preference -> {
                        securityReg.randomZIDSalt();
                        hasChanges = true;
                        Toast.makeText(getActivity(), R.string.zid_reset_done, Toast.LENGTH_SHORT).show();
                        return true;
                    }
            );
        }

        /**
         * Loads cipher suites
         */
        private void loadCipherSuites() {
            // TODO: fix static values initialization and default ciphers
            String ciphers = securityReg.getSDesCipherSuites();
            if (ciphers == null)
                ciphers = defaultCiphers;

            MultiSelectListPreference cipherList = findPreference(PREF_KEY_SEC_CIPHER_SUITES);

            cipherList.setEntries(cryptoSuiteEntries);
            cipherList.setEntryValues(cryptoSuiteEntries);

            Set<String> selected;
            selected = new HashSet<>();
            if (ciphers != null) {
                for (String entry : cryptoSuiteEntries) {
                    if (ciphers.contains(entry))
                        selected.add(entry);
                }
            }
            cipherList.setValues(selected);
        }

        /**
         * Shows the dialog that will allow user to edit security protocols settings
         */
        private void showEditSecurityProtocolsDialog() {
            SecurityProtocolsDialogFragment securityDialog = new SecurityProtocolsDialogFragment();

            Map<String, Integer> encryption = securityReg.getEncryptionProtocol();
            Map<String, Boolean> encryptionStatus = securityReg.getEncryptionProtocolStatus();

            Bundle args = new Bundle();
            args.putSerializable(SecurityProtocolsDialogFragment.ENCRYPTION_PRIORITY, (Serializable) encryption);
            args.putSerializable(SecurityProtocolsDialogFragment.ENCRYPTION_STATE, (Serializable) encryptionStatus);
            securityDialog.setArguments(args);

            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            securityDialog.show(ft, "SecProtocolsDlgFragment");
        }

        void onDialogClosed(SecurityProtocolsDialogFragment dialog) {
            if (dialog.hasChanges()) {
                hasChanges = true;
                dialog.commit(securityReg);
            }
            updateUsedProtocolsSummary();
        }

        /**
         * Refresh specifics summaries
         */
        private void updatePreferences() {
            updateUsedProtocolsSummary();
            updateZRTpOptionSummary();
            updateCipherSuitesSummary();
        }

        /**
         * Sets the summary for protocols preference
         */
        private void updateUsedProtocolsSummary() {
            final Map<String, Integer> encMap = securityReg.getEncryptionProtocol();
            List<String> encryptionsInOrder = new ArrayList<>(encMap.keySet());

            encryptionsInOrder.sort(Comparator.comparingInt(encMap::get));

            Map<String, Boolean> encStatus = securityReg.getEncryptionProtocolStatus();
            StringBuilder summary = new StringBuilder();
            int idx = 1;
            for (String encryption : encryptionsInOrder) {
                if (Boolean.TRUE.equals(encStatus.get(encryption))) {
                    if (idx > 1)
                        summary.append(" ");
                    summary.append(idx++).append(". ").append(encryption);
                }
            }

            String summaryStr = summary.toString();
            if (summaryStr.isEmpty()) {
                summaryStr = aTalkApp.getResString(R.string.none);
            }

            Preference preference = findPreference(PREF_KEY_SEC_PROTO_DIALOG);
            preference.setSummary(summaryStr);
        }

        /**
         * Sets the ZRTP signaling preference summary
         */
        private void updateZRTpOptionSummary() {
            Preference pref = findPreference(PREF_KEY_SEC_SIPZRTP_ATTR);
            boolean isOn = pref.getSharedPreferences().getBoolean(PREF_KEY_SEC_SIPZRTP_ATTR, true);

            String sumary = isOn
                    ? aTalkApp.getResString(R.string.sec_zrtp_signalling_on)
                    : aTalkApp.getResString(R.string.sec_zrtp_signalling_off);

            pref.setSummary(sumary);
        }

        /**
         * Sets the cipher suites preference summary
         */
        private void updateCipherSuitesSummary() {
            MultiSelectListPreference ml = findPreference(PREF_KEY_SEC_CIPHER_SUITES);
            String summary = getCipherSuitesSummary(ml);
            ml.setSummary(summary);
        }

        /**
         * Gets the summary text for given cipher suites preference
         *
         * @param ml the preference used for cipher suites setup
         *
         * @return the summary text describing currently selected cipher suites
         */
        private String getCipherSuitesSummary(MultiSelectListPreference ml) {
            Set<String> selected = ml.getValues();
            StringBuilder sb = new StringBuilder();

            boolean firstElem = true;
            for (String entry : cryptoSuiteEntries) {
                if (selected.contains(entry)) {
                    if (firstElem) {
                        sb.append(entry);
                        firstElem = false;
                    }
                    else {
                        // separator must not have space. Otherwise, result in unknown crypto suite error.
                        sb.append(",");
                        sb.append(entry);
                    }
                }
            }

            if (selected.isEmpty())
                sb.append(aTalkApp.getResString(R.string.none));
            return sb.toString();
        }

        public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key) {
            hasChanges = true;
            switch (key) {
                case PREF_KEY_SEC_ENABLED:
                    securityReg.setCallEncryption(shPreferences.getBoolean(PREF_KEY_SEC_ENABLED, true));
                    break;
                case PREF_KEY_SEC_SIPZRTP_ATTR:
                    updateZRTpOptionSummary();
                    securityReg.setSipZrtpAttribute(shPreferences.getBoolean(key, true));
                    break;
                case PREF_KEY_SEC_DTLS_CERT_SA: {
                    ListPreference lp = findPreference(key);
                    String certSA = lp.getValue();
                    lp.setSummary(certSA);
                    securityReg.setDtlsCertSa(certSA);
                    break;
                }
                case PREF_KEY_SEC_SAVP_OPTION: {
                    ListPreference lp = findPreference(key);
                    int idx = lp.findIndexOfValue(lp.getValue());
                    securityReg.setSavpOption(idx);
                    break;
                }
                case PREF_KEY_SEC_CIPHER_SUITES:
                    MultiSelectListPreference ml = findPreference(key);
                    String summary = getCipherSuitesSummary(ml);
                    ml.setSummary(summary);
                    securityReg.setSDesCipherSuites(summary);
                    break;
            }
        }
    }
}
