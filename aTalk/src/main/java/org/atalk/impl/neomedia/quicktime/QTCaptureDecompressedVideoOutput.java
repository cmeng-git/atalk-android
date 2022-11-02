/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents a QTKit <code>QTCaptureDecompressedVideoOutput</code> object.
 *
 * @author Lyubomir Marinov
 */
public class QTCaptureDecompressedVideoOutput extends QTCaptureOutput
{

	/**
	 * Initializes a new <code>QTCaptureDecompressedVideoOutput</code> which represents a new QTKit
	 * <code>QTCaptureDecompressedVideoOutput</code> object.
	 */
	public QTCaptureDecompressedVideoOutput()
	{
		this(allocAndInit());
	}

	/**
	 * Initializes a new <code>QTCaptureDecompressedVideoOutput</code> which is to represent a new
	 * QTKit <code>QTCaptureDecompressedVideoOutput</code> object.
	 *
	 * @param ptr
	 * 		the pointer to the QTKit <code>QTCaptureDecompressedVideoOutput</code> object to be
	 * 		represented by the new instance
	 */
	public QTCaptureDecompressedVideoOutput(long ptr)
	{
		super(ptr);
	}

	private static native long allocAndInit();

	/**
	 * Called by the garbage collector to release system resources and perform other cleanup.
	 *
	 * @see Object#finalize()
	 */
	@Override
	protected void finalize()
	{
		release();
	}

	/**
	 * Gets the minimum time interval between which this <code>QTCaptureDecompressedVideoOutput</code>
	 * will output consecutive video frames.
	 *
	 * @return the minimum time interval between which this
	 * <code>QTCaptureDecompressedVideoOutput</code> will output consecutive video frames. It is
	 * equivalent to the inverse of the maximum frame rate. The value of <code>0</code>
	 * indicates an unlimited maximum frame rate.
	 */
	public double minimumVideoFrameInterval()
	{
		return minimumVideoFrameInterval(getPtr());
	}

	/**
	 * Gets the minimum time interval between which a specific
	 * <code>QTCaptureDecompressedVideoOutput</code> instance will output consecutive video frames.
	 *
	 * @param ptr
	 * 		a pointer to the <code>QTCaptureDecompressedVideoOutput</code> instance to get the minimum
	 * 		time interval between consecutive video frame output of
	 * @return the minimum time interval between which a specific
	 * <code>QTCaptureDecompressedVideoOutput</code> instance will output consecutive video
	 * frames. It is equivalent to the inverse of the maximum frame rate. The value of
	 * <code>0</code> indicates an unlimited maximum frame rate.
	 */
	private static native double minimumVideoFrameInterval(long ptr);

	public NSDictionary pixelBufferAttributes()
	{
		long pixelBufferAttributesPtr = pixelBufferAttributes(getPtr());

		return (pixelBufferAttributesPtr == 0) ? null : new NSDictionary(pixelBufferAttributesPtr);
	}

	private static native long pixelBufferAttributes(long ptr);

	public boolean setAutomaticallyDropsLateVideoFrames(boolean automaticallyDropsLateVideoFrames)
	{
		return setAutomaticallyDropsLateVideoFrames(getPtr(), automaticallyDropsLateVideoFrames);
	}

	private static native boolean setAutomaticallyDropsLateVideoFrames(long ptr,
			boolean automaticallyDropsLateVideoFrames);

	public void setDelegate(Delegate delegate)
	{
		setDelegate(getPtr(), delegate);
	}

	private static native void setDelegate(long ptr, Delegate delegate);

	/**
	 * Sets the minimum time interval between which this <code>QTCaptureDecompressedVideoOutput</code>
	 * is to output consecutive video frames.
	 *
	 * @param minimumVideoFrameInterval
	 * 		the minimum time interval between which this <code>QTCaptureDecompressedVideoOutput</code>
	 * 		is to output consecutive video frames. It is equivalent to the inverse of the maximum
	 * 		frame rate. The value of <code>0</code> indicates an unlimited frame rate.
	 */
	public void setMinimumVideoFrameInterval(double minimumVideoFrameInterval)
	{
		setMinimumVideoFrameInterval(getPtr(), minimumVideoFrameInterval);
	}

