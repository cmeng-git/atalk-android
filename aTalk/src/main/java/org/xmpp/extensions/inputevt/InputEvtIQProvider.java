/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.inputevt;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Implements an <tt>IQProvider</tt> which parses incoming <tt>InputEvtIQ</tt>s.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class InputEvtIQProvider extends IQProvider<InputEvtIQ>
{
    /**
     * Parse the Input IQ sub-document and returns the corresponding <tt>InputEvtIQ</tt>.
     *
     * @param parser XML parser
     * @return <tt>InputEvtIQ</tt>
     * @throws IOException, XmlPullParserException if something goes wrong during parsing
     */
    @Override
    public InputEvtIQ parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        InputEvtIQ inputEvtIQ = new InputEvtIQ();
        InputEvtAction action = InputEvtAction.fromString(parser.getAttributeValue("",
                InputEvtIQ.ACTION_ATTR_NAME));

        inputEvtIQ.setAction(action);

        boolean done = false;

        while (!done) {
            switch (parser.next()) {
                case START_ELEMENT:
                    // <remote-control>
                    if (RemoteControlExtensionProvider.ELEMENT_REMOTE_CONTROL.equals(parser.getName())) {
                        RemoteControlExtensionProvider provider = new RemoteControlExtensionProvider();
                        RemoteControlExtension item = (RemoteControlExtension) provider.parseExtension(parser);
                        inputEvtIQ.addRemoteControl(item);
                    }
                    break;

                case END_ELEMENT:
                    if (InputEvtIQ.ELEMENT.equals(parser.getName()))
                        done = true;
                    break;
            }
        }
        return inputEvtIQ;
    }
}
