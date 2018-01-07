/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.coin;

import net.java.sip.communicator.impl.protocol.jabber.extensions.DefaultPacketExtensionProvider;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * An implementation of a Coin IQ provider that parses incoming Coin IQs.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class CoinIQProvider extends IQProvider<CoinIQ>
{
	/**
	 * Provider for description packet extension.
	 */
	private final DescriptionProvider descriptionProvider = new DescriptionProvider();

	/**
	 * Provider for users packet extension.
	 */
	private final UsersProvider usersProvider = new UsersProvider();

	/**
	 * Provider for state packet extension.
	 */
	private final StateProvider stateProvider = new StateProvider();

	/**
	 * Provider for URIs packet extension.
	 */
	private final DefaultPacketExtensionProvider<URIsPacketExtension> urisProvider = new DefaultPacketExtensionProvider<>(
		URIsPacketExtension.class);

	/**
	 * Provider for sidebars by val packet extension.
	 */
	private final DefaultPacketExtensionProvider<SidebarsByValPacketExtension> sidebarsByValProvider = new DefaultPacketExtensionProvider<>(
		SidebarsByValPacketExtension.class);

	/**
	 * Constructor.
	 */
	public CoinIQProvider()
	{
		ProviderManager.addExtensionProvider(UserRolesPacketExtension.ELEMENT_NAME,
			UserRolesPacketExtension.NAMESPACE,
			new DefaultPacketExtensionProvider<>(UserRolesPacketExtension.class));

		ProviderManager.addExtensionProvider(URIPacketExtension.ELEMENT_NAME,
			URIPacketExtension.NAMESPACE, new DefaultPacketExtensionProvider<>(
				URIPacketExtension.class));

		ProviderManager.addExtensionProvider(SIPDialogIDPacketExtension.ELEMENT_NAME,
			SIPDialogIDPacketExtension.NAMESPACE,
			new DefaultPacketExtensionProvider<>(SIPDialogIDPacketExtension.class));

		ProviderManager.addExtensionProvider(ConferenceMediumPacketExtension.ELEMENT_NAME,
			ConferenceMediumPacketExtension.NAMESPACE, new ConferenceMediumProvider());

		ProviderManager.addExtensionProvider(ConferenceMediaPacketExtension.ELEMENT_NAME,
			ConferenceMediaPacketExtension.NAMESPACE,
			new DefaultPacketExtensionProvider<>(ConferenceMediaPacketExtension.class));

		ProviderManager.addExtensionProvider(CallInfoPacketExtension.ELEMENT_NAME,
			CallInfoPacketExtension.NAMESPACE,
			new DefaultPacketExtensionProvider<>(CallInfoPacketExtension.class));
	}

	/**
	 * Parse the Coin IQ sub-document and returns the corresponding <tt>CoinIQ</tt>.
	 *
	 * @param parser
	 *        XML parser
	 * @return <tt>CoinIQ</tt>
	 * @throws XmlPullParserException, IOException, SmackException
	 *         if something goes wrong during parsing
	 */

	@Override
	public CoinIQ parse(XmlPullParser parser, int initialDepth)
		throws XmlPullParserException, IOException, SmackException
	{
		CoinIQ coinIQ = new CoinIQ();

		String entity = parser.getAttributeValue("", CoinIQ.ENTITY_ATTR_NAME);
		StateType state = StateType.full;
		// String stateStr = parser.getAttributeValue("", EndpointPacketExtension.STATE_ATTR_NAME);
		String stateStr = parser.getAttributeValue("", CoinIQ.STATE_ATTR_NAME);
		String version = parser.getAttributeValue("", CoinIQ.VERSION_ATTR_NAME);
		String sid = parser.getAttributeValue("", CoinIQ.SID_ATTR_NAME);

		if (stateStr != null) {
			state = StateType.parseString(stateStr);
		}

		coinIQ.setEntity(entity);
		coinIQ.setState(state);
		coinIQ.setVersion(Integer.parseInt(version));
		coinIQ.setSID(sid);

		// Now go on and parse the jingle element's content.
		int eventType;
		String elementName;
		boolean done = false;

		while (!done) {
			eventType = parser.next();
			elementName = parser.getName();

			if (eventType == XmlPullParser.START_TAG) {
				try {
					switch (elementName) {
						case DescriptionPacketExtension.ELEMENT_NAME: {
							ExtensionElement childExtension = descriptionProvider.parse(parser);
							coinIQ.addExtension(childExtension);
							break;
						}
						case StatePacketExtension.ELEMENT_NAME: {
							ExtensionElement childExtension = stateProvider.parseExtension(parser);
							coinIQ.addExtension(childExtension);
							break;
						}
						case UsersPacketExtension.ELEMENT_NAME: {
							ExtensionElement childExtension = usersProvider.parse(parser);
							coinIQ.addExtension(childExtension);
							break;
						}
						case URIsPacketExtension.ELEMENT_NAME: {
							ExtensionElement childExtension = urisProvider.parse(parser);
							coinIQ.addExtension(childExtension);
							break;
						}
						case SidebarsByValPacketExtension.ELEMENT_NAME: {
							ExtensionElement childExtension = sidebarsByValProvider.parse(parser);
							coinIQ.addExtension(childExtension);
							break;
						}
					}
				}
				catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (eventType == XmlPullParser.END_TAG) {
				if (parser.getName().equals(CoinIQ.ELEMENT_NAME)) {
					done = true;
				}
			}
		}
		return coinIQ;
	}

	/**
	 * Returns the content of the next {@link XmlPullParser#TEXT} element that we encounter in
	 * <tt>parser</tt>.
	 *
	 * @param parser
	 *        the parse that we'll be probing for text.
	 *
	 * @return the content of the next {@link XmlPullParser#TEXT} element we come across or
	 *         <tt>null</tt> if we encounter a closing tag first.
	 *
	 * @throws java.lang.Exception
	 *         if an error occurs parsing the XML.
	 */
	public static String parseText(XmlPullParser parser)
		throws Exception
	{
		boolean done = false;

		int eventType;
		String text = null;

		while (!done) {
			eventType = parser.next();

			if (eventType == XmlPullParser.TEXT) {
				text = parser.getText();
			}
			else if (eventType == XmlPullParser.END_TAG) {
				done = true;
			}
		}
		return text;
	}
}
