/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.util.ParserUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.jid.FullJid;
import org.xmpp.extensions.DefaultExtensionElementProvider;
import org.xmpp.extensions.colibri.WebSocketExtension;
import org.xmpp.extensions.condesc.CallIdExtension;
import org.xmpp.extensions.condesc.ConferenceDescriptionExtension;
import org.xmpp.extensions.jingle.*;
import org.xmpp.extensions.jingle.element.*;
import org.xmpp.extensions.jitsimeet.BundleExtension;
import org.xmpp.extensions.jitsimeet.SSRCInfoExtension;

import java.io.IOException;

import timber.log.Timber;

/**
 * An implementation of a Jingle IQ provider that parses incoming Jingle IQs.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class JingleProvider extends IQProvider<Jingle>
{
    /**
     * Creates a new instance of the <tt>JingleProvider</tt> and register all jingle related extension providers.
     * It is the responsibility of the application to register the <tt>JingleProvider</tt> itself.
     *
     * Note: All sub Elements without its own NAMESPACE use their parent NAMESPACE for provider support (Parser implementation)
     */
    public JingleProvider()
    {
        // <description/> provider
        ProviderManager.addExtensionProvider(
                RtpDescriptionExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtpDescriptionExtension.class));

        // <payload-type/> provider
        ProviderManager.addExtensionProvider(
                PayloadTypeExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(PayloadTypeExtension.class));

        // <parameter/> provider - RtpDescriptionExtensionElement
        ProviderManager.addExtensionProvider(
                ParameterExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(ParameterExtension.class));

        // <parameter/> provider - RTPHdrExtExtensionElement
        ProviderManager.addExtensionProvider(
                ParameterExtension.ELEMENT, RTPHdrExtExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(ParameterExtension.class));

        // <rtp-hdrext/> provider
        ProviderManager.addExtensionProvider(
                RTPHdrExtExtension.ELEMENT, RTPHdrExtExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RTPHdrExtExtension.class));

        // <sctpmap/> provider
        ProviderManager.addExtensionProvider(
                SctpMapExtension.ELEMENT, SctpMapExtension.NAMESPACE,
                new SctpMapExtensionProvider());

        // <encryption/> provider
        ProviderManager.addExtensionProvider(
                EncryptionExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(EncryptionExtension.class));

        // <zrtp-hash/> provider
        ProviderManager.addExtensionProvider(
                ZrtpHashExtension.ELEMENT, ZrtpHashExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(ZrtpHashExtension.class));

        // <crypto/> provider
        ProviderManager.addExtensionProvider(
                CryptoExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CryptoExtension.class));

        // <bundle/> provider
        ProviderManager.addExtensionProvider(
                BundleExtension.ELEMENT, BundleExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(BundleExtension.class));

        // <group/> provider
        ProviderManager.addExtensionProvider(
                GroupExtension.ELEMENT, GroupExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(GroupExtension.class));

        // ice-udp transport
        ProviderManager.addExtensionProvider(
                IceUdpTransportExtension.ELEMENT, IceUdpTransportExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(IceUdpTransportExtension.class));

        // <raw-udp/> provider
        ProviderManager.addExtensionProvider(
                RawUdpTransportExtension.ELEMENT, RawUdpTransportExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RawUdpTransportExtension.class));

        // ice-udp <candidate/> provider
        ProviderManager.addExtensionProvider(CandidateExtension.ELEMENT,
                IceUdpTransportExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CandidateExtension.class));

        // raw-udp <candidate/> provider
        ProviderManager.addExtensionProvider(
                CandidateExtension.ELEMENT, RawUdpTransportExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CandidateExtension.class));

        // ice-udp <remote-candidate/> provider
        ProviderManager.addExtensionProvider(
                RemoteCandidateExtension.ELEMENT, IceUdpTransportExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RemoteCandidateExtension.class));

        // inputevt <inputevt/> provider
        ProviderManager.addExtensionProvider(
                InputEvtExtension.ELEMENT, InputEvtExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(InputEvtExtension.class));

        // coin <conference-info/> provider
        ProviderManager.addExtensionProvider(
                CoinExtension.ELEMENT, CoinExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CoinExtension.class));

        // DTLS-SRTP
        ProviderManager.addExtensionProvider(
                DtlsFingerprintExtension.ELEMENT, DtlsFingerprintExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(DtlsFingerprintExtension.class));

        /*
         * XEP-0251: Jingle Session Transfer <transfer/> and <transferred> providers
         */
        ProviderManager.addExtensionProvider(
                TransferExtension.ELEMENT, TransferExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(TransferExtension.class));

        ProviderManager.addExtensionProvider(
                TransferredExtension.ELEMENT, TransferredExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(TransferredExtension.class));

        // conference description <callid/> provider
        ProviderManager.addExtensionProvider(
                CallIdExtension.ELEMENT, ConferenceDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(CallIdExtension.class));

        // rtcp-fb
        ProviderManager.addExtensionProvider(
                RtcpFbExtension.ELEMENT, RtcpFbExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpFbExtension.class));

        // rtcp-mux => XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22) https://xmpp.org/extensions/xep-0167.html
        ProviderManager.addExtensionProvider(
                RtcpmuxExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpmuxExtension.class));

        // rtcp-mux =>  Multiplexing RTP Data and Control Packets on a Single Port (April 2010)
        // https://tools.ietf.org/html/rfc5761 (5.1.3.  Interactions with ICE)
        ProviderManager.addExtensionProvider(
                RtcpmuxExtension.ELEMENT, IceUdpTransportExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpmuxExtension.class));

        //web-socket
        ProviderManager.addExtensionProvider(
                WebSocketExtension.ELEMENT, WebSocketExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(WebSocketExtension.class));

        // ssrcInfo
        ProviderManager.addExtensionProvider(
                SSRCInfoExtension.ELEMENT, SSRCInfoExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(SSRCInfoExtension.class));
    }

    /**
     * Parses a Jingle IQ sub-document and returns a {@link Jingle} instance.
     *
     * @param parser an XML parser.
     * @return a new {@link Jingle} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public Jingle parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        // let's first handle the "jingle" element params.
        String actionString = parser.getAttributeValue("", Jingle.ACTION_ATTRIBUTE_NAME);
        JingleAction action = JingleAction.fromString(actionString);
        String sessionId = parser.getAttributeValue("", Jingle.SESSION_ID_ATTRIBUTE_NAME);
        Jingle jingle = new Jingle(action, sessionId);

        FullJid initiator = ParserUtils.getFullJidAttribute(parser, Jingle.INITIATOR_ATTRIBUTE_NAME);
        jingle.setInitiator(initiator);

        FullJid responder = ParserUtils.getFullJidAttribute(parser, Jingle.RESPONDER_ATTRIBUTE_NAME);
        jingle.setResponder(responder);

        // Sub-elements providers
        DefaultExtensionElementProvider<JingleContent> contentProvider
                = new DefaultExtensionElementProvider<>(JingleContent.class);
        ReasonProvider reasonProvider = new ReasonProvider();
        DefaultExtensionElementProvider<TransferExtension> transferProvider
                = new DefaultExtensionElementProvider<>(TransferExtension.class);
        DefaultExtensionElementProvider<CoinExtension> coinProvider
                = new DefaultExtensionElementProvider<>(CoinExtension.class);
        DefaultExtensionElementProvider<CallIdExtension> callidProvider
                = new DefaultExtensionElementProvider<>(CallIdExtension.class);

        // Now go on and parse the jingle element's content.
        boolean done = false;
        String elementName;
        String namespace;

        while (!done) {
            XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    elementName = parser.getName();
                    namespace = parser.getNamespace();
                    switch (elementName) {
                        // <content/>
                        case JingleContent.ELEMENT:
                            JingleContent content = contentProvider.parse(parser);
                            jingle.addContent(content);
                            break;
                        // <reason/>
                        case JingleReason.ELEMENT:
                            JingleReason reason = reasonProvider.parse(parser);
                            jingle.setReason(reason);
                            break;
                        // <transfer/>
                        case TransferExtension.ELEMENT:
                            if (namespace.equals(TransferExtension.NAMESPACE)) {
                                jingle.addExtension(transferProvider.parse(parser));
                            }
                            break;
                        // <conference-info/>
                        case CoinExtension.ELEMENT:
                            jingle.addExtension(coinProvider.parse(parser));
                            break;
                        case CallIdExtension.ELEMENT:
                            jingle.addExtension(callidProvider.parse(parser));
                            break;
                        case GroupExtension.ELEMENT:
                            jingle.addExtension(GroupExtension.parseExtension(parser));
                            break;
                        default:
                            // <mute/> <active/> and other session-info element handlers
                            if (namespace.equals(SessionInfoExtension.NAMESPACE)) {
                                SessionInfoType type = SessionInfoType.valueOf(elementName);

                                // <mute/>
                                if (type == SessionInfoType.mute || type == SessionInfoType.unmute) {
                                    String name = parser.getAttributeValue("", MuteSessionInfoExtension.NAME_ATTR_VALUE);
                                    jingle.setSessionInfo(new MuteSessionInfoExtension(
                                            type == SessionInfoType.mute, name));
                                }
                                // <ringing/>, <hold/>, <unhold/>, <active/>, etc.
                                else {
                                    jingle.setSessionInfo(new SessionInfoExtension(type));
                                }
                            }
                            else {
                                Timber.w("Unknown jingle IQ: <%s xml:'%s'>)", elementName, namespace);
                                try {
                                    PacketParserUtils.addExtensionElement(jingle, parser, xmlEnvironment);
                                } catch (XmlPullParserException e) {
                                    // Exception if not supported by addExtensionElement, Just log info
                                    Timber.e("AddExtensionElement Exception: %s", jingle.toXML());
                                }
                            }
                    }
                    break;
                case END_ELEMENT:
                    // if (parser.getName().equals(Jingle.ELEMENT)) {
                    if (parser.getDepth() == initialDepth) {
                        done = true;
                    }
            }
        }
        return jingle;
    }
}
