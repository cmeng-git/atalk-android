/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import org.atalk.impl.neomedia.codec.FFmpeg;

import java.awt.Dimension;

import javax.media.Buffer;
import javax.media.Format;

/**
 * Represents a pointer to a native FFmpeg <code>AVFrame</code> object.
 *
 * @author Lyubomir Marinov
 */
public class AVFrame
{
	public static int read(Buffer buffer, Format format, ByteBuffer data)
	{
		AVFrameFormat frameFormat = (AVFrameFormat) format;

		Object o = buffer.getData();
		AVFrame frame;

		if (o instanceof AVFrame)
			frame = (AVFrame) o;
		else {
			frame = new AVFrame();
			buffer.setData(frame);
		}

		return frame.avpicture_fill(data, frameFormat);
	}

	/**
	 * The <code>ByteBuffer</code> whose native memory is set on the native counterpart of this instance/<code>AVFrame</code>.
	 */
	private ByteBuffer data;

	/**
	 * The indicator which determines whether the native memory represented by this instance is to be freed upon
	 * finalization.
	 */
	private boolean free;

	/**
	 * The pointer to the native FFmpeg <code>AVFrame</code> object represented by this instance.
	 */
	private long ptr;

	/**
	 * Initializes a new <code>FinalizableAVFrame</code> instance which is to allocate a new native FFmpeg <code>AVFrame</code>
	 * and represent it.
	 */
	public AVFrame() {
		this.ptr = FFmpeg.avcodec_alloc_frame();
		if (this.ptr == 0)
			throw new OutOfMemoryError("avcodec_alloc_frame()");

		this.free = true;
	}

	/**
	 * Initializes a new <code>AVFrame</code> instance which is to represent a specific pointer to a native FFmpeg
	 * <code>AVFrame</code> object. Because the native memory/<code>AVFrame</code> has been allocated outside the new instance,
	 * the new instance does not automatically free it upon finalization.
	 *
	 * @param ptr
	 *        the pointer to the native FFmpeg <code>AVFrame</code> object to be represented by the new instance
	 */
	public AVFrame(long ptr) {
		if (ptr == 0)
			throw new IllegalArgumentException("ptr");

		this.ptr = ptr;
		this.free = false;
	}

	public synchronized int avpicture_fill(ByteBuffer data, AVFrameFormat format)
	{
		Dimension size = format.getSize();
		int ret = FFmpeg.avpicture_fill(ptr, data.getPtr(), format.getPixFmt(), size.width, size.height);

		if (ret >= 0) {
			if (this.data != null)
				this.data.free();

			this.data = data;
		}
		return ret;
	}

	/**
	 * Deallocates the native memory/FFmpeg <code>AVFrame</code> object represented by this instance if this instance has
	 * allocated it upon initialization and it has not been deallocated yet i.e. ensures that {@link #free()} is invoked
	 * on this instance.
	 *
	 * @see Object#finalize()
	 */
	@Override
	protected void finalize()
		throws Throwable
	{
		try {
			free();
		}
		finally {
			super.finalize();
		}
	}

	/**
	 * Deallocates the native memory/FFmpeg <code>AVFrame</code> object represented by this instance if this instance has
	 * allocated it upon initialization and it has not been deallocated yet.
	 */
	public synchronized void free()
	{
		if (free && (ptr != 0)) {
			FFmpeg.avcodec_free_frame(ptr);
			free = false;
			ptr = 0;
		}

		if (data != null) {
			data.free();
			data = null;
		}
	}

	/**
	 * Gets the <code>ByteBuffer</code> whose native memory is set on the native counterpart of this instance/
	 * <code>AVFrame</code>.
	 *
	 * @return the <code>ByteBuffer</code> whose native memory is set on the native counterpart of this instance/
	 *         <code>AVFrame</code>.
	 */
	public synchronized ByteBuffer getData()
	{
		return data;
	}

	/**
	 * Gets the pointer to the native FFmpeg <code>AVFrame</code> object represented by this instance.
	 *
	 * @return the pointer to the native FFmpeg <code>AVFrame</code> object represented by this instance
	 */
	public synchronized long getPtr()
	{
		return ptr;
	}
}
