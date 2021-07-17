/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.MenuItem;
import android.view.View;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.LauncherActivity;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.gui.util.ThemeHelper;
import org.atalk.android.plugin.errorhandler.ExceptionHandler;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.ArrayList;
import java.util.List;

import androidx.core.app.NavUtils;
import androidx.fragment.app.FragmentActivity;
import timber.log.Timber;

/**
 * Implements a base <tt>FragmentActivity</tt> which employs OSGi.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OSGiActivity extends FragmentActivity
{
    private BundleActivator bundleActivator;

    private BundleContext bundleContext;

    private BundleContextHolder service;

    private ServiceConnection serviceConnection;

    /**
     * UI thread handler
     */
    public final static Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * EXIT action listener that triggers closes the <tt>Activity</tt>
     */
    private ExitActionListener exitListener = new ExitActionListener();

    /**
     * List of attached {@link OSGiUiPart}.
     */
    private List<OSGiUiPart> osgiFragments = new ArrayList<>();

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     * Both setLanguage and setTheme must happen before super.onCreate() is called
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     * then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        // Always call setTheme() method in baseclass and before super.onCreate()
        ThemeHelper.setTheme(this);

        // Hooks the exception handler to the UI thread
        ExceptionHandler.checkAndAttachExceptionHandler();

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // Disable up arrow on home activity
            Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
            if (this.getClass().equals(homeActivity)) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }
            ActionBarUtil.setTitle(this, getTitle());
        }

        super.onCreate(savedInstanceState);
        ServiceConnection serviceConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName name, IBinder service)
            {
                if (this == OSGiActivity.this.serviceConnection)
                    setService((BundleContextHolder) service);
            }

            public void onServiceDisconnected(ComponentName name)
            {
                if (this == OSGiActivity.this.serviceConnection)
                    setService(null);
            }
        };

        this.serviceConnection = serviceConnection;
        boolean bindService = false;
        try {
            bindService = bindService(new Intent(this, OSGiService.class), serviceConnection, BIND_AUTO_CREATE);
        } finally {
            if (!bindService)
                this.serviceConnection = null;
        }
        // Registers exit action listener
        this.registerReceiver(exitListener, new IntentFilter(aTalkApp.ACTION_EXIT));
    }

    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        aTalkApp.setCurrentActivity(this);
    }

    @Override
    protected void attachBaseContext(Context base)
    {
        LocaleHelper.setLocale(base);
        super.attachBaseContext(base);
    }

    protected void onResume()
    {
        super.onResume();
        aTalkApp.setCurrentActivity(this);
        // If OSGi service is running check for send logs
        if (bundleContext != null) {
            checkForSendLogsDialog();
        }
    }

    protected void onPause()
    {
        // Clear the references to this activity.
        clearReferences();
        super.onPause();
    }

    /**
     * Called when an activity is destroyed.
     */
    @Override
    protected void onDestroy()
    {
        // Unregisters exit action listener
        unregisterReceiver(exitListener);
        ServiceConnection serviceConnection = this.serviceConnection;

        this.serviceConnection = null;
        try {
            setService(null);
        } finally {
            if (serviceConnection != null)
                unbindService(serviceConnection);
        }
        super.onDestroy();
    }

    /**
     * Checks if the crash has occurred since the aTalk was last started. If it's true asks the
     * user about eventual logs report.
     */
    private void checkForSendLogsDialog()
    {
        // Checks if aTalk has previously crashed and asks the user user about log reporting
        if (!ExceptionHandler.hasCrashed()) {
            return;
        }
        // Clears the crash status and ask user to send debug log
        ExceptionHandler.resetCrashedStatus();
        AlertDialog.Builder question = new AlertDialog.Builder(this);
        question.setTitle(R.string.service_gui_WARNING)
                .setMessage(getString(R.string.service_gui_SEND_LOGS_QUESTION))
                .setPositiveButton(R.string.service_gui_YES, (dialog, which) -> {
                    dialog.dismiss();
                    aTalkApp.showSendLogsDialog();
                })
                .setNegativeButton(R.string.service_gui_NO, (dialog, which) -> dialog.dismiss())
                .create().show();
    }

    private void setService(BundleContextHolder service)
    {
        if (this.service != service) {
            if ((this.service != null) && (bundleActivator != null)) {
                try {
                    this.service.removeBundleActivator(bundleActivator);
                    bundleActivator = null;
                } finally {
                    try {
                        internalStop(null);
                    } catch (Throwable t) {
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            }

            this.service = service;
            if (this.service != null) {
                if (bundleActivator == null) {
                    bundleActivator = new BundleActivator()
                    {
                        public void start(BundleContext bundleContext)
                                throws Exception
                        {
                            internalStart(bundleContext);
                        }

                        public void stop(BundleContext bundleContext)
                                throws Exception
                        {
                            internalStop(bundleContext);
                        }
                    };
                }
                this.service.addBundleActivator(bundleActivator);
            }
        }
    }

    /**
     * Starts this osgi activity.
     *
     * @param bundleContext the osgi <tt>BundleContext</tt>
     * @throws Exception
     */
    private void internalStart(BundleContext bundleContext)
            throws Exception
    {
        this.bundleContext = bundleContext;
        boolean start = false;
        try {
            start(bundleContext);
            start = true;
        } finally {
            if (!start && (this.bundleContext == bundleContext))
                this.bundleContext = null;
        }
    }

    /**
     * Stops this osgi activity.
     *
     * @param bundleContext the osgi <tt>BundleContext</tt>
     * @throws Exception
     */
    private void internalStop(BundleContext bundleContext)
            throws Exception
    {
        if (this.bundleContext != null) {
            if (bundleContext == null)
                bundleContext = this.bundleContext;
            if (this.bundleContext == bundleContext)
                this.bundleContext = null;
            stop(bundleContext);
        }
    }

    protected void start(BundleContext bundleContext)
            throws Exception
    {
        // Starts children OSGI fragments.
        for (OSGiUiPart osGiFragment : osgiFragments) {
            osGiFragment.start(bundleContext);
        }
        // If OSGi has just started and we're on UI thread check for crash event. We must be on
        // UIThread to show the dialog and it makes no sense to show it from the background, so
        // it will be eventually displayed from onResume()
        if (Looper.getMainLooper() == Looper.myLooper()) {
            checkForSendLogsDialog();
        }
    }

    protected void stop(BundleContext bundleContext)
            throws Exception
    {
        // Stops children OSGI fragments.
        for (OSGiUiPart osGiFragment : osgiFragments) {
            osGiFragment.stop(bundleContext);
        }
    }

    /**
     * Registers child <tt>OSGiUiPart</tt> to be notified on startup.
     *
     * @param fragment child <tt>OSGiUiPart</tt> contained in this <tt>Activity</tt>.
     */
    public void registerOSGiFragment(OSGiUiPart fragment)
    {
        osgiFragments.add(fragment);

        if (bundleContext != null) {
            // If context exists it means we have started already, so start the fragment immediately
            try {
                fragment.start(bundleContext);
            } catch (Exception e) {
                Timber.e(e, "Error starting OSGiFragment");
            }
        }
    }

    /**
     * Unregisters child <tt>OSGiUiPart</tt>.
     *
     * @param fragment the <tt>OSGiUiPart</tt> that will be unregistered.
     */
    public void unregisterOSGiFragment(OSGiUiPart fragment)
    {
        if (bundleContext != null) {
            try {
                fragment.stop(bundleContext);
            } catch (Exception e) {
                Timber.e(e, "Error while trying to stop OSGiFragment");
            }
        }
        osgiFragments.remove(fragment);
    }

    /**
     * Convenience method which starts a new activity for given <tt>activityClass</tt> class
     *
     * @param activityClass the activity class
     */
    protected void startActivity(Class<?> activityClass)
    {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
    }

    /**
     * Start the application notification settings page
     */
    public void openNotificationSettings()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        }
        else {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    /**
     * Convenience method that switches from one activity to another.
     *
     * @param activityClass the activity class
     */
    protected void switchActivity(Class<?> activityClass)
    {
        startActivity(activityClass);
        finish();
    }

    /**
     * Convenience method that switches from one activity to another.
     *
     * @param activityIntent the next activity <tt>Intent</tt>
     */
    protected void switchActivity(Intent activityIntent)
    {
        startActivity(activityIntent);
        finish();
    }

    /**
     * Handler for home navigator. Use upIntent if parentActivityName defined. Otherwise execute onBackKeyPressed.
     * Account setting must back to its previous menu (BackKey) to properly save changes
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (upIntent != null) {
                    Timber.w("Process UpIntent for: %s", this.getLocalClassName());
                    NavUtils.navigateUpTo(this, upIntent);
                }
                else {
                    Timber.w("Replace Up with BackKeyPress for: %s", this.getLocalClassName());
                    super.onBackPressed();
                    // Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
                    // if (!this.getClass().equals(homeActivity)) {
                    //    switchActivity(homeActivity);
                    // }
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Returns OSGI <tt>BundleContext</tt>.
     *
     * @return OSGI <tt>BundleContext</tt>.
     */
    protected BundleContext getBundleContext()
    {
        return bundleContext;
    }

    /**
     * Returns the content <tt>View</tt>.
     *
     * @return the content <tt>View</tt>.
     */
    protected View getContentView()
    {
        return findViewById(android.R.id.content);
    }

    /**
     * Checks if the OSGi is started and if not eventually triggers <tt>LauncherActivity</tt>
     * that will restore current activity from its <tt>Intent</tt>.
     *
     * @return <tt>true</tt> if restore <tt>Intent</tt> has been posted.
     */
    protected boolean postRestoreIntent()
    {
        // Restore after OSGi startup
        if (AndroidGUIActivator.bundleContext == null) {
            Intent intent = new Intent(aTalkApp.getGlobalContext(), LauncherActivity.class);
            intent.putExtra(LauncherActivity.ARG_RESTORE_INTENT, getIntent());
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    /**
     * Broadcast listener that listens for {@link aTalkApp#ACTION_EXIT} and then finishes this <tt>Activity</tt>
     */
    class ExitActionListener extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            finish();
        }
    }

    private void clearReferences()
    {
        Activity currentActivity = aTalkApp.getCurrentActivity();
        if (currentActivity != null && currentActivity.equals(this))
            aTalkApp.setCurrentActivity(null);
    }
}
