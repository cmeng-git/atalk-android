/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.Map;

import javax.xml.namespace.QName;

/**
 * User languages packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserLanguagesExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the user languages data.
     */
    public static final String ELEMENT = "languages";

    /**
     * The namespace that user languages belongs to.
     */
    public static final String NAMESPACE = "";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

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
    public UserLanguagesExtension()
    {
        super(ELEMENT, NAMESPACE);
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
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.rightAngleBracket();

        xml.optElement(ELEMENT_LANGUAGES, languages);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(getElementName());
        return xml;
    }
}
