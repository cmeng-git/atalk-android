package org.atalk.android.gui.chat;

import android.app.Activity;

import org.atalk.android.gui.chat.ChatFragment.ChatListAdapter.MessageDisplay;

import java.util.List;

public interface SvpApi
{
    void onSVPClick(Activity activity, double latitude, double longitude);

    void onSVPLongClick(Activity activity, List<double[]> mLatLng);

    Object svpHandler(Object msvp, MessageDisplay msg);
}
