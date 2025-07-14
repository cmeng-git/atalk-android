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
package org.atalk.android.gui;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.actionbar.ActionBarStatusFragment;
import org.atalk.android.gui.call.CallHistoryFragment;
import org.atalk.android.gui.chat.chatsession.ChatSessionFragment;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.android.gui.util.DepthPageTransformer;
import org.atalk.android.gui.webview.WebViewFragment;
import org.atalk.service.osgi.OSGiService;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;

import de.cketti.library.changelog.ChangeLog;
import timber.log.Timber;

/**
 * The main <code>Activity</code> for aTalk application with pager slider for both contact and chatRoom list windows.
 *
 * @author Eng Chong Meng
 */
public class aTalk extends MainMenuActivity {
    /**
     * A map reference to find the FragmentPagerAdapter's fragment by a given position (Integer).
     */
    private static final Map<Integer, Fragment> mFragments = new HashMap<>();

    private static MainPagerAdapter mPagerAdapter;

    public final static int CL_FRAGMENT = 0;
    public final static int CRL_FRAGMENT = 1;
    public final static int CHAT_SESSION_FRAGMENT = 2;
    public final static int CALL_HISTORY_FRAGMENT = 3;
    public final static int WP_FRAGMENT = 4;

    /**
     * The number of pages (wizard steps) to show.
     */
    private static final int NUM_PAGES = 5;

    // android Permission Request Code
    public static final int PRC_CAMERA = 2000;
    public static final int PRC_GET_CONTACTS = 2001;
    public static final int PRC_RECORD_AUDIO = 2002;
    public static final int PRC_WRITE_EXTERNAL_STORAGE = 2003;

    public final static int Theme_Change = 1;
    public final static int Locale_Change = 2;
    public final static int HW_Codec_Change = 3;
    public static int mPrefChange = 0;
    private static final ArrayList<aTalk> mInstances = new ArrayList<>();

    /**
     * Variable caches instance state stored for example on rotate event to prevent from
     * recreating the contact list after rotation. It is passed as second argument of
     * {@link #handleIntent(Intent, Bundle)} when called from {@link #onNewIntent(Intent)}.
     */
    private Bundle mInstanceState;

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager2 mPager;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Checks if OSGi has been started and if not starts LauncherActivity which will restore this Activity from its Intent.
        if (postRestoreIntent()) {
            return;
        }

        setContentView(R.layout.main_view);
        if (savedInstanceState == null) {
            // Inserts ActionBar functionality
            getSupportFragmentManager().beginTransaction().add(new ActionBarStatusFragment(), "action_bar").commit();
        }

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = findViewById(R.id.mainViewPager);
        // The pager adapter, which provides the pages to the view pager widget.
        FragmentManager fragmentManager = getSupportFragmentManager();
        mPagerAdapter = new MainPagerAdapter(fragmentManager, getLifecycle());
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageTransformer(new DepthPageTransformer());

        handleIntent(getIntent(), savedInstanceState);
        getOnBackPressedDispatcher().addCallback(backPressedCallback);

