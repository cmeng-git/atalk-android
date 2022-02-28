/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.jingle;

import org.jivesoftware.smackx.AbstractXmlElement;

/**
 * An {@link AbstractXmlElement} implementation for Raw UDP transport elements.
 * @see <a href="https://xmpp.org/extensions/xep-0177.html">XEP-0177: Jingle Raw UDP Transport Method 1.1.1 (2020-12-10)</a>
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class RawUdpTransport extends IceUdpTransport
{
    /**
     * The elementName of the "transport" element.
     */
    public static final String ELEMENT = "transport";

    /**
     * The nameSpace of the "transport" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transports:raw-udp:1";

    public RawUdpTransport()
    {
        super(builder());
    }

    /**
     * Creates a new {@link RawUdpTransport}
     */
    public RawUdpTransport(Builder builder)
    {
        super(builder);
    }

    public static Builder builder()
    {
        return new Builder(ELEMENT, NAMESPACE);
    }

    /**
     * Builder for RawUdpTransport. Use {@link AbstractXmlElement#builder()} to
     * obtain a new instance and {@link #build} to build the RawUdpTransport.
     */
    public static final class Builder extends IceUdpTransport.Builder
    {
        protected Builder(String element, String namespace)
        {
            super(element, namespace);
        }

        @Override
        public RawUdpTransport build()
        {
            return new RawUdpTransport(this);
        }

        @Override
        protected Builder getThis()
        {
            return this;
        }
    }
}
