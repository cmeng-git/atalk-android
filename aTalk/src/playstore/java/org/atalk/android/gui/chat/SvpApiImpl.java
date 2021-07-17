/*
 *
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.app.Activity;
import android.content.Intent;

import com.google.android.gms.maps.model.LatLng;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatFragment.ChatListAdapter.MessageDisplay;
import org.atalk.android.plugin.geolocation.SplitStreetViewPanoramaAndMapActivity;

import java.util.*;

/**
 * The <tt>SvpApiImpl</tt> working in conjunction with ChatFragment to provide SVP i.e.
 * Split Street View Panorama And Map support.
 *
 * @author Eng Chong Meng
 */
public class SvpApiImpl implements SvpApi
{
    /**
     * Perform google street and map view fetch when user click the show map button
     */
    @Override
    public void onSVPClick(Activity activity, double latitude, double longitude)
    {
        LatLng mLatLng = new LatLng(latitude, longitude);
        Intent intent = new Intent(activity, SplitStreetViewPanoramaAndMapActivity.class);
        intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, mLatLng);
        activity.startActivity(intent);
    }

    /**
     * Perform google street and map view playback when user longclick the show map button
     *
     * @param mLatLng list of LatLng double value pairs
     */
    @Override
    public void onSVPLongClick(Activity activity, List<double[]> mLatLng)
    {
        ArrayList<LatLng> xLatLng = new ArrayList<>();
        LatLng latLng = null;
        for (double[] entry : mLatLng) {
            latLng = new LatLng(entry[0], entry[1]);
            xLatLng.add(latLng);
        }

        Intent intent = new Intent(activity, SplitStreetViewPanoramaAndMapActivity.class);
        intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_POSITION_KEY, latLng);
        intent.putExtra(SplitStreetViewPanoramaAndMapActivity.MARKER_LIST, xLatLng);
        activity.startActivity(intent);
    }

    @Override
    public Object svpHandler(Object mSVP, MessageDisplay msg)
    {
        if (mSVP == null) {
            Activity currentActivity = aTalkApp.getCurrentActivity();
            if (currentActivity != null) {
                if (currentActivity instanceof SplitStreetViewPanoramaAndMapActivity) {
                    mSVP = currentActivity;
                }
            }
        }
        if (mSVP != null) {
            if (msg.hasLatLng)
                ((SplitStreetViewPanoramaAndMapActivity) mSVP).onLocationChanged(new LatLng(msg.latitude, msg.longitude));
        }
        return mSVP;
    }
}
