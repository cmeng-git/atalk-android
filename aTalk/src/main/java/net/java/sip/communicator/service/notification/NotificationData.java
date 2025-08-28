/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification;

import java.util.Collections;
import java.util.Map;

/**
 * Object to cache fired notifications before all handler implementations are ready registered.
 *
 * @author Ingo Bauersachs
 */
public class NotificationData {
    /**
     * The name/key of the <code>NotificationData</code> extra which is provided to
     * {@link CommandNotificationHandler#execute(CommandNotificationAction, Map)} i.e. a
     * <code>Map&lt;String,String&gt;</code> which is known by the (argument) name <code>cmdargs</code>.
     */
    public static final String COMMAND_NOTIFICATION_HANDLER_CMDARGS_EXTRA = "CommandNotificationHandler.cmdargs";

    /**
     * The name/key of the <code>NotificationData</code> extra which is provided to
     * {@link PopupMessageNotificationHandler#popupMessage(PopupMessageNotificationAction, NotificationData)}
     * i.e. NotificationData contains an <code>Object</code> which is known by the (argument) name <code>tag</code>.
     */
    public static final String POPUP_MESSAGE_HANDLER_TAG_EXTRA = "PopupMessageNotificationHandler.tag";

    /**
     * The name/key of the <code>NotificationData</code> extra which is provided to {@link SoundNotificationHandler} i.e. a
     * <code>Callable&lt;Boolean&gt;</code> which is known as the condition which determines whether looping sounds are to
     * continue playing.
     */
    public static final String SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA = "SoundNotificationHandler.loopCondition";

    /**
     * The type of the event that we'd like to fire a notification for.
     *
     * @see net.java.sip.communicator.plugin.notificationwiring.NotificationManager
     */
    private final String eventType;

    /**
     * The sub-category of the event type.
     *
     * @see net.java.sip.communicator.service.systray.SystrayService
     */
    private final int msgType;

    /**
     * The {@link NotificationHandler}-specific extras provided to this instance. The keys are among the
     * <code>XXX_EXTRA</code> constants defined by the <code>NotificationData</code> class.
     */
    private final Map<String, Object> extras;

    private final byte[] icon;
    private final String message;
    private final String title;

    /**
     * Creates a new instance of this class.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param title the title of the given message
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     * @param extras additional/extra {@link NotificationHandler}-specific data to be provided
     * by the new instance to the various <code>NotificationHandler</code>
     */
    NotificationData(String eventType, int msgType, String title, String message, byte[] icon, Map<String, Object> extras) {
        this.eventType = eventType;
        this.msgType = msgType;
        this.title = title;
        this.message = message;
        this.icon = icon;
        this.extras = extras;
    }

    /**
     * Gets the type of the event that we'd like to fire a notification for
     *
     * @return the eventType
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * Gets the msgType of the event that we'd like to fire a notification for
     *
     * @return the msgType
     */
    public int getMessageType() {
        return msgType;
    }

    /**
     * Gets the {@link NotificationHandler}-specific extras provided to this instance.
     *
     * @return the <code>NotificationHandler</code>-specific extras provided to this instance. The keys are among the
     * <code>XXX_EXTRA</code> constants defined by the <code>NotificationData</code> class
     */
    public Map<String, Object> getExtras() {
        return Collections.unmodifiableMap(extras);
    }

    /**
     * Gets the {@link NotificationHandler}-specific extra provided to this instance associated with a specific key.
     *
     * @param key the key whose associated <code>NotificationHandler</code>-specific extra is to be returned. Well known keys
     * are defined by the <code>NotificationData</code> class as the <code>XXX_EXTRA</code> constants.
     *
     * @return the <code>NotificationHandler</code>-specific extra provided to this instance associated with the specified
     * <code>key</code>
     */
    public Object getExtra(String key) {
        return (extras == null) ? null : extras.get(key);
    }

    /**
     * Gets the icon to show in the notification if and where appropriate.
     *
     * @return the icon
     */
    public byte[] getIcon() {
        return icon;
    }

    /**
     * Gets the message to use if and where appropriate (e.g. with systray or log notification).
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the title of the given message.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }
}
