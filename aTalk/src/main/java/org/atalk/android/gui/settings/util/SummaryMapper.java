/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.util;

import android.content.SharedPreferences;

import androidx.preference.ListPreference;
import androidx.preference.Preference;

import java.util.HashMap;
import java.util.Map;

/**
 * The class can be used to set {@link Preference} value as its summary text. Optionally the empty string can be
 * provided that will be used when value is <code>null</code> or empty <code>String</code>. To make it work it has to be
 * registered to the {@link SharedPreferences} instance containing preferences we want to handle.
 * Single instance can map multiple {@link Preference} at one time.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SummaryMapper implements SharedPreferences.OnSharedPreferenceChangeListener {
    /**
     * The key to {@link androidx.preference.Preference} mapping
     */
    private final Map<String, Preference> mappedPreferences = new HashMap<>();

    /**
     * Mapping containing optional {@link SummaryConverter} that can provide custom operation on
     * the value before it is applied as a summary.
     */
    private final Map<String, SummaryConverter> convertersMap = new HashMap<>();

    /**
     * Mapping containing empty string definitions
     */
    private final Map<String, String> emptyStrMap = new HashMap<>();

    /**
     * Includes the {@link Preference} into summary mapping.
     *
     * @param pref the {@link Preference} to be included
     * @param empty optional empty String that will be set when the <code>Preference</code> value is <code>null</code> or empty
     * @param converter optional {@link SummaryConverter}
     *
     * @see SummaryMapper
     */
    public void includePreference(Preference pref, String empty, SummaryConverter converter) {
        if (pref == null)
            throw new NullPointerException("The preference cannot be null");

        String key = pref.getKey();
        mappedPreferences.put(key, pref);
        emptyStrMap.put(key, empty);

        if (converter != null)
            convertersMap.put(pref.getKey(), converter);
        setSummary(pref.getSharedPreferences(), pref);
    }

    /**
     * Triggers summary update on all registered <code>Preference</code>s.
     */
    public void updatePreferences() {
        for (Preference pref : mappedPreferences.values()) {
            setSummary(pref.getSharedPreferences(), pref);
        }
    }

    /**
     * Overload method for {@link #includePreference(Preference, String, SummaryConverter)}
     *
     * @see #includePreference(Preference, String, SummaryConverter)
     */
    public void includePreference(Preference pref, String empty) {
        includePreference(pref, empty, null);
    }

    /**
     * Sets the summary basing on actual {@link Preference} value
     *
     * @param sharedPrefs the {@link SharedPreferences} that manages the <code>preference</code>
     * @param preference Android Preference
     */
    private void setSummary(SharedPreferences sharedPrefs, Preference preference) {
        String key = preference.getKey();
        String value = sharedPrefs.getString(key, "");

        // Map entry instead of value for ListPreference
        if (preference instanceof ListPreference) {
            CharSequence entry = ((ListPreference) preference).getEntry();
            value = entry != null ? entry.toString() : "";
        }

        if (!value.isEmpty()) {
            SummaryConverter converter = convertersMap.get(key);
            if (converter != null)
                value = converter.convertToSummary(value);
        }
        else {
            value = emptyStrMap.get(key);
        }
        preference.setSummary(value);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = mappedPreferences.get(key);
        if (pref != null) {
            setSummary(sharedPreferences, pref);
        }
    }

    /**
     * The interface is used to provide custom value into summary conversion.
     */
    public interface SummaryConverter {
        /**
         * The method shall return summary text for given <code>input</code> value.
         *
         * @param input {@link Preference} value as a <code>String</code>
         *
         * @return output summary value
         */
        String convertToSummary(String input);
    }

    /**
     * Class is used for password preferences to display text as "*".
     */
    public static class PasswordMask implements SummaryConverter {
        public String convertToSummary(String input) {
            return input.replaceAll("(?s).", "*");
        }
    }
}
