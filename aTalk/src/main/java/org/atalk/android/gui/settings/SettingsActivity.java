/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.atalk.android.R;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.service.osgi.OSGiActivity;

/**
 * <code>Activity</code> implements aTalk global settings.
 *
 * @author Eng Chong Meng
 */
public class SettingsActivity extends OSGiActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // We do not allow opening settings if there is a call currently active
        if (AndroidCallUtil.checkCallInProgress(this))
            return;

        // Display the fragment as the android main content.
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);

        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
