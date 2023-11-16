/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.ByteBuffer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a pool of <code>ByteBuffer</code>s which reduces the allocations and deallocations of
 * <code>ByteBuffer</code>s in the Java heap and of native memory in the native heap.
 *
 * @author Lyubomir Marinov
 */
public class ByteBufferPool
{
	/**
	 * The <code>ByteBuffer</code>s which are managed by this <code>ByteBufferPool</code>.
	 */
	private final List<PooledByteBuffer> buffers = new ArrayList<>();

	/**
	 * Drains this <code>ByteBufferPool</code> i.e. frees the <code>ByteBuffer</code>s that it contains.
	 */
	public synchronized void drain()
	{
		for (Iterator<PooledByteBuffer> i = buffers.iterator(); i.hasNext(); ) {
			PooledByteBuffer buffer = i.next();

			i.remove();
			buffer.doFree();
		}
	}

	/**
	 * Gets a <code>ByteBuffer</code> out of this pool of <code>ByteBuffer</code>s which is capable to
	 * receiving at least <code>capacity</code> number of bytes.
	 *
	 * @param capacity
	 * 		the minimal number of bytes that the returned <code>ByteBuffer</code> is to be capable of
	 * 		receiving
	 * @return a <code>ByteBuffer</code> which is ready for writing captured media data into and which
	 * is capable of receiving at least <code>capacity</code> number of bytes
	 */
	public synchronized ByteBuffer getBuffer(int capacity)
	{
		// XXX Pad with FF_INPUT_BUFFER_PADDING_SIZE or hell will break loose.
		capacity += FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE;

		ByteBuffer buffer = null;

		for (Iterator<PooledByteBuffer> i = buffers.iterator(); i.hasNext(); ) {
			ByteBuffer aBuffer = i.next();

			if (aBuffer.getCapacity() >= capacity) {
				i.remove();
				buffer = aBuffer;
				break;
			}
		}
		if (buffer == null)
			buffer = new PooledByteBuffer(capacity, this);
		return buffer;
	}

	/**
	 * Returns a specific <code>ByteBuffer</code> into this pool of <code>ByteBuffer</code>s.
	 *
	 * @param buffer
	 * 		the <code>ByteBuffer</code> to be returned into this pool of <code>ByteBuffer</code>s
	 */
	private synchronized void returnBuffer(PooledByteBuffer buffer)
	{
		if (!buffers.contains(buffer))
			buffers.add(buffer);
	}

	/**
	 * Implements a <code>ByteBuffer</code> which is pooled in a <code>ByteBufferPool</code> in order to
	 * reduce the numbers of allocations and deallocations of <code>ByteBuffer</code>s and their
	 * respective native memory.
	 */
	private static class PooledByteBuffer extends ByteBuffer
	{
		/**
		 * The <code>ByteBufferPool</code> in which this instance is pooled and in which it should
		 * returns upon {@link #free()}.
		 */
		private final WeakReference<ByteBufferPool> pool;

		public PooledByteBuffer(int capacity, ByteBufferPool pool)
		{
			super(capacity);

			this.pool = new WeakReference<ByteBufferPool>(pool);
		}

		/**
		 * Invokes {@link ByteBuffer#free()} i.e. does not make any attempt to return this instance
		 * to the associated <code>ByteBufferPool</code> and frees the native memory represented by
		 * this instance.
		 */
		void doFree()
		{
			super.free();
		}

		/**
		 * {@inheritDoc}
		 * <p>
		 * Returns this <code>ByteBuffer</code> and, respectively, the native memory that it represents
		 * to the associated <code>ByteBufferPool</code>. If the <code>ByteBufferPool</code> has already
		 * been finalized by the garbage collector, frees the native memory represented by this
		 * instance.
		 */
		@Override
		public void free()
		{
			ByteBufferPool pool = this.pool.get();

			if (pool == null)
				doFree();
			else
				pool.returnBuffer(this);
		}
	}
}
