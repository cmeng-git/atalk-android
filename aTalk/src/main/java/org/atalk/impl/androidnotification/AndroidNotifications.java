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
 *
 * All pop-message events must be assigned to any one of the android xxx_GROUP, otherwise it will be blocked.
 * Each of these xxx_GROUP's will appear in android Notifications setting and user may disable it.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidNotifications implements BundleActivator
{
    /**
     * Calls notification group.
     */
    public static final String CALL_GROUP = "call";

    /**
     * Message notifications group.
     */
    public static final String MESSAGE_GROUP = "message";

    /**
     * Calls notification group.
     */
    public static final String FILE_GROUP = "file";

    /**
     * Default group that uses aTalk icon for notifications
     */
    public static final String DEFAULT_GROUP = "default";

    /**
     * Missed call event.
     */
    public static final String SILENT_GROUP = "silent";


    public static List<String> notificationIds
            = Arrays.asList(CALL_GROUP, MESSAGE_GROUP, FILE_GROUP, DEFAULT_GROUP, SILENT_GROUP);

    /**
     * Overrides SIP default notifications to suit Android devices available resources
     *
     * {@inheritDoc}
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        NotificationService notificationService = ServiceUtils.getService(bundleContext, NotificationService.class);
        if (notificationService == null)
            return;

        // Incoming call: modified default incoming call notification to be played only on notification stream.
        SoundNotificationAction inCallSoundHandler = new SoundNotificationAction(SoundProperties.INCOMING_CALL,
                2000, true, false, false);
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallSoundHandler);

        // Incoming call: Adds basic vibrate notification for incoming call
        VibrateNotificationAction inCallVibrate = new VibrateNotificationAction(NotificationManager.INCOMING_CALL,
                new long[]{1800, 1000}, 0);
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL, inCallVibrate);

        //  cmeng 20200525: added back for JingleMessage support
        // Incoming call Popup Message: replace with aTalk INCOMING_CALL popup message;
        // notificationService.removeEventNotificationAction(NotificationManager.INCOMING_CALL,
        //        NotificationAction.ACTION_POPUP_MESSAGE);

        // Incoming call : new(No default message, Notification hide timeout, displayed on incoming call icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.INCOMING_CALL,
                new PopupMessageNotificationAction(null, -1, CALL_GROUP));

        // Missed call : new(No default message, Notification hide timeout, displayed on missed call icon)
        notificationService.registerDefaultNotificationForEvent(NotificationManager.MISSED_CALL,
                new PopupMessageNotificationAction(null, -1, CALL_GROUP));

        notificationService.registerDefaultNotificationForEvent(NotificationManager.CALL_SECURITY_ERROR,
                new PopupMessageNotificationAction(null, -1, CALL_GROUP));

        notificationService.registerDefaultNotificationForEvent(NotificationManager.SECURITY_MESSAGE,
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
                new SoundNotificationAction(SoundProperties.INCOMING_INVITATION, -1,
                        true, false, false));

        // Remove obsoleted/unused events
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
