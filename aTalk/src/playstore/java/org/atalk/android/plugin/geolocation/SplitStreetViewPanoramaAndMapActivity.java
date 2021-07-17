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

import android.hardware.*;
import android.location.Location;
import android.os.Bundle;
import android.view.Surface;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.StreetViewPanorama.OnStreetViewPanoramaChangeListener;
import com.google.android.gms.maps.model.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.osgi.OSGiActivity;

import java.util.ArrayList;

import timber.log.Timber;

import static org.atalk.android.R.id.map;

/**
 * This shows how to create a simple activity with streetView and a map
 */
public class SplitStreetViewPanoramaAndMapActivity extends OSGiActivity
        implements OnMarkerDragListener, OnStreetViewPanoramaChangeListener, SensorEventListener
{
    public static final String MARKER_POSITION_KEY = "MarkerPosition";
    public static final String MARKER_LIST = "MarkerList";

    private Marker mMarker;
    private StreetViewPanorama mStreetViewPanorama;
    private GoogleMap mMap;

    private SensorManager mSensorManager;
    private double mAngle;
    private ArrayList<LatLng> markerList = new ArrayList<>();
    private Thread mThread = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.split_street_view_panorama_and_map);

        final LatLng markerPosition;
        if (savedInstanceState == null) {
            markerPosition = getIntent().getParcelableExtra(MARKER_POSITION_KEY);
            if (getIntent().hasExtra(MARKER_LIST))
                markerList = getIntent().getParcelableArrayListExtra(MARKER_LIST);
        }
        else {
            markerPosition = savedInstanceState.getParcelable(MARKER_POSITION_KEY);
            markerList = savedInstanceState.getParcelableArrayList(MARKER_LIST);
        }

        SupportStreetViewPanoramaFragment streetViewPanoramaFragment =
                (SupportStreetViewPanoramaFragment) getSupportFragmentManager().findFragmentById(R.id.streetviewpanorama);
        streetViewPanoramaFragment.getStreetViewPanoramaAsync(
                panorama -> {
                    mStreetViewPanorama = panorama;
                    mStreetViewPanorama.setOnStreetViewPanoramaChangeListener(
                            SplitStreetViewPanoramaAndMapActivity.this);
                    // Only need to set the position once as the streetView fragment will
                    // maintain its state.
                    mStreetViewPanorama.setPosition(markerPosition);
                });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(map);
        mapFragment.getMapAsync(map -> {
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
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putParcelable(MARKER_POSITION_KEY, mMarker.getPosition());
        outState.putParcelableArrayList(MARKER_LIST, markerList);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        mSensorManager = aTalkApp.getSensorManager();
        if (mSensorManager != null)
            mSensorManager.registerListener(this,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                    SensorManager.SENSOR_DELAY_GAME);
        aTalkApp.setCurrentActivity(this);
    }

    @Override
    protected void onPause()
    {
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
    public void onStreetViewPanoramaChange(StreetViewPanoramaLocation location)
    {
        if (location != null) {
            mMarker.setPosition(location.position);
        }
    }

    @Override
    public void onMarkerDragStart(Marker marker)
    {
    }

    @Override
    public void onMarkerDragEnd(Marker marker)
    {
        mStreetViewPanorama.setPosition(marker.getPosition(), 150);
    }

    @Override
    public void onMarkerDrag(Marker marker)
    {
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
    public void onSensorChanged(SensorEvent event)
    {
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            float bearing = Math.round(event.values[0]);

            // Realign bearing direction if system is in landscape mode
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            if (rotation == Surface.ROTATION_90)
                bearing += 90.0f;
            else if (rotation == Surface.ROTATION_270)
                bearing -= 90.0f;

            if (Math.abs(bearing - mAngle) > 1.0) {
                updateMapCamera(bearing);
                updateSVPCamera(bearing);
                mAngle = bearing;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
    }

    /**
     * Rotate the map camera to the actual bearing direction from user view point
     *
     * @param bearing the bearing direction in angle deg
     */
    private void updateMapCamera(float bearing)
    {
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
    private void updateSVPCamera(float bearing)
    {
        if (mStreetViewPanorama != null) {
            StreetViewPanoramaCamera previous = mStreetViewPanorama.getPanoramaCamera();

            StreetViewPanoramaCamera camera = new StreetViewPanoramaCamera.Builder(previous)
                    .bearing(bearing)
                    .build();

            mStreetViewPanorama.animateTo(camera, 0);
        }
    }

    public void onLocationChanged(Location location)
    {
        onLocationChanged(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    /**
     * Move the marker to the location mLatLng for both the street and map view
     *
     * @param mLatLng the new LatTng location to move to
     */
    public void onLocationChanged(LatLng mLatLng)
    {
        if (mMap != null) {
            LatLng markerPosition = new LatLng(mLatLng.latitude, mLatLng.longitude);
            mMarker.setPosition(markerPosition);
            mStreetViewPanorama.setPosition(markerPosition);
            mMap.moveCamera(CameraUpdateFactory.newLatLng(markerPosition));
        }
    }

    /**
     * Animate the location path given in an array in 2 second interval
     *
     * @param mList the ArrayList<LatLng>
     */
    private void startLocationUpdate(final ArrayList<LatLng> mList)
    {
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
