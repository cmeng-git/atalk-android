/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Parser for UserExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserProvider extends ExtensionElementProvider<UserExtension>
{
    /**
     * Parses a User extension sub-packet and creates a {@link UserExtension} instance. At the
     * beginning of the method call, the xml parser will be positioned on the opening element of the
     * packet extension. As required by the smack API, at the end of the method call, the parser
     * will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>User</tt> element.
     * @return a new {@link UserExtension} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public UserExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        String entity = parser.getAttributeValue("", UserExtension.ENTITY_ATTR_NAME);
        StateType state = StateType.full;
        String stateStr = parser.getAttributeValue("", UserExtension.STATE_ATTR_NAME);

        if (stateStr != null) {
            state = StateType.fromString(stateStr);
        }

        if (entity == null) {
            throw new XmlPullParserException("Coin user element must contain entity attribute");
        }

        UserExtension ext = new UserExtension(entity);
        ext.setAttribute(UserExtension.STATE_ATTR_NAME, state);
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(UserExtension.ELEMENT_DISPLAY_TEXT)) {
                    try {
                        ext.setDisplayText(CoinIQProvider.parseText(parser));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else if (elementName.equals(EndpointExtension.ELEMENT)) {
                    EndpointProvider provider = new EndpointProvider();
                    ExtensionElement childExtension = provider.parse(parser);
                    ext.addChildExtension(childExtension);
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(UserExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
