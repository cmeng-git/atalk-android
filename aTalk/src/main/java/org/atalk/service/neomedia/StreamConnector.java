/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.net.DatagramSocket;
import java.net.Socket;

/**
 * The <code>StreamConnector</code> interface represents a pair of datagram sockets that a media stream
 * could use for RTP and RTCP traffic.
 * <p>
 * The reason why this media service makes sockets visible through this <code>StreamConnector</code> is
 * so that they could be shared among media and other libraries that may need to use them like an
 * ICE implementation for example.
 *
 * @author Emil Ivov
 */
public interface StreamConnector
{
	/**
	 * Enumerates the protocols supported by <code>StreamConnector</code>.
	 */
	enum Protocol {
		/**
		 * UDP protocol.
		 */
		UDP,

		/**
		 * TCP protocol.
		 */
		TCP
	}

	/**
	 * Returns a reference to the <code>DatagramSocket</code> that a stream should use for data (e.g.
	 * RTP) traffic.
	 *
	 * @return a reference to the <code>DatagramSocket</code> that a stream should use for data (e.g.
	 *         RTP) traffic or <code>null</code> if this <code>StreamConnector</code> does not handle UDP
	 *         sockets.
	 */
	public DatagramSocket getDataSocket();

	/**
	 * Returns a reference to the <code>DatagramSocket</code> that a stream should use for control data
	 * (e.g. RTCP).
	 *
	 * @return a reference to the <code>DatagramSocket</code> that a stream should use for control data
	 *         (e.g. RTCP) or <code>null</code> if this <code>StreamConnector</code> does not handle UDP
	 *         sockets.
	 */
	public DatagramSocket getControlSocket();

	/**
	 * Returns a reference to the <code>Socket</code> that a stream should use for data (e.g. RTP)
	 * traffic.
	 *
	 * @return a reference to the <code>Socket</code> that a stream should use for data (e.g. RTP)
	 *         traffic or <code>null</code> if this <code>StreamConnector</code> does not handle TCP
	 *         sockets.
	 */
	public Socket getDataTCPSocket();

	/**
	 * Returns a reference to the <code>Socket</code> that a stream should use for control data (e.g.
	 * RTCP).
	 *
	 * @return a reference to the <code>Socket</code> that a stream should use for control data (e.g.
	 *         RTCP) or <code>null</code> if this <code>StreamConnector</code> does not handle TCP sockets.
	 */
	public Socket getControlTCPSocket();

	/**
	 * Returns the protocol of this <code>StreamConnector</code>.
	 *
	 * @return the protocol of this <code>StreamConnector</code>
	 */
	public Protocol getProtocol();

	/**
	 * Releases the resources allocated by this instance in the course of its execution and prepares
	 * it to be garbage collected.
	 */
	public void close();

	/**
	 * Notifies this instance that utilization of its <code>DatagramSocket</code>s for data and/or
	 * control traffic has started.
	 */
	public void started();

	/**
	 * Notifies this instance that utilization of its <code>DatagramSocket</code>s for data and/or
	 * control traffic has temporarily stopped. This instance should be prepared to be started at a
	 * later time again though.
	 */
	public void stopped();

	/**
	 * Returns <code>true</code> if this <code>StreamConnector</code> uses rtcp-mux, that is, if its data
	 * and control sockets share the same local address and port.
	 * 
	 * @return <code>true</code> if this <code>StreamConnector</code> uses rtcp-mux, that is, if its data
	 *         and control sockets share the same local address and port.
	 */
	public boolean isRtcpmux();
}
