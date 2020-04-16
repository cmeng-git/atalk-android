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

/**
 * Parser for StunExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class StunProvider extends ExtensionElementProvider<StunExtension>
{
    /**
     * Parses a users extension sub-packet and creates a {@link StunExtension} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening
     * element of the packet extension. As required by the smack API, at the end of the method
     * call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Server</tt> element.
     * @return a new {@link StunExtension} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public StunExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        StunExtension ext = new StunExtension();

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
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(StunExtension.ELEMENT)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
