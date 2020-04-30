package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;
import java.util.IllegalFormatException;

public class JingleNodesProvider extends IQProvider<JingleChannelIQ>
{
    @Override
    public JingleChannelIQ parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        JingleChannelIQ iq = null;
        XmlPullParser.Event eventType;
        String elementName;
        String namespace;

        while (!done) {
            eventType = parser.getEventType();
            elementName = parser.getName();
            namespace = parser.getNamespace();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(JingleChannelIQ.ELEMENT)
                        && namespace.equals(JingleChannelIQ.NAMESPACE)) {

                    final String protocol = parser.getAttributeValue(null, "protocol");
                    final String porta = parser.getAttributeValue(null, "localport");
                    final String portb = parser.getAttributeValue(null, "remoteport");
                    final String host = parser.getAttributeValue(null, "host");

                    try {
                        iq = new JingleChannelIQ();
                        iq.setProtocol(protocol == null ? JingleChannelIQ.UDP : protocol);
                        if (host != null)
                            iq.setHost(host);
                        if (porta != null)
                            iq.setLocalport(Integer.valueOf(porta));
                        if (portb != null)
                            iq.setRemoteport(Integer.valueOf(portb));
                    } catch (final IllegalFormatException | NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                done = true;
            }
            if (!done)
                parser.next();
        }
        return iq;
    }
}
