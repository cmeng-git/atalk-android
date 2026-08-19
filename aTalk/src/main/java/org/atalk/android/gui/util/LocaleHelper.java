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
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * Implementation of LocaleHelper to support proper Locale setting for Application/Activity classes.
 *
 * @author Eng Chong Meng
 */
public class LocaleHelper {

    // Default to system locale language; get init from DB by aTalkApp first call
    private static String mLanguage = "";

    // mLocale will have 'regional preference' value stripped off; mainly use for smack xml:lang
    private static Locale xmlLocale = Locale.getDefault();

    /**
     * Set aTalk Locale to the current mLanguage
     *
     * @param ctx Context
     */
    public static Context setLocale(Context ctx) {
        return wrap(ctx, mLanguage);
    }

    /**
     * Set the locale as per specified language; must use Application instance
     *
     * @param ctx Base Context
     * @param language the new UI language
     */
    public static Context setLocale(Context ctx, String language) {
        mLanguage = language;
        return wrap(ctx, language);
    }

    public static Locale getXmlLocale() {
        return xmlLocale;
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
     * @param context Base Context (ContextImpl)
     * @param language the new UI language
     * #return The new ContextImpl for use by caller
     */
    public static Context wrap(Context context, String language) {
        Configuration config = context.getResources().getConfiguration();

        Locale locale;
        if (TextUtils.isEmpty(language)) {
            // System default may contain regional preference i.e. 'en-US-#u-fw-sun-mu-celsius'
            locale = Resources.getSystem().getConfiguration().getLocales().get(0);

            // Strip off any regional preferences in the language
            language = locale.toString().split("_#")[0];
            int idx = language.indexOf("_");
            xmlLocale = (idx == -1) ? locale : new Locale(language.substring(0, idx), language.substring(idx + 1));
        }
        else {
            int idx = language.indexOf("_");
            if (idx != -1) {
                // language is in the form: en_US
                locale = new Locale(language.substring(0, idx), language.substring(idx + 1));
//                locale = new Locale.Builder().setLanguage(language.substring(0, idx))
//                        .setRegion(language.substring(idx + 1))
//                        .build();
            }
            else {
                locale = new Locale(language);
            }
            xmlLocale = locale;
        }

        config.setLayoutDirection(locale);
        config.setLocale(locale);

        // Timber.d(new Exception(), "set locale: %s: %s", language, context);
        return context.createConfigurationContext(config);
    }

    /**
     * Set the application's per-language preference.
     * @param languageCode The BCP-47 tag of the language (e.g., "en", "es", "fr")
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static void setAppLanguage(String languageCode) {
        // Create a locale list containing your selected language
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);

        // Apply the locales to the framework
        // This automatically saves the preference and updates the UI resources
        AppCompatDelegate.setApplicationLocales(appLocale);
    }

    /**
     * Get the currently active app language code.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public static String getAppLanguage() {
        LocaleListCompat currentLocales = AppCompatDelegate.getApplicationLocales();
        if (!currentLocales.isEmpty()) {
            return currentLocales.get(0).getDisplayName();
        }
        // Falls back to system language if not explicitly set
        return  Locale.getDefault().toLanguageTag();
    }
}
