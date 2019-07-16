/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.app.Dialog;
import android.hardware.*;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jetbrains.annotations.NotNull;

import androidx.fragment.app.*;
import timber.log.Timber;

/**
 * This fragment when added to parent <tt>VideoCallActivity</tt> will listen for proximity sensor
 * updates and turn the screen on and off when NEAR/FAR distance is detected.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ProximitySensorFragment extends Fragment implements SensorEventListener
{
    /**
     * Proximity sensor managed used by this fragment.
     */
    private Sensor proximitySensor;

    /**
     * Unreliable sensor status flag.
     */
    private boolean sensorDisabled = true;

    /**
     * Instant of fragmentManager for screen off Dialog creation
     */
    private FragmentManager fm = null;

    /**
     * Instant of screen off Dialog - dismiss in screenOn()
     */
    private ScreenOffDialog screenOffDialog = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume()
    {
        super.onResume();
        SensorManager manager = aTalkApp.getSensorManager();
        fm = getActivity().getSupportFragmentManager();

        // Skips if the sensor has been already attached
        if (proximitySensor != null) {
            // Re-registers the listener as it might have been unregistered in onPause()
            manager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            return;
        }

//		List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
//		Timber.d("Device has %s  sensors", sensors.size());
//		for (Sensor s : sensors) {
//			Timber.d("Sensor %s; type: %s", s.getName(), s.getType());
//		}

        proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor == null) {
            return;
        }

        Timber.i("Using proximity sensor: %s", proximitySensor.getName());
        manager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI);
        sensorDisabled = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause()
    {
        super.onPause();
        if (proximitySensor != null) {
            screenOn();
            aTalkApp.getSensorManager().unregisterListener(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (proximitySensor != null) {
            screenOn();
            aTalkApp.getSensorManager().unregisterListener(this);
            proximitySensor = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void onSensorChanged(SensorEvent event)
    {
        if (sensorDisabled)
            return;

        float proximity = event.values[0];
        // float max = event.sensor.getMaximumRange();
        // Timber.i("Proximity updated: " + proximity + " max range: " + max);

        if (proximity > 0) {
            screenOn();
        }
        else {
            screenOff();
        }
    }

    /**
     * Turns the screen off.
     */
    private void screenOff()
    {
        // ScreenOff exist - proximity detection screen on is out of sync; so just reuse the existing one
//		if (screenOffDialog != null) {
//			Timber.w("screenOffDialog exist when trying to perform screenOff");
//		}
        screenOffDialog = new ScreenOffDialog();
        screenOffDialog.show(fm, "screen_off_dialog");
    }

    /**
     * Turns the screen on.
     */
    private void screenOn()
    {
        if (screenOffDialog != null) {
            screenOffDialog.dismiss();
            screenOffDialog = null;
        }
//        else {
//			Timber.w("screenOffDialog was null when trying to perform screenOn");
//        }
    }

    /**
     * {@inheritDoc}
     */
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            sensorDisabled = true;
            screenOn();
        }
        else {
            sensorDisabled = false;
        }
    }

    /**
     * Blank full screen dialog that captures all keys (BACK is what interest us the most).
     */
    public static class ScreenOffDialog extends DialogFragment
    {
        private CallVolumeCtrlFragment volControl;

        @Override
        public void onResume()
        {
            super.onResume();
            volControl = ((VideoCallActivity) getActivity()).getVolCtrlFragment();
        }

        @Override
        public void onPause()
        {
            super.onPause();
            volControl = null;
        }

        @NotNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

            Dialog d = super.onCreateDialog(savedInstanceState);
            d.setContentView(R.layout.screen_off);

            d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            d.setOnKeyListener((dialog, keyCode, event) -> {
                // Capture all events, but dispatch volume keys to volume control fragment
                if (volControl != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        volControl.onKeyVolUp();
                    }
                    else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        volControl.onKeyVolDown();
                    }
                    // Exit Screen Lock
                    else if (keyCode == KeyEvent.KEYCODE_BACK) {
                        onResume();
                    }
                }
                return true;
            });
            return d;
        }
    }
}
