/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Packet extension that holds RTCP feedback types of the {@link PayloadTypeExtension}.
 * Defined in XEP-0293.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class RtcpFbExtension extends AbstractExtensionElement
{
    /**
     * The name space for RTP feedback elements.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:rtcp-fb:0";

    /**
     * The name of the RTCP feedback element.
     */
    public static final String ELEMENT = "rtcp-fb";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name the attribute that holds the feedback type.
     */
    public static final String TYPE_ATTR_NAME = "type";

    /**
     * The name the attribute that holds the feedback subtype.
     */
    public static final String SUBTYPE_ATTR_NAME = "subtype";

    /**
     * Creates new empty instance of <tt>RtcpFbExtensionElement</tt>.
     */
    public RtcpFbExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Sets RTCP feedback type attribute.
     *
     * @param feedbackType the RTCP feedback type to set.
     */
    public void setFeedbackType(String feedbackType)
    {
        setAttribute(TYPE_ATTR_NAME, feedbackType);
    }

    /**
     * Returns RTCP feedback type attribute value if already set or <tt>null</tt> otherwise.
     *
     * @return RTCP feedback type attribute if already set or <tt>null</tt> otherwise.
     */
    public String getFeedbackType()
    {
        return getAttributeAsString(TYPE_ATTR_NAME);
    }

    /**
     * Sets RTCP feedback subtype attribute.
     *
     * @param feedbackSubType the RTCP feedback subtype to set.
     */
    public void setFeedbackSubtype(String feedbackSubType)
    {
        setAttribute(SUBTYPE_ATTR_NAME, feedbackSubType);
    }

    /**
     * Returns RTCP feedback subtype attribute value if already set or <tt>null</tt> otherwise.
     *
     * @return RTCP feedback subtype attribute if already set or <tt>null</tt> otherwise.
     */
    public String getFeedbackSubtype()
    {
        return getAttributeAsString(SUBTYPE_ATTR_NAME);
    }
}
