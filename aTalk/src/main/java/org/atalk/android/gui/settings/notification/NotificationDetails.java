/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.notification;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;

import net.java.sip.communicator.plugin.notificationwiring.SoundProperties;
import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.NotificationChangeListener;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.SoundNotificationAction;
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent;
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.actionbar.ActionBarToggleFragment;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.impl.androidresources.AndroidResourceServiceImpl;
import org.atalk.service.resources.ResourceManagementService;

/**
 * The screen that displays notification event details. It allows user to enable/disable the whole
 * event as well as adjust particular notification handlers like popups, sound or vibration.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class NotificationDetails extends BaseActivity
        implements NotificationChangeListener, ActionBarToggleFragment.ActionBarToggleModel {
    /**
     * Event type extra key
     */
    private final static String EVENT_TYPE_EXTRA = "event_type";

    /**
     * The event type string that identifies the event
     */
    private String eventType;

    /**
     * Notification service instance
     */
    private NotificationService notificationService;

    /**
     * Resource service instance
     */
    private ResourceManagementService rms;

    /**
     * The description <code>View</code>
     */
    private TextView description;

    /**
     * Popup handler checkbox <code>View</code>
     */
    private CompoundButton popup;

    /**
     * Sound notification handler checkbox <code>View</code>
     */
    private CompoundButton soundNotification;

    /**
     * Sound playback handler checkbox <code>View</code>
     */
    private CompoundButton soundPlayback;

    /**
     * Vibrate handler checkbox <code>View</code>
     */
    private CompoundButton vibrate;

    // Sound Descriptor variable
    private Button mSoundDescriptor;

    private String eventTitle;
    private String ringToneTitle;

    private Uri soundDefaultUri;
    private Uri soundDescriptorUri;
    Ringtone ringTone = null;
    SoundNotificationAction soundHandler;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.eventType = getIntent().getStringExtra(EVENT_TYPE_EXTRA);
        if (eventType == null)
            throw new IllegalArgumentException();

        this.notificationService = ServiceUtils.getService(AppGUIActivator.bundleContext, NotificationService.class);
        this.rms = ServiceUtils.getService(AppGUIActivator.bundleContext, ResourceManagementService.class);

        setContentView(R.layout.notification_details);
        this.description = findViewById(R.id.description);
        this.popup = findViewById(R.id.popup);
        this.soundNotification = findViewById(R.id.soundNotification);
        this.soundPlayback = findViewById(R.id.soundPlayback);
        this.vibrate = findViewById(R.id.vibrate);

        ActivityResultLauncher<Integer> mPickRingTone = pickRingTone();
        mSoundDescriptor = findViewById(R.id.sound_descriptor);
        mSoundDescriptor.setOnClickListener(view -> {
            // set RingTone picker to show only the relevant notification or ringtone
            if (soundHandler.getLoopInterval() < 0)
                mPickRingTone.launch(RingtoneManager.TYPE_NOTIFICATION);
            else
                mPickRingTone.launch(RingtoneManager.TYPE_RINGTONE);
        });

        // ActionBarUtil.setTitle(this, aTalkApp.getStringResourceByName(NotificationSettings.N_PREFIX + eventType));
        eventTitle = rms.getI18NString(NotificationSettings.NOTICE_PREFIX + eventType);
        ActionBarUtil.setTitle(this, eventTitle);

        // The SoundNotification init
        initSoundNotification();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(ActionBarToggleFragment.newInstance(""),
                    "action_bar_toggle").commit();
        }
    }

    /**
     * Initialize all the sound Notification parameters on entry
     */
    private void initSoundNotification() {
        soundHandler = (SoundNotificationAction)
                notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_SOUND);

        if (soundHandler != null) {
            String soundFile = "android.resource://" + getPackageName() + "/" + SoundProperties.getSoundDescriptor(eventType);
            soundDefaultUri = Uri.parse(soundFile);

            String descriptor = soundHandler.getDescriptor();
            if (descriptor.startsWith(AndroidResourceServiceImpl.PROTOCOL)) {
                soundDescriptorUri = soundDefaultUri;
                ringToneTitle = eventTitle;
            }
            else {
                soundDescriptorUri = Uri.parse(descriptor);
                ringToneTitle = RingtoneManager.getRingtone(this, soundDescriptorUri).getTitle(this);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        updateDisplay();
        notificationService.addNotificationChangeListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        super.onPause();
        notificationService.removeNotificationChangeListener(this);
        if (ringTone != null) {
            ringTone.stop();
            ringTone = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    private void updateDisplay() {
        boolean enable = notificationService.isActive(eventType);

        // Description
        // description.setText(aTalkApp.getStringResourceByName(NotificationSettings.N_PREFIX + eventType + "_description"));
        description.setText(rms.getI18NString(NotificationSettings.NOTICE_PREFIX + eventType + ".description"));
        description.setEnabled(enable);

        // The popup
        NotificationAction popupHandler
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE);
        popup.setEnabled(enable && popupHandler != null);
        if (popupHandler != null)
            popup.setChecked(popupHandler.isEnabled());

        soundNotification.setEnabled(enable && soundHandler != null);
        soundPlayback.setEnabled(enable && soundHandler != null);

        // if soundHandler is null then hide the sound file selection else init its attributes
        if (soundHandler != null) {
            soundNotification.setChecked(soundHandler.isSoundNotificationEnabled());
            soundPlayback.setChecked(soundHandler.isSoundPlaybackEnabled());
            mSoundDescriptor.setText(ringToneTitle);
        }
        else {
            findViewById(R.id.soundAttributes).setVisibility(View.GONE);
        }

        // Vibrate action
        NotificationAction vibrateHandler
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE);
        vibrate.setEnabled(enable && vibrateHandler != null);
        if (vibrateHandler != null)
            vibrate.setChecked(vibrateHandler.isEnabled());
    }

    /**
     * Fired when popup checkbox is clicked.
     *
     * @param v popup checkbox <code>View</code>
     */
    public void onPopupClicked(View v) {
        boolean enabled = ((CompoundButton) v).isChecked();

        NotificationAction action
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE);
        action.setEnabled(enabled);
        notificationService.registerNotificationForEvent(eventType, action);
    }

    /**
     * Fired when sound notification checkbox is clicked.
     *
     * @param v sound notification checkbox <code>View</code>
     */
    public void onSoundNotificationClicked(View v) {
        boolean enabled = ((CompoundButton) v).isChecked();
        soundHandler.setSoundNotificationEnabled(enabled);
        notificationService.registerNotificationForEvent(eventType, soundHandler);
    }

    /**
     * Fired when sound playback checkbox is clicked.
     *
     * @param v sound playback checkbox <code>View</code>
     */
    public void onSoundPlaybackClicked(View v) {
        boolean enabled = ((CompoundButton) v).isChecked();
        soundHandler.setSoundPlaybackEnabled(enabled);
        notificationService.registerNotificationForEvent(eventType, soundHandler);
    }

    /**
     * Fired when vibrate notification checkbox is clicked.
     *
     * @param v vibrate notification checkbox <code>View</code>
     */
    public void onVibrateClicked(View v) {
        boolean enabled = ((CompoundButton) v).isChecked();

        NotificationAction action
                = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_VIBRATE);
        action.setEnabled(enabled);
        notificationService.registerNotificationForEvent(eventType, action);
    }

    /**
     * Toggle play mode for the ringtone when user clicks the play/pause button;
     *
     * @param v playback view
     */
    public void onPlayBackClicked(View v) {
        if (ringTone == null) {
            ringTone = RingtoneManager.getRingtone(this, soundDescriptorUri);
        }

        if (ringTone.isPlaying()) {
            ringTone.stop();
            ringTone = null;
        }
        else
            ringTone.play();
    }

    /**
     * PIckRingtone class ActivityResultContract implementation, with ringtoneType of either:
     * 1. RingtoneManager.TYPE_NOTIFICATION
     * 2. RingtoneManager.TYPE_RINGTONE
     */
    public class PickRingtone extends ActivityResultContract<Integer, Uri> {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @NonNull Integer ringtoneType) {
            final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, eventTitle);

            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, soundDefaultUri);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundDescriptorUri);
            return intent;
        }

        @Override
        public Uri parseResult(int resultCode, @Nullable Intent result) {
            if (resultCode != Activity.RESULT_OK || result == null) {
                return null;
            }
            return IntentCompat.getParcelableExtra(result, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
        }
    }

    /**
     * Opens a FileChooserDialog to let the user pick attachments
     */
    private ActivityResultLauncher<Integer> pickRingTone() {
        return registerForActivityResult(new PickRingtone(), ringToneUri -> {
            if (ringToneUri == null) {
                ringToneUri = soundDefaultUri;
            }
            updateSoundNotification(ringToneUri);
        });
    }

    /**
     * Update the display and setup the SoundNotification with the newly user selected ringTone
     *
     * @param ringToneUri user selected ringtone Uri
     */
    private void updateSoundNotification(Uri ringToneUri) {
        String soundDescriptor;
        if (soundDefaultUri.equals(ringToneUri)) {
            ringToneTitle = eventTitle;
            soundDescriptor = SoundProperties.getSoundDescriptor(eventType);
        }
        else {
            ringTone = RingtoneManager.getRingtone(this, ringToneUri);
            ringToneTitle = ringTone.getTitle(this);
            soundDescriptor = ringToneUri.toString();
        }
        soundDescriptorUri = ringToneUri;

        soundHandler.setDescriptor(soundDescriptor);
        notificationService.registerNotificationForEvent(eventType, soundHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionAdded(NotificationActionTypeEvent event) {
        handleActionEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionRemoved(NotificationActionTypeEvent event) {
        handleActionEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void actionChanged(NotificationActionTypeEvent event) {
        handleActionEvent(event);
    }

    /**
     * Handles add/changed/removed notification action events by refreshing the display if the event
     * is related with the one currently displayed.
     *
     * @param event the event object
     */
    private void handleActionEvent(NotificationActionTypeEvent event) {
        if (event.getEventType().equals(eventType)) {
            runOnUiThread(this::updateDisplay);
        }
    }

    /**
     * {@inheritDoc} Not interested in type added event.
     */
    @Override
    public void eventTypeAdded(NotificationEventTypeEvent event) {
    }

    /**
     * {@inheritDoc}
     * <p>
     * If removed event is the one currently displayed, closes the <code>Activity</code>.
     */
    @Override
    public void eventTypeRemoved(NotificationEventTypeEvent event) {
        if (!event.getEventType().equals(eventType))
            return;

        // Event no longer exists
        runOnUiThread(this::finish);
    }

    /**
     * Gets the <code>Intent</code> for starting <code>NotificationDetails</code> <code>Activity</code>.
     *
     * @param ctx the context
     * @param eventType name of the event that will be displayed by <code>NotificationDetails</code>.
     *
     * @return the <code>Intent</code> for starting <code>NotificationDetails</code> <code>Activity</code>.
     */
    public static Intent getIntent(Context ctx, String eventType) {
        Intent intent = new Intent(ctx, NotificationDetails.class);
        intent.putExtra(EVENT_TYPE_EXTRA, eventType);
        return intent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isChecked() {
        return notificationService.isActive(eventType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChecked(boolean isChecked) {
        notificationService.setActive(eventType, isChecked);
        updateDisplay();
    }
}
