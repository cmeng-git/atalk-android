package com.akhgupta.easylocation;

import android.app.Service;
import android.content.Intent;
import android.location.*;
import android.os.*;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.atalk.android.aTalkApp;

import java.io.IOException;
import java.util.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class LocationBgService extends Service
{
    private final String TAG = LocationBgService.class.getSimpleName();
    private static final long NO_FALLBACK = 0;
    private boolean mAddressRequest;

    private int mLocationMode;
    private long fallBackToLastLocationTime;

    /**
     * Contains parameters used by {@link com.google.android.gms.location.FusedLocationProviderClient}.
     */
    private LocationRequest mLocationRequest;

    /**
     * Provides access to the Fused Location Provider API.
     */
    private FusedLocationProviderClient mFusedLocationClient;

    /**
     * Callback for changes in location.
     */
    private LocationCallback mLocationCallback;

    private Handler mServiceHandler;

    /**
     * The current location.
     */
    private Location mLocation;

    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        mServiceHandler = new Handler();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mLocationCallback = new LocationCallback()
        {
            @Override
            public void onLocationResult(LocationResult locationResult)
            {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation());
            }
        };
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);

        String actionIintent = intent.getAction();
        // action not defined on gps service first startup
        if (actionIintent == null) {
            return START_NOT_STICKY;
        }

        Log.d(TAG, "mFusedLocationClient start command " + actionIintent);
        if (actionIintent.equals(AppConstants.ACTION_LOCATION_FETCH_START)) {
            mLocationMode = intent.getIntExtra(IntentKey.LOCATION_FETCH_MODE, AppConstants.SINGLE_FIX);
            mLocationRequest = intent.getParcelableExtra(IntentKey.LOCATION_REQUEST);
            mAddressRequest = intent.getBooleanExtra(IntentKey.ADDRESS_REQUEST, false);
            fallBackToLastLocationTime = intent.getLongExtra(IntentKey.FALLBACK_TO_LAST_LOCATION_TIME, NO_FALLBACK);
            if (mLocationRequest == null)
                throw new IllegalStateException("Location request can't be null");
            requestLocationUpdates();
        }
        else if (actionIintent.equals(AppConstants.ACTION_LOCATION_FETCH_STOP)) {
            stopLocationService();
        }

        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY;
    }

    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdates()
    {
        Log.i(TAG, "Requesting location updates");
        if (mLocationRequest != null) {
            startFallbackToLastLocationTimer();

            startService(new Intent(getApplicationContext(), LocationBgService.class));
            try {
                mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                        mLocationCallback, Looper.myLooper());
            } catch (SecurityException unlikely) {
                Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startFallbackToLastLocationTimer()
    {
        if (fallBackToLastLocationTime != NO_FALLBACK) {
            mServiceHandler.removeCallbacksAndMessages(null);
            mServiceHandler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    getLastLocation();
                }
            }, fallBackToLastLocationTime);
        }
    }

    private void getLastLocation()
    {
        try {
            mFusedLocationClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>()
            {
                @Override
                public void onComplete(@NonNull Task<Location> task)
                {
                    if (task.isSuccessful() && task.getResult() != null) {
                        mLocation = task.getResult();
                    }
                    else {
                        aTalkApp.showToastMessage("Failed to get GPS location information!");
                        Log.w(TAG, "Failed to get location.");
                    }
                }
            });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

//	@Override
//	public void onConnectionSuspended(int i)
//	{
//		Log.d(TAG, "googleApiClient connection suspended");
//		stopLocationService();
//	}

    /**
     * Removes location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    private void stopLocationService()
    {
        if (mServiceHandler != null)
            mServiceHandler.removeCallbacksAndMessages(null);

        Log.d(TAG, "mFusedLocationClient removing location updates");
        try {
            if (mFusedLocationClient != null)
                mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            Log.d(TAG, "mFusedLocationClient stop service");
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not remove updates. " + unlikely);
        }
        stopSelf();
    }

    private void onNewLocation(Location location)
    {
        Log.i(TAG, "New location received: " + location);
        mLocation = location;
        if (location != null) {
            PreferenceUtil.getInstance(this).saveLastKnownLocation(location);

            // Notify anyone listening for broadcasts about the new location.
            Intent intent = new Intent();
            intent.setAction(AppConstants.INTENT_LOCATION_RECEIVED);
            intent.putExtra(IntentKey.LOCATION, location);

            String locAddress = null;
            if (mAddressRequest) {
                locAddress = getLocationAddress(location);
            }
            intent.putExtra(IntentKey.ADDRESS, locAddress);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        else {
            Intent intent = new Intent();
            intent.setAction(AppConstants.INTENT_NO_LOCATION_RECEIVED);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        if (mLocationMode == AppConstants.SINGLE_FIX) {
            stopLocationService();
        }
    }

    /**
     * To get address location from coordinates
     */
    private String getLocationAddress(Location loc)
    {
        String locAddress = "No service available or no address found";
        Geocoder gcd = new Geocoder(getBaseContext(), Locale.getDefault());
        List<Address> addresses;
        try {
            addresses = gcd.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            ArrayList<String> addressFragments = new ArrayList<>();
            if ((addresses != null) && (addresses.size() > 0)) {
                Address address = addresses.get(0);
                // Fetch the address lines using getAddressLine,
                // join them, and send them to the thread.
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    addressFragments.add(address.getAddressLine(i));
                }
                locAddress = TextUtils.join(" \n", addressFragments);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return locAddress;
    }
}