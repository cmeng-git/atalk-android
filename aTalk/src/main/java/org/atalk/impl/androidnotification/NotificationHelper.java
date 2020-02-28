/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.impl.androidnotification;

import android.annotation.TargetApi;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;

import org.atalk.android.R;

import java.util.List;

import androidx.annotation.RequiresApi;

/**
 * Helper class to manage notification channels, and create notifications.
 *
 * @author Eng Chong Meng
 */
public class NotificationHelper extends ContextWrapper
{
    private NotificationManager notificationManager = null;

    private static final int LED_COLOR = 0xff00ff00;

    /**
     * Registers notification channels, which can be used later by individual notifications.
     *
     * @param ctx The application context
     */
    public NotificationHelper(Context ctx)
    {
        super(ctx);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Init the system service NotificationManager
            notificationManager = ctx.getSystemService(NotificationManager.class);

            // Delete any unused channel IDs
            deleteObsoletedChannelIds();

            final NotificationChannel nMessage = new NotificationChannel(AndroidNotifications.MESSAGE_GROUP,
                    getString(R.string.noti_channel_MESSAGE_GROUP), NotificationManager.IMPORTANCE_HIGH);
            nMessage.setShowBadge(true);
            nMessage.setLightColor(LED_COLOR);
            // nMessage.setAllowBubbles(true);
            nMessage.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(nMessage);

            final NotificationChannel nFile = new NotificationChannel(AndroidNotifications.FILE_GROUP,
                    getString(R.string.noti_channel_FILE_GROUP), NotificationManager.IMPORTANCE_LOW);
            nFile.setShowBadge(false);
            // nFile.setLightColor(Color.GREEN);
            nFile.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(nFile);

            final NotificationChannel nCall = new NotificationChannel(AndroidNotifications.CALL_GROUP,
                    getString(R.string.noti_channel_CALL_GROUP), NotificationManager.IMPORTANCE_LOW);
            nCall.setShowBadge(false);
            // nCall.setLightColor(Color.CYAN);
            nCall.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(nCall);

            final NotificationChannel nMissCall = new NotificationChannel(AndroidNotifications.MISSED_CALL,
                    getString(R.string.noti_channel_MISSED_CALL), NotificationManager.IMPORTANCE_LOW);
            nMissCall.setShowBadge(false);
            nMissCall.setLightColor(Color.RED);
            nMissCall.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(nMissCall);

            final NotificationChannel nDefault = new NotificationChannel(AndroidNotifications.DEFAULT_GROUP,
                    getString(R.string.noti_channel_DEFAULT_GROUP), NotificationManager.IMPORTANCE_LOW);
            nDefault.setShowBadge(false);
            // nDefault.setLightColor(Color.WHITE);
            nDefault.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(nDefault);

            final NotificationChannel nQuietHours = new NotificationChannel(AndroidNotifications.SILENT_GROUP,
                    getString(R.string.noti_channel_SILENT_GROUP), NotificationManager.IMPORTANCE_LOW);
            nQuietHours.setShowBadge(true);
            nQuietHours.setLightColor(LED_COLOR);
            nQuietHours.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(nQuietHours);
        }
    }

    /*
     * Send a notification.
     *
     * @param id The ID of the notification
     * @param notification The notification object
     */
    public void notify(int id, Notification.Builder notification)
    {
        notificationManager.notify(id, notification.build());
    }

    /**
     * Send Intent to load system Notification Settings for this app.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void goToNotificationSettings()
    {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    /**
     * Send intent to load system Notification Settings UI for a particular channel.
     *
     * @param channel Name of channel to configure
     */
    @TargetApi(Build.VERSION_CODES.O)
    public void goToNotificationSettings(String channel)
    {
        Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        i.putExtra(Settings.EXTRA_CHANNEL_ID, channel);
        startActivity(i);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void deleteObsoletedChannelIds()
    {
        List<NotificationChannel> channelGroups = notificationManager.getNotificationChannels();
        for (NotificationChannel nc : channelGroups) {
            if (!AndroidNotifications.notificationIds.contains(nc.getId())) {
                notificationManager.deleteNotificationChannel(nc.getId());
            }
        }
    }
}
