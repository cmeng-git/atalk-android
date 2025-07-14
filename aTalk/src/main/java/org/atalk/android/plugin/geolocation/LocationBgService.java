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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import timber.log.Timber;

/**
 * GeoLocation service that retrieve current location update, and broadcast to the intended receiver.
 * Use the best available Location provider on the device in onCreate()
 *
 * @author Eng Chong Meng
 */
public class LocationBgService extends Service implements LocationListenerCompat {
    private static final long NO_FALLBACK = 0;
    private LocationManager mLocationManager;
    private String mProvider;
    private Handler mServiceHandler;

    private int mLocationMode;
    private boolean mAddressRequest;
    private long mLocationUpdateMinTime = 0;
    private float mLocationUpdateMinDistance = 0.0f;
    private long fallBackToLastLocationTime;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mServiceHandler = new Handler(Looper.getMainLooper());

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mProvider = mLocationManager.getBestProvider(criteria, true);
        // FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        Timber.d("Best location provider selected: %s", mProvider);

    }

    @SuppressWarnings("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String actionIntent = intent.getAction();
        // action not defined on gps service first startup
        if (actionIntent == null) {
            return START_NOT_STICKY;
        }

        Timber.d("Location background service start command %s", actionIntent);
        if (actionIntent.equals(GeoConstants.ACTION_LOCATION_FETCH_START)) {
            GeoLocationRequest geoLocationRequest = IntentCompat.getParcelableExtra(intent, GeoIntentKey.LOCATION_REQUEST, GeoLocationRequest.class);
            if (geoLocationRequest != null) {
                mLocationMode = geoLocationRequest.getLocationFetchMode();
                mAddressRequest = geoLocationRequest.getAddressRequest();
                mLocationUpdateMinTime = geoLocationRequest.getLocationUpdateMinTime();
                mLocationUpdateMinDistance = geoLocationRequest.getLocationUpdateMinDistance();
                fallBackToLastLocationTime = geoLocationRequest.getFallBackToLastLocationTime();
                requestLocationUpdates();
            }
        }
        else if (actionIntent.equals(GeoConstants.ACTION_LOCATION_FETCH_STOP)) {
            stopLocationService();
        }

        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY;
    }

    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdates() {
        Timber.i("Requesting location updates");
        startFallbackToLastLocationTimer();

        // Use higher accuracy location fix for SINGLE_FIX request
        // int quality = (GeoConstants.SINGLE_FIX == mLocationMode) ? LocationRequestCompat.QUALITY_HIGH_ACCURACY :
        //        LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY;
        try {
            LocationRequestCompat locationRequest = new LocationRequestCompat.Builder(mLocationUpdateMinTime)
                    .setMinUpdateIntervalMillis(mLocationUpdateMinTime)
                    .setMinUpdateDistanceMeters(mLocationUpdateMinDistance)
                    .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                    .build();
            LocationManagerCompat.requestLocationUpdates(mLocationManager, mProvider, locationRequest, this, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Timber.e("Lost location permission. Could not request updates:%s", unlikely.getMessage());
        } catch (Throwable ex) {
            Timber.e("Unable to attach listener for location provider %s; check permissions? %s", mProvider, ex.getMessage());
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startFallbackToLastLocationTimer() {
        if (fallBackToLastLocationTime != NO_FALLBACK) {
            mServiceHandler.removeCallbacksAndMessages(null);
            mServiceHandler.postDelayed(this::getLastLocation, fallBackToLastLocationTime);
        }
    }

    private void getLastLocation() {
        try {
            LocationManagerCompat.getCurrentLocation(mLocationManager, mProvider, (CancellationSignal) null,
                    Runnable::run, location -> {
                        if (location != null) {
                            Timber.d("Fallback location received: %s", location);
                            onLocationChanged(location);
                        }
                    });
        } catch (SecurityException unlikely) {
            Timber.e(unlikely, "Lost location permission.");
        }
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    private void stopLocationService() {
        if (mServiceHandler != null)
            mServiceHandler.removeCallbacksAndMessages(null);

        if (mLocationManager != null) {
            // Do not set mLocationManager=null, startService immediately after softSelf will not execute onCreate()
            try {
                mLocationManager.removeUpdates(this);
            } catch (Throwable ex) {
                Timber.w("Unable to de-attach location listener: %s", ex.getMessage());
            }
        }
        stopSelf();
        // Timber.d("Stop Location Manager background service");
    }

    @Override
    public void onLocationChanged(Location location) {
        // Timber.d("New location received: %s", location);
        if (location != null) {
            // force to a certain location for testing
            // ocation.setLatitude(34.687274);
            // location.setLongitude(135.525453);
            // location.setAltitude(12.023f);

            GeoPreferenceUtil.getInstance(this).saveLastKnownLocation(location);
            String locAddress = null;
            if (mAddressRequest) {
                locAddress = getLocationAddress(location);
            }

            // Notify anyone listening for broadcasts about the new location.
            Intent intent = new Intent();
            intent.setAction(GeoConstants.INTENT_LOCATION_RECEIVED);
            intent.putExtra(GeoIntentKey.LOCATION, location);
            intent.putExtra(GeoIntentKey.ADDRESS, locAddress);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        else {
            Intent intent = new Intent();
            intent.setAction(GeoConstants.INTENT_NO_LOCATION_RECEIVED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }

        mServiceHandler.removeCallbacksAndMessages(null);
        if (mLocationMode == GeoConstants.SINGLE_FIX) {
            stopLocationService();
        }
    }

    /**
     * To get address location from coordinates
     *
     * @param loc location from which the address is being retrieved
     *
     * @return the Address
     */
    private String getLocationAddress(Location loc) {
        String locAddress = "No service available or no address found";
        Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
        List<Address> addresses;
        try {
            addresses = gcd.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            ArrayList<String> addressFragments = new ArrayList<>();
            if ((addresses != null) && (addresses.size() > 0)) {
                Address address = addresses.get(0);
                // Fetch the address lines using getAddressLine, concatenate them, and send them to the thread.
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    addressFragments.add(address.getAddressLine(i));
                }
                locAddress = TextUtils.join(" \n", addressFragments);
            }
        } catch (IllegalArgumentException | IOException e) {
            Timber.e("Get location address: %s", e.getMessage());
        }
        return locAddress;
    }
}