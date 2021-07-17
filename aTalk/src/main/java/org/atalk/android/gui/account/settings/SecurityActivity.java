/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.*;
import android.view.KeyEvent;
import android.widget.Toast;

import net.java.sip.communicator.service.protocol.SecurityAccountRegistration;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.service.neomedia.SDesControl;
import org.atalk.service.osgi.OSGiActivity;
import org.osgi.framework.BundleContext;

import java.io.Serializable;
import java.util.*;

import ch.imvs.sdes4j.srtp.SrtpCryptoSuite;

/**
 * The activity allows user to edit security part of account settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class SecurityActivity extends OSGiActivity implements SecurityProtocolsDialogFragment.DialogClosedListener
{
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

    private static final String PREF_KEY_SEC_ENABLED = aTalkApp.getResString(R.string.pref_key_enable_encryption);

    private static final String PREF_KEY_SEC_PROTO_DIALOG = aTalkApp.getResString(R.string.pref_key_enc_protocols_dialog);

    private static final String PREF_KEY_SEC_SIPZRTP_ATTR = aTalkApp.getResString(R.string.pref_key_enc_sipzrtp_attr);

    private static final String PREF_KEY_SEC_CIPHER_SUITES = aTalkApp.getResString(R.string.pref_key_enc_cipher_suites);

    private static final String PREF_KEY_SEC_SAVP_OPTION = aTalkApp.getResString(R.string.pref_key_enc_savp_option);

    private static final String PREF_KEY_SEC_RESET_ZID = aTalkApp.getResString(R.string.pref_key_zid_reset);

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

    @Override
    protected void start(BundleContext bundleContext)
            throws Exception
    {
        super.start(bundleContext);
    }

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            securityFragment = new SecurityPreferenceFragment();

            // Display the fragment as the main content.
            getFragmentManager().beginTransaction().replace(android.R.id.content, securityFragment).commit();
        }
        else {
            securityFragment = (SecurityPreferenceFragment) getFragmentManager().findFragmentById(android.R.id.content);
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
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

    public void onDialogClosed(SecurityProtocolsDialogFragment dialog)
    {
        securityFragment.onDialogClosed(dialog);
    }

    /**
     * Fragment handles {@link Preference}s used for manipulating security settings.
     */
    public static class SecurityPreferenceFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        private static final String STATE_SEC_REG = "security_reg";

        private final SummaryMapper summaryMapper = new SummaryMapper();

        /**
         * Flag indicating if any changes have been made in this activity
         */
        protected boolean hasChanges = false;

        protected SecurityAccountRegistration securityReg;

        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            if (savedInstanceState == null) {
                Intent intent = getActivity().getIntent();
                securityReg = (SecurityAccountRegistration) intent.getSerializableExtra(EXTR_KEY_SEC_REGISTRATION);
            }
            else {
                securityReg = (SecurityAccountRegistration) savedInstanceState.get(STATE_SEC_REG);
            }

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.acc_encoding_preferences);

            Preference secProtocolsPref = findPreference(PREF_KEY_SEC_PROTO_DIALOG);
            secProtocolsPref.setOnPreferenceClickListener(preference -> {
                showEditSecurityProtocolsDialog();
                return true;
            });

            ListPreference savpPreference = (ListPreference) findPreference(PREF_KEY_SEC_SAVP_OPTION);
            savpPreference.setValueIndex(securityReg.getSavpOption());

            summaryMapper.includePreference(savpPreference, "");

            CheckBoxPreference encEnabled = (CheckBoxPreference) findPreference(PREF_KEY_SEC_ENABLED);
            encEnabled.setChecked(securityReg.isDefaultEncryption());

            CheckBoxPreference zrtpAttr = (CheckBoxPreference) findPreference(PREF_KEY_SEC_SIPZRTP_ATTR);
            zrtpAttr.setChecked(securityReg.isSipZrtpAttribute());

            loadCipherSuites();

            initResetZID();
        }

        /**
         * Loads cipher suites
         */
        private void loadCipherSuites()
        {
            // TODO: fix static values initialization and default ciphers
            String ciphers = securityReg.getSDesCipherSuites();
            if (ciphers == null)
                ciphers = defaultCiphers;

            MultiSelectListPreference cipherList = (MultiSelectListPreference) findPreference(PREF_KEY_SEC_CIPHER_SUITES);

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

        private void initResetZID()
        {
            findPreference(PREF_KEY_SEC_RESET_ZID).setOnPreferenceClickListener(
                    preference -> {
                        securityReg.randomZIDSalt();
                        hasChanges = true;
                        Toast.makeText(getActivity(), R.string.ZID_has_been_reset_toast, Toast.LENGTH_SHORT).show();
                        return true;
                    }
            );
        }

        @Override
        public void onSaveInstanceState(Bundle outState)
        {
            super.onSaveInstanceState(outState);
            outState.putSerializable(STATE_SEC_REG, securityReg);
        }

        /**
         * Shows the dialog that will allow user to edit security protocols settings
         */
        private void showEditSecurityProtocolsDialog()
        {
            SecurityProtocolsDialogFragment securityDialog = new SecurityProtocolsDialogFragment();

            Map<String, Integer> encryptions = securityReg.getEncryptionProtocols();
            Map<String, Boolean> statusMap = securityReg.getEncryptionProtocolStatus();
            Bundle args = new Bundle();
            args.putSerializable(SecurityProtocolsDialogFragment.ARG_ENCRYPTIONS, (Serializable) encryptions);
            args.putSerializable(SecurityProtocolsDialogFragment.ARG_STATUS_MAP, (Serializable) statusMap);
            securityDialog.setArguments(args);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            securityDialog.show(ft, "SecProtocolsDlgFragment");
        }

        void onDialogClosed(SecurityProtocolsDialogFragment dialog)
        {
            if (dialog.hasChanges()) {
                hasChanges = true;
                dialog.commit(securityReg);
            }
            updateUsedProtocolsSummary();
        }

        @Override
        public void onResume()
        {
            super.onResume();
            updatePreferences();
            SharedPreferences shPrefs = getPreferenceScreen().getSharedPreferences();
            shPrefs.registerOnSharedPreferenceChangeListener(this);
            shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);
        }

        @Override
        public void onPause()
        {
            SharedPreferences shPrefs = getPreferenceScreen().getSharedPreferences();
            shPrefs.unregisterOnSharedPreferenceChangeListener(this);
            shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper);
            super.onPause();
        }

        /**
         * Refresh specifics summaries
         */
        private void updatePreferences()
        {
            updateUsedProtocolsSummary();
            updateZRTpOptionSummary();
            updateCipherSuitesSummary();
        }

        /**
         * Sets the summary for protocols preference
         */
        private void updateUsedProtocolsSummary()
        {
            final Map<String, Integer> encMap = securityReg.getEncryptionProtocols();
            List<String> encryptionsInOrder = new ArrayList<>(encMap.keySet());

            Collections.sort(encryptionsInOrder, (s, s2) -> encMap.get(s) - encMap.get(s2));

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
                summaryStr = aTalkApp.getResString(R.string.service_gui_LIST_NONE);
            }

            Preference preference = findPreference(PREF_KEY_SEC_PROTO_DIALOG);
            preference.setSummary(summaryStr);
        }

        /**
         * Sets the ZRTP signaling preference summary
         */
        private void updateZRTpOptionSummary()
        {
            Preference pref = findPreference(PREF_KEY_SEC_SIPZRTP_ATTR);
            boolean isOn = pref.getSharedPreferences().getBoolean(PREF_KEY_SEC_SIPZRTP_ATTR, true);

            String sumary = isOn
                    ? aTalkApp.getResString(R.string.service_gui_SEC_ZRTP_SIGNALING_ON)
                    : aTalkApp.getResString(R.string.service_gui_SEC_ZRTP_SIGNALING_OFF);

            pref.setSummary(sumary);
        }

        /**
         * Sets the cipher suites preference summary
         */
        private void updateCipherSuitesSummary()
        {
            MultiSelectListPreference ml = (MultiSelectListPreference) findPreference(PREF_KEY_SEC_CIPHER_SUITES);
            String summary = getCipherSuitesSummary(ml);
            ml.setSummary(summary);
        }

        /**
         * Gets the summary text for given cipher suites preference
         *
         * @param ml the preference used for cipher suites setup
         * @return the summary text describing currently selected cipher suites
         */
        private String getCipherSuitesSummary(MultiSelectListPreference ml)
        {
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
                sb.append(aTalkApp.getResString(R.string.service_gui_LIST_NONE));
            return sb.toString();
        }

        public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key)
        {
            hasChanges = true;
            if (key.equals(PREF_KEY_SEC_ENABLED)) {
                securityReg.setDefaultEncryption(shPreferences.getBoolean(PREF_KEY_SEC_ENABLED, true));
            }
            else if (key.equals(PREF_KEY_SEC_SIPZRTP_ATTR)) {
                updateZRTpOptionSummary();
                securityReg.setSipZrtpAttribute(shPreferences.getBoolean(key, true));
            }
            else if (key.equals(PREF_KEY_SEC_SAVP_OPTION)) {
                ListPreference lp = (ListPreference) findPreference(key);
                int idx = lp.findIndexOfValue(lp.getValue());
                securityReg.setSavpOption(idx);
            }
            else if (key.equals(PREF_KEY_SEC_CIPHER_SUITES)) {
                MultiSelectListPreference ml = (MultiSelectListPreference) findPreference(key);
                String summary = getCipherSuitesSummary(ml);
                ml.setSummary(summary);
                securityReg.setSDesCipherSuites(summary);
            }
        }
    }
}
