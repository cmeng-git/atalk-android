package org.atalk.android.gui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TimePicker;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

public class TimePreference extends DialogPreference implements Preference.OnPreferenceChangeListener
{
    private TimePicker picker = null;
    public final static long DEFAULT_VALUE = 0L;

    public TimePreference(final Context context, final AttributeSet attrs)
    {
        super(context, attrs, 0);
        this.setOnPreferenceChangeListener(this);
    }

    protected void setTime(final long time)
    {
        persistLong(time);
        notifyDependencyChange(shouldDisableDependents());
        notifyChanged();
        updateSummary(time);
    }

    private void updateSummary(final long time)
    {
        final DateFormat dateFormat = android.text.format.DateFormat.getTimeFormat(getContext());
        final Date date = minutesToCalender(time).getTime();
        setSummary(dateFormat.format(date.getTime()));
    }

    @Override
    protected View onCreateDialogView()
    {
        picker = new TimePicker(getContext());
        picker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(getContext()));
        return picker;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    protected void onBindDialogView(final View v)
    {
        super.onBindDialogView(v);
        long time = getPersistedLong(DEFAULT_VALUE);

        picker.setCurrentHour((int) ((time % (24 * 60)) / 60));
        picker.setCurrentMinute((int) ((time % (24 * 60)) % 60));
    }

    @Override
    protected void onDialogClosed(final boolean positiveResult)
    {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            setTime(picker.getCurrentHour() * 60 + picker.getCurrentMinute());
        }
    }

    private static Calendar minutesToCalender(long time)
    {
        final Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (int) ((time % (24 * 60)) / 60));
        c.set(Calendar.MINUTE, (int) ((time % (24 * 60)) % 60));
        return c;
    }

    public static long minutesToTimestamp(long time)
    {
        return minutesToCalender(time).getTimeInMillis();
    }

    @Override
    protected Object onGetDefaultValue(final TypedArray a, final int index)
    {
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(final boolean restorePersistedValue, final Object defaultValue)
    {
        long time;
        if (defaultValue instanceof Long) {
            time = restorePersistedValue ? getPersistedLong((Long) defaultValue) : (Long) defaultValue;
        }
        else {
            time = restorePersistedValue ? getPersistedLong(DEFAULT_VALUE) : DEFAULT_VALUE;
        }
        setTime(time);
        updateSummary(time);
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, final Object newValue)
    {
        ((TimePreference) preference).updateSummary((Long) newValue);
        return true;
    }
}
