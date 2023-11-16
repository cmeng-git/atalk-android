/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform;

import org.atalk.impl.neomedia.RTCPConnectorInputStream;
import org.atalk.impl.neomedia.RTPConnectorUDPInputStream;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.LinkedList;
import java.util.List;

import javax.media.Buffer;

/**
 * Implement control channel (RTCP) for <code>TransformInputStream</code> which notify listeners when
 * RTCP feedback messages are received.
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class ControlTransformInputStream extends RTPConnectorUDPInputStream
{
	/**
	 * The list of <code>RTCPFeedbackMessageListener</code>s.
	 */
	private final List<RTCPFeedbackMessageListener> listeners = new LinkedList<>();

	/**
	 * Initializes a new <code>ControlTransformInputStream</code> which is to receive packet data from a
	 * specific UDP socket.
	 *
	 * @param socket
	 *        the UDP socket the new instance is to receive data from
	 */
	public ControlTransformInputStream(DatagramSocket socket)
	{
		super(socket);
	}

	/**
	 * Adds an <code>RTCPFeedbackMessageListener</code>.
	 *
	 * @param listener
	 *        the <code>RTCPFeedbackMessageListener</code> to add
	 */
	public void addRTCPFeedbackMessageListener(RTCPFeedbackMessageListener listener)
	{
		if (listener == null)
			throw new NullPointerException("listener");
		synchronized (listeners) {
			if (!listeners.contains(listener))
				listeners.add(listener);
		}
	}

	/**
	 * Removes an <code>RTCPFeedbackMessageListener</code>.
	 *
	 * @param listener
	 *        the <code>RTCPFeedbackMessageListener</code> to remove
	 */
	public void removeRTCPFeedbackMessageListener(RTCPFeedbackMessageListener listener)
	{
		if (listener != null) {
			synchronized (listeners) {
				listeners.remove(listener);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int read(Buffer buffer, byte[] data, int offset, int length)
		throws IOException
	{
		int pktLength = super.read(buffer, data, offset, length);

		RTCPConnectorInputStream.fireRTCPFeedbackMessageReceived(this, data, offset, pktLength,
			listeners);

		return pktLength;
	}
}
