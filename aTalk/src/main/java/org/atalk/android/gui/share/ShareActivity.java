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

package org.atalk.android.gui.share;

import android.app.ActionBar;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.atalk.android.R;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.menu.ExitMenuActivity;
import org.atalk.android.gui.util.DepthPageTransformer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

import androidx.fragment.app.*;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * ShareActivity is defined as SingleTask, to avoid multiple instances being created if user does not exit
 * this activity before start another sharing.
 *
 * ShareActivity provides multiple contacts sharing. However this requires aTalk does not have any
 * chatFragment current in active open state. Otherwise Android OS destroys this activity on first
 * contact sharing; and multiple contacts sharing is no possible.
 *
 * @author Eng Chong Meng
 */
public class ShareActivity extends ExitMenuActivity
{
    /**
     * The number of pages (wizard steps) to show.
     */
    private static final int NUM_PAGES = 2;

    /**
     * A reference of the share object
     */
    private static Share mShare;

    /**
     * mCategories is used in aTalk to sore msgContent if multiple type sharing is requested by user
     */
    private static class Share
    {
        Set<String> mCategories;
        ArrayList<Uri> uris = new ArrayList<>();
        public String action;
        public String type;
        public String text;

        public void clear()
        {
            mCategories = null;
            uris = new ArrayList<>();
            action = null;
            type = null;
            text = null;
        }
    }

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
        setContentView(R.layout.sharewith_view);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setCustomView(R.layout.action_bar);

            TextView tv = findViewById(R.id.actionBarTitle);
            tv.setText(R.string.APPLICATION_NAME);

            tv = findViewById(R.id.actionBarStatus);
            tv.setText(R.string.service_gui_SHARE);
            actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.color_bg_share)));
        }

        /*
         * The pager widget, which handles animation and allows swiping horizontally to access previous
         * and next wizard steps.
         */
        ViewPager mPager = findViewById(R.id.shareViewPager);
        PagerAdapter mPagerAdapter = new SharePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageTransformer(true, new DepthPageTransformer());
        mShare = new Share();

        handleIntent(getIntent());
    }

    /**
     * Called when new <tt>Intent</tt> is received(this <tt>Activity</tt> is launched in <tt>singleTask</tt> mode.
     *
     * @param intent new <tt>Intent</tt> data.
     */
    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /**
     * Decides what should be displayed based on supplied <tt>Intent</tt> and instance state.
     *
     * @param intent <tt>Activity</tt> <tt>Intent</tt>.
     * @param instanceState <tt>Activity</tt> instance state.
     */
    private void handleIntent(Intent intent)
    {
        super.onStart();
        if (intent == null) {
            return;
        }
        final String type = intent.getType();
        final String action = intent.getAction();

        mShare.clear();
        mShare.type = type;
        mShare.action = action;

        if (Intent.ACTION_SEND.equals(action)) {
            final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            if (type != null && uri != null) {
                mShare.uris.clear();
                mShare.uris.add(uri);
            }
            else {
                mShare.text = text;
            }
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            final ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            mShare.uris = (uris == null) ? new ArrayList<>() : uris;

            // aTalk send extra_text in categories in this case
            mShare.mCategories = intent.getCategories();
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mShare.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.share_with, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.menu_done) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Retrieve the earlier saved Share object parameters for use with chatIntent
     *
     * @param shareIntent Sharable Intent
     * @return a reference copy of the update chatIntent
     */
    public static Intent getShareIntent(Intent shareIntent)
    {
        if (mShare == null) {
            return null;
        }

        shareIntent.setAction(mShare.action);
        shareIntent.setType(mShare.type);

        if (Intent.ACTION_SEND.equals(mShare.action)) {
            if (!mShare.uris.isEmpty()) {
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.putExtra(Intent.EXTRA_STREAM, mShare.uris.get(0));
            }
            else {
                shareIntent.putExtra(Intent.EXTRA_TEXT, mShare.text);
            }
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(mShare.action)) {
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, mShare.uris);

            // aTalk has the extra_text in Intent.category in this case
            if (mShare.mCategories != null)
                shareIntent.addCategory(mShare.mCategories.toString());
        }
        return shareIntent;
    }

    /**
     * A simple pager adapter that represents 3 Screen Slide PageFragment objects, in sequence.
     */
    private static class SharePagerAdapter extends FragmentPagerAdapter
    {
        private SharePagerAdapter(FragmentManager fm)
        {
            // Must use BEHAVIOR_SET_USER_VISIBLE_HINT to see conference list on first slide to conference view
            // super(fm, BEHAVIOR_SET_USER_VISIBLE_HINT); not valid anymore after change to BaseChatRoomListAdapter
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NotNull
        @Override
        public Fragment getItem(int position)
        {
            if (position == 0) {
                return new ContactListFragment();
            }
            else {
                return new ChatRoomListFragment();
            }
        }

        @Override
        public int getCount()
        {
            return NUM_PAGES;
        }
    }
}
