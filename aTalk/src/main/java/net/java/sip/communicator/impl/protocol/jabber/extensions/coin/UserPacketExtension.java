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
 * User packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserPacketExtension extends AbstractPacketExtension
{
    /**
     * The name of the element that contains the user data.
     */
    public static final String ELEMENT_NAME = "user";

    /**
     * The namespace that user belongs to.
     */
    public static final String NAMESPACE = null;

    /**
     * Entity attribute name.
     */
    public static final String ENTITY_ATTR_NAME = "entity";

    /**
     * Entity attribute name.
     */
    public static final String STATE_ATTR_NAME = "state";

    /**
     * Display text element name.
     */
    public static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * Display text.
     */
    private String displayText = null;

    /**
     * Constructor.
     *
     * @param entity entity
     */
    public UserPacketExtension(String entity)
    {
        super(ELEMENT_NAME, NAMESPACE);
        setAttribute("entity", entity);
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

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML());
        }

        xml.closeElement(getElementName());
        return xml;
    }
}
