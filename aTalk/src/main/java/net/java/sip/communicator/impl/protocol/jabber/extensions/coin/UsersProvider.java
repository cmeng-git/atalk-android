/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * Parser for UsersPacketExtension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UsersProvider extends ExtensionElementProvider<UsersPacketExtension>
{
    /**
     * Parses a users extension sub-packet and creates a {@link UsersPacketExtension} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening element of
     * the packet extension. As required by the smack API, at the end of the method call, the parser
     * will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>Users</tt> element.
     * @return a new {@link UsersPacketExtension} instance.
     * @throws java.lang.Exception if an error occurs parsing the XML.
     */
    @Override
    public UsersPacketExtension parse(XmlPullParser parser, int depth)
            throws Exception
    {
        boolean done = false;
        int eventType;
        String elementName = null;
        StateType state = StateType.full;
        String stateStr = parser.getAttributeValue("", UserPacketExtension.STATE_ATTR_NAME);

        if (stateStr != null) {
            state = StateType.parseString(stateStr);
        }

        UsersPacketExtension ext = new UsersPacketExtension();
        ext.setAttribute(UsersPacketExtension.STATE_ATTR_NAME, state);

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals(UserPacketExtension.ELEMENT_NAME)) {
                    UserProvider provider = new UserProvider();
                    ExtensionElement childExtension = provider.parse(parser);
                    ext.addChildExtension(childExtension);
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(UsersPacketExtension.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }
        return ext;
    }
}
