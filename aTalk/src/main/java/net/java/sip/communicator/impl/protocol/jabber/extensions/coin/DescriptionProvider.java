/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parser for DescriptionExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class DescriptionProvider extends ExtensionElementProvider<DescriptionExtensionElement>
{
    /**
     * Parses a description extension sub-packet and creates a {@link DescriptionExtensionElement}
     * instance. At the beginning of the method call, the xml parser will be positioned on the
     * opening element of the packet extension. As required by the smack API, at the end of the
     * method call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>description</tt> element.
     * @return a new {@link DescriptionExtensionElement} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */

    @Override
    public DescriptionExtensionElement parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        DescriptionExtensionElement ext = new DescriptionExtensionElement();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                    switch (elementName) {
                        case DescriptionExtensionElement.ELEMENT_SUBJECT:
                            ext.setSubject(CoinIQProvider.parseText(parser));
                            break;
                        case DescriptionExtensionElement.ELEMENT_FREE_TEXT:
                            ext.setFreeText(CoinIQProvider.parseText(parser));
                            break;
                        case DescriptionExtensionElement.ELEMENT_DISPLAY_TEXT:
                            ext.setDisplayText(CoinIQProvider.parseText(parser));
                            break;
                    }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(DescriptionExtensionElement.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
