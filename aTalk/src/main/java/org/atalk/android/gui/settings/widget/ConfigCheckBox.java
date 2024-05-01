/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.PreferenceManager;

import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.service.configuration.ConfigurationService;

/**
 * Checkbox preference that persists the value through <code>ConfigurationService</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ConfigCheckBox extends CheckBoxPreference {
    /**
     * <code>ConfigWidgetUtil</code> used by this instance.
     */
    private final ConfigWidgetUtil configUtil = new ConfigWidgetUtil(this);

    public ConfigCheckBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        configUtil.parseAttributes(context, attrs);
    }

    public ConfigCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        configUtil.parseAttributes(context, attrs);
    }

    public ConfigCheckBox(Context context) {
        super(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        super.onSetInitialValue(defaultValue);
        configUtil.updateSummary(isChecked());
    }

    @Override
    protected void onAttachedToHierarchy(PreferenceManager preferenceManager) {
        // Force load default value from configuration service
        setDefaultValue(getPersistedBoolean(false));
        super.onAttachedToHierarchy(preferenceManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean getPersistedBoolean(boolean defaultReturnValue) {
        ConfigurationService configService = AndroidGUIActivator.getConfigurationService();
        if (configService == null)
            return defaultReturnValue;

        return configService.getBoolean(getKey(), defaultReturnValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean persistBoolean(boolean value) {
        super.persistBoolean(value);
        // Sets boolean value in the ConfigurationService
        configUtil.handlePersistValue(value);
        return true;
    }
}
