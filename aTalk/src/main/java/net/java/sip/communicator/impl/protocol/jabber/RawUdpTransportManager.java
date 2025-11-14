/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.OperationFailedException;

import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle_rtp.CandidateType;
import org.jivesoftware.smackx.jingle_rtp.JingleUtils;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransportCandidate;
import org.jivesoftware.smackx.jingle_rtp.element.RawUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;

/**
 * A {@link TransportManagerJabberImpl} implementation that would only gather a single candidate
 * pair (i.e. RTP and RTCP).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class RawUdpTransportManager extends TransportManagerJabberImpl {
    /**
     * The list of <code>JingleContent</code>s which represents the local counterpart of the
     * negotiation between the local and the remote peers.
     */
    private List<JingleContent> local;

    /**
     * The collection of <code>JingleContent</code>s which represents the remote counterpart of
     * the negotiation between the local and the remote peers.
     */
    private final List<Iterable<JingleContent>> remotes = new LinkedList<>();

    /**
     * Creates a new instance of this transport manager, binding it to the specified peer.
     *
     * @param callPeer the {@link CallPeer} whose traffic we will be taking care of.
     */
    public RawUdpTransportManager(CallPeerJabberImpl callPeer) {
        super(callPeer);
    }

    /**
     * {@inheritDoc}
     */
    protected XmlElement createTransport(String media)
            throws OperationFailedException {
        MediaType mediaType = MediaType.parseString(media);
        return createTransport(mediaType, getStreamConnector(mediaType));
    }

    /**
     * Creates a raw UDP transport element according to a specific <code>StreamConnector</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> which uses the specified
     * <code>connector</code> or <code>channel</code>
     * @param connector the <code>StreamConnector</code> to be described within the transport element
     *
     * @return a {@link RawUdpTransport} containing the RTP and RTCP candidates of the specified <code>connector</code>
     */
    private RawUdpTransport createTransport(MediaType mediaType, StreamConnector connector) {
        RawUdpTransport.Builder tpBuilder = RawUdpTransport.getBuilder();
        int generation = getCurrentGeneration();

        // create and add candidates that correspond to the stream connector
        //=== RTP ===/
        DatagramSocket dataSocket = connector.getDataSocket();
        IceUdpTransportCandidate.Builder tcpBuilder = IceUdpTransportCandidate.getBuilder();
        tcpBuilder.setComponent(IceUdpTransportCandidate.RTP_COMPONENT_ID)
                .setGeneration(generation)
                .setID(getNextID())
                .setType(CandidateType.host)
                .setIP(dataSocket.getLocalAddress().getHostAddress())
                .setPort(dataSocket.getLocalPort());
        tpBuilder.addChildElement(tcpBuilder.build());

        //=== RTCP ===/
        DatagramSocket controlSocket = connector.getControlSocket();
        IceUdpTransportCandidate.Builder rtcpBuilder = IceUdpTransportCandidate.getBuilder()
                .setComponent(IceUdpTransportCandidate.RTCP_COMPONENT_ID)
                .setGeneration(generation)
                .setID(getNextID())
                .setType(CandidateType.host)
                .setIP(controlSocket.getLocalAddress().getHostAddress())
                .setPort(controlSocket.getLocalPort());
        tpBuilder.addChildElement(rtcpBuilder.build());

        return tpBuilder.build();
    }

    /**
     * {@inheritDoc}
     */
    protected XmlElement createTransportPacketExtension() {
        return new RawUdpTransport();
    }

    /**
     * Implements {@link TransportManagerJabberImpl#getStreamTarget(MediaType)}. Gets the
     * <code>MediaStreamTarget</code> to be used as the <code>target</code> of the <code>MediaStream</code> with
     * a specific <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> which is to have its
     * <code>target</code> set to the returned <code>MediaStreamTarget</code>
     *
     * @return the <code>MediaStreamTarget</code> to be used as the <code>target</code> of the
     * <code>MediaStream</code> with the specified <code>MediaType</code>
     *
     * @see TransportManagerJabberImpl#getStreamTarget(MediaType)
     */
    @Override
    public MediaStreamTarget getStreamTarget(MediaType mediaType) {
        ColibriConferenceIQ.Channel channel = getColibriChannel(mediaType, true /* local */);
        MediaStreamTarget streamTarget = null;

        if (channel == null) {
            String media = mediaType.toString();

            for (Iterable<JingleContent> remote : remotes) {
                for (JingleContent content : remote) {
                    RtpDescription rtpDescription
                            = content.getFirstChildElement(RtpDescription.class);

                    if (media.equals(rtpDescription.getMedia())) {
                        streamTarget = JingleUtils.extractDefaultTarget(content);
                        break;
                    }
                }
            }
        }
        else {
            IceUdpTransport transport = channel.getTransport();

            if (transport != null)
                streamTarget = JingleUtils.extractDefaultTarget(transport);
            if (streamTarget == null) {
                /*
                 * For the purposes of compatibility with legacy Jitsi Videobridge, support the
                 * channel attributes host, rtpPort and rtcpPort.
                 */
                @SuppressWarnings("deprecation")
                String host = channel.getHost();

                if (host != null) {
                    @SuppressWarnings("deprecation")
                    int rtpPort = channel.getRTPPort();
                    @SuppressWarnings("deprecation")
                    int rtcpPort = channel.getRTCPPort();

                    streamTarget = new MediaStreamTarget(new InetSocketAddress(host, rtpPort),
                            new InetSocketAddress(host, rtcpPort));
                }
            }
        }
        return streamTarget;
    }

    /**
     * Implements {@link TransportManagerJabberImpl#getXmlNamespace()}. Gets the XML namespace of
     * the Jingle transport implemented by this <code>TransportManagerJabberImpl</code>.
     *
     * @return the XML namespace of the Jingle transport implemented by this <code>TransportManagerJabberImpl</code>
     *
     * @see TransportManagerJabberImpl#getXmlNamespace()
     */
    @Override
    public String getXmlNamespace() {
        return ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0;
    }

    /**
     * Removes a content with a specific name from the transport-related part of the session
     * represented by this <code>TransportManagerJabberImpl</code> which may have been reported through
     * previous calls to the <code>startCandidateHarvest</code> and <code>startConnectivityEstablishment</code> methods.
     *
     * @param name the name of the content to be removed from the transport-related part of the session
     * represented by this <code>TransportManagerJabberImpl</code>
     *
     * @see TransportManagerJabberImpl#removeContent(String)
     */
    @Override
    public void removeContent(String name) {
        if (local != null)
            removeContent(local, name);
        removeRemoteContent(name);
    }

    /**
     * Removes a content with a specific name from the remote counterpart of the negotiation between
     * the local and the remote peers.
     *
     * @param name the name of the content to be removed from the remote counterpart of the negotiation
     * between the local and the remote peers
     */
    private void removeRemoteContent(String name) {
        for (Iterator<Iterable<JingleContent>> remoteIter = remotes.iterator(); remoteIter.hasNext(); ) {
            Iterable<JingleContent> remote = remoteIter.next();

            /*
             * Once the remote content is removed, make sure that we are not retaining sets which do
             * not have any contents.
             */
            if ((removeContent(remote, name) != null) && !remote.iterator().hasNext()) {
                remoteIter.remove();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    protected XmlElement startCandidateHarvest(JingleContent theirContent,
            JingleContent ourContent, TransportInfoSender transportInfoSender, String media)
            throws OperationFailedException {
        return createTransportForStartCandidateHarvest(media);
    }

    /**
     * Starts transport candidate harvest. This method should complete rapidly and, in case of
     * lengthy procedures like STUN/TURN/UPnP candidate harvests are necessary, they should be
     * executed in a separate thread. Candidate harvest would then need to be concluded in the
     * {@link #wrapupCandidateHarvest()} method which would be called once we absolutely need the candidates.
     *
     * @param theirOffer a media description offer that we've received from the remote party and that we should
     * use in case we need to know what transports our peer is using.
     * @param ourAnswer the content descriptions that we should be adding our transport lists to (although not
     * necessarily in this very instance).
     * @param transportInfoSender the <code>TransportInfoSender</code> to be used by this
     * <code>TransportManagerJabberImpl</code> to send <code>transport-info</code> <code>Jingle</code>s
     * from the local peer to the remote peer if this <code>TransportManagerJabberImpl</code>
     * wishes to utilize <code>transport-info</code>. Local candidate addresses sent by this
     * <code>TransportManagerJabberImpl</code> in <code>transport-info</code> are expected to not be
     * included in the result of {@link #wrapupCandidateHarvest()}.
     *
     * @throws OperationFailedException if we fail to allocate a port number.
     * @see TransportManagerJabberImpl#startCandidateHarvest(List, List, TransportInfoSender)
     */
    @Override
    public void startCandidateHarvest(List<JingleContent> theirOffer,
            List<JingleContent> ourAnswer, TransportInfoSender transportInfoSender)
            throws OperationFailedException {
        this.local = ourAnswer;
        super.startCandidateHarvest(theirOffer, ourAnswer, transportInfoSender);
    }

    /**
     * Overrides the super implementation in order to remember the remote counterpart of the
     * negotiation between the local and the remote peer for subsequent calls to
     * {@link #getStreamTarget(MediaType)}.
     *
     * @param remote the collection of <code>JingleContent</code>s which represents the remote
     * counterpart of the negotiation between the local and the remote peer
     *
     * @return <code>true</code> because <code>RawUdpTransportManager</code> does not perform connectivity checks
     *
     * @see TransportManagerJabberImpl#startConnectivityEstablishment(Iterable)
     */
    @Override
    public boolean startConnectivityEstablishment(Iterable<JingleContent> remote)
            throws OperationFailedException {
        if ((remote != null) && !remotes.contains(remote)) {
            /*
             * The state of the session in Jingle is maintained by each peer and is modified by
             * content-add and content-remove. The remotes field of this RawUdpTransportManager
             * represents the state of the session with respect to the remote peer. When the remote
             * peer tells us about a specific set of contents, make sure that it is the only record
             * we will have with respect to the specified set of contents.
             */
            for (JingleContent content : remote)
                removeRemoteContent(content.getName());

            remotes.add(remote);
        }
        return super.startConnectivityEstablishment(remote);
    }

    /**
     * Simply returns the list of local candidates that we gathered during the harvest.
     * This is a raw UDP transport manager so there's no real wrapping up to do.
     *
     * @return the list of local candidates that we gathered during the harvest
     *
     * @see TransportManagerJabberImpl#wrapupCandidateHarvest()
     */
    @Override
    public List<JingleContent> wrapupCandidateHarvest() {
        return local;
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return The extended type of the candidate selected if this transport manager is using ICE. Otherwise, returns null.
     */
    @Override
    public String getICECandidateExtendedType(String streamName) {
        return null;
    }

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing.
     */
    @Override
    public String getICEState() {
        return null;
    }

    /**
     * Returns the ICE local host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE local host address if this transport manager is using ICE. Otherwise, returns null.
     */
    @Override
    public InetSocketAddress getICELocalHostAddress(String streamName) {
        return null;
    }

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise, returns null.
     */
    @Override
    public InetSocketAddress getICERemoteHostAddress(String streamName) {
        return null;
    }

    /**
     * Returns the ICE local reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE local reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the local candidate used.
     */
    @Override
    public InetSocketAddress getICELocalReflexiveAddress(String streamName) {
        return null;
    }

    /**
     * Returns the ICE remote reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE remote reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the remote candidate used.
     */
    @Override
    public InetSocketAddress getICERemoteReflexiveAddress(String streamName) {
        return null;
    }

    /**
     * Returns the ICE local relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE local relayed address. May be null if this transport manager is not using ICE
     * or if there is no relayed address for the local candidate used.
     */
    @Override
    public InetSocketAddress getICELocalRelayedAddress(String streamName) {
        return null;
    }

    /**
     * Returns the ICE remote relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE remote relayed address. May be null if this transport manager is not using
     * ICE or if there is no relayed address for the remote candidate used.
     */
    @Override
    public InetSocketAddress getICERemoteRelayedAddress(String streamName) {
        return null;
    }

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is null,
     * or if the agent has nevers harvested.
     */
    @Override
    public long getTotalHarvestingTime() {
        return 0;
    }

    /**
     * Returns the harvesting time (in ms) for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     *
     * @return The harvesting time (in ms) for the harvester given in parameter. 0 if this harvester
     * does not exists, if the ICE agent is null, or if the agent has never harvested with this harvester.
     */
    @Override
    public long getHarvestingTime(String harvesterName) {
        return 0;
    }

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    @Override
    public int getNbHarvesting() {
        return 0;
    }

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     *
     * @return The number of harvesting time for the harvester given in parameter.
     */
    @Override
    public int getNbHarvesting(String harvesterName) {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRtcpmux(boolean rtcpmux) {
        if (rtcpmux) {
            throw new IllegalArgumentException("rtcp mux not supported by " + getClass().getSimpleName());
        }
    }
}
