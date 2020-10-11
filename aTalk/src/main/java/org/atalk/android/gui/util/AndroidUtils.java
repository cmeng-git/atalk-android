/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.app.*;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.androidnotification.AndroidNotifications;
import org.atalk.service.osgi.OSGiService;

import java.util.List;

import androidx.core.app.NotificationCompat;
import timber.log.Timber;

/**
 * The <tt>AndroidUtils</tt> class provides a set of utility methods allowing an easy way to show
 * an alert dialog on android, show a general notification, etc.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidUtils
{
    /**
     * Api level constant. Change it here to simulate lower api on new devices.
     *
     * All API level decisions should be done based on {@link #hasAPI(int)} call result.
     */
    private static final int API_LEVEL = Build.VERSION.SDK_INT;

    /**
     * Var used to track last aTalk icon notification text in order to prevent from posting
     * updates that make no sense. This will happen when providers registration state changes
     * and global status is still the same(online or offline).
     */
    private static String lastNotificationText = null;

    /**
     * Shows an alert dialog for the given context and a title given by <tt>titleId</tt> and
     * message given by <tt>messageId</tt>.
     *
     * @param context the android <tt>Context</tt>
     * @param titleId the title identifier in the resources
     * @param messageId the message identifier in the resources
     */
    public static void showAlertDialog(Context context, final int titleId, final int messageId)
    {
        String title = context.getResources().getString(titleId);
        String msg = context.getResources().getString(messageId);
        showAlertDialog(context, title, msg);
    }

    public static void showAlertDialog(Context context, final int titleId, final int messageId, final Object... arg)
    {
        String title = context.getResources().getString(titleId);
        String msg = context.getResources().getString(messageId, arg);
        showAlertDialog(context, title, msg);
    }

    /**
     * Shows an alert dialog for the given context and a title given by <tt>titleId</tt> and
     * message given by <tt>messageId</tt>.
     *
     * @param context the android <tt>Context</tt>
     * @param title the title identifier in the resources
     * @param message the message identifier in the resources
     * @param button the confirm button string identifier
     * @param listener the <tt>DialogInterface.DialogListener</tt> to attach to the confirm button
     */
    public static void showAlertConfirmDialog(Context context, final String title,
            final String message, final String button, final DialogActivity.DialogListener listener)
    {
        DialogActivity.showConfirmDialog(context, title, message, button, listener);
    }

    /**
     * Shows an alert dialog for the given context and a title given by <tt>titleId</tt> and
     * message given by <tt>messageId</tt>.
     *
     * @param context the android <tt>Context</tt>
     * @param title the title of the message
     * @param message the message
     */
    public static void showAlertDialog(final Context context, final String title, final String message)
    {
        DialogActivity.showDialog(context, title, message);
    }

    /**
     * Clears the general notification.
     *
     * @param appContext the <tt>Context</tt> that will be used to create new activity from notification
     * <tt>Intent</tt>.
     */
    public static void clearGeneralNotification(Context appContext)
    {
        int id = OSGiService.getGeneralNotificationId();
        if (id < 0) {
            Timber.log(TimberLog.FINER, "There's no global notification icon found");
            return;
        }

        AndroidUtils.generalNotificationInvalidated();
        AndroidGUIActivator.getLoginRenderer().updateaTalkIconNotification();
    }

    /**
     * Shows an alert dialog for the given context and a title given by <tt>titleId</tt> and
     * message given by <tt>messageId</tt>.
     *
     * @param context the android <tt>Context</tt>
     * @param notificationID the identifier of the notification to update
     * @param title the title of the message
     * @param message the message
     * @param date the date on which the event corresponding to the notification happened
     */
    public static void updateGeneralNotification(Context context, int notificationID, String title,
            String message, long date)
    {
        // Filter out the same subsequent notifications
        if (lastNotificationText != null && lastNotificationText.equals(message)) {
            return;
        }

        NotificationCompat.Builder nBuilder;
        nBuilder = new NotificationCompat.Builder(context, AndroidNotifications.DEFAULT_GROUP);

        nBuilder.setContentTitle(title)
                .setContentText(message)
                .setWhen(date)
                .setSmallIcon(R.drawable.ic_notification);

        nBuilder.setContentIntent(aTalkApp.getaTalkIconIntent());
        NotificationManager mNotificationManager
                = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification notification = nBuilder.build();
        notification.flags = Notification.FLAG_ONLY_ALERT_ONCE
                & Notification.FLAG_FOREGROUND_SERVICE & Notification.FLAG_NO_CLEAR;

        // mId allows you to update the notification later on.
        mNotificationManager.notify(notificationID, notification);
        lastNotificationText = message;
    }

    /**
     * This method should be called when general notification is changed from the outside(like in
     * call notification for example).
     */
    public static void generalNotificationInvalidated()
    {
        lastNotificationText = null;
    }

    /**
     * Indicates if the service given by <tt>activityClass</tt> is currently running.
     *
     * @param context the Android context
     * @param activityClass the activity class to check
     * @return <tt>true</tt> if the activity given by the class is running, <tt>false</tt> - otherwise
     */
    public static boolean isActivityRunning(Context context, Class<?> activityClass)
    {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> services = activityManager.getRunningTasks(Integer.MAX_VALUE);

        boolean isServiceFound = false;
        for (int i = 0; i < services.size(); i++) {
            if (services.get(i).topActivity.getClassName().equals(activityClass.getName())) {
                isServiceFound = true;
            }
        }
        return isServiceFound;
    }

    public static void setOnTouchBackgroundEffect(View view)
    {
        view.setOnTouchListener(new OnTouchListener()
        {
            public boolean onTouch(View v, MotionEvent event)
            {
                if (!(v.getBackground() instanceof TransitionDrawable))
                    return false;

                TransitionDrawable transition = (TransitionDrawable) v.getBackground();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        transition.startTransition(500);
                        break;
                    case MotionEvent.ACTION_HOVER_EXIT:
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        transition.reverseTransition(500);
                        break;
                }
                return false;
            }
        });
    }

    /**
     * Returns <tt>true</tt> if we are currently running on tablet device.
     *
     * @return <tt>true</tt> if we are currently running on tablet device.
     */
    public static boolean isTablet()
    {
        Context context = aTalkApp.getGlobalContext();

        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    /**
     * Returns <tt>true</tt> if this device supports at least given API level.
     *
     * @param minApiLevel API level value to check
     * @return <tt>true</tt> if this device supports at least given API level.
     */
    public static boolean hasAPI(int minApiLevel)
    {
        return API_LEVEL >= minApiLevel;
    }

    /**
     * Returns <tt>true</tt> if current <tt>Thread</tt> is UI thread.
     *
     * @return <tt>true</tt> if current <tt>Thread</tt> is UI thread.
     */
    public static boolean isUIThread()
    {
        return Looper.getMainLooper().getThread() == Thread.currentThread();
    }

    /**
     * Converts pixels to density independent pixels.
     *
     * @param px pixels value to convert.
     * @return density independent pixels value for given pixels value.
     */
    public static int pxToDp(int px)
    {
        return (int) (((float) px) * aTalkApp.getAppResources().getDisplayMetrics().density + 0.5f);
    }
}