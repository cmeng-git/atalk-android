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
package org.atalk.android;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.gui.util.ThemeHelper;

/**
 * BaseActivity implements the support of user set Theme and locale.
 * All app activities must extend BaseActivity inorder to support Theme and locale.
 */
public class BaseActivity extends AppCompatActivity {
    /**
     * The EXIT action name that is broadcast to all OSGiActivities
     */
    protected static final String ACTION_EXIT = "org.atalk.android.exit";

    /**
     * UI thread handler used to call all operations that access data model.
     * This guarantees that it is accessed from the main thread.
     */
    public final static Handler uiHandler = new Handler(Looper.getMainLooper());

    /**
     * EXIT action listener that triggers closes the <code>Activity</code>
     */
    private final ExitActionListener exitListener = new ExitActionListener();

    /**
     * Override AppCompatActivity#onCreate() to support Theme setting
     * Must setTheme() before super.onCreate(), otherwise user selected Theme is not working
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Always call setTheme() method in base class and before super.onCreate()
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        configureToolBar();

        // Registers exit action listener
        ContextCompat.registerReceiver(this, exitListener,
                new IntentFilter(ACTION_EXIT), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Override AppCompatActivity#attachBaseContext() to support Locale setting.
     * Language value is initialized in Application class with user selected language.
     */
    @Override
    protected void attachBaseContext(Context base) {
        Context context = LocaleHelper.setLocale(base);
        super.attachBaseContext(context);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        aTalkApp.setCurrentActivity(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        aTalkApp.setCurrentActivity(this);
    }

    /**
     * Called when an activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        // Unregisters exit action listener
        super.onDestroy();
        unregisterReceiver(exitListener);
    }

    /**
     * Convenience method which starts a new activity for given <code>activityClass</code> class
     *
     * @param activityClass the activity class
     */
    protected void startActivity(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
    }

    /**
     * Convenience method that switches from one activity to another.
     *
     * @param activityClass the activity class
     */
    protected void switchActivity(Class<?> activityClass) {
        startActivity(activityClass);
        finish();
    }

    protected void configureToolBar() {
        // Find the toolbar view inside the activity layout - aTalk cannot use ToolBar; has layout problems
        // Toolbar toolbar = findViewById(R.id.my_toolbar);
        // if (toolbar != null)
        //   setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // mActionBar.setDisplayOptions(ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_CUSTOM );
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayUseLogoEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setCustomView(R.layout.action_bar);

            // Disable up arrow on home activity
            Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
            if (this.getClass().equals(homeActivity)) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setHomeButtonEnabled(false);

                TextView tv = findViewById(R.id.actionBarStatus);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            }
            ActionBarUtil.setTitle(this, getTitle());
            ActionBarUtil.setAvatar(this, R.drawable.ic_icon);
        }
    }

    /**
     * Returns the content <code>View</code>.
     *
     * @return the content <code>View</code>.
     */
    protected View getContentView() {
        return findViewById(android.R.id.content);
    }

    /**
     * Should return current {@link Display} rotation as defined in {@link Display#getRotation()}.
     *
     * @return current {@link Display} rotation as one of values:
     * {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
     */
    public int getDisplayRotation() {
        return ActivityCompat.getDisplayOrDefault(this).getRotation();
    }

    /**
     * Set preference title using android inbuilt toolbar
     *
     * @param resId preference tile resourceID
     */
    public void setMainTitle(int resId) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                    | ActionBar.DISPLAY_USE_LOGO
                    | ActionBar.DISPLAY_SHOW_TITLE);

            actionBar.setLogo(R.drawable.ic_icon);
            actionBar.setTitle(resId);
        }
    }

    public void setScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true);
            setShowWhenLocked(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            keyguardManager.requestDismissKeyguard(this, null);
        }
        else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    /**
     * Broadcast listener that listens for {@link #ACTION_EXIT} and then finishes this <code>Activity</code>
     */
    class ExitActionListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    }
}