        // allow 15 seconds for first launch login to complete before showing history log if the activity is still active
        ChangeLog cl = new ChangeLog(this);
        if (cl.isFirstRun()) {
            runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing()) {
                    cl.getLogDialog().show();
                }
            }, 15000));
        }
    }

    /**
     * Called when new <code>Intent</code> is received(this <code>Activity</code> is launched in <code>singleTask</code> mode.
     *
     * @param intent new <code>Intent</code> data.
     */
    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, mInstanceState);
    }

    /**
     * Decides what should be displayed based on supplied <code>Intent</code> and instance state.
     *
     * @param intent <code>Activity</code> <code>Intent</code>.
     * @param instanceState <code>Activity</code> instance state.
     */
    private void handleIntent(Intent intent, Bundle instanceState) {
        mInstances.add(this);

        String action = intent.getAction();
        if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            Timber.w("Search intent not handled for query: %s", query);
        }
        // Start aTalk with contactList UI for IM setup
        if (Intent.ACTION_SENDTO.equals(action)) {
            mPager.setCurrentItem(0);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mInstanceState = savedInstanceState;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mInstanceState = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * Need to restart whole app to make aTalkApp Locale change working
         * Note: Start aTalk Activity does not apply to aTalkApp Application class.
         */
        if (mPrefChange >= Locale_Change) {
            // Shutdown the OSGi service
            stopService(new Intent(this, OSGiService.class));

            PackageManager pm = getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                ComponentName componentName = intent.getComponent();
                Intent mainIntent = Intent.makeRestartActivityTask(componentName);
                mainIntent.setPackage(getPackageName());
                startActivity(mainIntent);
                Runtime.getRuntime().exit(0);
            }
        }
        // Re-init aTalk to refresh the newly user selected language and theme;
        // else the main option menu is not updated
        else if (mPrefChange == Theme_Change) {
            mPrefChange = 0;
            finish();
            startActivity(aTalk.class);
        }
    }

    /*
     * If the user is currently looking at the first page, allow the system to handle the
     * Back button. If Telephony fragment is shown, backKey closes the fragment only.
     * The call finish() on this activity and pops the back stack.
     */
    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mPager.getCurrentItem() == 0) {
                // mTelephony is not null if Telephony is closed by Cancel button.
                if (mTelephony != null) {
                    if (!mTelephony.closeFragment()) {
                        finish();
                    }
                    mTelephony = null;
                }
                else {
                    finish();
                }
            }
            else {
                // Otherwise, select the previous page.
                mPager.setCurrentItem(mPager.getCurrentItem() - 1);
            }
        }
    };

    /**
     * Called when an activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        synchronized (this) {
            BundleContext bundleContext = getBundleContext();
            if (bundleContext != null) {
                try {
                    stop(bundleContext);
                } catch (Throwable t) {
                    Timber.e(t, "Error stopping application:%s", t.getLocalizedMessage());
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                }
            }
        }
    }

    public static void setPrefChange(int change) {
        if (Locale_Change <= change)
            aTalkApp.showToastMessage(R.string.settings_restart_require);
        mPrefChange |= change;
    }

    /**
     * A simple pager adapter that represents Screen Slide PageFragment objects, in sequence.
     */
    private static class MainPagerAdapter extends FragmentStateAdapter {

        public MainPagerAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
            super(fragmentManager, lifecycle);
        }

        @NotNull
        @Override
        public Fragment createFragment(int position) {
            if (mFragments.get(position) != null)
                return Objects.requireNonNull(mFragments.get(position));

            switch (position) {
                case CL_FRAGMENT:
                    // The main pager view fragment containing the contact List
                    mFragments.put(CL_FRAGMENT, new ContactListFragment());
                    break;

                case CRL_FRAGMENT:
                    mFragments.put(CRL_FRAGMENT, new ChatRoomListFragment());
                    break;

                case CHAT_SESSION_FRAGMENT:
                    mFragments.put(CHAT_SESSION_FRAGMENT, new ChatSessionFragment());
                    break;

                case CALL_HISTORY_FRAGMENT:
                    mFragments.put(CALL_HISTORY_FRAGMENT, new CallHistoryFragment());
                    break;

                default: // if (position == WP_FRAGMENT){
                    mFragments.put(WP_FRAGMENT, new WebViewFragment());
                    break;
            }
            return Objects.requireNonNull(mFragments.get(position));
        }

        @Override
        public int getItemCount() {
            return NUM_PAGES;
        }
    }

    /**
     * Get the fragment reference for the given position in pager
     *
     * @param position position in the mFragmentTags
     *
     * @return the requested fragment for the specified position or null
     */
    public static Fragment getFragment(int position) {
        return mPagerAdapter.createFragment(position);
    }

    public static aTalk getInstance() {
        return mInstances.isEmpty() ? null : mInstances.get(0);
    }

    // =========== Runtime permission handlers ==========

    /**
     * Check the WRITE_EXTERNAL_STORAGE state; proceed to request for permission if requestPermission == true.
     * Require to support WRITE_EXTERNAL_STORAGE pending aTalk installed API version.
     *
     * @param callBack the requester activity to receive onRequestPermissionsResult()
     * @param requestPermission Proceed to request for the permission if was denied; check only if false
     *
     * @return the current WRITE_EXTERNAL_STORAGE permission state
     */
    public static boolean hasWriteStoragePermission(Activity callBack, boolean requestPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        return hasPermission(callBack, requestPermission, PRC_WRITE_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean hasPermission(Activity callBack, boolean requestPermission, int requestCode, String permission) {
        // Timber.d(new Exception(),"Callback: %s => %s (%s)", callBack, permission, requestPermission);
        // Do not use getInstance() as mInstances may be empty
        if (ActivityCompat.checkSelfPermission(aTalkApp.getInstance(), permission) != PackageManager.PERMISSION_GRANTED) {
            if (requestPermission && (callBack != null)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(callBack, permission)) {
                    ActivityCompat.requestPermissions(callBack, new String[]{permission}, requestCode);
                }
                else {
                    showHintMessage(requestCode, permission);
                }
            }
            return false;
        }
        return true;
    }

    // ========== Media call resource permission requests ==========
    public static boolean isMediaCallAllowed(boolean isVideoCall) {
        // Check for resource permission before continue
        if (hasPermission(getInstance(), true, PRC_RECORD_AUDIO, Manifest.permission.RECORD_AUDIO)) {
            return !isVideoCall || hasPermission(getInstance(), true, PRC_CAMERA, Manifest.permission.CAMERA);
        }
        return false;
    }

    public static void showHintMessage(int requestCode, String permission) {
        if (requestCode == PRC_RECORD_AUDIO) {
            aTalkApp.showToastMessage(R.string.mic_permission_denied_feedback);
        }
        else if (requestCode == PRC_CAMERA) {
            aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback);
        }
        else {
            aTalkApp.showToastMessage(aTalkApp.getResString(R.string.permission_rationale_title) + ": " + permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int @NotNull [] grantResults) {
        Timber.d("onRequestPermissionsResult: %s => %s", requestCode, permissions);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PRC_RECORD_AUDIO) {
            if ((grantResults.length != 0) && (PackageManager.PERMISSION_GRANTED != grantResults[0])) {
                aTalkApp.showToastMessage(R.string.mic_permission_denied_feedback);
            }
        }
        else if (requestCode == PRC_CAMERA) {
            if ((grantResults.length != 0) && (PackageManager.PERMISSION_GRANTED != grantResults[0])) {
                aTalkApp.showToastMessage(R.string.camera_permission_denied_feedback);
            }
        }
    }
}
