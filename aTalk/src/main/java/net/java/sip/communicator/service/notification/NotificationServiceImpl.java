/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification;

import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent;
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.gui.settings.TimePreference;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;

import java.util.*;

import timber.log.Timber;

import static net.java.sip.communicator.service.notification.NotificationAction.ACTION_COMMAND;
import static net.java.sip.communicator.service.notification.NotificationAction.ACTION_LOG_MESSAGE;
import static net.java.sip.communicator.service.notification.NotificationAction.ACTION_POPUP_MESSAGE;
import static net.java.sip.communicator.service.notification.NotificationAction.ACTION_SOUND;
import static net.java.sip.communicator.service.notification.NotificationAction.ACTION_VIBRATE;
import static net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent.ACTION_ADDED;
import static net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent.ACTION_CHANGED;
import static net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent.ACTION_REMOVED;
import static net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent.EVENT_TYPE_ADDED;
import static net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent.EVENT_TYPE_REMOVED;

/**
 * The implementation of the <code>NotificationService</code>.
 *
 * @author Yana Stamcheva
 * @author Ingo Bauersachs
 * @author Eng Chong Meng
 */
class NotificationServiceImpl implements NotificationService {
    private static final String NOTIFICATIONS_PREFIX = "notifications";

    /**
     * Defines the number of actions that have to be registered before cached notifications are fired.
     * <p>
     * Current value = 4 (vibrate action excluded).
     */
    public static final int NUM_ACTIONS = 4;

    /**
     * A list of all registered <code>NotificationChangeListener</code>s.
     */
    private final List<NotificationChangeListener> changeListeners = new Vector<>();

    private final ConfigurationService configService = NotificationServiceActivator.getConfigurationService();

    /**
     * A set of all registered event notifications.
     */
    private final Map<String, Notification> defaultNotifications = new HashMap<>();

    /**
     * Contains the notification handler per action type.
     */
    private final Map<String, NotificationHandler> handlers = new HashMap<>();

    /**
     * Queue to cache fired notifications before all handlers are registered.
     */
    private Queue<NotificationData> notificationCache = new LinkedList<>();

    /**
     * A set of all registered event notifications.
     */
    private final Map<String, Notification> notifications = new HashMap<>();

    /**
     * Creates an instance of <code>NotificationServiceImpl</code> by loading all previously saved notifications.
     */
    NotificationServiceImpl() {
        // Load all previously saved notifications.
        this.loadNotifications();
    }

    /**
     * Adds an object that executes the actual action of a notification action. If the same action
     * type is added twice, the last added wins.
     *
     * @param handler The handler that executes the action.
     */
    public void addActionHandler(NotificationHandler handler) {
        if (handler == null)
            throw new IllegalArgumentException("handler cannot be null");

        synchronized (handlers) {
            handlers.put(handler.getActionType(), handler);
            if ((handlers.size() == NUM_ACTIONS) && (notificationCache != null)) {
                for (NotificationData event : notificationCache) {
                    fireNotification(event);
                }
                notificationCache.clear();
                notificationCache = null;
            }
        }
    }

    /**
     * Adds the given <code>listener</code> to the list of change listeners.
     *
     * @param listener the listener that we'd like to register to listen for changes in the event
     * notifications stored by this service.
     */
    public void addNotificationChangeListener(NotificationChangeListener listener) {
        synchronized (changeListeners) {
            changeListeners.add(listener);
        }
    }

