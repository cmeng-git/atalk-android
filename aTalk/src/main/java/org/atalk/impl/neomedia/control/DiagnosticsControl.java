/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import javax.media.Control;

/**
 * Defines an FMJ <code>Control</code> which allows the diagnosis of the functional health of a
 * procedure/process.
 *
 * @author Lyubomir Marinov
 */
public interface DiagnosticsControl extends Control
{
	/**
	 * The constant which expresses a non-existent time in milliseconds for the purposes of
	 * {@link #getMalfunctioningSince()}. Explicitly chosen to be <code>0</code> rather than <code>-1</code>
	 * in the name of efficiency.
	 */
	public static final long NEVER = 0;

	/**
	 * Gets the time in milliseconds at which the associated procedure/process has started
	 * malfunctioning.
	 *
	 * @return the time in milliseconds at which the associated procedure/process has started
	 * malfunctioning or <code>NEVER</code> if the associated procedure/process is functioning
	 * normally
	 */
	public long getMalfunctioningSince();

	/**
	 * Returns a human-readable <code>String</code> representation of the associated procedure/process.
	 *
	 * @return a human-readable <code>String</code> representation of the associated procedure/process
	 */
	public String toString();
}
