/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.service.neomedia.RawPacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * RTPConnectorOutputStream implementation for UDP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorUDPOutputStream extends RTPConnectorOutputStream
{
    /**
     * UDP socket used to send packet data
     */
    private final DatagramSocket socket;

    /**
     * Initializes a new <code>RTPConnectorUDPOutputStream</code>.
     *
     * @param socket a <code>DatagramSocket</code>
     */
    public RTPConnectorUDPOutputStream(DatagramSocket socket)
    {
        this.socket = socket;
    }

    /**
     * Sends a specific <code>RawPacket</code> through this <code>OutputDataStream</code> to a specific <code>InetSocketAddress</code>.
     *
     * @param packet the <code>RawPacket</code> to send through this <code>OutputDataStream</code> to the specified <code>target</code>
     * @param target the <code>InetSocketAddress</code> to which the specified <code>packet</code> is to be sent
     * through this <code>OutputDataStream</code>
     * @throws IOException if anything goes wrong while sending the specified <code>packet</code> through this
     * <code>OutputDataStream</code> to the specified <code>target</code>
     */
    @Override
    protected void sendToTarget(RawPacket packet, InetSocketAddress target)
            throws IOException
    {
        socket.send(new DatagramPacket(packet.getBuffer(), packet.getOffset(), packet.getLength(),
                target.getAddress(), target.getPort()));
    }

    /**
     * Returns whether or not this <code>RTPConnectorOutputStream</code> has a valid socket.
     *
     * @returns true if this <code>RTPConnectorOutputStream</code> has a valid socket, false otherwise
     */
    @Override
    protected boolean isSocketValid()
    {
        return (socket != null);
    }
}
