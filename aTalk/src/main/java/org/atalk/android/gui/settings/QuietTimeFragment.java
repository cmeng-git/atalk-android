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
package org.atalk.android.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.Objects;

import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.android.gui.util.PreferenceUtil;

/**
 * The preferences fragment implements for QuietTime settings.
 *
 * @author Eng Chong Meng
 */
public class QuietTimeFragment extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener, PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    // QuietTime
    public static final String P_KEY_QUIET_HOURS_ENABLE = "pref.key.quiet_hours_enable";
    public static final String P_KEY_QUIET_HOURS_START = "pref.key.quiet_hours_start";
    public static final String P_KEY_QUIET_HOURS_END = "pref.key.quiet_hours_end";

    private static final String DIALOG_FRAGMENT_TAG = "TimePickerDialog";

    private PreferenceScreen mPreferenceScreen;
    private SharedPreferences shPrefs;

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private final SummaryMapper summaryMapper = new SummaryMapper();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the quiet time preferences from an XML resource
        setPreferencesFromResource(R.xml.quiet_time_preferences, rootKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        setPrefTitle(R.string.quiet_hours);

        mPreferenceScreen = getPreferenceScreen();
        shPrefs = getPreferenceManager().getSharedPreferences();
        shPrefs.registerOnSharedPreferenceChangeListener(this);
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);
        initQuietTimePreferences();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        shPrefs.unregisterOnSharedPreferenceChangeListener(this);
        shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper);
        super.onStop();
    }

    /**
     * Initializes notifications section
     */
    private void initQuietTimePreferences() {
        // Quite hours enable
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_QUIET_HOURS_ENABLE,
                ConfigurationUtils.isQuiteHoursEnable());

        ((TimePreference) findPreference(P_KEY_QUIET_HOURS_START)).setTime(ConfigurationUtils.getQuiteHoursStart());
        ((TimePreference) findPreference(P_KEY_QUIET_HOURS_END)).setTime(ConfigurationUtils.getQuiteHoursEnd());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key) {
        switch (Objects.requireNonNull(key)) {
            case P_KEY_QUIET_HOURS_ENABLE:
                ConfigurationUtils.setQuiteHoursEnable(shPreferences.getBoolean(P_KEY_QUIET_HOURS_ENABLE, true));
                break;
            case P_KEY_QUIET_HOURS_START:
                ConfigurationUtils.setQuiteHoursStart(shPreferences.getLong(P_KEY_QUIET_HOURS_START, TimePreference.DEFAULT_VALUE));
                break;
            case P_KEY_QUIET_HOURS_END:
                ConfigurationUtils.setQuiteHoursEnd(shPreferences.getLong(P_KEY_QUIET_HOURS_END, TimePreference.DEFAULT_VALUE));
                break;
        }
    }

    /**
     * @param caller The fragment containing the preference requesting the dialog
     * @param pref The preference requesting the dialog
     *
     * @return {@code true} if the dialog creation has been handled
     */
    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        if (pref instanceof TimePreference) {
            TimePickerPreferenceDialog dialogFragment = TimePickerPreferenceDialog.newInstance((TimePreference) pref);
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
            return true;
        }
        return false;
    }
}