    /**
     * Checking an action when it is edited (property.default=false). Checking for older versions
     * of the property. If it is older one we migrate it to new configuration using the default values.
     *
     * @param eventType the event type.
     * @param defaultAction the default action which values we will use.
     */
    private void checkDefaultAgainstLoadedNotification(String eventType, NotificationAction defaultAction) {
        // checking for new sound action properties
        if (defaultAction instanceof SoundNotificationAction) {
            SoundNotificationAction soundDefaultAction = (SoundNotificationAction) defaultAction;
            SoundNotificationAction soundAction = (SoundNotificationAction)
                    getEventNotificationAction(eventType, ACTION_SOUND);

            boolean isSoundNotificationEnabledPropExist = getNotificationActionProperty
                    (eventType, defaultAction, "isSoundNotificationEnabled") != null;
            if (!isSoundNotificationEnabledPropExist) {
                soundAction.setSoundNotificationEnabled(soundDefaultAction.isSoundNotificationEnabled());
            }

            boolean isSoundPlaybackEnabledPropExist = getNotificationActionProperty(eventType,
                    defaultAction, "isSoundPlaybackEnabled") != null;
            if (!isSoundPlaybackEnabledPropExist) {
                soundAction.setSoundPlaybackEnabled(soundDefaultAction.isSoundPlaybackEnabled());
            }

            boolean isSoundPCSpeakerEnabledPropExist = getNotificationActionProperty(eventType,
                    defaultAction, "isSoundPCSpeakerEnabled") != null;
            if (!isSoundPCSpeakerEnabledPropExist) {
                soundAction.setSoundPCSpeakerEnabled(soundDefaultAction.isSoundPCSpeakerEnabled());
            }

            // cmeng: does not apply to aTalk - can be removed
            boolean fixDialingLoop = false;
            // hack to fix wrong value:just check whether loop for outgoing call (dialing) has
            // gone into config as 0, should be -1
            if (eventType.equals("Dialing") && soundAction.getLoopInterval() == 0) {
                soundAction.setLoopInterval(soundDefaultAction.getLoopInterval());
                fixDialingLoop = true;
            }

            if (!(isSoundNotificationEnabledPropExist
                    && isSoundPCSpeakerEnabledPropExist
                    && isSoundPlaybackEnabledPropExist) || fixDialingLoop) {
                // this check is done only when the notification is edited and is not default
                saveNotification(eventType, soundAction, soundAction.isEnabled(), false);
            }
        }

        // Just update the modified popUp Message action properties
        else if (defaultAction instanceof PopupMessageNotificationAction) {
            PopupMessageNotificationAction popUpAction = (PopupMessageNotificationAction) defaultAction;
            saveNotification(eventType, popUpAction, popUpAction.isEnabled(), false);
        }
    }

    /**
     * Executes a notification data object on the handlers on conditions:
     * a. EvenType is active
     * b. The specific action of the eventype is enabled
     * c. There is a valid handler for the action
     *
     * @param data The notification data to act upon.
     */
    private void fireNotification(NotificationData data) {
        Notification notification = notifications.get(data.getEventType());
        if ((notification == null) || !notification.isActive())
            return;

        // Loop and take action for each action that is enabled
        for (NotificationAction action : notification.getActions().values()) {
            String actionType = action.getActionType();
            if (!action.isEnabled())
                continue;

            NotificationHandler handler = handlers.get(actionType);
            if (handler == null)
                continue;

            try {
                switch (actionType) {
                    case ACTION_LOG_MESSAGE:
                        ((LogMessageNotificationHandler) handler).logMessage(
                                (LogMessageNotificationAction) action, data.getMessage());
                        break;

                    case ACTION_SOUND:
                        SoundNotificationAction soundNotificationAction = (SoundNotificationAction) action;
                        if (!isQuietHours() && (soundNotificationAction.isSoundNotificationEnabled()
                                || soundNotificationAction.isSoundPlaybackEnabled()
                                || soundNotificationAction.isSoundPCSpeakerEnabled())) {
                            ((SoundNotificationHandler) handler).start((SoundNotificationAction) action, data);
                        }
                        break;

                    case ACTION_COMMAND:
                        @SuppressWarnings("unchecked")
                        Map<String, String> cmdargs = (Map<String, String>) data.getExtra(
                                NotificationData.COMMAND_NOTIFICATION_HANDLER_CMDARGS_EXTRA);
                        ((CommandNotificationHandler) handler).execute((CommandNotificationAction) action, cmdargs);
                        break;

                    case ACTION_VIBRATE:
                        ((VibrateNotificationHandler) handler).vibrate((VibrateNotificationAction) action);
                        break;

                    case ACTION_POPUP_MESSAGE:
                    default:
                        ((PopupMessageNotificationHandler) handler).popupMessage((PopupMessageNotificationAction) action, data);
                        break;
                }
            } catch (Exception e) {
                Timber.e(e, "Error dispatching notification of type %s from %s", actionType, handler);
            }
        }
    }

    /**
     * Check if Quite Hours is in effect
     *
     * @return false if option is not enable or is not wihtin the quite hours period.
     */
    public static boolean isQuietHours() {
        if (!ConfigurationUtils.isQuiteHoursEnable())
            return false;

        final long startTime = TimePreference.minutesToTimestamp(ConfigurationUtils.getQuiteHoursStart());
        final long endTime = TimePreference.minutesToTimestamp(ConfigurationUtils.getQuiteHoursEnd());
        final long nowTime = Calendar.getInstance().getTimeInMillis();

        if (endTime < startTime) {
            return nowTime > startTime || nowTime < endTime;
        }
        else {
            return nowTime > startTime && nowTime < endTime;
        }
    }

    /**
     * If there is a registered event notification of the given <code>eventType</code> and the event
     * notification is currently activated, we go through the list of registered actions and execute them.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     *
     * @return An object referencing the notification. It may be used to stop a still running
     * notification. Can be null if the eventType is unknown, or the notification is not active.
     */
    public NotificationData fireNotification(String eventType) {
        return fireNotification(eventType, SystrayService.INFORMATION_MESSAGE_TYPE, null, null, null);
    }

