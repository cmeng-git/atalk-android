package org.atalk.android.plugin.geolocation;

import android.location.Location;

import org.atalk.service.osgi.OSGiActivity;

/**
 * Dummy class for fdroid build interface
 */
public class GeoLocation extends OSGiActivity
{
    public static String SEND_LOCATION = "Send_Location";
    private static LocationListener mCallBack;

    public static void registeredLocationListener(LocationListener listener)
    {
        mCallBack = listener;
    }

    public interface LocationListener
    {
        void onResult(Location location, String locAddress);
    }
}