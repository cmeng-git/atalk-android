/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingleinfo;

import org.jivesoftware.smack.packet.IQ;

/**
 * The <tt>JingleInfoQueryIQ</tt> is used to discover STUN and relay server via the Google's Jingle
 * Server Discovery extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class JingleInfoQueryIQ extends IQ
{
    /**
     * The element name.
     */
    public static final String ELEMENT = "query";

    /**
     * The namespace.
     */
    public static final String NAMESPACE = "google:jingleinfo";

    public JingleInfoQueryIQ()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the sub-element XML section of the IQ packet, or null if there isn't one. Packet
     * extensions must be included, if any are defined.
     *
     * @return the child element section of the IQ XML.
     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.setEmptyElement();
        return xml;
    }
}
