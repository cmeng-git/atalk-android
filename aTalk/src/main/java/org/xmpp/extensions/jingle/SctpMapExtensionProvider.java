/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * The <tt>SctpMapExtensionProvider</tt> parses "sctpmap" elements into <tt>SctpMapExtension</tt>
 * instances.
 *
 * @author lishunyang
 * @author Eng Chong Meng
 * @see SctpMapExtension
 */
public class SctpMapExtensionProvider extends ExtensionElementProvider<SctpMapExtension>
{
    /**
     * {@inheritDoc}
     */
    @Override
    public SctpMapExtension parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException
    {
        SctpMapExtension result = new SctpMapExtension();

        if (parser.getName().equals(SctpMapExtension.ELEMENT)
                && parser.getNamespace().equals(SctpMapExtension.NAMESPACE)) {
            result.setPort(Integer.parseInt(parser.getAttributeValue(null,
                    SctpMapExtension.PORT_ATTR_NAME)));
            result.setProtocol(parser.getAttributeValue(null, SctpMapExtension.PROTOCOL_ATTR_NAME));
            result.setStreams(Integer.parseInt(parser.getAttributeValue(null,
                    SctpMapExtension.STREAMS_ATTR_NAME)));
        }
        return result;
    }
}
