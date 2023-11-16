/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.control;

import java.util.Map;

import javax.media.Control;

/**
 * An interface used to pass additional attributes (received via SDP/Jingle) to codecs.
 *
 * @author Damian Minkov
 */
public interface AdvancedAttributesAwareCodec extends Control {
    /**
     * Sets the additional attributes to <code>attributes</code>
     *
     * @param attributes The additional attributes to set
     */
    public void setAdvancedAttributes(Map<String, String> attributes);
}
