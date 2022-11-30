/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.Toast;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.event.VolumeChangeEvent;
import org.atalk.service.neomedia.event.VolumeChangeListener;
import org.atalk.service.osgi.OSGiFragment;

/**
 * Fragment used to control call volume. Key events for volume up and down have to be captured by the parent
 * <code>Activity</code> and passed here, before they get to system audio service. The volume is increased using
 * <code>AudioManager</code> until it reaches maximum level, then we increase the Libjitsi volume gain.
 * The opposite happens when volume is being decreased.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CallVolumeCtrlFragment extends OSGiFragment implements VolumeChangeListener
{
    /**
     * Current volume gain "position" in range from 0 to 10.
     */
    private int position;

    /**
     * Output volume control.
     */
    private VolumeControl volumeControl;

    /**
     * The <code>AudioManager</code> used to control voice call stream volume.
     */
    private AudioManager audioManager;

    /**
     * The toast instance used to update currently displayed toast if any.
     */
    private Toast toast;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager)  aTalkApp.getGlobalContext().getSystemService(Context.AUDIO_SERVICE);
        MediaServiceImpl mediaService = NeomediaActivator.getMediaServiceImpl();
        if (mediaService != null)
            volumeControl = mediaService.getOutputVolumeControl();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (volumeControl == null)
            return;

        float currentVol = volumeControl.getVolume();
        // Default
        if (currentVol < 0) {
            position = 5;
        }
        else {
            position = calcPosition(currentVol);
        }
        volumeControl.addVolumeChangeListener(this);
    }

    @Override
    public void onPause()
    {
        if (volumeControl != null) {
            volumeControl.removeVolumeChangeListener(this);
        }
        if (toast != null && toast.getView() != null) {
            toast.cancel();
            toast = null;
        }
        super.onPause();
    }

    /**
     * Returns current volume index for <code>AudioManager.STREAM_VOICE_CALL</code>.
     *
     * @return current volume index for <code>AudioManager.STREAM_VOICE_CALL</code>.
     */
    private int getAudioStreamVolume()
    {
        return audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
    }

    /**
     * Method should be called by the parent <code>Activity</code> when volume up key is pressed.
     */
    public void onKeyVolUp()
    {
        int controlMode = AudioManager.ADJUST_RAISE;
        if (position < 5) {
            controlMode = AudioManager.ADJUST_SAME;
        }

        int current = getAudioStreamVolume();
        audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, controlMode, AudioManager.FLAG_SHOW_UI);
        int newStreamVol = getAudioStreamVolume();

        if (current == newStreamVol) {
            setVolumeGain(position + 1);
        }
        else {
            setVolumeGain(5);
        }
    }

    /**
     * Method should be called by the parent <code>Activity</code> when volume down key is pressed.
     */
    public void onKeyVolDown()
    {
        int controlMode = AudioManager.ADJUST_LOWER;
        if (position > 5) {
            // We adjust the same just to show the gui
            controlMode = AudioManager.ADJUST_SAME;
        }

        int current = getAudioStreamVolume();
        audioManager.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, controlMode, AudioManager.FLAG_SHOW_UI);
        int newStreamVol = getAudioStreamVolume();

        if (current == newStreamVol) {
            setVolumeGain(position - 1);
        }
        else {
            setVolumeGain(5);
        }
    }

    private int calcPosition(float volumeGain)
    {
        return (int) ((volumeGain / getVolumeCtrlRange()) * 10f);
    }

    private void setVolumeGain(int newPosition)
    {
        float newVolume = getVolumeCtrlRange() * (((float) newPosition) / 10f);
        this.position = calcPosition(volumeControl.setVolume(newVolume));
    }

    @Override
    public void volumeChange(VolumeChangeEvent volumeChangeEvent)
    {
        position = calcPosition(volumeChangeEvent.getLevel() / getVolumeCtrlRange());
        runOnUiThread(() -> {
            Activity parent = getActivity();
            if (parent == null)
                return;

            String txt = aTalkApp.getResString(R.string.service_gui_VOLUME_GAIN_LEVEL, position * 10);
            if (toast == null) {
                toast = Toast.makeText(parent, txt, Toast.LENGTH_SHORT);
            }
            else {
                toast.setText(txt);
            }
            toast.show();
        });
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
}
