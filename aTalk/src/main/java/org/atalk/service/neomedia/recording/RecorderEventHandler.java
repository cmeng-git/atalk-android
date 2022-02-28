/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording;

/**
 * An interface that allows handling of <code>RecorderEvent</code> instances, such as writing them to
 * disk in some format.
 *
 * @author Boris Grozev
 */
public interface RecorderEventHandler
{
	/**
	 * Handle a specific <code>RecorderEvent</code>
	 * 
	 * @param ev
	 *        the event to handle.
	 * @return
	 */
	public boolean handleEvent(RecorderEvent ev);

	/**
	 * Closes the <code>RecorderEventHandler</code>.
	 */
	public void close();
}
