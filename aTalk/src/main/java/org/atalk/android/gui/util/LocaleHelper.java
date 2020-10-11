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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;

import java.util.Locale;

import timber.log.Timber;

/**
 * LocaleHelper class that set the app Locale
 *
 * @author Eng Chong Meng
 */
public class LocaleHelper
{
    // Default to system locale language; get init from DB by aTalkApp first call
    private static String mLanguage = "";

    /**
     * Set aTalk Locale to the current mLanguage
     *
     * @param ctx Context
     */
    public static void setLocale(Context ctx)
    {
        updateResources(ctx, mLanguage);
    }

    /**
     * Set the locale as per specified language
     *
     * @param ctx context
     * @param language the new UI language
     */
    public static void setLocale(Context ctx, String language)
    {
        setLanguage(language);
        updateResources(ctx, language);
    }

    public static String getLanguage()
    {
        return mLanguage;
    }

    public static void setLanguage(String language)
    {
        mLanguage = language;
    }

    /**
     * Update the app local as per specified language
     *
     * @param ctx context
     * @param language the new UI language
     */
    private static void updateResources(Context ctx, String language)
    {
        // Timber.d(new Exception(), "Set Locale: %s", mLanguage);
        Locale locale;
        if (TextUtils.isEmpty(language)) {
            // System default
            locale = Resources.getSystem().getConfiguration().locale;
        }
        else if (language.length() == 5 && mLanguage.charAt(2) == '_') {
            // language is in the form: en_US
            locale = new Locale(language.substring(0, 2), language.substring(3));
        }
        else {
            locale = new Locale(language);
        }

        Locale.setDefault(locale);
        Resources res = ctx.getResources();

        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        ctx.createConfigurationContext(config);

        res.updateConfiguration(config, res.getDisplayMetrics());
    }
}
