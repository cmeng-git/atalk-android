/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.*;
import android.webkit.WebView;
import android.widget.Toast;

import com.jakewharton.threetenabp.AndroidThreeTen;

import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.gui.LauncherActivity;
import org.atalk.android.gui.*;
import org.atalk.android.gui.account.AccountLoginActivity;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.DrawableCache;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.plugin.permissions.PermissionsActivity;
import org.atalk.android.plugin.timberlog.TimberLogImpl;
import org.atalk.impl.androidnotification.NotificationHelper;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.log.LogUploadService;
import org.atalk.service.osgi.OSGiService;
import org.osgi.framework.BundleContext;

import androidx.lifecycle.*;
import timber.log.Timber;

/**
 * <tt>aTalkApp</tt> is used, as a global context and utility class for global actions (like EXIT broadcast).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class aTalkApp extends Application implements LifecycleObserver
{
    /**
     * Name of config property that indicates whether foreground icon should be displayed.
     */
    public static final String SHOW_ICON_PROPERTY_NAME = "org.atalk.android.show_icon";

    /**
     * The EXIT action name that is broadcast to all OSGiActivities
     */
    public static final String ACTION_EXIT = "org.atalk.android.exit";

    /**
     * Indicate if aTalk is in foreground (true) or background (false)
     */
    public static boolean isForeground = false;

    public static boolean permissionFirstRequest = true;

    /**
     * Static instance holder.
     */
    private static aTalkApp mInstance;

    /**
     * The currently shown activity.
     */
    private static Activity currentActivity = null;

    /**
     * Bitmap cache instance.
     */
    private final DrawableCache drawableCache = new DrawableCache();

    /**
     * Used to keep the track of GUI activity.
     */
    private static long lastGuiActivity;

    /**
     * Used to track current <tt>Activity</tt>. This monitor is notified each time current <tt>Activity</tt> changes.
     */
    private static final Object currentActivityMonitor = new Object();

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate()
    {
        TimberLogImpl.init();

        // This helps to prevent WebView resets UI back to system default.
        // Must skip for < N else weired exceptions happen in Note-5
        // chromium-Monochrome.aab-stable-424011020:5 throw NPE at org.chromium.ui.base.Clipboard.<init>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                new WebView(this).destroy();
            } catch (Exception e) {
                Timber.e("WebView init exception: %s", e.getMessage());
            }
        }

        // Must initialize Notification channels before any notification is being issued.
        new NotificationHelper(this);

        // force delete in case system locked during testing
        // ServerPersistentStoresRefreshDialog.deleteDB();  // purge sql database

        // Trigger the aTalk database upgrade or creation if none exist
        DatabaseBackend.getInstance(this);

        // Do this after WebView(this).destroy(); Set up contextWrapper to use aTalk user selected Language
        mInstance = this;
        String language = ConfigurationUtils.getProperty(getString(R.string.pref_key_locale), "");
        LocaleHelper.setLocale(mInstance, language);

        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        AndroidThreeTen.init(this);
    }

    /**
     * This method is for use in emulated process environments.  It will never be called on a production Android
     * device, where processes are removed by simply killing them; no user code (including this callback)
     * is executed when doing so.
     */
    @Override
    public void onTerminate()
    {
        mInstance = null;
        super.onTerminate();
    }

    // ========= Lifecycle implementations ======= //

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded()
    {
        isForeground = true;
        Timber.d("APP FOREGROUNDED");
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded()
    {
        isForeground = false;
        Timber.d("APP BACKGROUNDED");
    }

    /**
     * All language setting changes must call via this so aTalkApp contextWrapper is updated
     *
     * @param language locale for the aTalkApp
     */
    public static void setLocale(String language)
    {
        LocaleHelper.setLocale(mInstance, language);
    }

    /**
     * Shutdowns the app by stopping <tt>OSGiService</tt> and broadcasting {@link #ACTION_EXIT}.
     */
    public static void shutdownApplication()
    {
        mInstance.doShutdownApplication();
    }

    /**
     * Shutdowns the OSGI service and sends the EXIT action broadcast.
     */
    private void doShutdownApplication()
    {
        // Shutdown the OSGi service
        stopService(new Intent(this, OSGiService.class));
        // Broadcast the exit action
        Intent exitIntent = new Intent();
        exitIntent.setAction(ACTION_EXIT);
        sendBroadcast(exitIntent);
    }

    /**
     * Returns global bitmap cache of the application.
     *
     * @return global bitmap cache of the application.
     */
    public static DrawableCache getImageCache()
    {
        return mInstance.drawableCache;
    }

    /**
     * Retrieves <tt>AudioManager</tt> instance using application context.
     *
     * @return <tt>AudioManager</tt> service instance.
     */
    public static AudioManager getAudioManager()
    {
        return (AudioManager) getGlobalContext().getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Retrieves <tt>PowerManager</tt> instance using application context.
     *
     * @return <tt>PowerManager</tt> service instance.
     */
    public static PowerManager getPowerManager()
    {
        return (PowerManager) getGlobalContext().getSystemService(Context.POWER_SERVICE);
    }

    /**
     * Retrieves <tt>SensorManager</tt> instance using application context.
     *
     * @return <tt>SensorManager</tt> service instance.
     */
    public static SensorManager getSensorManager()
    {
        return (SensorManager) getGlobalContext().getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Retrieves <tt>NotificationManager</tt> instance using application context.
     *
     * @return <tt>NotificationManager</tt> service instance.
     */
    public static NotificationManager getNotificationManager()
    {
        return (NotificationManager) getGlobalContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Retrieves <tt>DownloadManager</tt> instance using application context.
     *
     * @return <tt>DownloadManager</tt> service instance.
     */
    public static DownloadManager getDownloadManager()
    {
        return (DownloadManager) getGlobalContext().getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /**
     * Get aTalkApp application instance
     *
     * @return aTalkApp mInstance
     */
    public static aTalkApp getInstance()
    {
        return mInstance;
    }

    /**
     * Returns global application context.
     *
     * @return Returns global application <tt>Context</tt>.
     */
    public static Context getGlobalContext()
    {
        return mInstance.getApplicationContext();
    }

    /**
     * Returns application <tt>Resources</tt> object.
     *
     * @return application <tt>Resources</tt> object.
     */
    public static Resources getAppResources()
    {
        return mInstance.getResources();
    }

    /**
     * Returns Android string resource of the user selected language for given <tt>id</tt>
     * and format arguments that will be used for substitution.
     *
     * @param id the string identifier.
     * @param arg the format arguments that will be used for substitution.
     * @return Android string resource for given <tt>id</tt> and format arguments.
     */
    public static String getResString(int id, Object... arg)
    {
        return mInstance.getString(id, arg);
    }

    /**
     * Returns Android string resource for given <tt>id</tt> and format arguments that will be used for substitution.
     *
     * @param id the string identifier.
     * @param arg the format arguments that will be used for substitution.
     * @return Android string resource for given <tt>id</tt> and format arguments.
     */

    public static String getResStringByName(String aString)
    {
        String packageName = mInstance.getPackageName();
        int resId = mInstance.getResources().getIdentifier(aString, "string", packageName);
        return mInstance.getString(resId);
    }

    /**
     * Toast show message in UI thread
     *
     * @param message the string message to display.
     */
    public static void showToastMessage(final String message)
    {
        new Handler(Looper.getMainLooper()).post(()
                -> Toast.makeText(getGlobalContext(), message, Toast.LENGTH_LONG).show());
    }

    public static void showToastMessage(int id)
    {
        showToastMessage(getResString(id));
    }

    public static void showToastMessage(int id, Object... arg)
    {
        showToastMessage(mInstance.getString(id, arg));
    }

    public static void showGenericError(final int id, final Object... arg)
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            String msg = mInstance.getString(id, arg);
            DialogActivity.showDialog(mInstance, mInstance.getString(R.string.service_gui_ERROR), msg);
        });
    }

    /**
     * Returns home <tt>Activity</tt> class.
     *
     * @return Returns home <tt>Activity</tt> class.
     */
    public static Class<?> getHomeScreenActivityClass()
    {
        BundleContext osgiContext = AndroidGUIActivator.bundleContext;
        if (osgiContext == null) {
            // If OSGI has not started show splash screen as home
            return LauncherActivity.class;
        }

        // If account manager is null means that OSGI has not started yet
        AccountManager accountManager = ServiceUtils.getService(osgiContext, AccountManager.class);
        if (accountManager == null) {
            return LauncherActivity.class;
        }

        final int accountCount = accountManager.getStoredAccounts().size();
        // Start new account Activity if none is found
        if (accountCount == 0) {
            return AccountLoginActivity.class;
        }
        else {
            // Start main view
            return aTalk.class;
        }
    }

    /**
     * Creates the home <tt>Activity</tt> <tt>Intent</tt>.
     *
     * @return the home <tt>Activity</tt> <tt>Intent</tt>.
     */
    public static Intent getHomeIntent()
    {
        // Home is singleTask anyway, but this way it can be started from non Activity context.
        Intent homeIntent = new Intent(mInstance, getHomeScreenActivityClass());
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return homeIntent;
    }

    /**
     * Creates pending <tt>Intent</tt> to be started, when aTalk icon is clicked.
     *
     * @return new pending <tt>Intent</tt> to be started, when aTalk icon is clicked.
     */
    public static PendingIntent getaTalkIconIntent()
    {
        Intent intent = ChatSessionManager.getLastChatIntent();
        if (intent == null) {
            intent = getHomeIntent();
        }
        return PendingIntent.getActivity(getGlobalContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Returns <tt>ConfigurationService</tt> instance.
     *
     * @return <tt>ConfigurationService</tt> instance.
     */
    public static ConfigurationService getConfig()
    {
        return ServiceUtils.getService(AndroidGUIActivator.bundleContext, ConfigurationService.class);
    }

    /**
     * Returns <tt>true</tt> if aTalk notification icon should be displayed.
     *
     * @return <tt>true</tt> if aTalk notification icon should be displayed.
     */
    public static boolean isIconEnabled()
    {
        return (getConfig() == null) || getConfig().getBoolean(SHOW_ICON_PROPERTY_NAME, false);
    }

    /**
     * Sets the current activity.
     *
     * @param a the current activity to set
     */
    public static void setCurrentActivity(Activity a)
    {
        synchronized (currentActivityMonitor) {
            // Timber.i("Current activity set to %s", a);
            currentActivity = a;

            if (currentActivity == null) {
                lastGuiActivity = System.currentTimeMillis();
            }
            else {
                lastGuiActivity = -1;
            }
            // Notify listening threads
            currentActivityMonitor.notifyAll();
        }
    }

    /**
     * Returns monitor object that will be notified each time current <tt>Activity</tt> changes.
     *
     * @return monitor object that will be notified each time current <tt>Activity</tt> changes.
     */
    static public Object getCurrentActivityMonitor()
    {
        return currentActivityMonitor;
    }

    /**
     * Returns the current activity.
     *
     * @return the current activity
     */
    public static Activity getCurrentActivity()
    {
        return currentActivity;
    }

    /**
     * Returns the time elapsed since last atalk <tt>Activity</tt> was open in milliseconds.
     *
     * @return the time elapsed since last atalk <tt>Activity</tt> was open in milliseconds.
     */
    public static long getLastGuiActivityInterval()
    {
        // GUI is currently active
        if (lastGuiActivity == -1) {
            return 0;
        }
        return System.currentTimeMillis() - lastGuiActivity;
    }

    /**
     * Checks if current <tt>Activity</tt> is the home one.
     *
     * @return <tt>true</tt> if the home <tt>Activity</tt> is currently active.
     */
    public static boolean isHomeActivityActive()
    {
        return currentActivity != null && currentActivity.getClass().equals(getHomeScreenActivityClass());
    }

    /**
     * Displays the send logs dialog.
     */
    public static void showSendLogsDialog()
    {
        LogUploadService logUpload = ServiceUtils.getService(AndroidGUIActivator.bundleContext, LogUploadService.class);
        String defaultEmail = getConfig().getString("org.atalk.android.LOG_REPORT_EMAIL");

        if (logUpload != null) {
            logUpload.sendLogs(new String[]{defaultEmail},
                    getResString(R.string.service_gui_SEND_LOGS_SUBJECT),
                    getResString(R.string.service_gui_SEND_LOGS_TITLE));
        }
    }

    /**
     * If OSGi has not started, then wait for the <tt>LauncherActivity</tt> etc to complete before
     * showing any dialog. Dialog should only be shown while <tt>NOT in LaunchActivity</tt> etc
     * Otherwise the dialog will be obscured by these activities; max wait = 5 waits of 1000ms each
     */
    public static Activity waitForFocus()
    {
        // if (AndroidGUIActivator.bundleContext == null) { #false on first application installation
        synchronized (currentActivityMonitor) {
            int wait = 6; // 5 waits each lasting max of 1000ms
            while (wait-- > 0) {
                try {
                    currentActivityMonitor.wait(1000);
                } catch (InterruptedException e) {
                    Timber.e("%s", e.getMessage());
                }

                Activity activity = aTalkApp.getCurrentActivity();
                if (activity != null) {
                    if (!(activity instanceof LauncherActivity
                            || activity instanceof Splash
                            || activity instanceof PermissionsActivity)) {
                        return activity;
                    }
                    else {
                        Timber.d("Wait %s sec for aTalk focus on activity: %s", wait, activity);
                    }
                }
            }
            return null;
        }
    }
}
