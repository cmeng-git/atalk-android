/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

/**
 * All datasources that support muting functionality implement <code>MuteDataSource</code>.
 *
 * @author Damian Minkov
 */
public interface MuteDataSource
{
	/**
	 * Determines whether this <code>DataSource</code> is mute.
	 *
	 * @return <code>true</code> if this <code>DataSource</code> is mute; otherwise, <code>false</code>
	 */
	public boolean isMute();

	/**
	 * Sets the mute state of this <code>DataSource</code>.
	 *
	 * @param mute
	 *        <code>true</code> to mute this <code>DataSource</code>; otherwise, <code>false</code>
	 */
	public void setMute(boolean mute);
}
