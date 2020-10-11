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

/**
 * Execution packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ExecutionExtension extends AbstractExtensionElement
{
    /**
     * The namespace that media belongs to.
     */
    public static final String NAMESPACE = null;

    /**
     * The name of the element that contains the media data.
     */
    public static final String ELEMENT_REFERRED_NAME = "referred";

    /**
     * The name of the element that contains the media data.
     */
    public static final String ELEMENT_DISCONNECTION_NAME = "disconnection-info";

    /**
     * The name of the element that contains the media data.
     */
    public static final String ELEMENT_JOINING_NAME = "joining-info";

    /**
     * The name of the element that contains the media data.
     */
    public static final String ELEMENT_MODIFIED_NAME = "modified";

    /**
     * "By" element name.
     */
    public static final String ELEMENT_BY = "by";

    /**
     * "Reason" element name.
     */
    public static final String ELEMENT_REASON = "reason";

    /**
     * "When" element name.
     */
    public static final String ELEMENT_WHEN = "display-text";

    /**
     * Date of the execution.
     */
    private String when = null;

    /**
     * By.
     */
    private String by = null;

    /**
     * Reason.
     */
    private String reason = null;

    /**
     * Set "by" field.
     *
     * @param by string to set
     */
    public void setBy(String by)
    {
        this.by = by;
    }

    /**
     * Get "by" field.
     *
     * @return "by" field
     */
    public String getBy()
    {
        return by;
    }

    /**
     * Set "when" field.
     *
     * @param when string to set
     */
    public void setWhen(String when)
    {
        this.when = when;
    }

    /**
     * Get "when" field.
     *
     * @return "when" field
     */
    public String getWhen()
    {
        return when;
    }

    /**
     * Set "reason" field.
     *
     * @param reason string to set
     */
    public void setReason(String reason)
    {
        this.reason = reason;
    }

    /**
     * Get "reason" field.
     *
     * @return "reason" field
     */
    public String getReason()
    {
        return reason;
    }

    /**
     * Constructor.
     *
     * @param elementName name of the element
     */
    public ExecutionExtension(String elementName)
    {
        super(elementName, NAMESPACE);
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

        xml.optElement(ELEMENT_BY, by);
        xml.optElement(ELEMENT_WHEN, when);
        xml.optElement(ELEMENT_REASON, reason);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(getElementName());
        return xml;
    }
}