    /**
     * If there is a registered event notification of the given <code>eventType</code> and the event
     * notification is currently activated, the list of registered actions is executed.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param title the title of the given message
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     *
     * @return An object referencing the notification. It may be used to stop a still running
     * notification. Can be null if the eventType is unknown or the notification is not active.
     */
    public NotificationData fireNotification(String eventType, int msgType, String title, String message, byte[] icon) {
        return fireNotification(eventType, msgType, title, message, icon, null);
    }

    /**
     * If there is a registered event notification of the given <code>eventType</code> and the event
     * notification is currently activated, the list of registered actions is executed.
     *
     * @param eventType the type of the event that we'd like to fire a notification for.
     * @param msgType the notification sub-category message type
     * @param title the title of the given message
     * @param message the message to use if and where appropriate (e.g. with systray or log notification.)
     * @param icon the icon to show in the notification if and where appropriate
     * @param extras additional/extra {@link NotificationHandler}-specific data to be provided to the firing
     * of the specified notification(s). The well-known keys are defined by the
     * <code>NotificationData</code> <code>XXX_EXTRA</code> constants.
     *
     * @return An object referencing the notification. It may be used to stop a still running
     * notification. Can be null if the eventType is unknown or the notification is not active.
     */
    public NotificationData fireNotification(String eventType, int msgType, String title, String message,
            byte[] icon, Map<String, Object> extras) {
        Notification notification = notifications.get(eventType);
        if ((notification == null) || !notification.isActive())
            return null;

        NotificationData data = new NotificationData(eventType, msgType, title, message, icon, extras);
        // cache the notification when the handlers are not yet ready
        // Timber.d("Fire notification for: %s %s", eventType, notificationCache);
        if (notificationCache != null)
            notificationCache.add(data);
        else
            fireNotification(data);
        return data;
    }

    /**
     * Notifies all registered <code>NotificationChangeListener</code>s that a
     * <code>NotificationActionTypeEvent</code> has occurred.
     *
     * @param eventType the type of the event, which is one of ACTION_XXX constants declared in the
     * <code>NotificationActionTypeEvent</code> class.
     * @param sourceEventType the <code>eventType</code>, which is the parent of the action
     * @param action the notification action
     */
    private void fireNotificationActionTypeEvent(String eventType, String sourceEventType, NotificationAction action) {
        NotificationActionTypeEvent event
                = new NotificationActionTypeEvent(this, eventType, sourceEventType, action);

        for (NotificationChangeListener listener : changeListeners) {
            switch (eventType) {
                case ACTION_ADDED:
                    listener.actionAdded(event);
                    break;
                case ACTION_REMOVED:
                    listener.actionRemoved(event);
                    break;
                case ACTION_CHANGED:
                    listener.actionChanged(event);
                    break;
            }
        }
    }

    /**
     * Notifies all registered <code>NotificationChangeListener</code>s that a
     * <code>NotificationEventTypeEvent</code> has occurred.
     *
     * @param eventType the type of the event, which is one of EVENT_TYPE_XXX constants declared in the
     * <code>NotificationEventTypeEvent</code> class.
     * @param sourceEventType the <code>eventType</code>, for which this event is about
     */
    private void fireNotificationEventTypeEvent(String eventType, String sourceEventType) {
        Timber.d("Dispatching NotificationEventType Change. Listeners = %s evt = %s",
                changeListeners.size(), eventType);

        NotificationEventTypeEvent event = new NotificationEventTypeEvent(this, eventType, sourceEventType);
        for (NotificationChangeListener listener : changeListeners) {
            if (eventType.equals(EVENT_TYPE_ADDED)) {
                listener.eventTypeAdded(event);
            }
            else if (eventType.equals(EVENT_TYPE_REMOVED)) {
                listener.eventTypeRemoved(event);
            }
        }
    }

    /**
     * Gets a list of handler for the specified action type.
     *
     * @param actionType the type for which the list of handlers should be retrieved or <code>null</code> if all
     * handlers shall be returned.
     */
    public Iterable<NotificationHandler> getActionHandlers(String actionType) {
        if (actionType != null) {
            NotificationHandler handler = handlers.get(actionType);
            Set<NotificationHandler> ret;
            if (handler == null)
                ret = Collections.emptySet();
            else
                ret = Collections.singleton(handler);
            return ret;
        }
        else
            return handlers.values();
    }

    /**
     * Returns the notification action corresponding to the given <code>eventType</code> and <code>actionType</code>.
     *
     * @param eventType the type of the event that we'd like to retrieve.
     * @param actionType the type of the action that we'd like to retrieve a descriptor for.
     *
     * @return the notification action of the action to be executed when an event of the specified type has occurred.
     */
    public NotificationAction getEventNotificationAction(String eventType, String actionType) {
        Notification notification = notifications.get(eventType);
        return (notification == null) ? null : notification.getAction(actionType);
    }

