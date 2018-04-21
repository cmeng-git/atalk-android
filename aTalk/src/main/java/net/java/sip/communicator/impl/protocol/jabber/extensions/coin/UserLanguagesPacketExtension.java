/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.Map;

/**
 * User languages packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserLanguagesPacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the element that contains the user languages data.
     */
    public static final String ELEMENT_NAME = "languages";

    /**
     * The namespace that user languages belongs to.
     */
    public static final String NAMESPACE = "";

    /**
     * The name of the element that contains the media data.
     */
    public static final String ELEMENT_LANGUAGES = "stringvalues";

    /**
     * The list of languages separated by space.
     */
    private String languages = null;

    /**
     * Constructor.
     */
    public UserLanguagesPacketExtension()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Set languages.
     *
     * @param languages list of languages
     */
    public void setLanguages(String languages)
    {
        this.languages = languages;
    }

    /**
     * Get languages.
     *
     * @return languages
     */
    public String getLanguages()
    {
        return languages;
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
        xml.prelude(getElementName(), getNamespace());

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.append(">");

        xml.optElement(ELEMENT_LANGUAGES, languages);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML());
        }

        xml.closeElement(getElementName());
        return xml;
    }
}
