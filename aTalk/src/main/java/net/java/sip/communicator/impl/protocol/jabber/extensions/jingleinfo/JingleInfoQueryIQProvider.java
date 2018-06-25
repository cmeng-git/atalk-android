/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingleinfo;

import net.java.sip.communicator.impl.protocol.jabber.extensions.DefaultPacketExtensionProvider;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Provider for the <tt>JingleInfoQueryIQ</tt>.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class JingleInfoQueryIQProvider extends IQProvider<JingleInfoQueryIQ>
{
    /**
     * STUN packet extension provider.
     */
    private final ExtensionElementProvider stunProvider = new StunProvider();

    /**
     * Relay packet extension provider.
     */
    private final ExtensionElementProvider relayProvider = new RelayProvider();

    /**
     * Creates a new instance of the <tt>JingleInfoQueryIQProvider</tt> and register all related
     * extension providers. It is the responsibility of the application to register the
     * <tt>JingleInfoQueryIQProvider</tt> itself.
     */
    public JingleInfoQueryIQProvider()
    {
        ProviderManager.addExtensionProvider(ServerPacketExtension.ELEMENT_NAME, ServerPacketExtension.NAMESPACE,
                new DefaultPacketExtensionProvider<>(ServerPacketExtension.class));
    }

    /**
     * Parses a JingleInfoQueryIQ</tt>.
     *
     * @param parser an XML parser.
     * @return a new {@link JingleInfoQueryIQ} instance.
     * @throws XmlPullParserException, IOException, SmackException if an error occurs parsing the XML.
     */
    @Override
    public JingleInfoQueryIQ parse(XmlPullParser parser, int depth)
            throws Exception
    {
        boolean done = false;
        JingleInfoQueryIQ iq = new JingleInfoQueryIQ();

        // Now go on and parse the session element's content.
        while (!done) {
            int eventType = parser.next();
            String elementName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals(StunPacketExtension.ELEMENT_NAME)) {
                    iq.addExtension((StunPacketExtension) stunProvider.parse(parser));
                }
                else if (elementName.equals(RelayPacketExtension.ELEMENT_NAME)) {
                    iq.addExtension((RelayPacketExtension) relayProvider.parse(parser));
                }
            }
            if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(JingleInfoQueryIQ.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return iq;
    }
}
