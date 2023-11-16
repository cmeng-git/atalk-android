/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.ice4j.TransportAddress;
import org.ice4j.ice.CandidateExtendedType;
import org.ice4j.ice.CandidateType;
import org.ice4j.ice.Component;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.socket.IceSocketWrapper;
import org.ice4j.socket.IceUdpSocketWrapper;
import org.ice4j.socket.MultiplexingDatagramSocket;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.SocketException;

/**
 * Represents a <code>Candidate</code> obtained via Jingle Nodes.
 *
 * @author Sebastien Vincent
 */
public class JingleNodesCandidate extends LocalCandidate
{
    /**
     * The socket used to communicate with relay.
     */
    private IceSocketWrapper socket = null;

    /**
     * The <code>RelayedCandidateDatagramSocket</code> of this <code>JingleNodesCandidate</code>.
     */
    private JingleNodesCandidateDatagramSocket jingleNodesCandidateDatagramSocket = null;

    /**
     * <code>TransportAddress</code> of the Jingle Nodes relay where we will send our packet.
     */
    private TransportAddress localEndPoint = null;

    /**
     * Creates a <code>JingleNodesRelayedCandidate</code> for the specified transport, address, and base.
     *
     * @param transportAddress the transport address that this candidate is encapsulating.
     * @param parentComponent the <code>Component</code> that this candidate belongs to.
     * @param localEndPoint <code>TransportAddress</code> of the Jingle Nodes relay where we will send our packet.
     */
    public JingleNodesCandidate(TransportAddress transportAddress, Component parentComponent,
            TransportAddress localEndPoint)
    {
        super(transportAddress, parentComponent, CandidateType.RELAYED_CANDIDATE,
                CandidateExtendedType.JINGLE_NODE_CANDIDATE, null);
        setBase(this);
        setRelayServerAddress(localEndPoint);
        this.localEndPoint = localEndPoint;
    }

    /**
     * Gets the <code>JingleNodesCandidateDatagramSocket</code> of this <code>JingleNodesCandidate</code>.
     *
     * <b>Note</b>: The method is part of the internal API of <code>RelayedCandidate</code> and
     * <code>TurnCandidateHarvest</code> and is not intended for public use.
     *
     * @return the <code>RelayedCandidateDatagramSocket</code> of this <code>RelayedCandidate</code>
     */
    private synchronized JingleNodesCandidateDatagramSocket getRelayedCandidateDatagramSocket()
    {
        if (jingleNodesCandidateDatagramSocket == null) {
            try {
                jingleNodesCandidateDatagramSocket
                        = new JingleNodesCandidateDatagramSocket(this, localEndPoint);
            } catch (SocketException sex) {
                throw new UndeclaredThrowableException(sex);
            }
        }
        return jingleNodesCandidateDatagramSocket;
    }

    /**
     * Gets the <code>DatagramSocket</code> associated with this <code>Candidate</code>.
     *
     * @return the <code>DatagramSocket</code> associated with this <code>Candidate</code>
     */
    @Override
    protected IceSocketWrapper getCandidateIceSocketWrapper()
    {
        if (socket == null) {
            try {
                socket = new IceUdpSocketWrapper(new MultiplexingDatagramSocket(getRelayedCandidateDatagramSocket()));
            } catch (SocketException sex) {
                throw new UndeclaredThrowableException(sex);
            }
        }
        return socket;
    }
}
