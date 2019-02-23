/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.Map;

/**
 * SIP Dialog ID packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class SIPDialogIDExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the SIP Dialog ID data.
     */
    public static final String ELEMENT_NAME = "sip";

    /**
     * The namespace that SIP Dialog ID belongs to.
     */
    public static final String NAMESPACE = "sip";

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
    public SIPDialogIDExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Returns an XML representation of this extension.
     *
     * @return an XML representation of this extension.
     */
    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.prelude(getElementName(), getNamespace());

        // add the rest of the attributes if any
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            xml.optAttribute(entry.getKey(), entry.getValue().toString());
        }
        xml.append(">");

        xml.optElement(ELEMENT_DISPLAY_TEXT, displayText);
        xml.optElement(ELEMENT_CALLID, callID);
        xml.optElement(ELEMENT_FROMTAG, fromTag);
        xml.optElement(ELEMENT_TOTAG, toTag);

        for (ExtensionElement ext : getChildExtensions()) {
            xml.append(ext.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(ELEMENT_NAME);
        return xml;
    }
}
