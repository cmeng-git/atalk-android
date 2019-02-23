/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

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

        if (parser.getName().equals(SctpMapExtension.ELEMENT_NAME)
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
