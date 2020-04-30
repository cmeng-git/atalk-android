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
package org.atalk.android.gui.util;

import android.content.Context;

import org.atalk.android.R;

/**
 * ThemeHelper class that set the aTalk app Theme as specified by user
 *
 * @author Eng Chong Meng
 */
public class ThemeHelper
{
    /**
     * Possible values for the different theme settings. Important:
     * Do not change the order of the items! The ordinal value (position) is used when saving the settings.
     */
    public enum Theme
    {
        LIGHT,
        DARK
    }

    // Note: mTheme will get initialized from DB by ConfigurationUtils on aTalk startup
    private static Theme mTheme = Theme.DARK;

    /**
     * Set the aTalk Theme per current mTheme
     *
     * @param ctx context
     */
    public static void setTheme(Context ctx)
    {
        ctx.setTheme(getAppThemeResourceId(mTheme));
    }

    /**
     * Set the aTalk theme as per specified
     *
     * @param ctx context
     * @param theme the new theme
     */
    public static void setTheme(Context ctx, Theme theme)
    {
        setAppTheme(theme);
        ctx.setTheme(getAppThemeResourceId(theme));
    }

    public static Theme getAppTheme()
    {
        return mTheme;
    }

    public static void setAppTheme(Theme theme)
    {
        mTheme = theme;
    }

    public static int getAppThemeResourceId()
    {
        return getAppThemeResourceId(mTheme);
    }

    /**
     * Get the app specific theme to init android theme for use
     *
     * @param theme the current theme
     * @return app android theme for use
     */
    private static int getAppThemeResourceId(Theme theme)
    {
        return (theme == Theme.LIGHT) ? R.style.AppTheme_Light : R.style.AppTheme_Dark;
    }

    /**
     * Return true if the current aTalk mTheme is per the specifies theme
     *
     * @param theme the theme
     * @return true if the current aTalk mThem is the same as the specified mTheme
     */
    public static boolean isAppTheme(Theme theme)
    {
        return (mTheme == theme);
    }
}
