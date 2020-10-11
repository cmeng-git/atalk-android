/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingleinfo;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Server packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ServerExtension extends AbstractExtensionElement
{
    /**
     * The namespace.
     */
    public static final String NAMESPACE = "google:jingleinfo";

    /**
     * The element name.
     */
    public static final String ELEMENT = "server";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Host attribute name.
     */
    public static final String HOST_ATTR_NAME = "host";

    /**
     * TCP attribute name.
     */
    public static final String TCP_ATTR_NAME = "tcp";

    /**
     * UDP attribute name.
     */
    public static final String UDP_ATTR_NAME = "udp";

    /**
     * SSL attribute name.
     */
    public static final String SSL_ATTR_NAME = "tcpssl";

    /**
     * Constructor.
     */
    public ServerExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the host address.
     *
     * @return this host address
     */
    public String getHost()
    {
        return super.getAttributeAsString(HOST_ATTR_NAME);
    }

    /**
     * Returns the UDP port.
     *
     * @return the UDP port
     */
    public int getUdp()
    {
        return Integer.parseInt(super.getAttributeAsString(UDP_ATTR_NAME));
    }

    /**
     * Returns the TCP port.
     *
     * @return the TCP port
     */
    public int getTcp()
    {
        return Integer.parseInt(super.getAttributeAsString(TCP_ATTR_NAME));
    }

    /**
     * Returns the SSL port.
     *
     * @return the SSL port
     */
    public int getSsl()
    {
        return Integer.parseInt(super.getAttributeAsString(SSL_ATTR_NAME));
    }
}
