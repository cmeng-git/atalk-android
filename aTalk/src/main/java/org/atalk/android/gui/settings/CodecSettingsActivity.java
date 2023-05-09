/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiPreferenceActivity;

/**
 * Base class for settings screens which only adds preferences from XML resource.
 * By default preference resource id is obtained from <code>Activity</code> meta-data,
 * resource key: "androidx.preference".
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CodecSettingsActivity extends OSGiPreferenceActivity {
    /**
     * Returns preference XML resource ID.
     *
     * @return preference XML resource ID.
     */
    protected int getPreferencesXmlId() {
        // Cant' find custom preference classes using:
        // addPreferencesFromIntent(getActivity().getIntent());
        try {
            ActivityInfo app = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);

            if(app.name.contains("Opus"))
                setMainTitle(R.string.service_gui_settings_OPUS);
            else
                setMainTitle(R.string.service_gui_settings_SILK);

            return app.metaData.getInt("android.preference");
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment(getPreferencesXmlId())).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat {
        private final int mPreferResId;

        public MyPreferenceFragment(int preferResId) {
            mPreferResId = preferResId;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(mPreferResId, rootKey);
        }
    }
}
