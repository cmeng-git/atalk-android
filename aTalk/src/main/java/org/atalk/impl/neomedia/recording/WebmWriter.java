package org.atalk.impl.neomedia.recording;

import java.io.IOException;

public class WebmWriter
{
	static {
		System.loadLibrary("jnvpx");
        System.loadLibrary(WebmWriter.class.getName());
	}

	/**
	 * Constant corresponding to <code>VPX_FRAME_IS_KEY</code> from libvpx's <code>vpx/vpx_encoder.h</code>
	 */
	public static int FLAG_FRAME_IS_KEY = 0x01;

	/**
	 * Constant corresponding to <code>VPX_FRAME_IS_INVISIBLE</code> from libvpx's
	 * <code>vpx/vpx_encoder.h</code>
	 */
	public static int FLAG_FRAME_IS_INVISIBLE = 0x04;

	private long glob;

	private native long allocCfg();

	/**
	 * Free-s <code>glob</code> and closes the file opened for writing.
	 * 
	 * @param glob
	 */
	private native void freeCfg(long glob);

	private native boolean openFile(long glob, String fileName);

	private native void writeWebmFileHeader(long glob, int width, int height);

	public void writeWebmFileHeader(int width, int height)
	{
		writeWebmFileHeader(glob, width, height);
	}

	private native void writeWebmBlock(long glob, FrameDescriptor fd);

	private native void writeWebmFileFooter(long glob, long hash);

	public WebmWriter(String filename)
		throws IOException
	{
		glob = allocCfg();

		if (glob == 0) {
			throw new IOException("allocCfg() failed");
		}

		if (openFile(glob, filename)) {
			throw new IOException("Can not open " + filename + " for writing");
		}
	}

	public void close()
	{
		writeWebmFileFooter(glob, 0);
		freeCfg(glob); // also closes the file
	}

	public void writeFrame(FrameDescriptor fd)
	{
		writeWebmBlock(glob, fd);
	}

	public static class FrameDescriptor
	{
		public byte[] buffer;
		public int offset;
		public long length;
		public long pts;
		public int flags;
	}
}
