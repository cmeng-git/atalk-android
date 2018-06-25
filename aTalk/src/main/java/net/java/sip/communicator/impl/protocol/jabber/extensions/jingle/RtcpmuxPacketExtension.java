/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

/**
 * An 'rtcp-mux' extension.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class RtcpmuxPacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the "encryption" element.
     */
    public static final String ELEMENT_NAME = "rtcp-mux";

    /**
     * Creates a new instance of <tt>RtcpmuxPacketExtension</tt>.
     */
    public RtcpmuxPacketExtension()
    {
        super(ELEMENT_NAME, null);
    }
}
