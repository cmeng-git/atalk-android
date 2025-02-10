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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

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
     * Override AppCompatActivity#onCreate() to support Theme setting
     * Must setTheme() before super.onCreate(), otherwise user selected Theme is not working
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Always call setTheme() method in base class and before super.onCreate()
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
        configureToolBar();
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Disable up arrow on home activity
            Class<?> homeActivity = aTalkApp.getHomeScreenActivityClass();
            if (this.getClass().equals(homeActivity)) {
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }
            ActionBarUtil.setTitle(this, getTitle());
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
}
