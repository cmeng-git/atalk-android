/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.security.Security;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.appnotification.AppNotifications;
import org.atalk.impl.osgi.OSGiServiceImpl;

/**
 * Implements an Android {@link Service} which (automatically) starts and stops an OSGi framework (implementation).
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class OSGiService extends Service {
    /**
     * The ID of aTalk notification icon
     */
    private static final int GENERAL_NOTIFICATION_ID = R.string.application_name;

    private NotificationManager mNotificationManager;

    /**
     * Indicates that aTalk icon is being displayed on android notification tray.
     */
    private static boolean appIcon_shown = false;

    /**
     * Indicates if the service has been started and general notification icon is available
     */
    private static boolean serviceStarted;

    /**
     * This field will be cleared by System.exit() called after shutdown completes.
     */
    private static boolean isShuttingDown;

    /**
     * The very implementation of this Android <code>Service</code> which is split out of the class <code>OSGiService</code> so
     * that the class <code>OSGiService</code> may remain in a <code>service</code> package and be treated as public from the
     * Android point of view and the class <code>OSGiServiceImpl</code> may reside in an <code>impl</code> package and be
     * recognized as internal from the aTalk point of view.
     */
    private final OSGiServiceImpl impl;

    /**
     * Initializes a new <code>OSGiService</code> implementation.
     */
    public OSGiService() {
        impl = new OSGiServiceImpl(this);
    }

    public IBinder onBind(Intent intent) {
        return impl.onBind(intent);
    }

    /**
     * Protects against starting next OSGi service while the previous one has not completed it's shutdown procedure.
     * This field will be cleared by System.exit() called after shutdown completes.
     */
    private static boolean started;

    public static boolean hasStarted() {
        return started;
    }

    public static boolean isShuttingDown() {
        return isShuttingDown;
    }

    @Override
    public void onCreate() {
        // We are still running
        if (started) {
            return;
        }
        mNotificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        started = true;
        impl.onCreate();
    }

    @Override
    public void onDestroy() {
        if (isShuttingDown) {
            return;
        }
        isShuttingDown = true;
        impl.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return impl.onStartCommand(intent, flags, startId);
    }

    /**
     * Method called by OSGi impl when start command completes.
     */
    public void onOSGiStarted() {
        serviceStarted = true;

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
    }

    /**
     * Start the service in foreground and creates shows general notification icon.
     */
    private void showIcon() {
        String title = getResources().getString(R.string.application_name);
        // The intent to launch when the user clicks the expanded notification
        PendingIntent pendIntent = aTalkApp.getaTalkIconIntent();

        NotificationCompat.Builder nBuilder = new NotificationCompat.Builder(this, AppNotifications.DEFAULT_GROUP);
        nBuilder.setContentTitle(title)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification)
                .setNumber(0)
                .setContentIntent(pendIntent);

        Notification notice = nBuilder.build();
        notice.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

        mNotificationManager.notify(GENERAL_NOTIFICATION_ID, notice);
        appIcon_shown = true;
    }

    /**
     * Stops the foreground service and hides general notification icon
     */
    public void stopForegroundService() {
        hideIcon();
        serviceStarted = false;
    }

    private void hideIcon() {
        mNotificationManager.cancel(GENERAL_NOTIFICATION_ID);
        appIcon_shown = false;
    }

    /**
     * Returns general notification ID that can be used to post notification bound to our global icon
     * in android notification tray
     *
     * @return the notification ID greater than 0 or -1 if service is not running
     */
    public static int getGeneralNotificationId() {
        if (serviceStarted && appIcon_shown) {
            return GENERAL_NOTIFICATION_ID;
        }
        return -1;
    }

    static {
        Security.insertProviderAt(new org.bouncycastle.jce.provider.BouncyCastleProvider(), 1);
    }
}
