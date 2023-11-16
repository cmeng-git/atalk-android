/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.util.PortTracker;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.DefaultStreamConnector;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.util.MediaType;
import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.LocalCandidate;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import timber.log.Timber;

/**
 * <code>TransportManager</code>s are responsible for allocating ports, gathering local candidates and
 * managing ICE whenever we are using it.
 *
 * @param <U> the peer extension class like for example <code>CallPeerSipImpl</code> or <code>CallPeerJabberImpl</code>
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public abstract class TransportManager<U extends MediaAwareCallPeer<?, ?, ?>>
{
    /**
     * The port tracker that we should use when binding generic media streams.
     *
     * Initialized by {@link #initializePortNumbers()}.
     */
    private static final PortTracker defaultPortTracker = new PortTracker(5000, 6000);

    /**
     * The port tracker that we should use when binding video media streams.
     *
     * Potentially initialized by {@link #initializePortNumbers()} if the necessary properties are set.
     */
    private static PortTracker videoPortTracker;

    /**
     * The port tracker that we should use when binding data channels.
     *
     * Potentially initialized by {@link #initializePortNumbers()} if the necessary properties are set.
     */
    private static PortTracker dataPortTracker;

    /**
     * The port tracker that we should use when binding data media streams.
     *
     * Potentially initialized by {@link #initializePortNumbers()} if the necessary properties are set.
     */
    private static PortTracker audioPortTracker;

    /**
     * RTP audio DSCP configuration property name.
     */
    private static final String RTP_AUDIO_DSCP_PROPERTY = "protocol.RTP_AUDIO_DSCP";

    /**
     * RTP video DSCP configuration property name.
     */
    private static final String RTP_VIDEO_DSCP_PROPERTY = "protocol.RTP_VIDEO_DSCP";

    /**
     * Number of empty UDP packets to send for NAT hole punching.
     */
    private static final String HOLE_PUNCH_PKT_COUNT_PROPERTY = "protocol.HOLE_PUNCH_PKT_COUNT";

    /**
     * Number of empty UDP packets to send for NAT hole punching.
     */
    private static final int DEFAULT_HOLE_PUNCH_PKT_COUNT = 3;

    /**
     * Returns the port tracker that we are supposed to use when binding ports for the specified {@link MediaType}.
     *
     * @param mediaType the media type that we want to obtain the port tracker for. Use <code>null</code> to
     * obtain the default port tracker.
     * @return the port tracker that we are supposed to use when binding ports for the specified {@link MediaType}.
     */
    protected static PortTracker getPortTracker(MediaType mediaType)
    {
        // make sure our port numbers reflect the configuration service settings
        initializePortNumbers();

        if (mediaType != null) {
            switch (mediaType) {
                case AUDIO:
                    if (audioPortTracker != null)
                        return audioPortTracker;
                    else
                        break;
                case VIDEO:
                    if (videoPortTracker != null)
                        return videoPortTracker;
                    else
                        break;
                case DATA:
                    if (dataPortTracker != null)
                        return dataPortTracker;
                    else
                        break;
            }
        }
        return defaultPortTracker;
    }

    /**
     * Returns the port tracker that we are supposed to use when binding ports for the
     * {@link MediaType} indicated by the string param. If we do not recognize the string as a valid
     * media type, we simply return the default port tracker.
     *
     * @param mediaTypeStr the name of the media type that we want to obtain a port tracker for.
     * @return the port tracker that we are supposed to use when binding ports for the
     * {@link MediaType} with the specified name or the default tracker in case the name doesn't ring a bell.
     */
    protected static PortTracker getPortTracker(String mediaTypeStr)
    {
        try {
            return getPortTracker(MediaType.parseString(mediaTypeStr));
        } catch (Exception e) {
            Timber.i("Returning default port tracker for unrecognized media type: %s", mediaTypeStr);
            return defaultPortTracker;
        }
    }

    /**
     * The {@link MediaAwareCallPeer} whose traffic we will be taking care of.
     */
    private U callPeer;

    /**
     * The RTP/RTCP socket couples that this <code>TransportManager</code> uses to send and receive
     * media flows through indexed by <code>MediaType</code> (ordinal).
     */
    private final StreamConnector[] streamConnectors = new StreamConnector[MediaType.values().length];

    /**
     * Creates a new instance of this transport manager, binding it to the specified peer.
     *
     * @param callPeer the {@link MediaAwareCallPeer} whose traffic we will be taking care of.
     */
    protected TransportManager(U callPeer)
    {
        this.callPeer = callPeer;
    }

    /**
     * Returns the <code>StreamConnector</code> instance that this media handler should use for streams
     * of the specified <code>mediaType</code>. The method would also create a new
     * <code>StreamConnector</code> if no connector has been initialized for this <code>mediaType</code> yet
     * or in case one of its underlying sockets has been closed.
     *
     * @param mediaType the <code>MediaType</code> that we'd like to create a connector for.
     * @return this media handler's <code>StreamConnector</code> for the specified <code>mediaType</code>.
     * @throws OperationFailedException in case we failed to initialize our connector.
     */
    public StreamConnector getStreamConnector(MediaType mediaType)
            throws OperationFailedException
    {
        int streamConnectorIndex = mediaType.ordinal();
        StreamConnector streamConnector = streamConnectors[streamConnectorIndex];

        if ((streamConnector == null) || (streamConnector.getProtocol() == StreamConnector.Protocol.UDP)) {
            DatagramSocket controlSocket;

            if ((streamConnector == null) || streamConnector.getDataSocket().isClosed()
                    || (((controlSocket = streamConnector.getControlSocket()) != null)
                    && controlSocket.isClosed())) {
                streamConnectors[streamConnectorIndex] = streamConnector = createStreamConnector(mediaType);
            }
        }
        else if (streamConnector.getProtocol() == StreamConnector.Protocol.TCP) {
            Socket controlTCPSocket;

            if (streamConnector.getDataTCPSocket().isClosed()
                    || (((controlTCPSocket = streamConnector.getControlTCPSocket()) != null)
                    && controlTCPSocket.isClosed())) {
                streamConnectors[streamConnectorIndex] = streamConnector = createStreamConnector(mediaType);
            }
        }
        return streamConnector;
    }

    /**
     * Closes the existing <code>StreamConnector</code>, if any, associated with a specific
     * <code>MediaType</code> and removes its reference from this <code>TransportManager</code>.
     *
     * @param mediaType the <code>MediaType</code> associated with the <code>StreamConnector</code> to close
     */
    public void closeStreamConnector(MediaType mediaType)
    {
        int index = mediaType.ordinal();
        StreamConnector streamConnector = streamConnectors[index];

        if (streamConnector != null) {
            try {
                closeStreamConnector(mediaType, streamConnector);
            } catch (OperationFailedException e) {
                Timber.e(e, "Failed to close stream connector for %s", mediaType);
            }
            streamConnectors[index] = null;
        }
    }

    /**
     * Closes a specific <code>StreamConnector</code> associated with a specific <code>MediaType</code>. If
     * this <code>TransportManager</code> has a reference to the specified <code>streamConnector</code>, it
     * remains. Allows extenders to override and perform additional customizations to the closing of
     * the specified <code>streamConnector</code>.
     *
     * @param mediaType the <code>MediaType</code> associated with the specified <code>streamConnector</code>
     * @param streamConnector the <code>StreamConnector</code> to be closed @see #closeStreamConnector(MediaType)
     */
    protected void closeStreamConnector(MediaType mediaType, StreamConnector streamConnector)
            throws OperationFailedException
    {
        /*
         * XXX The connected owns the sockets so it is important that it decides whether to close
         * them i.e. this TransportManager is not allowed to explicitly close the sockets by itself.
         */
        streamConnector.close();
    }

    /**
     * Creates a media <code>StreamConnector</code> for a stream of a specific <code>MediaType</code>. The
     * minimum and maximum of the media port boundaries are taken into account.
     *
     * @param mediaType the <code>MediaType</code> of the stream for which a <code>StreamConnector</code> is to be created
     * @return a <code>StreamConnector</code> for the stream of the specified <code>mediaType</code>
     * @throws OperationFailedException if the binding of the sockets fails
     */
    protected StreamConnector createStreamConnector(MediaType mediaType)
            throws OperationFailedException
    {
        NetworkAddressManagerService nam = ProtocolMediaActivator.getNetworkAddressManagerService();
        InetAddress intendedDestination = getIntendedDestination(getCallPeer());
        InetAddress localHostForPeer = nam.getLocalHost(intendedDestination);
        PortTracker portTracker = getPortTracker(mediaType);

        // create the RTP socket.
        DatagramSocket rtpSocket = createDatagramSocket(localHostForPeer, portTracker);

        // create the RTCP socket, preferably on the port following our RTP one.
        DatagramSocket rtcpSocket = createDatagramSocket(localHostForPeer, portTracker);
        return new DefaultStreamConnector(rtpSocket, rtcpSocket);
    }

    /**
     * Creates <code>DatagramSocket</code> bind to <code>localHostForPeer</code>,
     * used the port numbers provided by <code>portTracker</code> and update it with
     * the result socket port so we do not try to bind to occupied ports.
     *
     * @param portTracker the port tracker.
     * @param localHostForPeer the address to bind to.
     * @return the newly created datagram socket.
     * @throws OperationFailedException if we fail to create the socket.
     */
    private DatagramSocket createDatagramSocket(InetAddress localHostForPeer, PortTracker portTracker)
            throws OperationFailedException
    {
        NetworkAddressManagerService nam = ProtocolMediaActivator.getNetworkAddressManagerService();

        //create the socket.
        DatagramSocket socket;
        try {
            socket = nam.createDatagramSocket(localHostForPeer, portTracker.getPort(),
                    portTracker.getMinPort(), portTracker.getMaxPort());
        } catch (Exception exc) {
            throw new OperationFailedException("Failed to allocate the network ports necessary for the call.",
                    OperationFailedException.INTERNAL_ERROR, exc);
        }
        //make sure that next time we don't try to bind on occupied ports
        portTracker.setNextPort(socket.getLocalPort() + 1);
        return socket;
    }

    /**
     * Tries to set the ranges of the <code>PortTracker</code>s (e.g. default, audio, video, data
     * channel) to the values specified in the <code>ConfigurationService</code>.
     */
    protected synchronized static void initializePortNumbers()
    {
        // try the default tracker first
        ConfigurationService cfg = ProtocolMediaActivator.getConfigurationService();
        String minPort, maxPort;

        minPort = cfg.getString(OperationSetBasicTelephony.MIN_MEDIA_PORT_NUMBER_PROPERTY_NAME);
        if (minPort != null) {
            maxPort = cfg.getString(OperationSetBasicTelephony.MAX_MEDIA_PORT_NUMBER_PROPERTY_NAME);
            if (maxPort != null) {
                // Try the specified range; otherwise, leave the tracker as it is: [5000, 6000].
                defaultPortTracker.tryRange(minPort, maxPort);
            }
        }

        // try the VIDEO tracker
        minPort = cfg.getString(OperationSetBasicTelephony.MIN_VIDEO_PORT_NUMBER_PROPERTY_NAME);
        if (minPort != null) {
            maxPort = cfg.getString(OperationSetBasicTelephony.MAX_VIDEO_PORT_NUMBER_PROPERTY_NAME);
            if (maxPort != null) {
                // Try the specified range; otherwise, leave the tracker to null.
                if (videoPortTracker == null) {
                    videoPortTracker = PortTracker.createTracker(minPort, maxPort);
                }
                else {
                    videoPortTracker.tryRange(minPort, maxPort);
                }
            }
        }

        // try the AUDIO tracker
        minPort = cfg.getString(OperationSetBasicTelephony.MIN_AUDIO_PORT_NUMBER_PROPERTY_NAME);
        if (minPort != null) {
            maxPort = cfg.getString(OperationSetBasicTelephony.MAX_AUDIO_PORT_NUMBER_PROPERTY_NAME);
            if (maxPort != null) {
                // Try the specified range; otherwise, leave the tracker to null.
                if (audioPortTracker == null) {
                    audioPortTracker = PortTracker.createTracker(minPort, maxPort);
                }
                else {
                    audioPortTracker.tryRange(minPort, maxPort);
                }
            }
        }

        // try the DATA CHANNEL tracker
        minPort = cfg.getString(OperationSetBasicTelephony.MIN_DATA_CHANNEL_PORT_NUMBER_PROPERTY_NAME);
        if (minPort != null) {
            maxPort = cfg.getString(OperationSetBasicTelephony.MAX_DATA_CHANNEL_PORT_NUMBER_PROPERTY_NAME);
            if (maxPort != null) {
                // Try the specified range; otherwise, leave the tracker to null.
                if (dataPortTracker == null) {
                    dataPortTracker = PortTracker.createTracker(minPort, maxPort);
                }
                else {
                    dataPortTracker.tryRange(minPort, maxPort);
                }
            }
        }
    }

    /**
     * Returns the <code>InetAddress</code> that we are using in one of our <code>StreamConnector</code>s
     * or, in case we don't have any connectors yet the address returned by the our network address
     * manager as the best local address to use when contacting the <code>CallPeer</code> associated
     * with this <code>MediaHandler</code>. This method is primarily meant for use with the o= and c=
     * fields of a newly created session description. The point is that we create our
     * <code>StreamConnector</code>s when constructing the media descriptions so we already have a
     * specific local address assigned to them at the time we get ready to create the c= and o=
     * fields. It is therefore better to try and return one of these addresses before trying the net
     * address manager again and running the slight risk of getting a different address.
     *
     * @return an <code>InetAddress</code> that we use in one of the <code>StreamConnector</code>s in this class.
     */
    public InetAddress getLastUsedLocalHost()
    {
        for (MediaType mediaType : MediaType.values()) {
            StreamConnector streamConnector = streamConnectors[mediaType.ordinal()];

            if (streamConnector != null)
                return streamConnector.getDataSocket().getLocalAddress();
        }

        NetworkAddressManagerService nam = ProtocolMediaActivator.getNetworkAddressManagerService();
        InetAddress intendedDestination = getIntendedDestination(getCallPeer());
        return nam.getLocalHost(intendedDestination);
    }

    /**
     * Sends empty UDP packets to target destination data/control ports in order
     * to open ports on NATs or and help RTP proxies latch onto our RTP ports.
     *
     * @param target <code>MediaStreamTarget</code>
     * @param type the {@link MediaType} of the connector we'd like to send the hole punching packet through.
     */
    public void sendHolePunchPacket(MediaStreamTarget target, MediaType type)
    {
        this.sendHolePunchPacket(target, type, null);
    }

    /**
     * Sends empty UDP packets to target destination data/control ports in order
     * to open ports on NATs or/and help RTP proxies latch onto our RTP ports.
     *
     * @param target <code>MediaStreamTarget</code>
     * @param type the {@link MediaType} of the connector we'd like to send the hole punching packet through.
     * @param packet (optional) use a pre-generated packet that will be sent
     */
    public void sendHolePunchPacket(MediaStreamTarget target, MediaType type, RawPacket packet)
    {
        // target may have been closed by remote action
        if (target == null)
            return;

        // check how many hole punch packets we would be supposed to send:
        int packetCount = ProtocolMediaActivator.getConfigurationService()
                .getInt(HOLE_PUNCH_PKT_COUNT_PROPERTY, DEFAULT_HOLE_PUNCH_PKT_COUNT);

        if (packetCount < 0)
            packetCount = DEFAULT_HOLE_PUNCH_PKT_COUNT;
        if (packetCount == 0)
            return;

        Timber.i("Send NAT hole punch packets to port for media: %s", type.name());
        try {
            final StreamConnector connector = getStreamConnector(type);
            if (connector.getProtocol() == StreamConnector.Protocol.TCP)
                return;

            byte[] buf;
            if (packet != null)
                buf = packet.getBuffer();
            else
                buf = new byte[0];

            synchronized (connector) {
                // we may want to send more than one packet in case they get lost
                for (int i = 0; i < packetCount; i++) {
                    DatagramSocket socket;

                    // data/RTP
                    if ((socket = connector.getDataSocket()) != null) {
                        InetSocketAddress dataAddress = target.getDataAddress();
                        // Timber.e(new Exception(), "Send Hole Punch Packet for media: %s; %s", type.name(), target);
                        socket.send(new DatagramPacket(buf, buf.length, dataAddress.getAddress(), dataAddress.getPort()));
                    }

                    // control/RTCP
                    if ((socket = connector.getControlSocket()) != null) {
                        InetSocketAddress controlAddress = target.getControlAddress();
                        socket.send(new DatagramPacket(buf, buf.length, controlAddress.getAddress(), controlAddress.getPort()));
                    }
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Error in sending remote peer for media: %s; %s", type.name(), target);
        }
    }

    /**
     * Set traffic class (QoS) for the RTP socket.
     *
     * @param target <code>MediaStreamTarget</code>
     * @param type the {@link MediaType} of the connector we'd like to set traffic class
     */
    protected void setTrafficClass(MediaStreamTarget target, MediaType type)
    {
        // get traffic class value for RTP audio/video
        int trafficClass = getDSCP(type);
        if (trafficClass <= 0)
            return;

        Timber.i("Set traffic class for %s to %s", type, trafficClass);
        try {
            StreamConnector connector = getStreamConnector(type);

            synchronized (connector) {
                if (connector.getProtocol() == StreamConnector.Protocol.TCP) {
                    connector.getDataTCPSocket().setTrafficClass(trafficClass);

                    Socket controlTCPSocket = connector.getControlTCPSocket();
                    if (controlTCPSocket != null)
                        controlTCPSocket.setTrafficClass(trafficClass);
                }
                else {
                    /* data port (RTP) */
                    connector.getDataSocket().setTrafficClass(trafficClass);

                    /* control port (RTCP) */
                    DatagramSocket controlSocket = connector.getControlSocket();
                    if (controlSocket != null)
                        controlSocket.setTrafficClass(trafficClass);
                }
            }
        } catch (Exception e) {
            Timber.e(e, "Failed to set traffic class for %s to %s", type, trafficClass);
        }
    }

    /**
     * Gets the SIP traffic class associated with a specific <code>MediaType</code> from the configuration.
     *
     * @param type the <code>MediaType</code> to get the associated SIP traffic class of
     * @return the SIP traffic class associated with the specified <code>MediaType</code> or <code>0</code>
     * if not configured
     */
    private int getDSCP(MediaType type)
    {
        String dscpPropertyName;
        switch (type) {
            case AUDIO:
                dscpPropertyName = RTP_AUDIO_DSCP_PROPERTY;
                break;
            case VIDEO:
                dscpPropertyName = RTP_VIDEO_DSCP_PROPERTY;
                break;
            default:
                dscpPropertyName = null;
                break;
        }

        return (dscpPropertyName == null)
                ? 0 : (ProtocolMediaActivator.getConfigurationService().getInt(dscpPropertyName, 0) << 2);
    }

    /**
     * Returns the <code>InetAddress</code> that is most likely to be used as a next hop when contacting
     * the specified <code>destination</code>. This is an utility method that is used whenever we have
     * to choose one of our local addresses to put in the Via, Contact or (in the case of no
     * registrar accounts) From headers.
     *
     * @param peer the CallPeer that we would contact.
     * @return the <code>InetAddress</code> that is most likely to be to be used as a next hop when
     * contacting the specified <code>destination</code>.
     * @throws IllegalArgumentException if <code>destination</code> is not a valid host/ip/fqdn
     */
    protected abstract InetAddress getIntendedDestination(U peer);

    /**
     * Returns the {@link MediaAwareCallPeer} that this transport manager is serving.
     *
     * @return the {@link MediaAwareCallPeer} that this transport manager is serving.
     */
    public U getCallPeer()
    {
        return callPeer;
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return The extended type of the candidate selected if this transport manager is using ICE.
     * Otherwise, returns null.
     */
    public abstract String getICECandidateExtendedType(String streamName);

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing if this transport manager is using ICE. Otherwise, returns null.
     */
    public abstract String getICEState();

    /**
     * Returns the ICE local host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local host address if this transport manager is using ICE. Otherwise, returns null.
     */
    public abstract InetSocketAddress getICELocalHostAddress(String streamName);

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise, returns null.
     */
    public abstract InetSocketAddress getICERemoteHostAddress(String streamName);

    /**
     * Returns the ICE local reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the local candidate used.
     */
    public abstract InetSocketAddress getICELocalReflexiveAddress(String streamName);

    /**
     * Returns the ICE remote reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the remote candidate used.
     */
    public abstract InetSocketAddress getICERemoteReflexiveAddress(String streamName);

    /**
     * Returns the ICE local relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local relayed address. May be null if this transport manager is not using ICE
     * or if there is no relayed address for the local candidate used.
     */
    public abstract InetSocketAddress getICELocalRelayedAddress(String streamName);

    /**
     * Returns the ICE remote relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote relayed address. May be null if this transport manager is not using
     * ICE or if there is no relayed address for the remote candidate used.
     */
    public abstract InetSocketAddress getICERemoteRelayedAddress(String streamName);

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is null,
     * or if the agent has nevers harvested.
     */
    public abstract long getTotalHarvestingTime();

    /**
     * Returns the harvesting time (in ms) for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The harvesting time (in ms) for the harvester given in parameter. 0 if this harvester
     * does not exists, if the ICE agent is null, or if the agent has never harvested with this harvester.
     */
    public abstract long getHarvestingTime(String harvesterName);

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    public abstract int getNbHarvesting();

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The number of harvesting time for the harvester given in parameter.
     */
    public abstract int getNbHarvesting(String harvesterName);

    /**
     * Returns the ICE candidate extended type selected by the given agent.
     *
     * @param iceAgent The ICE agent managing the ICE offer/answer exchange, collecting and selecting the candidate.
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return The ICE candidate extended type selected by the given agent. null if the iceAgent is
     * null or if there is no candidate selected or available.
     */
    public static String getICECandidateExtendedType(Agent iceAgent, String streamName)
    {
        if (iceAgent != null) {
            LocalCandidate localCandidate = iceAgent.getSelectedLocalCandidate(streamName);

            if (localCandidate != null)
                return localCandidate.getExtendedType().toString();
        }
        return null;
    }

    /**
     * Creates the ICE agent that we would be using in this transport manager for all negotiation.
     *
     * @return the ICE agent to use for all the ICE negotiation that this transport manager would be going through
     */
    protected Agent createIceAgent()
    {
        // work in progress
        return null;
    }

    /**
     * Creates an {@link IceMediaStream} with the specified <code>media</code> name.
     *
     * @param media the name of the stream we'd like to create.
     * @param agent the ICE {@link Agent} that we will be appending the stream to.
     * @return the newly created {@link IceMediaStream}
     * @throws OperationFailedException if binding on the specified media stream fails for some reason.
     */
    protected IceMediaStream createIceStream(String media, Agent agent)
            throws OperationFailedException
    {
        return null;
    }
}
