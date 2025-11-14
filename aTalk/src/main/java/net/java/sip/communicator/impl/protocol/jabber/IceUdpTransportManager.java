/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import static net.java.sip.communicator.service.protocol.StunServerDescriptor.PROTOCOL_UDP;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.SecurityAuthority;
import net.java.sip.communicator.service.protocol.StunServerDescriptor;
import net.java.sip.communicator.service.protocol.UserCredentials;
import net.java.sip.communicator.service.protocol.media.TransportManager;
import net.java.sip.communicator.util.PortTracker;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.neomedia.DefaultStreamConnector;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.util.MediaType;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.ice.harvest.UPNPHarvester;
import org.ice4j.security.LongTermCredential;
import org.ice4j.socket.DatagramPacketFilter;
import org.ice4j.socket.MultiplexingDatagramSocket;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smackx.externalservicediscovery.IceCandidateHarvester;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle_rtp.CandidateType;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransportCandidate;
import org.jivesoftware.smackx.jingle_rtp.element.RtcpMux;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jinglenodes.SmackServiceNode;

import timber.log.Timber;

/**
 * A {@link TransportManagerJabberImpl} implementation that would use ICE for candidate management.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 * @author MilanKral
 * @link <a href="https://github.com/MilanKral/atalk-android/commit/d61d5165dda4d290280ebb3e93075e8846e255ad">...</a>
 * Enhance TURN with TCP, TLS, DTLS transport
 */
public class IceUdpTransportManager extends TransportManagerJabberImpl implements PropertyChangeListener {
    // The default STUN servers that will be used in the peer to peer connections if none is specified;
    // pick the first one that is reachable; multiple stun servers only add time in redundant candidates harvest
    List<String> stunServers = Arrays.asList(
            "stun1.l.google.com:19302",
            "stun2.l.google.com:19302",
            "stun3.l.google.com:19302"
    );

    /**
     * The ICE <code>Component</code> IDs in their common order used, for example,
     * by <code>DefaultStreamConnector</code>, <code>MediaStreamTarget</code>.
     */
    private static final int[] COMPONENT_IDS = new int[]{Component.RTP, Component.RTCP};

    /**
     * A filter which accepts any non-RTCP packets (RTP, DTLS, etc).
     */
    private static final DatagramPacketFilter RTP_FILTER = new DatagramPacketFilter() {
        @Override
        public boolean accept(DatagramPacket p) {
            return !RTCP_FILTER.accept(p);
        }
    };

    /**
     * A filter which accepts RTCP packets.
     */
    private final static DatagramPacketFilter RTCP_FILTER = p -> {
        if (p == null) {
            return false;
        }

        byte[] buf = p.getData();
        int off = p.getOffset();
        int len = p.getLength();

        if (buf == null || len < 8 || off + len > buf.length) {
            return false;
        }

        int version = (buf[off] & 0xC0) >>> 6;
        if (version != 2) {
            return false;
        }

        int pt = buf[off + 1] & 0xff;
        return 200 <= pt && pt <= 211;
    };

    /**
     * This is where we keep our answer between the time we get the offer and are ready with the answer.
     */
    protected List<JingleContent> cpeList;

    /**
     * The ICE agent that this transport manager would be using for ICE negotiation.
     */
    protected final Agent iceAgent;

    /**
     * Whether this transport manager should use rtcp-mux. When using rtcp-mux,
     * the ICE Agent initializes a single Component per stream, and we use
     * {@link org.ice4j.socket.MultiplexingDatagramSocket} to split its
     * socket into a socket accepting RTCP packets, and one for everything else (RTP, DTLS).
     *
     * Set the property as static so that it retains the state init by the caller. It will then use
     * as default when making a new call: to take care jitsi that cannot support <rtcp-mux/>
     */
    private boolean rtcpmux = true;

    /**
     * Caches the sockets for the stream connector so that they are not re-created.
     */
    private DatagramSocket[] streamConnectorSockets = null;

    /**
     * Creates a new instance of this transport manager, binding it to the specified peer.
     *
     * @param callPeer the {@link CallPeer} whose traffic we will be taking care of.
     */
    public IceUdpTransportManager(CallPeerJabberImpl callPeer) {
        super(callPeer);
        iceAgent = createIceAgent();
        iceAgent.addStateChangeListener(this);
    }

