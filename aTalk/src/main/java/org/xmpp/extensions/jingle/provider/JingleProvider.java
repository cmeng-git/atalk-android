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
import org.xmpp.extensions.colibri.WebSocketExtensionElement;
import org.xmpp.extensions.condesc.CallIdExtensionElement;
import org.xmpp.extensions.condesc.ConferenceDescriptionExtensionElement;
import org.xmpp.extensions.jingle.*;
import org.xmpp.extensions.jingle.element.*;
import org.xmpp.extensions.jitsimeet.BundleExtensionElement;
import org.xmpp.extensions.jitsimeet.SSRCInfoExtensionElement;

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
     * Creates a new instance of the <tt>JingleProvider</tt> and register all jingle related
     * extension providers. It is the responsibility of the application to register the
     * <tt>JingleProvider</tt> itself.
     */
    public JingleProvider()
    {
        // <description/> provider
        ProviderManager.addExtensionProvider(
                RtpDescriptionExtensionElement.ELEMENT, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtpDescriptionExtensionElement.class));

        // <payload-type/> provider
        ProviderManager.addExtensionProvider(
                PayloadTypeExtensionElement.ELEMENT, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(PayloadTypeExtensionElement.class));

        // <parameter/> provider - RtpDescriptionExtensionElement
        ProviderManager.addExtensionProvider(
                ParameterExtensionElement.ELEMENT, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(ParameterExtensionElement.class));

        // <parameter/> provider - RTPHdrExtExtensionElement
        ProviderManager.addExtensionProvider(
                ParameterExtensionElement.ELEMENT, RTPHdrExtExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(ParameterExtensionElement.class));

        // <rtp-hdrext/> provider
        ProviderManager.addExtensionProvider(
                RTPHdrExtExtensionElement.ELEMENT, RTPHdrExtExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RTPHdrExtExtensionElement.class));

        // <sctpmap/> provider
        ProviderManager.addExtensionProvider(
                SctpMapExtension.ELEMENT, SctpMapExtension.NAMESPACE,
                new SctpMapExtensionProvider());

        // <encryption/> provider
        ProviderManager.addExtensionProvider(
                EncryptionExtensionElement.ELEMENT, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(EncryptionExtensionElement.class));

        // <zrtp-hash/> provider
        ProviderManager.addExtensionProvider(
                ZrtpHashExtensionElement.ELEMENT, ZrtpHashExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(ZrtpHashExtensionElement.class));

        // <crypto/> provider
        ProviderManager.addExtensionProvider(
                CryptoExtensionElement.ELEMENT, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CryptoExtensionElement.class));

        // <bundle/> provider
        ProviderManager.addExtensionProvider(
                BundleExtensionElement.ELEMENT, BundleExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(BundleExtensionElement.class));

        // <group/> provider
        ProviderManager.addExtensionProvider(
                GroupExtensionElement.ELEMENT, GroupExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(GroupExtensionElement.class));

        // ice-udp transport
        ProviderManager.addExtensionProvider(
                IceUdpTransportExtensionElement.ELEMENT, IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(IceUdpTransportExtensionElement.class));

        // <raw-udp/> provider
        ProviderManager.addExtensionProvider(
                RawUdpTransportExtensionElement.ELEMENT, RawUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RawUdpTransportExtensionElement.class));

        // ice-udp <candidate/> provider
        ProviderManager.addExtensionProvider(CandidateExtensionElement.ELEMENT,
                IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CandidateExtensionElement.class));

        // raw-udp <candidate/> provider
        ProviderManager.addExtensionProvider(
                CandidateExtensionElement.ELEMENT, RawUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CandidateExtensionElement.class));

        // ice-udp <remote-candidate/> provider
        ProviderManager.addExtensionProvider(
                RemoteCandidateExtensionElement.ELEMENT, IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RemoteCandidateExtensionElement.class));

        // inputevt <inputevt/> provider
        ProviderManager.addExtensionProvider(
                InputEvtExtensionElement.ELEMENT, InputEvtExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(InputEvtExtensionElement.class));

        // coin <conference-info/> provider
        ProviderManager.addExtensionProvider(
                CoinExtensionElement.ELEMENT, CoinExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CoinExtensionElement.class));

        // DTLS-SRTP
        ProviderManager.addExtensionProvider(
                DtlsFingerprintExtensionElement.ELEMENT, DtlsFingerprintExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(DtlsFingerprintExtensionElement.class));

        /*
         * XEP-0251: Jingle Session Transfer <transfer/> and <transferred> providers
         */
        ProviderManager.addExtensionProvider(
                TransferExtensionElement.ELEMENT, TransferExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(TransferExtensionElement.class));

        ProviderManager.addExtensionProvider(
                TransferredExtensionElement.ELEMENT, TransferredExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(TransferredExtensionElement.class));

        // conference description <callid/> provider
        ProviderManager.addExtensionProvider(
                CallIdExtensionElement.ELEMENT, ConferenceDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CallIdExtensionElement.class));

        // rtcp-fb
        ProviderManager.addExtensionProvider(
                RtcpFbExtensionElement.ELEMENT, RtcpFbExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpFbExtensionElement.class));

        // rtcp-mux
        ProviderManager.addExtensionProvider(
                RtcpmuxExtensionElement.ELEMENT, IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpmuxExtensionElement.class));

        //web-socket
        ProviderManager.addExtensionProvider(
                WebSocketExtensionElement.ELEMENT, WebSocketExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(WebSocketExtensionElement.class));

        // ssrcInfo
        ProviderManager.addExtensionProvider(
                SSRCInfoExtensionElement.ELEMENT, SSRCInfoExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(SSRCInfoExtensionElement.class));
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
        DefaultExtensionElementProvider<TransferExtensionElement> transferProvider
                = new DefaultExtensionElementProvider<>(TransferExtensionElement.class);
        DefaultExtensionElementProvider<CoinExtensionElement> coinProvider
                = new DefaultExtensionElementProvider<>(CoinExtensionElement.class);
        DefaultExtensionElementProvider<CallIdExtensionElement> callidProvider
                = new DefaultExtensionElementProvider<>(CallIdExtensionElement.class);

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
                        case TransferExtensionElement.ELEMENT:
                            if (namespace.equals(TransferExtensionElement.NAMESPACE)) {
                                jingle.addExtension(transferProvider.parse(parser));
                            }
                            break;
                        // <conference-info/>
                        case CoinExtensionElement.ELEMENT:
                            jingle.addExtension(coinProvider.parse(parser));
                            break;
                        case CallIdExtensionElement.ELEMENT:
                            jingle.addExtension(callidProvider.parse(parser));
                            break;
                        case GroupExtensionElement.ELEMENT:
                            jingle.addExtension(GroupExtensionElement.parseExtension(parser));
                            break;
                        default:
                            // <mute/> <active/> and other session-info element handlers
                            if (namespace.equals(SessionInfoExtensionElement.NAMESPACE)) {
                                SessionInfoType type = SessionInfoType.valueOf(elementName);

                                // <mute/>
                                if (type == SessionInfoType.mute || type == SessionInfoType.unmute) {
                                    String name = parser.getAttributeValue("", MuteSessionInfoExtensionElement.NAME_ATTR_VALUE);
                                    jingle.setSessionInfo(new MuteSessionInfoExtensionElement(
                                            type == SessionInfoType.mute, name));
                                }
                                // <ringing/>, <hold/>, <unhold/>, <active/>, etc.
                                else {
                                    jingle.setSessionInfo(new SessionInfoExtensionElement(type));
                                }
                            }
                            else {
                                /*
                                 * Seem to have problem with extracting correct elementName???; will failed if passed on to
                                 * PacketParserUtils.addExtensionElement(jingle, parser);
                                 *
                                 * <jingle xmlns='urn:xmpp:jingle:1' action='session-initiate'
                                 *         initiator='swordfish@atalk.org/atalk' sid='5e00252pm8if7'>
                                 *     <content creator='initiator' name='audio'>
                                 * Unknown jingle IQ type: content; urn:xmpp:jingle:1)
                                 */
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
                    // if (parser.getName().equals(Jingle.ELEMENT_NAME)) {
                    if (parser.getDepth() == initialDepth) {
                        done = true;
                    }
            }
        }
        return jingle;
    }
}
