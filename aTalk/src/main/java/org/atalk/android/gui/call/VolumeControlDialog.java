/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import org.atalk.android.R;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.event.VolumeChangeEvent;
import org.atalk.service.neomedia.event.VolumeChangeListener;
import org.atalk.service.osgi.OSGiDialogFragment;

/**
 * The dialog allows user to manipulate input or output volume gain level. To specify which one will be manipulated by
 * current instance the {@link #ARG_DIRECTION} should be specified with one of direction values:
 * {@link #DIRECTION_INPUT} or {@link #DIRECTION_OUTPUT}. Static factory methods are convenient for creating
 * parametrized dialogs.
 *
 * @author Pawel Domas
 */
public class VolumeControlDialog extends OSGiDialogFragment implements VolumeChangeListener, SeekBar.OnSeekBarChangeListener
{
	/**
	 * The argument specifies whether output or input volume gain will be manipulated by this dialog.
	 */
	public static final String ARG_DIRECTION = "ARG_DIRECTION";

	/**
	 * The direction argument value for output volume gain.
	 */
	public static final int DIRECTION_OUTPUT = 0;

	/**
	 * The direction argument value for input volume gain.
	 */
	public static final int DIRECTION_INPUT = 1;

	/**
	 * Abstract volume control used by this dialog.
	 */
	private VolumeControl volumeControl;

	/**
	 * {@inheritDoc}
	 */
	@Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
		MediaServiceImpl mediaService = NeomediaActivator.getMediaServiceImpl();

		// Selects input or output volume control based on the arguments.
		int direction = getArguments().getInt(ARG_DIRECTION, 0);
		if (direction == DIRECTION_OUTPUT) {
			this.volumeControl = mediaService.getOutputVolumeControl();
		}
		else if (direction == DIRECTION_INPUT) {
			this.volumeControl = mediaService.getInputVolumeControl();
		}
		else {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onResume()
	{
		super.onResume();

		volumeControl.addVolumeChangeListener(this);

		SeekBar bar = getVolumeBar();
		// Initialize volume bar
		int progress = getVolumeBarProgress(bar, volumeControl.getVolume());
		bar.setProgress(progress);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onPause()
	{
		super.onPause();

		volumeControl.removeVolumeChangeListener(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View content = inflater.inflate(R.layout.volume_control, container, false);

		SeekBar bar = (SeekBar) content.findViewById(R.id.seekBar);
		bar.setOnSeekBarChangeListener(this);

		int titleStrId = R.string.volume_control_title;
		if (getArguments().getInt(ARG_DIRECTION) == DIRECTION_INPUT) {
			titleStrId = R.string.mic_control_title;
		}
		getDialog().setTitle(titleStrId);

		return content;
	}

	/**
	 * Returns the <code>SeekBar</code> used to control the volume.
	 * 
	 * @return the <code>SeekBar</code> used to control the volume.
	 */
	private SeekBar getVolumeBar()
	{
		return (SeekBar) getView().findViewById(R.id.seekBar);
	}

	/**
	 * {@inheritDoc}
	 */
	public void volumeChange(VolumeChangeEvent volumeChangeEvent)
	{
		SeekBar seekBar = getVolumeBar();

		int progress = getVolumeBarProgress(seekBar, volumeChangeEvent.getLevel());
		seekBar.setProgress(progress);
	}

	/**
	 * Calculates the progress value suitable for given <code>SeekBar</code> from the device volume level.
	 * 
	 * @param volumeBar
	 *        the <code>SeekBar</code> for which the progress value will be calculated.
	 * @param volLevel
	 *        actual volume level from <code>VolumeControl</code>. Value <code>-1.0</code> means the level is invalid and
	 *        default progress value should be provided.
	 * @return the progress value calculated from given volume level that will be suitable for specified
	 *         <code>SeekBar</code>.
	 */
	private int getVolumeBarProgress(SeekBar volumeBar, float volLevel)
	{
		if (volLevel == -1.0) {
			// If the volume is invalid position at the middle
			volLevel = getVolumeCtrlRange() / 2;
		}

		float progress = volLevel / getVolumeCtrlRange();
		return (int) (progress * volumeBar.getMax());
	}

	/**
	 * Returns abstract volume control range calculated for volume control min and max values.
	 * 
	 * @return the volume control range calculated for current volume control min and max values.
	 */
	private float getVolumeCtrlRange()
	{
		return volumeControl.getMaxValue() - volumeControl.getMinValue();
	}

	/**
	 * {@inheritDoc}
	 */
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (!fromUser)
			return;

		float position = (float) progress / (float) seekBar.getMax();
		volumeControl.setVolume(getVolumeCtrlRange() * position);
	}

	/**
	 * {@inheritDoc}
	 */
	public void onStartTrackingTouch(SeekBar seekBar)
	{

	}

	/**
	 * {@inheritDoc}
	 */
	public void onStopTrackingTouch(SeekBar seekBar)
	{

	}

	/**
	 * Creates the <code>VolumeControlDialog</code> that can be used to control output volume gain level.
	 * 
	 * @return the <code>VolumeControlDialog</code> for output volume gain level.
	 */
	static public VolumeControlDialog createOutputVolCtrlDialog()
	{
		VolumeControlDialog dialog = new VolumeControlDialog();

		Bundle args = new Bundle();
		args.putInt(ARG_DIRECTION, DIRECTION_OUTPUT);
		dialog.setArguments(args);

		return dialog;
	}

	/**
	 * Creates the <code>VolumeControlDialog</code> for controlling microphone gain level.
	 * 
	 * @return the <code>VolumeControlDialog</code> that can be used to set microphone gain level.
	 */
	static public VolumeControlDialog createInputVolCtrlDialog()
	{
		VolumeControlDialog dialog = new VolumeControlDialog();

		Bundle args = new Bundle();
		args.putInt(ARG_DIRECTION, DIRECTION_INPUT);
		dialog.setArguments(args);

		return dialog;
	}
}
