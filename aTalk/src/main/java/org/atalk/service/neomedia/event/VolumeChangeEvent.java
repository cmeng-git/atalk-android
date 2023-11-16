/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

import org.atalk.service.neomedia.VolumeControl;

import java.util.EventObject;

/**
 * Represents the event fired when playback volume value has changed.
 *
 * @author Damian Minkov
 */
public class VolumeChangeEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The volume level.
	 */
	private final float level;

	/**
	 * The indicator which determines whether the volume is muted.
	 */
	private final boolean mute;

	/**
	 * Initializes a new <code>VolumeChangeEvent</code> which is to notify about a specific volume level
	 * and its mute state.
	 *
	 * @param source
	 *        the <code>VolumeControl</code> which is the source of the change
	 * @param level
	 *        the volume level
	 * @param mute
	 *        <code>true</code> if the volume is muted; otherwise, <code>false</code>
	 * @throws IllegalArgumentException
	 *         if source is <code>null</code>
	 */
	public VolumeChangeEvent(VolumeControl source, float level, boolean mute)
	{
		super(source);

		this.level = level;
		this.mute = mute;
	}

	/**
	 * Gets the <code>VolumeControl</code> which is the source of the change notified about by this
	 * <code>VolumeChangeEvent</code>.
	 *
	 * @return the <code>VolumeControl</code> which is the source of the change notified about by this
	 *         <code>VolumeChangeEvent</code>
	 */
	public VolumeControl getSourceVolumeControl()
	{
		return (VolumeControl) getSource();
	}

	/**
	 * Gets the volume level notified about by this <code>VolumeChangeEvent</code>.
	 *
	 * @return the volume level notified about by this <code>VolumeChangeEvent</code>
	 */
	public float getLevel()
	{
		return level;
	}

	/**
	 * Gets the indicator which determines whether the volume is muted.
	 *
	 * @return <code>true</code> if the volume is muted; otherwise, <code>false</code>
	 */
	public boolean getMute()
	{
		return mute;
	}
}
