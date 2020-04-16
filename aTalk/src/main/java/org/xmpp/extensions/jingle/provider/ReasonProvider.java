/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.xmpp.extensions.jingle.element.JingleReason;
import org.xmpp.extensions.jingle.element.Reason;

import java.io.IOException;

/**
 * The <tt>ReasonProvider</tt> parses "reason" elements into {@link JingleReason} instances.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class ReasonProvider extends ExtensionElementProvider<JingleReason>
{
    /**
     * Parses a reason extension sub-packet and creates a {@link JingleReason} instance. At
     * the beginning of the method call, the xml parser will be positioned on the opening element of
     * the packet extension. As required by the smack API, at the end of the method call, the parser
     * will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>reason</tt> element.
     * @return a new {@link JingleReason} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */

    @Override
    public JingleReason parse(XmlPullParser parser, int initDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        String text = null;
        Reason reason = null;

        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName;

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                // the reason itself.
                if (reason == null) {
                    // let the parse exception fly as it would mean we have some weird element first in the list.
                    reason = Reason.fromString(elementName);
                }
                else if (elementName.equals(JingleReason.TEXT_ELEMENT)) {
                        text = parseText(parser);
                }
                else {
                    // this is an element that we don't currently support.
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(JingleReason.ELEMENT)) {
                    done = true;
                }
            }
        }
        return new JingleReason(reason, text, null);
    }

    /**
     * Returns the content of the next {@link XmlPullParser.Event#TEXT_CHARACTERS} element that we encounter in <tt>parser</tt>.
     *
     * @param parser the parse that we'll be probing for text.
     * @return the content of the next {@link XmlPullParser.Event#TEXT_CHARACTERS} element we come across or
     * <tt>null</tt> if we encounter a closing tag first.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    public String parseText(XmlPullParser parser)
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
