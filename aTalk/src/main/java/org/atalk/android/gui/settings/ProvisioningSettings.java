/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import net.java.sip.communicator.plugin.provisioning.ProvisioningActivator;
import net.java.sip.communicator.plugin.provisioning.ProvisioningServiceImpl;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.util.StringUtils;

/**
 * Provisioning preferences Settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ProvisioningSettings extends BaseActivity {
    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setMainTitle(R.string.provisioning);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends BasePreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        /**
         * value defined in preference keys
         */
        private final static String P_KEY_PROVISIONING_METHOD = ProvisioningServiceImpl.PROVISIONING_METHOD_PROP;
        private final static String P_KEY_UUID = ProvisioningServiceImpl.PROVISIONING_UUID_PROP;
        private final static String P_KEY_URL = ProvisioningServiceImpl.PROVISIONING_URL_PROP;
        private final static String P_KEY_USERNAME = ProvisioningServiceImpl.PROVISIONING_USERNAME_PROP;
        private final static String P_KEY_PASSWORD = ProvisioningServiceImpl.PROVISIONING_PASSWORD_PROP;
        private final static String P_KEY_FORGET_PASSWORD = "pref.key.provisioning.FORGET_PASSWORD";

        private CredentialsStorageService credentialsService;
        private ConfigurationService mConfig;
        private SharedPreferences mPref;
        private final SummaryMapper summaryMapper = new SummaryMapper();

        /**
         * Username edit text
         */
        private EditTextPreference prefUsername;

        /**
         * Password edit text; Do not use ConfigEditText preference,
         * else another copy of the unencrypted pwd is also stored in DB.
         */
        private EditTextPreference prefPassword;
        private Preference prefForgetPass;

        /**
         * {@inheritDoc}
         * All setText() will be handled by onCreatePreferences itself on init
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.provisioning_preferences, rootKey);
            mConfig = AppGUIActivator.getConfigurationService();
            mPref = getPreferenceManager().getSharedPreferences();
            credentialsService = ProvisioningActivator.getCredentialsStorageService();

            // Load UUID
            // EditTextPreference uuidPref = findPreference(P_KEY_UUID);
            // uuidPref.setText(mConfig.getString(ProvisioningServiceImpl.PROVISIONING_UUID_PROP));

            // Initialize username and password fields
            prefUsername = findPreference(P_KEY_USERNAME);
            prefUsername.setEnabled(true);
            // usernamePreference.setText(mConfig.getString(ProvisioningServiceImpl.PROVISIONING_USERNAME_PROP));

            String password = credentialsService.loadPassword(ProvisioningServiceImpl.PROVISIONING_PASSWORD_PROP);
            prefPassword = findPreference(P_KEY_PASSWORD);
            summaryMapper.includePreference(prefPassword, "Not Set", new SummaryMapper.PasswordMask());
            // passwordPreference.setText(password); // not necessary, get set when onCreatePreferences
            prefPassword.setEnabled(true);

            // Enable clear credentials button if password exists
            prefForgetPass = findPreference(P_KEY_FORGET_PASSWORD);
            prefForgetPass.setVisible(!TextUtils.isEmpty(password));

            // Forget password action handler
            prefForgetPass.setOnPreferenceClickListener(preference -> {
                askForgetPassword();
                return false;
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onResume() {
            super.onResume();
            mPref.registerOnSharedPreferenceChangeListener(this);
            mPref.registerOnSharedPreferenceChangeListener(summaryMapper);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPause() {
            super.onPause();
            mPref.unregisterOnSharedPreferenceChangeListener(this);
            mPref.unregisterOnSharedPreferenceChangeListener(summaryMapper);
        }

        /**
         * Asks the user for confirmation of password clearing and eventually clears it.
         */
        private void askForgetPassword() {
            if (StringUtils.isNullOrEmpty(prefPassword.getText())) {
                return;
            }

            AlertDialog.Builder askForget = new AlertDialog.Builder(mContext);
            askForget.setTitle(R.string.remove)
                    .setMessage(R.string.provisioning_remove_credentials_message)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        credentialsService.removePassword(ProvisioningServiceImpl.PROVISIONING_PASSWORD_PROP);
                        prefUsername.setText(null);
                        prefPassword.setText(null);
                        prefForgetPass.setVisible(false);
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> dialog.dismiss()).show();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (StringUtils.isNullOrEmpty(key))
                return;

            switch (key) {
                case P_KEY_PROVISIONING_METHOD:
                    if ("NONE".equals(sharedPreferences.getString(P_KEY_PROVISIONING_METHOD, null))) {
                        mConfig.setProperty(ProvisioningServiceImpl.PROVISIONING_URL_PROP, null);
                    }
                    break;

                case P_KEY_URL:
                    String url = sharedPreferences.getString(P_KEY_URL, null);
                    if (!StringUtils.isNullOrEmpty(url))
                        mConfig.setProperty(ProvisioningServiceImpl.PROVISIONING_URL_PROP, url);
                    else
                        mConfig.setProperty(ProvisioningServiceImpl.PROVISIONING_URL_PROP, null);
                    break;

                // Seems Jitsi impl does not allow user to change user and password
                case P_KEY_USERNAME:
                    String username = sharedPreferences.getString(P_KEY_USERNAME, null);
                    if (!StringUtils.isNullOrEmpty(username))
                        mConfig.setProperty(ProvisioningServiceImpl.PROVISIONING_USERNAME_PROP, username);
                    break;

                case P_KEY_PASSWORD:
                    String password = sharedPreferences.getString(P_KEY_PASSWORD, null);
                    if (StringUtils.isNullOrEmpty(password, true)) {
                        credentialsService.removePassword(ProvisioningServiceImpl.PROVISIONING_PASSWORD_PROP);
                        prefForgetPass.setVisible(false);
                    }
                    else {
                        credentialsService.storePassword(ProvisioningServiceImpl.PROVISIONING_PASSWORD_PROP, password);
                        prefForgetPass.setVisible(true);
                    }
                    break;
            }
        }
    }
}
