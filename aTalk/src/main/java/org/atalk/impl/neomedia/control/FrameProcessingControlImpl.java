/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import java.awt.Component;

import javax.media.control.FrameProcessingControl;

/**
 * Provides a base implementation of <code>FrameProcessingControl</code> which keeps track of the
 * <code>minimalProcessing</code> property, switches its value to <code>true</code> when it's notified that
 * its owner is at least one frame behind and doesn't implement the <code>controlComponent</code> and
 * <code>framesDropped</code> properties.
 *
 * @author Lyubomir Marinov
 */
public class FrameProcessingControlImpl implements FrameProcessingControl
{

	/**
	 * The indicator which determines whether the owner of this <code>FrameProcessingControl</code> is
	 * to perform only the minimum operations necessary to keep it working normally but without
	 * producing output.
	 */
	private boolean minimalProcessing = false;

	/**
	 * Gets the UI <code>Component</code> associated with this <code>Control</code> object.
	 *
	 * @return the UI <code>Component</code> associated with this <code>Control</code> object
	 */
	public Component getControlComponent()
	{
		/*
		 * We totally don't care about providing a UI component which controls frame drop from
		 * inside the media implementation.
		 */
		return null;
	}

	/**
	 * Gets the number of output frames that were dropped during processing since the last call to
	 * this method.
	 *
	 * @return the number of output frame that were dropped during processing since the last
	 * call to this method
	 */
	public int getFramesDropped()
	{
		return 0; // Not implemented.
	}

	/**
	 * Determines whether the owner of this <code>FrameProcessingControl</code> is to perform only the
	 * minimum operations necessary to keep it working normally but without producing output.
	 *
	 * @return <code>true</code> if the owner of this <code>FrameProcessingControl</code> is to perform
	 * only the minimum operations necessary to keep it working normally but without producing
	 * output; otherwise, <code>false</code>
	 */
	public boolean isMinimalProcessing()
	{
		return minimalProcessing;
	}

	/**
	 * Sets the number of frames the owner of this <code>FrameProcessingControl</code> is lagging
	 * behind. It is a hint to do minimal processing for the next <code>framesBehind</code> frames in
	 * order to catch up.
	 *
	 * @param framesBehind
	 * 		the number of frames the owner of this <code>FrameProcessingControl</code> is lagging
	 * 		behind
	 */
	public void setFramesBehind(float framesBehind)
	{
		setMinimalProcessing(framesBehind > 0);
	}

	/**
	 * Sets the indicator which determines whether the owner of this
	 * <code>FrameProcessingControl</code> is to perform only the minimal operations necessary to
	 * keep it working normally but without producing output.
	 *
	 * @param minimalProcessing
	 * 		<code>true</code> if minimal processing mode is to be turned on or <code>false</code> if
	 * 		minimal processing mode is to be turned off
	 * @return the actual minimal processing mode in effect after the set attempt
	 */
	public boolean setMinimalProcessing(boolean minimalProcessing)
	{
		this.minimalProcessing = minimalProcessing;
		return this.minimalProcessing;
	}
}
