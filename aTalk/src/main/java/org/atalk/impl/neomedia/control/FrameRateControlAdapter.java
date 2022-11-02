/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import java.awt.Component;

import javax.media.Control;
import javax.media.control.FrameRateControl;

/**
 * Provides a default implementation of <code>FrameRateControl</code>.
 *
 * @author Lyubomir Marinov
 */
public class FrameRateControlAdapter implements FrameRateControl
{
	/**
	 * Gets the UI <code>Component</code> associated with this <code>Control</code> object.
	 * <code>FrameRateControlAdapter</code> always returns <code>null</code>.
	 *
	 * @return the UI <code>Component</code> associated with this <code>Control</code> object
	 * @see Control#getControlComponent()
	 */
	public Component getControlComponent()
	{
		return null;
	}

	/**
	 * Gets the current output frame rate. <code>FrameRateControlAdapter</code> always returns
	 * <code>-1</code>.
	 *
	 * @return the current output frame rate if it is known; otherwise, <code>-1</code>
	 * @see FrameRateControl#getFrameRate()
	 */
	public float getFrameRate()
	{
		return -1;
	}

	/**
	 * Gets the maximum supported output frame rate. <code>FrameRateControlAdapter</code> always
	 * returns <code>-1</code>.
	 *
	 * @return the maximum supported output frame rate if it is known; otherwise, <code>-1</code>
	 * @see FrameRateControl#getMaxSupportedFrameRate()
	 */
	public float getMaxSupportedFrameRate()
	{
		return -1;
	}

	/**
	 * Gets the default/preferred output frame rate. <code>FrameRateControlAdapter</code> always
	 * returns
	 * <code>-1</code>.
	 *
	 * @return the default/preferred output frame rate if it is known; otherwise, <code>-1</code>
	 * @see FrameRateControl#getPreferredFrameRate()
	 */
	public float getPreferredFrameRate()
	{
		return -1;
	}

	/**
	 * Sets the output frame rate. <code>FrameRateControlAdapter</code> always returns <code>-1</code>.
	 *
	 * @param frameRate
	 * 		the output frame rate to change the current one to
	 * @return the actual current output frame rate or <code>-1</code> if it is unknown or not
	 * controllable
	 * @see FrameRateControl#setFrameRate(float)
	 */
	public float setFrameRate(float frameRate)
	{
		return -1;
	}
}
