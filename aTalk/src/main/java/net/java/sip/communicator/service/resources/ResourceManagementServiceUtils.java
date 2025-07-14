/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.resources;

import java.util.Locale;

import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleContext;

/**
 * @author Lubomir Marinov
 */
public final class ResourceManagementServiceUtils {
    /**
     * Constructs a new <code>Locale</code> instance from a specific locale
     * identifier which can either be a two-letter language code or contain a
     * two-letter language code and a two-letter country code in the form
     * <code>&lt;language&gt;_&lt;country&gt;</code>.
     *
     * @param localeId the locale identifier describing the new <code>Locale</code>
     * instance to be created
     *
     * @return a new <code>Locale</code> instance with language and country (if
     * specified) matching the given locale identifier
     */
    public static Locale getLocale(String localeId) {
        int underscoreIndex = localeId.indexOf('_');
        String language;
        String country;

        if (underscoreIndex == -1) {
            language = localeId;
            country = "";
        }
        else {
            language = localeId.substring(0, underscoreIndex);
            country = localeId.substring(underscoreIndex + 1);
        }
        // Locale.of requires API-36
        // Locale.forLanguageTag(language);
        return new Locale(language, country);
    }

    /**
     * Gets the <code>ResourceManagementService</code> instance registered in a
     * specific <code>BundleContext</code> (if any).
     *
     * @param bundleContext the <code>BundleContext</code> to be checked for a
     * registered <code>ResourceManagementService</code>
     *
     * @return a <code>ResourceManagementService</code> instance registered in
     * the specified <code>BundleContext</code> if any; otherwise, <code>null</code>
     */
    public static ResourceManagementService getService(BundleContext bundleContext) {
        return ServiceUtils.getService(bundleContext, ResourceManagementService.class);
    }

    /**
     * Prevents the creation of <code>ResourceManagementServiceUtils</code> instances.
     */
    private ResourceManagementServiceUtils() {
    }
}
