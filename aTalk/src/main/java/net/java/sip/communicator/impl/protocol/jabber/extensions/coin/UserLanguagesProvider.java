/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import java.io.IOException;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Parser for UserLanguagesPacketExtension.
 *
 * @author Sebastien Vincent
 */
public class UserLanguagesProvider extends ExtensionElementProvider<ExtensionElement>
{
	/**
	 * Parses a UserLanguages extension sub-packet and creates a
	 * {@link UserLanguagesPacketExtension} instance. At the beginning of the method call, the xml
	 * parser will be positioned on the opening element of the packet extension. As required by the
	 * smack API, at the end of the method call, the parser will be positioned on the closing
	 * element of the packet extension.
	 *
	 * @param parser
	 *        an XML parser positioned at the opening <tt>UserLanguages</tt> element.
	 *
	 * @return a new {@link UserLanguagesPacketExtension} instance.
	 * @throws java.lang.Exception
	 *         if an error occurs parsing the XML.
	 */
	public ExtensionElement parseExtension(XmlPullParser parser)
		throws Exception
	{
		boolean done = false;
		int eventType;
		String elementName = null;

		UserLanguagesPacketExtension ext = new UserLanguagesPacketExtension();

		while (!done) {
			eventType = parser.next();
			elementName = parser.getName();

			if (eventType == XmlPullParser.START_TAG) {
				if (elementName.equals(UserLanguagesPacketExtension.ELEMENT_LANGUAGES)) {
					ext.setLanguages(CoinIQProvider.parseText(parser));
				}
			}
			else if (eventType == XmlPullParser.END_TAG) {
				if (parser.getName().equals(UserLanguagesPacketExtension.ELEMENT_NAME)) {
					done = true;
				}
			}
		}

		return ext;
	}

	@Override
	public ExtensionElement parse(XmlPullParser paramXmlPullParser, int paramInt)
		throws XmlPullParserException, IOException, SmackException
	{
		// TODO Auto-generated method stub
		return null;
	}
}
