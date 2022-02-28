/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.device;

/**
 * Represents a special-purpose <code>MediaDevice</code> which is effectively built on top of and
 * forwarding to another <code>MediaDevice</code>.
 *
 * @author Lyubomir Marinov
 */
public interface MediaDeviceWrapper extends MediaDevice
{
	/**
	 * Gets the actual <code>MediaDevice</code> which this <code>MediaDevice</code> is effectively built on
	 * top of and forwarding to.
	 *
	 * @return the actual <code>MediaDevice</code> which this <code>MediaDevice</code> is effectively built
	 *         on top of and forwarding to
	 */
	public MediaDevice getWrappedDevice();
}
