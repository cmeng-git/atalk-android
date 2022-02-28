/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.codec.EncodingConfiguration;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * An interface to get/set settings in the encodings panel.
 *
 * @author Boris Grozev
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class EncodingsRegistrationUtil implements Serializable
{
    /**
     * Whether to override global encoding settings.
     */
    private boolean overrideEncodingSettings = false;

    /**
     * Encoding properties associated with this account.
     */
    private Map<String, String> encodingProperties = new HashMap<>();

    /**
     * Get the stored encoding properties
     *
     * @return The stored encoding properties.
     */
    public Map<String, String> getEncodingProperties()
    {
        return encodingProperties;
    }

    /**
     * Set the encoding properties
     *
     * @param encodingProperties The encoding properties to set.
     */
    public void setEncodingProperties(Map<String, String> encodingProperties)
    {
        this.encodingProperties = encodingProperties;
    }

    /**
     * Whether override encodings is enabled
     *
     * @return Whether override encodings is enabled
     */
    public boolean isOverrideEncodings()
    {
        return overrideEncodingSettings;
    }

    /**
     * Set the override encodings setting to <code>override</code>
     *
     * @param override The value to set the override encoding settings to.
     */
    public void setOverrideEncodings(boolean override)
    {
        this.overrideEncodingSettings = override;
    }

    /**
     * Loads encoding properties from given <code>accountID</code> into this encodings registration object.
     *
     * @param accountID the <code>AccountID</code> to be loaded.
     * @param mediaService the <code>MediaService</code> that will be used to create <code>EncodingConfiguration</code>.
     */
    public void loadAccount(AccountID accountID, MediaService mediaService)
    {
        String overrideEncodings = accountID.getAccountPropertyString(ProtocolProviderFactory.OVERRIDE_ENCODINGS);
        boolean isOverrideEncodings = Boolean.parseBoolean(overrideEncodings);
        setOverrideEncodings(isOverrideEncodings);

        Map<String, String> encodingProperties = new HashMap<>();
        EncodingConfiguration encodingConfiguration = mediaService.createEmptyEncodingConfiguration();
        encodingConfiguration.loadProperties(accountID.getAccountProperties(),
                ProtocolProviderFactory.ENCODING_PROP_PREFIX);
        encodingConfiguration.storeProperties(encodingProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".");
        setEncodingProperties(encodingProperties);
    }

    /**
     * Stores encoding configuration properties in given <code>propertiesMap</code>.
     *
     * @param propertiesMap the properties map that will be used.
     */
    public void storeProperties(Map<String, String> propertiesMap)
    {
        propertiesMap.put(ProtocolProviderFactory.OVERRIDE_ENCODINGS, Boolean.toString(isOverrideEncodings()));
        propertiesMap.putAll(getEncodingProperties());
    }

    /**
     * Creates new instance of <code>EncodingConfiguration</code> reflecting this object's encoding configuration state.
     *
     * @param mediaService the <code>MediaService</code> that will be used to create new instance of
     * <code>EncodingConfiguration</code>.
     * @return <code>EncodingConfiguration</code> reflecting this object's encoding configuration state.
     */
    public EncodingConfiguration createEncodingConfig(MediaService mediaService)
    {
        EncodingConfiguration encodingConfiguration = mediaService.createEmptyEncodingConfiguration();
        encodingConfiguration.loadProperties(encodingProperties,
                ProtocolProviderFactory.ENCODING_PROP_PREFIX + ".");

        return encodingConfiguration;
    }
}