    /**
     * Creates the ICE agent that we would be using in this transport manager for all negotiation.
     *
     * @return the ICE agent to use for all the ICE negotiation that this transport manager would be going through
     */
    protected Agent createIceAgent() {
        long startGatheringHarvesterTime = System.currentTimeMillis();
        CallPeerJabberImpl peer = getCallPeer();
        ProtocolProviderServiceJabberImpl provider = peer.getProtocolProvider();
        XMPPConnection connection = provider.getConnection();
        NetworkAddressManagerService namSer = getNetAddrMgr();
        boolean atLeastOneStunServer = false;
        Agent agent = namSer.createIceAgent();

        /*
         * XEP-0176: the initiator MUST include the ICE-CONTROLLING attribute,
         * the responder MUST include the ICE-CONTROLLED attribute.
         */
        agent.setControlling(!peer.isInitiator());

        // we will now create the harvesters
        JabberAccountIDImpl accID = (JabberAccountIDImpl) provider.getAccountID();

        if (accID.isStunServerDiscoveryEnabled()) {
            List<StunCandidateHarvester> extServiceHarvester = IceCandidateHarvester.getExtServiceHarvester(connection, PROTOCOL_UDP);
            Timber.i("Auto discovered STUN/TURN extService harvester: %s", extServiceHarvester);
            if (!extServiceHarvester.isEmpty()) {
                for (StunCandidateHarvester iceCandidate : extServiceHarvester) {
                    agent.addCandidateHarvester(iceCandidate);
                }
                atLeastOneStunServer = true;
            }

            // the default server is supposed to use the same user name and password as the account itself.
            String username = accID.getEntityBareJid().toString();
            String password = JabberActivator.getProtocolProviderFactory().loadPassword(accID);
            UserCredentials credentials = provider.getUserCredentials();

            if (credentials != null)
                password = credentials.getPasswordAsString();

            // ask for password if not saved
            if (password == null) {
                // create a default credentials object
                credentials = new UserCredentials();
                credentials.setUserName(accID.getUserID());
                // request a password from the user
                credentials = provider.getAuthority().obtainCredentials(accID, credentials,
                        SecurityAuthority.AUTHENTICATION_REQUIRED, false);

                // in case user has canceled the login window
                if (credentials == null) {
                    Timber.i("Credentials were null. User has most likely canceled the login operation");
                    return null;
                }

                // extract the password the user passed us.
                char[] pass = credentials.getPassword();

                // the user didn't provide us a password (i.e. canceled the operation)
                if (pass == null) {
                    Timber.i("Password was null. User has most likely canceled the login operation");
                    return null;
                }
                password = new String(pass);

                if (credentials.isPasswordPersistent()) {
                    JabberActivator.getProtocolProviderFactory().storePassword(accID, password);
                }
            }
            StunCandidateHarvester autoHarvester = namSer.discoverStunServer(accID.getService(),
                    username.getBytes(StandardCharsets.UTF_8),
                    password.getBytes(StandardCharsets.UTF_8));

            Timber.i("Auto discovered STUN/TURN-server harvester: %s", autoHarvester);
            if (autoHarvester != null) {
                atLeastOneStunServer = true;
                agent.addCandidateHarvester(autoHarvester);
            }
        }

        // now create stun server descriptors for whatever other STUN/TURN servers the user may have set.
        // cmeng: added to support other protocol (20200428)
        // see https://github.com/MilanKral/atalk-android/commit/d61d5165dda4d290280ebb3e93075e8846e255ad
        for (StunServerDescriptor desc : accID.getStunServers()) {
            final String protocol = desc.getProtocol();
            Transport transport;
            switch (protocol) {
                case StunServerDescriptor.PROTOCOL_UDP:
                    transport = Transport.UDP;
                    break;
                case StunServerDescriptor.PROTOCOL_TCP:
                    transport = Transport.TCP;
                    break;
                case StunServerDescriptor.PROTOCOL_DTLS:
                    transport = Transport.DTLS;
                    break;
                case StunServerDescriptor.PROTOCOL_TLS:
                    transport = Transport.TLS;
                    break;
                default:
                    Timber.w("Unknown protocol %s", protocol);
                    transport = Transport.UDP;
                    break;
            }

            for (TransportAddress addr : getTransportAddress(desc.getAddress(), desc.getPort(), transport)) {
                // if we get STUN server from automatic discovery, it may just be server name
                // (i.e. stun.domain.org), and it may be possible that it cannot be resolved
                if (addr.getAddress() == null) {
                    Timber.i("Unresolved STUN server address for %s", addr);
                    continue;
                }

                StunCandidateHarvester harvester;
                if (desc.isTurnSupported()) {
                    // this is a TURN server
                    harvester = new TurnCandidateHarvester(addr, new LongTermCredential(desc.getUsername(), desc.getPassword()));
                }
                else {
                    // this is a STUN only server
                    harvester = new StunCandidateHarvester(addr);
                }

                Timber.i("Adding pre-configured harvester %s", harvester);
                atLeastOneStunServer = true;
                agent.addCandidateHarvester(harvester);
            }
        }

        // Found no configured or discovered STUN server; so takes default stunServers provided if user allows it
        if (!atLeastOneStunServer && accID.isUseDefaultStunServer()) {
            for (String stunServer : stunServers) {
                String[] hostPort = stunServer.split(":");
                for (TransportAddress addr : getTransportAddress(hostPort[0], Integer.parseInt(hostPort[1]), Transport.UDP)) {
                    agent.addCandidateHarvester(new StunCandidateHarvester(addr));
                    atLeastOneStunServer = true;
                }

                // Skip the rest if one has set up successfully
                if (atLeastOneStunServer)
                    break;
            }
        }

        /* Jingle nodes candidate */
        if (accID.isJingleNodesRelayEnabled()) {
            /*
             * this method is blocking until Jingle Nodes auto-discovery (if enabled) finished
             */
            SmackServiceNode serviceNode = provider.getJingleNodesServiceNode();
            if (serviceNode != null) {
                agent.addCandidateHarvester(new JingleNodesHarvester(serviceNode));
            }
        }

        if (accID.isUPNPEnabled()) {
            agent.addCandidateHarvester(new UPNPHarvester());
        }

        long stopGatheringHarvesterTime = System.currentTimeMillis();
        long gatheringHarvesterTime = stopGatheringHarvesterTime - startGatheringHarvesterTime;
        Timber.i("End gathering harvesters within %d ms; size: %s; Harvesters:\n%s",
                gatheringHarvesterTime, agent.getHarvesters().size(), agent.getHarvesters());
        return agent;
    }

