package org.jivesoftware.smackx.jinglenodes.provider;

import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_EXPIRE;
import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_HOST;
import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_ID;
import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_LOCALPORT;
import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_MAXKBPS;
import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_PROTOCOL;
import static org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ.ATTR_REMOTEPORT;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.jinglenodes.element.JingleChannelIQ;

import java.io.IOException;
import java.util.IllegalFormatException;

public class JingleChannelProvider extends IQProvider<JingleChannelIQ>
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

                    final String id = parser.getAttributeValue(null, ATTR_ID);
                    final String host = parser.getAttributeValue(null, ATTR_HOST);
                    final String portLocal = parser.getAttributeValue(null, ATTR_LOCALPORT);
                    final String portRemote = parser.getAttributeValue(null, ATTR_REMOTEPORT);
                    final String protocol = parser.getAttributeValue(null, ATTR_PROTOCOL);
                    final String maxKbps = parser.getAttributeValue(null, ATTR_MAXKBPS);
                    final String expire = parser.getAttributeValue(null, ATTR_EXPIRE);

                    try {
                        iq = new JingleChannelIQ();
                        iq.setProtocol(protocol == null ? JingleChannelIQ.UDP : protocol);
                        if (id != null)
                            iq.setChannelId(id);
                        if (host != null)
                            iq.setHost(host);
                        if (portLocal != null)
                            iq.setLocalport(Integer.parseInt(portLocal));
                        if (portRemote != null)
                            iq.setRemoteport(Integer.parseInt(portRemote));
                        if (maxKbps != null)
                            iq.setMaxKbps(Integer.parseInt(maxKbps));
                        if (expire != null)
                            iq.setExpire(Integer.parseInt(expire));
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
