/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import org.atalk.impl.neomedia.control.FrameRateControlAdapter;

import javax.media.MediaLocator;
import javax.media.control.FrameRateControl;

/**
 * Provides a base implementation of <code>PullBufferDataSource</code> and <code>CaptureDevice</code> for
 * the purposes of video in order to facilitate implementers by taking care of boilerplate in the
 * most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractVideoPullBufferCaptureDevice extends AbstractPullBufferCaptureDevice
{

	/**
	 * Initializes a new <code>AbstractVideoPullBufferCaptureDevice</code> instance.
	 */
	protected AbstractVideoPullBufferCaptureDevice()
	{
	}

	/**
	 * Initializes a new <code>AbstractVideoPullBufferCaptureDevice</code> instance from a specific
	 * <code>MediaLocator</code>.
	 *
	 * @param locator
	 * 		the <code>MediaLocator</code> to create the new instance from
	 */
	protected AbstractVideoPullBufferCaptureDevice(MediaLocator locator)
	{
		super(locator);
	}

	/**
	 * Creates a new <code>FrameRateControl</code> instance which is to allow the getting and
	 * setting of the frame rate of this <code>AbstractVideoPullBufferCaptureDevice</code>.
	 *
	 * @return a new <code>FrameRateControl</code> instance which is to allow the getting and
	 * setting of the frame rate of this <code>AbstractVideoPullBufferCaptureDevice</code>
	 * @see AbstractPullBufferCaptureDevice#createFrameRateControl()
	 */
	@Override
	protected FrameRateControl createFrameRateControl()
	{
		return new FrameRateControlAdapter()
		{
			/**
			 * The output frame rate of this <code>AbstractVideoPullBufferCaptureDevice</code>.
			 */
			private float frameRate = -1;

			@Override
			public float getFrameRate()
			{
				return frameRate;
			}

			@Override
			public float setFrameRate(float frameRate)
			{
				this.frameRate = frameRate;
				return this.frameRate;
			}
		};
	}
}
