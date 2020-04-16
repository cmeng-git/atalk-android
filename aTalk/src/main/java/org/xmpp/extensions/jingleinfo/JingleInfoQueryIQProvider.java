/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingleinfo;

import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.xmpp.extensions.DefaultExtensionElementProvider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.*;

import java.io.IOException;

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
        ProviderManager.addExtensionProvider(ServerExtension.ELEMENT, ServerExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(ServerExtension.class));
    }

    /**
     * Parses a JingleInfoQueryIQ</tt>.
     *
     * @param parser an XML parser.
     * @return a new {@link JingleInfoQueryIQ} instance.
     * @throws XmlPullParserException, IOException, SmackException if an error occurs parsing the XML.
     */
    @Override
    public JingleInfoQueryIQ parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        boolean done = false;
        JingleInfoQueryIQ iq = new JingleInfoQueryIQ();

        // Now go on and parse the session element's content.
        while (!done) {
            XmlPullParser.Event eventType = parser.next();
            String elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(StunExtension.ELEMENT)) {
                    iq.addExtension((StunExtension) stunProvider.parse(parser));
                }
                else if (elementName.equals(RelayExtension.ELEMENT)) {
                    iq.addExtension((RelayExtension) relayProvider.parse(parser));
                }
            }
            if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(JingleInfoQueryIQ.ELEMENT)) {
                    done = true;
                }
            }
        }
        return iq;
    }
}