    /**
     * Getting a notification property directly from configuration service. Used to check do we
     * have an updated version of already saved/edited notification configurations. Detects old configurations.
     *
     * @param eventType the event type
     * @param action the action which property to check.
     * @param property the property name without the action prefix.
     *
     * @return the property value or null if missing.
     * @throws IllegalArgumentException when the event ot action is not found.
     */
    private String getNotificationActionProperty(String eventType, NotificationAction action, String property)
            throws IllegalArgumentException {
        String eventTypeNodeName = null;
        String actionTypeNodeName = null;

        List<String> eventTypes = configService.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true);

        for (String eventTypeRootPropName : eventTypes) {
            String eType = configService.getString(eventTypeRootPropName);
            if (eType.equals(eventType))
                eventTypeNodeName = eventTypeRootPropName;
        }

        // If we didn't find the given event type in the configuration there is not need to
        // further check
        if (eventTypeNodeName == null) {
            throw new IllegalArgumentException("Missing event type node");
        }

        // Go through contained actions.
        String actionPrefix = eventTypeNodeName + ".actions";
        List<String> actionTypes = configService.getPropertyNamesByPrefix(actionPrefix, true);

        for (String actionTypeRootPropName : actionTypes) {
            String aType = configService.getString(actionTypeRootPropName);
            if (aType.equals(action.getActionType()))
                actionTypeNodeName = actionTypeRootPropName;
        }

        // If we didn't find the given actionType in the configuration there is no need to further check
        if (actionTypeNodeName == null)
            throw new IllegalArgumentException("Missing action type node");

