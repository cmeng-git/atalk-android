/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.colibri;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.util.StringUtils;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.*;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.jid.parts.Localpart;
import org.xmpp.extensions.DefaultExtensionElementProvider;
import org.xmpp.extensions.jingle.*;
import org.xmpp.extensions.jitsimeet.SSRCInfoExtensionElement;

import java.io.IOException;

import timber.log.Timber;

/**
 * Implements an <tt>org.jivesoftware.smack.provider.IQProvider</tt> for the Jitsi Videobridge
 * extension <tt>ColibriConferenceIQ</tt>.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class ColibriIQProvider extends IQProvider<ColibriConferenceIQ>
{
    /**
     * Initializes a new <tt>ColibriIQProvider</tt> instance.
     */
    public ColibriIQProvider()
    {
        ProviderManager.addExtensionProvider(
                PayloadTypeExtensionElement.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                new DefaultExtensionElementProvider<>(PayloadTypeExtensionElement.class));

        ProviderManager.addExtensionProvider(
                RtcpFbExtensionElement.ELEMENT, RtcpFbExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(RtcpFbExtensionElement.class));

        ProviderManager.addExtensionProvider(
                RTPHdrExtExtensionElement.ELEMENT, ColibriConferenceIQ.NAMESPACE,
                new DefaultExtensionElementProvider<>(RTPHdrExtExtensionElement.class));

        ProviderManager.addExtensionProvider(
                SourceExtensionElement.ELEMENT_NAME, SourceExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(SourceExtensionElement.class));

        ProviderManager.addExtensionProvider(
                SourceGroupExtensionElement.ELEMENT_NAME, SourceGroupExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(SourceGroupExtensionElement.class));

        ProviderManager.addExtensionProvider(
                SourceRidGroupExtensionElement.ELEMENT_NAME, SourceRidGroupExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(SourceRidGroupExtensionElement.class));

        ExtensionElementProvider<ParameterExtensionElement> parameterPacketExtension
                = new DefaultExtensionElementProvider<>(ParameterExtensionElement.class);

        ProviderManager.addExtensionProvider(
                ParameterExtensionElement.ELEMENT, ColibriConferenceIQ.NAMESPACE, parameterPacketExtension);

        ProviderManager.addExtensionProvider(
                ParameterExtensionElement.ELEMENT, SourceExtensionElement.NAMESPACE, parameterPacketExtension);

        // Shutdown IQ
        ProviderManager.addIQProvider(ShutdownIQ.GRACEFUL_ELEMENT_NAME, ShutdownIQ.NAMESPACE, this);
        ProviderManager.addIQProvider(ShutdownIQ.FORCE_ELEMENT_NAME, ShutdownIQ.NAMESPACE, this);

        // Shutdown extension
        ExtensionElementProvider shutdownProvider
                = new DefaultExtensionElementProvider<>(ColibriConferenceIQ.GracefulShutdown.class);
        ProviderManager.addExtensionProvider(
                ColibriConferenceIQ.GracefulShutdown.ELEMENT_NAME, ColibriConferenceIQ.GracefulShutdown.NAMESPACE,
                shutdownProvider);

        // ColibriStatsIQ
        ProviderManager.addIQProvider(ColibriStatsIQ.ELEMENT_NAME, ColibriStatsIQ.NAMESPACE, this);

        // ColibriStatsExtensionElement
        ExtensionElementProvider statsProvider
                = new DefaultExtensionElementProvider<>(ColibriStatsExtensionElement.class);
        ProviderManager.addExtensionProvider(
                ColibriStatsExtensionElement.ELEMENT_NAME, ColibriStatsExtensionElement.NAMESPACE, statsProvider);

        // ColibriStatsExtensionElement.Stat
        ExtensionElementProvider statProvider
                = new DefaultExtensionElementProvider<>(ColibriStatsExtensionElement.Stat.class);
        ProviderManager.addExtensionProvider(
                ColibriStatsExtensionElement.Stat.ELEMENT_NAME, ColibriStatsExtensionElement.NAMESPACE, statProvider);

        // ssrc-info
        ProviderManager.addExtensionProvider(
                SSRCInfoExtensionElement.ELEMENT, SSRCInfoExtensionElement.NAMESPACE,
                new DefaultExtensionElementProvider<>(SSRCInfoExtensionElement.class));
    }

    private void addChildExtension(ColibriConferenceIQ.Channel channel, ExtensionElement childExtension)
    {
        if (childExtension instanceof PayloadTypeExtensionElement) {
            PayloadTypeExtensionElement payloadType = (PayloadTypeExtensionElement) childExtension;
            if ("opus".equals(payloadType.getName()) && (payloadType.getChannels() != 2)) {
                /*
                 * We only have a Format for opus with 2 channels, because it MUST be advertised
                 * with 2 channels. Fixing the number of channels here allows us to be compatible
                 * with agents who advertise it with 1 channel.
                 */
                payloadType.setChannels(2);
            }
            channel.addPayloadType(payloadType);
        }
        else if (childExtension instanceof IceUdpTransportExtensionElement) {
            IceUdpTransportExtensionElement transport = (IceUdpTransportExtensionElement) childExtension;
            channel.setTransport(transport);
        }
        else if (childExtension instanceof SourceExtensionElement) {
            channel.addSource((SourceExtensionElement) childExtension);
        }
        else if (childExtension instanceof SourceGroupExtensionElement) {
            SourceGroupExtensionElement sourceGroup = (SourceGroupExtensionElement) childExtension;
            channel.addSourceGroup(sourceGroup);
        }
        else if (childExtension instanceof RTPHdrExtExtensionElement) {
            RTPHdrExtExtensionElement rtpHdrExtPacketExtension = (RTPHdrExtExtensionElement) childExtension;
            channel.addRtpHeaderExtension(rtpHdrExtPacketExtension);
        }
        else {
            Timber.e("Ignoring a child of 'channel' of unknown type: %s", childExtension);
        }
    }

    private void addChildExtension(ColibriConferenceIQ.ChannelBundle bundle, ExtensionElement childExtension)
    {
        if (childExtension instanceof IceUdpTransportExtensionElement) {
            IceUdpTransportExtensionElement transport = (IceUdpTransportExtensionElement) childExtension;
            bundle.setTransport(transport);
        }
    }

    private void addChildExtension(ColibriConferenceIQ.SctpConnection sctpConnection,
            ExtensionElement childExtension)
    {
        if (childExtension instanceof IceUdpTransportExtensionElement) {
            IceUdpTransportExtensionElement transport = (IceUdpTransportExtensionElement) childExtension;
            sctpConnection.setTransport(transport);
        }
    }

    private ExtensionElement parseExtension(XmlPullParser parser, String name, String namespace)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        ExtensionElementProvider extensionProvider = ProviderManager.getExtensionProvider(name, namespace);
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
            extension = (ExtensionElement) extensionProvider.parse(parser);
        }
        return extension;
    }

    /**
     * Parses an IQ sub-document and creates an <tt>org.jivesoftware.smack.packet.IQ</tt> instance.
     *
     * @param parser an <tt>XmlPullParser</tt> which specifies the IQ sub-document to be parsed into a new
     * <tt>IQ</tt> instance
     * @return a new <tt>IQ</tt> instance parsed from the specified IQ sub-document
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
                if (!StringUtils.isNullOrEmpty(conferenceName))
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
                        else if (ColibriConferenceIQ.Channel.ELEMENT_NAME.equals(name)) {
                            content.addChannel(channel);
                            channel = null;
                        }
                        else if (ColibriConferenceIQ.SctpConnection.ELEMENT_NAME.equals(name)) {
                            if (sctpConnection != null)
                                content.addSctpConnection(sctpConnection);
                            sctpConnection = null;
                        }
                        else if (ColibriConferenceIQ.ChannelBundle.ELEMENT_NAME.equals(name)) {
                            if (bundle != null) {
                                if (conferenceIQ.addChannelBundle(bundle) != null) {
                                    Timber.w("Replacing a channel-bundle with the same ID (not a valid Colibri packet).");
                                }
                                bundle = null;
                            }
                        }
                        else if (ColibriConferenceIQ.Endpoint.ELEMENT_NAME.equals(name)) {
                            if (conferenceIQ.addEndpoint(conferenceEndpoint) != null) {
                                Timber.w("Replacing an endpoint element with the same ID (not a valid Colibri packet).");
                            }
                            conferenceEndpoint = null;
                        }
                        else if (ColibriConferenceIQ.Channel.SSRC_ELEMENT_NAME.equals(name)) {
                            String s = ssrc.toString().trim();
                            if (s.length() != 0) {
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
                        else if (ColibriConferenceIQ.Content.ELEMENT_NAME.equals(name)) {
                            conferenceIQ.addContent(content);
                            content = null;
                        }
                        else if (ColibriConferenceIQ.RTCPTerminationStrategy.ELEMENT_NAME.equals(name)) {
                            conferenceIQ.setRTCPTerminationStrategy(rtcpTerminationStrategy);
                            rtcpTerminationStrategy = null;
                        }
                        else if (ColibriConferenceIQ.Recording.ELEMENT_NAME.equals(name)) {
                            conferenceIQ.setRecording(recording);
                            recording = null;
                        }
                        else if (ColibriConferenceIQ.GracefulShutdown.ELEMENT_NAME.equals(name)) {
                            conferenceIQ.setGracefulShutdown(true);
                        }
                        break;
                    }

                    case START_ELEMENT: {
                        String name = parser.getName();

                        if (ColibriConferenceIQ.Channel.ELEMENT_NAME.equals(name)) {
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
                            if (!StringUtils.isNullOrEmpty(direction)) {
                                channel.setDirection(MediaDirection.fromString(direction));
                            }

                            // endpoint
                            String endpoint = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.ENDPOINT_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(endpoint)) {
                                channel.setEndpoint(endpoint);
                            }

                            String channelBundleId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelCommon.CHANNEL_BUNDLE_ID_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(channelBundleId)) {
                                channel.setChannelBundleId(channelBundleId);
                            }

                            // expire
                            String expire = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.EXPIRE_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(expire)) {
                                channel.setExpire(Integer.parseInt(expire));
                            }

                            // packetDelay
                            String packetDelay = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.PACKET_DELAY_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(packetDelay)) {
                                channel.setPacketDelay(Integer.parseInt(packetDelay));
                            }

                            // host
                            String host = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.HOST_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(host)) {
                                channel.setHost(host);
                            }

                            // id
                            String channelID = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.ID_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(channelID)) {
                                channel.setID(channelID);
                            }

                            // initiator
                            String initiator = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.INITIATOR_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(initiator)) {
                                channel.setInitiator(Boolean.valueOf(initiator));
                            }

                            // lastN
                            String lastN = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.LAST_N_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(lastN)) {
                                channel.setLastN(Integer.parseInt(lastN));
                            }

                            // simulcastMode
                            String simulcastMode = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.SIMULCAST_MODE_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(simulcastMode)) {
                                channel.setSimulcastMode(SimulcastMode.fromString(simulcastMode));
                            }

                            // receiving simulcast layer
                            String receivingSimulcastLayer = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RECEIVING_SIMULCAST_LAYER);
                            if (!StringUtils.isNullOrEmpty(receivingSimulcastLayer)) {
                                channel.setReceivingSimulcastLayer(Integer.parseInt(receivingSimulcastLayer));
                            }

                            // rtcpPort
                            String rtcpPort = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RTCP_PORT_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(rtcpPort)) {
                                channel.setRTCPPort(Integer.parseInt(rtcpPort));
                            }

                            // rtpLevelRelayType
                            String rtpLevelRelayType = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RTP_LEVEL_RELAY_TYPE_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(rtpLevelRelayType)) {
                                channel.setRTPLevelRelayType(rtpLevelRelayType);
                            }

                            // rtpPort
                            String rtpPort = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Channel.RTP_PORT_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(rtpPort)) {
                                channel.setRTPPort(Integer.parseInt(rtpPort));
                            }
                        }
                        else if (ColibriConferenceIQ.ChannelBundle.ELEMENT_NAME.equals(name)) {
                            String bundleId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelBundle.ID_ATTR_NAME);

                            if (!StringUtils.isNullOrEmpty(bundleId)) {
                                bundle = new ColibriConferenceIQ.ChannelBundle(bundleId);
                            }
                        }
                        else if (ColibriConferenceIQ.RTCPTerminationStrategy.ELEMENT_NAME.equals(name)) {
                            rtcpTerminationStrategy = new ColibriConferenceIQ.RTCPTerminationStrategy();

                            // name
                            String strategyName = parser.getAttributeValue("",
                                    ColibriConferenceIQ.RTCPTerminationStrategy.NAME_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(strategyName)) {
                                rtcpTerminationStrategy.setName(strategyName);
                            }
                        }
                        else if (ColibriConferenceIQ.OctoChannel.RELAY_ELEMENT_NAME.equals(name)) {
                            String id = parser.getAttributeValue("",
                                    ColibriConferenceIQ.OctoChannel.RELAY_ID_ATTR_NAME);
                            if (id != null
                                    && channel instanceof ColibriConferenceIQ.OctoChannel) {
                                ((ColibriConferenceIQ.OctoChannel) channel).addRelay(id);
                            }
                        }
                        else if (ColibriConferenceIQ.Channel.SSRC_ELEMENT_NAME.equals(name)) {
                            ssrc = new StringBuilder();
                        }
                        else if (ColibriConferenceIQ.Content.ELEMENT_NAME.equals(name)) {
                            content = new ColibriConferenceIQ.Content();

                            String contentName = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Content.NAME_ATTR_NAME);
                            if ((contentName != null) && (contentName.length() != 0))
                                content.setName(contentName);
                        }
                        else if (ColibriConferenceIQ.Recording.ELEMENT_NAME.equals(name)) {
                            String stateStr = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Recording.STATE_ATTR_NAME);

                            String token = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Recording.TOKEN_ATTR_NAME);

                            recording = new ColibriConferenceIQ.Recording(stateStr, token);
                        }
                        else if (ColibriConferenceIQ.SctpConnection.ELEMENT_NAME.equals(name)) {
                            // Endpoint
                            String endpoint = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.ENDPOINT_ATTR_NAME);

                            // id
                            String connID = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelCommon.ID_ATTR_NAME);

                            if (StringUtils.isNullOrEmpty(connID) && StringUtils.isNullOrEmpty(endpoint)) {
                                sctpConnection = null;
                                continue;
                            }

                            sctpConnection = new ColibriConferenceIQ.SctpConnection();
                            if (!StringUtils.isNullOrEmpty(connID))
                                sctpConnection.setID(connID);

                            if (!StringUtils.isNullOrEmpty(endpoint)) {
                                sctpConnection.setEndpoint(endpoint);
                            }

                            // port
                            String port = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.PORT_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(port))
                                sctpConnection.setPort(Integer.parseInt(port));

                            String channelBundleId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.ChannelCommon.CHANNEL_BUNDLE_ID_ATTR_NAME);
                            if (!StringUtils.isNullOrEmpty(channelBundleId))
                                sctpConnection.setChannelBundleId(channelBundleId);

                            // initiator
                            String initiator = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.INITIATOR_ATTR_NAME);

                            if (!StringUtils.isNullOrEmpty(initiator))
                                sctpConnection.setInitiator(Boolean.valueOf(initiator));

                            // expire
                            String expire = parser.getAttributeValue("",
                                    ColibriConferenceIQ.SctpConnection.EXPIRE_ATTR_NAME);

                            if (!StringUtils.isNullOrEmpty(expire))
                                sctpConnection.setExpire(Integer.parseInt(expire));
                        }
                        else if (ColibriConferenceIQ.Endpoint.ELEMENT_NAME.equals(name)) {
                            String id = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Endpoint.ID_ATTR_NAME);

                            String displayName = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Endpoint.DISPLAYNAME_ATTR_NAME);

                            String statsId = parser.getAttributeValue("",
                                    ColibriConferenceIQ.Endpoint.STATS_ID_ATTR_NAME);

                            if (!StringUtils.isNullOrEmpty(id)) {
                                conferenceEndpoint = new ColibriConferenceIQ.Endpoint(id, statsId, displayName);
                            }
                        }
                        else if (channel != null || sctpConnection != null || bundle != null) {
                            String peName = null;
                            String peNamespace = null;

                            if (IceUdpTransportExtensionElement.ELEMENT.equals(name)
                                    && IceUdpTransportExtensionElement.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = IceUdpTransportExtensionElement.NAMESPACE;
                            }
                            else if (PayloadTypeExtensionElement.ELEMENT.equals(name)) {
                                /*
                                 * The channel element of the Jitsi Videobridge protocol reuses the
                                 * payload-type element defined in XEP-0167: Jingle RTP Sessions.
                                 */
                                peName = name;
                                peNamespace = namespace;
                            }
                            else if (RtcpFbExtensionElement.ELEMENT.equals(name)
                                    && RtcpFbExtensionElement.NAMESPACE.equals(parser.getNamespace())) {
                                /*
                                 * The channel element of the Jitsi Videobridge protocol reuses the
                                 * payload-type element defined in XEP-0167: Jingle RTP Sessions.
                                 */
                                peName = name;
                                peNamespace = namespace;
                            }
                            else if (RTPHdrExtExtensionElement.ELEMENT.equals(name)) {
                                /*
                                 * The channel element of the Jitsi Videobridge protocol reuses the
                                 * rtp-hdrext element defined in XEP-0167: Jingle RTP Sessions.
                                 */
                                peName = name;
                                peNamespace = namespace;
                            }
                            else if (RawUdpTransportExtensionElement.ELEMENT.equals(name)
                                    && RawUdpTransportExtensionElement.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = RawUdpTransportExtensionElement.NAMESPACE;
                            }
                            else if (SourceExtensionElement.ELEMENT_NAME.equals(name)
                                    && SourceExtensionElement.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = SourceExtensionElement.NAMESPACE;
                            }
                            else if (SourceGroupExtensionElement.ELEMENT_NAME.equals(name)
                                    && SourceGroupExtensionElement.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = SourceGroupExtensionElement.NAMESPACE;
                            }
                            else if (SourceRidGroupExtensionElement.ELEMENT_NAME.equals(name)
                                    && SourceRidGroupExtensionElement.NAMESPACE.equals(parser.getNamespace())) {
                                peName = name;
                                peNamespace = SourceRidGroupExtensionElement.NAMESPACE;
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
        else if (ColibriStatsIQ.ELEMENT_NAME.equals(parser.getName()) && ColibriStatsIQ.NAMESPACE.equals(namespace)) {
            String rootElement = parser.getName();
            ColibriStatsIQ statsIQ = new ColibriStatsIQ();
            iq = statsIQ;
            ColibriStatsExtensionElement.Stat stat = null;

            boolean done = false;
            while (!done) {
                switch (parser.next()) {
                    case START_ELEMENT: {
                        String name = parser.getName();

                        if (ColibriStatsExtensionElement.Stat.ELEMENT_NAME.equals(name)) {
                            stat = new ColibriStatsExtensionElement.Stat();

                            String statName = parser.getAttributeValue("",
                                    ColibriStatsExtensionElement.Stat.NAME_ATTR_NAME);
                            stat.setName(statName);

                            String statValue = parser.getAttributeValue("",
                                    ColibriStatsExtensionElement.Stat.VALUE_ATTR_NAME);
                            stat.setValue(statValue);
                        }
                        break;
                    }
                    case END_ELEMENT: {
                        String name = parser.getName();
                        if (rootElement.equals(name)) {
                            done = true;
                        }
                        else if (ColibriStatsExtensionElement.Stat.ELEMENT_NAME.equals(name)) {
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
     * Parses using a specific <tt>XmlPullParser</tt> and ignores XML content presuming that the
     * specified <tt>parser</tt> is currently at the start tag of an element with a specific name
     * and throwing away until the end tag with the specified name is encountered.
     *
     * @param parser the <tt>XmlPullParser</tt> which parses the XML content
     * @param name the name of the element at the start tag of which the specified <tt>parser</tt> is
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
