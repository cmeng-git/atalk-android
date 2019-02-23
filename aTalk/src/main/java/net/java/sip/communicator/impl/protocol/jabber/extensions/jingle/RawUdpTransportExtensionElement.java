/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.util.List;

/**
 * An {@link AbstractExtensionElement} implementation for transport elements.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class RawUdpTransportExtensionElement extends IceUdpTransportExtensionElement
{
    /**
     * The name of the "transport" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transports:raw-udp:1";
    /**
     * The name of the "transport" element.
     */
    public static final String ELEMENT_NAME = "transport";

    /**
     * Creates a new {@link RawUdpTransportExtensionElement} instance.
     */
    public RawUdpTransportExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Returns this element's child (local or remote) candidate elements.
     *
     * @return this element's child (local or remote) candidate elements.
     */
    @Override
    public List<? extends ExtensionElement> getChildExtensions()
    {
        // TODO Auto-generated method stub
        return super.getChildExtensions();
    }
}
