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
 * Parser for MediaExtensionElement.
 *
 * @author Sebastien Vincent
 */
public class MediaProvider extends ExtensionElementProvider<MediaExtension>
{
    /**
     * Parses a media extension sub-packet and creates a {@link MediaExtension} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening element of
     * the packet extension. As required by the smack API, at the end of the method call, the parser
     * will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Media</tt> element.
     * @return a new {@link MediaExtension} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    @Override
    public MediaExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        String id = parser.getAttributeValue("", MediaExtension.ID_ATTR_NAME);

        if (id == null) {
            throw new XmlPullParserException("Coin media must contains src-id element");
        }

        MediaExtension ext = new MediaExtension(id);
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                switch (elementName) {
                    case MediaExtension.ELEMENT_DISPLAY_TEXT:
                        ext.setDisplayText(CoinIQProvider.parseText(parser));
                        break;
                    case MediaExtension.ELEMENT_LABEL:
                        ext.setLabel(CoinIQProvider.parseText(parser));
                        break;
                    case MediaExtension.ELEMENT_SRC_ID:
                        ext.setSrcID(CoinIQProvider.parseText(parser));
                        break;
                    case MediaExtension.ELEMENT_STATUS:
                        ext.setStatus(CoinIQProvider.parseText(parser));
                        break;
                    case MediaExtension.ELEMENT_TYPE:
                        ext.setType(CoinIQProvider.parseText(parser));
                        break;
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(MediaExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
