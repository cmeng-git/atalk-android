/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import android.text.TextUtils;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.WebSocketPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jitsimeet.*;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.*;
import org.jxmpp.jid.impl.JidCreate;
import org.xmlpull.v1.*;

import java.io.IOException;

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
				RtpDescriptionPacketExtension.ELEMENT_NAME, RtpDescriptionPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(RtpDescriptionPacketExtension.class));

		// <payload-type/> provider
		ProviderManager.addExtensionProvider(
				PayloadTypePacketExtension.ELEMENT_NAME, RtpDescriptionPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(PayloadTypePacketExtension.class));

		// <parameter/> provider
		ProviderManager.addExtensionProvider(
				ParameterPacketExtension.ELEMENT_NAME, RtpDescriptionPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(ParameterPacketExtension.class));

		// <rtp-hdrext/> provider
		ProviderManager.addExtensionProvider(
				RTPHdrExtPacketExtension.ELEMENT_NAME, RTPHdrExtPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(RTPHdrExtPacketExtension.class));

		// <sctpmap/> provider
		ProviderManager.addExtensionProvider(
				SctpMapExtension.ELEMENT_NAME, SctpMapExtension.NAMESPACE,
				new SctpMapExtensionProvider());

		// <encryption/> provider
		ProviderManager.addExtensionProvider(
				EncryptionPacketExtension.ELEMENT_NAME, RtpDescriptionPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(EncryptionPacketExtension.class));

		// <zrtp-hash/> provider
		ProviderManager.addExtensionProvider(
				ZrtpHashPacketExtension.ELEMENT_NAME, ZrtpHashPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(ZrtpHashPacketExtension.class));

		// <crypto/> provider
		ProviderManager.addExtensionProvider(
				CryptoPacketExtension.ELEMENT_NAME, RtpDescriptionPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(CryptoPacketExtension.class));

		// <bundle/> provider
		ProviderManager.addExtensionProvider(
				BundlePacketExtension.ELEMENT_NAME, BundlePacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(BundlePacketExtension.class));

		// <group/> provider
		ProviderManager.addExtensionProvider(
				GroupPacketExtension.ELEMENT_NAME, GroupPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(GroupPacketExtension.class));

		// ice-udp transport
		ProviderManager.addExtensionProvider(
				IceUdpTransportPacketExtension.ELEMENT_NAME, IceUdpTransportPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(IceUdpTransportPacketExtension.class));

		// <raw-udp/> provider
		ProviderManager.addExtensionProvider(
				RawUdpTransportPacketExtension.ELEMENT_NAME, RawUdpTransportPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(RawUdpTransportPacketExtension.class));

		// ice-udp <candidate/> provider
		ProviderManager.addExtensionProvider(CandidatePacketExtension.ELEMENT_NAME,
				IceUdpTransportPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(CandidatePacketExtension.class));

		// raw-udp <candidate/> provider
		ProviderManager.addExtensionProvider(
				CandidatePacketExtension.ELEMENT_NAME, RawUdpTransportPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(CandidatePacketExtension.class));

		// ice-udp <remote-candidate/> provider
		ProviderManager.addExtensionProvider(
				RemoteCandidatePacketExtension.ELEMENT_NAME, IceUdpTransportPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(RemoteCandidatePacketExtension.class));

		// inputevt <inputevt/> provider
		ProviderManager.addExtensionProvider(
				InputEvtPacketExtension.ELEMENT_NAME, InputEvtPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(InputEvtPacketExtension.class));

		// coin <conference-info/> provider
		ProviderManager.addExtensionProvider(
				CoinPacketExtension.ELEMENT_NAME, CoinPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(CoinPacketExtension.class));

		// DTLS-SRTP
		ProviderManager.addExtensionProvider(
				DtlsFingerprintPacketExtension.ELEMENT_NAME, DtlsFingerprintPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(DtlsFingerprintPacketExtension.class));

		/*
		 * XEP-0251: Jingle Session Transfer <transfer/> and <transferred> providers
		 */
		ProviderManager.addExtensionProvider(
				TransferPacketExtension.ELEMENT_NAME, TransferPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(TransferPacketExtension.class));

		ProviderManager.addExtensionProvider(
				TransferredPacketExtension.ELEMENT_NAME, TransferredPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(TransferredPacketExtension.class));

		// conference description <callid/> provider
		ProviderManager.addExtensionProvider(
				ConferenceDescriptionPacketExtension.CALLID_ELEM_NAME, ConferenceDescriptionPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(CallIdPacketExtension.class));

		// rtcp-fb
		ProviderManager.addExtensionProvider(
				RtcpFbPacketExtension.ELEMENT_NAME, RtcpFbPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(RtcpFbPacketExtension.class));

		// rtcp-mux
		ProviderManager.addExtensionProvider(
				RtcpmuxPacketExtension.ELEMENT_NAME, IceUdpTransportPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(RtcpmuxPacketExtension.class));

		//web-socket
		ProviderManager.addExtensionProvider(
				WebSocketPacketExtension.ELEMENT_NAME, WebSocketPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(WebSocketPacketExtension.class));

		// ssrcInfo
		ProviderManager.addExtensionProvider(
				SSRCInfoPacketExtension.ELEMENT_NAME, SSRCInfoPacketExtension.NAMESPACE,
				new DefaultPacketExtensionProvider<>(SSRCInfoPacketExtension.class));
	}

	/**
	 * Parses a Jingle IQ sub-document and returns a {@link JingleIQ} instance.
	 *
	 * @param parser
	 * 		an XML parser.
	 * @return a new {@link JingleIQ} instance.
	 * @throws XmlPullParserException, IOException, SmackException
	 * 		if an error occurs parsing the XML.
	 */
	@Override
	public JingleIQ parse(XmlPullParser parser, int depth)
			throws XmlPullParserException, IOException, SmackException
	{
		JingleIQ jingleIQ = new JingleIQ();

		// let's first handle the "jingle" element params.
		JingleAction action = JingleAction.parseString(parser.getAttributeValue("", JingleIQ.ACTION_ATTR_NAME));
		jingleIQ.setAction(action);

		String initiator = parser.getAttributeValue("", JingleIQ.INITIATOR_ATTR_NAME);
		if (!TextUtils.isEmpty(initiator))
			jingleIQ.setInitiator(JidCreate.from(initiator));

		String responder = parser.getAttributeValue("", JingleIQ.RESPONDER_ATTR_NAME);
		if (!TextUtils.isEmpty(responder))
			jingleIQ.setResponder(JidCreate.from(responder));

		String sid = parser.getAttributeValue("", JingleIQ.SID_ATTR_NAME);
		jingleIQ.setSID(sid);


		// Sub-elements providers
		DefaultPacketExtensionProvider<ContentPacketExtension> contentProvider
				= new DefaultPacketExtensionProvider<>(ContentPacketExtension.class);
		ReasonProvider reasonProvider = new ReasonProvider();
		DefaultPacketExtensionProvider<TransferPacketExtension> transferProvider
				= new DefaultPacketExtensionProvider<>(TransferPacketExtension.class);
		DefaultPacketExtensionProvider<CoinPacketExtension> coinProvider
				= new DefaultPacketExtensionProvider<>(CoinPacketExtension.class);
		DefaultPacketExtensionProvider<CallIdPacketExtension> callidProvider
				= new DefaultPacketExtensionProvider<>(CallIdPacketExtension.class);

		// Now go on and parse the jingle element's content.
		boolean done = false;
		int eventType;
		String elementName;
		String namespace;

		while (!done) {
			eventType = parser.next();
			elementName = parser.getName();
			namespace = parser.getNamespace();

			if (eventType == XmlPullParser.START_TAG) {
				// <content/>
				try {
					if (elementName.equals(ContentPacketExtension.ELEMENT_NAME)) {
						ContentPacketExtension content = contentProvider.parseExtension(parser);
						jingleIQ.addContent(content);
					}
					// <reason/>
					else if (elementName.equals(ReasonPacketExtension.ELEMENT_NAME)) {
						ReasonPacketExtension reason = reasonProvider.parse(parser);
						jingleIQ.setReason(reason);
					}
					// <transfer/>
					else if (elementName.equals(TransferPacketExtension.ELEMENT_NAME)
							&& namespace.equals(TransferPacketExtension.NAMESPACE)) {
						jingleIQ.addExtension(transferProvider.parseExtension(parser));
					}
					else if (elementName.equals(CoinPacketExtension.ELEMENT_NAME)) {
						jingleIQ.addExtension(coinProvider.parseExtension(parser));
					}
					else if (elementName
							.equals(ConferenceDescriptionPacketExtension.CALLID_ELEM_NAME)) {
						jingleIQ.addExtension(callidProvider.parseExtension(parser));
					}
					else if (elementName.equals(GroupPacketExtension.ELEMENT_NAME)) {
						jingleIQ.addExtension(GroupPacketExtension.parseExtension(parser));
					}
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				// <mute/> <active/> and other session-info elements
				if (namespace.equals(SessionInfoPacketExtension.NAMESPACE)) {
					SessionInfoType type = SessionInfoType.valueOf(elementName);

					// <mute/>
					if (type == SessionInfoType.mute || type == SessionInfoType.unmute) {
						String name = parser.getAttributeValue("", MuteSessionInfoPacketExtension.NAME_ATTR_VALUE);

						jingleIQ.setSessionInfo(new MuteSessionInfoPacketExtension(
								type == SessionInfoType.mute, name));
					}
					// <hold/>, <unhold/>, <active/>, etc.
					else {
						jingleIQ.setSessionInfo(new SessionInfoPacketExtension(type));
					}
				}
			}
			if ((eventType == XmlPullParser.END_TAG)
					&& parser.getName().equals(JingleIQ.ELEMENT_NAME)) {
				done = true;
			}
		}
		return jingleIQ;
	}
}
