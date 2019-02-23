/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingleinfo;

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
 * Parser for StunExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class StunProvider extends ExtensionElementProvider<StunExtensionElement>
{
    /**
     * Parses a users extension sub-packet and creates a {@link StunExtensionElement} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening
     * element of the packet extension. As required by the smack API, at the end of the method
     * call, the parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Server</tt> element.
     * @return a new {@link StunExtensionElement} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public StunExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        StunExtensionElement ext = new StunExtensionElement();

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
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(StunExtensionElement.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
