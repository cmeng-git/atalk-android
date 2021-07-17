/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.xmpp.extensions.DefaultExtensionElementProvider;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;

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
    private final DefaultExtensionElementProvider<URIsExtension> urisProvider
            = new DefaultExtensionElementProvider<>(URIsExtension.class);

    /**
     * Provider for sidebars by val packet extension.
     */
    private final DefaultExtensionElementProvider<SidebarsByValExtension> sidebarsByValProvider
            = new DefaultExtensionElementProvider<>(SidebarsByValExtension.class);

    /**
     * Constructor.
     */
    public CoinIQProvider()
    {
        ProviderManager.addExtensionProvider(
                UserRolesExtension.ELEMENT, UserRolesExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(UserRolesExtension.class));

        ProviderManager.addExtensionProvider(
                URIExtension.ELEMENT, URIExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(URIExtension.class));

        ProviderManager.addExtensionProvider(
                SIPDialogIDExtension.ELEMENT, SIPDialogIDExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(SIPDialogIDExtension.class));

        ProviderManager.addExtensionProvider(
                ConferenceMediumExtension.ELEMENT, ConferenceMediumExtension.NAMESPACE,
                new ConferenceMediumProvider());

        ProviderManager.addExtensionProvider(
                ConferenceMediaExtension.ELEMENT, ConferenceMediaExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(ConferenceMediaExtension.class));

        ProviderManager.addExtensionProvider(
                CallInfoExtension.ELEMENT, CallInfoExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CallInfoExtension.class));
    }

    /**
     * Parse the Coin IQ sub-document and returns the corresponding <tt>CoinIQ</tt>.
     *
     * @param parser XML parser
     * @return <tt>CoinIQ</tt>
     * @throws IOException, XmlPullParserException, ParseException if something goes wrong during parsing
     */

    @Override
    public CoinIQ parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        CoinIQ coinIQ = new CoinIQ();

        String entity = parser.getAttributeValue("", CoinIQ.ENTITY_ATTR_NAME);
        String version = parser.getAttributeValue("", CoinIQ.VERSION_ATTR_NAME);
        String sid = parser.getAttributeValue("", CoinIQ.SID_ATTR_NAME);

        StateType state = StateType.full;
        String stateStr = parser.getAttributeValue("", CoinIQ.STATE_ATTR_NAME);
        if (stateStr != null) {
            state = StateType.fromString(stateStr);
        }

        coinIQ.setEntity(entity);
        coinIQ.setVersion(Integer.parseInt(version));
        coinIQ.setState(state);
        coinIQ.setSID(sid);

        // Now go on and parse the jingle element's content.
        XmlPullParser.Event eventType;
        String elementName;
        boolean done = false;

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                switch (elementName) {
                    case DescriptionExtension.ELEMENT: {
                        ExtensionElement childExtension = descriptionProvider.parse(parser);
                        coinIQ.addExtension(childExtension);
                        break;
                    }
                    case UsersExtension.ELEMENT: {
                        ExtensionElement childExtension = usersProvider.parse(parser);
                        coinIQ.addExtension(childExtension);
                        break;
                    }
                    case StateExtension.ELEMENT: {
                        ExtensionElement childExtension = stateProvider.parse(parser);
                        coinIQ.addExtension(childExtension);
                        break;
                    }
                    case URIsExtension.ELEMENT: {
                        ExtensionElement childExtension = urisProvider.parse(parser);
                        coinIQ.addExtension(childExtension);
                        break;
                    }
                    case SidebarsByValExtension.ELEMENT: {
                        ExtensionElement childExtension = sidebarsByValProvider.parse(parser);
                        coinIQ.addExtension(childExtension);
                        break;
                    }
                }
            }
            if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(CoinIQ.ELEMENT)) {
                    done = true;
                }
            }
        }
        return coinIQ;
    }

    /**
     * Returns the content of the next {@link XmlPullParser.Event#TEXT_CHARACTERS} element that we encounter in
     * <tt>parser</tt>.
     *
     * @param parser the parse that we'll be probing for text.
     * @return the content of the next {@link XmlPullParser.Event#TEXT_CHARACTERS} element we come across or
     * <tt>null</tt> if we encounter a closing tag first.
     * @throws IOException, XmlPullParserException if an error occurs parsing the XML.
     */
    public static String parseText(XmlPullParser parser)
            throws IOException, XmlPullParserException
    {
        boolean done = false;

        XmlPullParser.Event eventType;
        String text = null;

        while (!done) {
            eventType = parser.next();

            if (eventType == XmlPullParser.Event.TEXT_CHARACTERS) {
                text = parser.getText();
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                done = true;
            }
        }
        return text;
    }
}
