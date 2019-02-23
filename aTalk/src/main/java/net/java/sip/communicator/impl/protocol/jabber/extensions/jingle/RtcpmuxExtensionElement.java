/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;

/**
 * An 'rtcp-mux' extension.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class RtcpmuxExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the "encryption" element.
     */
    public static final String ELEMENT_NAME = "rtcp-mux";

    /**
     * Creates a new instance of <tt>RtcpmuxExtensionElement</tt>.
     */
    public RtcpmuxExtensionElement()
    {
        super(ELEMENT_NAME, null);
    }
}
