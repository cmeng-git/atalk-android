/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

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
     * {@link UserLanguagesExtensionElement} instance. At the beginning of the method call, the xml
     * parser will be positioned on the opening element of the packet extension. As required by the
     * smack API, at the end of the method call, the parser will be positioned on the closing
     * element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>UserLanguages</tt> element.
     * @return a new {@link UserLanguagesExtensionElement} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    @Override
    public ExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        int eventType;
        String elementName = null;

        UserLanguagesExtensionElement ext = new UserLanguagesExtensionElement();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals(UserLanguagesExtensionElement.ELEMENT_LANGUAGES)) {
                    ext.setLanguages(CoinIQProvider.parseText(parser));
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(UserLanguagesExtensionElement.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
