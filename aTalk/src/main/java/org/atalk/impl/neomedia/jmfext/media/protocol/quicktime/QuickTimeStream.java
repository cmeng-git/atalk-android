/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.quicktime;

import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.AVFrame;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.codec.video.ByteBuffer;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.atalk.impl.neomedia.jmfext.media.protocol.ByteBufferPool;
import org.atalk.impl.neomedia.quicktime.CVImageBuffer;
import org.atalk.impl.neomedia.quicktime.CVPixelBuffer;
import org.atalk.impl.neomedia.quicktime.CVPixelBufferAttributeKey;
import org.atalk.impl.neomedia.quicktime.CVPixelFormatType;
import org.atalk.impl.neomedia.quicktime.NSDictionary;
import org.atalk.impl.neomedia.quicktime.NSMutableDictionary;
import org.atalk.impl.neomedia.quicktime.QTCaptureDecompressedVideoOutput;
import org.atalk.impl.neomedia.quicktime.QTCaptureOutput;
import org.atalk.impl.neomedia.quicktime.QTSampleBuffer;

import java.awt.Dimension;
import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.control.FormatControl;
import javax.media.control.FrameRateControl;
import javax.media.format.RGBFormat;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.PushBufferStream;

/**
 * Implements a <code>PushBufferStream</code> using QuickTime/QTKit.
 *
 * @author Lyubomir Marinov
 */
public class QuickTimeStream extends AbstractPushBufferStream<DataSource>
{

	/**
	 * The indicator which determines whether {@link #captureOutput} automatically drops late
	 * frames. If <code>false</code>, we have to drop them ourselves because QuickTime/QTKit will buffer
	 * them all and the video will be late.
	 */
	private final boolean automaticallyDropsLateVideoFrames;

	/**
	 * The pool of <code>ByteBuffer</code>s this instances is using to transfer the media data captured
	 * by {@link #captureOutput} out of this instance through the <code>Buffer</code>s specified in its
	 * {@link #read(Buffer)}.
	 */
	private final ByteBufferPool byteBufferPool = new ByteBufferPool();

	/**
	 * The <code>QTCaptureOutput</code> represented by this <code>SourceStream</code>.
	 */
	final QTCaptureDecompressedVideoOutput captureOutput = new QTCaptureDecompressedVideoOutput();

	/**
	 * The <code>VideoFormat</code> which has been successfully set on {@link #captureOutput}.
	 */
	private VideoFormat captureOutputFormat;

	/**
	 * The captured media data to be returned in {@link #read(Buffer)}.
	 */
	private ByteBuffer data;

	/**
	 * The <code>Format</code> of {@link #data} if known. If possible, determined by the
	 * <code>CVPixelBuffer</code> video frame from which <code>data</code> is acquired.
	 */
	private Format dataFormat;

	/**
	 * The <code>Object</code> which synchronizes the access to the {@link #data}-related fields of this
	 * instance.
	 */
	private final Object dataSyncRoot = new Object();

	/**
	 * The time stamp in nanoseconds of {@link #data}.
	 */
	private long dataTimeStamp;

	/**
	 * The last-known <code>Format</code> of the media data made available by this
	 * <code>PushBufferStream</code>.
	 */
	private Format format;

	/**
	 * The captured media data to become the value of {@link #data} as soon as the latter becomes is
	 * consumed. Thus prepares this <code>QuickTimeStream</code> to provide the latest available frame
	 * and not wait for QuickTime/QTKit to capture a new one.
	 */
	private ByteBuffer nextData;

	/**
	 * The <code>Format</code> of {@link #nextData} if known.
	 */
	private Format nextDataFormat;

	/**
	 * The time stamp in nanoseconds of {@link #nextData}.
	 */
	private long nextDataTimeStamp;

	/**
	 * The <code>Thread</code> which is to call
	 * {@link BufferTransferHandler#transferData(PushBufferStream)} for this
	 * <code>QuickTimeStream</code> so that the call is not made in QuickTime/QTKit and we can drop late
	 * frames when {@link #automaticallyDropsLateVideoFrames} is <code>false</code>.
	 */
	private Thread transferDataThread;

