/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.service.configuration.ConfigurationService;

/**
 * Edit text preference which persists its value through the <code>ConfigurationService</code>. Current value is reflected
 * in the summary. It also supports minimum and maximum value limits of integer or float type.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ConfigEditText extends EditTextPreference implements Preference.OnPreferenceChangeListener {
    /**
     * Integer upper bound for accepted value
     */
    private Integer intMax;
    /**
     * Integer lower limit for accepted value
     */
    private Integer intMin;
    /**
     * Float upper bound for accepted value
     */
    private Float floatMin;
    /**
     * Float lower limit for accepted value
     */
    private Float floatMax;

    /**
     * <code>ConfigWidgetUtil</code> used by this instance
     */
    private final ConfigWidgetUtil configUtil = new ConfigWidgetUtil(this, true);

    /**
     * Flag indicates if this edit text field is editable.
     */
    private boolean editable = true;
    /**
     * Flag indicates if we want to allow empty values to go thought the value range check.
     */
    private boolean allowEmpty = true;

    public ConfigEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initAttributes(context, attrs);
    }

    public ConfigEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initAttributes(context, attrs);
    }

    public ConfigEditText(Context context) {
        super(context);
    }

    /**
     * Parses attributes array.
     *
     * @param context the Android context.
     * @param attrs attributes set.
     */
    private void initAttributes(Context context, AttributeSet attrs) {
        TypedArray attArray = context.obtainStyledAttributes(attrs, R.styleable.ConfigEditText);

        for (int i = 0; i < attArray.getIndexCount(); i++) {
            int attribute = attArray.getIndex(i);
            switch (attribute) {
                case R.styleable.ConfigEditText_intMax:
                    this.intMax = attArray.getInt(attribute, -1);
                    break;
                case R.styleable.ConfigEditText_intMin:
                    this.intMin = attArray.getInt(attribute, -1);
                    break;
                case R.styleable.ConfigEditText_floatMax:
                    this.floatMax = attArray.getFloat(attribute, -1);
                    break;
                case R.styleable.ConfigEditText_floatMin:
                    this.floatMin = attArray.getFloat(attribute, -1);
                    break;
                case R.styleable.ConfigEditText_editable:
                    this.editable = attArray.getBoolean(attribute, true);
                    break;
                case R.styleable.ConfigEditText_allowEmpty:
                    this.allowEmpty = attArray.getBoolean(attribute, true);
                    break;
            }
        }
        // Register listener to perform checks before new value is accepted
        setOnPreferenceChangeListener(this);

        configUtil.parseAttributes(context, attrs);
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        // Force load default value from configuration service
        setDefaultValue(getPersistedString(null));
        super.onAttachedToHierarchy(preferenceManager);
    }

    /**
     * {@inheritDoc}
     * // Set summary on init
     */
    @Override
    protected void onSetInitialValue(Object defaultValue) {
        super.onSetInitialValue(defaultValue);
        configUtil.updateSummary(getText());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getPersistedString(String defaultReturnValue) {
        ConfigurationService configService = AppGUIActivator.getConfigurationService();
        if (configService == null)
            return defaultReturnValue;

        return configService.getString(getKey(), defaultReturnValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean persistString(String value) {
        super.persistString(value);
        configUtil.handlePersistValue(value);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Performs value range checks before the value is accepted.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (allowEmpty && StringUtils.isEmpty((String) newValue)) {
            return true;
        }

        if (intMax != null && intMin != null) {
            // Integer range check
            try {
                Integer newInt = Integer.parseInt((String) newValue);
                return intMin <= newInt && newInt <= intMax;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        else if (floatMin != null && floatMax != null) {
            // Float range check
            try {
                Float newFloat = Float.parseFloat((String) newValue);
                return floatMin <= newFloat && newFloat <= floatMax;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        // No checks by default
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClick() {
        if (editable)
            super.onClick();
    }
}
