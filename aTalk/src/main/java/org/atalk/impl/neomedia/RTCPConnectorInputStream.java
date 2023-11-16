/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;

import javax.media.Buffer;

/**
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 */
public class RTCPConnectorInputStream extends RTPConnectorUDPInputStream
{
	/**
	 * List of RTCP feedback message listeners;
	 */
	private final List<RTCPFeedbackMessageListener> listeners = new ArrayList<>();

	/**
	 * Initializes a new <code>RTCPConnectorInputStream</code> which is to receive packet data from a
	 * specific UDP socket.
	 *
	 * @param socket
	 *        the UDP socket the new instance is to receive data from
	 */
	public RTCPConnectorInputStream(DatagramSocket socket)
	{
		super(socket);
	}

	/**
	 * Add an <code>RTCPFeedbackMessageListener</code>.
	 *
	 * @param listener
	 *        object that will listen to incoming RTCP feedback messages.
	 */
	public void addRTCPFeedbackMessageListener(RTCPFeedbackMessageListener listener)
	{
		if (listener == null)
			throw new NullPointerException("listener");
		if (!listeners.contains(listener))
			listeners.add(listener);
	}

	/**
	 * Notifies a specific list of <code>RTCPFeedbackMessageListener</code>s about a specific RTCP
	 * feedback message if such a message can be parsed out of a specific <code>byte</code> buffer.
	 *
	 * @param source
	 *        the object to be reported as the source of the <code>RTCPFeedbackMessageEvent</code> to be
	 *        fired
	 * @param buffer
	 *        the <code>byte</code> buffer which may specific an RTCP feedback message
	 * @param offset
	 *        the offset in <code>buffer</code> at which the reading of bytes is to begin
	 * @param length
	 *        the number of bytes in <code>buffer</code> to be read for the purposes of parsing an RTCP
	 *        feedback message and firing an <code>RTPCFeedbackEvent</code>
	 * @param listeners
	 *        the list of <code>RTCPFeedbackMessageListener</code>s to be notified about the specified
	 *        RTCP feedback message if such a message can be parsed out of the specified
	 *        <code>buffer</code>
	 */
	public static void fireRTCPFeedbackMessageReceived(Object source, byte[] buffer, int offset,
		int length, List<RTCPFeedbackMessageListener> listeners)
	{
		/*
         * RTCP feedback message length is minimum 12 bytes:
         * 1. Version/Padding/Feedback message type: 1 byte
         * 2. Payload type: 1 byte
         * 3. Length: 2 bytes
         * 4. SSRC of packet sender: 4 bytes
         * 5. SSRC of media source: 4 bytes
		 */
		if ((length >= 12) && !listeners.isEmpty()) {
            int pt = buffer[offset + 1] & 0xFF;

			if ((pt == RTCPFeedbackMessageEvent.PT_PS) || (pt == RTCPFeedbackMessageEvent.PT_TL)) {
				int fmt = buffer[offset] & 0x1F;
				RTCPFeedbackMessageEvent ev = new RTCPFeedbackMessageEvent(source, fmt, pt);

				for (RTCPFeedbackMessageListener l : listeners)
					l.rtcpFeedbackMessageReceived(ev);
			}
		}
	}

	/**
	 * Remove an <code>RTCPFeedbackMessageListener</code>.
	 *
	 * @param listener
	 *        object to remove from listening RTCP feedback messages.
	 */
	public void removeRTCPFeedbackMessageListener(RTCPFeedbackMessageListener listener)
	{
		listeners.remove(listener);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected int read(Buffer buffer, byte[] data, int offset, int length)
		throws IOException
	{
		int pktLength = super.read(buffer, data, offset, length);

		fireRTCPFeedbackMessageReceived(this, data, offset, pktLength, listeners);

		return pktLength;
	}
}
