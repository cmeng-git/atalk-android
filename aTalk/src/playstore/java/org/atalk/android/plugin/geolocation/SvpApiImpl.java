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

import java.util.ArrayList;
import java.util.List;

import com.google.android.gms.maps.model.LatLng;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.SvpApi;

/**
 * The <code>SvpApiImpl</code> working in conjunction with ChatFragment to provide street map view support.
 * Split Street View Panorama and Map for google playstore release.
 *
 * @author Eng Chong Meng
 */
public class SvpApiImpl implements SvpApi {
    /**
     * Perform google street map view when user click the show map button
     */
    @Override
    public void onSVPClick(Activity activity, double[] dblLocation) {
        Intent intent = new Intent(activity, SplitStreetViewPanoramaAndMapActivity.class);
        intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, toLatLng(dblLocation));
        activity.startActivity(intent);
    }

    /**
     * Perform google street and map view followMe when user long click the show map button in chatFragment.
     *
     * @param dblLocations List of double[] values containing Latitude, Longitude and Altitude
     */
    @Override
    public void onSVPLongClick(Activity activity, List<double[]> dblLocations) {
        ArrayList<LatLng> xLatLng = new ArrayList<>();
        for (double[] entry : dblLocations) {
            xLatLng.add(toLatLng(entry));
        }

        Intent intent = new Intent(activity, SplitStreetViewPanoramaAndMapActivity.class);
        intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, xLatLng.get(0));
        intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_LIST, xLatLng);
        activity.startActivity(intent);
    }

    /**
     * Animate to the new given location in SplitStreetViewPanoramaAndMap street view if active;
     * call by chatFragment when a new location is received.
     *
     * @param mSVP SplitStreetViewPanoramaAndMapActivity
     * @param dblLocation: double[] value containing Latitude, Longitude and Altitude
     *
     * @return SplitStreetViewPanoramaAndMapActivity, updated if any
     */
    @Override
    public Object svpHandler(Object mSVP, double[] dblLocation) {
        if (mSVP == null) {
            Activity currentActivity = aTalkApp.getCurrentActivity();
            if (currentActivity != null) {
                if (currentActivity instanceof SplitStreetViewPanoramaAndMapActivity) {
                    mSVP = currentActivity;
                }
            }
        }
        if (mSVP != null) {
            ((SplitStreetViewPanoramaAndMapActivity) mSVP).onLocationChanged(toLatLng(dblLocation));
        }
        return mSVP;
    }

    /**
     * Covert double[] to LatLng
     *
     * @param dblLocation double[] value containing Latitude, Longitude and Altitude
     *
     * @return LatLng
     */
    private LatLng toLatLng(double[] dblLocation) {
        return new LatLng(dblLocation[0], dblLocation[1]);
    }
}
