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
 * Conference medium packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ConferenceMediumExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the conference medium.
     */
    public static final String ELEMENT = "medium";

    /**
     * The namespace that conference medium belongs to.
     */
    public static final String NAMESPACE = CoinIQ.NAMESPACE;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Display text element name.
     */
    public static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * Type element name.
     */
    public static final String ELEMENT_TYPE = "type";

    /**
     * Status element name.
     */
    public static final String ELEMENT_STATUS = "status";

    /**
     * Label attribute name.
     */
    public static final String LABEL_ATTR_NAME = "label";

    /**
     * Type.
     */
    private String type = null;

    /**
     * Display text.
     */
    private String displayText = null;

    /**
     * Media status.
     */
    private String status = null;

    /**
     * Constructor.
     *
     * @param elementName element name
     * @param label label
     */
    public ConferenceMediumExtension(String elementName, String label)
    {
        super(elementName, NAMESPACE);
        setAttribute(LABEL_ATTR_NAME, label);
    }

    /**
     * Set status.
     *
     * @param status status.
     */
    public void setStatus(String status)
    {
        this.status = status;
    }

    /**
     * Set type.
     *
     * @param type type
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * Set display text.
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
     * Get type.
     *
     * @return type
     */
    public String getType()
    {
        return type;
    }

    /**
     * Get status.
     *
     * @return status.
     */
    public String getStatus()
    {
        return status;
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
        xml.append(">");

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_TYPE, type);
        xml.optElement(ELEMENT_STATUS, status);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }

        xml.closeElement(getElementName());
        return xml;
    }
}
