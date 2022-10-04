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

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.SvpApi;

import java.util.ArrayList;
import java.util.List;

/**
 * The <code>SvpApiImpl</code> working in conjunction with ChatFragment to provide street map view support.
 * An implementation API for F-Droid release
 *
 * @author Eng Chong Meng
 */
public class SvpApiImpl implements SvpApi
{
    /**
     * Perform osMap street map view when user click the show map button
     */
    @Override
    public void onSVPClick(Activity activity, double[] dblLocation)
    {
        Location mLocation = toLocation(dblLocation);

        Intent intent = new Intent(activity, OsmActivity.class);
        intent.putExtra(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.ZERO_FIX);
        intent.putExtra(GeoIntentKey.LOCATION, mLocation);
        intent.putExtra(GeoIntentKey.LOCATION_LIST, new ArrayList<Location>());
        activity.startActivity(intent);
    }

    /**
     * Perform osMap street map view followMe when user long click the show map button in chatFragment.
     *
     * @param dblLocations List of double[] values containing Latitude, Longitude and Altitude
     */
    @Override

    public void onSVPLongClick(Activity activity, List<double[]> dblLocations)
    {
        ArrayList<Location> locations = new ArrayList<>();
        for (double[] entry : dblLocations) {
            locations.add(toLocation(entry));
        }

        // *** for testing only **** //
        // double delta = 0;
        // for (int i = 0; i < 100; i++) {
        //     delta += 0.001;
        //     Location location = toLocation(dblLocations.get(0));
        //     location.setLatitude(location.getLatitude() + delta);
        //     location.setLongitude(location.getLongitude() - delta);
        //     locations.add(location);
        // }

        Intent intent = new Intent(activity, OsmActivity.class);
        intent.putExtra(GeoIntentKey.LOCATION_FETCH_MODE, GeoConstants.ZERO_FIX);
        intent.putExtra(GeoIntentKey.LOCATION, toLocation(dblLocations.get(0)));
        intent.putExtra(GeoIntentKey.LOCATION_LIST, locations);
        activity.startActivity(intent);
    }

    /**
     * Animate to the new given location in osmMap street view if active;
     * call by chatFragment when a new location is received.
     *
     * @param mSVP OsmActivity
     * @param dblLocation: double[] value containing Latitude, Longitude and Altitude
     * @return  OsmActivity, update if any
     */
    @Override
    public Object svpHandler(Object mSVP, double[] dblLocation)
    {
        if (mSVP == null) {
            Activity currentActivity = aTalkApp.getCurrentActivity();
            if (currentActivity != null) {
                if (currentActivity instanceof OsmActivity) {
                    mSVP = currentActivity;
                }
            }
        }
        if (mSVP != null) {
            ((OsmActivity) mSVP).showLocation(toLocation(dblLocation));
        }
        return mSVP;
    }

    /**
     * Covert double[] to Location
     *
     * @param dblLocation double[] value containing Latitude, Longitude and Altitude
     * @return Location
     */
    private Location toLocation(double[] dblLocation) {
        Location mLocation = new Location(LocationManager.GPS_PROVIDER);
        mLocation.setLatitude(dblLocation[0]);
        mLocation.setLongitude(dblLocation[1]);
        mLocation.setAltitude(dblLocation[2]);

        return mLocation;
    }
}
