/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import javax.media.Buffer;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.PushBufferStream;

/**
 * Implements a <code>BufferTransferHandler</code> which reads from a specified
 * <code>PushBufferStream</code> as soon as possible and throws the read data away.
 *
 * @author Lyubomir Marinov
 */
public class NullBufferTransferHandler implements BufferTransferHandler
{
	/**
	 * The FMJ <code>Buffer</code> into which this <code>BufferTransferHandler</code> is to read data from
	 * any <code>PushBufferStream</code>.
	 */
	private final Buffer buffer = new Buffer();

	@Override
	public void transferData(PushBufferStream stream)
	{
		try {
			stream.read(buffer);
		}
		catch (Exception ex) {
			// The purpose of NullBufferTransferHandler is to read from the
			// specified PushBufferStream as soon as possible and throw the read
			// data away. Hence, Exceptions are of no concern.
		}
	}
}
