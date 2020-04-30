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
 * SIP Dialog ID packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class SIPDialogIDExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the SIP Dialog ID data.
     */
    public static final String ELEMENT = "sip";

    /**
     * The namespace that SIP Dialog ID belongs to.
     */
    public static final String NAMESPACE = "sip";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Display text element name.
     */
    public static final String ELEMENT_DISPLAY_TEXT = "display-text";

    /**
     * Call ID element name.
     */
    public static final String ELEMENT_CALLID = "call-id";

    /**
     * From tag element name.
     */
    public static final String ELEMENT_FROMTAG = "from-tag";

    /**
     * From tag element name.
     */
    public static final String ELEMENT_TOTAG = "to-tag";

    /**
     * Display text.
     */
    private String displayText = null;

    /**
     * Call ID.
     */
    private String callID = null;

    /**
     * From tag.
     */
    private String fromTag = null;

    /**
     * To tag.
     */
    private String toTag = null;

    /**
     * Constructor
     */
    public SIPDialogIDExtension()
    {
        super(ELEMENT, NAMESPACE);
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
        xml.optElement(ELEMENT_CALLID, callID);
        xml.optElement(ELEMENT_FROMTAG, fromTag);
        xml.optElement(ELEMENT_TOTAG, toTag);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(ELEMENT);
        return xml;
    }
}