	/**
	 * Initializes a new <code>QuickTimeStream</code> instance which is to have its <code>Format</code>
	 * -related information abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param dataSource
	 *        the <code>DataSource</code> which is creating the new instance so that it becomes one of
	 *        its <code>streams</code>
	 * @param formatControl
	 *        the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 *        information of the new instance
	 */
	QuickTimeStream(DataSource dataSource, FormatControl formatControl)
	{
		super(dataSource, formatControl);

		if (formatControl != null) {
			Format format = formatControl.getFormat();

			if (format != null)
				setCaptureOutputFormat(format);
		}

		automaticallyDropsLateVideoFrames = captureOutput
			.setAutomaticallyDropsLateVideoFrames(true);
		captureOutput.setDelegate(new QTCaptureDecompressedVideoOutput.Delegate()
		{

			/**
			 * Notifies this <code>Delegate</code> that the <code>QTCaptureOutput</code> to which it is set
			 * has output a specific <code>CVImageBuffer</code> representing a video frame with a
			 * specific <code>QTSampleBuffer</code>.
			 *
			 * @param videoFrame
			 *        the <code>CVImageBuffer</code> which represents the output video frame
			 * @param sampleBuffer
			 *        the <code>QTSampleBuffer</code> which represents additional details about the
			 *        output video samples
			 */
			@Override
			public void outputVideoFrameWithSampleBuffer(CVImageBuffer videoFrame,
				QTSampleBuffer sampleBuffer)
			{
				captureOutputDidOutputVideoFrameWithSampleBuffer(captureOutput, videoFrame,
					sampleBuffer);
			}
		});

		FrameRateControl frameRateControl = (FrameRateControl) dataSource
			.getControl(FrameRateControl.class.getName());

		if (frameRateControl != null) {
			float frameRate = frameRateControl.getFrameRate();

			if (frameRate > 0)
				setFrameRate(frameRate);
		}
	}

	/**
	 * Notifies this instance that its <code>QTCaptureOutput</code> has output a specific
	 * <code>CVImageBuffer</code> representing a video frame with a specific <code>QTSampleBuffer</code>.
	 *
	 * @param captureOutput
	 *        the <code>QTCaptureOutput</code> which has output a video frame
	 * @param videoFrame
	 *        the <code>CVImageBuffer</code> which represents the output video frame
	 * @param sampleBuffer
	 *        the <code>QTSampleBuffer</code> which represents additional details about the output video
	 *        samples
	 */
	private void captureOutputDidOutputVideoFrameWithSampleBuffer(QTCaptureOutput captureOutput,
		CVImageBuffer videoFrame, QTSampleBuffer sampleBuffer)
	{
		CVPixelBuffer pixelBuffer = (CVPixelBuffer) videoFrame;
		boolean transferData;
		Format videoFrameFormat = getVideoFrameFormat(pixelBuffer);

		synchronized (dataSyncRoot) {
			if (!automaticallyDropsLateVideoFrames && (data != null)) {
				if (nextData != null) {
					nextData.free();
					nextData = null;
				}
				nextData = byteBufferPool.getBuffer(pixelBuffer.getByteCount());
				if (nextData != null) {
					nextData.setLength(pixelBuffer.getBytes(nextData.getPtr(),
						nextData.getCapacity()));
					nextDataTimeStamp = System.nanoTime();
					if (nextDataFormat == null)
						nextDataFormat = videoFrameFormat;
				}
				return;
			}

			if (data != null) {
				data.free();
				data = null;
			}
			data = byteBufferPool.getBuffer(pixelBuffer.getByteCount());
			if (data != null) {
				data.setLength(pixelBuffer.getBytes(data.getPtr(), data.getCapacity()));
				dataTimeStamp = System.nanoTime();
				if (dataFormat == null)
					dataFormat = videoFrameFormat;
			}

			if (nextData != null) {
				nextData.free();
				nextData = null;
			}

			if (automaticallyDropsLateVideoFrames)
				transferData = (data != null);
			else {
				transferData = false;
				dataSyncRoot.notifyAll();
			}
		}

		if (transferData) {
			BufferTransferHandler transferHandler = this.transferHandler;

			if (transferHandler != null)
				transferHandler.transferData(this);
		}
	}

	/**
	 * Releases the resources used by this instance throughout its existence and makes it available
	 * for garbage collection. This instance is considered unusable after closing.
	 *
	 * @see AbstractPushBufferStream#close()
	 */
	@Override
	public void close()
	{
		super.close();

		captureOutput.setDelegate(null);
		byteBufferPool.drain();
	}

