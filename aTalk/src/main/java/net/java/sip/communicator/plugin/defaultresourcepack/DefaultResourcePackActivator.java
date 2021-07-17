/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
pHideExtendedAwayStatus * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.plugin.defaultresourcepack;

import net.java.sip.communicator.service.resources.*;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.net.URL;
import java.util.*;

import timber.log.Timber;

/**
 * @author damencho
 * @author Eng Chong Meng
 */
public class DefaultResourcePackActivator implements BundleActivator
{
    static BundleContext bundleContext;

    // buffer for ressource files found
    private static Hashtable<String, Iterator<String>> ressourcesFiles = new Hashtable<>();

    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;

        DefaultColorPackImpl colPackImpl = new DefaultColorPackImpl();
        Hashtable<String, String> props = new Hashtable<>();
        props.put(ResourcePack.RESOURCE_NAME, ColorPack.RESOURCE_NAME_DEFAULT_VALUE);
        bundleContext.registerService(ColorPack.class.getName(), colPackImpl, props);

        DefaultImagePackImpl imgPackImpl = new DefaultImagePackImpl();
        Hashtable<String, String> imgProps = new Hashtable<>();
        imgProps.put(ResourcePack.RESOURCE_NAME, ImagePack.RESOURCE_NAME_DEFAULT_VALUE);
        bundleContext.registerService(ImagePack.class.getName(), imgPackImpl, imgProps);

        //		DefaultLanguagePackImpl langPackImpl = new DefaultLanguagePackImpl();
        //		Hashtable<String, String> langProps = new Hashtable<String, String>();
        //		langProps.put(ResourcePack.RESOURCE_NAME, LanguagePack.RESOURCE_NAME_DEFAULT_VALUE);
        //		bundleContext.registerService(LanguagePack.class.getName(), langPackImpl, langProps);

        DefaultSettingsPackImpl setPackImpl = new DefaultSettingsPackImpl();
        Hashtable<String, String> setProps = new Hashtable<>();
        setProps.put(ResourcePack.RESOURCE_NAME, SettingsPack.RESOURCE_NAME_DEFAULT_VALUE);
        bundleContext.registerService(SettingsPack.class.getName(), setPackImpl, setProps);

        DefaultSoundPackImpl sndPackImpl = new DefaultSoundPackImpl();
        Hashtable<String, String> sndProps = new Hashtable<>();
        sndProps.put(ResourcePack.RESOURCE_NAME, SoundPack.RESOURCE_NAME_DEFAULT_VALUE);
        bundleContext.registerService(SoundPack.class.getName(), sndPackImpl, sndProps);

        Timber.i("Default resources ... [REGISTERED]");
    }

    public void stop(BundleContext bc)
            throws Exception
    {
    }

    /**
     * Finds all properties files for the given path in this bundle.
     *
     * @param path the path pointing to the properties files.
     */
    protected static Iterator<String> findResourcePaths(String path, String pattern)
    {
        Iterator<String> bufferedResult = ressourcesFiles.get(path + pattern);
        if (bufferedResult != null) {
            return bufferedResult;
        }

        ArrayList<String> propertiesList = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Enumeration<URL> propertiesUrls = bundleContext.getBundle().findEntries(path, pattern, false);

        if (propertiesUrls != null) {
            while (propertiesUrls.hasMoreElements()) {
                URL propertyUrl = propertiesUrls.nextElement();

                // Remove the first slash.
                String propertyFilePath = propertyUrl.getPath().substring(1);

                // Replace all slashes with dots.
                propertyFilePath = propertyFilePath.replaceAll("/", ".");

                propertiesList.add(propertyFilePath);
            }
        }

        Iterator<String> result = propertiesList.iterator();
        ressourcesFiles.put(path + pattern, result);

        return result;
    }
}
