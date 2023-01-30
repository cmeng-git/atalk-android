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

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;

import androidx.fragment.app.*;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.actionbar.ActionBarStatusFragment;
import org.atalk.android.gui.call.CallHistoryFragment;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.chatsession.ChatSessionFragment;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.android.gui.util.DepthPageTransformer;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.gui.webview.WebViewFragment;
import org.atalk.impl.neomedia.device.AndroidCameraSystem;
import org.atalk.persistance.migrations.MigrateDir;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;

import java.util.*;

import de.cketti.library.changelog.ChangeLog;
import timber.log.Timber;

/**
 * The main <code>Activity</code> for aTalk application with pager slider for both contact and chatRoom list windows.
 *
 * @author Eng Chong Meng
 */
public class aTalk extends MainMenuActivity implements EntityListHelper.TaskCompleted
{
    /**
     * A map reference to find the FragmentPagerAdapter's fragmentTag (String) by a given position (Integer)
     */
    private static final Map<Integer, String> mFragmentTags = new HashMap<>();

    private static FragmentManager mFragmentManager;

    public final static int CL_FRAGMENT = 0;
    public final static int CRL_FRAGMENT = 1;
    public final static int CHAT_SESSION_FRAGMENT = 2;
    public final static int CALL_HISTORY_FRAGMENT = 3;
    // public final static int WP_FRAGMENT = 4;

    /**
     * The action that will show contacts.
     */
    public static final String ACTION_SHOW_CONTACTS = "org.atalk.show_contacts";

    /**
     * The main pager view fragment containing the contact List
     */
    private ContactListFragment contactListFragment = null;

    /**
     * Variable caches instance state stored for example on rotate event to prevent from
     * recreating the contact list after rotation. It is passed as second argument of
     * {@link #handleIntent(Intent, Bundle)} when called from {@link #onNewIntent(Intent)}.
     */
    private Bundle mInstanceState;

    /**
     * The number of pages (wizard steps) to show.
     */
    private static final int NUM_PAGES = 5;

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager mPager;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
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
        mFragmentManager = getSupportFragmentManager();
        PagerAdapter mPagerAdapter = new MainPagerAdapter(mFragmentManager);
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageTransformer(true, new DepthPageTransformer());

        handleIntent(getIntent(), savedInstanceState);

        // allow 15 seconds for first launch login to complete before showing history log if the activity is still active
        ChangeLog cl = new ChangeLog(this);
        if (cl.isFirstRun()) {
            // Purge obsoleted aTalk avatarCache directory and contents 2.2.0 (2020/03/13): To be removed in future release.
            MigrateDir.purgeAvatarCache();
            // Update camera database, and remove mediaRecorder support (2021/11/05); not longer supported since API-23.
            AndroidCameraSystem.cleanMediaDB();

            runOnUiThread(() -> new Handler().postDelayed(() -> {
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
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        handleIntent(intent, mInstanceState);
    }

    /**
     * Decides what should be displayed based on supplied <code>Intent</code> and instance state.
     *
     * @param intent <code>Activity</code> <code>Intent</code>.
     * @param instanceState <code>Activity</code> instance state.
     */
    private void handleIntent(Intent intent, Bundle instanceState)
    {
        String action = intent.getAction();
        if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            Timber.w("Search intent not handled for query: %s", query);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        mInstanceState = savedInstanceState;
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        mInstanceState = null;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        /*
         * Must perform Locale change here instead of in aTalkApp (but must make reference to aTalkApp context);
         * else selected locale resources are only partially being updated.
         */
        String language = ConfigurationUtils.getProperty(getString(R.string.pref_key_locale), "");
        LocaleHelper.setLocale(language);
    }

    /*
     * If the user is currently looking at the first page, allow the system to handle the
     * Back button. This call finish() on this activity and pops the back stack.
     */
    @Override
    public void onBackPressed()
    {
        if (mPager.getCurrentItem() == 0) {
            super.onBackPressed();
        }
        else {
            // Otherwise, select the previous page.
            mPager.setCurrentItem(mPager.getCurrentItem() - 1);
        }
    }

    /**
     * Called when an activity is destroyed.
     */
    @Override
    protected void onDestroy()
    {
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

    /**
     * Handler for contactListFragment chatSessions on completed execution of
     *
     * @see EntityListHelper#eraseEntityChatHistory(Context, Object, List, List)
     * @see EntityListHelper#eraseAllEntityHistory(Context)
     */
    @Override
    public void onTaskComplete(Integer result, List<String> deletedUUIDs)
    {
        if (result == EntityListHelper.CURRENT_ENTITY) {
            MetaContact clickedContact = contactListFragment.getClickedContact();
            ChatPanel clickedChat = ChatSessionManager.getActiveChat(clickedContact);
            if (clickedChat != null) {
                contactListFragment.onCloseChat(clickedChat);
            }
        }
        else if (result == EntityListHelper.ALL_ENTITIES) {
            contactListFragment.onCloseAllChats();
        }
        else { // failed
            String errMsg = getString(R.string.service_gui_HISTORY_REMOVE_ERROR,
                    contactListFragment.getClickedContact().getDisplayName());
            aTalkApp.showToastMessage(errMsg);
        }
    }

    /**
     * A simple pager adapter that represents 3 Screen Slide PageFragment objects, in sequence.
     */
    private class MainPagerAdapter extends FragmentPagerAdapter
    {
        private MainPagerAdapter(FragmentManager fm)
        {
            // Must use BEHAVIOR_SET_USER_VISIBLE_HINT to see conference list on first slide to conference view
            // super(fm, BEHAVIOR_SET_USER_VISIBLE_HINT); not valid anymore after change to BaseChatRoomListAdapter
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NotNull
        @Override
        public Fragment getItem(int position)
        {
            switch (position) {
                case CL_FRAGMENT:
                    contactListFragment = new ContactListFragment();
                    return contactListFragment;

                case CRL_FRAGMENT:
                    return new ChatRoomListFragment();

                case CHAT_SESSION_FRAGMENT:
                    return new ChatSessionFragment();

                case CALL_HISTORY_FRAGMENT:
                    return new CallHistoryFragment();

                default: // if (position == WP_FRAGMENT){
                    return new WebViewFragment();
            }
        }

        /**
         * Save the reference of position to FragmentPagerAdapter fragmentTag in mFragmentTags
         *
         * @param container The viewGroup
         * @param position The pager position
         * @return Fragment object at the specific location
         */
        @NotNull
        @Override
        public Object instantiateItem(@NotNull ViewGroup container, int position)
        {
            Object obj = super.instantiateItem(container, position);
            if (obj instanceof Fragment) {
                Fragment f = (Fragment) obj;
                assert f.getTag() != null;
                mFragmentTags.put(position, f.getTag());
            }
            return obj;
        }

        @Override
        public int getCount()
        {
            return NUM_PAGES;
        }
    }

    /**
     * Get the fragment reference for the given position in pager
     *
     * @param position position in the mFragmentTags
     * @return the requested fragment for the specified postion or null
     */
    public static Fragment getFragment(int position)
    {
        String tag = mFragmentTags.get(position);
        return (mFragmentManager != null) ? mFragmentManager.findFragmentByTag(tag) : null;
    }
}
