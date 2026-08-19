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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.swing.ImageIcon;

import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * The abstract class for ResourceManagementService. It listens for
 * {@link ResourcePack} that are registered and exposes them later for use by
 * subclasses. It implements default behaviour for most methods.
 */
public abstract class AbstractResourcesService implements ResourceManagementService, ServiceListener {
    /**
     * The OSGI BundleContext
     */
    private final BundleContext bundleContext;

    /**
     * Resources for currently loaded <code>SettingsPack</code>.
     */
    private Map<String, String> settingsResources;

    /**
     * Currently loaded settings pack.
     */
    private ResourcePack settingsPack = null;

    /**
     * Resources for currently loaded <code>LanguagePack</code>.
     */
    private Map<String, String> languageResources;

    /**
     * Currently loaded language pack.
     */
    private LanguagePack languagePack = null;

    /**
     * The {@link Locale} of {@code languageResources} so that the caching of the later
     * can be used when a string with the same {@code Locale} is requested.
     */
    private Locale languageLocale;

    /**
     * Resources for currently loaded <code>ImagePack</code>.
     */
    private Map<String, String> imageResources;

    /**
     * Currently loaded image pack.
     */
    private ImagePack imagePack = null;

    /**
     * Resources for currently loaded <code>ColorPack</code>.
     */
    private Map<String, String> colorResources;

    /**
     * Currently loaded color pack.
     */
    private ResourcePack colorPack = null;

    /**
     * Resources for currently loaded <code>SoundPack</code>.
     */
    private Map<String, String> soundResources;

    /**
     * Currently loaded sound pack.
     */
    private ResourcePack soundPack = null;

    /**
     * Currently loaded <code>SkinPack</code>.
     */
    private SkinPack skinPack = null;

    /**
     * Creates an instance of <code>AbstractResourcesService</code>.
     *
     * @param bundleContext the OSGi bundle context
     */
    public AbstractResourcesService(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        bundleContext.addServiceListener(this);

        colorPack = getDefaultResourcePack(ColorPack.class, ColorPack.RESOURCE_NAME_DEFAULT_VALUE);
        if (colorPack != null)
            colorResources = getResources(colorPack);

        imagePack = getDefaultResourcePack(ImagePack.class, ImagePack.RESOURCE_NAME_DEFAULT_VALUE);
        if (imagePack != null)
            imageResources = getResources(imagePack);

        // changes the default locale if set in the config
        ConfigurationService confService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        String defaultLocale = (String) confService.getProperty(DEFAULT_LOCALE_CONFIG);
        if (defaultLocale != null)
            Locale.setDefault(ResourceManagementServiceUtils.getLocale(defaultLocale));

        languagePack = getDefaultResourcePack(LanguagePack.class, LanguagePack.RESOURCE_NAME_DEFAULT_VALUE);
        if (languagePack != null) {
            languageLocale = Locale.getDefault();
            languageResources = languagePack.getResources(languageLocale);
        }

        settingsPack = getDefaultResourcePack(SettingsPack.class, SettingsPack.RESOURCE_NAME_DEFAULT_VALUE);
        if (settingsPack != null)
            settingsResources = getResources(settingsPack);

        soundPack = getDefaultResourcePack(SoundPack.class, SoundPack.RESOURCE_NAME_DEFAULT_VALUE);
        if (soundPack != null)
            soundResources = getResources(soundPack);

        skinPack = getDefaultResourcePack(SkinPack.class, SkinPack.RESOURCE_NAME_DEFAULT_VALUE);
        if (skinPack != null) {
            if (imageResources != null)
                imageResources.putAll(skinPack.getImageResources());
            colorResources.putAll(skinPack.getColorResources());
            settingsResources.putAll(skinPack.getSettingsResources());
        }
    }

