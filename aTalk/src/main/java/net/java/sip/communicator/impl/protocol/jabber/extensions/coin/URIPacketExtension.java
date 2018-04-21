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
 * URI packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class URIPacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the element that contains the URI data.
     */
    public static final String ELEMENT_NAME = "uri";

    /**
     * The namespace that URI belongs to.
     */
    public static final String NAMESPACE = "uri";

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
    public URIPacketExtension(String elementName)
    {
        super(NAMESPACE, elementName);
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
    public XmlStringBuilder toXML()
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.prelude(getElementName(), getNamespace());

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.append(">");

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_PURPOSE, purpose);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML());
        }

        xml.closeElement(getElementName());
        return xml;
    }
}
