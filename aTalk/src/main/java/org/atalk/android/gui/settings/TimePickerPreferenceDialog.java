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

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TimePicker;

import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * This module is for managing the visual aspect of the dialog TimePreference UI display.
 * This is where we will actually create and display the TimePicker for user selection
 *
 * @author Eng Chong Meng
 */
public class TimePickerPreferenceDialog extends PreferenceDialogFragmentCompat {
    public final static String ARG_KEY = "key";

    private TimePicker timePicker = null;

    public static TimePickerPreferenceDialog newInstance(TimePreference pref) {
        TimePickerPreferenceDialog dialogFragment = new TimePickerPreferenceDialog();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, pref.getKey());
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    /**
     * Generate the TimePicker to be displayed
     *
     * @param context Context
     *
     * @return a reference copy of the TimePicker
     */
    @Override
    protected View onCreateDialogView(Context context) {
        timePicker = new TimePicker(context);
        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(getContext()));
        return timePicker;
    }

    /**
     * Get the TimePreference to set that value into the TimePicker.
     *
     * @param v View
     */
    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        TimePreference pref = (TimePreference) getPreference();
        long time = pref.getPersistedValue();

        timePicker.setCurrentHour((int) ((time % (24 * 60)) / 60));
        timePicker.setCurrentMinute((int) ((time % (24 * 60)) % 60));
    }

    /**
     * Save the value selected by the user after clicking the positive button.
     *
     * @param positiveResult true if changed
     */
    @Override
    public void onDialogClosed(boolean positiveResult) {
        // Save the user changed settings
        if (positiveResult) {
            TimePreference pref = (TimePreference) getPreference();
            pref.setTime(timePicker.getCurrentHour() * 60 + timePicker.getCurrentMinute());
        }
    }
}