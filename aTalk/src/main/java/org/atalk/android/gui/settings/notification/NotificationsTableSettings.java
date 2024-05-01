/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.notification;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TableLayout;
import android.widget.TableRow;

import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.NotificationChangeListener;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.PopupMessageNotificationAction;
import net.java.sip.communicator.service.notification.SoundNotificationAction;
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent;
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.resources.ResourceManagementService;

/**
 * Activity displays table of all notification events allowing user to change their settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class NotificationsTableSettings extends OSGiActivity implements NotificationChangeListener {
    /**
     * Enable button's tag
     */
    private final static String ENABLE_TAG = "enable";

    /**
     * Popup checkbox tag
     */
    private final static String POPUP_TAG = "popup";

    /**
     * Notification sound checkbox tag
     */
    private final static String SOUND_NOTIFY_TAG = "soundNotification";

    /**
     * Playback sound checkbox tag
     */
    private final static String SOUND_PLAYBACK_TAG = "soundPlayback";

    /**
     * Vibrate checkbox tag
     */
    private final static String VIBRATE_TAG = "vibrate";

    /**
     * Description label tag
     */
    private final static String DESCRIPTION_TAG = "description";

    /**
     * The notification service
     */
    private NotificationService notificationService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.notificationService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, NotificationService.class);
        notificationService.addNotificationChangeListener(this);

        buildTable();
    }

    /**
     * Builds the table of currently existing notification events.
     */
    private void buildTable() {
        setContentView(R.layout.notifications_settings);

        TableLayout table = findViewById(R.id.table_body);

        LayoutInflater inflater = getLayoutInflater();

        ResourceManagementService rms = UtilActivator.getResources();

        for (String eventType : notificationService.getRegisteredEvents()) {
            View tableRow = inflater.inflate(R.layout.notification_row, table, false);

            ViewUtil.setCompoundChecked(tableRow, ENABLE_TAG, notificationService.isActive(eventType));

            // Popup
            NotificationAction popupHandler = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE);

            if (popupHandler != null)
                ViewUtil.setCompoundChecked(tableRow, POPUP_TAG, popupHandler.isEnabled());
            // Sound
            SoundNotificationAction soundHandler = (SoundNotificationAction) notificationService.getEventNotificationAction(eventType,
                    NotificationAction.ACTION_SOUND);

            if (soundHandler != null)
                ViewUtil.setCompoundChecked(tableRow, SOUND_NOTIFY_TAG, soundHandler.isSoundNotificationEnabled());

            if (soundHandler != null)
                ViewUtil.setCompoundChecked(tableRow, SOUND_PLAYBACK_TAG, soundHandler.isSoundPlaybackEnabled());
            // Vibrate
            NotificationAction vibrateHandler
                    = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE);

            if (vibrateHandler != null)
                ViewUtil.setCompoundChecked(tableRow, VIBRATE_TAG, vibrateHandler.isEnabled());

            // Description
            String desc = rms.getI18NString(NotificationSettings.NOTICE_PREFIX + eventType);

            ViewUtil.setTextViewValue(tableRow, DESCRIPTION_TAG, desc);

            // Event name as tag for the row
            tableRow.setTag(eventType);

            // Enable particular checkboxes
            ensureRowEnabled((CompoundButton) tableRow.findViewWithTag(ENABLE_TAG));

            // Add created row
            table.addView(tableRow);
        }
    }

    /**
     * Sets particular checkboxes enabled stated based on whole event enabled state and it's sub actions.
     *
     * @param enableColumnButton the button that enables whole event.
     */
    private void ensureRowEnabled(CompoundButton enableColumnButton) {
        boolean enable = enableColumnButton.isChecked();

        TableRow row = (TableRow) enableColumnButton.getParent();

        String eventType = (String) row.getTag();

        // The popup
        NotificationAction popupHandler
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE);

        ViewUtil.ensureEnabled(row, POPUP_TAG, enable && popupHandler != null);

        // The sound
        NotificationAction soundHandler
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND);

        ViewUtil.ensureEnabled(row, SOUND_NOTIFY_TAG, enable && soundHandler != null);
        ViewUtil.ensureEnabled(row, SOUND_PLAYBACK_TAG, enable && soundHandler != null);

        // Vibrate action
        NotificationAction vibrateHandler
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE);
        ViewUtil.ensureEnabled(row, VIBRATE_TAG, enable && vibrateHandler != null);

        // Description label
        ViewUtil.ensureEnabled(row, DESCRIPTION_TAG, enable);
    }

    /**
     * Fired when enable event toggle button is clicked
     *
     * @param v toggle button <code>View</code>
     */
    public void onEnableItemClicked(View v) {
        View parent = (View) v.getParent();
        CompoundButton cb = (CompoundButton) v;

        String eventType = (String) parent.getTag();
        notificationService.setActive(eventType, cb.isChecked());
        ensureRowEnabled(cb);
    }

    /**
     * Fired when popup checkbox is clicked
     *
     * @param v the popup checkbox
     */
    public void onPopupItemClicked(View v) {
        View parent = (View) v.getParent();
        CompoundButton cb = (CompoundButton) v;

        boolean isPopup = cb.isChecked();
        String eventType = (String) parent.getTag();

        PopupMessageNotificationAction popupAction = (PopupMessageNotificationAction)
                notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE);

        popupAction.setEnabled(isPopup);
        notificationService.registerNotificationForEvent(eventType, popupAction);

        /*
         * if(isPopup) { notificationService.registerNotificationForEvent( eventType,
         * NotificationAction.ACTION_POPUP_MESSAGE, "", ""); } else {
         */
        // notificationService.removeEventNotificationAction(
        // eventType, NotificationAction.ACTION_POPUP_MESSAGE);
        // }
    }

    /**
     * Fired when sound notification checkbox is clicked
     *
     * @param v the sound notification checkbox
     */
    public void onSoundNotificationItemClicked(View v) {
        View parent = (View) v.getParent();
        CompoundButton cb = (CompoundButton) v;

        boolean isSoundNotification = cb.isChecked();
        String eventType = (String) parent.getTag();

        SoundNotificationAction soundNotificationAction = (SoundNotificationAction)
                notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND);

        soundNotificationAction.setSoundNotificationEnabled(isSoundNotification);
        notificationService.registerNotificationForEvent(eventType, soundNotificationAction);
    }

    /**
     * Fired when sound playback checkbox is clicked
     *
     * @param v sound playback checkbox
     */
    public void onSoundPlaybackItemClicked(View v) {
        View parent = (View) v.getParent();
        CompoundButton cb = (CompoundButton) v;

        boolean isSoundPlayback = cb.isChecked();
        String eventType = (String) parent.getTag();

        SoundNotificationAction soundPlaybackAction = (SoundNotificationAction)
                notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND);

        soundPlaybackAction.setSoundPlaybackEnabled(isSoundPlayback);
        notificationService.registerNotificationForEvent(eventType, soundPlaybackAction);
    }

    /**
     * Fired when vibrate checkbox is clicked
     *
     * @param v the vibrate checkbox
     */
    public void onVibrateItemClicked(View v) {
        View parent = (View) v.getParent();
        CompoundButton cb = (CompoundButton) v;

        boolean isVibrate = cb.isChecked();
        String eventType = (String) parent.getTag();

        NotificationAction vibrateAction
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE);

        vibrateAction.setEnabled(isVibrate);
        notificationService.registerNotificationForEvent(eventType, vibrateAction);
    }

    /**
     * Rebuilds the whole table on UI thread
     */
    private void rebuildTable() {
        runOnUiThread(this::buildTable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionAdded(NotificationActionTypeEvent event) {
        // It should not happen that often and will be much easier to rebuild the whole table from scratch
        rebuildTable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionRemoved(NotificationActionTypeEvent event) {
        rebuildTable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionChanged(NotificationActionTypeEvent event) {
        // rebuildTable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventTypeAdded(NotificationEventTypeEvent event) {
        rebuildTable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventTypeRemoved(NotificationEventTypeEvent event) {
        rebuildTable();
    }
}
