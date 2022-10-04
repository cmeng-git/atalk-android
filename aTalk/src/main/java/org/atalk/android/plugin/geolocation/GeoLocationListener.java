package org.atalk.android.plugin.geolocation;

import android.location.Location;

public interface GeoLocationListener
{
    void onLocationPermissionGranted();

    void onLocationPermissionDenied();

    void onLocationReceived(Location location, String locAddress);

    void onLocationReceivedNone();

    void onLocationProviderEnabled();

    void onLocationProviderDisabled();
}