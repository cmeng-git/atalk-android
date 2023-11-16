/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import java.util.EventListener;

/**
 * Represents a listener which is to be notified before and after an associated
 * <code>DeviceSystem</code>'s function to update the list of available devices is invoked.
 *
 * @author Lyubomir Marinov
 */
public interface UpdateAvailableDeviceListListener extends EventListener
{
	/**
	 * Notifies this listener that the associated <code>DeviceSystem</code>'s function to update the
	 * list of available devices was invoked.
	 *
	 * @throws Exception
	 *         if this implementation encounters an error. Any <code>Throwable</code> apart from
	 *         <code>ThreadDeath</code> will be ignored after it is logged for debugging purposes.
	 */
	void didUpdateAvailableDeviceList()
		throws Exception;

	/**
	 * Notifies this listener that the associated <code>DeviceSystem</code>'s function to update the
	 * list of available devices will be invoked.
	 *
	 * @throws Exception
	 *         if this implementation encounters an error. Any <code>Throwable</code> apart from
	 *         <code>ThreadDeath</code> will be ignored after it is logged for debugging purposes.
	 */
	void willUpdateAvailableDeviceList()
		throws Exception;
}
