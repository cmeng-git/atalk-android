/*
 *
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.chat;

import android.app.Activity;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatFragment.ChatListAdapter.MessageDisplay;

import java.util.List;

/**
 * The <tt>SvpApiImpl</tt> working in conjunction with ChatFragment to provide SVP support.
 * An implementation API for F-Droid release
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
        aTalkApp.showToastMessage("Feature not supported in F-Droid release!");
    }

    /**
     * Perform google street and map view playback when user longclick the show map button
     *
     * @param mLatLng list of LatLng double value pairs
     */
    @Override
    public void onSVPLongClick(Activity activity, List<double[]> mLatLng)
    {
        aTalkApp.showToastMessage("Feature not supported in F-Droid release!");
    }

    @Override
    public Object svpHandler(Object mSVP, MessageDisplay msg)
    {
        return null;
    }
}
