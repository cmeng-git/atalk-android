/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android;

import static org.atalk.android.gui.settings.SettingsFragment.P_KEY_LOCALE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.DownloadManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.jakewharton.threetenabp.AndroidThreeTen;

import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.LauncherActivity;
import org.atalk.android.gui.Splash;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.account.AccountLoginActivity;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.DrawableCache;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.plugin.permissions.PermissionsActivity;
import org.atalk.impl.androidnotification.NotificationHelper;
import org.atalk.impl.androidtray.NotificationPopupHandler;
import org.atalk.impl.timberlog.TimberLogImpl;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.log.LogUploadService;
import org.osgi.framework.BundleContext;

import java.awt.Dimension;

import timber.log.Timber;

/**
 * <code>aTalkApp</code> is used, as a global context and utility class for global actions (like EXIT broadcast).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class aTalkApp extends Application implements LifecycleEventObserver {
    /**
     * Name of config property that indicates whether foreground icon should be displayed.
     */
    public static final String SHOW_ICON_PROPERTY_NAME = "org.atalk.android.show_icon";

    /**
     * Indicate if aTalk is in the foreground (true) or background (false)
     */
    public static boolean isForeground = false;

    public static boolean permissionFirstRequest = true;

    /**
     * Static instance holder.
     */
    private static Context mInstance;

    /**
     * The currently shown activity.
     */
    private static AppCompatActivity currentActivity = null;

    /**
     * Bitmap cache instance.
     */
    private final static DrawableCache drawableCache = new DrawableCache();

    /**
     * Used to keep the track of GUI activity.
     */
    private static long lastGuiActivity;

    /**
     * Used to track current <code>Activity</code>. This monitor is notified each time current <code>Activity</code> changes.
     */
    private static final Object currentActivityMonitor = new Object();

    public static boolean isPortrait = true;
    public static Dimension mDisplaySize;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
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
        // MigrationTo6.updateChatSessionTable(DatabaseBackend.getInstance(this).getWritableDatabase());

        // Do this after WebView(this).destroy(); Set up contextWrapper to use aTalk user selected Language
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        AndroidThreeTen.init(this);

        getDisplaySize();
    }

    /**
     * setLocale for Application class to work properly with PBContext class.
     */
    @Override
    protected void attachBaseContext(Context base) {
        // mInstance must be initialize before getProperty() for SQLiteConfigurationStore() init.
        mInstance = base;
        String language = ConfigurationUtils.getProperty(P_KEY_LOCALE, "");
        // showToastMessage("aTalkApp reinit locale: " + language);
        mInstance = LocaleHelper.setLocale(base, language);
        super.attachBaseContext(mInstance);
    }

    /**
     * Returns the size of the main application window.
     * Must support different android API else system crashes on some devices
     * e.g. UnsupportedOperationException: in Xiaomi Mi 11 Android 11 (SDK 30)
     *
     * @return the size of the main application display window.
     */
    public static Dimension getDisplaySize() {
        // Get android device screen display size
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Point size = new Point();
            ((WindowManager) mInstance.getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getSize(size);
            mDisplaySize = new Dimension(size.x, size.y);
        }
        else {
            Rect mBounds = ((WindowManager) mInstance.getSystemService(WINDOW_SERVICE)).getCurrentWindowMetrics().getBounds();
            mDisplaySize = new Dimension(Math.abs(mBounds.width()), Math.abs(mBounds.height()));
        }
        return mDisplaySize;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        isPortrait = (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT);
    }

    /**
     * This method is for use in emulated process environments.  It will never be called on a production Android
     * device, where processes are removed by simply killing them; no user code (including this callback)
     * is executed when doing so.
     */
    @Override
    public void onTerminate() {
        mInstance = null;
        super.onTerminate();
    }

    // ========= LifecycleEventObserver implementations ======= //
    @Override
    public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
        if (Lifecycle.Event.ON_START == event) {
            isForeground = true;
            Timber.d("APP FOREGROUNDED");
        }
        else if (Lifecycle.Event.ON_STOP == event) {
            isForeground = false;
            Timber.d("APP BACKGROUNDED");
        }
    }

    /**
     * Returns true if the device is locked or screen turned off (in case password not set)
     */
    public static boolean isDeviceLocked() {
        boolean isLocked;

        // First we check the locked state
        KeyguardManager keyguardManager = (KeyguardManager) mInstance.getSystemService(Context.KEYGUARD_SERVICE);
        boolean inKeyguardRestrictedInputMode = keyguardManager.inKeyguardRestrictedInputMode();

        if (inKeyguardRestrictedInputMode) {
            isLocked = true;
        }
        else {
            // If password is not set in the settings, the inKeyguardRestrictedInputMode() returns false,
            // so we need to check if screen on for this case
            PowerManager powerManager = (PowerManager) mInstance.getSystemService(Context.POWER_SERVICE);
            isLocked = !powerManager.isInteractive();
        }
        Timber.d("Android device is %s.", isLocked ? "locked" : "unlocked");
        return isLocked;
    }

    /**
     * Returns global bitmap cache of the application.
     *
     * @return global bitmap cache of the application.
     */
    public static DrawableCache getImageCache() {
        return drawableCache;
    }

    /**
     * Retrieves <code>AudioManager</code> instance using application context.
     *
     * @return <code>AudioManager</code> service instance.
     */
    public static AudioManager getAudioManager() {
        return (AudioManager) mInstance.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * Retrieves <code>CameraManager</code> instance using application context.
     *
     * @return <code>CameraManager</code> service instance.
     */
    public static CameraManager getCameraManager() {
        return (CameraManager) mInstance.getSystemService(Context.CAMERA_SERVICE);
    }

    /**
     * Retrieves <code>SensorManager</code> instance using application context.
     *
     * @return <code>SensorManager</code> service instance.
     */
    public static SensorManager getSensorManager() {
        return (SensorManager) mInstance.getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Retrieves <code>NotificationManager</code> instance using application context.
     *
     * @return <code>NotificationManager</code> service instance.
     */
    public static NotificationManager getNotificationManager() {
        return (NotificationManager) mInstance.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Retrieves <code>DownloadManager</code> instance using application context.
     *
     * @return <code>DownloadManager</code> service instance.
     */
    public static DownloadManager getDownloadManager() {
        return (DownloadManager) mInstance.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /**
     * Get aTalkApp application instance
     *
     * @return aTalkApp mInstance
     */
    public static Context getInstance() {
        return mInstance;
    }

    /**
     * Returns global application context.
     *
     * @return Returns global application <code>Context</code>.
     */
    public static Context getGlobalContext() {
        return mInstance.getApplicationContext();
    }

    /**
     * Returns application <code>Resources</code> object.
     *
     * @return application <code>Resources</code> object.
     */
    public static Resources getAppResources() {
        return mInstance.getResources();
    }

    /**
     * Returns Android string resource of the user selected language for given <code>id</code>
     * and format arguments that will be used for substitution.
     *
     * @param id the string identifier.
     * @param arg the format arguments that will be used for substitution.
     *
     * @return Android string resource for given <code>id</code> and format arguments.
     */
    public static String getResString(int id, Object... arg) {
        return mInstance.getString(id, arg);
    }

    /**
     * Returns Android string resource for given <code>id</code> and format arguments that will be used for substitution.
     *
     * @param aString the string identifier.
     *
     * @return Android string resource for given <code>id</code> and format arguments.
     */
    public static String getResStringByName(String aString) {
        String packageName = mInstance.getPackageName();
        @SuppressLint("DiscouragedApi")
        int resId = mInstance.getResources().getIdentifier(aString, "string", packageName);

        return (resId != 0) ? mInstance.getString(resId) : "";
    }

    private static Toast toast = null;

    /**
     * Toast show message in UI thread;
     * Cancel current toast view to allow immediate display of new toast message.
     *
     * @param message the string message to display.
     */
    public static void showToastMessage(final String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (toast != null && toast.getView() != null) {
                toast.cancel();
            }
            toast = Toast.makeText(mInstance, message, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    public static void showToastMessage(int id, Object... arg) {
        showToastMessage(mInstance.getString(id, arg));
    }

    public static void showGenericError(final int id, final Object... arg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            String msg = mInstance.getString(id, arg);
            DialogActivity.showDialog(mInstance, mInstance.getString(R.string.service_gui_ERROR), msg);
        });
    }

    /**
     * Returns home <code>Activity</code> class.
     *
     * @return Returns home <code>Activity</code> class.
     */
    public static Class<?> getHomeScreenActivityClass() {
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
     * Creates the home <code>Activity</code> <code>Intent</code>.
     *
     * @return the home <code>Activity</code> <code>Intent</code>.
     */
    public static Intent getHomeIntent() {
        // Home is singleTask anyway, but this way it can be started from non Activity context.
        Intent homeIntent = new Intent(mInstance, getHomeScreenActivityClass());
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return homeIntent;
    }

    /**
     * Creates pending <code>Intent</code> to be started, when aTalk icon is clicked.
     *
     * @return new pending <code>Intent</code> to be started, when aTalk icon is clicked.
     */
    public static PendingIntent getaTalkIconIntent() {
        Intent intent = ChatSessionManager.getLastChatIntent();
        if (intent == null) {
            intent = getHomeIntent();
        }
        return PendingIntent.getActivity(mInstance, 0, intent,
                NotificationPopupHandler.getPendingIntentFlag(false, true));
    }

    /**
     * Returns <code>ConfigurationService</code> instance.
     *
     * @return <code>ConfigurationService</code> instance.
     */
    public static ConfigurationService getConfig() {
        return ServiceUtils.getService(AndroidGUIActivator.bundleContext, ConfigurationService.class);
    }

    /**
     * Returns <code>true</code> if aTalk notification icon should be displayed.
     *
     * @return <code>true</code> if aTalk notification icon should be displayed.
     */
    public static boolean isIconEnabled() {
        return (getConfig() == null) || getConfig().getBoolean(SHOW_ICON_PROPERTY_NAME, false);
    }

    /**
     * Sets the current activity.
     *
     * @param a the current activity to set
     */
    public static void setCurrentActivity(AppCompatActivity a) {
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
     * Returns monitor object that will be notified each time current <code>Activity</code> changes.
     *
     * @return monitor object that will be notified each time current <code>Activity</code> changes.
     */
    static public Object getCurrentActivityMonitor() {
        return currentActivityMonitor;
    }

    /**
     * Returns the current activity.
     *
     * @return the current activity
     */
    public static AppCompatActivity getCurrentActivity() {
        return currentActivity;
    }

    /**
     * Returns the time elapsed since last aTalk <code>Activity</code> was open in milliseconds.
     *
     * @return the time elapsed since last aTalk <code>Activity</code> was open in milliseconds.
     */
    public static long getLastGuiActivityInterval() {
        // GUI is currently active
        if (lastGuiActivity == -1) {
            return 0;
        }
        return System.currentTimeMillis() - lastGuiActivity;
    }

    /**
     * Checks if current <code>Activity</code> is the home one.
     *
     * @return <code>true</code> if the home <code>Activity</code> is currently active.
     */
    public static boolean isHomeActivityActive() {
        return currentActivity != null && currentActivity.getClass().equals(getHomeScreenActivityClass());
    }

    /**
     * Displays the send logs dialog.
     */
    public static void showSendLogsDialog() {
        LogUploadService logUpload = ServiceUtils.getService(AndroidGUIActivator.bundleContext, LogUploadService.class);
        String defaultEmail = getConfig().getString("org.atalk.android.LOG_REPORT_EMAIL");

        if (logUpload != null) {
            logUpload.sendLogs(new String[]{defaultEmail},
                    getResString(R.string.service_gui_SEND_LOGS_SUBJECT),
                    getResString(R.string.service_gui_SEND_LOGS_TITLE));
        }
    }

    /**
     * If OSGi has not started, then wait for the <code>LauncherActivity</code> etc to complete before
     * showing any dialog. Dialog should only be shown while <code>NOT in LaunchActivity</code> etc
     * Otherwise the dialog will be obscured by these activities; max wait = 5 waits of 1000ms each
     */
    public static Activity waitForFocus() {
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
