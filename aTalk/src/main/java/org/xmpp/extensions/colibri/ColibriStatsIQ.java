/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.colibri;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.XmlEnvironment;

/**
 * The stats IQ that can be used to request Colibri stats on demand (used in server side focus).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ColibriStatsIQ extends IQ
{
    /**
     * The XML element name of the Jitsi Videobridge <tt>stats</tt> extension.
     */
    public static final String ELEMENT_NAME = ColibriStatsExtensionElement.ELEMENT_NAME;

    /**
     * The XML COnferencing with LIghtweight BRIdging namespace of the Jitsi Videobridge
     * <tt>stats</tt> extension.
     */
    public static final String NAMESPACE = ColibriStatsExtensionElement.NAMESPACE;

    private final ColibriStatsExtensionElement backEnd = new ColibriStatsExtensionElement();

    public ColibriStatsIQ()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Adds stat extension.
     *
     * @param stat the stat to be added
     */
    public void addStat(ColibriStatsExtensionElement.Stat stat)
    {
        backEnd.addStat(stat);
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.append('>');
        xml.append(backEnd.toXML(XmlEnvironment.EMPTY));
        return xml;
    }
}
