/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jibri;

import org.atalk.util.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parses {@link JibriIq}.
 */
public class JibriIqProvider extends IQProvider<JibriIq> {
	/**
	 * {@inheritDoc}
	 */
	@Override
	public JibriIq parse(XmlPullParser parser, int initialDepth)
			throws XmlPullParserException, IOException, SmackException {
		String namespace = parser.getNamespace();

		// Check the namespace
		if (!JibriIq.NAMESPACE.equals(namespace)) {
			return null;
		}

		String rootElement = parser.getName();
		JibriIq iq;

		if (JibriIq.ELEMENT_NAME.equals(rootElement)) {
			iq = new JibriIq();

			String action = parser.getAttributeValue("", JibriIq.ACTION_ATTR_NAME);
			iq.setAction(JibriIq.Action.parse(action));

			String status = parser.getAttributeValue("", JibriIq.STATUS_ATTR_NAME);
			iq.setStatus(JibriIq.Status.parse(status));

            String recordingMode = parser.getAttributeValue( "", JibriIq.RECORDING_MODE_ATTR_NAME);
            if (!StringUtils.isNullOrEmpty(recordingMode))
                iq.setRecordingMode(JibriIq.RecordingMode.parse(recordingMode));
						
			String room = parser.getAttributeValue("", JibriIq.ROOM_ATTR_NAME);
			if (!StringUtils.isNullOrEmpty(room)) {
                EntityBareJid roomJid = JidCreate.entityBareFrom(room);
                iq.setRoom(roomJid);
            }

			String streamId = parser.getAttributeValue("", JibriIq.STREAM_ID_ATTR_NAME);
			if (!StringUtils.isNullOrEmpty(streamId))
				iq.setStreamId(streamId);

            String displayName = parser.getAttributeValue("", JibriIq.DISPLAY_NAME_ATTR_NAME);
            if (!StringUtils.isNullOrEmpty(displayName))
                iq.setDisplayName(displayName);

            String sipAddress = parser.getAttributeValue("", JibriIq.SIP_ADDRESS_ATTR_NAME);
            if (!StringUtils.isNullOrEmpty(sipAddress))
                iq.setSipAddress(sipAddress);
		}
		else {
			return null;
		}

		boolean done = false;

		while (!done) {
			switch (parser.next()) {
				case XmlPullParser.START_TAG: {
					String name = parser.getName();
					if ("error".equals(name)) {
						try {
							XMPPError.Builder error = PacketParserUtils.parseError(parser);
							iq.setXMPPError(error.build());
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}
					break;
				}
				case XmlPullParser.END_TAG: {
					String name = parser.getName();

					if (rootElement.equals(name)) {
						done = true;
					}
					break;
				}
			}
		}
		return iq;
	}
}
