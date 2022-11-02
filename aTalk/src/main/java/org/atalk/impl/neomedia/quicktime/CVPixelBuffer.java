/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents a CoreVideo <code>CVPixelBufferRef</code>.
 *
 * @author Lyubomir Marinov
 */
public class CVPixelBuffer extends CVImageBuffer
{
	/**
	 * Initializes a new <code>CVPixelBuffer</code> instance which is to represent a specific CoreVideo
	 * <code>CVPixelBufferRef</code>.
	 *
	 * @param ptr
	 * 		the CoreVideo <code>CVPixelBufferRef</code> to be represented by the new instance
	 */
	public CVPixelBuffer(long ptr)
	{
		super(ptr);
	}

	/**
	 * Gets the number of bytes which represent the pixels of the associated CoreVideo
	 * <code>CVPixelBufferRef</code>.
	 *
	 * @return the number of bytes which represent the pixels of the associated CoreVideo
	 * <code>CVPixelBufferRef</code>
	 */
	public int getByteCount()
	{
		return getByteCount(getPtr());
	}

	/**
	 * Gets the number of bytes which represent the pixels of a specific CoreVideo
	 * <code>CVPixelBufferRef</code>.
	 *
	 * @param ptr
	 * 		the <code>CVPixelBufferRef</code> to get the number of bytes which represent its pixels of
	 * @return the number of bytes which represent the pixels of the specified CoreVideo
	 * <code>CVPixelBufferRef</code>
	 */
	private static native int getByteCount(long ptr);

	/**
	 * Gets a <code>byte</code> array which represents the pixels of the associated CoreVideo
	 * <code>CVPixelBufferRef</code>.
	 *
	 * @return a <code>byte</code> array which represents the pixels of the associated CoreVideo
	 * <code>CVPixelBufferRef</code>
	 */
	public byte[] getBytes()
	{
		return getBytes(getPtr());
	}

	/**
	 * Gets a <code>byte</code> array which represents the pixels of a specific CoreVideo
	 * <code>CVPixelBufferRef</code>.
	 *
	 * @param ptr
	 * 		the <code>CVPixelBufferRef</code> to get the pixel bytes of
	 * @return a <code>byte</code> array which represents the pixels of the specified CoreVideo
	 * <code>CVPixelBufferRef</code>
	 */
	private static native byte[] getBytes(long ptr);

	/**
	 * Gets the bytes which represent the pixels of the associated <code>CVPixelBufferRef</code> into a
	 * specific native byte buffer with a specific capacity.
	 *
	 * @param buf
	 * 		the native byte buffer to return the bytes into
	 * @param bufLength
	 * 		the capacity in bytes of <code>buf</code>
	 * @return the number of bytes written into <code>buf</code>
	 */
	public int getBytes(long buf, int bufLength)
	{
		return getBytes(getPtr(), buf, bufLength);
	}

	/**
	 * Gets the bytes which represent the pixels of a specific <code>CVPixelBufferRef</code> into a
	 * specific native byte buffer with a specific capacity.
	 *
	 * @param ptr
	 * 		the <code>CVPixelBufferRef</code> to get the bytes of
	 * @param buf
	 * 		the native byte buffer to return the bytes into
	 * @param bufLength
	 * 		the capacity in bytes of <code>buf</code>
	 * @return the number of bytes written into <code>buf</code>
	 */
	private static native int getBytes(long ptr, long buf, int bufLength);

	/**
	 * Gets the height in pixels of this <code>CVPixelBuffer</code>.
	 *
	 * @return the height in pixels of this <code>CVPixelBuffer</code>
	 */
	public int getHeight()
	{
		return getHeight(getPtr());
	}

	/**
	 * Gets the height in pixels of a specific CoreVideo <code>CVPixelBufferRef</code>.
	 *
	 * @param ptr
	 * 		the CoreVideo <code>CVPixelBufferRef</code> to get the height in pixels of
	 * @return the height in pixels of the specified CoreVideo <code>CVPixelBufferRef</code>
	 */
	private static native int getHeight(long ptr);

	/**
	 * Gets the width in pixels of this <code>CVPixelBuffer</code>.
	 *
	 * @return the width in pixels of this <code>CVPixelBuffer</code>
	 */
	public int getWidth()
	{
		return getWidth(getPtr());
	}

	/**
	 * Gets the width in pixels of a specific CoreVideo <code>CVPixelBufferRef</code>.
	 *
	 * @param ptr
	 * 		the CoreVideo <code>CVPixelBufferRef</code> to get the width in pixels of
	 * @return the width in pixels of the specified CoreVideo <code>CVPixelBufferRef</code>
	 */
	private static native int getWidth(long ptr);

	/**
	 * Native copy from native pointer <code>src</code> to byte array <code>dst</code>.
	 *
	 * @param dst
	 * 		destination array
	 * @param dstOffset
	 * 		offset of <code>dst</code> to copy data to
	 * @param dstLength
	 * 		length of <code>dst</code>
	 * @param src
	 * 		native pointer source
	 */
	public static native void memcpy(byte[] dst, int dstOffset, int dstLength, long src);
}
