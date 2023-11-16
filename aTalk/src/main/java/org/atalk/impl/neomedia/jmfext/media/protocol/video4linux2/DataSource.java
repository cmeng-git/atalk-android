/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2;

import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.device.DeviceSystem;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPullBufferCaptureDevice;

import java.io.IOException;

import javax.media.MediaLocator;
import javax.media.control.FormatControl;

/**
 * Implements a <code>PullBufferDataSource</code> and <code>CaptureDevice</code> using the Video for Linux
 * Two API Specification.
 *
 * @author Lyubomir Marinov
 */
public class DataSource extends AbstractVideoPullBufferCaptureDevice
{
	/**
	 * The map of Video for Linux Two API Specification pixel formats to FFmpeg pixel formats which
	 * allows converting between the two.
	 */
	private static final int[] V4L2_TO_FFMPEG_PIX_FMT = new int[] { Video4Linux2.V4L2_PIX_FMT_UYVY,
		FFmpeg.PIX_FMT_UYVY422, Video4Linux2.V4L2_PIX_FMT_YUV420, FFmpeg.PIX_FMT_YUV420P,
		Video4Linux2.V4L2_PIX_FMT_YUYV, FFmpeg.PIX_FMT_YUYV422, Video4Linux2.V4L2_PIX_FMT_MJPEG,
		FFmpeg.PIX_FMT_YUVJ422P, Video4Linux2.V4L2_PIX_FMT_JPEG, FFmpeg.PIX_FMT_YUVJ422P,
		Video4Linux2.V4L2_PIX_FMT_RGB24, FFmpeg.PIX_FMT_RGB24_1, Video4Linux2.V4L2_PIX_FMT_BGR24,
		FFmpeg.PIX_FMT_BGR24_1, };

	/**
	 * The file descriptor of the opened Video for Linux Two API Specification device represented by
	 * this <code>DataSource</code>.
	 */
	private int fd = -1;

	/**
	 * Initializes a new <code>DataSource</code> instance.
	 */
	public DataSource()
	{
	}

	/**
	 * Initializes a new <code>DataSource</code> instance from a specific <code>MediaLocator</code>.
	 *
	 * @param locator
	 *        the <code>MediaLocator</code> to create the new instance from
	 */
	public DataSource(MediaLocator locator)
	{
		super(locator);
	}

	/**
	 * Creates a new <code>PullBufferStream</code> which is to be at a specific zero-based index in the
	 * list of streams of this <code>PullBufferDataSource</code>. The <code>Format</code>-related
	 * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param streamIndex
	 *        the zero-based index of the <code>PullBufferStream</code> in the list of streams of this
	 *        <code>PullBufferDataSource</code>
	 * @param formatControl
	 *        the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 *        information of the new instance
	 * @return a new <code>PullBufferStream</code> which is to be at the specified <code>streamIndex</code>
	 *         in the list of streams of this <code>PullBufferDataSource</code> and which has its
	 *         <code>Format</code>-related information abstracted by the specified
	 *         <code>formatControl</code>
	 */
	@Override
	protected Video4Linux2Stream createStream(int streamIndex, FormatControl formatControl)
	{
		return new Video4Linux2Stream(this, formatControl);
	}

	/**
	 * Opens a connection to the media source specified by the <code>MediaLocator</code> of this
	 * <code>DataSource</code>.
	 *
	 * @throws IOException
	 *         if anything goes wrong while opening the connection to the media source specified by
	 *         the <code>MediaLocator</code> of this <code>DataSource</code>
	 * @see AbstractPullBufferCaptureDevice#doConnect()
	 */
	@Override
	protected void doConnect()
		throws IOException
	{
		super.doConnect();

		String deviceName = getDeviceName();
		int fd = Video4Linux2.open(deviceName, Video4Linux2.O_RDWR);

		if (-1 == fd)
			throw new IOException("Failed to open " + deviceName);
		else {
			boolean close = true;

			try {
				synchronized (getStreamSyncRoot()) {
					for (Object stream : getStreams())
						((Video4Linux2Stream) stream).setFd(fd);
				}
				close = false;
			}
			finally {
				if (close) {
					Video4Linux2.close(fd);
					fd = -1;
				}
			}
			this.fd = fd;
		}
	}

	/**
	 * Closes the connection to the media source specified by the <code>MediaLocator</code> of this
	 * <code>DataSource</code>.
	 */
	@Override
	protected void doDisconnect()
	{
		try {
			/*
			 * Letting the Video4Linux2Stream know that the fd is going to be closed is necessary at
			 * least because AbstractPullBufferStream#close() is not guaranteed.
			 */
			synchronized (getStreamSyncRoot()) {
				Object[] streams = streams();

				if (streams != null) {
					for (Object stream : streams) {
						try {
							((Video4Linux2Stream) stream).setFd(-1);
						}
						catch (IOException ioex) {
						}
					}
				}
			}
		}
		finally {
			try {
				super.doDisconnect();
			}
			finally {
				Video4Linux2.close(fd);
			}
		}
	}

	/**
	 * Gets the name of the Video for Linux Two API Specification device which represents the media
	 * source of this <code>DataSource</code>.
	 *
	 * @return the name of the Video for Linux Two API Specification device which represents the
	 *         media source of this <code>DataSource</code>
	 */
	private String getDeviceName()
	{
		MediaLocator locator = getLocator();

		return ((locator != null) && DeviceSystem.LOCATOR_PROTOCOL_VIDEO4LINUX2
			.equalsIgnoreCase(locator.getProtocol())) ? locator.getRemainder() : null;
	}

	/**
	 * Gets the Video for Linux Two API Specification pixel format matching a specific FFmpeg pixel
	 * format.
	 *
	 * @param v4l2PixFmt
	 *        the FFmpeg pixel format to get the matching Video for Linux Two API Specification
	 *        pixel format of
	 * @return the Video for Linux Two API Specification pixel format matching the specified FFmpeg
	 *         format
	 */
	public static int getFFmpegPixFmt(int v4l2PixFmt)
	{
		for (int i = 0; i < V4L2_TO_FFMPEG_PIX_FMT.length; i += 2)
			if (V4L2_TO_FFMPEG_PIX_FMT[i] == v4l2PixFmt)
				return V4L2_TO_FFMPEG_PIX_FMT[i + 1];
		return FFmpeg.PIX_FMT_NONE;
	}

	/**
	 * Gets the FFmpeg pixel format matching a specific Video for Linux Two API Specification pixel
	 * format.
	 *
	 * @param ffmpegPixFmt
	 *        the Video for Linux Two API Specification pixel format to get the matching FFmpeg
	 *        pixel format of
	 * @return the FFmpeg pixel format matching the specified Video for Linux Two API Specification
	 *         pixel format
	 */
	public static int getV4L2PixFmt(int ffmpegPixFmt)
	{
		for (int i = 0; i < V4L2_TO_FFMPEG_PIX_FMT.length; i += 2)
			if (V4L2_TO_FFMPEG_PIX_FMT[i + 1] == ffmpegPixFmt)
				return V4L2_TO_FFMPEG_PIX_FMT[i];
		return Video4Linux2.V4L2_PIX_FMT_NONE;
	}
}
