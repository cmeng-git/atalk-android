/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Parser for StateExtensionElement.
 *
 * @author Sebastien Vincent
 */
public class StateProvider extends ExtensionElementProvider<StateExtension>
{
    /**
     * Parses a state extension sub-packet and creates a {@link StateExtension} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening element of
     * the packet extension. As required by the smack API, at the end of the method call, the parser
     * will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>State</tt> element.
     * @return a new {@link StateExtension} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    @Override
    public StateExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;

        StateExtension ext = new StateExtension();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(StateExtension.ELEMENT_ACTIVE)) {
                    ext.setActive(Boolean.parseBoolean(CoinIQProvider.parseText(parser)) ? 1 : 0);
                }
                else if (elementName.equals(StateExtension.ELEMENT_LOCKED)) {
                    ext.setLocked(Boolean.parseBoolean(CoinIQProvider.parseText(parser)) ? 1 : 0);
                }
                if (elementName.equals(StateExtension.ELEMENT_USER_COUNT)) {
                    ext.setUserCount(Integer.parseInt(CoinIQProvider.parseText(parser)));
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(StateExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
