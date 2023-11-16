/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.control.FormatControl;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;

/**
 * Implements {@link PushBufferDataSource} for the purposes of {@link RTPTranslatorImpl} when it
 * does not have a <code>CaptureDevice</code> yet <code>RTPManager.createSendStream(DataSource, int)</code>
 * has to be called to have <code>RTPTranslatorImpl</code> send packets.
 *
 * @author Lyubomir Marinov
 */
public class FakePushBufferDataSource extends AbstractPushBufferCaptureDevice
{
	/**
	 * The <code>Format</code>s in which this <code>DataSource</code> is capable of providing media.
	 */
	private final Format[] supportedFormats;

	/**
	 * Initializes a new <code>FakePushBufferCaptureDevice</code> instance which is to report a
	 * specific list of <code>Format</code>s as supported.
	 *
	 * @param supportedFormats
	 * 		the list of <code>Format</code>s to be reported as supported by the new instance
	 */
	public FakePushBufferDataSource(Format... supportedFormats)
	{
		this.supportedFormats = (supportedFormats == null) ? null : supportedFormats.clone();
	}

	/**
	 * Opens a connection to the media source specified by the <code>MediaLocator</code> of this
	 * <code>DataSource</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while opening the connection to the media source specified by
	 * 		the <code>MediaLocator</code> of this <code>DataSource</code>
	 */
	@Override
	public void connect()
			throws IOException
	{
		/*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
	}

	/**
	 * Create a new <code>PushBufferStream</code> which is to be at a specific zero-based index in the
	 * list of streams of this <code>PushBufferDataSource</code>. The <code>Format</code>-related
	 * information of the new instance is to be abstracted by a specific <code>FormatControl</code> .
	 *
	 * @param streamIndex
	 * 		the zero-based index of the <code>PushBufferStream</code> in the list of streams of this
	 * 		<code>PushBufferDataSource</code>
	 * @param formatControl
	 * 		the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 * 		information of the new instance
	 * @return a new <code>PushBufferStream</code> which is to be at the specified <code>streamIndex</code>
	 * in the list of streams of this <code>PushBufferDataSource</code> and which has its
	 * <code>Format</code> -related information abstracted by the specified
	 * <code>formatControl</code>
	 */
	@Override
	protected FakePushBufferStream createStream(int streamIndex, FormatControl formatControl)
	{
		return new FakePushBufferStream(this, formatControl);
	}

	/**
	 * Closes the connection to the media source specified of this
	 * <code>AbstractBufferCaptureDevice</code>. If such a connection has not been opened, the call is
	 * ignored.
	 */
	@Override
	public void disconnect()
	{
		/*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
	}

	/**
	 * Gets the <code>Format</code>s which are to be reported by a <code>FormatControl</code> as supported
	 * formats for a <code>PushBufferStream</code> at a specific zero-based index in the list of
	 * streams of this <code>PushBufferDataSource</code>.
	 *
	 * @param streamIndex
	 * 		the zero-based index of the <code>PushBufferStream</code> for which the specified
	 * 		<code>FormatControl</code> is to report the list of supported <code>Format</code>s
	 * @return an array of <code>Format</code>s to be reported by a <code>FormatControl</code> as the
	 * supported formats for the <code>PushBufferStream</code> at the specified
	 * <code>streamIndex</code> in the list of streams of this <code>PushBufferDataSource</code>
	 */
	@Override
	protected Format[] getSupportedFormats(int streamIndex)
	{
		return (supportedFormats == null) ? null : supportedFormats.clone();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Allows setting an arbitrary <code>Format</code> on this <code>DataSource</code> because it does not
	 * really provide any media.
	 */
	@Override
	protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
	{
		return newValue;
	}

	/**
	 * Starts the transfer of media data from this <code>DataSource</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while starting the transfer of media data from this
	 * 		<code>DataSource</code>
	 */
	@Override
	public void start()
			throws IOException
	{
		/*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
	}

	/**
	 * Stops the transfer of media data from this <code>DataSource</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while stopping the transfer of media data from this
	 * 		<code>DataSource</code>
	 */
	@Override
	public void stop()
			throws IOException
	{
		/*
		 * The connect, disconnect, start and stop methods of the super have been overridden in
		 * order to disable consistency checks with respect to the connected and started states.
		 */
	}

	/**
	 * Implements {@link PushBufferStream} for the purposes of <code>FakePushBufferDataSource</code>.
	 */
	private static class FakePushBufferStream
			extends AbstractPushBufferStream<FakePushBufferDataSource>
	{
		/**
		 * Initializes a new <code>FakePushBufferStream</code> instance which is to have its
		 * <code>Format</code>-related information abstracted by a specific <code>FormatControl</code>.
		 *
		 * @param dataSource
		 * 		the <code>FakePushBufferDataSource</code> which is creating the new instance so that
		 * 		it becomes one of its <code>streams</code>
		 * @param formatControl
		 * 		the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
		 * 		information of the new instance
		 */
		FakePushBufferStream(FakePushBufferDataSource dataSource, FormatControl formatControl)
		{
			super(dataSource, formatControl);
		}

		/**
		 * {@inheritDoc}
		 * <p>
		 * Allows setting an arbitrary format on this <code>SourceStream</code> because it does not
		 * really provide any media.
		 */
		@Override
		protected Format doSetFormat(Format format)
		{
			return format;
		}

		/**
		 * Reads media data from this <code>PushBufferStream</code> into a specific <code>Buffer</code>
		 * without blocking.
		 *
		 * @param buffer
		 * 		the <code>Buffer</code> in which media data is to be read from this
		 * 		<code>PushBufferStream</code>
		 * @throws IOException
		 * 		if anything goes wrong while reading media data from this
		 * 		<code>PushBufferStream</code> into the specified <code>buffer</code>
		 */
		@Override
		public void read(Buffer buffer)
				throws IOException
		{
			/*
			 * The whole point of FakePushBufferDataSource and FakePushBufferStream is that this
			 * read method is a no-op (and this FakePushBufferStream will never invoke its
			 * associated transferHandler).
			 */
		}
	}
}
