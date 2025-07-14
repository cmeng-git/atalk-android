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

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.Locale;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * GeoLocationBase class for updating Location info and displaying map view if desired
 *
 * @author Eng Chong Meng
 */
public class GeoLocationBase extends BaseActivity implements View.OnClickListener,
        SeekBar.OnSeekBarChangeListener, GeoLocationListener {
    public static final String SHARE_ALLOW = "Share_Allow";

    private static LocationListener mCallBack;
    private static GeoLocationDelegate mGeoLocationDelegate;
    private Location mLocation = null;
    private ObjectAnimator mAnimation;
    private static boolean isGpsShare = false;
    private boolean isFollowMe;
    private boolean mShareAllow = false;
    private boolean mShowMap = false;
    protected boolean mSVP_Started;
    protected int mLocationFetchMode;

    private static int gpsMinDistance = 50;        // meters
    private static int sendTimeInterval = 60;      // seconds
    private final int gpsDistanceStep = 5;  // meters
    private final int timeIntervalStep = 10; // seconds

    private TextView mLatitudeTextView;
    private TextView mLongitudeTextView;
    private TextView mAltitudeTextView;
    private TextView mLocationAddressTextView;
    private SeekBar mSeekDistanceInterval;

    private Button mBtnSingleFix;
    private Button mBtnFollowMe;
    private CheckBox mBtnGpsShare;

    private boolean mDemo = false;
    private float delta = 0; // for demo

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setMainTitle(R.string.location);
        isFollowMe = (mGeoLocationDelegate != null);
        if (isFollowMe) {
            mGeoLocationDelegate.unregisterLocationBroadcastReceiver();
            mGeoLocationDelegate = null;
        }
        mGeoLocationDelegate = new GeoLocationDelegate(this, this);
        mGeoLocationDelegate.onCreate();

        if (savedInstanceState != null) {
            mShareAllow = savedInstanceState.getBoolean(SHARE_ALLOW);
        }
        else {
            mShareAllow = getIntent().getExtras().getBoolean(SHARE_ALLOW, false);
        }

        setContentView(R.layout.geo_location);
        mLatitudeTextView = findViewById(R.id.latitude_textview);
        mLongitudeTextView = findViewById(R.id.longitude_textview);
        mAltitudeTextView = findViewById(R.id.altitude_textview);
        mLocationAddressTextView = findViewById(R.id.locationAddress_textview);

        mBtnSingleFix = findViewById(R.id.btn_single_fix);
        mBtnSingleFix.setOnClickListener(this);
        mBtnFollowMe = findViewById(R.id.btn_follow_me);
        mBtnFollowMe.setText(String.format(getString(R.string.follow_me_start), gpsMinDistance, sendTimeInterval));
        mBtnFollowMe.setOnClickListener(this);

        mAnimation = ObjectAnimator.ofInt(mBtnFollowMe, "textColor", Color.GREEN, Color.BLACK);
        mAnimation.setDuration(1000);
        mAnimation.setEvaluator(new ArgbEvaluator());
        mAnimation.setRepeatCount(ValueAnimator.INFINITE);
        mAnimation.setRepeatMode(ValueAnimator.REVERSE);

        mBtnGpsShare = findViewById(R.id.gps_share);
        mBtnGpsShare.setEnabled(mShareAllow);
        mBtnGpsShare.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isGpsShare = isChecked;
        });
        mBtnGpsShare.setChecked(mShareAllow && isGpsShare);

        mSeekDistanceInterval = findViewById(R.id.seekDistanceInterval);
        mSeekDistanceInterval.setMax(100);
        mSeekDistanceInterval.setProgress(gpsMinDistance / gpsDistanceStep);
        mSeekDistanceInterval.setOnSeekBarChangeListener(this);

        SeekBar seekTimeInterval = findViewById(R.id.seekTimeInterval);
        seekTimeInterval.setMax(100);
        int progress = (sendTimeInterval - timeIntervalStep) / timeIntervalStep;
        if (progress < 0)
            progress = 0;
        seekTimeInterval.setProgress(progress);
        seekTimeInterval.setOnSeekBarChangeListener(this);

        // Long press for demo at 0m and 2S interval
        mBtnFollowMe.setOnLongClickListener(v -> {
            mDemo = true;
            mSeekDistanceInterval.setProgress(0);
            sendTimeInterval = 2;
            mBtnFollowMe.performClick();
            return true;
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHARE_ALLOW, mShareAllow);
    }

    @Override
    protected void onResume() {
        super.onResume();
        aTalkApp.setCurrentActivity(this);
        mLocation = null;
        mSVP_Started = false;
        mShowMap = false;
        mDemo = false;

        if (isFollowMe) {
            updateSendButton(false);
            mAnimation.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isFollowMe && (mGeoLocationDelegate != null)) {
            mGeoLocationDelegate.unregisterLocationBroadcastReceiver();
            mGeoLocationDelegate = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_single_fix:
                mLocationFetchMode = GeoConstants.SINGLE_FIX;
                if (isFollowMe) {
                    updateSendButton(true);
                    stopLocationUpdates();
                }
                mShowMap = true;
                GeoLocationRequest geoLocationRequest = new GeoLocationRequest.GeoLocationRequestBuilder()
                        .setLocationFetchMode(mLocationFetchMode)
                        .setAddressRequest(true)
                        .setLocationUpdateMinTime(0L)
                        .setLocationUpdateMinDistance(0.0f)
                        .setFallBackToLastLocationTime(3000)
                        .build();

                requestLocationUpdates(geoLocationRequest);
                break;

            case R.id.btn_follow_me:
                mLocationFetchMode = (mDemo) ? GeoConstants.ZERO_FIX : GeoConstants.FOLLOW_ME_FIX;
                if (isFollowMe) {
                    updateSendButton(true);
                    stopLocationUpdates();
                }
                else {
                    updateSendButton(false);
                    mShowMap = true;
                    geoLocationRequest = new GeoLocationRequest.GeoLocationRequestBuilder()
                            .setLocationFetchMode(mLocationFetchMode)
                            .setAddressRequest(true)
                            .setLocationUpdateMinTime(sendTimeInterval * 1000L)
                            .setLocationUpdateMinDistance(gpsMinDistance)
                            .setFallBackToLastLocationTime(sendTimeInterval * 500L)
                            .build();

                    requestLocationUpdates(geoLocationRequest);
                }
        }
    }

    private void updateSendButton(boolean followMe) {
        if (followMe) {
            isFollowMe = false;
            mBtnFollowMe.setText(getString(R.string.follow_me_start, gpsMinDistance, sendTimeInterval));
            mBtnFollowMe.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            mAnimation.end();
            mAnimation.cancel();
        }
        else {
            isFollowMe = true;
            mBtnFollowMe.setText(getString(R.string.follow_me_stop, gpsMinDistance, sendTimeInterval));
            mAnimation.start();
        }
    }

    public boolean isGpsShare() {
        return isFollowMe && isGpsShare;
    }

    @Override
    public void onLocationPermissionGranted() {
        showToast("Location permission granted");
    }

    @Override
    public void onLocationPermissionDenied() {
        showToast("Location permission denied");
    }

    public void onLocationReceived(Location location, String locAddress) {
        if (mDemo) {
            delta += 0.0001;
            location.setLatitude(location.getLatitude() + delta);
            location.setLongitude(location.getLongitude() - delta);
        }

        String mLatitude = String.valueOf(location.getLatitude());
        String mLongitude = String.valueOf(location.getLongitude());
        String mAltitude = String.format(Locale.US, "%.03fm", location.getAltitude());

        mLatitudeTextView.setText(mLatitude);
        mLongitudeTextView.setText(mLongitude);
        mAltitudeTextView.setText(mAltitude);
        mLocationAddressTextView.setText(locAddress);

        Timber.d("Update map needed: %s %s %s", isFollowMe,
                (mLocation != null) ? location.distanceTo(mLocation) : 0, location);
        // aTalkApp.showToastMessage("on Location Received: " + ((mLocation != null) ? location.distanceTo(mLocation) : 0) + "; " + location);
        mLocation = location;

        if (mBtnGpsShare.isChecked() && (mCallBack != null)) {
            mCallBack.onResult(location, locAddress);
        }
        if (mShowMap)
            showStreetMap(location);
    }

    /**
     * To be implemented by app if show streetMap is desired after a new Location is received.
     *
     * @param location at which the pointer is place and map centered
     */
    public void showStreetMap(Location location) {
    }

    @Override
    public void onLocationReceivedNone() {
        showToast("No location received");
    }

    @Override
    public void onLocationProviderEnabled() {
        showToast("Location services are now ON");
    }

    @Override
    public void onLocationProviderDisabled() {
        showToast("Location services are still Off");
    }

    /**
     * Notification that the progress level has changed. Clients can use the fromUser parameter
     * to distinguish user-initiated changes from those that occurred programmatically.
     *
     * @param seekBar The SeekBar whose progress has changed
     * @param progress The current progress level. This will be in the range 0..max where max
     * was set by {@link ProgressBar#setMax(int)}. (The default value for max is 100.)
     * @param fromUser True if the progress change was initiated by the user.
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (seekBar == mSeekDistanceInterval)
            gpsMinDistance = progress * gpsDistanceStep;
        else {
            if (progress == 0)
                sendTimeInterval = 5;
            else
                sendTimeInterval = (progress) * timeIntervalStep;
        }

        mBtnFollowMe.setText(getString(R.string.follow_me_start, gpsMinDistance, sendTimeInterval));
        mBtnFollowMe.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (isFollowMe) {
            mBtnFollowMe.setText(getString(R.string.follow_me_stop, gpsMinDistance, sendTimeInterval));
        }
        showToast(getString(R.string.apply_new_location_setting));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static void registeredLocationListener(LocationListener listener) {
        mCallBack = listener;
    }

    public interface LocationListener {
        void onResult(Location location, String locAddress);

    }

    protected Location getLastKnownLocation() {
        return mGeoLocationDelegate.getLastKnownLocation();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mGeoLocationDelegate.onActivityResult(requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mGeoLocationDelegate.onRequestPermissionsResult(requestCode, grantResults);
    }

    private void requestLocationUpdates(GeoLocationRequest geoLocationRequest) {
        mGeoLocationDelegate.requestLocationUpdate(geoLocationRequest);
    }

    private void stopLocationUpdates() {
        mGeoLocationDelegate.stopLocationUpdates();
    }
}