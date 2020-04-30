/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.util.List;

import javax.xml.namespace.QName;

/**
 * An {@link AbstractExtensionElement} implementation for transport elements.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class RawUdpTransportExtension extends IceUdpTransportExtension
{
    /**
     * The name of the "transport" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transports:raw-udp:1";
    /**
     * The name of the "transport" element.
     */
    public static final String ELEMENT = "transport";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Creates a new {@link RawUdpTransportExtension} instance.
     */
    public RawUdpTransportExtension()
    {
        super(ELEMENT, NAMESPACE);
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
