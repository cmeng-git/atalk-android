/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.app.Dialog;
import android.content.DialogInterface;
import android.hardware.*;
import android.os.Bundle;
import android.support.v4.app.*;
import android.view.*;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.service.osgi.OSGiActivity;

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
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(ProximitySensorFragment.class);

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
		fm = ((OSGiActivity) getActivity()).getSupportFragmentManager();

		// Skips if the sensor has been already attached
		if (proximitySensor != null) {
			// Re-registers the listener as it might have been unregistered in onPause()
			manager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI);
			return;
		}

//		List<Sensor> sensors = manager.getSensorList(Sensor.TYPE_ALL);
//		logger.trace("Device has " + sensors.size() + " sensors");
//		for (Sensor s : sensors) {
//			logger.trace("Sensor " + s.getName() + " type: " + s.getType());
//		}

		proximitySensor = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		if (proximitySensor == null) {
			return;
		}

		logger.info("Using proximity sensor: " + proximitySensor.getName());
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
		float max = event.sensor.getMaximumRange();
//		logger.info("Proximity updated: " + proximity + " max range: " + max);

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
		// ScreenOff exist - proximity detection screen on is out of sync; so just reuse the
		// existing one
//		if (screenOffDialog != null) {
//			logger.warn("screenOffDialog exist when trying to perform screenOff");
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
		else {
//			logger.warn("screenOffDialog was null when trying to perform screenOn");
		}
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

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			setStyle(R.style.ScreenOffDialog, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

			Dialog d = super.onCreateDialog(savedInstanceState);
			d.setContentView(R.layout.screen_off);

			d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
					| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			d.setOnKeyListener(new DialogInterface.OnKeyListener()
			{
				@Override
				public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event)
				{
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
				}
			});
			return d;
		}
	}
}
