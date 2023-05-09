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
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.text.TextUtils;

import java.util.Locale;

/**
 * Implementation of ContextWrapper for Application class proper Locale setting
 */
public class LocaleHelper extends ContextWrapper {

    public LocaleHelper(Context base) {
        super(base);
    }

    // Default to system locale language; get init from DB by aTalkApp first call
    private static String mLanguage = "";

    /**
     * Set aTalk Locale to the current mLanguage
     *
     * @param ctx Context
     */
    public static LocaleHelper setLocale(Context ctx) {
        return wrap(ctx, mLanguage);
    }

    /**
     * Set the locale as per specified language; must use Application instance
     *
     * @param ctx BaseContext
     * @param language the new UI language
     */
    public static LocaleHelper setLocale(Context ctx, String language) {
        mLanguage = language;
        return wrap(ctx, language);
    }

    public static String getLanguage() {
        return mLanguage;
    }

    public static void setLanguage(String language) {
        mLanguage = language;
    }

    /**
     * Update the app local as per specified language.
     *
     * @param context Context
     * @param language the new UI language
     * #return The new PbContext for use by caller
     */
    public static LocaleHelper wrap(Context context, String language) {
        Configuration config = context.getResources().getConfiguration();

        Locale locale;
        if (TextUtils.isEmpty(language)) {
            // System default
            locale = Resources.getSystem().getConfiguration().locale;
        }
        else if (language.length() == 5 && language.charAt(2) == '_') {
            // language is in the form: en_US
            locale = new Locale(language.substring(0, 2), language.substring(3));
        }
        else {
            locale = new Locale(language);
        }

        Locale.setDefault(locale);
        config.setLayoutDirection(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
        }
        else {
            config.locale = locale;
        }

        context = context.createConfigurationContext(config);
        // Timber.d(new Exception(), "set locale: %s: %s", language, context);
        return new LocaleHelper(context);
    }
}
