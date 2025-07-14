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
import android.location.Location;

import androidx.core.content.IntentCompat;

/**
 * GeoLocation broadcast receiver implementation.
 *
 * @author Eng Chong Meng
 */
public class LocationBroadcastReceiver extends BroadcastReceiver {
    private final GeoLocationListener geoLocationListener;

    public LocationBroadcastReceiver(GeoLocationListener geoLocationListener) {
        this.geoLocationListener = geoLocationListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (GeoConstants.INTENT_LOCATION_RECEIVED.equals(intent.getAction())) {
            Location location = IntentCompat.getParcelableExtra(intent, GeoIntentKey.LOCATION, Location.class);
            String locAddress = intent.getStringExtra(GeoIntentKey.ADDRESS);
            geoLocationListener.onLocationReceived(location, locAddress);
        } else if (GeoConstants.INTENT_NO_LOCATION_RECEIVED.equals(intent.getAction())) {
            geoLocationListener.onLocationReceivedNone();
        }
    }
}