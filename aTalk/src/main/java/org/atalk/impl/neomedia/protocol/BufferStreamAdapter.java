/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.SourceStream;

/**
 * Represents a base class for adapters of <code>SourceStream</code>s, usually ones reading data in
 * arrays of bytes and not in <code>Buffer</code>s, to <code>SourceStream</code>s reading data in
 * <code>Buffer</code>s. An example use is creating a PushBufferStream representation of a
 * PushSourceStream.
 *
 * @param <T>
 * 		the very type of <code>SourceStream</code> to be adapted by a <code>BufferStreamAdapter</code>
 * @author Lyubomir Marinov
 */
public abstract class BufferStreamAdapter<T extends SourceStream>
		implements SourceStream
{

	/**
	 * The <code>Format</code> of this stream to be reported through the output <code>Buffer</code> this
	 * instance reads data into.
	 */
	private final Format format;

	/**
	 * The <code>SourceStream</code> being adapted by this instance.
	 */
	protected final T stream;

	/**
	 * Initializes a new <code>BufferStreamAdapter</code> which is to adapt a specific
	 * <code>SourceStream</code> into a <code>SourceStream</code> with a specific <code>Format</code>.
	 *
	 * @param stream
	 * 		the <code>SourceStream</code> to be adapted
	 * @param format
	 * 		the specific <code>Format</code> of the <code>SourceStream</code>
	 */
	public BufferStreamAdapter(T stream, Format format)
	{
		this.stream = stream;
		this.format = format;
	}

	/**
	 * Implements SourceStream#endOfStream(). Delegates to the wrapped SourceStream.
	 *
	 * @return true if the stream is finished, false otherwise
	 */
	public boolean endOfStream()
	{
		return stream.endOfStream();
	}

	/**
	 * Implements SourceStream#getContentDescriptor(). Delegates to the wrapped SourceStream.
	 *
	 * @return the <code>ContentDescriptor</code> of the stream
	 */
	public ContentDescriptor getContentDescriptor()
	{
		return stream.getContentDescriptor();
	}

	/**
	 * Implements SourceStream#getContentLength(). Delegates to the wrapped SourceStream.
	 *
	 * @return content length
	 */
	public long getContentLength()
	{
		return stream.getContentLength();
	}

	/**
	 * Implements Controls#getControl(String). Delegates to the wrapped SourceStream.
	 *
	 * @param controlType
	 * 		a <code>String</code> value naming the type of the control of this instance to be
	 * 		retrieved
	 * @return an <code>Object</code> which represents the control of this instance with the specified
	 * type
	 */
	public Object getControl(String controlType)
	{
		return stream.getControl(controlType);
	}

	/**
	 * Implements Controls#getControls(). Delegates to the wrapped SourceStream.
	 *
	 * @return array of JMF <code>Control</code> objects
	 */
	public Object[] getControls()
	{
		return stream.getControls();
	}

	/**
	 * Gets the <code>Format</code> of the data this stream provides.
	 *
	 * @return the <code>Format</code> of the data this stream provides
	 */
	public Format getFormat()
	{
		return format;
	}

	/**
	 * Gets the <code>SourceStream</code> wrapped by this instance.
	 *
	 * @return the <code>SourceStream</code> wrapped by this instance
	 */
	public T getStream()
	{
		return stream;
	}

	/**
	 * Reads byte data from this stream into a specific <code>Buffer</code> which is to use a specific
	 * array of bytes for its data.
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> to read byte data into from this instance
	 * @param data
	 * 		the array of <code>byte</code>s to read data into from this instance and to be set as the
	 * 		data of the specified <code>buffer</code>
	 * @throws IOException
	 * 		if I/O related errors occurred during read operation
	 */
	protected void read(Buffer buffer, byte[] data, int offset, int length)
			throws IOException
	{
		int numberOfBytesRead = doRead(buffer, data, offset, length);

		buffer.setData(data);
		if (numberOfBytesRead >= 0) {
			buffer.setLength(numberOfBytesRead);
		}
		else {
			buffer.setLength(0);
			if (numberOfBytesRead == -1)
				buffer.setEOM(true);
		}
		buffer.setOffset(offset);

		Format format = getFormat();

		if (format != null)
			buffer.setFormat(format);
	}

	/**
	 * Reads byte data from this stream into a specific array of <code>byte</code>s starting the
	 * storing
	 * at a specific offset and reading at most a specific number of bytes.
	 *
	 * @param buffer
	 * 		an optional <code>Buffer</code> instance associated with the specified <code>data</code>,
	 * 		<code>offset</code> and <code>length</code> and provided to the method in case the
	 * 		implementation would like to provide additional <code>Buffer</code> properties such as
	 * 		<code>flags</code>
	 * @param data
	 * 		the array of <code>byte</code>s into which the data read from this stream is to be written
	 * @param offset
	 * 		the offset in the specified <code>data</code> at which writing data read from this stream
	 * 		should start
	 * @param length
	 * 		the maximum number of bytes to be written into the specified <code>data</code>
	 * @return the number of bytes read from this stream and written into the specified
	 * <code>data</code>
	 * @throws IOException
	 * 		if I/O related errors occurred during read operation
	 */
	protected abstract int doRead(Buffer buffer, byte[] data, int offset, int length)
			throws IOException;
}
