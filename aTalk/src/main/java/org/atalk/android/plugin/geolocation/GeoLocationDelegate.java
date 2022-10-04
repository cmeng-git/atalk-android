package org.atalk.android.plugin.geolocation;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.atalk.android.R;

public class GeoLocationDelegate
{
    private static final int PERMISSIONS_REQUEST = 100;
    private static final int ENABLE_LOCATION_SERVICES_REQUEST = 101;

    private final GeoLocationListener mGeoLocationListener;
    private final LocationBroadcastReceiver mLocationReceiver;
    private final Activity mActivity;

    private LocationManager mLocationManager;
    private GeoLocationRequest mGeoLocationRequest;

    public GeoLocationDelegate(Activity activity, GeoLocationListener geoLocationListener)
    {
        mActivity = activity;
        mGeoLocationListener = geoLocationListener;
        mLocationReceiver = new LocationBroadcastReceiver(geoLocationListener);
    }

    public void onCreate()
    {
        mLocationManager = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);
        registerLocationBroadcastReceiver();
    }

    public void onDestroy()
    {
        unregisterLocationBroadcastReceiver();
        stopLocationUpdates();
    }

    private void registerLocationBroadcastReceiver()
    {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GeoConstants.INTENT_LOCATION_RECEIVED);
        intentFilter.addAction(GeoConstants.INTENT_NO_LOCATION_RECEIVED);
        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mLocationReceiver, intentFilter);
    }

    public void unregisterLocationBroadcastReceiver()
    {
        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mLocationReceiver);
    }

    private void startLocationBGService()
    {
        if (!LocationManagerCompat.isLocationEnabled(mLocationManager))
            showLocationServicesRequireDialog();
        else {
            Intent intent = new Intent(mActivity, LocationBgService.class);
            intent.setAction(GeoConstants.ACTION_LOCATION_FETCH_START);
            intent.putExtra(GeoIntentKey.LOCATION_REQUEST, mGeoLocationRequest);
            mActivity.startService(intent);
        }
    }

    public void stopLocationUpdates()
    {
        Intent intent = new Intent(mActivity, LocationBgService.class);
        intent.setAction(GeoConstants.ACTION_LOCATION_FETCH_STOP);
        mActivity.startService(intent);
    }

    public void requestLocationUpdate(GeoLocationRequest geoLocationRequest)
    {
        if (geoLocationRequest == null)
            throw new IllegalStateException("geoLocationRequest can't be null");

        mGeoLocationRequest = geoLocationRequest;
        checkForPermissionAndRequestLocation();
    }

    private void openLocationSettings()
    {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        mActivity.startActivityForResult(intent, ENABLE_LOCATION_SERVICES_REQUEST);
    }

    private boolean hasLocationPermission()
    {
        return ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionRequireDialog()
    {
        new AlertDialog.Builder(mActivity)
                .setCancelable(true)
                .setTitle(R.string.location_permission_dialog_title)
                .setMessage(R.string.location_permission_dialog_message)
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i)
                        -> mGeoLocationListener.onLocationPermissionDenied())
                .setPositiveButton(android.R.string.ok, (dialogInterface, i)
                        -> requestPermission())
                .create().show();
    }

    private void showLocationServicesRequireDialog()
    {
        new AlertDialog.Builder(mActivity)
                .setCancelable(true)
                .setTitle(R.string.location_services_off)
                .setMessage(R.string.open_location_settings)
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i)
                        -> mGeoLocationListener.onLocationProviderDisabled())
                .setPositiveButton(android.R.string.ok, (dialogInterface, i)
                        -> openLocationSettings())
                .create().show();
    }

    private void requestPermission()
    {
        ActivityCompat.requestPermissions(mActivity,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST);
    }

    protected void checkForPermissionAndRequestLocation()
    {
        if (!hasLocationPermission()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity, android.Manifest.permission.ACCESS_FINE_LOCATION))
                showPermissionRequireDialog();
            else
                requestPermission();
        }
        else {
            startLocationBGService();
        }
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults)
    {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationUpdate(mGeoLocationRequest);
                mGeoLocationListener.onLocationPermissionGranted();
            }
            else {
                mGeoLocationListener.onLocationPermissionDenied();
            }
        }
    }

    public void onActivityResult(int requestCode)
    {
        if (requestCode == ENABLE_LOCATION_SERVICES_REQUEST) {
            if (LocationManagerCompat.isLocationEnabled(mLocationManager)) {
                requestLocationUpdate(mGeoLocationRequest);
                mGeoLocationListener.onLocationProviderEnabled();
            }
            else {
                mGeoLocationListener.onLocationProviderDisabled();
            }
        }
    }

    public Location getLastKnownLocation()
    {
        return GeoPreferenceUtil.getInstance(mActivity).getLastKnownLocation();
    }
}