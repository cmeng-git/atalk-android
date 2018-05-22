/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import java.io.*;
import java.net.*;

import org.atalk.impl.neomedia.transform.TransformInputStream;

/**
 * RTPConnectorInputStream implementation for UDP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorUDPInputStream extends TransformInputStream<DatagramSocket>
{
	/**
	 * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive packet data from a
	 * specific UDP socket.
	 *
	 * @param socket
	 *        the UDP socket the new instance is to receive data from
	 */
	public RTPConnectorUDPInputStream(DatagramSocket socket)
	{
		super(socket);
	}

	/**
	 * Receive packet.
	 *
	 * @param p
	 *        packet for receiving
	 * @throws IOException
	 *         if something goes wrong during receiving
	 */
	@Override
	protected void receive(DatagramPacket p)
		throws IOException
	{
		socket.receive(p);
	}

	@Override
	protected void setReceiveBufferSize(int receiveBufferSize)
		throws IOException
	{
		socket.setReceiveBufferSize(receiveBufferSize);
	}
}
