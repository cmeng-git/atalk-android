/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.colibri;

import org.atalk.service.neomedia.MediaDirection;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.*;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.DefaultExtensionElementProvider;
import org.jivesoftware.smackx.jingle_rtp.DefaultXmlElementProvider;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.ParameterElement;
import org.jivesoftware.smackx.jingle_rtp.element.PayloadType;
import org.jivesoftware.smackx.jingle_rtp.element.RawUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.RtcpFb;
import org.jivesoftware.smackx.jingle_rtp.element.RtpHeader;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSource;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSourceGroup;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSourceRidGroup;
import org.jivesoftware.smackx.jitsimeet.SSRCInfoExtension;
import org.jxmpp.jid.parts.Localpart;

import java.io.IOException;

import timber.log.Timber;

/**
 * Implements an <code>org.jivesoftware.smack.provider.IQProvider</code> for the Jitsi Videobridge
 * extension <code>ColibriConferenceIQ</code>.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class ColibriIQProvider extends IQProvider<ColibriConferenceIQ>
{
    /**
     * Initializes a new <code>ColibriIQProvider</code> instance; only for those no defined in JingleProvider.
     */
    public ColibriIQProvider()
    {
        ProviderManager.addExtensionProvider(
                PayloadType.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                new DefaultXmlElementProvider<>(PayloadType.class, ColibriConferenceIQ.NAMESPACE));

        ProviderManager.addExtensionProvider(
                RtpHeader.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                new DefaultXmlElementProvider<>(RtpHeader.class, ColibriConferenceIQ.NAMESPACE));

        ProviderManager.addExtensionProvider(
                SdpSource.ELEMENT, SdpSource.NAMESPACE,
                new DefaultXmlElementProvider<>(SdpSource.class));

        ProviderManager.addExtensionProvider(
                SdpSourceGroup.ELEMENT, SdpSourceGroup.NAMESPACE,
                new DefaultXmlElementProvider<>(SdpSourceGroup.class));

        ProviderManager.addExtensionProvider(
                SdpSourceRidGroup.ELEMENT, SdpSourceRidGroup.NAMESPACE,
                new DefaultXmlElementProvider<>(SdpSourceRidGroup.class));

        ProviderManager.addExtensionProvider(
                ParameterElement.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                new DefaultXmlElementProvider<>(ParameterElement.class, ColibriConferenceIQ.NAMESPACE));

        // Shutdown IQ
        ProviderManager.addIQProvider(ShutdownIQ.GRACEFUL_ELEMENT, ShutdownIQ.NAMESPACE, this);
        ProviderManager.addIQProvider(ShutdownIQ.FORCE_ELEMENT, ShutdownIQ.NAMESPACE, this);

        // Shutdown extension
        ExtensionElementProvider<?> shutdownProvider
                = new DefaultExtensionElementProvider<>(ColibriConferenceIQ.GracefulShutdown.class);
        ProviderManager.addExtensionProvider(
                ColibriConferenceIQ.GracefulShutdown.ELEMENT, ColibriConferenceIQ.GracefulShutdown.NAMESPACE,
                shutdownProvider);

        // ColibriStatsIQ
        ProviderManager.addIQProvider(ColibriStatsIQ.ELEMENT, ColibriStatsIQ.NAMESPACE, this);

        // ColibriStatsExtensionElement
        ExtensionElementProvider<?> statsProvider
                = new DefaultExtensionElementProvider<>(ColibriStatsExtension.class);
        ProviderManager.addExtensionProvider(
                ColibriStatsExtension.ELEMENT, ColibriStatsExtension.NAMESPACE, statsProvider);

        // ColibriStatsExtensionElement.Stat
        ExtensionElementProvider<?> statProvider
                = new DefaultExtensionElementProvider<>(ColibriStatsExtension.Stat.class);
        ProviderManager.addExtensionProvider(
                ColibriStatsExtension.Stat.ELEMENT, ColibriStatsExtension.NAMESPACE, statProvider);

        // ssrc-info
        ProviderManager.addExtensionProvider(
                SSRCInfoExtension.ELEMENT, SSRCInfoExtension.NAMESPACE,
                new DefaultExtensionElementProvider<>(SSRCInfoExtension.class));
    }

    private void addChildExtension(ColibriConferenceIQ.Channel channel, ExtensionElement childExtension)
    {
        if (childExtension instanceof PayloadType) {
            PayloadType payloadType = (PayloadType) childExtension;
            if ("opus".equals(payloadType.getName()) && (payloadType.getChannels() != 2)) {
                /*
                 * We only have a Format for opus with 2 channels, because it MUST be advertised
                 * with 2 channels. Fixing the number of channels here allows us to be compatible
                 * with agents who advertise it with 1 channel.
                 */
                payloadType.setChannels(2);
//                payloadType = ((PayloadType.Builder) payloadType.getBuilder(payloadType.getNamespace()))
//                        .setChannels(2)
//                        .build();
            }
            channel.addPayloadType(payloadType);
        }
        else if (childExtension instanceof IceUdpTransport) {
            IceUdpTransport transport = (IceUdpTransport) childExtension;
            channel.setTransport(transport);
        }
        else if (childExtension instanceof SdpSource) {
            channel.addSource((SdpSource) childExtension);
        }
        else if (childExtension instanceof SdpSourceGroup) {
            SdpSourceGroup sourceGroup = (SdpSourceGroup) childExtension;
            channel.addSourceGroup(sourceGroup);
        }
        else if (childExtension instanceof RtpHeader) {
            RtpHeader rtpHdrExtPacketExtension = (RtpHeader) childExtension;
            channel.addRtpHeaderExtension(rtpHdrExtPacketExtension);
        }
        else {
            Timber.e("Ignoring a child of 'channel' of unknown type: %s", childExtension);
        }
    }

    private void addChildExtension(ColibriConferenceIQ.ChannelBundle bundle, ExtensionElement childExtension)
    {
        if (childExtension instanceof IceUdpTransport) {
            IceUdpTransport transport = (IceUdpTransport) childExtension;
            bundle.setTransport(transport);
        }
    }

    private void addChildExtension(ColibriConferenceIQ.SctpConnection sctpConnection,
            ExtensionElement childExtension)
    {
        if (childExtension instanceof IceUdpTransport) {
            IceUdpTransport transport = (IceUdpTransport) childExtension;
            sctpConnection.setTransport(transport);
        }
    }

    private ExtensionElement parseExtension(XmlPullParser parser, String name, String namespace)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        ExtensionElementProvider<?> extensionProvider = ProviderManager.getExtensionProvider(name, namespace);
        ExtensionElement extension;
        if (extensionProvider == null) {
            /*
             * No PacketExtensionProvider for the specified name and namespace has been registered.
             * Throw away the element.
             */
            throwAway(parser, name);
            extension = null;
        }
        else {
            extension = extensionProvider.parse(parser);
        }
        return extension;
    }

    /**
     * Parses an IQ sub-document and creates an <code>org.jivesoftware.smack.packet.IQ</code> instance.
     *
     * @param parser an <code>XmlPullParser</code> which specifies the IQ sub-document to be parsed into a new
     * <code>IQ</code> instance
     * @return a new <code>IQ</code> instance parsed from the specified IQ sub-document
     */
    // Compatibility with legacy Jitsi and Jitsi Videobridge
    @SuppressWarnings("deprecation")
    @Override
    public ColibriConferenceIQ parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        String namespace = parser.getNamespace();
        IQ iq;

        if (ColibriConferenceIQ.ELEMENT.equals(parser.getName())
                && ColibriConferenceIQ.NAMESPACE.equals(namespace)) {
            ColibriConferenceIQ conferenceIQ = new ColibriConferenceIQ();
            String conferenceID = parser.getAttributeValue("", ColibriConferenceIQ.ID_ATTR_NAME);
            if ((conferenceID != null) && (conferenceID.length() != 0))
                conferenceIQ.setID(conferenceID);

            String conferenceGID = parser.getAttributeValue("", ColibriConferenceIQ.GID_ATTR_NAME);
            if ((conferenceGID != null) && (conferenceGID.length() != 0))
                conferenceIQ.setGID(conferenceGID);

            String conferenceName = parser.getAttributeValue("", ColibriConferenceIQ.NAME_ATTR_NAME);
            if ((conferenceName != null) && (conferenceName.length() != 0))
                if (StringUtils.isNotEmpty(conferenceName))
                    conferenceIQ.setName(Localpart.from(conferenceName));
            boolean done = false;
            ColibriConferenceIQ.Channel channel = null;
            ColibriConferenceIQ.RTCPTerminationStrategy rtcpTerminationStrategy = null;
            ColibriConferenceIQ.SctpConnection sctpConnection = null;
            ColibriConferenceIQ.ChannelBundle bundle = null;
            ColibriConferenceIQ.Content content = null;
            ColibriConferenceIQ.Recording recording = null;
            ColibriConferenceIQ.Endpoint conferenceEndpoint = null;
            StringBuilder ssrc = null;

            while (!done) {
                switch (parser.next()) {
                    case END_ELEMENT: {
                        String name = parser.getName();

                        if (ColibriConferenceIQ.ELEMENT.equals(name)) {
                            done = true;
                        }
                        else if (ColibriConferenceIQ.Channel.ELEMENT.equals(name)) {
                            content.addChannel(channel);
                            channel = null;
                        }
                        else if (ColibriConferenceIQ.SctpConnection.ELEMENT.equals(name)) {
                            if (sctpConnection != null)
                                content.addSctpConnection(sctpConnection);
                            sctpConnection = null;
                        }
                        else if (ColibriConferenceIQ.ChannelBundle.ELEMENT.equals(name)) {
                            if (bundle != null) {
                                if (conferenceIQ.addChannelBundle(bundle) != null) {
                                    Timber.w("Replacing a channel-bundle with the same ID (not a valid Colibri packet).");
                                }
                                bundle = null;
                            }
                        }
                        else if (ColibriConferenceIQ.Endpoint.ELEMENT.equals(name)) {
                            if (conferenceIQ.addEndpoint(conferenceEndpoint) != null) {
                                Timber.w("Replacing an endpoint element with the same ID (not a valid Colibri packet).");
                            }
                            conferenceEndpoint = null;
                        }
                        else if (ColibriConferenceIQ.Channel.SSRC_ELEMENT.equals(name)) {
                            String s = (ssrc == null) ? null : ssrc.toString().trim();
                            if (StringUtils.isNotEmpty(s)) {
                                int i;

                                /*
                                 * Legacy versions of Jitsi and Jitsi Videobridge may send a
                                 * synchronization source (SSRC) identifier as a negative integer.
                                 */
                                if (s.startsWith("-"))
                                    i = Integer.parseInt(s);
                                else
                                    i = (int) Long.parseLong(s);
                                channel.addSSRC(i);
                            }
                            ssrc = null;
                        }
                        else if (ColibriConferenceIQ.Content.ELEMENT.equals(name)) {
                            conferenceIQ.addContent(content);
                            content = null;
                        }
                        else if (ColibriConferenceIQ.RTCPTerminationStrategy.ELEMENT.equals(name)) {
                            conferenceIQ.setRTCPTerminationStrategy(rtcpTerminationStrategy);
                            rtcpTerminationStrategy = null;
                        }
                        else if (ColibriConferenceIQ.Recording.ELEMENT.equals(name)) {
                            conferenceIQ.setRecording(recording);
                            recording = null;
                        }
                        else if (ColibriConferenceIQ.GracefulShutdown.ELEMENT.equals(name)) {
                            conferenceIQ.setGracefulShutdown(true);
                        }
                        break;
                    }

                    case START_ELEMENT: {
                        String name = parser.getName();

                        if (ColibriConferenceIQ.Channel.ELEMENT.equals(name)) {
                            String type = parser.getAttributeValue("", ColibriConferenceIQ.Channel.TYPE_ATTR_NAME);

                            if (ColibriConferenceIQ.OctoChannel.TYPE.equals(type)) {
                                channel = new ColibriConferenceIQ.OctoChannel();
                            }
                            else {
                                channel = new ColibriConferenceIQ.Channel();
                            }

                            // direction
                            String direction = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.DIRECTION_ATTR_NAME);
                            if (StringUtils.isNotEmpty(direction)) {
                                channel.setDirection(MediaDirection.fromString(direction));
                            }

                            // endpoint
                            String endpoint = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.ENDPOINT_ATTR_NAME);
                            if (StringUtils.isNotEmpty(endpoint)) {
                                channel.setEndpoint(endpoint);
                            }

                            String channelBundleId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelCommon.CHANNEL_BUNDLE_ID_ATTR_NAME);
                            if (StringUtils.isNotEmpty(channelBundleId)) {
                                channel.setChannelBundleId(channelBundleId);
                            }

                            // expire
                            String expire = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.EXPIRE_ATTR_NAME);
                            if (StringUtils.isNotEmpty(expire)) {
                                channel.setExpire(Integer.parseInt(expire));
                            }

                            // packetDelay
                            String packetDelay = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.PACKET_DELAY_ATTR_NAME);
                            if (StringUtils.isNotEmpty(packetDelay)) {
                                channel.setPacketDelay(Integer.parseInt(packetDelay));
                            }

                            // host
                            String host = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.HOST_ATTR_NAME);
                            if (StringUtils.isNotEmpty(host)) {
                                channel.setHost(host);
                            }

                            // id
                            String channelID = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.ID_ATTR_NAME);
                            if (StringUtils.isNotEmpty(channelID)) {
                                channel.setID(channelID);
                            }

                            // initiator
                            String initiator = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.INITIATOR_ATTR_NAME);
                            if (StringUtils.isNotEmpty(initiator)) {
                                channel.setInitiator(Boolean.valueOf(initiator));
                            }

                            // lastN
                            String lastN = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.LAST_N_ATTR_NAME);
                            if (StringUtils.isNotEmpty(lastN)) {
                                channel.setLastN(Integer.parseInt(lastN));
                            }

                            // simulcastMode
                            String simulcastMode = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.SIMULCAST_MODE_ATTR_NAME);
                            if (StringUtils.isNotEmpty(simulcastMode)) {
                                channel.setSimulcastMode(SimulcastMode.fromString(simulcastMode));
                            }

                            // receiving simulcast layer
                            String receivingSimulcastLayer = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RECEIVING_SIMULCAST_LAYER);
                            if (StringUtils.isNotEmpty(receivingSimulcastLayer)) {
                                channel.setReceivingSimulcastLayer(Integer.parseInt(receivingSimulcastLayer));
                            }

                            // rtcpPort
                            String rtcpPort = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RTCP_PORT_ATTR_NAME);
                            if (StringUtils.isNotEmpty(rtcpPort)) {
                                channel.setRTCPPort(Integer.parseInt(rtcpPort));
                            }

                            // rtpLevelRelayType
                            String rtpLevelRelayType = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RTP_LEVEL_RELAY_TYPE_ATTR_NAME);
                            if (StringUtils.isNotEmpty(rtpLevelRelayType)) {
                                channel.setRTPLevelRelayType(rtpLevelRelayType);
                            }

                            // rtpPort
                            String rtpPort = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RTP_PORT_ATTR_NAME);
                            if (StringUtils.isNotEmpty(rtpPort)) {
                                channel.setRTPPort(Integer.parseInt(rtpPort));
                            }
                        }
                        else if (ColibriConferenceIQ.ChannelBundle.ELEMENT.equals(name)) {
                            String bundleId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelBundle.ID_ATTR_NAME);

                            if (StringUtils.isNotEmpty(bundleId)) {
                                bundle = new ColibriConferenceIQ.ChannelBundle(bundleId);
                            }
                        }
                        else if (ColibriConferenceIQ.RTCPTerminationStrategy.ELEMENT.equals(name)) {
                            rtcpTerminationStrategy = new ColibriConferenceIQ.RTCPTerminationStrategy();

                            // name
                            String strategyName = parser.getAttributeValue("",
                                    ColibriConferenceIQ.RTCPTerminationStrategy.NAME_ATTR_NAME);
                            if (StringUtils.isNotEmpty(strategyName)) {
                                rtcpTerminationStrategy.setName(strategyName);
                            }
                        }
                        else if (ColibriConferenceIQ.OctoChannel.RELAY_ELEMENT.equals(name)) {
                            String id = parser.getAttributeValue("",
                                    ColibriConferenceIQ.OctoChannel.RELAY_ID_ATTR_NAME);
                            if (id != null
                                    && channel instanceof ColibriConferenceIQ.OctoChannel) {
                                ((ColibriConferenceIQ.OctoChannel) channel).addRelay(id);
                            }
                        }
                        else if (ColibriConferenceIQ.Channel.SSRC_ELEMENT.equals(name)) {
                            ssrc = new StringBuilder();
                        }
                        else if (ColibriConferenceIQ.Content.ELEMENT.equals(name)) {
                            content = new ColibriConferenceIQ.Content();

                            String contentName = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Content.NAME_ATTR_NAME);
                            if ((contentName != null) && (contentName.length() != 0))
                                content.setName(contentName);
                        }
                        else if (ColibriConferenceIQ.Recording.ELEMENT.equals(name)) {
                            String stateStr = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Recording.STATE_ATTR_NAME);

                            String token = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Recording.TOKEN_ATTR_NAME);

                            recording = new ColibriConferenceIQ.Recording(stateStr, token);
                        }
                        else if (ColibriConferenceIQ.SctpConnection.ELEMENT.equals(name)) {
                            // Endpoint
                            String endpoint = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.ENDPOINT_ATTR_NAME);

                            // id
                            String connID = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelCommon.ID_ATTR_NAME);

                            if (StringUtils.isEmpty(connID) && StringUtils.isEmpty(endpoint)) {
                                sctpConnection = null;
                                continue;
                            }

                            sctpConnection = new ColibriConferenceIQ.SctpConnection();
                            if (StringUtils.isNotEmpty(connID))
                                sctpConnection.setID(connID);

                            if (StringUtils.isNotEmpty(endpoint)) {
                                sctpConnection.setEndpoint(endpoint);
                            }

                            // port
                            String port = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.PORT_ATTR_NAME);
                            if (StringUtils.isNotEmpty(port))
                                sctpConnection.setPort(Integer.parseInt(port));

                            String channelBundleId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelCommon.CHANNEL_BUNDLE_ID_ATTR_NAME);
                            if (StringUtils.isNotEmpty(channelBundleId))
                                sctpConnection.setChannelBundleId(channelBundleId);

                            // initiator
                            String initiator = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.INITIATOR_ATTR_NAME);

                            if (StringUtils.isNotEmpty(initiator))
                                sctpConnection.setInitiator(Boolean.valueOf(initiator));

                            // expire
                            String expire = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.EXPIRE_ATTR_NAME);

                            if (StringUtils.isNotEmpty(expire))
                                sctpConnection.setExpire(Integer.parseInt(expire));
                        }
                        else if (ColibriConferenceIQ.Endpoint.ELEMENT.equals(name)) {
                            String id = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Endpoint.ID_ATTR_NAME);

                            String displayName = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Endpoint.DISPLAYNAME_ATTR_NAME);

                            String statsId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Endpoint.STATS_ID_ATTR_NAME);

                            if (StringUtils.isNotEmpty(id)) {
                                conferenceEndpoint = new ColibriConferenceIQ.Endpoint(id, statsId, displayName);
                            }
                        }
                        else if (channel != null || sctpConnection != null || bundle != null) {
                            String peName = null;
                            String peNamespace = null;

                            if (IceUdpTransport.ELEMENT.equals(name)
                                    && IceUdpTransport.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = IceUdpTransport.NAMESPACE;
                            }
                            else if (PayloadType.ELEMENT.equals(name)) {
                                /*
                                 * The channel element of the Jitsi Videobridge protocol reuses the
                                 * payload-type element defined in XEP-0167: Jingle RTP Sessions.
                                 */
                                peName = name;
                                peNamespace = namespace;
                            }
                            else if (RtcpFb.ELEMENT.equals(name)
                                    && RtcpFb.NAMESPACE.equals(parser.getNamespace())) {
                                /*
                                 * The channel element of the Jitsi Videobridge protocol reuses the
                                 * payload-type element defined in XEP-0167: Jingle RTP Sessions.
                                 */
                                peName = name;
                                peNamespace = namespace;
                            }
                            else if (RtpHeader.ELEMENT.equals(name)) {
                                /*
                                 * The channel element of the Jitsi Videobridge protocol reuses the
                                 * rtp-hdrext element defined in XEP-0167: Jingle RTP Sessions.
                                 */
                                peName = name;
                                peNamespace = namespace;
                            }
                            else if (RawUdpTransport.ELEMENT.equals(name)
                                    && RawUdpTransport.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = RawUdpTransport.NAMESPACE;
                            }
                            else if (SdpSource.ELEMENT.equals(name)
                                    && SdpSource.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = SdpSource.NAMESPACE;
                            }
                            else if (SdpSourceGroup.ELEMENT.equals(name)
                                    && SdpSourceGroup.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = SdpSourceGroup.NAMESPACE;
                            }
                            else if (SdpSourceRidGroup.ELEMENT.equals(name)
                                    && SdpSourceRidGroup.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = SdpSourceRidGroup.NAMESPACE;
                            }
                            if (peName == null) {
                                throwAway(parser, name);
                            }
                            else {
                                ExtensionElement extension = parseExtension(parser, peName, peNamespace);
                                if (extension != null) {
                                    if (channel != null) {
                                        addChildExtension(channel, extension);
                                    }
                                    else if (sctpConnection != null) {
                                        addChildExtension(sctpConnection, extension);
                                    }
                                    else {
                                        addChildExtension(bundle, extension);
                                    }
                                }
                            }
                        }
                        break;
                    }

                    case TEXT_CHARACTERS: {
                        if (ssrc != null)
                            ssrc.append(parser.getText());
                        break;
                    }
                }
            }
            iq = conferenceIQ;
        }
        else if (ShutdownIQ.NAMESPACE.equals(namespace) && ShutdownIQ.isValidElementName(parser.getName())) {
            String rootElement = parser.getName();
            iq = ShutdownIQ.createShutdownIQ(rootElement);
            boolean done = false;
            while (!done) {
                switch (parser.next()) {
                    case END_ELEMENT: {
                        String name = parser.getName();
                        if (rootElement.equals(name)) {
                            done = true;
                        }
                        break;
                    }
                }
            }
        }
        else if (ColibriStatsIQ.ELEMENT.equals(parser.getName()) && ColibriStatsIQ.NAMESPACE.equals(namespace)) {
            String rootElement = parser.getName();
            ColibriStatsIQ statsIQ = new ColibriStatsIQ();
            iq = statsIQ;
            ColibriStatsExtension.Stat stat = null;

            boolean done = false;
            while (!done) {
                switch (parser.next()) {
                    case START_ELEMENT: {
                        String name = parser.getName();

                        if (ColibriStatsExtension.Stat.ELEMENT.equals(name)) {
                            stat = new ColibriStatsExtension.Stat();

                            String statName = parser.getAttributeValue("",
                                    ColibriStatsExtension.Stat.NAME_ATTR_NAME);
                            stat.setName(statName);

                            String statValue = parser.getAttributeValue("",
                                    ColibriStatsExtension.Stat.VALUE_ATTR_NAME);
                            stat.setValue(statValue);
                        }
                        break;
                    }
                    case END_ELEMENT: {
                        String name = parser.getName();
                        if (rootElement.equals(name)) {
                            done = true;
                        }
                        else if (ColibriStatsExtension.Stat.ELEMENT.equals(name)) {
                            if (stat != null) {
                                statsIQ.addStat(stat);
                                stat = null;
                            }
                        }
                        break;
                    }
                }
            }
        }
        else
            iq = null;
        return (ColibriConferenceIQ) iq;
    }

    /**
     * Parses using a specific <code>XmlPullParser</code> and ignores XML content presuming that the
     * specified <code>parser</code> is currently at the start tag of an element with a specific name
     * and throwing away until the end tag with the specified name is encountered.
     *
     * @param parser the <code>XmlPullParser</code> which parses the XML content
     * @param name the name of the element at the start tag of which the specified <code>parser</code> is
     * presumed to currently be and until the end tag of which XML content is to be thrown away
     * @throws IOException, XmlPullParserException if an errors occurs while parsing the XML content
     */
    private void throwAway(XmlPullParser parser, String name)
            throws IOException, XmlPullParserException
    {
        while ((XmlPullParser.Event.END_ELEMENT != parser.next()) || !name.equals(parser.getName()))
            ;
    }
}
