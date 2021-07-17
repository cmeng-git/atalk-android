/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingleinfo;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import javax.xml.namespace.QName;

/**
 * Relay packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RelayExtension extends AbstractExtensionElement
{
    /**
     * The namespace.
     */
    public static final String NAMESPACE = "google:jingleinfo";

    /**
     * The element name.
     */
    public static final String ELEMENT = "relay";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The token.
     */
    private String token = null;

    /**
     * Constructor.
     */
    public RelayExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Set the token.
     *
     * @param token token
     */
    public void setToken(String token)
    {
        this.token = token;
    }

    /**
     * Get the token.
     *
     * @return authentication token
     */
    public String getToken()
    {
        return token;
    }

    /**
     * Get an XML string representation.
     *
     * @return XML string representation
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder();

        xml.openElement(ELEMENT);
        xml.optElement(token, token);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(ELEMENT);
        return xml;
    }
}
