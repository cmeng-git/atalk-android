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
 * Description packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class DescriptionExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the description data.
     */
    public static final String ELEMENT = "conference-description";

    /**
     * The namespace that description belongs to.
     */
    public static final String NAMESPACE = null;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Subject element name.
     */
    public static final String ELEMENT_SUBJECT = "subject";

    /**
     * Display text element name.
     */
    public static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * Free text element name.
     */
    public static final String ELEMENT_FREE_TEXT = "free-text";

    /**
     * Max user count element name.
     */
    public static final String ELEMENT_MAX_USER_COUNT = "maximum-user-count";

    /**
     * The subject.
     */
    private String subject = "";

    /**
     * Display text.
     */
    private String displayText = null;

    /**
     * Free text.
     */
    private String freeText = null;

    /**
     * Maximum user count.
     */
    private int maximumUserCount = 0;

    /**
     * Constructor.
     */
    public DescriptionExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Set subject.
     *
     * @param subject subject
     */
    public void setSubject(String subject)
    {
        this.subject = subject;
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
     * Set free text.
     *
     * @param freeText free text
     */
    public void setFreeText(String freeText)
    {
        this.freeText = freeText;
    }

    /**
     * Get subject.
     *
     * @return subject
     */
    public String getSubject()
    {
        return subject;
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
     * Get free text.
     *
     * @return free text
     */
    public String getFreeText()
    {
        return freeText;
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

        xml.optElement(ELEMENT_SUBJECT, subject);
        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_FREE_TEXT, freeText);

        if (maximumUserCount != 0)
            xml.optIntElement(ELEMENT_MAX_USER_COUNT, maximumUserCount);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }

        xml.closeElement(getElementName());
        return xml;
    }
}
