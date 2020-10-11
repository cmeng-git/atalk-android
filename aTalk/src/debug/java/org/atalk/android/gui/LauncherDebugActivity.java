/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui;

import android.content.Intent;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.androidnotification.NotificationHelper;
import org.atalk.impl.androidupdate.OnlineUpdateService;
import org.atalk.service.EventReceiver;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiService;
import org.osgi.framework.BundleContext;

/**
 * The splash screen fragment displays animated aTalk logo and indeterminate progress indicators.
 * <p>
 * <p>
 * TODO: Eventually add exit option to the launcher Currently it's not possible to cancel OSGi
 * startup. Attempt to stop service during startup is causing immediate service restart after
 * shutdown even with synchronization of onCreate and OnDestroy commands. Maybe there is still
 * some reference to OSGI service being held at that time ?
 * <p>
 * TODO: Prevent from recreating this Activity on startup. On startup when this Activity is
 * recreated it will also destroy OSGiService which is currently not handled properly. Options
 * specified in AndroidManifest.xml should cover most cases for now:
 * android:configChanges="keyboardHidden|orientation|screenSize"
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class LauncherDebugActivity extends OSGiActivity
{
    /**
     * Argument that holds an <tt>Intent</tt> that will be started once OSGi startup is finished.
     */
    public static final String ARG_RESTORE_INTENT = "ARG_RESTORE_INTENT";

    /**
     * Intent instance that will be called once OSGi startup is finished.
     */
    private Intent restoreIntent;

    private boolean startOnReboot = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setupStrictMode();
        super.onCreate(savedInstanceState);

        if (OSGiService.isShuttingDown()) {
            switchActivity(ShutdownActivity.class);
            return;
        }

        // Must initialize Notification channels before any notification is being issued.
        new NotificationHelper(this);

        // Get restore Intent and display "Restoring..." label
        Intent intent = getIntent();
        if (intent != null) {
            this.restoreIntent = intent.getParcelableExtra(ARG_RESTORE_INTENT);
            startOnReboot = intent.getBooleanExtra(EventReceiver.AUTO_START_ONBOOT, false);
        }

        setContentView(R.layout.splash);
        TextView stateText = findViewById(R.id.stateInfo);
        if (restoreIntent != null)
            stateText.setText(R.string.service_gui_RESTORING);

        ProgressBar mActionBarProgress = findViewById(R.id.actionbar_progress);
        mActionBarProgress.setVisibility(ProgressBar.VISIBLE);

        // Starts fade in animation
        ImageView myImageView = findViewById(R.id.loadingImage);
        Animation myFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        myImageView.startAnimation(myFadeInAnimation);
    }

    @Override
    protected void start(BundleContext osgiContext)
            throws Exception
    {
        super.start(osgiContext);
        runOnUiThread(() -> {
            if (restoreIntent != null) {
                // Starts restore intent
                startActivity(restoreIntent);
                finish();
            }
            else {
                // Perform software version update check on first launch - for debug version only
                Intent dailyCheckupIntent = new Intent(getApplicationContext(), OnlineUpdateService.class);
                dailyCheckupIntent.setAction(OnlineUpdateService.ACTION_AUTO_UPDATE_START);
                startService(dailyCheckupIntent);
            }

            // Start home screen Activity
            Class<?> activityClass = aTalkApp.getHomeScreenActivityClass();
            if (!startOnReboot || !aTalk.class.equals(activityClass)) {
                switchActivity(activityClass);
            }
            else {
                startOnReboot = false;
                finish();
            }
        });
    }

    private void setupStrictMode()
    {
        // #TODO - change all disk access to using thread
        // cmeng - disable android.os.StrictMode$StrictModeDisk Access Violation
        StrictMode.ThreadPolicy old = StrictMode.getThreadPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(old)
                .permitDiskReads()
                .permitDiskWrites()
                .build());
    }
}
