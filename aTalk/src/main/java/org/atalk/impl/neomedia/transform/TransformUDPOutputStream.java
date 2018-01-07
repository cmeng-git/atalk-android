/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform;

import org.atalk.impl.neomedia.RTPConnectorUDPOutputStream;
import org.atalk.service.neomedia.RawPacket;

import java.net.DatagramSocket;

/**
 * Extends <tt>RTPConnectorUDPOutputStream</tt> with transform logic.
 *
 * In this implementation, UDP socket is used to send the data out. When a normal RTP/RTCP packet is
 * passed down from RTPManager, we first transform the packet using user define PacketTransformer
 * and then send it out through network to all the stream targets.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lubomir Marinov
 */
public class TransformUDPOutputStream extends RTPConnectorUDPOutputStream
		implements TransformOutputStream
{
	/**
	 * The {@code TransformOutputStream} which aids this instance in implementing the interface in
	 * question.
	 */
	private final TransformOutputStreamImpl _impl;

	/**
	 * Initializes a new <tt>TransformOutputStream</tt> which is to send packet data out through a
	 * specific UDP socket.
	 *
	 * @param socket
	 *        the UDP socket used to send packet data out
	 */
	public TransformUDPOutputStream(DatagramSocket socket)
	{
		super(socket);
		_impl = new TransformOutputStreamImpl(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PacketTransformer getTransformer()
	{
		return _impl.getTransformer();
	}

	/**
	 * {@inheritDoc}
	 *
	 * Transforms the array of {@code RawPacket}s returned by the super
	 * {@link #packetize(byte[],int,int,Object)} implementation using the associated
	 * {@code PacketTransformer}.
	 */
	@Override
	protected RawPacket[] packetize(byte[] buf, int off, int len, Object context)
	{
		RawPacket[] pkts = super.packetize(buf, off, len, context);
		return _impl.transform(pkts, context);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setTransformer(PacketTransformer transformer)
	{
		_impl.setTransformer(transformer);
	}
}
