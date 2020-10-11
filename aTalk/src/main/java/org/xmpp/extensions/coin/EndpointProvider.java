/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.xmpp.extensions.DefaultExtensionElementProvider;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;

import java.io.IOException;

/**
 * Parser for EndpointExtensionElement.
 *
 * @author Sebastien Vincent
 */
public class EndpointProvider extends ExtensionElementProvider<EndpointExtension>
{
    /**
     * Parses a endpoint extension sub-packet and creates a {@link EndpointExtension}
     * instance. At the beginning of the method call, the xml parser will be positioned on the
     * opening element of the packet extension. As required by the smack API, at the end of the
     * method call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Endpoint</tt> element.
     * @return a new {@link EndpointExtension} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public EndpointExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        String entity = parser.getAttributeValue("", EndpointExtension.ENTITY_ATTR_NAME);
        StateType state = StateType.full;
        String stateStr = parser.getAttributeValue("", EndpointExtension.STATE_ATTR_NAME);

        if (stateStr != null) {
            state = StateType.fromString(stateStr);
        }

        EndpointExtension ext = new EndpointExtension(entity);
        ext.setAttribute(EndpointExtension.STATE_ATTR_NAME, state);

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                switch (elementName) {
                    case EndpointExtension.ELEMENT_DISPLAY_TEXT:
                        ext.setDisplayText(CoinIQProvider.parseText(parser));
                        break;
                    case EndpointExtension.ELEMENT_DISCONNECTION:
                        ext.setDisconnectionType(DisconnectionType.fromString(parser.getText()));
                        break;
                    case EndpointExtension.ELEMENT_JOINING:
                        ext.setJoiningType(JoiningType.fromString(CoinIQProvider.parseText(parser)));
                        break;
                    case EndpointExtension.ELEMENT_STATUS:
                        ext.setStatus(EndpointStatusType.fromString(CoinIQProvider.parseText(parser)));
                        break;
                    case CallInfoExtension.ELEMENT: {
                        DefaultExtensionElementProvider<CallInfoExtension> provider
                                = new DefaultExtensionElementProvider<>(CallInfoExtension.class);
                        ExtensionElement childExtension = provider.parse(parser);
                        ext.addChildExtension(childExtension);
                        break;
                    }
                    case MediaExtension.ELEMENT: {
                        MediaProvider provider = new MediaProvider();
                        ExtensionElement childExtension = provider.parse(parser);
                        ext.addChildExtension(childExtension);
                        break;
                    }
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(EndpointExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
