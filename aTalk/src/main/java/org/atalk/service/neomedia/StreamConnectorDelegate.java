/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.net.DatagramSocket;
import java.net.Socket;

/**
 * Implements a {@link StreamConnector} which wraps a specific <code>StreamConnector</code> instance.
 *
 * @param <T>
 *        the very type of the <code>StreamConnector</code> wrapped by <code>StreamConnectorDelegate</code>
 *
 * @author Lyubomir Marinov
 */
public class StreamConnectorDelegate<T extends StreamConnector> implements StreamConnector
{
	/**
	 * The <code>StreamConnector</code> wrapped by this instance.
	 */
	protected final T streamConnector;

	/**
	 * Initializes a new <code>StreamConnectorDelegate</code> which is to wrap a specific
	 * <code>StreamConnector</code>.
	 *
	 * @param streamConnector
	 *        the <code>StreamConnector</code> to be wrapped by the new instance
	 */
	public StreamConnectorDelegate(T streamConnector)
	{
		if (streamConnector == null)
			throw new NullPointerException("streamConnector");

		this.streamConnector = streamConnector;
	}

	/**
	 * Releases the resources allocated by this instance in the course of its execution and prepares
	 * it to be garbage collected. Calls {@link StreamConnector#close()} on the
	 * <code>StreamConnector</code> wrapped by this instance.
	 */
	@Override
	public void close()
	{
		streamConnector.close();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatagramSocket getControlSocket()
	{
		return streamConnector.getControlSocket();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Socket getControlTCPSocket()
	{
		return streamConnector.getControlTCPSocket();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatagramSocket getDataSocket()
	{
		return streamConnector.getDataSocket();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Socket getDataTCPSocket()
	{
		return streamConnector.getDataTCPSocket();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Protocol getProtocol()
	{
		return streamConnector.getProtocol();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void started()
	{
		streamConnector.started();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void stopped()
	{
		streamConnector.stopped();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isRtcpmux()
	{
		return streamConnector.isRtcpmux();
	}
}
