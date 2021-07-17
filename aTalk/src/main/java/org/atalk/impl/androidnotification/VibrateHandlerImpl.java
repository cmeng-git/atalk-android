/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification;

import android.content.Context;
import android.os.Vibrator;

import net.java.sip.communicator.service.notification.*;

import org.atalk.android.aTalkApp;

/**
 * Android implementation of {@link VibrateNotificationHandler}.
 *
 * @author Pawel Domas
 */
public class VibrateHandlerImpl implements VibrateNotificationHandler
{
	/**
	 * The <tt>Vibrator</tt> if present on this device.
	 */
	private final Vibrator vibratorService;

	/**
	 * Creates new instance of <tt>VibrateHandlerImpl</tt>.
	 */
	public VibrateHandlerImpl() {
		this.vibratorService = (Vibrator) aTalkApp.getGlobalContext().getSystemService(Context.VIBRATOR_SERVICE);
	}

	/**
	 * Returns <tt>true</tt> if the <tt>Vibrator</tt> service is present on this device.
	 *
	 * @return <tt>true</tt> if the <tt>Vibrator</tt> service is present on this device.
	 */
	private boolean hasVibrator()
	{
		return (vibratorService != null) && vibratorService.hasVibrator();
	}

	/**
	 * {@inheritDoc}
	 */
	public void vibrate(VibrateNotificationAction action)
	{
		if (!hasVibrator())
			return;
		vibratorService.vibrate(action.getPattern(), action.getRepeat());
	}

	/**
	 * {@inheritDoc}
	 */
	public void cancel()
	{
		if (!hasVibrator())
			return;
		vibratorService.cancel();
	}

	/**
	 * {@inheritDoc}
	 */
	public String getActionType()
	{
		return NotificationAction.ACTION_VIBRATE;
	}
}
