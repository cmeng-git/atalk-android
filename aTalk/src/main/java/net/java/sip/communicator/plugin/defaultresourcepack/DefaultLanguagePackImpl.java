/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.plugin.defaultresourcepack;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Vector;

import net.java.sip.communicator.service.resources.LanguagePack;
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils;

/**
 * @author Damian Minkov
 */
public class DefaultLanguagePackImpl implements LanguagePack {
    private static final String DEFAULT_RESOURCE_PATH = "resources.languages.resources";

    /**
     * The locale used for the last resources request
     */
    private Locale localeInBuffer = null;

    /**
     * The result of the last resources request
     */
    private Map<String, String> lastResourcesAsked = null;

    /**
     * All language resource locales.
     */
    private Vector<Locale> availableLocales = new Vector<Locale>();

    /**
     * Constructor.
     */
    public DefaultLanguagePackImpl() {
        // Finds all the files *.properties in the path : /resources/languages.
        Enumeration<?> fsEnum = DefaultResourcePackActivator.bundleContext.getBundle()
                .findEntries("/resources/languages", "*.properties", false);

        if (fsEnum != null) {
            while (fsEnum.hasMoreElements()) {
                String fileName = ((URL) fsEnum.nextElement()).getFile();
                int localeIndex = fileName.indexOf('_');

                if (localeIndex != -1) {
                    String localeId = fileName.substring(localeIndex + 1, fileName.indexOf('.', localeIndex));

                    availableLocales.add(ResourceManagementServiceUtils.getLocale(localeId));
                }
            }
        }
    }

    /**
     * Returns a <code>Map</code>, containing all [key, value] pairs for this resource pack.
     *
     * @return a <code>Map</code>, containing all [key, value] pairs for this resource pack.
     */
    public Map<String, String> getResources() {
        return getResources(Locale.getDefault());
    }

    /**
     * Returns a <code>Map</code>, containing all [key, value] pairs for the given locale.
     *
     * @param locale The <code>Locale</code> we're looking for.
     *
     * @return a <code>Map</code>, containing all [key, value] pairs for the given locale.
     */
    public Map<String, String> getResources(Locale locale) {
        // check if we didn't computed it at the previous call
        if (locale.equals(localeInBuffer) && lastResourcesAsked != null) {
            return lastResourcesAsked;
        }

        ResourceBundle resourceBundle
                = ResourceBundle.getBundle(DEFAULT_RESOURCE_PATH, locale, new ResourceBundle.Control() {
            // work around Java's backwards compatibility
            @Override
            public String toBundleName(String baseName, Locale locale) {
                if (locale.equals(new Locale("he"))) {
                    return baseName + "_he";
                }
                else if (locale.equals(new Locale("yi"))) {
                    return baseName + "_yi";
                }
                else if (locale.equals(new Locale("id"))) {
                    return baseName + "_id";
                }
                return super.toBundleName(baseName, locale);
            }
        });

        Map<String, String> resources = new Hashtable<>();
        this.initResources(resourceBundle, resources);
        this.initPluginResources(resources, locale);

        // keep it just in case of...
        localeInBuffer = locale;
        lastResourcesAsked = resources;
        return resources;
    }

    /**
     * Returns a Set of the keys contained only in the ResourceBundle for locale.
     *
     * @param locale the locale for which the keys are requested
     *
     * @return a Set of the keys contained only in the ResourceBundle for locale
     */
    @SuppressWarnings("unchecked")
    public Set<String> getResourceKeys(Locale locale) {
        try {
            Method handleKeySet = ResourceBundle.class.getDeclaredMethod("handleKeySet");
            handleKeySet.setAccessible(true);
            return (Set<String>) handleKeySet.invoke(ResourceBundle.getBundle(DEFAULT_RESOURCE_PATH, locale));
        } catch (Exception e) {
        }
        return new HashSet<>();
    }

    /**
     * Returns the name of this resource pack.
     *
     * @return the name of this resource pack.
     */
    public String getName() {
        return "Default Language Resources";
    }

    /**
     * Returns the description of this resource pack.
     *
     * @return the description of this resource pack.
     */
    public String getDescription() {
        return "Provide Jitsi default Language resource pack.";
    }

    /**
     * Fills the given resource map with all (key,value) pairs obtained from the given <code>ResourceBundle</code>.
     * This method will look in the properties files for references to other properties files and will include
     * in the final map data from all referenced files.
     *
     * @param resourceBundle The initial <code>ResourceBundle</code>, corresponding to the "main" properties file.
     * @param resources A <code>Map</code> that would store the data.
     */
    private void initResources(ResourceBundle resourceBundle, Map<String, String> resources) {
        Enumeration<String> colorKeys = resourceBundle.getKeys();

        while (colorKeys.hasMoreElements()) {
            String key = colorKeys.nextElement();
            String value = resourceBundle.getString(key);
            resources.put(key, value);
        }
    }

    /**
     * Finds all plugin color resources, matching the "images-*.properties" pattern and adds them to this resource pack.
     */
    private void initPluginResources(Map<String, String> resources, Locale locale) {
        Iterator<String> pluginProperties
                = DefaultResourcePackActivator.findResourcePaths("resources/languages", "strings-*.properties");

        while (pluginProperties.hasNext()) {
            String resourceBundleName = pluginProperties.next();

            if (resourceBundleName.indexOf('_') == -1) {
                ResourceBundle resourceBundle = ResourceBundle.getBundle(resourceBundleName.substring(0,
                        resourceBundleName.indexOf(".properties")), locale);
                initResources(resourceBundle, resources);
            }
        }
    }

    /**
     * All the locales in the language pack.
     *
     * @return all the locales this Language pack contains.
     */
    public Iterator<Locale> getAvailableLocales() {
        return availableLocales.iterator();
    }
}
