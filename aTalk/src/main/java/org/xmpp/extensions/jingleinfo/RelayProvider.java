/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingleinfo;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;

/**
 * Parser for RelayExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RelayProvider extends ExtensionElementProvider<RelayExtension>
{
    /**
     * Parses a users extension sub-packet and creates a {@link StunExtension} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening
     * element of the packet extension. As required by the smack API, at the end of the method
     * call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Server</tt> element.
     * @return a new {@link RelayExtension} instance.
     * @throws ParseException, XmlPullParserException, IOException if an error occurs parsing the XML.
     */

    @Override
    public RelayExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        RelayExtension ext = new RelayExtension();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(ServerExtension.ELEMENT)) {
                    ExtensionElementProvider provider = ProviderManager.getExtensionProvider(
                            ServerExtension.ELEMENT, ServerExtension.NAMESPACE);
                    ExtensionElement childExtension = (ExtensionElement) provider.parse(parser);
                    ext.addChildExtension(childExtension);
                }
                else if (elementName.equals("token")) {
                    ext.setToken(parseText(parser));
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(RelayExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }

    /**
     * Returns the content of the next {@link XmlPullParser.Event#TEXT_CHARACTERS} element that we encounter in
     * <tt>parser</tt>.
     *
     * @param parser the parse that we'll be probing for text.
     * @return the content of the next {@link XmlPullParser.Event#TEXT_CHARACTERS} element we come across or
     * <tt>null</tt> if we encounter a closing tag first.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    public static String parseText(XmlPullParser parser)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String text = null;

        while (!done) {
            eventType = parser.next();
            if (eventType == XmlPullParser.Event.TEXT_CHARACTERS) {
                text = parser.getText();
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                done = true;
            }
        }
        return text;
    }
}
