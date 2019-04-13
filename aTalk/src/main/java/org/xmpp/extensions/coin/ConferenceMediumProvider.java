/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parser for ConferenceMediumProvider.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ConferenceMediumProvider extends ExtensionElementProvider<ConferenceMediumExtensionElement>
{
    /**
     * Parses a ConferenceMedium extension sub-packet and creates a
     * {@link ConferenceMediumExtensionElement} instance. At the beginning of the method call, the
     * xml parser will be positioned on the opening element of the packet extension. As required by
     * the smack API, at the end of the method call, the parser will be positioned on the closing
     * element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>ConferenceMedium</tt> element.
     * @return a new {@link ConferenceMediumExtensionElement} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    @Override
    public ConferenceMediumExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        String label = parser.getAttributeValue("", ConferenceMediumExtensionElement.LABEL_ATTR_NAME);

        if (label == null) {
            throw new XmlPullParserException("Coin medium element must contain entity attribute");
        }

        ConferenceMediumExtensionElement ext = new ConferenceMediumExtensionElement("entry", label);
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                switch (elementName) {
                    case MediaExtensionElement.ELEMENT_DISPLAY_TEXT:
                        ext.setDisplayText(CoinIQProvider.parseText(parser));
                        break;
                    case MediaExtensionElement.ELEMENT_STATUS:
                        ext.setStatus(CoinIQProvider.parseText(parser));
                        break;
                    case MediaExtensionElement.ELEMENT_TYPE:
                        ext.setType(CoinIQProvider.parseText(parser));
                        break;
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(ConferenceMediumExtensionElement.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
