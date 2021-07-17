/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceScreen;

import net.java.otr4j.OtrPolicy;
import net.java.sip.communicator.plugin.otr.OtrActivator;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.PreferenceUtil;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiPreferenceFragment;

/**
 * Chat security settings screen with OTR preferences - modified for aTalk
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatSecuritySettings extends OSGiActivity
{
    // Preference mKeys
    static private final String P_KEY_CRYPTO_ENABLE = aTalkApp.getResString(R.string.pref_key_crypto_enable);

    private static final String AUTO_INIT_OTR_PROP = "otr.AUTO_INIT_PRIVATE_MESSAGING";

    /**
     * A property specifying whether private messaging should be made mandatory.
     */
    private static final String OTR_MANDATORY_PROP = "otr.PRIVATE_MESSAGING_MANDATORY";

    // OMEMO Security section
    static private final String P_KEY_OMEMO_KEY_BLIND_TRUST
            = aTalkApp.getResString(R.string.pref_key_omemo_key_blind_trust);

    static private ConfigurationService mConfig = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
        }
    }

    /**
     * The preferences fragment implements OTR settings.
     */
    public static class SettingsFragment extends OSGiPreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.security_preferences);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onStart()
        {
            super.onStart();

            mConfig = UtilActivator.getConfigurationService();
            OtrPolicy otrPolicy = OtrActivator.scOtrEngine.getGlobalPolicy();
            PreferenceScreen screen = getPreferenceScreen();
            PreferenceUtil.setCheckboxVal(screen, P_KEY_CRYPTO_ENABLE, otrPolicy.getEnableManual());
            PreferenceUtil.setCheckboxVal(screen, P_KEY_OMEMO_KEY_BLIND_TRUST,
                    mConfig.getBoolean(mConfig.PNAME_OMEMO_KEY_BLIND_TRUST, true));

            SharedPreferences shPrefs = getPreferenceManager().getSharedPreferences();

            // cmeng: remove unused preferences
            SharedPreferences.Editor mEditor = shPrefs.edit();
            mEditor.remove(getResources().getString(R.string.pref_key_otr_auto));
            mEditor.remove(getResources().getString(R.string.pref_key_crypto_require));
            mEditor.commit();

            // cmeng: Purge all the unnecessary OTR implementations for aTalk - will be removed in future release
            mConfig.setProperty(AUTO_INIT_OTR_PROP, null);
            mConfig.setProperty(OTR_MANDATORY_PROP, null);

            shPrefs.registerOnSharedPreferenceChangeListener(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onStop()
        {
            SharedPreferences shPrefs = getPreferenceManager().getSharedPreferences();
            shPrefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onStop();
        }

        /**
         * {@inheritDoc}
         */
        public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key)
        {
            if (key.equals(P_KEY_CRYPTO_ENABLE)) {
                OtrPolicy otrPolicy = OtrActivator.scOtrEngine.getGlobalPolicy();

                boolean isEnabled = shPreferences.getBoolean(P_KEY_CRYPTO_ENABLE, otrPolicy.getEnableManual());
                otrPolicy.setEnableManual(isEnabled);
                OtrActivator.configService.setProperty(OtrActivator.OTR_DISABLED_PROP, Boolean.toString(!isEnabled));

                // Store changes immediately
                OtrActivator.scOtrEngine.setGlobalPolicy(otrPolicy);
            }
            else if (key.equals(P_KEY_OMEMO_KEY_BLIND_TRUST)) {
                mConfig.setProperty(mConfig.PNAME_OMEMO_KEY_BLIND_TRUST,
                        shPreferences.getBoolean(P_KEY_OMEMO_KEY_BLIND_TRUST, true));
            }
        }
    }
}
