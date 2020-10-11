/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.impl.androidnotification.AndroidNotifications;
import org.atalk.impl.osgi.OSGiServiceImpl;

import java.security.Security;

import androidx.core.app.NotificationCompat;

/**
 * Implements an Android {@link Service} which (automatically) starts and stops an OSGi framework (implementation).
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class OSGiService extends Service
{
    /**
     * The ID of aTalk notification icon
     */
    private static int GENERAL_NOTIFICATION_ID = R.string.APPLICATION_NAME;

    /**
     * Indicates that aTalk is running in foreground mode and its icon is being displayed on android notification tray.
     * If user disable show aTalk icon, then running_foreground = false
     */
    private static boolean running_foreground = false;

    /**
     * Indicates if the service has been started and general notification icon is available
     */
    private static boolean serviceStarted;

    /**
     * The very implementation of this Android <tt>Service</tt> which is split out of the class <tt>OSGiService</tt> so
     * that the class <tt>OSGiService</tt> may remain in a <tt>service</tt> package and be treated as public from the
     * Android point of view and the class <tt>OSGiServiceImpl</tt> may reside in an <tt>impl</tt> package and be
     * recognized as internal from the aTalk point of view.
     */
    private final OSGiServiceImpl impl;

    /**
     * Initializes a new <tt>OSGiService</tt> implementation.
     */
    public OSGiService()
    {
        impl = new OSGiServiceImpl(this);
    }

    public IBinder onBind(Intent intent)
    {
        return impl.onBind(intent);
    }

    /**
     * Protects against starting next OSGi service while the previous one has not completed it's shutdown procedure.
     *
     * This field will be cleared by System.exit() called after shutdown completes.
     */
    private static boolean started;

    public static boolean hasStarted()
    {
        return started;
    }

    /**
     * This field will be cleared by System.exit() called after shutdown completes.
     */
    private static boolean shuttingdown;

    public static boolean isShuttingDown()
    {
        return shuttingdown;
    }

    @Override
    public void onCreate()
    {
        if (started) {
            // We are still running
            return;
        }
        started = true;
        impl.onCreate();
    }

    @Override
    public void onDestroy()
    {
        if (shuttingdown) {
            return;
        }
        shuttingdown = true;
        impl.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return impl.onStartCommand(intent, flags, startId);
    }

    /**
     * Method called by OSGi impl when start command completes.
     */
    public void onOSGiStarted()
    {
        if (aTalkApp.isIconEnabled()) {
            showIcon();
        }

        aTalkApp.getConfig().addPropertyChangeListener(aTalkApp.SHOW_ICON_PROPERTY_NAME, event -> {
            if (aTalkApp.isIconEnabled()) {
                showIcon();
            }
            else {
                hideIcon();
            }
        });
        serviceStarted = true;
    }

    /**
     * Start the service in foreground and creates shows general notification icon.
     */
    private void showIcon()
    {
        String title = getResources().getString(R.string.APPLICATION_NAME);
        // The intent to launch when the user clicks the expanded notification
        PendingIntent pendIntent = aTalkApp.getaTalkIconIntent();

        NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(this, AndroidNotifications.DEFAULT_GROUP);
        nBuilder.setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification)
                .setNumber(0)
                .setContentIntent(pendIntent);

        Notification notice = nBuilder.build();
        notice.flags |= Notification.FLAG_NO_CLEAR;

        this.startForeground(GENERAL_NOTIFICATION_ID, notice);
        running_foreground = true;
    }

    /**
     * Stops the foreground service and hides general notification icon
     */
    public void stopForegroundService()
    {
        serviceStarted = false;
        hideIcon();
    }

    private void hideIcon()
    {
        if (running_foreground) {
            stopForeground(true);
            running_foreground = false;
            AndroidUtils.generalNotificationInvalidated();
        }
    }

    /**
     * Returns general notification ID that can be used to post notification bound to our global icon
     * in android notification tray
     *
     * @return the notification ID greater than 0 or -1 if service is not running
     */
    public static int getGeneralNotificationId()
    {
        if (serviceStarted && running_foreground) {
            return GENERAL_NOTIFICATION_ID;
        }
        return -1;
    }

    static {
        Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);
    }
}
