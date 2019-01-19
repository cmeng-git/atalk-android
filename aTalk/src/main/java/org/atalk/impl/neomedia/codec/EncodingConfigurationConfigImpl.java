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
 */
public class EncodingConfigurationConfigImpl extends EncodingConfigurationImpl
{
    /**
     * Holds the prefix that will be used to store properties
     */
    private final String propPrefix;

    /**
     * The <tt>ConfigurationService</tt> instance that will be used to store properties
     */
    private final ConfigurationService cfg = LibJitsi.getConfigurationService();

    /**
     * Constructor. Loads the configuration from <tt>prefix</tt>
     *
     * @param prefix the prefix to use when loading and storing properties
     */
    public EncodingConfigurationConfigImpl(String prefix)
    {
        propPrefix = prefix;
        loadConfig();
    }

    /**
     * Loads the properties stored under <tt>this.propPrefix</tt>
     */
    private void loadConfig()
    {
        Map<String, String> properties = new HashMap<>();

        for (String pName : cfg.getPropertyNamesByPrefix(propPrefix, false))
            properties.put(pName, cfg.getString(pName));
        loadProperties(properties);
    }

    /**
     * Sets the preference associated with <tt>encoding</tt> to <tt>priority</tt>, and stores the
     * appropriate property in the configuration service.
     *
     * @param encoding the <tt>MediaFormat</tt> specifying the encoding to set the priority of
     * @param priority a positive <tt>int</tt> indicating the priority of <tt>encoding</tt> to set
     * @see EncodingConfigurationImpl#setPriority(MediaFormat, int)
     */
    @Override
    public void setPriority(MediaFormat encoding, int priority)
    {
        super.setPriority(encoding, priority);
        cfg.setProperty(propPrefix + "." + getEncodingPreferenceKey(encoding), priority);
    }
}
