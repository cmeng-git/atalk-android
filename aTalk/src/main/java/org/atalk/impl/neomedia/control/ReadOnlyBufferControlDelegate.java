/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import java.awt.Component;

import javax.media.control.BufferControl;

/**
 * Represents a wrapper of a specific <code>BufferControl</code> which does not call the setters of the
 * wrapped instance and calls only the getters.
 *
 * @author Lubomir Marinov
 */
public class ReadOnlyBufferControlDelegate implements BufferControl
{

	/**
	 * The <code>BufferControl</code> wrapped by this instance.
	 */
	private final BufferControl bufferControl;

	/**
	 * Initializes a new <code>ReadOnlyBufferControlDelegate</code> instance which is to wrap a
	 * specific <code>BufferControl</code> and call only its getters.
	 *
	 * @param bufferControl
	 * 		the <code>BufferControl</code> to be wrapped by the new instance
	 */
	public ReadOnlyBufferControlDelegate(BufferControl bufferControl)
	{
		this.bufferControl = bufferControl;
	}

	/**
	 * Implements {@link BufferControl#getBufferLength()}. Gets the length in milliseconds of the
	 * buffering performed by the owner of the wrapped <code>BufferControl</code>.
	 *
	 * @return the length in milliseconds of the buffering performed by the owner of the wrapped
	 * <code>BufferControl</code>
	 */
	public long getBufferLength()
	{
		return bufferControl.getBufferLength();
	}

	/**
	 * Implements {@link javax.media.Control#getControlComponent()}. Gets the UI <code>Component</code>
	 * representing this instance and exported by the owner of the wrapped <code>BufferControl</code>.
	 *
	 * @return the UI <code>Component</code> representing the wrapped <code>BufferControl</code> and
	 * exported by its owner if such a <code>Component</code> is available; otherwise,
	 * <code>null</code>
	 */
	public Component getControlComponent()
	{
		return bufferControl.getControlComponent();
	}

	/**
	 * Implements {@link BufferControl#getEnabledThreshold()}. Gets the indicator of the wrapped
	 * <code>BufferControl</code> which determines whether threshold calculations are enabled.
	 *
	 * @return <code>true</code> if threshold calculations are enabled in the wrapped
	 * <code>BufferControl</code>; otherwise, <code>false</code>
	 */
	public boolean getEnabledThreshold()
	{
		return bufferControl.getEnabledThreshold();
	}

	/**
	 * Implements {@link BufferControl#getMinimumThreshold()}. Gets the minimum threshold in
	 * milliseconds for the buffering performed by the owner of the wrapped <code>BufferControl</code>.
	 *
	 * @return the minimum threshold in milliseconds for the buffering performed by the owner of
	 * the wrapped <code>BufferControl</code>
	 */
	public long getMinimumThreshold()
	{
		return bufferControl.getMinimumThreshold();
	}

	/**
	 * Implements {@link BufferControl#setBufferLength(long)}. Ignores the request because this
	 * instance provides read-only support and returns the value actually in effect.
	 *
	 * @param bufferLength
	 * 		the length in milliseconds of the buffering to be performed by the owner of the
	 * 		wrapped <code>BufferControl</code>
	 * @return the length in milliseconds of the buffering performed by the owner of the wrapped
	 * <code>BufferControl</code> that is actually in effect
	 */
	public long setBufferLength(long bufferLength)
	{
		return getBufferLength();
	}

	/**
	 * Implements {@link BufferControl#setEnabledThreshold(boolean)}. Ignores the set request
	 * because this instance provides read-only support.
	 *
	 * @param enabledThreshold
	 * 		<code>true</code> if threshold calculations are to be enabled; otherwise, <code>false</code>
	 */
	public void setEnabledThreshold(boolean enabledThreshold)
	{
	}

	/**
	 * Implements {@link BufferControl#setMinimumThreshold(long)}. Ignores the set request because
	 * this instance provides read-only support and returns the value actually in effect.
	 *
	 * @param minimumThreshold
	 * 		the minimum threshold in milliseconds for the buffering to be performed by the owner
	 * 		of the wrapped <code>BufferControl</code>
	 * @return the minimum threshold in milliseconds for the buffering performed by the owner of
	 * the wrapped <code>BufferControl</code> that is actually in effect
	 */
	public long setMinimumThreshold(long minimumThreshold)
	{
		return getMinimumThreshold();
	}
}
