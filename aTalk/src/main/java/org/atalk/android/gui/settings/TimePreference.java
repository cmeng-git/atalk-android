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
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;
import androidx.preference.Preference;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This class is used in our preference where user can pick a quiet time for notifications not to appear.
 * Specifically, this class is responsible for saving/retrieving preference data.
 *
 * @author Eng Chong Meng
 */
public class TimePreference extends DialogPreference
        implements Preference.OnPreferenceChangeListener {
    public final static long DEFAULT_VALUE = 0L;

    public TimePreference(final Context context, final AttributeSet attrs) {
        super(context, attrs, 0);
        this.setOnPreferenceChangeListener(this);
    }

    protected void setTime(final long time) {
        persistLong(time);
        notifyDependencyChange(shouldDisableDependents());
        notifyChanged();
        updateSummary(time);
    }

    private void updateSummary(final long time) {
        final DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(getContext());
        final Date date = minutesToCalender(time).getTime();
        setSummary(dateFormat.format(date.getTime()));
    }

    private static Calendar minutesToCalender(long time) {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (int) ((time % (24 * 60)) / 60));
        c.set(Calendar.MINUTE, (int) ((time % (24 * 60)) % 60));
        return c;
    }

    public static long minutesToTimestamp(long time) {
        return minutesToCalender(time).getTimeInMillis();
    }

    @Override
    protected Object onGetDefaultValue(final TypedArray a, final int index) {
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(final Object defaultValue) {
        long time = (defaultValue instanceof Long) ? (Long) defaultValue : DEFAULT_VALUE;
        setTime(time);
    }

    /**
     * get the TimePreference persistent value the timePicker value update
     */
    public Long getPersistedValue() {
        return (long) getPersistedLong(DEFAULT_VALUE);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue) {
        ((TimePreference) preference).updateSummary((Long) newValue);
        return true;
    }
}
