/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.plugin.notificationwiring.SoundProperties;
import net.java.sip.communicator.service.notification.*;
import net.java.sip.communicator.util.ServiceUtils;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.Arrays;
import java.util.List;

/**
 * Android notifications wiring which overrides some default notifications and adds vibrate actions.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidNotifications implements BundleActivator
{
    /**
     * Default group that will use aTalk icon for notifications
     */
    public static final String DEFAULT_GROUP = "default";

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
     * Missed call event.
     */
    public static final String SILENT_GROUP = "silent";


    public static List<String> notificationIds
            = Arrays.asList(DEFAULT_GROUP, MESSAGE_GROUP, FILE_GROUP, CALL_GROUP, MISSED_CALL, SILENT_GROUP);

    /**
     * Overrides SIP default notifications to suit Android devices available resources
     *
     * {@inheritDoc}
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        NotificationService notificationService = ServiceUtils.getService(bundleContext, NotificationService.class);

        // Incoming call: Removes popup message
        notificationService.removeEventNotificationAction(NotificationManager.INCOMING_CALL,
                NotificationAction.ACTION_POPUP_MESSAGE);

        // Incoming call: Override default incoming call notification to be played only on notification stream.
        SoundNotificationAction inCallSoundHandler = new SoundNotificationAction(SoundProperties.INCOMING_CALL,
                2000, true, false, false);
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallSoundHandler);

        // Incoming call: Adds basic vibrate notification for incoming call
        VibrateNotificationAction inCallVibrate = new VibrateNotificationAction(NotificationManager.INCOMING_CALL,
                new long[]{1800, 1000}, 0);
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallVibrate);

        // Missed call : new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.MISSED_CALL,
                new PopupMessageNotificationAction(null, -1, CALL_GROUP));

        // Incoming message: new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_MESSAGE,
                new PopupMessageNotificationAction(null, -1, MESSAGE_GROUP));

        // Incoming file: new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_FILE,
                new PopupMessageNotificationAction(null, -1, FILE_GROUP));

        // Proactive notifications: new(No default message, Notification hide timeout, displayed on aTalk icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.PROACTIVE_NOTIFICATION,
                new PopupMessageNotificationAction(null, 7000, SILENT_GROUP));

        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_INVITATION,
                new SoundNotificationAction(SoundProperties.INCOMING_INVITATION, -1, true, false, false));

        // Remove obosoleted/unused events
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
