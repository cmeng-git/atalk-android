/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.plugin.notificationwiring;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.plugin.notificationwiring.SoundProperties;
import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.PopupMessageNotificationAction;
import net.java.sip.communicator.service.notification.SoundNotificationAction;
import net.java.sip.communicator.service.notification.VibrateNotificationAction;
import net.java.sip.communicator.util.ServiceUtils;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Android notifications wiring which overrides some default notifications and adds vibrate actions.
 *
 * @author Pawel Domas
 */
public class AndroidNotifications implements BundleActivator
{
	/**
	 * Default group that will use Jitsi icon for notifications
	 */
	public static final String DEFAULT_GROUP = null;

	/**
	 * Message notifications group.
	 */
	public static final String MESSAGE_GROUP = "message";

	/**
	 * Calls notification group.
	 */
	public static final String FILE_GROUP = "file";
	
	/**
	 * Calls notification group.
	 */
	public static final String CALL_GROUP = "call";

	/**
	 * Missed call event.
	 */
	public static final String MISSED_CALL = "missed_call";

	/**
	 * {@inheritDoc}
	 */
	public void start(BundleContext bundleContext)
		throws Exception
	{
		// Overrides default notifications to fit Android
		NotificationService notificationService = ServiceUtils.getService(bundleContext, NotificationService.class);

		/**
		 * Override default incoming call notification to be played only on notification stream.
		 */
		SoundNotificationAction inCallSoundHandler = new SoundNotificationAction(SoundProperties.INCOMING_CALL, 2000, true, false, false);
		notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallSoundHandler);

		/**
		 * Adds basic vibrate notification for incoming call
		 */
		VibrateNotificationAction inCallVibrate = new VibrateNotificationAction("incoming_call", new long[] { 1800, 1000 }, 0);
		notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallVibrate);

		// Removes popup for incoming call
		notificationService.removeEventNotificationAction(NotificationManager.INCOMING_CALL, NotificationAction.ACTION_POPUP_MESSAGE);

		// Missed call : new(No default message, Notification hide timeout, displayed on Jitsi icon)
		notificationService.registerDefaultNotificationForEvent(MISSED_CALL, new PopupMessageNotificationAction(null, -1, CALL_GROUP));

		// Incoming message: new(No default message, Notification hide timeout, displayed on Jitsi icon)
		notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_MESSAGE, new PopupMessageNotificationAction(null, -1,
			MESSAGE_GROUP));

		// Incoming file: new(No default message, Notification hide timeout, displayed on Jitsi icon)
		notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_FILE, new PopupMessageNotificationAction(null, -1, FILE_GROUP));

		// Proactive notifications: new(No default message, Notification hide timeout, displayed on Jitsi icon)
		notificationService.registerDefaultNotificationForEvent(NotificationManager.PROACTIVE_NOTIFICATION, new PopupMessageNotificationAction(null, 7000,
			DEFAULT_GROUP));

		// Remove not-used events
		notificationService.removeEventNotification(NotificationManager.CALL_SAVED);
	}

	/**
	 * {@inheritDoc}
	 */
	public void stop(BundleContext bundleContext)
		throws Exception
	{
	}
}
