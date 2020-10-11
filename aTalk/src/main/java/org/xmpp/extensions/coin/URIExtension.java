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
 * URI packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class URIExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the URI data.
     */
    public static final String ELEMENT = "uri";

    /**
     * The namespace that URI belongs to. (cmeng added 20180618)
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transports:http:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Display text element name.
     */
    public static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * Purpose element name.
     */
    public static final String ELEMENT_PURPOSE = "purpose";

    /**
     * Display text.
     */
    private String displayText = null;

    /**
     * Purpose.
     */
    private String purpose = null;

    /**
     * Constructor.
     *
     * @param elementName element name
     */
    public URIExtension(String elementName)
    {
        super(elementName, NAMESPACE);
    }

    /**
     * Set the display text.
     *
     * @param displayText display text
     */
    public void setDisplayText(String displayText)
    {
        this.displayText = displayText;
    }

    /**
     * Get display text.
     *
     * @return display text
     */
    public String getDisplayText()
    {
        return displayText;
    }

    /**
     * Set the purpose.
     *
     * @param purpose purpose
     */
    public void setPurpose(String purpose)
    {
        this.purpose = purpose;
    }

    /**
     * Get purpose.
     *
     * @return purpose
     */
    public String getPurpose()
    {
        return purpose;
    }

    /**
     * Returns an XML representation of this extension.
     *
     * @return an XML representation of this extension.
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

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_PURPOSE, purpose);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(getElementName());
        return xml;
    }
}
