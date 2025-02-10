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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.GeomagneticField;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.core.location.LocationListenerCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.location.LocationRequestCompat;

import java.util.ArrayList;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.IOrientationConsumer;
import org.osmdroid.views.overlay.compass.IOrientationProvider;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import timber.log.Timber;

/**
 * OSM fragment supporting various osmdroid overlays i.e. followMe, compass, scaleBar, rotation etc
 * Based off the osmdroid examples
 *
 * @author Eng Chong Meng
 * @author Alex O'Ree
 */
public class OsmFragment extends BaseFragment implements LocationListenerCompat, IOrientationConsumer {
    private static final int MENU_LAST_ID = Menu.FIRST;

    private MyLocationNewOverlay mLocationOverlay;
    private CompassOverlay mCompassOverlay;
    private ScaleBarOverlay mScaleBarOverlay;
    private RotationGestureOverlay mRotationGestureOverlay;

    private LocationManager mLocationManager;
    private LocationRequestCompat mLocationRequest;
    private String mProvider;
    private MapView mMapView;
    private Marker mMarker;

    public OsmActivity mActivity;
    private Thread mThread = null;
    private Location mLocation = null;
    private ArrayList<Location> mLocations = null;
    private int mLocationFetchMode = GeoConstants.FOLLOW_ME_FIX;

    protected ImageButton btCenterMap;
    protected ImageButton btFollowMe;

    private IOrientationProvider mOrientationProvider = null;
    private static final long mLocationUpdateMinTime = 1000L;      // mS
    private static final float mLocationUpdateMinDistance = 1.0f;  // meters
    private static final float gpsSpeedThreshold = 0.5f;           // m/s

    private int mDeviceOrientation = 0;
    private float gpsSpeed;
    private float lat = 0;
    private float lon = 0;
    private float alt = 0;
    private long timeOfFix = 0;
    private boolean mOrientationSupported = false;
    private boolean mHasBearing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (OsmActivity) getActivity();
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
        criteria.setPowerRequirement(Criteria.POWER_MEDIUM);

