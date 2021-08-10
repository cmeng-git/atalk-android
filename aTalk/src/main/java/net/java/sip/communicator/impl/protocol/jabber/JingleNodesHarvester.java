/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Component;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.harvest.AbstractCandidateHarvester;
import org.ice4j.socket.IceSocketWrapper;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.xmpp.jnodes.smack.*;

import java.util.Collection;
import java.util.HashSet;

import timber.log.Timber;

/**
 * Implements a <tt>CandidateHarvester</tt> which gathers <tt>Candidate</tt>s for a specified
 * {@link Component} using Jingle Nodes as defined in XEP 278 "Jingle Relay Nodes".
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 **/
public class JingleNodesHarvester extends AbstractCandidateHarvester
{
    /**
     * XMPPTCPConnection
     */
    private SmackServiceNode serviceNode;

    /**
     * JingleNodes relay allocate two address/port couple for us. Due to the architecture of Ice4j
     * that harvest address for each component, we store the second address/port couple.
     */
    private TransportAddress localAddressSecond = null;

    /**
     * JingleNodes relay allocate two address/port couple for us. Due to the architecture of Ice4j
     * that harvest address for each component, we store the second address/port couple.
     */
    private TransportAddress relayedAddressSecond = null;

    /**
     * Constructor.
     *
     * @param serviceNode the <tt>SmackServiceNode</tt>
     */
    public JingleNodesHarvester(SmackServiceNode serviceNode)
    {
        this.serviceNode = serviceNode;
    }

    /**
     * Gathers Jingle Nodes candidates for all host <tt>Candidate</tt>s that are already present in
     * the specified <tt>component</tt>. This method relies on the specified <tt>component</tt> to
     * already contain all its host candidates so that it would resolve them.
     *
     * @param component the {@link Component} that we'd like to gather candidate Jingle Nodes
     * <tt>Candidate</tt>s for
     * @return the <tt>LocalCandidate</tt>s gathered by this <tt>CandidateHarvester</tt>
     */
    @Override
    public synchronized Collection<LocalCandidate> harvest(Component component)
    {
        Timber.i("harvest Jingle Nodes");
        Collection<LocalCandidate> candidates = new HashSet<>();
        String ip;
        int port = -1;

        /* if we have already a candidate (RTCP) allocated, get it */
        if (localAddressSecond != null && relayedAddressSecond != null) {
            LocalCandidate candidate = createJingleNodesCandidate(relayedAddressSecond, component, localAddressSecond);

            // try to add the candidate to the component and then only add it to the harvest not
            // redundant (not sure how it could be red. but ...)
            if (component.addLocalCandidate(candidate)) {
                candidates.add(candidate);
            }
            localAddressSecond = null;
            relayedAddressSecond = null;
            return candidates;
        }

        XMPPConnection conn = serviceNode.getConnection();
        JingleChannelIQ ciq = null;

        if (serviceNode != null) {
            final TrackerEntry preferred = serviceNode.getPreferredRelay();
            if (preferred != null) {
                try {
                    ciq = SmackServiceNode.getChannel(conn, preferred.getJid());
                } catch (SmackException.NotConnectedException | InterruptedException e) {
                    Timber.e("Could not get JingleNodes channel: %s", e.getMessage());
                }
            }
        }

        if (ciq != null) {
            ip = ciq.getHost();
            port = ciq.getRemoteport();

            Timber.i("JN relay: %s remote port: %s local port: %s", ip, port, ciq.getLocalport());

            if (ip == null || ciq.getRemoteport() == 0) {
                Timber.w("JN relay ignored because ip was null or port 0");
                return candidates;
            }

            // Drop the scope or interface name if the relay sends it along in its IPv6 address.
            // The scope/ifname is only valid on host that owns the IP and we don't need it here.
            int scopeIndex = ip.indexOf('%');
            if (scopeIndex > 0) {
                Timber.w("Dropping scope from assumed IPv6 address %s", ip);
                ip = ip.substring(0, scopeIndex);
            }

            /* RTP */
            TransportAddress relayedAddress = new TransportAddress(ip, port, Transport.UDP);
            TransportAddress localAddress = new TransportAddress(ip, ciq.getLocalport(), Transport.UDP);

            LocalCandidate local = createJingleNodesCandidate(relayedAddress, component, localAddress);

            /* RTCP */
            relayedAddressSecond = new TransportAddress(ip, port + 1, Transport.UDP);
            localAddressSecond = new TransportAddress(ip, ciq.getLocalport() + 1, Transport.UDP);

            // try to add the candidate to the component and then only add it to
            // the harvest not redundant (not sure how it could be red. but ...)
            if (component.addLocalCandidate(local)) {
                candidates.add(local);
            }
        }
        return candidates;
    }

    /**
     * Creates a new <tt>JingleNodesRelayedCandidate</tt> instance which is to represent a specific
     * <tt>TransportAddress</tt>.
     *
     * @param transportAddress the <tt>TransportAddress</tt> allocated by the relay
     * @param component the <tt>Component</tt> for which the candidate will be added
     * @param localEndPoint <tt>TransportAddress</tt> of the Jingle Nodes relay where we will send our packet.
     * @return a new <tt>JingleNodesRelayedCandidate</tt> instance which represents the specified
     * <tt>TransportAddress</tt>
     */
    protected JingleNodesCandidate createJingleNodesCandidate(TransportAddress transportAddress,
            Component component, TransportAddress localEndPoint)
    {
        JingleNodesCandidate candidate = null;
        try {
            candidate = new JingleNodesCandidate(transportAddress, component, localEndPoint);
            IceSocketWrapper stunSocket = candidate.getStunSocket(null);
            candidate.getStunStack().addSocket(stunSocket);
            // cmeng ice4j-v2.0
            component.getComponentSocket().add(candidate.getCandidateIceSocketWrapper());
        } catch (Throwable e) {
            Timber.i("Exception occurred when creating JingleNodesCandidate: %s", e.getMessage());
        }
        return candidate;
    }
}
