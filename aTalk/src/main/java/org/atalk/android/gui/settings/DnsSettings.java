/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

/**
 * DNS settings activity. Reads default value for fallback ip from config and
 * takes care of disabling parallel resolver when DNSSEC is enabled.
 *
 * @author Pawel Domas
 */
public class DnsSettings extends BasicSettingsActivity
{
    /**
     * Used property keys
     */

    private final static String P_KEY_DNSSEC_ENABLED = aTalkApp.getResString(R.string.pref_key_dns_dnssec_enabled);

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null)
            return;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference)
    {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
