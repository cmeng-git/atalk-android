/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * An 'rtcp-mux' extension.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class RtcpmuxExtension extends AbstractExtensionElement
{
    /**
     * The name of the "encryption" element.
     */
    public static final String ELEMENT = "rtcp-mux";

    /**
     * Creates a new instance of <tt>RtcpmuxExtensionElement</tt>.
     */
    public RtcpmuxExtension()
    {
        super(ELEMENT, null);
    }
}
