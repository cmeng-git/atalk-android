package com.akhgupta.easylocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

class LocationBroadcastReceiver extends BroadcastReceiver {
    private final EasyLocationListener easyLocationListener;

    public LocationBroadcastReceiver(EasyLocationListener easyLocationListener) {
        this.easyLocationListener = easyLocationListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(AppConstants.INTENT_LOCATION_RECEIVED)) {
            Location location = intent.getParcelableExtra(IntentKey.LOCATION);
            String locAddress = intent.getStringExtra(IntentKey.ADDRESS);
            easyLocationListener.onLocationReceived(location, locAddress);
        } else if (AppConstants.INTENT_NO_LOCATION_RECEIVED.equals(intent.getAction())) {
            easyLocationListener.noLocationReceived();
        }
    }
}