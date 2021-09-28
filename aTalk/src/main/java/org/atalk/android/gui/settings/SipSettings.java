/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SIP protocol settings screen.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SipSettings extends BasicSettingsActivity
{
    @Override
    public int getPreferencesXmlId()
    {
        return R.xml.sip_preferences;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public class MyPreferenceFragment extends PreferenceFragmentCompat
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            super.onCreate(savedInstanceState);

            // Create supported protocols checkboxes
            PreferenceCategory protocols = findPreference(getString(R.string.pref_cat_sip_ssl_protocols));

            String configuredProtocols = Arrays.toString(ConfigurationUtils.getEnabledSslProtocols());

            for (String protocol : ConfigurationUtils.getAvailableSslProtocols()) {
                CheckBoxPreference cbPRef = new CheckBoxPreference(getContext());
                cbPRef.setTitle(protocol);
                cbPRef.setChecked(configuredProtocols.contains(protocol));
                protocols.addPreference(cbPRef);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDestroy()
        {
            super.onDestroy();

            // Find ssl protocol checkboxes and commit changes
            PreferenceCategory protocols = findPreference(getString(R.string.pref_cat_sip_ssl_protocols));

            int count = protocols.getPreferenceCount();
            List<String> enabledSslProtocols = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                CheckBoxPreference protoPref = (CheckBoxPreference) protocols.getPreference(i);
                if (protoPref.isChecked())
                    enabledSslProtocols.add(protoPref.getTitle().toString());
            }
            ConfigurationUtils.setEnabledSslProtocols(enabledSslProtocols.toArray(new String[]{}));
        }
    }
}
