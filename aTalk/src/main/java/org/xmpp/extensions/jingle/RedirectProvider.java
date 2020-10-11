/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * The <tt>RedirectProvider</tt> parses "redirect" elements into {@link RedirectExtension} instances.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RedirectProvider extends ExtensionElementProvider<RedirectExtension>
{
    /**
     * Parses a reason extension sub-packet and creates a {@link RedirectExtension} instance.
     * At the beginning of the method call, the xml parser will be positioned on the opening element
     * of the packet extension. As required by the smack API, at the end of the method call, the
     * parser will be positioned on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the opening <tt>redirect</tt> element.
     * @return a new {@link RedirectExtension} instance.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    @Override
    public RedirectExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        String text = null;
        boolean done = false;
        XmlPullParser.Event eventType;

        text = parseText(parser);
        while (!done) {
            eventType = parser.next();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(RedirectExtension.ELEMENT)) {
                    done = true;
                }
            }
        }

        RedirectExtension redirectExt = new RedirectExtension();
        redirectExt.setText(text);
        redirectExt.setRedir(text);
        return redirectExt;
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
                done = true;
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                done = true;
            }
        }

        return text;
    }
}
