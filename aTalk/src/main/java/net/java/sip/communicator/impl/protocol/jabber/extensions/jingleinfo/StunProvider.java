/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingleinfo;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.*;
import org.xmlpull.v1.XmlPullParser;

/**
 * Parser for StunPacketExtension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class StunProvider extends ExtensionElementProvider<StunPacketExtension>
{
	/**
	 * Parses a users extension sub-packet and creates a {@link StunPacketExtension} instance. At
	 * the beginning of the method call, the xml parser will be positioned on the opening
	 * element of the packet extension. As required by the smack API, at the end of the method
	 * call, the parser will be positioned on the closing element of the packet extension.
	 *
	 * @param parser
	 * 		an XML parser positioned at the opening <tt>Server</tt> element.
	 * @return a new {@link StunPacketExtension} instance.
	 * @throws java.lang.Exception
	 * 		if an error occurs parsing the XML.
	 */
	@Override
	public StunPacketExtension parse(XmlPullParser parser, int depth)
			throws Exception
	{
		boolean done = false;
		int eventType;
		String elementName = null;
		StunPacketExtension ext = new StunPacketExtension();

		while (!done) {
			eventType = parser.next();
			elementName = parser.getName();

			if (eventType == XmlPullParser.START_TAG) {
				if (elementName.equals(ServerPacketExtension.ELEMENT_NAME)) {
					ExtensionElementProvider<ExtensionElement> provider
							= ProviderManager.getExtensionProvider(
									ServerPacketExtension.ELEMENT_NAME,
									ServerPacketExtension.NAMESPACE);
					ExtensionElement childExtension = provider.parse(parser);
					ext.addChildExtension(childExtension);
				}
			}
			else if (eventType == XmlPullParser.END_TAG) {
				if (parser.getName().equals(StunPacketExtension.ELEMENT_NAME)) {
					done = true;
				}
			}
		}
		return ext;
	}
}
