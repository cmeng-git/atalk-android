/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import javax.media.control.FormatControl;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;

/**
 * Provides a base implementation of <code>PushBufferStream</code> in order to facilitate implementers
 * by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractPushBufferStream<T extends PushBufferDataSource>
		extends AbstractBufferStream<T> implements PushBufferStream
{
	/**
	 * The name of the <code>PushBufferStream</code> class.
	 */
	public static final String PUSH_BUFFER_STREAM_CLASS_NAME = PushBufferStream.class.getName();

	/**
	 * The <code>BufferTransferHandler</code> which is notified by this <code>PushBufferStream</code> when
	 * data is available for reading.
	 */
	protected BufferTransferHandler transferHandler;

	/**
	 * Initializes a new <code>AbstractPushBufferStream</code> instance which is to have its
	 * <code>Format</code>-related information abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param dataSource
	 * 		the <code>PushBufferDataSource</code> which is creating the new instance so that it
	 * 		becomes one of its <code>streams</code>
	 * @param formatControl
	 * 		the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 * 		information of the new instance
	 */
	protected AbstractPushBufferStream(T dataSource, FormatControl formatControl)
	{
		super(dataSource, formatControl);
	}

	/**
	 * Sets the <code>BufferTransferHandler</code> which is to be notified by this
	 * <code>PushBufferStream</code> when data is available for reading.
	 *
	 * @param transferHandler
	 * 		the <code>BufferTransferHandler</code> which is to be notified by this
	 * 		<code>PushBufferStream</code> when data is available for reading
	 */
	public void setTransferHandler(BufferTransferHandler transferHandler)
	{
		this.transferHandler = transferHandler;
	}
}
