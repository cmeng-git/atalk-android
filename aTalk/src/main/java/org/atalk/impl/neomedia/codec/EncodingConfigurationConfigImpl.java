/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.format.MediaFormat;

import java.util.HashMap;
import java.util.Map;

/**
 * An EncodingConfiguration implementation that synchronizes its preferences with a ConfigurationService.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class EncodingConfigurationConfigImpl extends EncodingConfigurationImpl
{
    /**
     * Holds the prefix that will be used to store properties
     */
    private final String propPrefix;

    /**
     * The <code>ConfigurationService</code> instance that will be used to store properties
     */
    private final ConfigurationService cfg = LibJitsi.getConfigurationService();

    /**
     * Constructor. Loads the configuration from <code>prefix</code>
     *
     * @param prefix the prefix to use when loading and storing properties
     */
    public EncodingConfigurationConfigImpl(String prefix)
    {
        propPrefix = prefix;
        loadConfig();
    }

    /**
     * Loads the properties stored under <code>this.propPrefix</code>
     */
    private void loadConfig()
    {
        Map<String, String> properties = new HashMap<>();

        for (String pName : cfg.getPropertyNamesByPrefix(propPrefix, false))
            properties.put(pName, cfg.getString(pName));
        loadProperties(properties);
    }

    /**
     * Sets the preference associated with <code>encoding</code> to <code>priority</code>, and stores the
     * appropriate property in the configuration service.
     *
     * @param encoding the <code>MediaFormat</code> specifying the encoding to set the priority of
     * @param priority a positive <code>int</code> indicating the priority of <code>encoding</code> to set
     * @see EncodingConfigurationImpl#setPriority(MediaFormat, int)
     */
    @Override
    public void setPriority(MediaFormat encoding, int priority)
    {
        super.setPriority(encoding, priority);
        cfg.setProperty(propPrefix + "." + getEncodingPreferenceKey(encoding), priority);
    }
}