	/**
	 * Gets the <code>Format</code> of this <code>PushBufferStream</code> as directly known by it.
	 *
	 * @return the <code>Format</code> of this <code>PushBufferStream</code> as directly known by it or
	 *         <code>null</code> if this <code>PushBufferStream</code> does not directly know its
	 *         <code>Format</code> and it relies on the <code>PushBufferDataSource</code> which created it
	 *         to report its <code>Format</code>
	 */
	@Override
	protected Format doGetFormat()
	{
		Format format;

		if (this.format == null) {
			format = getCaptureOutputFormat();
			if (format == null)
				format = super.doGetFormat();
			else {
				VideoFormat videoFormat = (VideoFormat) format;

				if (videoFormat.getSize() != null)
					this.format = format;
				else {
					Dimension defaultSize = NeomediaServiceUtils.getMediaServiceImpl()
						.getDeviceConfiguration().getVideoSize();

					format = videoFormat.intersects(new VideoFormat(
					/* encoding */null, new Dimension(defaultSize.width, defaultSize.height),
					/* maxDataLength */Format.NOT_SPECIFIED,
					/* dataType */null,
					/* frameRate */Format.NOT_SPECIFIED));
				}
			}
		}
		else
			format = this.format;
		return format;
	}

	/**
	 * Gets the <code>Format</code> of the media data made available by this <code>PushBufferStream</code>
	 * as indicated by {@link #captureOutput}.
	 *
	 * @return the <code>Format</code> of the media data made available by this
	 *         <code>PushBufferStream</code> as indicated by {@link #captureOutput}
	 */
	private Format getCaptureOutputFormat()
	{
		NSDictionary pixelBufferAttributes = captureOutput.pixelBufferAttributes();

		if (pixelBufferAttributes != null) {
			int pixelFormatType = pixelBufferAttributes
				.intForKey(CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey);
			int width = pixelBufferAttributes
				.intForKey(CVPixelBufferAttributeKey.kCVPixelBufferWidthKey);
			int height = pixelBufferAttributes
				.intForKey(CVPixelBufferAttributeKey.kCVPixelBufferHeightKey);

			switch (pixelFormatType) {
				case CVPixelFormatType.kCVPixelFormatType_32ARGB:
					if (captureOutputFormat instanceof AVFrameFormat) {
						return new AVFrameFormat(((width == 0) && (height == 0) ? null
							: new Dimension(width, height)),
						/* frameRate */Format.NOT_SPECIFIED, FFmpeg.PIX_FMT_ARGB,
							CVPixelFormatType.kCVPixelFormatType_32ARGB);
					}
					else {
						return new RGBFormat(((width == 0) && (height == 0) ? null : new Dimension(
							width, height)),
						/* maxDataLength */Format.NOT_SPECIFIED, Format.byteArray,
						/* frameRate */Format.NOT_SPECIFIED, 32, 2, 3, 4);
					}
				case CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar:
					if ((width == 0) && (height == 0)) {
						if (captureOutputFormat instanceof AVFrameFormat) {
							return new AVFrameFormat(FFmpeg.PIX_FMT_YUV420P,
								CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar);
						}
						else
							return new YUVFormat(YUVFormat.YUV_420);
					}
					else if (captureOutputFormat instanceof AVFrameFormat) {
						return new AVFrameFormat(new Dimension(width, height),
						/* frameRate */Format.NOT_SPECIFIED, FFmpeg.PIX_FMT_YUV420P,
							CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar);
					}
					else {
						int strideY = width;
						int strideUV = strideY / 2;
						int offsetY = 0;
						int offsetU = strideY * height;
						int offsetV = offsetU + strideUV * height / 2;

						return new YUVFormat(new Dimension(width, height),
						/* maxDataLength */Format.NOT_SPECIFIED, Format.byteArray,
						/* frameRate */Format.NOT_SPECIFIED, YUVFormat.YUV_420, strideY, strideUV,
							offsetY, offsetU, offsetV);
					}
			}
		}
		return null;
	}

	/**
	 * Gets the output frame rate of the <code>QTCaptureDecompressedVideoOutput</code> represented by
	 * this <code>QuickTimeStream</code>.
	 *
	 * @return the output frame rate of the <code>QTCaptureDecompressedVideoOutput</code> represented by
	 *         this <code>QuickTimeStream</code>
	 */
	public float getFrameRate()
	{
		return (float) (1.0d / captureOutput.minimumVideoFrameInterval());
	}

