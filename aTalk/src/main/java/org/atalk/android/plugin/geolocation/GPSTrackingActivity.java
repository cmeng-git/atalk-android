package org.atalk.android.plugin.geolocation;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.atalk.android.R;

/**
 * Created by cmeng on 1/19/2017.
 */

public class GPSTrackingActivity extends Activity
{
    Button btnShowLocation;

    // GPSTracker class
    GPSTracker gps;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gps_tracker);

        btnShowLocation = findViewById(R.id.btnShowLocation);

        // Show location button click event
        btnShowLocation.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View arg0)
            {
                // Create class object
                gps = new GPSTracker(GPSTrackingActivity.this);

                // Check if GPS enabled
                if (gps.canGetLocation()) {

                    double latitude = gps.getLatitude();
                    double longitude = gps.getLongitude();

                    // \n is for new line
                    Toast.makeText(getApplicationContext(), "Your Location is - \nLat: " + latitude + "\nLong: " + longitude, Toast.LENGTH_LONG).show();
                }
                else {
                    // Can't get location.
                    // GPS or network is not enabled.
                    // Ask user to enable GPS/network in settings.
                    gps.showSettingsAlert();
                }
            }
        });
    }
}
