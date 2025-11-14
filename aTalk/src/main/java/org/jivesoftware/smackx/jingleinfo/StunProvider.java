/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.jingleinfo;

import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.JxmppContext;

import java.io.IOException;
import java.text.ParseException;

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
     * @param parser an XML parser positioned at the opening <code>Server</code> element.
     * @return a new {@link StunExtension} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public StunExtension parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment, JxmppContext jxmppContext)
            throws XmlPullParserException, IOException, SmackParsingException, ParseException {
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName = null;
        StunExtension ext = new StunExtension();

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(ServerExtension.ELEMENT)) {
                    ExtensionElementProvider<?> provider = ProviderManager.getExtensionProvider(
                            ServerExtension.ELEMENT, ServerExtension.NAMESPACE);
                    XmlElement childExtension = provider.parse(parser);
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
