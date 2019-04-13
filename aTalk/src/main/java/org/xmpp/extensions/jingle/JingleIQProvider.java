/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import android.text.TextUtils;

import org.xmpp.extensions.DefaultExtensionElementProvider;
import org.xmpp.extensions.colibri.WebSocketExtensionElement;
import org.xmpp.extensions.condesc.CallIdExtensionElement;
import org.xmpp.extensions.condesc.ConferenceDescriptionExtensionElement;
import org.xmpp.extensions.jitsimeet.BundleExtensionElement;
import org.xmpp.extensions.jitsimeet.SSRCInfoExtensionElement;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jxmpp.jid.impl.JidCreate;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import timber.log.Timber;

/**
 * An implementation of a Jingle IQ provider that parses incoming Jingle IQs.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class JingleIQProvider extends IQProvider<JingleIQ>
{
    /**
     * Creates a new instance of the <tt>JingleIQProvider</tt> and register all jingle related
     * extension providers. It is the responsibility of the application to register the
     * <tt>JingleIQProvider</tt> itself.
     */
    public JingleIQProvider()
    {
        // <description/> provider
        ProviderManager.addExtensionProvider(
                RtpDescriptionExtensionElement.ELEMENT_NAME, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtpDescriptionExtensionElement.class));

        // <payload-type/> provider
        ProviderManager.addExtensionProvider(
                PayloadTypeExtensionElement.ELEMENT_NAME, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(PayloadTypeExtensionElement.class));

        // <parameter/> provider - RtpDescriptionExtensionElement
        ProviderManager.addExtensionProvider(
                ParameterExtensionElement.ELEMENT_NAME, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(ParameterExtensionElement.class));

        // <parameter/> provider - RTPHdrExtExtensionElement
        ProviderManager.addExtensionProvider(
                ParameterExtensionElement.ELEMENT_NAME, RTPHdrExtExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(ParameterExtensionElement.class));

        // <rtp-hdrext/> provider
        ProviderManager.addExtensionProvider(
                RTPHdrExtExtensionElement.ELEMENT_NAME, RTPHdrExtExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RTPHdrExtExtensionElement.class));

        // <sctpmap/> provider
        ProviderManager.addExtensionProvider(
                SctpMapExtension.ELEMENT_NAME, SctpMapExtension.NAMESPACE,
                new SctpMapExtensionProvider());

        // <encryption/> provider
        ProviderManager.addExtensionProvider(
                EncryptionExtensionElement.ELEMENT_NAME, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(EncryptionExtensionElement.class));

        // <zrtp-hash/> provider
        ProviderManager.addExtensionProvider(
                ZrtpHashExtensionElement.ELEMENT_NAME, ZrtpHashExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(ZrtpHashExtensionElement.class));

        // <crypto/> provider
        ProviderManager.addExtensionProvider(
                CryptoExtensionElement.ELEMENT_NAME, RtpDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CryptoExtensionElement.class));

        // <bundle/> provider
        ProviderManager.addExtensionProvider(
                BundleExtensionElement.ELEMENT_NAME, BundleExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(BundleExtensionElement.class));

        // <group/> provider
        ProviderManager.addExtensionProvider(
                GroupExtensionElement.ELEMENT_NAME, GroupExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(GroupExtensionElement.class));

        // ice-udp transport
        ProviderManager.addExtensionProvider(
                IceUdpTransportExtensionElement.ELEMENT_NAME, IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(IceUdpTransportExtensionElement.class));

        // <raw-udp/> provider
        ProviderManager.addExtensionProvider(
                RawUdpTransportExtensionElement.ELEMENT_NAME, RawUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RawUdpTransportExtensionElement.class));

        // ice-udp <candidate/> provider
        ProviderManager.addExtensionProvider(CandidateExtensionElement.ELEMENT_NAME,
                IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CandidateExtensionElement.class));

        // raw-udp <candidate/> provider
        ProviderManager.addExtensionProvider(
                CandidateExtensionElement.ELEMENT_NAME, RawUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CandidateExtensionElement.class));

        // ice-udp <remote-candidate/> provider
        ProviderManager.addExtensionProvider(
                RemoteCandidateExtensionElement.ELEMENT_NAME, IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RemoteCandidateExtensionElement.class));

        // inputevt <inputevt/> provider
        ProviderManager.addExtensionProvider(
                InputEvtExtensionElement.ELEMENT_NAME, InputEvtExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(InputEvtExtensionElement.class));

        // coin <conference-info/> provider
        ProviderManager.addExtensionProvider(
                CoinExtensionElement.ELEMENT_NAME, CoinExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CoinExtensionElement.class));

        // DTLS-SRTP
        ProviderManager.addExtensionProvider(
                DtlsFingerprintExtensionElement.ELEMENT_NAME, DtlsFingerprintExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(DtlsFingerprintExtensionElement.class));

        /*
         * XEP-0251: Jingle Session Transfer <transfer/> and <transferred> providers
         */
        ProviderManager.addExtensionProvider(
                TransferExtensionElement.ELEMENT_NAME, TransferExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(TransferExtensionElement.class));

        ProviderManager.addExtensionProvider(
                TransferredExtensionElement.ELEMENT_NAME, TransferredExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(TransferredExtensionElement.class));

        // conference description <callid/> provider
        ProviderManager.addExtensionProvider(
                CallIdExtensionElement.ELEMENT_NAME, ConferenceDescriptionExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(CallIdExtensionElement.class));

        // rtcp-fb
        ProviderManager.addExtensionProvider(
                RtcpFbExtensionElement.ELEMENT_NAME, RtcpFbExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpFbExtensionElement.class));

        // rtcp-mux
        ProviderManager.addExtensionProvider(
                RtcpmuxExtensionElement.ELEMENT_NAME, IceUdpTransportExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpmuxExtensionElement.class));

        //web-socket
        ProviderManager.addExtensionProvider(
                WebSocketExtensionElement.ELEMENT_NAME, WebSocketExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(WebSocketExtensionElement.class));

        // ssrcInfo
        ProviderManager.addExtensionProvider(
                SSRCInfoExtensionElement.ELEMENT_NAME, SSRCInfoExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(SSRCInfoExtensionElement.class));
    }

    /**
     * Parses a Jingle IQ sub-document and returns a {@link JingleIQ} instance.
     *
     * @param parser an XML parser.
     * @return a new {@link JingleIQ} instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public JingleIQ parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        // let's first handle the "jingle" element params.
        JingleAction action = JingleAction.parseString(parser.getAttributeValue("", JingleIQ.ACTION_ATTR_NAME));
        String initiator = parser.getAttributeValue("", JingleIQ.INITIATOR_ATTR_NAME);
        String responder = parser.getAttributeValue("", JingleIQ.RESPONDER_ATTR_NAME);
        String sid = parser.getAttributeValue("", JingleIQ.SID_ATTR_NAME);

        JingleIQ jingleIQ = new JingleIQ(action, sid);
        if (!TextUtils.isEmpty(initiator)) {
            jingleIQ.setInitiator(JidCreate.from(initiator));
        }

        if (!TextUtils.isEmpty(responder)) {
            jingleIQ.setResponder(JidCreate.from(responder));
        }

        // Sub-elements providers
        DefaultExtensionElementProvider<ContentExtensionElement> contentProvider
                = new DefaultExtensionElementProvider<>(ContentExtensionElement.class);
        ReasonProvider reasonProvider = new ReasonProvider();
        DefaultExtensionElementProvider<TransferExtensionElement> transferProvider
                = new DefaultExtensionElementProvider<>(TransferExtensionElement.class);
        DefaultExtensionElementProvider<CoinExtensionElement> coinProvider
                = new DefaultExtensionElementProvider<>(CoinExtensionElement.class);
        DefaultExtensionElementProvider<CallIdExtensionElement> callidProvider
                = new DefaultExtensionElementProvider<>(CallIdExtensionElement.class);

        // Now go on and parse the jingle element's content.
        boolean done = false;
        int eventType;
        String elementName;
        String namespace;

        while (!done) {
            eventType = parser.next();
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    elementName = parser.getName();
                    namespace = parser.getNamespace();
                    switch (elementName) {
                        // <content/>
                        case ContentExtensionElement.ELEMENT_NAME:
                            ContentExtensionElement content = contentProvider.parse(parser);
                            jingleIQ.addContent(content);
                            break;
                        // <reason/>
                        case ReasonExtensionElement.ELEMENT_NAME:
                            ReasonExtensionElement reason = reasonProvider.parse(parser);
                            jingleIQ.setReason(reason);
                            break;
                        // <transfer/>
                        case TransferExtensionElement.ELEMENT_NAME:
                            if (namespace.equals(TransferExtensionElement.NAMESPACE)) {
                                jingleIQ.addExtension(transferProvider.parse(parser));
                            }
                            break;
                        // <conference-info/>
                        case CoinExtensionElement.ELEMENT_NAME:
                            jingleIQ.addExtension(coinProvider.parse(parser));
                            break;
                        case CallIdExtensionElement.ELEMENT_NAME:
                            jingleIQ.addExtension(callidProvider.parse(parser));
                            break;
                        case GroupExtensionElement.ELEMENT_NAME:
                            jingleIQ.addExtension(GroupExtensionElement.parseExtension(parser));
                            break;
                    }

                    // <mute/> <active/> and other session-info elements
                    if (namespace.equals(SessionInfoExtensionElement.NAMESPACE)) {
                        SessionInfoType type = SessionInfoType.valueOf(elementName);

                        // <mute/>
                        if (type == SessionInfoType.mute || type == SessionInfoType.unmute) {
                            String name = parser.getAttributeValue("", MuteSessionInfoExtensionElement.NAME_ATTR_VALUE);
                            jingleIQ.setSessionInfo(new MuteSessionInfoExtensionElement(
                                    type == SessionInfoType.mute, name));
                        }
                        // <hold/>, <unhold/>, <active/>, etc.
                        else {
                            jingleIQ.setSessionInfo(new SessionInfoExtensionElement(type));
                        }
                    }
                    else if (!ContentExtensionElement.ELEMENT_NAME.equals(elementName)) {
                        /*
                         * Seem to have problem with extracting correct elementName???; will failed if passed on to
                         * PacketParserUtils.addExtensionElement(jingleIQ, parser);
                         *
                         * <jingle xmlns='urn:xmpp:jingle:1' action='session-initiate' initiator='swordfish@atalk.org/atalk' sid='5e00252pm8if7'>
                         *     <content creator='initiator' name='audio'>
                         * Unknown jingle IQ type: content; urn:xmpp:jingle:1)
                         */
                        // Just log info; no safe to call, media call may failed if not supported by addExtensionElement
                        Timber.w("Unknown jingle IQ type: %s; %s)", elementName, namespace);
                        // PacketParserUtils.addExtensionElement(jingleIQ, parser);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    // if (parser.getName().equals(JingleIQ.ELEMENT_NAME)) {
                    if (parser.getDepth() == initialDepth) {
                        done = true;
                    }
            }
        }
        return jingleIQ;
    }
}
