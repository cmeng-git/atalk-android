/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.plugin.geolocation;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.os.BundleCompat;

import java.util.ArrayList;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.StreetViewPanorama;
import com.google.android.gms.maps.StreetViewPanorama.OnStreetViewPanoramaChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.SupportStreetViewPanoramaFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.StreetViewPanoramaCamera;
import com.google.android.gms.maps.model.StreetViewPanoramaLocation;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * This shows how to create a simple activity with streetView and a map
 */
public class SplitStreetViewPanoramaAndMapActivity extends BaseActivity
        implements OnMarkerDragListener, OnStreetViewPanoramaChangeListener, SensorEventListener {
    public static final String MARKER_POSITION_KEY = "MarkerPosition";
    public static final String MARKER_LIST = "MarkerList";

    private Marker mMarker;
    private StreetViewPanorama mStreetViewPanorama;
    private GoogleMap mMap;

    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private float[] mGravity;
    private float[] mGeomagnetic;
    private double mAngle;

    private ArrayList<LatLng> markerList = new ArrayList<>();
    private LatLng markerPosition;
    private Thread mThread = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.split_street_view_panorama_and_map);

        mSensorManager = aTalkApp.getSensorManager();
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (savedInstanceState == null) {
            markerPosition = IntentCompat.getParcelableExtra(getIntent(), MARKER_POSITION_KEY, LatLng.class);
            if (getIntent().hasExtra(MARKER_LIST))
                markerList = IntentCompat.getParcelableArrayListExtra(getIntent(), MARKER_LIST, LatLng.class);
        }
        else {
            markerPosition = BundleCompat.getParcelable(savedInstanceState, MARKER_POSITION_KEY, LatLng.class);
            markerList = BundleCompat.getParcelableArrayList(savedInstanceState, MARKER_LIST, LatLng.class);
        }

        SupportStreetViewPanoramaFragment streetViewPanoramaFragment =
                (SupportStreetViewPanoramaFragment) getSupportFragmentManager().findFragmentById(R.id.street_view);
        if (streetViewPanoramaFragment != null)
            streetViewPanoramaFragment.getStreetViewPanoramaAsync(
                    panorama -> {
                        mStreetViewPanorama = panorama;
                        mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(this);
                        // Only need to set the position once as the streetView fragment will maintain its state.
                        mStreetViewPanorama.setPosition(markerPosition);
                    });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_view);
        if (mapFragment != null)
            mapFragment.getMapAsync(
                    map -> {
                        mMap = map;
                        map.setOnMarkerDragListener(SplitStreetViewPanoramaAndMapActivity.this);
                        // Creates a draggable marker. Long press to drag.
                        mMarker = map.addMarker(new MarkerOptions()
                                .position(markerPosition)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.pegman))
                                .draggable(true));

                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(markerPosition, 15.5f));
                        if (markerList != null && !markerList.isEmpty()) {
                            startLocationUpdate(markerList);
                        }
                    });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(MARKER_POSITION_KEY, mMarker.getPosition());
        outState.putParcelableArrayList(MARKER_LIST, markerList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSensorManager != null) {
            mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        aTalkApp.setCurrentActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSensorManager != null)
            mSensorManager.unregisterListener(this);
        // onPause is called before onSaveInstanceState
        // markerList = null;
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    @Override
    public void onStreetViewPanoramaChange(StreetViewPanoramaLocation location) {
        if (location != null) {
            mMarker.setPosition(location.position);
        }
    }

    @Override
    public void onMarkerDragStart(@NonNull Marker marker) {
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        mStreetViewPanorama.setPosition(marker.getPosition(), 150);
    }

    @Override
    public void onMarkerDrag(@NonNull Marker marker) {
    }

    /**
     * Extract the actual device orientation relative to earth magnetic north and make adjustment
     * for the display rotation when in landscape mode.
     * The orientation (change > 1.0deg> is used as bearing for positioning the StreetView and Map
     * camera to the actual direction from user view point
     *
     * @param event the Rotation Sensor triggered event
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;

        if (mGravity != null && mGeomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                // orientation contains: azimut, pitch and roll
                float azimut = orientation[0];
                float bearing = (float) Math.toDegrees(azimut);

                if (Math.abs(bearing - mAngle) > 2.0) {
                    // Realign bearing direction if system is in landscape mode
                    int rotation = getDisplayRotation();
                    if (rotation == Surface.ROTATION_90)
                        bearing += 90.0f;
                    else if (rotation == Surface.ROTATION_270)
                        bearing -= 90.0f;

                    updateMapCamera(bearing);
                    updateSVPCamera(bearing);
                    mAngle = bearing;
                }
            }
        }
//        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
//            float bearing = Math.round(event.values[0]);
//
//            // Realign bearing direction if system is in landscape mode
//            int rotation = getDisplayRotation;
//            if (rotation == Surface.ROTATION_90)
//                bearing += 90.0f;
//            else if (rotation == Surface.ROTATION_270)
//                bearing -= 90.0f;
//
//            if (Math.abs(bearing - mAngle) > 1.0) {
//                updateMapCamera(bearing);
//                updateSVPCamera(bearing);
//                mAngle = bearing;
//            }
//        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /**
     * Rotate the map camera to the actual bearing direction from user view point
     *
     * @param bearing the bearing direction in angle deg
     */
    private void updateMapCamera(float bearing) {
        if (mMap != null) {
            CameraPosition oldPos = mMap.getCameraPosition();

            CameraPosition pos = CameraPosition.builder(oldPos)
                    .bearing(bearing)
                    .build();
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
    }

    /**
     * Rotate the StreetView camera to the actual bearing direction from user view point
     *
     * @param bearing the bearing direction in angle deg
     */
    private void updateSVPCamera(float bearing) {
        if (mStreetViewPanorama != null) {
            StreetViewPanoramaCamera previous = mStreetViewPanorama.getPanoramaCamera();
            StreetViewPanoramaCamera camera = new StreetViewPanoramaCamera.Builder(previous)
                    .bearing(bearing)
                    .build();

            mStreetViewPanorama.animateTo(camera, 0);
        }
    }

    /**
     * Move the marker to the location mLatLng for both the street and map view
     *
     * @param latLng the new LatTng location to move to
     */
    public void onLocationChanged(LatLng latLng) {
        Timber.d("Update map view: %s", latLng);
        if (mMap != null) {
            LatLng markerPosition = new LatLng(latLng.latitude, latLng.longitude);
            mMarker.setPosition(markerPosition);
            mStreetViewPanorama.setPosition(markerPosition);
            mMap.moveCamera(CameraUpdateFactory.newLatLng(markerPosition));
        }
    }

    /**
     * Animate the location path given in an array at 2 second interval
     *
     * @param mList the ArrayList<LatLng>
     */
    private void startLocationUpdate(final ArrayList<LatLng> mList) {
        mThread = new Thread(() -> {
            for (final LatLng mLatLng : mList) {
                try {
                    Thread.sleep(2000);
                    runOnUiThread(() -> onLocationChanged(mLatLng));
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