    /**
     * Handles all <code>ServiceEvent</code>s corresponding to <code>ResourcePack</code>
     * being registered or unregistered.
     *
     * @param event the <code>ServiceEvent</code> that notified us
     */
    public void serviceChanged(ServiceEvent event) {
        Object sService = bundleContext.getService(event.getServiceReference());
        if (!(sService instanceof ResourcePack)) {
            return;
        }

        ResourcePack resourcePack = (ResourcePack) sService;
        if (event.getType() == ServiceEvent.REGISTERED) {
            Timber.i("Resource registered %s", resourcePack);

            Map<String, String> resources = getResources(resourcePack);
            if (resourcePack instanceof ColorPack && colorPack == null) {
                colorPack = resourcePack;
                colorResources = resources;
            }
            else if (resourcePack instanceof ImagePack && imagePack == null) {
                imagePack = (ImagePack) resourcePack;
                imageResources = resources;
            }
            else if (resourcePack instanceof LanguagePack && languagePack == null) {
                languagePack = (LanguagePack) resourcePack;
                languageLocale = Locale.getDefault();
                languageResources = resources;
            }
            else if (resourcePack instanceof SettingsPack && settingsPack == null) {
                settingsPack = resourcePack;
                settingsResources = resources;
            }
            else if (resourcePack instanceof SoundPack && soundPack == null) {
                soundPack = resourcePack;
                soundResources = resources;
            }
            else if (resourcePack instanceof SkinPack && skinPack == null) {
                skinPack = (SkinPack) resourcePack;

                if (imagePack != null)
                    imageResources = getResources(imagePack);

                if (colorPack != null)
                    colorResources = getResources(colorPack);

                if (settingsPack != null)
                    settingsResources = getResources(settingsPack);

                if (imageResources != null)
                    imageResources.putAll(skinPack.getImageResources());
                colorResources.putAll(skinPack.getColorResources());
                settingsResources.putAll(skinPack.getSettingsResources());
                onSkinPackChanged();
            }
        }
        else if (event.getType() == ServiceEvent.UNREGISTERING) {
            if (resourcePack instanceof ColorPack && colorPack.equals(resourcePack)) {
                colorPack = getDefaultResourcePack(ColorPack.class, ColorPack.RESOURCE_NAME_DEFAULT_VALUE);
                if (colorPack != null)
                    colorResources = getResources(colorPack);
            }
            else if (resourcePack instanceof ImagePack && imagePack.equals(resourcePack)) {
                imagePack = getDefaultResourcePack(ImagePack.class, ImagePack.RESOURCE_NAME_DEFAULT_VALUE);
                if (imagePack != null)
                    imageResources = getResources(imagePack);
            }
            else if (resourcePack instanceof LanguagePack && languagePack.equals(resourcePack)) {
                languagePack = getDefaultResourcePack(LanguagePack.class, LanguagePack.RESOURCE_NAME_DEFAULT_VALUE);
            }
            else if (resourcePack instanceof SettingsPack && settingsPack.equals(resourcePack)) {
                settingsPack = getDefaultResourcePack(SettingsPack.class, SettingsPack.RESOURCE_NAME_DEFAULT_VALUE);
                if (settingsPack != null)
                    settingsResources = getResources(settingsPack);
            }
            else if (resourcePack instanceof SoundPack && soundPack.equals(resourcePack)) {
                soundPack = getDefaultResourcePack(SoundPack.class, SoundPack.RESOURCE_NAME_DEFAULT_VALUE);
                if (soundPack != null)
                    soundResources = getResources(soundPack);
            }
            else if (resourcePack instanceof SkinPack && skinPack.equals(resourcePack)) {
                if (imagePack != null) {
                    imageResources = getResources(imagePack);
                }

                if (colorPack != null) {
                    colorResources = getResources(colorPack);
                }

                if (settingsPack != null) {
                    settingsResources = getResources(settingsPack);
                }

                skinPack = getDefaultResourcePack(SkinPack.class, SkinPack.RESOURCE_NAME_DEFAULT_VALUE);
                if (skinPack != null) {
                    imageResources.putAll(skinPack.getImageResources());
                    colorResources.putAll(skinPack.getColorResources());
                    settingsResources.putAll(skinPack.getSettingsResources());
                }
                onSkinPackChanged();
            }
        }
    }

    /**
     * Method is invoked when the SkinPack is loaded or unloaded.
     */
    protected abstract void onSkinPackChanged();

    /**
     * Searches for the <code>ResourcePack</code> corresponding to the given <code>className</code> and <code></code>.
     *
     * @param clazz The name of the resource class.
     * @param typeName The name of the type we're looking for. For example: RESOURCE_NAME_DEFAULT_VALUE
     *
     * @return the <code>ResourcePack</code> corresponding to the given <code>className</code> and <code></code>.
     */
    protected <T extends ResourcePack> T getDefaultResourcePack(Class<T> clazz, String typeName) {
        Collection<ServiceReference<T>> serRefs;
        String osgiFilter = "(" + ResourcePack.RESOURCE_NAME + "=" + typeName + ")";

        try {
            serRefs = bundleContext.getServiceReferences(clazz, osgiFilter);
        } catch (InvalidSyntaxException ex) {
            serRefs = null;
            Timber.e(ex, "Could not obtain resource packs reference.");
        }

        if ((serRefs != null) && !serRefs.isEmpty()) {
            return bundleContext.getService(serRefs.iterator().next());
        }
        return null;
    }

