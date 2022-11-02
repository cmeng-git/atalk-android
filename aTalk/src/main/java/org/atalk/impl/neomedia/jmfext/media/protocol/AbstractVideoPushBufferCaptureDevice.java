/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import javax.media.MediaLocator;
import javax.media.control.FrameRateControl;

/**
 * Provides a base implementation of <code>PushBufferDataSource</code> and <code>CaptureDevice</code> for
 * the purposes of video in order to facilitate implementers by taking care of boilerplate in the
 * most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractVideoPushBufferCaptureDevice extends AbstractPushBufferCaptureDevice
{

	/**
	 * Initializes a new <code>AbstractVideoPushBufferCaptureDevice</code> instance.
	 */
	protected AbstractVideoPushBufferCaptureDevice()
	{
		this(null);
	}

	/**
	 * Initializes a new <code>AbstractVideoPushBufferCaptureDevice</code> instance from a specific
	 * <code>MediaLocator</code>.
	 *
	 * @param locator
	 * 		the <code>MediaLocator</code> to create the new instance from
	 */
	protected AbstractVideoPushBufferCaptureDevice(MediaLocator locator)
	{
		super(locator);
	}

	/**
	 * Creates a new <code>FrameRateControl</code> instance which is to allow the getting and
	 * setting of the frame rate of this <code>AbstractVideoPushBufferCaptureDevice</code>.
	 *
	 * @return a new <code>FrameRateControl</code> instance which is to allow the getting and
	 * setting of the frame rate of this <code>AbstractVideoPushBufferCaptureDevice</code>
	 * @see AbstractPushBufferCaptureDevice#createFrameRateControl()
	 */
	@Override
	protected FrameRateControl createFrameRateControl()
	{
		return null;
	}
}
