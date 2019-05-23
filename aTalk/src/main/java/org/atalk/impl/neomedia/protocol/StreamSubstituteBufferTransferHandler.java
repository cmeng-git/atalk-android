/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import javax.media.protocol.*;

/**
 * Implements a <tt>BufferTransferHandler</tt> wrapper which doesn't expose a
 * <tt>PushBufferStream</tt> but rather a specific substitute in order to give full control to the
 * {@link PushBufferStream#read(javax.media.Buffer)} method of the substitute.
 * <p>
 * The purpose is achieved in {@code #transferData(PushBufferStream)} where the method argument
 * {@code stream} is ignored and the substitute is used instead.
 * </p>
 *
 * @author Lubomir Marinov
 */
public class StreamSubstituteBufferTransferHandler implements BufferTransferHandler
{

	/**
	 * The {@code PushBufferStream} to be overridden for {@code transferHandler} with the
	 * {@code substitute} of this instance.
	 */
	private final PushBufferStream stream;

	/**
	 * The {@code PushBufferStream} to override the {@code stream} of this instance for
	 * {@code transferHandler}.
	 */
	private final PushBufferStream substitute;

	/**
	 * The wrapped <tt>BufferTransferHandler</tt> which receives the actual events from the wrapped
	 * <tt>PushBufferStream</tt>.
	 */
	private final BufferTransferHandler transferHandler;

	/**
	 * Initializes a new <tt>StreamSubstituteBufferTransferHandler</tt> instance which is to
	 * overwrite the source <tt>PushBufferStream</tt> of a specific <tt>BufferTransferHandler</tt>.
	 *
	 * @param transferHandler
	 * 		the <tt>BufferTransferHandler</tt> the new instance is to overwrite the source
	 * 		<tt>PushBufferStream</tt> of
	 * @param stream
	 * 		the {@code PushBufferStream} to be overridden for the specified
	 * 		{@code transferHandler} with the specified (@code substitute}
	 * @param substitute
	 * 		the {@code PushBufferStream} to override the specified {@code stream} for
	 * 		the specified {@code transferHandler}
	 */
	public StreamSubstituteBufferTransferHandler(BufferTransferHandler transferHandler,
			PushBufferStream stream, PushBufferStream substitute)
	{
		this.transferHandler = transferHandler;
		this.stream = stream;
		this.substitute = substitute;
	}

	/**
	 * Implements BufferTransferHandler#transferData(PushBufferStream). Puts in place the
	 * essence of the StreamSubstituteBufferTransferHandler class which is to report to the
	 * transferHandler from the same PushBufferStream to which it was set so that the substitute
	 * can gain full control.
	 *
	 * @param stream
	 * 		the <tt>PushBufferStream</tt> to transfer
	 */
	public void transferData(PushBufferStream stream)
	{
		transferHandler.transferData((stream == this.stream) ? substitute : stream);
	}
}