	/**
	 * Gets the <code>Format</code> of the media data made available by this <code>PushBufferStream</code>
	 * as indicated by a specific <code>CVPixelBuffer</code>.
	 *
	 * @param videoFrame
	 *        the <code>CVPixelBuffer</code> which provides details about the <code>Format</code> of the
	 *        media data made available by this <code>PushBufferStream</code>
	 * @return the <code>Format</code> of the media data made available by this
	 *         <code>PushBufferStream</code> as indicated by the specified <code>CVPixelBuffer</code>
	 */
	private Format getVideoFrameFormat(CVPixelBuffer videoFrame)
	{
		Format format = getFormat();
		Dimension size = ((VideoFormat) format).getSize();

		if ((size == null) || ((size.width == 0) && (size.height == 0))) {
			format = format.intersects(new VideoFormat(
			/* encoding */null, new Dimension(videoFrame.getWidth(), videoFrame.getHeight()),
			/* maxDataLength */Format.NOT_SPECIFIED,
			/* dataType */null,
			/* frameRate */Format.NOT_SPECIFIED));
		}
		return format;
	}

	/**
	 * Reads media data from this <code>PushBufferStream</code> into a specific <code>Buffer</code> without
	 * blocking.
	 *
	 * @param buffer
	 *        the <code>Buffer</code> in which media data is to be read from this
	 *        <code>PushBufferStream</code>
	 * @throws IOException
	 *         if anything goes wrong while reading media data from this <code>PushBufferStream</code>
	 *         into the specified <code>buffer</code>
	 */
	public void read(Buffer buffer)
		throws IOException
	{
		synchronized (dataSyncRoot) {
			if (data == null) {
				buffer.setLength(0);
				return;
			}

			if (dataFormat != null)
				buffer.setFormat(dataFormat);

			Format format = buffer.getFormat();

			if (format == null) {
				format = getFormat();
				if (format != null)
					buffer.setFormat(format);
			}
			if (format instanceof AVFrameFormat) {
				if (AVFrame.read(buffer, format, data) < 0)
					data.free();
				/*
				 * XXX For the sake of safety, make sure that this instance does not reference the
				 * data instance as soon as it is set on the AVFrame.
				 */
				data = null;
			}
			else {
				Object o = buffer.getData();
				byte[] bytes;
				int length = data.getLength();

				if (o instanceof byte[]) {
					bytes = (byte[]) o;
					if (bytes.length < length)
						bytes = null;
				}
				else
					bytes = null;
				if (bytes == null) {
					bytes = new byte[length];
					buffer.setData(bytes);
				}

				CVPixelBuffer.memcpy(bytes, 0, length, data.getPtr());
				data.free();
				data = null;

				buffer.setLength(length);
				buffer.setOffset(0);
			}

			buffer.setFlags(Buffer.FLAG_LIVE_DATA | Buffer.FLAG_SYSTEM_TIME);
			buffer.setTimeStamp(dataTimeStamp);

			if (!automaticallyDropsLateVideoFrames)
				dataSyncRoot.notifyAll();
		}
	}

	/**
	 * Calls {@link BufferTransferHandler#transferData(PushBufferStream)} from inside
	 * {@link #transferDataThread} so that the call is not made in QuickTime/QTKit and we can drop
	 * late frames in the meantime.
	 */
	private void runInTransferDataThread()
	{
		boolean transferData = false;

		while (Thread.currentThread().equals(transferDataThread)) {
			if (transferData) {
				BufferTransferHandler transferHandler = this.transferHandler;

				if (transferHandler != null)
					transferHandler.transferData(this);

				synchronized (dataSyncRoot) {
					if (data != null)
						data.free();
					data = nextData;
					dataTimeStamp = nextDataTimeStamp;
					if (dataFormat == null)
						dataFormat = nextDataFormat;
					nextData = null;
				}
			}

			synchronized (dataSyncRoot) {
				if (data == null) {
					data = nextData;
					dataTimeStamp = nextDataTimeStamp;
					if (dataFormat == null)
						dataFormat = nextDataFormat;
					nextData = null;
				}
				if (data == null) {
					boolean interrupted = false;

					try {
						dataSyncRoot.wait();
					}
					catch (InterruptedException iex) {
						interrupted = true;
					}
					if (interrupted)
						Thread.currentThread().interrupt();

					transferData = (data != null);
				}
				else
					transferData = true;
			}
		}
	}

