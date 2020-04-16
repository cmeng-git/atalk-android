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
 * Parser for DescriptionExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class DescriptionProvider extends ExtensionElementProvider<DescriptionExtension>
{
    /**
     * Parses a description extension sub-packet and creates a {@link DescriptionExtension}
     * instance. At the beginning of the method call, the xml parser will be positioned on the
     * opening element of the packet extension. As required by the smack API, at the end of the
     * method call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>description</tt> element.
     * @return a new {@link DescriptionExtension} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */

    @Override
    public DescriptionExtension parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        DescriptionExtension ext = new DescriptionExtension();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                    switch (elementName) {
                        case DescriptionExtension.ELEMENT_SUBJECT:
                            ext.setSubject(CoinIQProvider.parseText(parser));
                            break;
                        case DescriptionExtension.ELEMENT_FREE_TEXT:
                            ext.setFreeText(CoinIQProvider.parseText(parser));
                            break;
                        case DescriptionExtension.ELEMENT_DISPLAY_TEXT:
                            ext.setDisplayText(CoinIQProvider.parseText(parser));
                            break;
                    }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(DescriptionExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
