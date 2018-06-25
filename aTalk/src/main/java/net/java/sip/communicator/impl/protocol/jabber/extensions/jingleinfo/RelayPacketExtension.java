/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingleinfo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * Relay packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RelayPacketExtension extends AbstractPacketExtension
{
    /**
     * The namespace.
     */
    public static final String NAMESPACE = "google:jingleinfo";

    /**
     * The element name.
     */
    public static final String ELEMENT_NAME = "relay";

    /**
     * The token.
     */
    private String token = null;

    /**
     * Constructor.
     */
    public RelayPacketExtension()
    {
        super(ELEMENT_NAME, NAMESPACE);
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
    public XmlStringBuilder toXML()
    {
        XmlStringBuilder xml = new XmlStringBuilder();

        xml.openElement(ELEMENT_NAME);
        xml.optElement(token, token);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML());
        }
        xml.closeElement(ELEMENT_NAME);
        return xml;
    }
}
