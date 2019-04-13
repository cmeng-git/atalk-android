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
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;

/**
 * Parser for RelayExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RelayProvider extends ExtensionElementProvider<RelayExtensionElement>
{
    /**
     * Parses a users extension sub-packet and creates a {@link StunExtensionElement} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening
     * element of the packet extension. As required by the smack API, at the end of the method
     * call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Server</tt> element.
     * @return a new {@link RelayExtensionElement} instance.
     * @throws ParseException, XmlPullParserException, IOException if an error occurs parsing the XML.
     */

    @Override
    public RelayExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        RelayExtensionElement ext = new RelayExtensionElement();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals(ServerExtensionElement.ELEMENT_NAME)) {
                    ExtensionElementProvider provider = ProviderManager.getExtensionProvider(
                            ServerExtensionElement.ELEMENT_NAME, ServerExtensionElement.NAMESPACE);
                    ExtensionElement childExtension = (ExtensionElement) provider.parse(parser);
                    ext.addChildExtension(childExtension);
                }
                else if (elementName.equals("token")) {
                    ext.setToken(parseText(parser));
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(RelayExtensionElement.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }

    /**
     * Returns the content of the next {@link XmlPullParser#TEXT} element that we encounter in
     * <tt>parser</tt>.
     *
     * @param parser the parse that we'll be probing for text.
     * @return the content of the next {@link XmlPullParser#TEXT} element we come across or
     * <tt>null</tt> if we encounter a closing tag first.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    public static String parseText(XmlPullParser parser)
            throws IOException, XmlPullParserException
    {
        boolean done = false;
        int eventType;
        String text = null;

        while (!done) {
            eventType = parser.next();
            if (eventType == XmlPullParser.TEXT) {
                text = parser.getText();
            }
            else if (eventType == XmlPullParser.END_TAG) {
                done = true;
            }
        }
        return text;
    }
}
