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

import org.atalk.android.aTalkApp;

/**
 * OSM class for displaying map view
 *
 * @author Eng Chong Meng
 */
public class GeoLocationActivity extends GeoLocationBase
{
    private OsmActivity mSVP = null;

    @Override
    protected void onResume()
    {
        super.onResume();
        mSVP = null;
    }

    public void showStreetMap(Location location)
    {
        if (!mSVP_Started) {
            mSVP_Started = true;
            Intent intent = new Intent(this, OsmActivity.class);
            intent.putExtra(GeoIntentKey.LOCATION_FETCH_MODE, mLocationFetchMode);
            intent.putExtra(GeoIntentKey.LOCATION, location);
            startActivity(intent);
        }
        else if (GeoConstants.ZERO_FIX == mLocationFetchMode) {
            if (mSVP == null) {
                Activity currentActivity = aTalkApp.getCurrentActivity();
                if (currentActivity != null) {
                    if (currentActivity instanceof OsmActivity) {
                        mSVP = (OsmActivity) currentActivity;
                    }
                }
            }
            if (mSVP != null) {
                mSVP.showLocation(location);
            }
        }
    }
}
