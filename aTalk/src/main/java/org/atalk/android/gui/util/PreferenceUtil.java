/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import androidx.preference.*;

/**
 * Utility class exposing methods to operate on <code>Preference</code> subclasses.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class PreferenceUtil
{
    /**
     * Sets the <code>CheckBoxPreference</code> "checked" property.
     *
     * @param screen the <code>PreferenceScreen</code> containing the <code>CheckBoxPreference</code> we want to edit.
     * @param prefKey preference key id from <code>R.string</code>.
     * @param isChecked the value we want to set to the "checked" property of <code>CheckBoxPreference</code>.
     */
    static public void setCheckboxVal(PreferenceScreen screen, String prefKey, boolean isChecked)
    {
        CheckBoxPreference cbPref = screen.findPreference(prefKey);
        cbPref.setChecked(isChecked);
    }

    /**
     * Sets the text of <code>EditTextPreference</code> identified by given preference key string.
     *
     * @param screen the <code>PreferenceScreen</code> containing the <code>EditTextPreference</code> we want to edit.
     * @param prefKey preference key id from <code>R.string</code>.
     * @param txtValue the text value we want to set on <code>EditTextPreference</code>
     */
    public static void setEditTextVal(PreferenceScreen screen, String prefKey, String txtValue)
    {
        EditTextPreference cbPref = screen.findPreference(prefKey);
        cbPref.setText(txtValue);
    }

    /**
     * Sets the value of <code>ListPreference</code> identified by given preference key string.
     *
     * @param screen the <code>PreferenceScreen</code> containing the <code>ListPreference</code> we want to edit.
     * @param prefKey preference key id from <code>R.string</code>.
     * @param value the value we want to set on <code>ListPreference</code>
     */
    public static void setListVal(PreferenceScreen screen, String prefKey, String value)
    {
        ListPreference lstPref = screen.findPreference(prefKey);
        lstPref.setValue(value);
    }
}
