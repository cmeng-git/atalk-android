/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.PushSourceStream;
import javax.media.protocol.SourceTransferHandler;

/**
 * Represents a <code>PushBufferStream</code> which reads its data from a specific
 * <code>PushSourceStream</code>.
 *
 * @author Lyubomir Marinov
 */
public class PushBufferStreamAdapter extends BufferStreamAdapter<PushSourceStream>
		implements PushBufferStream
{

	/**
	 * Initializes a new <code>PushBufferStreamAdapter</code> instance which reads its data from a
	 * specific <code>PushSourceStream</code> with a specific <code>Format</code>
	 *
	 * @param stream
	 * 		the <code>PushSourceStream</code> the new instance is to read its data from
	 * @param format
	 * 		the <code>Format</code> of the specified input <code>stream</code> and of the new instance
	 */
	public PushBufferStreamAdapter(PushSourceStream stream, Format format)
	{
		super(stream, format);
	}

	/**
	 * Implements PushBufferStream#read(Buffer). Delegates to the wrapped PushSourceStream by
	 * allocating a new byte[] buffer of size equal to PushSourceStream#getMinimumTransferSize().
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> to read
	 * @throws IOException
	 * 		if I/O related errors occurred during read operation
	 */
	public void read(Buffer buffer)
			throws IOException
	{
		byte[] data = (byte[]) buffer.getData();
		int minimumTransferSize = stream.getMinimumTransferSize();

		if ((data == null) || (data.length < minimumTransferSize)) {
			data = new byte[minimumTransferSize];
			buffer.setData(data);
		}

		buffer.setOffset(0);
		read(buffer, data, 0, minimumTransferSize);
	}

	/**
	 * Implements <code>BufferStreamAdapter#doRead(Buffer, byte[], int, int)</code>. Delegates to the
	 * wrapped <code>PushSourceStream</code>.
	 *
	 * @param buffer
	 * @param data
	 * 		byte array to read
	 * @param offset
	 * 		offset to start reading
	 * @param length
	 * 		length to read
	 * @return number of bytes read
	 * @throws IOException
	 * 		if I/O related errors occurred during read operation
	 */
	@Override
	protected int doRead(Buffer buffer, byte[] data, int offset, int length)
			throws IOException
	{
		return stream.read(data, offset, length);
	}

	/**
	 * Implements PushBufferStream#setTransferHandler(BufferTransferHandler). Delegates to the
	 * wrapped PushSourceStream by translating the specified BufferTransferHandler to a
	 * SourceTransferHandler.
	 *
	 * @param transferHandler
	 * 		a <code>BufferTransferHandler</code> to set
	 */
	public void setTransferHandler(final BufferTransferHandler transferHandler)
	{
		stream.setTransferHandler(new SourceTransferHandler()
		{
			public void transferData(PushSourceStream stream)
			{
				transferHandler.transferData(PushBufferStreamAdapter.this);
			}
		});
	}
}
