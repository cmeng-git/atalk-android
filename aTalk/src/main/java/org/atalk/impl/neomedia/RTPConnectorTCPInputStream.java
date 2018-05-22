/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import java.io.*;
import java.net.*;

import org.atalk.impl.neomedia.transform.TransformInputStream;
import org.atalk.util.Logger;

/**
 * RTPConnectorInputStream implementation for TCP protocol.
 *
 * @author Sebastien Vincent
 */
public class RTPConnectorTCPInputStream extends TransformInputStream<Socket>
{
	/**
	 * The <tt>Logger</tt> used by instances for logging output.
	 */
	private static final Logger logger = Logger.getLogger(RTPConnectorTCPInputStream.class);

	/**
	 * Initializes a new <tt>RTPConnectorInputStream</tt> which is to receive packet data from a
	 * specific TCP socket.
	 *
	 * @param socket
	 *        the TCP socket the new instance is to receive data from
	 */
	public RTPConnectorTCPInputStream(Socket socket)
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
		byte[] data;
		int len;

		try {
			data = p.getData();
			len = socket.getInputStream().read(data);
		}
		catch (Exception e) {
			data = null;
			len = -1;
			logger.info("problem read: " + e);
		}

		if (len > 0) {
			p.setData(data);
			p.setLength(len);
			p.setAddress(socket.getInetAddress());
			p.setPort(socket.getPort());
		}
		else {
			throw new IOException("Failed to read on TCP socket");
		}
	}

	@Override
	protected void setReceiveBufferSize(int receiveBufferSize)
		throws IOException
	{
		socket.setReceiveBufferSize(receiveBufferSize);
	}
}
