/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.inputevt;

import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;

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
     * @throws Exception if something goes wrong during parsing
     */
    @Override
    public InputEvtIQ parse(XmlPullParser parser, int depth)
            throws Exception
    {
        InputEvtIQ inputEvtIQ = new InputEvtIQ();
        InputEvtAction action = InputEvtAction.parseString(parser.getAttributeValue("",
                InputEvtIQ.ACTION_ATTR_NAME));

        inputEvtIQ.setAction(action);

        boolean done = false;

        while (!done) {
            switch (parser.next()) {
                case XmlPullParser.START_TAG:
                    // <remote-control>
                    if (RemoteControlExtensionProvider.ELEMENT_REMOTE_CONTROL.equals(parser.getName())) {
                        RemoteControlExtensionProvider provider = new RemoteControlExtensionProvider();
                        RemoteControlExtension item = (RemoteControlExtension) provider.parseExtension(parser);
                        inputEvtIQ.addRemoteControl(item);
                    }
                    break;

                case XmlPullParser.END_TAG:
                    if (InputEvtIQ.ELEMENT_NAME.equals(parser.getName()))
                        done = true;
                    break;
            }
        }
        return inputEvtIQ;
    }
}
