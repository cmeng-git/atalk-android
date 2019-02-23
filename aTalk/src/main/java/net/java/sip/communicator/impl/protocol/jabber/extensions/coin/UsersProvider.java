/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;

/**
 * Parser for UsersExtensionElement.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UsersProvider extends ExtensionElementProvider<UsersExtensionElement>
{
    /**
     * Parses a users extension sub-packet and creates a {@link UsersExtensionElement} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening element of
     * the packet extension. As required by the smack API, at the end of the method call, the parser
     * will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Users</tt> element.
     * @return a new {@link UsersExtensionElement} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public UsersExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        StateType state = StateType.full;
        String stateStr = parser.getAttributeValue("", UserExtensionElement.STATE_ATTR_NAME);

        if (stateStr != null) {
            state = StateType.parseString(stateStr);
        }

        UsersExtensionElement ext = new UsersExtensionElement();
        ext.setAttribute(UsersExtensionElement.STATE_ATTR_NAME, state);
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals(UserExtensionElement.ELEMENT_NAME)) {
                    UserProvider provider = new UserProvider();
                    ExtensionElement childExtension = provider.parse(parser);
                    ext.addChildExtension(childExtension);
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(UsersExtensionElement.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
