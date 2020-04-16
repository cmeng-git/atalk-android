/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle.element;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import javax.xml.namespace.QName;

/**
 * The <tt>reason</tt> element provides human or machine-readable information explaining what
 * prompted the <tt>action</tt> of the encapsulating <tt>jingle</tt> element.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */

public class JingleReason implements ExtensionElement
{
    /**
     * The name space (or rather lack thereof ) that the reason element belongs to.
     */
    public static final String NAMESPACE = "";

    /**
     * The name of the "content" element.
     */
    public static final String ELEMENT = "reason";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the text element.
     */
    public static final String TEXT_ELEMENT = "text";

    /**
     * The reason that this packet extension is transporting.
     */
    private final Reason reason;

    /**
     * The content of the text element (if any) providing human-readable information about the reason for the action.
     */
    private String text;

    /**
     * XEP-0166 mentions that the "reason" element MAY contain an element qualified by some other
     * namespace that provides more detailed machine- readable information about the reason for the action.
     */
    private ExtensionElement otherExtension;

    /**
     * Creates a new <tt>JingleReason</tt> instance with the specified reason String.
     *
     * @param reason the reason string that we'd like to transport in this packet extension, which may or
     * may not be one of the static strings defined here.
     * @param text an element providing human-readable information about the reason for the action or
     * <tt>null</tt> if no such information is currently available.
     * @param packetExtension any other element that MAY be providing further information or <tt>null</tt> if no
     * such element has been specified.
     */
    public JingleReason(Reason reason, String text, ExtensionElement packetExtension)
    {
        this.reason = reason;
        this.text = text;
        this.otherExtension = packetExtension;
    }

    /**
     * Returns the reason string that this packet extension is transporting.
     *
     * @return the reason string that this packet extension is transporting.
     */
    public Reason getReason()
    {
        return reason;
    }

    /**
     * Returns human-readable information about the reason for the action or <tt>null</tt> if no
     * such information is currently available.
     *
     * @return human-readable information about the reason for the action or <tt>null</tt> if no
     * such information is currently available.
     */
    public String getText()
    {
        return text;
    }

    /**
     * Sets the human-readable information about the reason for the action or <tt>null</tt> if no
     * such information is currently available
     *
     * @param text the human-readable information about the reason for the action or <tt>null</tt> if no
     * such information is currently available
     */
    public void setText(String text)
    {
        this.text = text;
    }

    /**
     * Returns an extra extension containing further info about this action or <tt>null</tt> if no
     * such extension has been specified. This method returns the extension that XEP-0166 refers to
     * the following way: the "reason" element MAY contain an element qualified by some other
     * namespace that provides more detailed machine-readable information about the reason for the
     * action.
     *
     * @return an extra extension containing further info about this action or <tt>null</tt> if no
     * such extension has been specified.
     */
    public ExtensionElement getOtherExtension()
    {
        return otherExtension;
    }

    /**
     * Sets the extra extension containing further info about this action or <tt>null</tt> if no
     * such extension has been specified.
     *
     * @param otherExtension the extra extension containing further info about this action or <tt>null</tt> if no
     * such extension has been specified
     */
    public void setOtherExtension(ExtensionElement otherExtension)
    {
        this.otherExtension = otherExtension;
    }

    /**
     * Returns the root element name.
     *
     * @return the element name.
     */
    public String getElementName()
    {
        return ELEMENT;
    }

    /**
     * Returns the root element XML namespace.
     *
     * @return the namespace.
     */
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * Returns the XML representation of the ExtensionElement.
     *
     * @return the packet extension as XML.
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.openElement(getElementName());

        xml.halfOpenElement(getReason().toString());
        xml.closeEmptyElement();

        // add reason "text" if we have it
        xml.optElement(TEXT_ELEMENT, getText());

        // add the extra element if it has been specified.
        if (getOtherExtension() != null) {
            xml.append(getOtherExtension().toXML(XmlEnvironment.EMPTY));
        }

        xml.closeElement(getElementName());
        return xml;
    }
}