    /**
     * Returns the <code>Map</code> of (key, value) pairs contained in the given resource pack.
     *
     * @param resourcePack The <code>ResourcePack</code> from which we're obtaining the resources.
     *
     * @return the <code>Map</code> of (key, value) pairs contained in the given resource pack.
     */
    protected Map<String, String> getResources(ResourcePack resourcePack) {
        return resourcePack.getResources();
    }

    /**
     * All the locales in the language pack.
     *
     * @return all the locales this Language pack contains.
     */
    public Iterator<Locale> getAvailableLocales() {
        return languagePack.getAvailableLocales();
    }

    /**
     * Returns the string for given <code>key</code> for specified <code>locale</code>.
     * It's the real process of retrieving string for specified locale.
     * The result is used in other methods that operate on localized strings.
     *
     * @param key the key name for the string
     * @param locale the Locale of the string
     *
     * @return the resources string corresponding to the given <code>key</code> and <code>locale</code>
     */
    protected String doGetI18String(String key, Locale locale) {
        Map<String, String> stringResources;
        if ((locale != null) && locale.equals(languageLocale)) {
            stringResources = languageResources;
        }
        else {
            stringResources = (languagePack == null) ? null : languagePack.getResources(locale);
        }
        return (stringResources == null) ? null : stringResources.get(key);
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     *
     * @return An internationalized string corresponding to the given key.
     */
    public String getI18NString(String key) {
        return getI18NString(key, null, Locale.getDefault());
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string.
     * @param params the parameters to pass to the localized string
     *
     * @return An internationalized string corresponding to the given key.
     */
    public String getI18NString(String key, String[] params) {
        return getI18NString(key, params, Locale.getDefault());
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param locale The locale.
     *
     * @return An internationalized string corresponding to the given key and given locale.
     */
    public String getI18NString(String key, Locale locale) {
        return getI18NString(key, null, locale);
    }

    /**
     * Does the additional processing on the resource string. It removes "&"
     * marks used for mnemonics and other characters.
     *
     * @param resourceString the resource string to be processed
     *
     * @return the processed string
     */
    private String processI18NString(String resourceString) {
        if (resourceString == null)
            return null;

        int mnemonicIndex = resourceString.indexOf('&');

        if (mnemonicIndex == 0 || (mnemonicIndex > 0
                && resourceString.charAt(mnemonicIndex - 1) != '\\')) {
            String firstPart = resourceString.substring(0, mnemonicIndex);
            String secondPart = resourceString.substring(mnemonicIndex + 1);
            resourceString = firstPart.concat(secondPart);
        }

        if (resourceString.indexOf('\\') > -1) {
            resourceString = resourceString.replaceAll("\\\\", "");
        }

        if (resourceString.contains("''")) {
            resourceString = resourceString.replaceAll("''", "'");
        }
        return resourceString;
    }

    /**
     * Returns an internationalized string corresponding to the given key.
     *
     * @param key The identifier of the string in the resources properties file.
     * @param params the parameters to pass to the localized string
     * @param locale The locale.
     *
     * @return An internationalized string corresponding to the given key.
     */
    public String getI18NString(String key, String[] params, Locale locale) {
        String resourceString = doGetI18String(key, locale);
        if (resourceString == null) {
            Timber.w("Missing resource for key: %s", key);
            return '!' + key + '!';
        }
        if (params != null) {
            resourceString = MessageFormat.format(resourceString, (Object[]) params);
        }
        return processI18NString(resourceString);
    }

    /**
     * Returns the character after the first '&' in the internationalized string corresponding to <code>key</code>
     *
     * @param key The identifier of the string in the resources properties file.
     *
     * @return the character after the first '&' in the internationalized string corresponding to <code>key</code>.
     */
    public char getI18nMnemonic(String key) {
        return getI18nMnemonic(key, Locale.getDefault());
    }

    /**
     * Returns the character after the first '&' in the internationalized string corresponding to <code>key</code>
     *
     * @param key The identifier of the string in the resources properties file.
     * @param locale The locale that we'd like to receive the result in.
     *
     * @return the character after the first '&' in the internationalized string corresponding to <code>key</code>.
     */
    public char getI18nMnemonic(String key, Locale locale) {
        String resourceString = doGetI18String(key, locale);

        if (resourceString == null) {
            Timber.w("Missing resource for key: %s", key);
            return 0;
        }

        int mnemonicIndex = resourceString.indexOf('&');
        if (mnemonicIndex > -1 && mnemonicIndex < resourceString.length() - 1) {
            return resourceString.charAt(mnemonicIndex + 1);
        }
        return 0;
    }

    /**
     * Returns the string value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     *
     * @return the string of the corresponding configuration key.
     */
    public String getSettingsString(String key) {
        return (settingsResources == null) ? null : settingsResources.get(key);
    }

    /**
     * Returns the int value of the corresponding configuration key.
     *
     * @param key The identifier of the string in the resources properties file.
     *
     * @return the int value of the corresponding configuration key.
     */
    public int getSettingsInt(String key) {
        String resourceString = getSettingsString(key);
        if (resourceString == null) {
            Timber.w("Missing resource for key: %s", key);
            return 0;
        }
        return Integer.parseInt(resourceString);
    }

    /**
     * Returns an <code>URL</code> from a given identifier.
     *
     * @param urlKey The identifier of the url.
     *
     * @return The url for the given identifier.
     */
    public URL getSettingsURL(String urlKey) {
        String path = getSettingsString(urlKey);
        if (path == null || path.isEmpty()) {
            Timber.w("Missing resource for key: %s", urlKey);
            return null;
        }
        return settingsPack.getClass().getClassLoader().getResource(path);
    }

    /**
     * Returns a stream from a given identifier.
     *
     * @param streamKey The identifier of the stream.
     *
     * @return The stream for the given identifier.
     */
    public InputStream getSettingsInputStream(String streamKey) {
        return getSettingsInputStream(streamKey, settingsPack.getClass());
    }

    /**
     * Returns a stream from a given identifier, obtained through the class
     * loader of the given resourceClass.
     *
     * @param streamKey The identifier of the stream.
     * @param resourceClass the resource class through which the resource would be obtained
     *
     * @return The stream for the given identifier.
     */
    public InputStream getSettingsInputStream(String streamKey, Class<?> resourceClass) {
        String path = getSettingsString(streamKey);
        if (path == null || path.isEmpty()) {
            Timber.w("Missing resource for key: %s", streamKey);
            return null;
        }
        return resourceClass.getClassLoader().getResourceAsStream(path);
    }

    /**
     * Returns the image path corresponding to the given key.
     *
     * @param key The identifier of the image in the resource properties file.
     *
     * @return the image path corresponding to the given key.
     */
    public String getImagePath(String key) {
        return (imageResources == null) ? null : imageResources.get(key);
    }

    /**
     * Loads an image from a given image identifier.
     *
     * @param imageID The identifier of the image.
     *
     * @return The image for the given identifier.
     */
    public byte[] getImageInBytes(String imageID) {
        InputStream in = getImageInputStream(imageID);
        if (in == null)
            return null;

        byte[] image = null;
        try {
            image = new byte[in.available()];
            in.read(image);
        } catch (IOException e) {
            Timber.e(e, "Failed to load image:%s", imageID);
        }
        return image;
    }

    /**
     * Loads an image from a given image identifier.
     *
     * @param imageId The identifier of the image.
     *
     * @return The image for the given identifier.
     */
    public ImageIcon getImage(String imageId) {
        URL imageURL = getImageURL(imageId);
        return (imageURL == null) ? null : new ImageIcon(imageURL);
    }

    /**
     * Returns the path of the sound corresponding to the given property key.
     *
     * @param soundKey the key, for the sound path
     *
     * @return the path of the sound corresponding to the given property key.
     */
    public String getSoundPath(String soundKey) {
        return soundResources.get(soundKey);
    }

    /**
     * Resources for currently loaded <code>ColorPack</code>.
     *
     * @return the currently color resources
     */
    protected Map<String, String> getColorResources() {
        return colorResources;
    }

    /**
     * Currently loaded <code>SkinPack</code>.
     *
     * @return the currently loaded skin pack
     */
    protected SkinPack getSkinPack() {
        return skinPack;
    }

    /**
     * Currently loaded image pack.
     *
     * @return the currently loaded image pack
     */
    protected ImagePack getImagePack() {
        return imagePack;
    }

    /**
     * Currently loaded sound pack.
     *
     * @return the currently loaded sound pack
     */
    protected ResourcePack getSoundPack() {
        return soundPack;
    }
}
