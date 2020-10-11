/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Parser for UserLanguagesExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UserLanguagesProvider extends ExtensionElementProvider<ExtensionElement>
{
    /**
     * Parses a UserLanguages extension sub-packet and creates a
     * {@link UserLanguagesExtension} instance. At the beginning of the method call, the xml
     * parser will be positioned on the opening element of the packet extension. As required by the
     * smack API, at the end of the method call, the parser will be positioned on the closing
     * element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>UserLanguages</tt> element.
     * @return a new {@link UserLanguagesExtension} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    @Override
    public ExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;

        UserLanguagesExtension ext = new UserLanguagesExtension();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(UserLanguagesExtension.ELEMENT_LANGUAGES)) {
                    ext.setLanguages(CoinIQProvider.parseText(parser));
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(UserLanguagesExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