    /**
     * Generate a list of TransportAddress from the given hostname, port and transport.
     * The given host name is resolved into both IPv4 and IPv6 InetAddresses.
     *
     * Note: android InetAddress.getByName(hostname) returns the first IP found, any may be an IPv6 InetAddress;
     * if mobile network setting for APN=IPV4/IPv6 or APN=IPv6. This causes problem in STUN candidate harvest:
     *
     * @param hostname the address itself
     * @param port the port number
     * @param transport the transport to use with this address.
     *
     * @see <a href="https://github.com/jitsi/ice4j/issues/255">...</a>
     */
    protected List<TransportAddress> getTransportAddress(String hostname, int port, Transport transport) {
        List<TransportAddress> transportAddress = new ArrayList<>();
        try {
            // return all associated InetAddress in both IPv4 and IPv6 address
            InetAddress[] inetAddresses = InetAddress.getAllByName(hostname);
            for (InetAddress inetAddress : inetAddresses) {
                transportAddress.add(new TransportAddress(inetAddress, port, transport));
            }
        } catch (UnknownHostException e) {
            Timber.e("UnknownHostException: %s", e.getMessage());
        }
        return transportAddress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected StreamConnector doCreateStreamConnector(MediaType mediaType)
            throws OperationFailedException {
        /*
         * If this instance is participating in a telephony conference utilizing the Jitsi
         * Videobridge server-side technology that is organized by the local peer, then there is a
         * single MediaStream (of the specified mediaType) shared among multiple TransportManagers
         * and its StreamConnector may be determined only by the TransportManager which is
         * establishing the connectivity with the Jitsi Videobridge server (as opposed to a CallPeer).
         */
        TransportManagerJabberImpl delegate = findTransportManagerEstablishingConnectivityWithJitsiVideobridge();

        if ((delegate != null) && (delegate != this))
            return delegate.doCreateStreamConnector(mediaType);

        DatagramSocket[] streamConnectorSockets = getStreamConnectorSockets(mediaType);

        /*
         * XXX If the iceAgent has not completed (yet), go with a default StreamConnector (until it completes).
         */
        return (streamConnectorSockets == null) ? super.doCreateStreamConnector(mediaType)
                : new DefaultStreamConnector(streamConnectorSockets[0 /* RTP */], streamConnectorSockets[1 /* RTCP */]);
    }

    /**
     * Gets the <code>StreamConnector</code> to be used as the <code>connector</code> of the
     * <code>MediaStream</code> with a specific <code>MediaType</code> .
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> which is to have its
     * <code>connector</code> set to the returned <code>StreamConnector</code>
     *
     * @return the <code>StreamConnector</code> to be used as the <code>connector</code> of the
     * <code>MediaStream</code> with the specified <code>MediaType</code>
     *
     * @throws OperationFailedException if anything goes wrong while initializing the requested <code>StreamConnector</code>
     * @see net.java.sip.communicator.service.protocol.media.TransportManager#getStreamConnector(MediaType)
     */
    @Override
    public StreamConnector getStreamConnector(MediaType mediaType)
            throws OperationFailedException {
        StreamConnector streamConnector = super.getStreamConnector(mediaType);

        /*
         * Since the super caches the StreamConnectors, make sure that the returned one is up-to-date with the iceAgent.
         */
        if (streamConnector != null) {
            DatagramSocket[] streamConnectorSockets = getStreamConnectorSockets(mediaType);

            /*
             * XXX If the iceAgent has not completed (yet), go with the default StreamConnector (until it completes).
             */
            if ((streamConnectorSockets != null)
                    && ((streamConnector.getDataSocket() != streamConnectorSockets[0 /* RTP */])
                    || (streamConnector.getControlSocket() != streamConnectorSockets[1 /* RTCP */]))) {
                // Recreate the StreamConnector for the specified mediaType.
                closeStreamConnector(mediaType);
                streamConnector = super.getStreamConnector(mediaType);
            }
        }
        return streamConnector;
    }

    /**
     * Gets an array of <code>DatagramSocket</code>s which represents the sockets to be used by the
     * <code>StreamConnector</code> with the specified <code>MediaType</code> in the order of
     * {@link #COMPONENT_IDS} if {@link #iceAgent} has completed.
     *
     * @param mediaType the <code>MediaType</code> of the <code>StreamConnector</code> for which the
     * <code>DatagramSocket</code>s are to be returned
     *
     * @return an array of <code>DatagramSocket</code>s which represents the sockets to be used by the
     * <code>StreamConnector</code> which the specified <code>MediaType</code> in the order of
     * {@link #COMPONENT_IDS} if {@link #iceAgent} has completed; otherwise, <code>null</code>
     */
    private DatagramSocket[] getStreamConnectorSockets(MediaType mediaType) {
        // cmeng: aTalk remote video cannot receive if enabled even for ice4j-2.0
        // if (streamConnectorSockets != null) {
        //     return streamConnectorSockets;
        // }

        IceMediaStream stream = iceAgent.getStream(mediaType.toString());
        if (stream != null) {
            if (rtcpmux) {
                Component component = stream.getComponent(Component.RTP);
                MultiplexingDatagramSocket componentSocket = component.getSocket();

                // ICE is not ready yet
                if (componentSocket == null) {
                    return null;
                }

                DatagramSocket[] streamConnectorSockets = new DatagramSocket[2];
                try {
                    streamConnectorSockets[0] = componentSocket.getSocket(RTP_FILTER);
                    streamConnectorSockets[1] = componentSocket.getSocket(RTCP_FILTER);
                } catch (Exception e) {
                    Timber.e("Failed to create filtered sockets.");
                    return null;
                }
                return this.streamConnectorSockets = streamConnectorSockets;
            }
            else {
                DatagramSocket[] streamConnectorSockets = new DatagramSocket[COMPONENT_IDS.length];
                int streamConnectorSocketCount = 0;

                for (int i = 0; i < COMPONENT_IDS.length; i++) {
                    Component component = stream.getComponent(COMPONENT_IDS[i]);

                    if (component != null) {
                        DatagramSocket streamConnectorSocket = component.getSocket();
                        if (streamConnectorSocket != null) {
                            streamConnectorSockets[i] = streamConnectorSocket;
                            streamConnectorSocketCount++;
                            Timber.log(TimberLog.FINER, "Added a streamConnectorSocket to StreamConnectorSocket list"
                                    + " and increase streamConnectorSocketCount to %s", streamConnectorSocketCount);
                        }
                        // }
                    }
                }
                if (streamConnectorSocketCount > 0) {
                    return this.streamConnectorSockets = streamConnectorSockets;
                }
            }
        }
        return null;
    }

    /**
     * Implements {@link TransportManagerJabberImpl#getStreamTarget(MediaType)}. Gets the <code>MediaStreamTarget</code>
     * to be used as the <code>target</code> of the <code>MediaStream</code> with a specific <code>MediaType</code>.
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
        /*
         * If this instance is participating in a telephony conference utilizing the Jitsi
         * Videobridge server-side technology that is organized by the local peer, then there is a
         * single MediaStream (of the specified mediaType) shared among multiple TransportManagers
         * and its MediaStreamTarget may be determined only by the TransportManager which is
         * establishing the connectivity with the Jitsi Videobridge server (as opposed to a CallPeer).
         */

        TransportManagerJabberImpl delegate = findTransportManagerEstablishingConnectivityWithJitsiVideobridge();
        if ((delegate != null) && (delegate != this))
            return delegate.getStreamTarget(mediaType);

        IceMediaStream stream = iceAgent.getStream(mediaType.toString());
        MediaStreamTarget streamTarget = null;

        if (stream != null) {
            InetSocketAddress[] streamTargetAddresses = new InetSocketAddress[COMPONENT_IDS.length];
            int streamTargetAddressCount = 0;

            for (int i = 0; i < COMPONENT_IDS.length; i++) {
                Component component = stream.getComponent(COMPONENT_IDS[i]);

                if (component != null) {
                    CandidatePair selectedPair = component.getSelectedPair();

                    if (selectedPair != null) {
                        InetSocketAddress streamTargetAddress = selectedPair.getRemoteCandidate().getTransportAddress();
                        if (streamTargetAddress != null) {
                            streamTargetAddresses[i] = streamTargetAddress;
                            streamTargetAddressCount++;
                        }
                    }
                }
            }
            if (rtcpmux) {
                streamTargetAddresses[1] = streamTargetAddresses[0];
                streamTargetAddressCount++;
            }
            if (streamTargetAddressCount > 0) {
                streamTarget = new MediaStreamTarget(streamTargetAddresses[0 /* RTP */], streamTargetAddresses[1 /* RTCP */]);
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
        return ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1;
    }

    /**
     * {@inheritDoc}
     *
     * Both the transport-info attributes i.e. ufrag and pwd must be set for IceUdpTransport by default;
     * In case there are child elements other than candidates e.g. DTLS fingerPrint
     */
    protected XmlElement createTransportPacketExtension() {
        IceUdpTransport.Builder tpBuilder = IceUdpTransport.getBuilder()
                .setUfrag(iceAgent.getLocalUfrag())
                .setPassword(iceAgent.getLocalPassword());

        return tpBuilder.build();
    }

    /**
     * {@inheritDoc}
     */
    protected XmlElement startCandidateHarvest(JingleContent theirContent, JingleContent ourContent,
            TransportInfoSender transportInfoSender, String media)
            throws OperationFailedException {
        XmlElement pe;

        // Report the gathered candidate addresses.
        if (transportInfoSender == null) {
            pe = createTransportForStartCandidateHarvest(media);
        }
        else {
            /*
             * The candidates will be sent in transport-info so the transport of session-accept just
             * has to be present, not populated with candidates.
             */
            pe = createTransportPacketExtension();

            /*
             * Create the content to be sent in a transport-info. The transport is the only
             * extension to be sent in transport-info so the content has the same attributes as in
             * our answer and none of its non-transport extensions.
             */
            JingleContent.Builder content = JingleContent.getBuilder();
            for (String name : ourContent.getAttributes().keySet()) {
                String value = ourContent.getAttributeValue(name);
                if (value != null)
                    content.addAttribute(name, value);
            }
            content.addChildElement(createTransportForStartCandidateHarvest(media));

            /*
             * We send each media content in separate transport-info. It is absolutely not mandatory
             * (we can simply send all content in one transport-info) but the XMPP Jingle client
             * Empathy (via telepathy-gabble), which is present on many Linux distributions and N900
             * mobile phone, has a bug when it receives more than one content in transport-info. The
             * related bug has been fixed in mainstream but the Linux distributions have not updated
             * their packages yet. That's why we made this modification to be fully interoperable
             * with Empathy right now. In the future, we will get back to the original behavior:
             * sending all content in one transport-info.
             */
            Collection<JingleContent> transportInfoContents = new LinkedList<>();
            transportInfoContents.add(content.build());
            transportInfoSender.sendTransportInfo(transportInfoContents);
        }
        return pe;
    }

    /**
     * Starts transport candidate harvest. This method should complete rapidly and, in case of lengthy procedures
     * like STUN/TURN/UPnP candidate harvests are necessary, they should be executed in a separate thread.
     * Candidate harvest would then need to be concluded in the {@link #wrapupCandidateHarvest()} method which
     * would be called once we absolutely need the candidates.
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
        // Timber.w(new Exception("CPE list updated"));
        this.cpeList = ourAnswer;
        super.startCandidateHarvest(theirOffer, ourAnswer, transportInfoSender);
    }

    /**
     * Converts the ICE media <code>stream</code> and its local candidates into a
     * {@link IceUdpTransport}.
     *
     * @param stream the {@link IceMediaStream} that we'd like to describe in XML.
     *
     * @return the {@link IceUdpTransport} that we
     */
    private XmlElement createTransport(IceMediaStream stream) {
        Agent iceAgent = stream.getParentAgent();
        IceUdpTransport.Builder tpBuilder = IceUdpTransport.getBuilder()
                .setUfrag(iceAgent.getLocalUfrag())
                .setPassword(iceAgent.getLocalPassword());

        /*
         * @see RtcpMux per XEP-0167: Jingle RTP Sessions 1.2.1 (2020-09-29) https://xmpp.org/extensions/xep-0167.html#format
         *
         * This is a patch for jitsi and is non XEP standard: may want to remove once jitsi has updated;
         * still required on 2.11.5633. Jitsi works only on the audio but no video call (only local video);
         * In aTalk: rtcp-mux will re-align itself to jitsi rtcp-mux mode after first call from jitsi i.e. false.
         */
        if (rtcpmux) {
            tpBuilder.addChildElement(RtcpMux.builder(IceUdpTransport.NAMESPACE).build());
        }

        for (Component component : stream.getComponents()) {
            for (Candidate<?> candidate : component.getLocalCandidates())
                tpBuilder.addChildElement(createCandidate(candidate));
        }
        return tpBuilder.build();
    }

    /**
     * {@inheritDoc}
     */
    protected XmlElement createTransport(String media)
            throws OperationFailedException {
        IceMediaStream iceStream = iceAgent.getStream(media);
        if (iceStream == null)
            iceStream = createIceStream(media);
        return createTransport(iceStream);
    }

    /**
     * Creates a {@link IceUdpTransportCandidate} and initializes it so that it would describe the
     * state of <code>candidate</code>
     *
     * @param candidate the ICE4J {@link Candidate} that we'd like to convert into an XMPP packet extension.
     *
     * @return a new {@link IceUdpTransportCandidate} corresponding to the state of the <code>candidate</code> candidate.
     */
    private IceUdpTransportCandidate createCandidate(Candidate<?> candidate) {
        IceUdpTransportCandidate.Builder cBuilder = IceUdpTransportCandidate.getBuilder();
        cBuilder.setFoundation(candidate.getFoundation());

        Component component = candidate.getParentComponent();
        cBuilder.setComponent(component.getComponentID())
                .setProtocol(candidate.getTransport().toString())
                .setPriority(candidate.getPriority())
                .setGeneration(component.getParentStream().getParentAgent().getGeneration());

        TransportAddress transportAddress = candidate.getTransportAddress();
        cBuilder.setID(getNextID())
                .setIP(transportAddress.getHostAddress())
                .setPort(transportAddress.getPort())
                .setType(CandidateType.valueOf(candidate.getType().toString()));

        TransportAddress relAddr = candidate.getRelatedAddress();
        if (relAddr != null) {
            cBuilder.setRelAddr(relAddr.getHostAddress())
                    .setRelPort(relAddr.getPort());
        }
        /*
         * FIXME The XML schema of XEP-0176: Jingle ICE-UDP Transport Method specifies the network attribute as required.
         */
        cBuilder.setNetwork(0);
        return cBuilder.build();
    }

    /**
     * Creates an {@link IceMediaStream} with the specified <code>media</code> name.
     *
     * @param media the name of the stream we'd like to create.
     *
     * @return the newly created {@link IceMediaStream}
     *
     * @throws OperationFailedException if binding on the specified media stream fails for some reason.
     */
    protected IceMediaStream createIceStream(String media)
            throws OperationFailedException {
        IceMediaStream stream;
        PortTracker portTracker;

        Timber.d("Created Ice stream agent for %s", media);
        try {
            portTracker = getPortTracker(media);
            // the following call involves STUN processing so it may take a while
            stream = getNetAddrMgr().createIceStream(rtcpmux ? 1 : 2, portTracker.getPort(), media, iceAgent);
        } catch (Exception ex) {
            throw new OperationFailedException("Failed to initialize stream " + media,
                    OperationFailedException.INTERNAL_ERROR, ex);
        }

        // Attempt to minimize subsequent bind retries: see if we have allocated
        // any ports from the dynamic range, and if so update the port tracker.
        // Do NOT update the port tracker with non-dynamic ports (e.g. 4443
        // coming from TCP) because this will force it to revert back it its
        // configured min port. When maxPort is reached, allocation will begin
        // from minPort again, so we don't have to worry about wraps.
        try {
            int maxAllocatedPort = getMaxAllocatedPort(stream, portTracker.getMinPort(), portTracker.getMaxPort());

            if (maxAllocatedPort > 0) {
                int nextPort = 1 + maxAllocatedPort;
                portTracker.setNextPort(nextPort);
                Timber.d("Updating the port tracker min port: %s", nextPort);
            }
        } catch (Throwable t) {
            //hey, we were just trying to be nice. if that didn't work for
            //some reason we really can't be held responsible!
            Timber.d(t, "Determining next port didn't work.");
        }
        return stream;
    }

    /**
     * @return the highest local port used by any of the local candidates of
     * {@code iceStream}, which falls in the range [{@code min}, {@code max}].
     */
    private int getMaxAllocatedPort(IceMediaStream iceStream, int min, int max) {
        return Math.max(
                getMaxAllocatedPort(iceStream.getComponent(Component.RTP), min, max),
                getMaxAllocatedPort(iceStream.getComponent(Component.RTCP), min, max));
    }

    /**
     * @return the highest local port used by any of the local candidates of
     * {@code component}, which falls in the range [{@code min}, {@code max}].
     */
    private int getMaxAllocatedPort(Component component, int min, int max) {
        int maxAllocatedPort = -1;

        if (component != null) {
            for (LocalCandidate candidate : component.getLocalCandidates()) {
                int candidatePort = candidate.getTransportAddress().getPort();

                if ((min <= candidatePort) && (candidatePort <= max) && (maxAllocatedPort < candidatePort)) {
                    maxAllocatedPort = candidatePort;
                }
            }
        }
        return maxAllocatedPort;
    }

    /**
     * Simply returns the list of local candidates that we gathered during the harvest.
     *
     * @return the list of local candidates that we gathered during the harvest
     *
     * @see TransportManagerJabberImpl#wrapupCandidateHarvest()
     */
    @Override
    public List<JingleContent> wrapupCandidateHarvest() {
        return cpeList;
    }

    /**
     * Returns a reference to the {@link NetworkAddressManagerService}. The only reason this method
     * exists is that {@link JabberActivator #getNetworkAddressManagerService()} is too long to
     * write and makes code look clumsy.
     *
     * @return a reference to the {@link NetworkAddressManagerService}.
     */
    private static NetworkAddressManagerService getNetAddrMgr() {
        return JabberActivator.getNetworkAddressManagerService();
    }

    /**
     * Starts the connectivity establishment of the associated ICE <code>Agent</code>.
     *
     * @param remote the collection of <code>JingleContent</code>s which represents the remote
     * counterpart of the negotiation between the local and the remote peers
     *
     * @return <code>true</code> if connectivity establishment has been started in response to the call;
     * otherwise, <code>false</code>
     *
     * @see TransportManagerJabberImpl#startConnectivityEstablishment(Iterable)
     */
    @Override
    public synchronized boolean startConnectivityEstablishment(Iterable<JingleContent> remote)
            throws OperationFailedException {
        // Timber.w(new Exception("start Connectivity Establishment"));
        Map<String, IceUdpTransport> mediaTransports = new LinkedHashMap<>();
        for (JingleContent content : remote) {
            IceUdpTransport transport = content.getFirstChildElement(IceUdpTransport.class);
            /*
             * If we cannot associate an IceMediaStream with the remote content, we will not have
             * anything to add the remote candidates to.
             */
            RtpDescription description = content.getFirstChildElement(RtpDescription.class);
            if ((description == null) && (cpeList != null)) {
                JingleContent localContent = findContentByName(cpeList, content.getName());

                if (localContent != null) {
                    description = localContent.getFirstChildElement(RtpDescription.class);
                }
            }

            if (description != null) {
                String media = description.getMedia();
                mediaTransports.put(media, transport);
                // Timber.d("### Processing Jingle IQ (transport-info) media map add: %s (%s)",
                // media, transport.getFirstChildElement(IceUdpTransportCandidate.class).toXML());
            }
        }
        /*
         * When the local peer is organizing a telephony conference using the Jitsi Videobridge
         * server-side technology, it is establishing connectivity by using information from a
         * colibri Channel and not from the offer/answer of the remote peer.
         */
        if (getCallPeer().isJitsiVideobridge()) {
            sendTransportInfoToJitsiVideobridge(mediaTransports);
            return false;
        }
        else {
            boolean status = startConnectivityEstablishment(mediaTransports);
            Timber.d("### Processed Jingle (transport-info) for media: %s; startConnectivityEstablishment: %s",
                    mediaTransports.keySet(), status);
            return status;
        }
    }

    /**
     * Starts the connectivity establishment of the associated ICE <code>Agent</code>.
     *
     * @param remote a <code>Map</code> of media-<code>IceUdpTransport</code> pairs which represents
     * the remote counterpart of the negotiation between the local and the remote peers
     *
     * @return <code>true</code> if connectivity establishment has been started in response to the call;
     * otherwise, <code>false</code>
     *
     * @see TransportManagerJabberImpl#startConnectivityEstablishment(Map)
     */
    @Override
    protected synchronized boolean startConnectivityEstablishment(Map<String, IceUdpTransport> remote) {
        /*
         * If ICE is running already, we try to update the checklists with the candidates.
         * Note that this is a best effort.
         */
        // Timber.w("Ice Agent in used: %s", iceAgent);
        boolean iceAgentStateIsRunning = IceProcessingState.RUNNING.equals(iceAgent.getState());
        if (iceAgentStateIsRunning)
            Timber.i("Updating ICE remote candidates");

        int generation = iceAgent.getGeneration();
        boolean startConnectivityEstablishment = false;

        for (Map.Entry<String, IceUdpTransport> e : remote.entrySet()) {
            IceUdpTransport transport = e.getValue();
            List<IceUdpTransportCandidate> candidates = transport.getChildElements(IceUdpTransportCandidate.class);

            if (iceAgentStateIsRunning && candidates.isEmpty()) {
                Timber.i("Connectivity establishment has not been started because candidate list is empty");
                return false;
            }

            String media = e.getKey();
            IceMediaStream stream = iceAgent.getStream(media);
            if (stream == null) {
                Timber.w("No ICE media stream for media: %s (%s)", media, iceAgent.getStreams());
                continue;
            }

            // Sort the remote candidates (host < reflexive < relayed) in order to create first the
            // host, then the reflexive, the relayed candidates and thus be able to set the
            // relative-candidate matching the rel-addr/rel-port attribute.
            Collections.sort(candidates);

            // Different stream may have different ufrag/passwordProcess valid component candidate
            String ufrag = transport.getUfrag();
            if (ufrag != null)
                stream.setRemoteUfrag(ufrag);

            String password = transport.getPassword();
            if (password != null)
                stream.setRemotePassword(password);

            for (IceUdpTransportCandidate candidate : candidates) {
                /*
                 * Is the remote candidate from the current generation of the iceAgent?
                 */
                if (candidate.getGeneration() != generation)
                    continue;

                if (candidate.getIP() == null || "".equals(candidate.getIP())) {
                    Timber.w("Skipped ICE candidate with empty IP");
                    continue;
                }

                String relAddr;
                int relPort;
                TransportAddress relatedAddress = null;

                if (((relAddr = candidate.getRelAddr()) != null) && ((relPort = candidate.getRelPort()) != -1)) {
                    relatedAddress = new TransportAddress(relAddr, relPort, Transport.parse(candidate.getProtocol()));
                }

                // must check for null else NPE in component.findRemoteCandidate()
                Component component = stream.getComponent(candidate.getComponent());
                if (component != null) {
                    // Timber.d("Process valid component candidate type: %s: %s", media, iceAgent.getStream(media));// candidate.toXML());
                    RemoteCandidate relatedCandidate = component.findRemoteCandidate(relatedAddress);
                    RemoteCandidate remoteCandidate = new RemoteCandidate(new TransportAddress(
                            candidate.getIP(), candidate.getPort(), Transport.parse(candidate.getProtocol())), component,
                            org.ice4j.ice.CandidateType.parse(candidate.getType().toString()),
                            candidate.getFoundation(), candidate.getPriority(), relatedCandidate);
                    if (iceAgentStateIsRunning) {
                        component.addUpdateRemoteCandidates(remoteCandidate);
                    }
                    else {
                        component.addRemoteCandidate(remoteCandidate);
                        startConnectivityEstablishment = true;
                    }
                }
                else {
                    // Conversations sends single candidate with each transport, and sends component 1 and 2
                    // for RTP and RCTP even with <rtcp-mux/> specified; So just skip and continue with next
                    // aTalk support <rtcp-mux/>.
                    Timber.w("Skip invalid component candidate: %s", candidate.toXML());
                }
            }
        }

        if (iceAgentStateIsRunning) {
            // update all components of all streams
            for (IceMediaStream stream : iceAgent.getStreams()) {
                for (Component component : stream.getComponents())
                    component.updateRemoteCandidates();
            }
        }
        else if (startConnectivityEstablishment) {
            /*
             * Once again because the ICE Agent does not support adding candidates after the
             * connectivity establishment has been started and because multiple transport-info
             * JingleIQs may be used to send the whole set of transport candidates from the remote
             * peer to the local peer, do not really start the connectivity establishment until we
             * have at least one remote candidate per ICE Component i.e. audio.RTP, audio.RTCP,
             * video.RTP & video.RTCP.
             */
            for (IceMediaStream stream : iceAgent.getStreams()) {
                for (Component component : stream.getComponents()) {
                    if (component.getRemoteCandidateCount() < 1) {
                        Timber.d("### Insufficient remote candidates to startConnectivityEstablishment! %s: %s %s",
                                component.toShortString(), component.getRemoteCandidateCount(), iceAgent.getStreams());
                        startConnectivityEstablishment = false;
                        break;
                    }
                }
                if (!startConnectivityEstablishment)
                    break;
            }

            if (startConnectivityEstablishment) {
                iceAgent.startConnectivityEstablishment();
                return true;
            }
        }
        return false;
    }

    /**
     * Waits for the associated ICE <code>Agent</code> to finish any started connectivity checks.
     *
     * @throws OperationFailedException if ICE processing has failed
     * @see TransportManagerJabberImpl#wrapupConnectivityEstablishment()
     */
    @Override
    public void wrapupConnectivityEstablishment()
            throws OperationFailedException {
        TransportManagerJabberImpl delegate = findTransportManagerEstablishingConnectivityWithJitsiVideobridge();
        if ((delegate == null) || (delegate == this)) {
            final Object iceProcessingStateSyncRoot = new Object();
            PropertyChangeListener stateChangeListener = new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    Agent iceAgent = (Agent) evt.getSource();
                    if (iceAgent.isOver()) {
                        Timber.d("Current IceProcessingState: %s", evt.getNewValue());
                        iceAgent.removeStateChangeListener(this);
                        if (iceAgent == IceUdpTransportManager.this.iceAgent) {
                            synchronized (iceProcessingStateSyncRoot) {
                                iceProcessingStateSyncRoot.notify();
                            }
                        }
                    }
                }
            };
            iceAgent.addStateChangeListener(stateChangeListener);

            /*
             * Wait for the ICE connectivity checks to complete if they have started or
             * waiting for transport info with max TOT of 5S.
             */
            boolean interrupted = false;
            int maxWaitTimer = 5; // in seconds
            synchronized (iceProcessingStateSyncRoot) {
                while (IceProcessingState.RUNNING.equals(iceAgent.getState())
                        || IceProcessingState.WAITING.equals(iceAgent.getState())) {
                    try {
                        iceProcessingStateSyncRoot.wait(1000);
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                    // Break the loop if maxWaitTimer timeout
                    if (maxWaitTimer-- < 0)
                        break;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
            /*
             * Make sure stateChangeListener is removed from iceAgent in case its
             * #propertyChange(PropertyChangeEvent) has never been executed.
             */
            iceAgent.removeStateChangeListener(stateChangeListener);
            /* check the state of ICE processing and throw exception if failed */
            if (IceProcessingState.FAILED.equals(iceAgent.getState())) {
                String msg = aTalkApp.getResString(R.string.protocol_ice_failed);
                throw new OperationFailedException(msg, OperationFailedException.GENERAL_ERROR);
            }
        }
        else {
            delegate.wrapupConnectivityEstablishment();
        }

        /*
         * Once we're done establishing connectivity, we shouldn't be sending any more candidates
         * because we will not be able to perform connectivity checks for them.
         * Besides, they must have been sent in transport-info already.
         *
         * cmeng 2020529: Do not remove attributes UFRAG_ATTR_NAME and PWD_ATTR_NAME if
         * transport-info contains child elements e.g. DTLS FingerPrint
         */
        if (cpeList != null) {
            for (JingleContent content : cpeList) {
                IceUdpTransport transport = content.getFirstChildElement(IceUdpTransport.class);
                if (transport != null) {
                    transport.removeCandidate(new IceUdpTransportCandidate());
                }
            }
        }
    }

    /**
     * Removes a content with a specific name from the transport-related part of the session
     * represented by this <code>TransportManagerJabberImpl</code> which may have been reported through
     * previous calls to the <code>startCandidateHarvest</code> and
     * <code>startConnectivityEstablishment</code> methods.
     *
     * @param name the name of the content to be removed from the transport-related part of the session
     * represented by this <code>TransportManagerJabberImpl</code>
     *
     * @see TransportManagerJabberImpl#removeContent(String)
     */
    @Override
    public void removeContent(String name) {
        JingleContent content = removeContent(cpeList, name);
        if (content != null) {
            RtpDescription rtpDescription = content.getFirstChildElement(RtpDescription.class);

            if (rtpDescription != null) {
                IceMediaStream stream = iceAgent.getStream(rtpDescription.getMedia());
                if (stream != null)
                    iceAgent.removeStream(stream);
            }
        }
    }

    /**
     * Close this transport manager and release resources. In case of ICE, it releases Ice4j's Agent
     * that will cleanup all streams, component and close every candidate's sockets.
     */
    @Override
    public synchronized void close() {
        if (iceAgent != null) {
            iceAgent.removeStateChangeListener(this);
            iceAgent.free();
        }
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return The extended type of the candidate selected if this transport manager is using ICE.
     * Otherwise, returns null.
     */
    @Override
    public String getICECandidateExtendedType(String streamName) {
        return TransportManager.getICECandidateExtendedType(iceAgent, streamName);
    }

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing.
     */
    @Override
    public String getICEState() {
        return iceAgent.getState().toString();
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
        if (iceAgent != null) {
            LocalCandidate localCandidate = iceAgent.getSelectedLocalCandidate(streamName);
            if (localCandidate != null)
                return localCandidate.getHostAddress();
        }
        return null;
    }

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     *
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise,
     * returns null.
     */
    @Override
    public InetSocketAddress getICERemoteHostAddress(String streamName) {
        if (iceAgent != null) {
            RemoteCandidate remoteCandidate = iceAgent.getSelectedRemoteCandidate(streamName);
            if (remoteCandidate != null)
                return remoteCandidate.getHostAddress();
        }
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
        if (iceAgent != null) {
            LocalCandidate localCandidate = iceAgent.getSelectedLocalCandidate(streamName);
            if (localCandidate != null)
                return localCandidate.getReflexiveAddress();
        }
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
        if (iceAgent != null) {
            RemoteCandidate remoteCandidate = iceAgent.getSelectedRemoteCandidate(streamName);
            if (remoteCandidate != null)
                return remoteCandidate.getReflexiveAddress();
        }
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
        if (iceAgent != null) {
            LocalCandidate localCandidate = iceAgent.getSelectedLocalCandidate(streamName);
            if (localCandidate != null)
                return localCandidate.getRelayedAddress();
        }
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
        if (iceAgent != null) {
            RemoteCandidate remoteCandidate = iceAgent.getSelectedRemoteCandidate(streamName);
            if (remoteCandidate != null)
                return remoteCandidate.getRelayedAddress();
        }
        return null;
    }

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is null,
     * or if the agent has never harvested.
     */
    @Override
    public long getTotalHarvestingTime() {
        return (iceAgent == null) ? 0 : iceAgent.getTotalHarvestingTime();
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
        return (iceAgent == null) ? 0 : iceAgent.getHarvestingTime(harvesterName);
    }

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    @Override
    public int getNbHarvesting() {
        return (iceAgent == null) ? 0 : iceAgent.getHarvestCount();
    }

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     *
     * @return The number of harvesting time for the harvester given in parameter.
     */
    public int getNbHarvesting(String harvesterName) {
        return (iceAgent == null) ? 0 : iceAgent.getHarvestCount(harvesterName);
    }

    /**
     * Retransmit state change events from the Agent to the media handler.
     *
     * @param evt the event for state change.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        getCallPeer().getMediaHandler().firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRtcpmux(boolean rtcpmux) {
        this.rtcpmux = rtcpmux;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRtcpmux() {
        return rtcpmux;
    }
}
