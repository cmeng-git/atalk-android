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
package org.atalk.android.plugin.geolocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.httputil.OkHttpUtils;
import org.osmdroid.config.Configuration;

import timber.log.Timber;

/**
 * Default osMap view activity ported from osmdroid.
 * Created by plusminus on 00:23:14 - 03.10.2008
 *
 * @author Manuel Stahl
 * @author Eng Chong Meng
 */
public class OsmActivity extends BaseActivity {
    private static final String MAP_FRAGMENT_TAG = "org.osmdroid.MAP_FRAGMENT_TAG";
    private OsmFragment osmFragment;
    private Location mLocation;
    private ArrayList<Location> mLocations = null;

    private int mLocationFetchMode = GeoConstants.ZERO_FIX;

    /**
     * The idea behind that is to force a MapView refresh when switching from offline to online.
     * If you don't do that, the map may display - when online - approximated tiles
     * - that were computed when offline
     * - that could be replaced by downloaded tiles
     * - but as the display is not refreshed there's no try to get better tiles
     *
     * @since 6.0
     */
    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                osmFragment.invalidateMapView();
            } catch (NullPointerException e) {
                // lazy handling of an improbable NPE
                Timber.e("Network receiver exception: %s", e.getMessage());
            }
        }
    };

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.osm_map_main);

        Configuration.getInstance().setUserAgentValue(OkHttpUtils.getUserAgent());

        // noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        if (savedInstanceState == null) {
            mLocationFetchMode = getIntent().getIntExtra(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.FOLLOW_ME_FIX);
            mLocation = getIntent().getParcelableExtra(GeoIntentKey.LOCATION);
            mLocations = getIntent().getParcelableArrayListExtra(GeoIntentKey.LOCATION_LIST);
        }
        else {
            mLocationFetchMode = savedInstanceState.getInt(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.FOLLOW_ME_FIX);
            mLocation = savedInstanceState.getParcelable(GeoIntentKey.LOCATION);
            mLocations = savedInstanceState.getParcelableArrayList(GeoIntentKey.LOCATION_LIST);
        }

        FragmentManager fm = getSupportFragmentManager();
        osmFragment = (OsmFragment) fm.findFragmentByTag(MAP_FRAGMENT_TAG);
        if (osmFragment == null) {
            osmFragment = new OsmFragment();
            Bundle args = new Bundle();
            args.putInt(GeoIntentKey.LOCATION_FETCH_MODE, mLocationFetchMode);
            args.putParcelable(GeoIntentKey.LOCATION, mLocation);
            args.putParcelableArrayList(GeoIntentKey.LOCATION_LIST, mLocations);
            osmFragment.setArguments(args);
            fm.beginTransaction().add(R.id.map_container, osmFragment, MAP_FRAGMENT_TAG).commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        aTalkApp.setCurrentActivity(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(GeoIntentKey.LOCATION_FETCH_MODE, mLocationFetchMode);
        outState.putParcelable(GeoIntentKey.LOCATION, mLocation);
        outState.putParcelableArrayList(GeoIntentKey.LOCATION_LIST, mLocations);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(networkReceiver);
        super.onDestroy();
    }

    /**
     * Move the marker to the new Location location on the street map view
     *
     * @param location the new location to animate to
     */
    public void showLocation(Location location) {
        if (osmFragment == null) {
            osmFragment = (OsmFragment) getSupportFragmentManager().findFragmentByTag(MAP_FRAGMENT_TAG);
        }

        if (osmFragment != null) {
            osmFragment.showLocation(location);
        }
    }
}
