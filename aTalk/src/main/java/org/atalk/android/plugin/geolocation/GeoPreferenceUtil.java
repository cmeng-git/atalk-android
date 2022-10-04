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

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.text.TextUtils;

/**
 * Class implementation to save/retrieve last known GeoLocation
 *
 * @author Eng Chong Meng
 */
public class GeoPreferenceUtil
{
    private static final String LAST_KNOWN_LOCATION = "last_known_location";
    private static final String GPS = "GPS";
    private static final String PREF_NAME = "geolocation";
    private final SharedPreferences mPreferences;
    private static GeoPreferenceUtil instance;

    public static GeoPreferenceUtil getInstance(Context context) {
        if(instance ==null) {
            instance = new GeoPreferenceUtil(context.getApplicationContext());
        }
        return instance;
    }

    private GeoPreferenceUtil(Context context) {
        mPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public Location getLastKnownLocation() {
        String locationString = mPreferences.getString(LAST_KNOWN_LOCATION, null);
        if(TextUtils.isEmpty(locationString))
            return null;
        else {
            String[] latLong = locationString.split(",");
            Location location = new Location(GPS);
            location.setLatitude(Double.parseDouble(latLong[0]));
            location.setLongitude(Double.parseDouble(latLong[1]));
            location.setAltitude((latLong.length == 3)? Double.parseDouble(latLong[2]) : 0.0f);
            return location;
        }
    }
    public void saveLastKnownLocation(Location location) {
        String geoLocation = location.getLatitude() + "," + location.getLongitude() + "," + location.getAltitude();
        mPreferences.edit().putString(LAST_KNOWN_LOCATION, geoLocation).apply();
    }
}