/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceManager;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.service.configuration.ConfigurationService;

/**
 * List preference that stores its value through the <code>ConfigurationService</code>. It also supports
 * "disable dependents value" attribute.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ConfigListPreference extends ListPreference
{
    /**
     * The optional attribute which contains value that disables all dependents.
     */
    private String dependentValue;
    /**
     * Disables dependents when current value is different than <code>dependentValue</code>.
     */
    private boolean disableOnNotEqual;

    public ConfigListPreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        initAttributes(context, attrs);
    }

    public ConfigListPreference(Context context)
    {
        super(context);
    }

    /**
     * Parses attribute set.
     *
     * @param context Android context.
     * @param attrs attribute set.
     */
    private void initAttributes(Context context, AttributeSet attrs)
    {
        TypedArray attArray = context.obtainStyledAttributes(attrs, R.styleable.ConfigListPreference);

        for (int i = 0; i < attArray.getIndexCount(); i++) {
            int attrIdx = attArray.getIndex(i);
            switch (attrIdx) {
                case R.styleable.ConfigListPreference_disableDependentsValue:
                    this.dependentValue = attArray.getString(attrIdx);
                    break;
                case R.styleable.ConfigListPreference_disableOnNotEqualValue:
                    this.disableOnNotEqual = attArray.getBoolean(attrIdx, false);
                    break;
            }
        }
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager)
    {
        // Force load default value from configuration service
        setDefaultValue(getPersistedString(null));
        super.onAttachedToHierarchy(preferenceManager);
    }

    /**
     * {@inheritDoc}
     // Update summary every time the value is read
     */
    @Override
    protected void onSetInitialValue(Object defaultValue)
    {
        super.onSetInitialValue(defaultValue);
        updateSummary(getValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getPersistedString(String defaultReturnValue)
    {
        ConfigurationService configService = AndroidGUIActivator.getConfigurationService();
        if (configService == null)
            return defaultReturnValue;

        return configService.getString(getKey(), defaultReturnValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean persistString(String value)
    {
        super.persistString(value);

        ConfigurationService configService = AndroidGUIActivator.getConfigurationService();
        if (configService == null)
            return false;

        // Update summary when the value has changed
        configService.setProperty(getKey(), value);
        updateSummary(value);
        return true;
    }

    /**
     * Updates the summary using entry corresponding to currently selected value.
     *
     * @param value the current value
     */
    private void updateSummary(String value)
    {
        int idx = findIndexOfValue(value);
        if (idx != -1) {
            setSummary(getEntries()[idx]);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValue(String value)
    {
        super.setValue(value);

        // Disables dependents
        notifyDependencyChange(shouldDisableDependents());
    }

    /**
     * {@inheritDoc}
     *
     * Additionally checks if current value is equal to disable dependents value.
     */
    @Override
    public boolean shouldDisableDependents()
    {
        return super.shouldDisableDependents()
                || (dependentValue != null && disableOnNotEqual != dependentValue.equals(getValue()));
    }
}
