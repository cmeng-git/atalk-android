/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.inputevt;

import java.io.IOException;
import java.text.ParseException;

import org.jivesoftware.smack.packet.IqData;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IqProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.JxmppContext;

/**
 * Implements an <code>IqProvider</code> which parses incoming <code>InputEvtIQ</code>s.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class InputEvtIQProvider extends IqProvider<InputEvtIQ> {
    /**
     * Parse the Input IQ sub-document and returns the corresponding <code>InputEvtIQ</code>.
     *
     * @param parser XML parser
     *
     * @return <code>InputEvtIQ</code>
     *
     * @throws IOException, XmlPullParserException if something goes wrong during parsing
     */
    @Override
    public InputEvtIQ parse(XmlPullParser parser, int initialDepth, IqData iqData, XmlEnvironment xmlEnvironment, JxmppContext jxmppContext)
            throws XmlPullParserException, IOException, SmackParsingException, ParseException {
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
