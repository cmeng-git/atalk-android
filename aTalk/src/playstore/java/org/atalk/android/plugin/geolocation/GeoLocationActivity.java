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
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import com.google.android.gms.maps.model.LatLng;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * GSM class for displaying map view
 *
 * @author Eng Chong Meng
 */
public class GeoLocationActivity extends GeoLocationBase implements OnMapsSdkInitializedCallback
{
    private static final int GOOGLE_PLAY_SERVICES_ERROR_DIALOG = 102;
    private GoogleApiAvailability googleApiAvailability;
    private SplitStreetViewPanoramaAndMapActivity mSVP = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        googleApiAvailability = GoogleApiAvailability.getInstance();
        // Not required to override; default to use LATEST
        // MapsInitializer.initialize(getApplicationContext(), MapsInitializer.Renderer.LATEST, this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        setMainTitle(R.string.geo_street_views_map);
        mSVP = null;
    }

    public void showStreetMap(Location location)
    {
        if (!isGoogleServiceAvailable()) {
            showGooglePlayServicesErrorDialog();
            return;
        }

        if (!mSVP_Started) {
            mSVP_Started = true;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            Intent intent = new Intent(this, SplitStreetViewPanoramaAndMapActivity.class);
            intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, latLng);
            startActivity(intent);
        }
        else if (!isGpsShare()){
            if (mSVP == null) {
                Activity currentActivity = aTalkApp.getCurrentActivity();
                if (currentActivity != null) {
                    if (currentActivity instanceof SplitStreetViewPanoramaAndMapActivity) {
                        mSVP = (SplitStreetViewPanoramaAndMapActivity) currentActivity;
                    }
                }
            }

            if (mSVP != null) {
                mSVP.onLocationChanged(new LatLng(location.getLatitude(), location.getLongitude()));
            }
        }
    }

    private boolean isGoogleServiceAvailable()
    {
        return googleApiAvailability.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;
    }

    private void showGooglePlayServicesErrorDialog()
    {
        int errorCode = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (googleApiAvailability.isUserResolvableError(errorCode))
            googleApiAvailability.getErrorDialog(this, errorCode, GOOGLE_PLAY_SERVICES_ERROR_DIALOG).show();
    }

    @Override
    public void onMapsSdkInitialized(@NonNull MapsInitializer.Renderer renderer)
    {
        switch (renderer) {
            case LATEST:
                Timber.d("GoogleMap: The latest version of the renderer is used.");
                break;
            case LEGACY:
                Timber.d("GoogleMap: The legacy version of the renderer is used.");
                break;
        }
    }
}