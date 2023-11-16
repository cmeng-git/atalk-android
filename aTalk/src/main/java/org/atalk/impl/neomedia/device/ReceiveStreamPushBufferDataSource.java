/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.protocol.NullBufferTransferHandler;
import org.atalk.impl.neomedia.protocol.PushBufferDataSourceDelegate;

import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.rtp.ReceiveStream;

/**
 * Wraps the <code>DataSource</code> of a specific <code>ReceiveStream</code> so that calls to its
 * {@link DataSource#disconnect()} can be explicitly controlled. It is introduced because it seems
 * that after the <code>DataSource</code> of a <code>ReceiveStream</code> is disconnected, it cannot be
 * connected to or started and if a <code>Processor</code> is created on it, it freezes in the
 * {@link javax.media.Processor#Configuring} state.
 *
 * @author Lyubomir Marinov
 */
public class ReceiveStreamPushBufferDataSource extends
		PushBufferDataSourceDelegate<PushBufferDataSource>
{
	/**
	 * Sets a <code>BufferTransferHandler</code> on a specific <code>ReceiveStream</code> which reads data
	 * as soon as possible and throws it away.
	 *
	 * @param receiveStream
	 *        the <code>ReceiveStream</code> on which to set a <code>BufferTransferHandler</code> which
	 *        reads data as soon as possible and throws it away
	 */
	public static void setNullTransferHandler(ReceiveStream receiveStream)
	{
		DataSource dataSource = receiveStream.getDataSource();

		if (dataSource != null) {
			if (dataSource instanceof PushBufferDataSource) {
				PushBufferStream[] streams = ((PushBufferDataSource) dataSource).getStreams();

				if ((streams != null) && (streams.length != 0)) {
					for (PushBufferStream stream : streams) {
						stream.setTransferHandler(new NullBufferTransferHandler());
					}
				}

				// If data is to be read as soon as possible and thrown away,
				// it sounds reasonable that buffering while stopped should be
				// disabled.
				if (dataSource instanceof net.sf.fmj.media.protocol.rtp.DataSource) {
					((net.sf.fmj.media.protocol.rtp.DataSource) dataSource)
						.setBufferWhenStopped(false);
				}
			}
		}
	}

	/**
	 * The <code>ReceiveStream</code> which has its <code>DataSource</code> wrapped by this instance.
	 * Currently, remembered just to be made available to callers in case they need it and not used
	 * by this instance.
	 */
	private final ReceiveStream receiveStream;

	/**
	 * The indicator which determines whether {@link DataSource#disconnect()} is to be called on the
	 * wrapped <code>DataSource</code> when it is called on this instance.
	 */
	private boolean suppressDisconnect;

	/**
	 * Initializes a new <code>ReceiveStreamPushBufferDataSource</code> instance which is to wrap a
	 * specific <code>DataSource</code> of a specific <code>ReceiveStream</code> for the purposes of
	 * enabling explicitly control of calls to its {@link DataSource#disconnect()}.
	 *
	 * @param receiveStream
	 *        the <code>ReceiveStream</code> which is to have its <code>DataSource</code>
	 * @param dataSource
	 *        the <code>DataSource</code> of <code>receiveStream</code> which is to be wrapped by this
	 *        instance
	 */
	public ReceiveStreamPushBufferDataSource(ReceiveStream receiveStream,
		PushBufferDataSource dataSource)
	{
		super(dataSource);

		this.receiveStream = receiveStream;
	}

	/**
	 * Initializes a new <code>ReceiveStreamPushBufferDataSource</code> instance which is to wrap a
	 * specific <code>DataSource</code> of a specific <code>ReceiveStream</code> for the purposes of
	 * enabling explicitly control of calls to its {@link DataSource#disconnect()} and, optionally,
	 * activates the suppresses the call in question.
	 *
	 * @param receiveStream
	 *        the <code>ReceiveStream</code> which is to have its <code>DataSource</code>
	 * @param dataSource
	 *        the <code>DataSource</code> of <code>receiveStream</code> which is to be wrapped by this
	 *        instance
	 * @param suppressDisconnect
	 *        <code>true</code> if calls to <code>DataSource#disconnect()</code> on the wrapped
	 *        <code>dataSource</code> are to be suppressed when there are such calls on the new
	 *        instance; otherwise, <code>false</code>
	 */
	public ReceiveStreamPushBufferDataSource(ReceiveStream receiveStream,
		PushBufferDataSource dataSource, boolean suppressDisconnect)
	{
		this(receiveStream, dataSource);

		setSuppressDisconnect(suppressDisconnect);
	}

	/**
	 * Implements {@link DataSource#disconnect()}. Disconnects the wrapped <code>DataSource</code> if it
	 * has not been explicitly suppressed by setting the <code>suppressDisconnect</code> property of
	 * this instance.
	 */
	@Override
	public void disconnect()
	{
		if (!suppressDisconnect)
			super.disconnect();
	}

	/**
	 * Gets the <code>ReceiveStream</code> which has its <code>DataSource</code> wrapped by this instance.
	 *
	 * @return the <code>ReceiveStream</code> which has its <code>DataSource</code> wrapped by this instance
	 */
	public ReceiveStream getReceiveStream()
	{
		return receiveStream;
	}

	/**
	 * Implements {@link PushBufferDataSource#getStreams()}. Delegates to the wrapped
	 * <code>DataSource</code> of the <code>ReceiveStream</code>.
	 *
	 * @return an array of the <code>PushBufferStream</code>s of the wrapped <code>DataSource</code> of the
	 *         <code>ReceiveStream</code>
	 */
	@Override
	public PushBufferStream[] getStreams()
	{
		return dataSource.getStreams();
	}

	/**
	 * Sets the indicator which determines whether calls to {@link DataSource#disconnect()} on the
	 * wrapped <code>DataSource</code> are to be suppressed when there are such calls on this instance.
	 *
	 * @param suppressDisconnect
	 *        <code>true</code> to suppress calls to <code>DataSource#disconnect()</code> on the wrapped
	 *        <code>DataSource</code> when there are such calls on this instance; otherwise,
	 *        <code>false</code>
	 */
	public void setSuppressDisconnect(boolean suppressDisconnect)
	{
		this.suppressDisconnect = suppressDisconnect;
	}
}