        mLocationManager = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);
        mProvider = mLocationManager.getBestProvider(criteria, true);

        // Disable all OSMap overlays menu items
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.osm_followme, null);
        mMapView = v.findViewById(R.id.mapview);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();

        GpsMyLocationProvider mGpsMyLocationProvider = new GpsMyLocationProvider(mActivity);
        mGpsMyLocationProvider.clearLocationSources();
        mGpsMyLocationProvider.addLocationSource(mProvider);
        mGpsMyLocationProvider.setLocationUpdateMinTime(mLocationUpdateMinTime);
        mGpsMyLocationProvider.setLocationUpdateMinDistance(mLocationUpdateMinDistance);

        Bitmap navIcon = BitmapFactory.decodeResource(mActivity.getResources(), R.drawable.map_navigation_icon);
        mLocationOverlay = new MyLocationNewOverlay(mGpsMyLocationProvider, mMapView);
        mLocationOverlay.setDirectionIcon(navIcon);
        mLocationOverlay.setDirectionAnchor(.5f, .63f);

        // orientation tracking - cannot reuse InternalCompassOrientationProvider from mCompassOverlay
        mCompassOverlay = new CompassOverlay(mActivity, new InternalCompassOrientationProvider(mActivity), mMapView);
        mOrientationProvider = new InternalCompassOrientationProvider(mActivity);

        mScaleBarOverlay = new ScaleBarOverlay(mMapView);
        mScaleBarOverlay.setCentred(true);
        mScaleBarOverlay.setScaleBarOffset(dm.widthPixels / 2, (int) (15 * dm.density));
        mScaleBarOverlay.setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric);

        mRotationGestureOverlay = new RotationGestureOverlay(mMapView);
        mRotationGestureOverlay.setEnabled(true);
        mMarker = new Marker(mMapView);

        mMapView.getController().setZoom(16.0f);
        mMapView.setTilesScaledToDpi(true);
        mMapView.setMultiTouchControls(true);
        mMapView.setFlingEnabled(true);

        mMapView.getOverlays().add(mLocationOverlay);
        mMapView.getOverlays().add(mCompassOverlay);
        mMapView.getOverlays().add(mScaleBarOverlay);
        mMapView.getOverlays().add(new CopyrightOverlay(mActivity));
        mMapView.getController();

        Bundle args = getArguments();
        if (args != null) {
            mLocationFetchMode = args.getInt(GeoIntentKey.LOCATION_FETCH_MODE);
            mLocation = args.getParcelable(GeoIntentKey.LOCATION);
            mLocations = args.getParcelableArrayList(GeoIntentKey.LOCATION_LIST);
        }

        btCenterMap = view.findViewById(R.id.ic_center_map);
        btCenterMap.setOnClickListener(v -> {
            if (mLocation != null) {
                mMapView.getController().animateTo(new GeoPoint(mLocation));
            }
        });

        btFollowMe = view.findViewById(R.id.ic_follow_me);
        btFollowMe.setOnClickListener(v -> updateFollowMe(!mLocationOverlay.isFollowLocationEnabled()));
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.onResume();
        }

        try {
            mLocationRequest = new LocationRequestCompat.Builder(mLocationUpdateMinTime)
                    .setMinUpdateIntervalMillis(mLocationUpdateMinTime)
                    .setMinUpdateDistanceMeters(mLocationUpdateMinDistance)
                    .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
                    .build();
            LocationManagerCompat.requestLocationUpdates(mLocationManager, mProvider, mLocationRequest, this, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Timber.e("Lost location permission. Could not request updates:%s", unlikely.getMessage());
        } catch (Throwable ex) {
            Timber.e("Unable to attach listener for location provider %s; check permissions? %s", mProvider, ex.getMessage());
        }

        mOrientationSupported = mOrientationProvider.startOrientationProvider(this);
        mLocationOverlay.enableMyLocation();
        mCompassOverlay.enableCompass();
        mScaleBarOverlay.enableScaleBar();

        // Always locks the current screen orientation when showing map.
        setDeviceOrientation();

        // Enable followMe if from user selected location to show
        boolean isFollowMe = (GeoConstants.ZERO_FIX != mLocationFetchMode);
        updateFollowMe(isFollowMe);
        if (!isFollowMe) {
            mLocationManager.removeUpdates(this);
            if (mLocation != null) {
                mMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mMapView.getOverlays().add(mMarker);
                showLocation(mLocation);
            }
            if (mLocations != null && !mLocations.isEmpty()) {
                startLocationFollowMe(mLocations);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMapView != null) {
            mMapView.onPause();
        }
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }

        try {
            mLocationManager.removeUpdates(this);
        } catch (Exception ex) {
            Timber.d("Unexpected exception: %s", ex.getMessage());
        }

        mLocationOverlay.disableMyLocation();
        mCompassOverlay.disableCompass();
        mScaleBarOverlay.disableScaleBar();

        // unlock the orientation
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (mOrientationProvider != null) {
            mOrientationProvider.stopOrientationProvider();
        }
        updateFollowMe(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMapView != null)
            mMapView.onDetach();
        mMapView = null;

        mLocationManager = null;
        mLocation = null;
        mLocationOverlay = null;
        mCompassOverlay = null;
        mScaleBarOverlay = null;
        mRotationGestureOverlay = null;
        mOrientationProvider.destroy();
        mOrientationProvider = null;
        btCenterMap = null;
        btFollowMe = null;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        try {
            mMapView.getOverlayManager().onCreateOptionsMenu(menu, MENU_LAST_ID, mMapView);
        } catch (NullPointerException npe) {
            // can happen during CI tests and very rapid fragment switching
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        mMapView.getOverlayManager().onPrepareOptionsMenu(menu, MENU_LAST_ID, mMapView);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return mMapView.getOverlayManager().onOptionsItemSelected(item, MENU_LAST_ID, mMapView);
    }

    public void invalidateMapView() {
        mMapView.invalidate();
    }

    @SuppressLint("MissingPermission")
    private void updateFollowMe(boolean isFollowMe) {
        if (isFollowMe) {
            mLocationOverlay.enableFollowLocation();
            btFollowMe.setImageResource(R.drawable.ic_follow_me_on);
            if (GeoConstants.ZERO_FIX != mLocationFetchMode) {
                mLocationFetchMode = GeoConstants.FOLLOW_ME_FIX;
                LocationManagerCompat.requestLocationUpdates(mLocationManager, mProvider, mLocationRequest, this, Looper.myLooper());
            }
        }
        else {
            mLocationOverlay.disableFollowLocation();
            btFollowMe.setImageResource(R.drawable.ic_follow_me);
        }
    }

    private void setDeviceOrientation() {
        int orientation;
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                mDeviceOrientation = 0;
                break;

            case Surface.ROTATION_90:
                mDeviceOrientation = 90;
                orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;

            case Surface.ROTATION_180:
                mDeviceOrientation = 180;
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;

            default:
            case Surface.ROTATION_270:
                mDeviceOrientation = 270;
                orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
        }

        // Lock the device in current screen orientation
        mActivity.setRequestedOrientation(orientation);
    }

    /*
     * Adjusts the desired map rotation based on device orientation and compass-trueNorth/gps-bearing heading
     */
    private void setMapOrientation(float direction) {
        float t = (360 - direction - mDeviceOrientation);
        if (t < 0) {
            t += 360;
        }
        if (t > 360) {
            t -= 360;
        }

        // help smooth everything out
        t = (int) t;
        t = t / 5;
        t = (int) t;
        t = t * 5;
        mMapView.setMapOrientation(t);
    }

    // Note: on devices without a compass this never fires this...
    // Only use the compass bit if we aren't moving, since gps is more accurate when we are moving.
    // aTalk always uses Compass if available, for screen orientation alignment
    @Override
    public void onOrientationChanged(float orientationToMagneticNorth, IOrientationProvider source) {
        // if (gpsSpeed < gpsSpeedThreshold || !mHasBearing) {
        if (mOrientationSupported) {
            GeomagneticField gmField = new GeomagneticField(lat, lon, alt, timeOfFix);
            Float trueNorth = orientationToMagneticNorth + gmField.getDeclination();

            synchronized (trueNorth) {
                if (trueNorth > 360.0f) {
                    trueNorth = trueNorth - 360.0f;
                }
                setMapOrientation(trueNorth);
                // Timber.d("Bearing compass: %s (%s)", trueNorth, gpsSpeed);
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (GeoConstants.FOLLOW_ME_FIX == mLocationFetchMode) {
            mLocation = location;
        }

        lat = (float) location.getLatitude();
        lon = (float) location.getLongitude();
        alt = (float) location.getAltitude(); //meters
        timeOfFix = location.getTime();
        mHasBearing = location.hasBearing();
        gpsSpeed = location.getSpeed();

        // Let the compass take over if stationary and Orientation is supported
        // if (mHasBearing && (gpsSpeed >= gpsSpeedThreshold || !mOrientationSupported)) {
        if (!mOrientationSupported && mHasBearing) {
            float gpsBearing = location.getBearing();
            setMapOrientation(gpsBearing);
            // Timber.d("Bearing GPS: %s (%s)", gpsBearing, gpsSpeed);
        }
    }

    /**
     * Move the marker to the new user selected location on the street map view
     *
     * @param location the new location location to animate to
     */
    public void showLocation(Location location) {
        if (mMapView != null) {
            mLocation = location;
            mMapView.getController().animateTo(new GeoPoint(location));
            mLocationOverlay.onLocationChanged(mLocation, null);
            mMarker.setPosition(new GeoPoint(location));
            Timber.d("Animate to location: %s", location);
        }
    }

    /**
     * Animate the followMe with the given location arrayList at 2 second interval
     *
     * @param locations the ArrayList<Location>
     */
    public void startLocationFollowMe(final ArrayList<Location> locations) {
        mThread = new Thread(() -> {
            for (final Location xLocation : locations) {
                try {
                    Thread.sleep(2000);
                    runOnUiThread(() -> showLocation(xLocation));
                } catch (InterruptedException ex) {
                    break;
                } catch (Exception ex) {
                    Timber.e("Exception: %s", ex.getMessage());
                }
            }
        });
        mThread.start();
    }
}
