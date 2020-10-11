/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import android.media.audiofx.*;

import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AudioTrackRenderer;
import org.atalk.service.neomedia.codec.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.media.*;
import javax.media.format.AudioFormat;

/**
 * Discovers and registers {@link android.media.AudioRecord} capture devices with FMJ.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
@SuppressWarnings("unused")
public class AudioRecordSystem extends AudioSystem
{
    /**
     * Initializes a new <tt>AudioRecordSystem</tt> instance which discovers and registers
     * <tt>AudioRecord</tt> capture devices with FMJ.
     *
     * @throws Exception if anything goes wrong while discovering and registering <tt>AudioRecord</tt>
     * capture devices with FMJ
     */
    public AudioRecordSystem()
            throws Exception
    {
        super(LOCATOR_PROTOCOL_AUDIORECORD, getFeatureSet());
    }

    /**
     * Returns feature set for current android device;
     * a. capture
     * b. playback
     *
     * @return feature set for current device.
     */
    public static int getFeatureSet()
    {
        int featureSet = FEATURE_NOTIFY_AND_PLAYBACK_DEVICES;
        if (AcousticEchoCanceler.isAvailable()) {
            featureSet |= FEATURE_ECHO_CANCELLATION;
        }
        if (NoiseSuppressor.isAvailable()) {
            featureSet |= FEATURE_DENOISE;
        }
        if (AutomaticGainControl.isAvailable()) {
            featureSet |= FEATURE_AGC;
        }
        return featureSet;
    }

    @Override
    public Renderer createRenderer(boolean playback)
    {
        return new AudioTrackRenderer(playback);
    }

    protected void doInitialize()
            throws Exception
    {
        List<Format> formats = new ArrayList<>();
        for (int i = 0; i < Constants.AUDIO_SAMPLE_RATES.length; i++) {
            double sampleRate = Constants.AUDIO_SAMPLE_RATES[i];

            // Certain sample rates do not seem to be supported by android.
            if (sampleRate == 48000)
                continue;

            formats.add(new AudioFormat(AudioFormat.LINEAR,
                    sampleRate,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */,
                    Format.byteArray));
        }

        // Audio capture device
        CaptureDeviceInfo2 captureDevice = new CaptureDeviceInfo2(
                "android.media.AudioRecordCapture", new MediaLocator(LOCATOR_PROTOCOL_AUDIORECORD + ":"),
                formats.toArray(new Format[0]), null, null, null);
        List<CaptureDeviceInfo2> captureDevices = new ArrayList<>(1);

        captureDevices.add(captureDevice);
        setCaptureDevices(captureDevices);

        // Audio playback (playback, notification) devices
        CaptureDeviceInfo2 playbackDevice = new CaptureDeviceInfo2(
                "android.media.AudioRecordPlayback", new MediaLocator(LOCATOR_PROTOCOL_AUDIORECORD + ":playback"),
                formats.toArray(new Format[0]), null, null, null);
        CaptureDeviceInfo2 notificationDevice = new CaptureDeviceInfo2(
                "android.media.AudioRecordNotification", new MediaLocator(LOCATOR_PROTOCOL_AUDIORECORD
                + ":notification"), formats.toArray(new Format[0]), null, null, null);

        List<CaptureDeviceInfo2> playbackDevices = new ArrayList<>(2);
        playbackDevices.add(playbackDevice);
        playbackDevices.add(notificationDevice);
        setPlaybackDevices(playbackDevices);

        setDevice(DataFlow.NOTIFY, notificationDevice, true);
        setDevice(DataFlow.PLAYBACK, playbackDevice, true);
    }

    /**
     * Obtains an audio input stream from the URL provided.
     *
     * @param url a valid url to a sound resource.
     * @return the input stream to audio data.
     * @throws java.io.IOException if an I/O exception occurs
     */
    public InputStream getAudioInputStream(String url)
            throws IOException
    {
        return AudioStreamUtils.getAudioInputStream(url);
    }

    /**
     * Returns the audio format for the <tt>InputStream</tt>. Or null if format cannot be obtained.
     * Support only Wave format in current implementation.
     *
     * @param audioInputStream the input stream.
     * @return the format of the audio stream.
     */
    public AudioFormat getFormat(InputStream audioInputStream)
    {
        return AudioStreamUtils.getFormat(audioInputStream);
    }
}
