/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.ice4j.TransportAddress;
import org.ice4j.socket.StunDatagramPacketFilter;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

import timber.log.Timber;

/**
 * Represents an application-purposed (as opposed to an ICE-specific) <code>DatagramSocket</code> for a
 * <code>JingleNodesCandidate</code>.
 *
 * @author Sebastien Vincent
 */
public class JingleNodesCandidateDatagramSocket extends DatagramSocket
{
    /**
     * Logs information about RTP losses if there is more then 5% of RTP packet lost (at most every
     * 5 seconds).
     *
     * @param totalNbLost The total number of lost packet since the beginning of this stream.
     * @param totalNbReceived The total number of received packet since the beginning of this stream.
     * @param lastLogTime The last time we have logged information about RTP losses.
     * @return the last log time updated if this function as log new information about RTP losses.
     * Otherwise, returns the same last log time value as given in parameter.
     */
    private static long logRtpLosses(long totalNbLost, long totalNbReceived, long lastLogTime)
    {
        double percentLost = ((double) totalNbLost) / ((double) (totalNbLost + totalNbReceived));
        // Log the information if the loss rate is upper 5% and if the last log is before 5 seconds.
        if (percentLost > 0.05) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogTime >= 5000) {
                Timber.i("RTP lost > 5%: %s", percentLost);
                return currentTime;
            }
        }
        return lastLogTime;
    }

    /**
     * Return the number of loss between the 2 last RTP packets received.
     *
     * @param lastRtpSequenceNumber The previous RTP sequence number.
     * @param newSeq The current RTP sequence number.
     * @return the number of loss between the 2 last RTP packets received.
     */
    private static long getNbLost(long lastRtpSequenceNumber, long newSeq)
    {
        long newNbLost = 0;
        if (lastRtpSequenceNumber <= newSeq) {
            newNbLost = newSeq - lastRtpSequenceNumber;
        }
        else {
            newNbLost = (0xffff - lastRtpSequenceNumber) + newSeq;
        }

        if (newNbLost > 1) {
            if (newNbLost < 0x00ff) {
                return newNbLost - 1;
            }
            // Else the paquet is desequenced, then count it as a single loss.
            else {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Determines the sequence number of an RTP packet.
     *
     * @param p the last RTP packet received.
     * @return The last RTP sequence number.
     */
    private static long getRtpSequenceNumber(DatagramPacket p)
    {
        // The sequence number is contained in the third and fourth bytes of the RTP header (stored in big endian).
        byte[] data = p.getData();
        int offset = p.getOffset();
        long seq_high = data[offset + 2] & 0xff;
        long seq_low = data[offset + 3] & 0xff;

        return seq_high << 8 | seq_low;
    }

    /**
     * <code>TransportAddress</code> of the Jingle Nodes relay where we will send our packet.
     */
    private TransportAddress localEndPoint = null;

    /**
     * The <code>JingleNodesCandidate</code>.
     */
    private JingleNodesCandidate jingleNodesCandidate;

    /**
     * The number of RTP packets received for this socket.
     */
    private long nbReceivedRtpPackets = 0;

    /**
     * The number of RTP packets sent for this socket.
     */
    private long nbSentRtpPackets = 0;

    /**
     * The number of RTP packets lost (not received) for this socket.
     */
    private long nbLostRtpPackets = 0;

    /**
     * The last RTP sequence number received for this socket.
     */
    private long lastRtpSequenceNumber = -1;

    /**
     * The last time an information about packet lost has been logged.
     */
    private long lastLostPacketLogTime = 0;

    /**
     * Initializes a new <code>JingleNodesdCandidateDatagramSocket</code> instance which is to be the
     * <code>socket</code> of a specific <code>JingleNodesCandidate</code>.
     *
     * @param jingleNodesCandidate the <code>JingleNodesCandidate</code> which is to use the new instance as
     * the value of its <code>socket</code> property
     * @param localEndPoint <code>TransportAddress</code> of the Jingle Nodes relay where we will send our packet.
     * @throws SocketException if anything goes wrong while initializing the new
     * <code>JingleNodesCandidateDatagramSocket</code> instance
     */
    public JingleNodesCandidateDatagramSocket(JingleNodesCandidate jingleNodesCandidate, TransportAddress localEndPoint)
            throws SocketException
    {
        super(/* bindaddr */(SocketAddress) null);
        this.jingleNodesCandidate = jingleNodesCandidate;
        this.localEndPoint = localEndPoint;
    }

    /**
     * Sends a datagram packet from this socket. The <code>DatagramPacket</code> includes information indicating
     * the data to be sent, its length, the IP address of the remote host, and the port number on the remote host.
     *
     * @param p the <code>DatagramPacket</code> to be sent
     * @throws IOException if an I/O error occurs
     * @see DatagramSocket#send(DatagramPacket)
     */
    @Override
    public void send(DatagramPacket p)
            throws IOException
    {
        byte data[] = p.getData();
        int dataLen = p.getLength();
        int dataOffset = p.getOffset();

        /* send to Jingle Nodes relay address on local port */
        DatagramPacket packet = new DatagramPacket(data, dataOffset, dataLen,
                new InetSocketAddress(localEndPoint.getAddress(), localEndPoint.getPort()));

        // XXX reuse an existing DatagramPacket ?
        super.send(packet);
    }

    /**
     * Receives a <code>DatagramPacket</code> from this socket. The DatagramSocket is overridden to log
     * the received packet.
     *
     * @param p <code>DatagramPacket</code>
     * @throws IOException if something goes wrong
     */
    @Override
    public void receive(DatagramPacket p)
            throws IOException
    {
        super.receive(p);
        // Log RTP losses if > 5%.
        updateRtpLosses(p);
    }

    /**
     * Gets the local address to which the socket is bound.
     * <code>JingleNodesCandidateDatagramSocket</code> returns the <code>address</code> of its
     * <code>localSocketAddress</code>.
     *
     * If there is a security manager, its <code>checkConnect</code> method is first called with the
     * host address and <code>-1</code> as its arguments to see if the operation is allowed.
     *
     * @return the local address to which the socket is bound, or an <code>InetAddress</code>
     * representing any local address if either the socket is not bound, or the security
     * manager <code>checkConnect</code> method does not allow the operation
     * @see #getLocalSocketAddress()
     * @see DatagramSocket#getLocalAddress()
     */
    @Override
    public InetAddress getLocalAddress()
    {
        return getLocalSocketAddress().getAddress();
    }

    /**
     * Returns the port number on the local host to which this socket is bound.
     * <code>JingleNodesCandidateDatagramSocket</code> returns the <code>port</code> of its
     * <code>localSocketAddress</code>.
     *
     * @return the port number on the local host to which this socket is bound
     * @see #getLocalSocketAddress()
     * @see DatagramSocket#getLocalPort()
     */
    @Override
    public int getLocalPort()
    {
        return getLocalSocketAddress().getPort();
    }

    /**
     * Returns the address of the endpoint this socket is bound to, or <code>null</code> if it is not
     * bound yet. Since <code>JingleNodesCandidateDatagramSocket</code> represents an
     * application-purposed <code>DatagramSocket</code> relaying data to and from a Jingle Nodes relay,
     * the <code>localSocketAddress</code> is the <code>transportAddress</code> of respective
     * <code>JingleNodesCandidate</code>.
     *
     * @return a <code>SocketAddress</code> representing the local endpoint of this socket, or
     * <code>null</code> if it is not bound yet
     * @see DatagramSocket#getLocalSocketAddress()
     */
    @Override
    public InetSocketAddress getLocalSocketAddress()
    {
        return jingleNodesCandidate.getTransportAddress();
    }

    /**
     * Updates and Logs information about RTP losses if there is more then 5% of RTP packet lost (at
     * most every 5 seconds).
     *
     * @param p The last packet received.
     */
    private void updateRtpLosses(DatagramPacket p)
    {
        // If this is not a STUN/TURN packet, then this is a RTP packet.
        if (!StunDatagramPacketFilter.isStunPacket(p)) {
            long newSeq = getRtpSequenceNumber(p);
            if (this.lastRtpSequenceNumber != -1) {
                nbLostRtpPackets += getNbLost(this.lastRtpSequenceNumber, newSeq);
            }
            this.lastRtpSequenceNumber = newSeq;
            this.lastLostPacketLogTime
                    = logRtpLosses(this.nbLostRtpPackets, this.nbReceivedRtpPackets, this.lastLostPacketLogTime);
        }
    }
}
