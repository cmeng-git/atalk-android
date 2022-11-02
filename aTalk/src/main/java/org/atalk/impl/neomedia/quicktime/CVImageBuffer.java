/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents a CoreVideo <code>CVImageBufferRef</code>.
 *
 * @author Lyubomir Marinov
 */
public class CVImageBuffer
{
	static {
		System.loadLibrary("jnquicktime");
	}

	/**
	 * The CoreVideo <code>CVImageBufferRef</code> represented by this instance.
	 */
	private long ptr;

	/**
	 * Initializes a new <code>CVImageBuffer</code> instance which is to represent a specific CoreVideo
	 * <code>CVImageBufferRef</code>.
	 *
	 * @param ptr
	 *        the CoreVideo <code>CVImageBufferRef</code> to be represented by the new instance
	 */
	public CVImageBuffer(long ptr)
	{
		setPtr(ptr);
	}

	/**
	 * Gets the CoreVideo <code>CVImageBufferRef</code> represented by this instance.
	 *
	 * @return the CoreVideo <code>CVImageBufferRef</code> represented by this instance
	 */
	protected long getPtr()
	{
		return ptr;
	}

	/**
	 * Sets the CoreVideo <code>CVImageBufferRef</code> represented by this instance.
	 *
	 * @param ptr
	 *        the CoreVideo <code>CVImageBufferRef</code> to be represented by this instance
	 */
	protected void setPtr(long ptr)
	{
		if (ptr == 0)
			throw new IllegalArgumentException("ptr");

		this.ptr = ptr;
	}
}
