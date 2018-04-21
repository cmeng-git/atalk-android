/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.DefaultPacketExtensionProvider;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parser for EndpointPacketExtension.
 *
 * @author Sebastien Vincent
 */
public class EndpointProvider extends ExtensionElementProvider<EndpointPacketExtension>
{
    /**
     * Parses a endpoint extension sub-packet and creates a {@link EndpointPacketExtension}
     * instance. At the beginning of the method call, the xml parser will be positioned on the
     * opening element of the packet extension. As required by the smack API, at the end of the
     * method call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Endpoint</tt> element.
     * @return a new {@link EndpointPacketExtension} instance.
     * @throws java.lang.Exception if an error occurs parsing the XML.
     */
    @Override
    public EndpointPacketExtension parse(XmlPullParser parser, int depth)
            throws XmlPullParserException, IOException, SmackException
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        String entity = parser.getAttributeValue("", EndpointPacketExtension.ENTITY_ATTR_NAME);
        StateType state = StateType.full;
        String stateStr = parser.getAttributeValue("", EndpointPacketExtension.STATE_ATTR_NAME);

        if (stateStr != null) {
            state = StateType.parseString(stateStr);
        }

        EndpointPacketExtension ext = new EndpointPacketExtension(entity);
        ext.setAttribute(EndpointPacketExtension.STATE_ATTR_NAME, state);

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            try {
                if (eventType == XmlPullParser.START_TAG) {
                    switch (elementName) {
                        case EndpointPacketExtension.ELEMENT_DISPLAY_TEXT:
                            ext.setDisplayText(CoinIQProvider.parseText(parser));
                            break;
                        case EndpointPacketExtension.ELEMENT_DISCONNECTION:
                            ext.setDisconnectionType(DisconnectionType.parseString(parser.getText()));
                            break;
                        case EndpointPacketExtension.ELEMENT_JOINING:
                            ext.setJoiningType(JoiningType.parseString(CoinIQProvider.parseText(parser)));
                            break;
                        case EndpointPacketExtension.ELEMENT_STATUS:
                            ext.setStatus(EndpointStatusType.parseString(CoinIQProvider.parseText(parser)));
                            break;
                        case CallInfoPacketExtension.ELEMENT_NAME: {
                            DefaultPacketExtensionProvider<CallInfoPacketExtension> provider
                                    = new DefaultPacketExtensionProvider<>(CallInfoPacketExtension.class);
                            ExtensionElement childExtension = provider.parse(parser);
                            ext.addChildExtension(childExtension);
                            break;
                        }
                        case MediaPacketExtension.ELEMENT_NAME: {
                            MediaProvider provider = new MediaProvider();
                            ExtensionElement childExtension = provider.parse(parser);
                            ext.addChildExtension(childExtension);
                            break;
                        }
                    }
                }
                else if (eventType == XmlPullParser.END_TAG) {
                    if (parser.getName().equals(EndpointPacketExtension.ELEMENT_NAME)) {
                        done = true;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ext;
    }
}
