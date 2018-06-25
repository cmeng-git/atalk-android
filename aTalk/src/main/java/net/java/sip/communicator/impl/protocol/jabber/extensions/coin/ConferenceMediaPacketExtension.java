/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

/**
 * Conference media packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ConferenceMediaPacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the element that contains the conference media.
     */
    public static final String ELEMENT_NAME = "available-media";

    /**
     * The namespace that conference media belongs to. cmeng NAMESPACE cannot be empty
     */
    public static final String NAMESPACE = CoinIQ.NAMESPACE;

    /**
     * Constructor.
     */
    public ConferenceMediaPacketExtension()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }
}
