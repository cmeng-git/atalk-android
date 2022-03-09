/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.util.MediaType;

import java.awt.Component;

/**
 * An interface that exposes the <code>Component</code>s used in media configuration user interfaces.
 *
 * @author Boris Grozev
 */
public interface MediaConfigurationService
{
    /**
     * Returns a <code>Component</code> for audio configuration
     *
     * @return A <code>Component</code> for audio configuration
     */
    public Component createAudioConfigPanel();

    /**
     * Returns a <code>Component</code> for video configuration
     *
     * @return A <code>Component</code> for video configuration
     */
    public Component createVideoConfigPanel();

    /**
     * Returns a <code>Component</code> for encodings configuration (either audio or video)
     *
     * @param mediaType The type of media -- either MediaType.AUDIO or MediaType.VIDEO
     * @param encodingConfiguration The <code>EncodingConfiguration</code> instance to use.
     * @return The <code>Component</code> for encodings configuration
     */
    public Component createEncodingControls(MediaType mediaType,
            EncodingConfiguration encodingConfiguration);

    /**
     * Returns the <code>MediaService</code> instance
     *
     * @return the <code>MediaService</code> instance
     */
    public MediaService getMediaService();
}