	/**
	 * Sets the minimum time interval between which a specific
	 * <code>QTCaptureDecompressedVideoOutput</code> instance is to output consecutive video frames.
	 *
	 * @param ptr
	 * 		a pointer to the <code>QTCaptureDecompressedVideoOutput</code> instance to set the minimum
	 * 		time interval between consecutive video frame output on
	 * @param minimumVideoFrameInterval
	 * 		the minimum time interval between which a specific
	 * 		<code>QTCaptureDecompressedVideoOutput</code> instance is to output consecutive video
	 * 		frames. It is equivalent to the inverse of the maximum frame rate. The value of
	 * 		<code>0</code> indicates an unlimited frame rate.
	 */
	private static native void setMinimumVideoFrameInterval(long ptr,
			double minimumVideoFrameInterval);

	public void setPixelBufferAttributes(NSDictionary pixelBufferAttributes)
	{
		setPixelBufferAttributes(getPtr(), pixelBufferAttributes.getPtr());
	}

	private static native void setPixelBufferAttributes(long ptr, long pixelBufferAttributesPtr);

	/**
	 * Represents the receiver of <code>CVImageBuffer</code> video frames and their associated
	 * <code>QTSampleBuffer</code>s captured by a <code>QTCaptureDecompressedVideoOutput</code>.
	 */
	public static abstract class Delegate
	{
		private MutableQTSampleBuffer sampleBuffer;

		private MutableCVPixelBuffer videoFrame;

		/**
		 * Notifies this <code>Delegate</code> that the <code>QTCaptureOutput</code> to which it is set has
		 * output a specific <code>CVImageBuffer</code> representing a video frame with a specific
		 * <code>QTSampleBuffer</code>.
		 *
		 * @param videoFrame
		 * 		the <code>CVImageBuffer</code> which represents the output video frame
		 * @param sampleBuffer
		 * 		the <code>QTSampleBuffer</code> which represents additional details about the output
		 * 		video samples
		 */
		public abstract void outputVideoFrameWithSampleBuffer(CVImageBuffer videoFrame,
				QTSampleBuffer sampleBuffer);

		void outputVideoFrameWithSampleBuffer(long videoFramePtr, long sampleBufferPtr)
		{
			if (videoFrame == null)
				videoFrame = new MutableCVPixelBuffer(videoFramePtr);
			else
				videoFrame.setPtr(videoFramePtr);

			if (sampleBuffer == null)
				sampleBuffer = new MutableQTSampleBuffer(sampleBufferPtr);
			else
				sampleBuffer.setPtr(sampleBufferPtr);

			outputVideoFrameWithSampleBuffer(videoFrame, sampleBuffer);
		}
	}

	/**
	 * Represents a <code>CVPixelBuffer</code> which allows public changing of the CoreVideo
	 * <code>CVPixelBufferRef</code> it represents.
	 */
	private static class MutableCVPixelBuffer extends CVPixelBuffer
	{
		/**
		 * Initializes a new <code>MutableCVPixelBuffer</code> which is to represent a specific
		 * CoreVideo <code>CVPixelBufferRef</code>.
		 *
		 * @param ptr
		 * 		the CoreVideo <code>CVPixelBufferRef</code> to be represented by the new instance
		 */
		private MutableCVPixelBuffer(long ptr)
		{
			super(ptr);
		}

		/**
		 * Sets the CoreVideo <code>CVImageBufferRef</code> represented by this instance.
		 *
		 * @param ptr
		 * 		the CoreVideo <code>CVImageBufferRef</code> to be represented by this instance
		 * @see CVPixelBuffer#setPtr(long)
		 */
		@Override
		public void setPtr(long ptr)
		{
			super.setPtr(ptr);
		}
	}

	/**
	 * Represents a <code>QTSampleBuffer</code> which allows public changing of the QTKit
	 * <code>QTSampleBuffer</code> object it represents.
	 */
	private static class MutableQTSampleBuffer extends QTSampleBuffer
	{
		/**
		 * Initializes a new <code>MutableQTSampleBuffer</code> instance which is to represent a
		 * specific QTKit <code>QTSampleBuffer</code> object.
		 *
		 * @param ptr
		 * 		the pointer to the QTKit <code>QTSampleBuffer</code> object to be represented by the
		 * 		new instance
		 */
		private MutableQTSampleBuffer(long ptr)
		{
			super(ptr);
		}

		/**
		 * Sets the pointer to the Objective-C object represented by this instance.
		 *
		 * @param ptr
		 * 		the pointer to the Objective-C object to be represented by this instance
		 * @see QTSampleBuffer#setPtr(long)
		 */
		@Override
		public void setPtr(long ptr)
		{
			super.setPtr(ptr);
		}
	}
}
