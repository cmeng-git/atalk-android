package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Element;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.*;

import java.io.IOException;
import java.util.IllegalFormatException;

public class JingleNodesProvider extends IQProvider
{
	@Override
	public Element parse(XmlPullParser parser, int depth)
			throws XmlPullParserException, IOException, SmackException
	{
		JingleChannelIQ iq = null;

		boolean done = false;
		int eventType;
		String elementName;
		String namespace;

		while (!done) {
			eventType = parser.getEventType();
			elementName = parser.getName();
			namespace = parser.getNamespace();

			if (eventType == XmlPullParser.START_TAG) {
				if (elementName.equals(JingleChannelIQ.NAME)
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
					}
					catch (final IllegalFormatException | NumberFormatException e) {
						e.printStackTrace();
					}
				}
			}
			else if (eventType == XmlPullParser.END_TAG) {
				done = true;
			}
			if (!done)
				parser.next();
		}
		return iq;
	}
}