        return (String) configService.getProperty(actionTypeNodeName + "." + property);
    }

    /**
     * Returns an iterator over a list of all events registered in this notification service. Each
     * line in the returned list consists of a String, representing the name of the event (as
     * defined by the plugin that registered it).
     *
     * @return an iterator over a list of all events registered in this notifications service
     */
    public Iterable<String> getRegisteredEvents() {
        return Collections.unmodifiableSet(notifications.keySet());
    }

    /**
     * Finds the <code>EventNotification</code> corresponding to the given <code>eventType</code> and
     * returns its isActive status.
     *
     * @param eventType the name of the event (as defined by the plugin that's registered it) that we are
     * checking.
     *
     * @return {@code true} if actions for the specified <code>eventType</code> are activated,
     * {@code false} - otherwise. If the given <code>eventType</code> is not contained in the
     * list of registered event types - returns {@code false}.
     */
    public boolean isActive(String eventType) {
        Notification eventNotification = notifications.get(eventType);
        return (eventNotification != null) && eventNotification.isActive();
    }

    private boolean isDefault(String eventType, String actionType) {
        List<String> eventTypes = configService.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true);
        for (String eventTypeRootPropName : eventTypes) {
            String eType = configService.getString(eventTypeRootPropName);
            if (!eType.equals(eventType))
                continue;

            List<String> actions = configService.getPropertyNamesByPrefix(eventTypeRootPropName
                    + ".actions", true);
            for (String actionPropName : actions) {
                String aType = configService.getString(actionPropName);
                if (!aType.equals(actionType))
                    continue;

                // if setting is missing we accept it is true this way we override old saved settings
                Object isDefaultObj = configService.getProperty(actionPropName + ".default");
                return (isDefaultObj == null) || Boolean.parseBoolean((String) isDefaultObj);
            }
        }
        return true;
    }

    private boolean isEnabled(String configProperty) {
        // if setting is missing we accept it is true this way we not affect old saved settings
        Object isEnabledObj = configService.getProperty(configProperty);
        return isEnabledObj == null || Boolean.parseBoolean((String) isEnabledObj);
    }

    /**
     * Loads all previously saved event notifications.
     */
    private void loadNotifications() {
        List<String> eventTypes = configService.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true);
        for (String eventTypeRootPropName : eventTypes) {
            boolean isEventActive = isEnabled(eventTypeRootPropName + ".active");
            String eventType = configService.getString(eventTypeRootPropName);

            // cmeng: Patch to purge old eventType "missed_call" which has been replaced with MissedCall;
            // Will be removed in future aTalk releases > e.g. v2.1.0
            if ("missed_call".equals(eventType)) {
                configService.removeProperty(eventTypeRootPropName);
                continue;
            }

            List<String> actions
                    = configService.getPropertyNamesByPrefix(eventTypeRootPropName + ".actions", true);
            for (String actionPropName : actions) {
                String actionType = configService.getString(actionPropName);
                NotificationAction action = null;

                switch (actionType) {
                    case ACTION_SOUND:
                        String soundFileDescriptor = configService.getString(actionPropName + ".soundFileDescriptor");
                        // loopInterval must not be null
                        String loopInterval = configService.getString(actionPropName + ".loopInterval", "-1");
                        boolean isSoundNotificationEnabled = configService.getBoolean(
                                actionPropName + ".isSoundNotificationEnabled", (soundFileDescriptor != null));
                        boolean isSoundPlaybackEnabled = configService.getBoolean(
                                actionPropName + ".isSoundPlaybackEnabled", false);
                        boolean isSoundPCSpeakerEnabled = configService.getBoolean(
                                actionPropName + ".isSoundPCSpeakerEnabled", false);
                        action = new SoundNotificationAction(soundFileDescriptor, Integer.parseInt(loopInterval),
                                isSoundNotificationEnabled, isSoundPlaybackEnabled, isSoundPCSpeakerEnabled);
                        break;
                    case ACTION_POPUP_MESSAGE:
                        String defaultMessage = configService.getString(actionPropName + ".defaultMessage");
                        long timeout = configService.getLong(actionPropName + ".timeout", -1);
                        String groupName = configService.getString(actionPropName + ".groupName");
                        action = new PopupMessageNotificationAction(defaultMessage, timeout, groupName);
                        break;
                    case ACTION_LOG_MESSAGE:
                        String logType = configService.getString(actionPropName + ".logType");
                        action = new LogMessageNotificationAction(logType);
                        break;
                    case ACTION_COMMAND:
                        String commandDescriptor = configService.getString(actionPropName + ".commandDescriptor");
                        action = new CommandNotificationAction(commandDescriptor);
                        break;
                    case ACTION_VIBRATE:
                        String descriptor = configService.getString(actionPropName + ".descriptor");
                        int patternLen = configService.getInt(actionPropName + ".patternLength", -1);
                        if (patternLen == -1) {
                            Timber.e("Invalid pattern length: %s", patternLen);
                            continue;
                        }
                        long[] pattern = new long[patternLen];
                        for (int pIdx = 0; pIdx < patternLen; pIdx++) {
                            pattern[pIdx] = configService.getLong(actionPropName + ".patternItem" + pIdx, -1);
                            if (pattern[pIdx] == -1) {
                                Timber.e("Invalid pattern interval: %s", (Object) pattern);
                            }
                        }
                        int repeat = configService.getInt(actionPropName + ".repeat", -1);
                        action = new VibrateNotificationAction(descriptor, pattern, repeat);
                        break;
                }
                if (action == null)
                    continue;

                action.setEnabled(isEnabled(actionPropName + ".enabled"));

                // Load the data in the notifications table.
                Notification notification = notifications.get(eventType);
                if (notification == null) {
                    notification = new Notification(eventType);
                    notifications.put(eventType, notification);
                }
                notification.setActive(isEventActive);
                notification.addAction(action);
            }
        }
    }

    /**
     * Creates a new default <code>EventNotification</code> or obtains the corresponding existing one
     * and registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param action the <code>NotificationAction</code> to register
     */
    public void registerDefaultNotificationForEvent(String eventType, NotificationAction action) {
        if (isDefault(eventType, action.getActionType())) {
            NotificationAction h = getEventNotificationAction(eventType, action.getActionType());
            boolean isNew = false;
            if (h == null) {
                isNew = true;
                h = action;
            }

            this.saveNotification(eventType, action, h.isEnabled(), true);
            Notification notification;
            if (notifications.containsKey(eventType))
                notification = notifications.get(eventType);
            else {
                notification = new Notification(eventType);
                notifications.put(eventType, notification);
            }
            notification.addAction(action);

            // We fire the appropriate event depending on whether this is an already existing actionType or a new one.
            fireNotificationActionTypeEvent(isNew ? ACTION_ADDED : ACTION_CHANGED, eventType, action);
        }
        else
            checkDefaultAgainstLoadedNotification(eventType, action);

        // now store this default events if we want to restore them
        Notification notification;
        if (defaultNotifications.containsKey(eventType))
            notification = defaultNotifications.get(eventType);
        else {
            notification = new Notification(eventType);
            defaultNotifications.put(eventType, notification);
        }
        notification.addAction(action);
    }

    /**
     * Creates a new default <code>EventNotification</code> or obtains the corresponding existing one
     * and registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param actionType the type of the action that is to be executed when the specified event occurs (could
     * be one of the ACTION_XXX fields).
     * @param actionDescriptor a String containing a description of the action (a URI to the sound file for audio
     * notifications or a command line for exec action types) that should be executed when the action occurs.
     * @param defaultMessage the default message to use if no specific message has been provided when firing the
     * notification.
     */
    public void registerDefaultNotificationForEvent(String eventType, String actionType,
            String actionDescriptor, String defaultMessage) {
        Timber.log(TimberLog.FINER, "Registering default event Type: %s; Action: %s; Descriptor: %s; Message: %s",
                eventType, actionType, actionDescriptor, defaultMessage);

        if (isDefault(eventType, actionType)) {
            NotificationAction action = getEventNotificationAction(eventType, actionType);
            boolean isNew = false;

            if (action == null) {
                isNew = true;
                switch (actionType) {
                    case ACTION_SOUND:
                        action = new SoundNotificationAction(actionDescriptor, -1);
                        break;
                    case ACTION_LOG_MESSAGE:
                        action = new LogMessageNotificationAction(LogMessageNotificationAction.INFO_LOG_TYPE);
                        break;
                    case ACTION_POPUP_MESSAGE:
                        action = new PopupMessageNotificationAction(defaultMessage);
                        break;
                    case ACTION_COMMAND:
                        action = new CommandNotificationAction(actionDescriptor);
                        break;
                }
            }

            this.saveNotification(eventType, action, action.isEnabled(), true);
            Notification notification;
            if (notifications.containsKey(eventType))
                notification = notifications.get(eventType);
            else {
                notification = new Notification(eventType);
                notifications.put(eventType, notification);
            }
            notification.addAction(action);

            // We fire the appropriate event depending on whether this is an already existing
            // actionType or a new one.
            fireNotificationActionTypeEvent(isNew ? ACTION_ADDED : ACTION_CHANGED, eventType, action);
        }

        // now store this default events if we want to restore them
        Notification notification;
        if (defaultNotifications.containsKey(eventType))
            notification = defaultNotifications.get(eventType);
        else {
            notification = new Notification(eventType);
            defaultNotifications.put(eventType, notification);
        }

        NotificationAction action = null;
        switch (actionType) {
            case ACTION_SOUND:
                action = new SoundNotificationAction(actionDescriptor, -1);
                break;
            case ACTION_LOG_MESSAGE:
                action = new LogMessageNotificationAction(
                        LogMessageNotificationAction.INFO_LOG_TYPE);
                break;
            case ACTION_POPUP_MESSAGE:
                action = new PopupMessageNotificationAction(defaultMessage);
                break;
            case ACTION_COMMAND:
                action = new CommandNotificationAction(actionDescriptor);
                break;
        }
        notification.addAction(action);
    }

    /**
     * Creates a new <code>EventNotification</code> or obtains the corresponding existing one and
     * registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param action the <code>NotificationAction</code> responsible for handling the given <code>actionType</code>
     */
    public void registerNotificationForEvent(String eventType, NotificationAction action) {
        Notification notification;
        if (notifications.containsKey(eventType))
            notification = notifications.get(eventType);
        else {
            notification = new Notification(eventType);
            notifications.put(eventType, notification);
            this.fireNotificationEventTypeEvent(EVENT_TYPE_ADDED, eventType);
        }

        Object existingAction = notification.addAction(action);

        // We fire the appropriate event depending on whether this is an already existing actionType or a new one.
        if (existingAction != null) {
            fireNotificationActionTypeEvent(ACTION_CHANGED, eventType, action);
        }
        else {
            fireNotificationActionTypeEvent(ACTION_ADDED, eventType, action);
        }
        // Save the notification through the ConfigurationService.
        this.saveNotification(eventType, action, true, false);
    }

    /**
     * Creates a new <code>EventNotification</code> or obtains the corresponding existing one and
     * registers a new action in it.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) that we are
     * setting an action for.
     * @param actionType the type of the action that is to be executed when the specified event occurs (could
     * be one of the ACTION_XXX fields).
     * @param actionDescriptor a String containing a description of the action (a URI to the sound file for audio
     * notifications or a command line for exec action types) that should be executed when the action occurs.
     * @param defaultMessage the default message to use if no specific message has been provided when firing the
     * notification.
     */
    public void registerNotificationForEvent(String eventType, String actionType,
            String actionDescriptor, String defaultMessage) {
        Timber.d("Registering event Type: %s; Action: %s; Descriptor: %s; Message: %s",
                eventType, actionType, actionDescriptor, defaultMessage);

        switch (actionType) {
            case ACTION_SOUND:
                Notification notification = defaultNotifications.get(eventType);
                SoundNotificationAction action = (SoundNotificationAction) notification.getAction(ACTION_SOUND);
                registerNotificationForEvent(eventType, new SoundNotificationAction(
                        actionDescriptor, action.getLoopInterval()));
                break;
            case ACTION_LOG_MESSAGE:
                registerNotificationForEvent(eventType, new LogMessageNotificationAction(
                        LogMessageNotificationAction.INFO_LOG_TYPE));
                break;
            case ACTION_POPUP_MESSAGE:
                registerNotificationForEvent(eventType, new PopupMessageNotificationAction(defaultMessage));
                break;
            case ACTION_COMMAND:
                registerNotificationForEvent(eventType, new CommandNotificationAction(actionDescriptor));
                break;
        }
    }

    /**
     * Removes an object that executes the actual action of notification action.
     *
     * @param actionType The handler type to remove.
     */
    public void removeActionHandler(String actionType) {
        if (actionType == null)
            throw new IllegalArgumentException("actionType cannot be null");

        synchronized (handlers) {
            handlers.remove(actionType);
        }
    }

    /**
     * Removes the <code>EventNotification</code> corresponding to the given <code>eventType</code> from
     * the table of registered event notifications.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) to be removed.
     */
    public void removeEventNotification(String eventType) {
        notifications.remove(eventType);
        this.fireNotificationEventTypeEvent(EVENT_TYPE_REMOVED, eventType);
    }

    /**
     * Removes the given actionType from the list of actions registered for the given <code>eventType</code>.
     *
     * @param eventType the name of the event (as defined by the plugin that's registering it) for which we'll
     * remove the notification.
     * @param actionType the type of the action that is to be executed when the specified event occurs (could
     * be one of the ACTION_XXX fields).
     */
    public void removeEventNotificationAction(String eventType, String actionType) {
        Notification notification = notifications.get(eventType);
        if (notification == null)
            return;

        NotificationAction action = notification.getAction(actionType);
        if (action == null)
            return;

        notification.removeAction(actionType);
        action.setEnabled(false);
        saveNotification(eventType, action, false, false);
        fireNotificationActionTypeEvent(ACTION_REMOVED, eventType, action);
    }

    /**
     * Removes the given <code>listener</code> from the list of change listeners.
     *
     * @param listener the listener that we'd like to remove
     */
    public void removeNotificationChangeListener(NotificationChangeListener listener) {
        synchronized (changeListeners) {
            changeListeners.remove(listener);
        }
    }

    /**
     * Deletes all registered events and actions and registers and saves the default events as current.
     */
    public void restoreDefaults() {
        for (String eventType : new Vector<>(notifications.keySet())) {
            Notification notification = notifications.get(eventType);
            if (notification != null) {
                for (String actionType : new Vector<>(notification.getActions().keySet()))
                    removeEventNotificationAction(eventType, actionType);
            }
            removeEventNotification(eventType);
        }

        for (Map.Entry<String, Notification> entry : defaultNotifications.entrySet()) {
            String eventType = entry.getKey();
            Notification notification = entry.getValue();

            for (NotificationAction action : notification.getActions().values())
                registerNotificationForEvent(eventType, action);
        }
    }

    /**
     * Saves the event notification action given by these parameters through the <code>ConfigurationService</code>.
     * OR globally set the active state if action is null.
     *
     * @param eventType the name of the event
     * @param action the notification action of the event to be changed
     * @param isActive is the global notification event active state, valid only if action is null
     * @param isDefault is it a default one
     */
    private void saveNotification(String eventType, NotificationAction action, boolean isActive, boolean isDefault) {
        String eventTypeNodeName = null;
        String actionTypeNodeName = null;

        List<String> eventTypes = configService.getPropertyNamesByPrefix(NOTIFICATIONS_PREFIX, true);
        for (String eventTypeRootPropName : eventTypes) {
            String eType = configService.getString(eventTypeRootPropName);
            if (eType.equals(eventType)) {
                eventTypeNodeName = eventTypeRootPropName;
                break;
            }
        }

        // If we didn't find the given event type in the configuration we create new here.
        if (eventTypeNodeName == null) {
            eventTypeNodeName = NOTIFICATIONS_PREFIX + ".eventType" + System.currentTimeMillis();
            configService.setProperty(eventTypeNodeName, eventType);
        }

        // We set active/inactive for the whole event notification if action is null
        if (action == null) {
            configService.setProperty(eventTypeNodeName + ".active", Boolean.toString(isActive));
            return;
        }

        // Go through contained actions to find the specific action
        String actionPrefix = eventTypeNodeName + ".actions";
        List<String> actionTypes = configService.getPropertyNamesByPrefix(actionPrefix, true);
        for (String actionTypeRootPropName : actionTypes) {
            String aType = configService.getString(actionTypeRootPropName);
            if (aType.equals(action.getActionType())) {
                actionTypeNodeName = actionTypeRootPropName;
                break;
            }
        }

        // List of specific action properties to be updated in database
        Map<String, Object> configProperties = new HashMap<>();

        // If we didn't find the given actionType in the configuration we create new here.
        if (actionTypeNodeName == null) {
            actionTypeNodeName = actionPrefix + ".actionType" + System.currentTimeMillis();
            configProperties.put(actionTypeNodeName, action.getActionType());
        }

        if (action instanceof SoundNotificationAction) {
            SoundNotificationAction soundAction = (SoundNotificationAction) action;
            configProperties.put(actionTypeNodeName + ".soundFileDescriptor", soundAction.getDescriptor());
            configProperties.put(actionTypeNodeName + ".loopInterval", soundAction.getLoopInterval());
            configProperties.put(actionTypeNodeName + ".isSoundNotificationEnabled", soundAction.isSoundNotificationEnabled());
            configProperties.put(actionTypeNodeName + ".isSoundPlaybackEnabled", soundAction.isSoundPlaybackEnabled());
            configProperties.put(actionTypeNodeName + ".isSoundPCSpeakerEnabled", soundAction.isSoundPCSpeakerEnabled());
        }
        else if (action instanceof PopupMessageNotificationAction) {
            PopupMessageNotificationAction messageAction = (PopupMessageNotificationAction) action;
            configProperties.put(actionTypeNodeName + ".defaultMessage", messageAction.getDefaultMessage());
            configProperties.put(actionTypeNodeName + ".timeout", messageAction.getTimeout());
            configProperties.put(actionTypeNodeName + ".groupName", messageAction.getGroupName());
        }
        else if (action instanceof LogMessageNotificationAction) {
            LogMessageNotificationAction logMessageAction = (LogMessageNotificationAction) action;
            configProperties.put(actionTypeNodeName + ".logType", logMessageAction.getLogType());
        }
        else if (action instanceof CommandNotificationAction) {
            CommandNotificationAction commandAction = (CommandNotificationAction) action;
            configProperties.put(actionTypeNodeName + ".commandDescriptor", commandAction.getDescriptor());
        }
        else if (action instanceof VibrateNotificationAction) {
            VibrateNotificationAction vibrateAction = (VibrateNotificationAction) action;
            configProperties.put(actionTypeNodeName + ".descriptor", vibrateAction.getDescriptor());
            long[] pattern = vibrateAction.getPattern();
            configProperties.put(actionTypeNodeName + ".patternLength", pattern.length);

            for (int pIdx = 0; pIdx < pattern.length; pIdx++) {
                configProperties.put(actionTypeNodeName + ".patternItem" + pIdx, pattern[pIdx]);
            }
            configProperties.put(actionTypeNodeName + ".repeat", vibrateAction.getRepeat());
        }

        // cmeng: should update based on action.isEnabled() instead of active meant for the global event state
        configProperties.put(actionTypeNodeName + ".enabled", Boolean.toString(action.isEnabled()));
        configProperties.put(actionTypeNodeName + ".default", Boolean.toString(isDefault));
        configService.setProperties(configProperties);
    }

    /**
     * Finds the <code>EventNotification</code> corresponding to the given <code>eventType</code> and
     * marks it as activated/deactivated.
     *
     * @param eventType the name of the event, which actions should be activated /deactivated.
     * @param isActive indicates whether to activate or deactivate the actions related to the specified <code>eventType</code>.
     */
    public void setActive(String eventType, boolean isActive) {
        Notification eventNotification = notifications.get(eventType);
        if (eventNotification == null)
            return;

        eventNotification.setActive(isActive);
        saveNotification(eventType, null, isActive, false);
    }

    /**
     * Stops a notification if notification is continuous, like playing sounds in loops.
     * Do nothing if there are no such events currently processing.
     *
     * @param data the data that has been returned when firing the event..
     */
    public void stopNotification(NotificationData data) {
        Iterable<NotificationHandler> soundHandlers = getActionHandlers(NotificationAction.ACTION_SOUND);

        // There could be no sound action handler for this event type e.g. call ringtone
        if (soundHandlers != null) {
            for (NotificationHandler handler : soundHandlers) {
                if (handler instanceof SoundNotificationHandler)
                    ((SoundNotificationHandler) handler).stop(data);
            }
        }

        Iterable<NotificationHandler> vibrateHandlers = getActionHandlers(NotificationAction.ACTION_VIBRATE);
        if (vibrateHandlers != null) {
            for (NotificationHandler handler : vibrateHandlers) {
                ((VibrateNotificationHandler) handler).cancel();
            }
        }
    }

    /**
     * Tells if the given sound notification is currently played.
     *
     * @param data Additional data for the event.
     */
    public boolean isPlayingNotification(NotificationData data) {
        boolean isPlaying = false;
        Iterable<NotificationHandler> soundHandlers = getActionHandlers(NotificationAction.ACTION_SOUND);

        // There could be no sound action handler for this event type
        if (soundHandlers != null) {
            for (NotificationHandler handler : soundHandlers) {
                if (handler instanceof SoundNotificationHandler) {
                    isPlaying |= ((SoundNotificationHandler) handler).isPlaying(data);
                }
            }
        }
        return isPlaying;
    }
}
