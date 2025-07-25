/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.view.MenuItem;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import java.util.ArrayList;
import java.util.List;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.LauncherActivity;
import org.atalk.android.plugin.errorhandler.ExceptionHandler;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Implements a base <code>FragmentActivity</code> which employs OSGi.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OSGiActivity extends BaseActivity {
    private BundleActivator bundleActivator;

    private BundleContext bundleContext;

    private BundleContextHolder mService;

    private ServiceConnection serviceConnection;

    /**
     * List of attached {@link OSGiUiPart}.
     */
    private final List<OSGiUiPart> osgiFragments = new ArrayList<>();

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     * Both setLanguage and setTheme must happen before super.onCreate() is called
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down
     * then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hooks the exception handler to the UI thread
        ExceptionHandler.checkAndAttachExceptionHandler();
        ServiceConnection serviceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder service) {
                if (this == OSGiActivity.this.serviceConnection)
                    setService((BundleContextHolder) service);
            }

            public void onServiceDisconnected(ComponentName name) {
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
    }

    protected void onResume() {
        super.onResume();
        // If OSGi service is running check for send logs
        if (bundleContext != null) {
            checkForSendLogsDialog();
        }
    }

    protected void onPause() {
        super.onPause();
        // Clear the references to this activity.
        clearReferences();
    }

    /**
     * Called when an activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ServiceConnection serviceConnection = this.serviceConnection;
        this.serviceConnection = null;
        try {
            setService(null);
        } finally {
            if (serviceConnection != null)
                unbindService(serviceConnection);
        }
    }

    /**
     * Checks if the crash has occurred since the aTalk was last started. If it's true asks the
     * user about eventual logs report.
     */
    private void checkForSendLogsDialog() {
        // Checks if aTalk has previously crashed and asks the user user about log reporting
        if (!ExceptionHandler.hasCrashed()) {
            return;
        }
        // Clears the crash status and ask user to send debug log
        ExceptionHandler.resetCrashedStatus();
        AlertDialog.Builder question = new AlertDialog.Builder(this);
        question.setTitle(R.string.warning)
                .setMessage(getString(R.string.send_log_prompt))
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    dialog.dismiss();
                    aTalkApp.showSendLogsDialog();
                })
                .setNegativeButton(R.string.no, (dialog, which) -> dialog.dismiss())
                .create().show();
    }

    private void setService(BundleContextHolder service) {
        if (mService != service) {
            if ((mService != null) && (bundleActivator != null)) {
                try {
                    mService.removeBundleActivator(bundleActivator);
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

            mService = service;
            if (mService != null) {
                if (bundleActivator == null) {
                    bundleActivator = new BundleActivator() {
                        public void start(BundleContext bundleContext)
                                throws Exception {
                            internalStart(bundleContext);
                        }

                        public void stop(BundleContext bundleContext)
                                throws Exception {
                            internalStop(bundleContext);
                        }
                    };
                }
                mService.addBundleActivator(bundleActivator);
            }
        }
    }

    /**
     * Starts this osgi activity.
     *
     * @param bundleContext the osgi <code>BundleContext</code>
     *
     * @throws Exception
     */
    private void internalStart(BundleContext bundleContext)
            throws Exception {
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
     * @param bundleContext the osgi <code>BundleContext</code>
     *
     * @throws Exception
     */
    private void internalStop(BundleContext bundleContext)
            throws Exception {
        if (this.bundleContext != null) {
            if (bundleContext == null)
                bundleContext = this.bundleContext;
            if (this.bundleContext == bundleContext)
                this.bundleContext = null;
            stop(bundleContext);
        }
    }

    protected void start(BundleContext bundleContext)
            throws Exception {
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
            throws Exception {
        // Stops children OSGI fragments.
        for (OSGiUiPart osGiFragment : osgiFragments) {
            osGiFragment.stop(bundleContext);
        }
    }

    /**
     * Registers child <code>OSGiUiPart</code> to be notified on startup.
     *
     * @param fragment child <code>OSGiUiPart</code> contained in this <code>Activity</code>.
     */
    public void registerOSGiFragment(OSGiUiPart fragment) {
        osgiFragments.add(fragment);

        if (bundleContext != null) {
            // If context exists it means we have started already, so start the fragment immediately
            try {
                fragment.start(bundleContext);
            } catch (Exception e) {
                Timber.e(e, "Error starting OSGi Fragment");
            }
        }
    }

    /**
     * Unregisters child <code>OSGiUiPart</code>.
     *
     * @param fragment the <code>OSGiUiPart</code> that will be unregistered.
     */
    public void unregisterOSGiFragment(OSGiUiPart fragment) {
        if (bundleContext != null) {
            try {
                fragment.stop(bundleContext);
            } catch (Exception e) {
                Timber.e(e, "Error while trying to stop OSGi Fragment");
            }
        }
        osgiFragments.remove(fragment);
    }

    /**
     * Handler for home navigator. Use upIntent if parentActivityName defined. Otherwise execute onBackKeyPressed.
     * Account setting must back to its previous menu (BackKey) to properly save changes
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Intent upIntent = NavUtils.getParentActivityIntent(this);
            if (upIntent != null) {
                Timber.w("Process UpIntent for: %s", this.getLocalClassName());
                // NavUtils.navigateUpTo(this, upIntent);
                navigateUpTo(upIntent);
            }
            else {
                Timber.w("Replace Up with BackKeyPress for: %s", this.getLocalClassName());
                finish();
                // Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
                // if (!this.getClass().equals(homeActivity)) {
                //    switchActivity(homeActivity);
                // }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Returns OSGI <code>BundleContext</code>.
     *
     * @return OSGI <code>BundleContext</code>.
     */
    protected BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Checks if the OSGi is started and if not eventually triggers <code>LauncherActivity</code>
     * that will restore current activity from its <code>Intent</code>.
     *
     * @return <code>true</code> if restore <code>Intent</code> has been posted.
     */
    protected boolean postRestoreIntent() {
        // Restore after OSGi startup
        if (AppGUIActivator.bundleContext == null) {
            Intent intent = new Intent(aTalkApp.getInstance(), LauncherActivity.class);
            intent.putExtra(LauncherActivity.ARG_RESTORE_INTENT, getIntent());
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    private void clearReferences() {
        AppCompatActivity currentActivity = aTalkApp.getCurrentActivity();
        if (currentActivity != null && currentActivity.equals(this))
            aTalkApp.setCurrentActivity(null);
    }
}