	/**
	 * Sets the <code>Format</code> of the media data made available by this <code>PushBufferStream</code>
	 * to {@link #captureOutput}.
	 *
	 * @param format
	 *        the <code>Format</code> of the media data made available by this <code>PushBufferStream</code>
	 *        to be set to {@link #captureOutput}
	 */
	private void setCaptureOutputFormat(Format format)
	{
		VideoFormat videoFormat = (VideoFormat) format;
		Dimension size = videoFormat.getSize();
		int width;
		int height;

		/*
		 * FIXME Mac OS X Leopard does not seem to report the size of the QTCaptureDevice in its
		 * formatDescriptions early in its creation. The workaround presented here is to just force
		 * a specific size.
		 */
		if (size == null) {
			Dimension defaultSize = NeomediaServiceUtils.getMediaServiceImpl()
				.getDeviceConfiguration().getVideoSize();

			width = defaultSize.width;
			height = defaultSize.height;
		}
		else {
			width = size.width;
			height = size.height;
		}

		NSMutableDictionary pixelBufferAttributes = null;

		if ((width > 0) && (height > 0)) {
			if (pixelBufferAttributes == null)
				pixelBufferAttributes = new NSMutableDictionary();
			pixelBufferAttributes.setIntForKey(width,
				CVPixelBufferAttributeKey.kCVPixelBufferWidthKey);
			pixelBufferAttributes.setIntForKey(height,
				CVPixelBufferAttributeKey.kCVPixelBufferHeightKey);
		}

		String encoding;

		if (format instanceof AVFrameFormat) {
			switch (((AVFrameFormat) format).getPixFmt()) {
				case FFmpeg.PIX_FMT_ARGB:
					encoding = VideoFormat.RGB;
					break;
				case FFmpeg.PIX_FMT_YUV420P:
					encoding = VideoFormat.YUV;
					break;
				default:
					encoding = null;
					break;
			}
		}
		else if (format.isSameEncoding(VideoFormat.RGB))
			encoding = VideoFormat.RGB;
		else if (format.isSameEncoding(VideoFormat.YUV))
			encoding = VideoFormat.YUV;
		else
			encoding = null;

		if (VideoFormat.RGB.equalsIgnoreCase(encoding)) {
			if (pixelBufferAttributes == null)
				pixelBufferAttributes = new NSMutableDictionary();
			pixelBufferAttributes.setIntForKey(CVPixelFormatType.kCVPixelFormatType_32ARGB,
				CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey);
		}
		else if (VideoFormat.YUV.equalsIgnoreCase(encoding)) {
			if (pixelBufferAttributes == null)
				pixelBufferAttributes = new NSMutableDictionary();
			pixelBufferAttributes.setIntForKey(
				CVPixelFormatType.kCVPixelFormatType_420YpCbCr8Planar,
				CVPixelBufferAttributeKey.kCVPixelBufferPixelFormatTypeKey);
		}
		else
			throw new IllegalArgumentException("format");

		if (pixelBufferAttributes != null) {
			captureOutput.setPixelBufferAttributes(pixelBufferAttributes);
			captureOutputFormat = videoFormat;
		}
	}

	/**
	 * Sets the output frame rate of the <code>QTCaptureDecompressedVideoOutput</code> represented by
	 * this <code>QuickTimeStream</code>.
	 *
	 * @param frameRate
	 *        the output frame rate to be set on the <code>QTCaptureDecompressedVideoOutput</code>
	 *        represented by this <code>QuickTimeStream</code>
	 * @return the output frame rate of the <code>QTCaptureDecompressedVideoOutput</code> represented by
	 *         this <code>QuickTimeStream</code>
	 */
	public float setFrameRate(float frameRate)
	{
		captureOutput.setMinimumVideoFrameInterval(1.0d / frameRate);
		return getFrameRate();
	}

	/**
	 * Starts the transfer of media data from this <code>PushBufferStream</code>.
	 *
	 * @throws IOException
	 *         if anything goes wrong while starting the transfer of media data from this
	 *         <code>PushBufferStream</code>
	 */
	@Override
	public void start()
		throws IOException
	{
		super.start();

		if (!automaticallyDropsLateVideoFrames) {
			transferDataThread = new Thread(getClass().getSimpleName())
			{
				@Override
				public void run()
				{
					runInTransferDataThread();
				}
			};
			transferDataThread.start();
		}
	}

	/**
	 * Stops the transfer of media data from this <code>PushBufferStream</code>.
	 *
	 * @throws IOException
	 *         if anything goes wrong while stopping the transfer of media data from this
	 *         <code>PushBufferStream</code>
	 */
	@Override
	public void stop()
		throws IOException
	{
		try {
			transferDataThread = null;

			synchronized (dataSyncRoot) {
				if (data != null) {
					data.free();
					data = null;
				}
				dataFormat = null;
				if (nextData != null) {
					nextData.free();
					nextData = null;
				}
				nextDataFormat = null;

				if (!automaticallyDropsLateVideoFrames)
					dataSyncRoot.notifyAll();
			}
		}
		finally {
			super.stop();

			byteBufferPool.drain();
		}
	}
}
