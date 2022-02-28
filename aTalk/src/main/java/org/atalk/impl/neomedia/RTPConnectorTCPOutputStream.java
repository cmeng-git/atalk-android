/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.service.neomedia.RawPacket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * RTPConnectorOutputStream implementation for TCP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorTCPOutputStream extends RTPConnectorOutputStream
{
    /**
     * TCP socket used to send packet data
     */
    private final Socket socket;

    /**
     * Initializes a new <code>RTPConnectorTCPOutputStream</code>.
     *
     * @param socket a <code>Socket</code>
     */
    public RTPConnectorTCPOutputStream(Socket socket)
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
        socket.getOutputStream().write(packet.getBuffer(), packet.getOffset(), packet.getLength());
    }

    /**
     * Returns whether or not this <code>RTPConnectorOutputStream</code> has a valid socket.
     *
     * @return <code>true</code>if this <code>RTPConnectorOutputStream</code> has a valid socket, and <code>false</code> otherwise.
     */
    @Override
    protected boolean isSocketValid()
    {
        return (socket != null);
    }
}
