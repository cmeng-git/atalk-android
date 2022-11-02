/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform;

import org.atalk.impl.neomedia.RTPConnectorInputStream;
import org.atalk.service.neomedia.RawPacket;

import java.io.Closeable;
import java.net.DatagramPacket;

/**
 * Extends <code>RTPConnectorInputStream</code> with transform logic.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class TransformInputStream<T extends Closeable> extends RTPConnectorInputStream<T>
{
    /**
     * The user defined <code>PacketTransformer</code> which is used to reverse transform packets.
     */
    private PacketTransformer transformer;

    /**
     * Initializes a new <code>TransformInputStream</code> which is to transform the packets received
     * from a specific (network) socket.
     *
     * @param socket the (network) socket from which packets are to be received and transformed by the new instance
     */
    protected TransformInputStream(T socket)
    {
        super(socket);
    }

    /**
     * Creates a new <code>RawPacket</code> array from a specific <code>DatagramPacket</code> in order to
     * have this instance receive its packet data through its {@link #read(byte[], int, int)}
     * method. Reverse-transforms the received packet.
     *
     * @param datagramPacket the <code>DatagramPacket</code> containing the packet data
     * @return a new <code>RawPacket</code> array containing the packet data of the specified
     * <code>DatagramPacket</code> or possibly its modification; <code>null</code> to ignore the
     * packet data of the specified <code>DatagramPacket</code> and not make it available to
     * this instance through its {@link #read(byte[], int, int)} method
     * @see RTPConnectorInputStream#createRawPacket(DatagramPacket)
     */
    @Override
    protected RawPacket[] createRawPacket(DatagramPacket datagramPacket)
    {
        RawPacket[] pkts = super.createRawPacket(datagramPacket);

        // Don't try to transform invalid (e.g. empty) packets.
        for (int i = 0; i < pkts.length; i++) {
            RawPacket pkt = pkts[i];
            if (pkt != null && pkt.isInvalid())
                pkts[i] = null; // null elements are ignored
        }
        PacketTransformer transformer = getTransformer();
        return (transformer == null) ? pkts : transformer.reverseTransform(pkts);
    }

    /**
     * Gets the <code>PacketTransformer</code> which is used to reverse-transform packets.
     *
     * @return the <code>PacketTransformer</code> which is used to reverse-transform packets
     */
    public PacketTransformer getTransformer()
    {
        return transformer;
    }

    /**
     * Sets the <code>PacketTransformer</code> which is to be used to reverse-transform packets. Set to
     * <code>null</code> to disable transformation.
     *
     * @param transformer the <code>PacketTransformer</code> which is to be used to reverse-transform packets.
     */
    public void setTransformer(PacketTransformer transformer)
    {
        this.transformer = transformer;
    }
}
