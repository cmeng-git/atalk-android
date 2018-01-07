package com.akhgupta.easylocation;

import android.location.Location;

interface EasyLocationListener {
    void onLocationPermissionGranted();
    void onLocationPermissionDenied();
    void onLocationReceived(Location location, String locAddress);
    void noLocationReceived();
    void onLocationProviderEnabled();
    void onLocationProviderDisabled();
}