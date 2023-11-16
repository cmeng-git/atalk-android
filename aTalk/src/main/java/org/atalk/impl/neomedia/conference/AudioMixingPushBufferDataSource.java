/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference;

import org.atalk.impl.neomedia.control.AbstractControls;
import org.atalk.impl.neomedia.protocol.InbandDTMFDataSource;
import org.atalk.impl.neomedia.protocol.MuteDataSource;
import org.atalk.service.neomedia.DTMFInbandTone;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.LinkedList;

import javax.media.CaptureDeviceInfo;
import javax.media.Time;
import javax.media.control.BufferControl;
import javax.media.control.FormatControl;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;

import timber.log.Timber;

/**
 * Represents a <code>PushBufferDataSource</code> which provides a single <code>PushBufferStream</code>
 * containing the result of the audio mixing of <code>DataSource</code>s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class AudioMixingPushBufferDataSource extends PushBufferDataSource
        implements CaptureDevice, MuteDataSource, InbandDTMFDataSource
{
    /**
     * The <code>AudioMixer</code> performing the audio mixing, managing the input <code>DataSource</code>s
     * and pushing the data of this output <code>PushBufferDataSource</code>.
     */
    final AudioMixer audioMixer;

    /**
     * The indicator which determines whether this <code>DataSource</code> is connected.
     */
    private boolean connected;

    /**
     * The indicator which determines whether this <code>DataSource</code> is set to transmit "silence"
     * instead of the actual media.
     */
    private boolean mute = false;

    /**
     * The one and only <code>PushBufferStream</code> this <code>PushBufferDataSource</code> provides to
     * its clients and containing the result of the audio mixing performed by <code>audioMixer</code>.
     */
    private AudioMixingPushBufferStream outStream;

    /**
     * The indicator which determines whether this <code>DataSource</code> is started.
     */
    private boolean started;

    /**
     * The tones to send via inband DTMF, if not empty.
     */
    private final LinkedList<DTMFInbandTone> tones = new LinkedList<>();

    /**
     * Initializes a new <code>AudioMixingPushBufferDataSource</code> instance which gives access to
     * the result of the audio mixing performed by a specific <code>AudioMixer</code>.
     *
     * @param audioMixer the <code>AudioMixer</code> performing audio mixing, managing the input
     * <code>DataSource</code>s and pushing the data of the new output <code>PushBufferDataSource</code>
     */
    public AudioMixingPushBufferDataSource(AudioMixer audioMixer)
    {
        this.audioMixer = audioMixer;
    }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    public void addDTMF(DTMFInbandTone tone)
    {
        tones.add(tone);
    }

    /**
     * Adds a new input <code>DataSource</code> to be mixed by the associated <code>AudioMixer</code> of
     * this instance and to not have its audio contributions included in the mixing output
     * represented by this <code>DataSource</code>.
     *
     * @param inDataSource a <code>DataSource</code> to be added for mixing to the <code>AudioMixer</code> associate with
     * this instance and to not have its audio contributions included in the mixing output
     * represented by this <code>DataSource</code>
     */
    public void addInDataSource(DataSource inDataSource)
    {
        audioMixer.addInDataSource(inDataSource, this);
    }

    /**
     * Implements {@link DataSource#connect()}. Lets the <code>AudioMixer</code> know that one of its
     * output <code>PushBufferDataSources</code> has been connected and marks this <code>DataSource</code> as connected.
     *
     * @throws IOException if the <code>AudioMixer</code> fails to connect
     */
    @Override
    public synchronized void connect()
            throws IOException
    {
        if (!connected) {
            audioMixer.connect();
            connected = true;
        }
    }

    /**
     * Implements {@link DataSource#disconnect()}. Marks this <code>DataSource</code> as disconnected
     * and notifies the <code>AudioMixer</code> that one of its output <code>PushBufferDataSources</code>
     * has been disconnected.
     */
    @Override
    public synchronized void disconnect()
    {
        try {
            stop();
        } catch (IOException ioex) {
            throw new UndeclaredThrowableException(ioex);
        }

        if (connected) {
            outStream = null;
            connected = false;

            audioMixer.disconnect();
        }
    }

    /**
     * Gets the <code>BufferControl</code> available for this <code>DataSource</code>. Delegates to the
     * <code>AudioMixer</code> because this instance is just a facet to it.
     *
     * @return the <code>BufferControl</code> available for this <code>DataSource</code>
     */
    private BufferControl getBufferControl()
    {
        return audioMixer.getBufferControl();
    }

    /**
     * Implements {@link CaptureDevice#getCaptureDeviceInfo()}. Delegates to the associated
     * <code>AudioMixer</code> because it knows which <code>CaptureDevice</code> is being wrapped.
     *
     * @return the <code>CaptureDeviceInfo</code> of the <code>CaptureDevice</code> of the <code>AudioMixer</code>
     */
    public CaptureDeviceInfo getCaptureDeviceInfo()
    {
        return audioMixer.getCaptureDeviceInfo();
    }

    /**
     * Implements {@link DataSource#getContentType()}. Delegates to the associated
     * <code>AudioMixer</code> because it manages the inputs and knows their characteristics.
     *
     * @return a <code>String</code> value which represents the type of the content being made
     * available by this <code>DataSource</code> i.e. the associated <code>AudioMixer</code>
     */
    @Override
    public String getContentType()
    {
        return audioMixer.getContentType();
    }

    /**
     * Implements {@link DataSource#getControl(String)}.
     *
     * @param controlType a <code>String</code> value which names the type of the control of this instance to be retrieved
     * @return an <code>Object</code> which represents the control of this instance with the specified
     * type if such a control is available; otherwise, <code>null</code>
     */
    @Override
    public Object getControl(String controlType)
    {
        return AbstractControls.getControl(this, controlType);
    }

    /**
     * Implements {@link DataSource#getControls()}. Gets an array of <code>Object</code>s which
     * represent the controls available for this <code>DataSource</code>.
     *
     * @return an array of <code>Object</code>s which represent the controls available for this <code>DataSource</code>
     */
    @Override
    public Object[] getControls()
    {
        BufferControl bufferControl = getBufferControl();
        FormatControl[] formatControls = getFormatControls();

        if (bufferControl == null)
            return formatControls;
        else if ((formatControls == null) || (formatControls.length < 1))
            return new Object[]{bufferControl};
        else {
            Object[] controls = new Object[1 + formatControls.length];

            controls[0] = bufferControl;
            System.arraycopy(formatControls, 0, controls, 1, formatControls.length);
            return controls;
        }
    }

    /**
     * Implements {@link DataSource#getDuration()}. Delegates to the associated <code>AudioMixer</code>
     * because it manages the inputs and knows their characteristics.
     *
     * @return a <code>Time</code> value which represents the duration of the media being made
     * available through this <code>DataSource</code>
     */
    @Override
    public Time getDuration()
    {
        return audioMixer.getDuration();
    }

    /**
     * Implements {@link CaptureDevice#getFormatControls()}. Delegates to the associated
     * <code>AudioMixer</code> because it knows which <code>CaptureDevice</code> is being wrapped.
     *
     * @return an array of <code>FormatControl</code>s of the <code>CaptureDevice</code> of the associated <code>AudioMixer</code>
     */
    public FormatControl[] getFormatControls()
    {
        return audioMixer.getFormatControls();
    }

    /**
     * Gets the next inband DTMF tone signal.
     *
     * @param sampleRate The sampling frequency (codec clock rate) in Hz of the stream which will encapsulate
     * this signal.
     * @param sampleSizeInBits The size of each sample (8 for a byte, 16 for a short and 32 for an int)
     * @return The data array containing the DTMF signal.
     */
    public short[] getNextToneSignal(double sampleRate, int sampleSizeInBits)
    {
        return tones.poll().getAudioSamples(sampleRate, sampleSizeInBits);
    }

    /**
     * Implements {@link PushBufferDataSource#getStreams()}. Gets a <code>PushBufferStream</code> which
     * reads data from the associated <code>AudioMixer</code> and mixes its inputs.
     *
     * @return an array with a single <code>PushBufferStream</code> which reads data from the
     * associated <code>AudioMixer</code> and mixes its inputs if this <code>DataSource</code> is
     * connected; otherwise, an empty array
     */
    @Override
    public synchronized PushBufferStream[] getStreams()
    {
        if (connected && (outStream == null)) {
            AudioMixerPushBufferStream audioMixerOutStream = audioMixer.getOutStream();

            if (audioMixerOutStream != null) {
                outStream = new AudioMixingPushBufferStream(audioMixerOutStream, this);
                if (started)
                    try {
                        outStream.start();
                    } catch (IOException ioex) {
                        Timber.e(ioex, "Failed to start %s  with hashCode %s",
                                outStream.getClass().getSimpleName(), outStream.hashCode());
                    }
            }
        }
        return (outStream == null) ? new PushBufferStream[0] : new PushBufferStream[]{outStream};
    }

    /**
     * Determines whether this <code>DataSource</code> is mute.
     *
     * @return <code>true</code> if this <code>DataSource</code> is mute; otherwise, <code>false</code>
     */
    public boolean isMute()
    {
        return mute;
    }

    /**
     * Determines whether this <code>DataSource</code> sends a DTMF tone.
     *
     * @return <code>true</code> if this <code>DataSource</code> is sending a DTMF tone; otherwise,
     * <code>false</code>.
     */
    public boolean isSendingDTMF()
    {
        return !tones.isEmpty();
    }

    /**
     * Sets the mute state of this <code>DataSource</code>.
     *
     * @param mute <code>true</code> to mute this <code>DataSource</code>; otherwise, <code>false</code>
     */
    public void setMute(boolean mute)
    {
        this.mute = mute;
    }

    /**
     * Implements {@link DataSource#start()}. Starts the output <code>PushBufferStream</code> of this
     * <code>DataSource</code> (if it exists) and notifies the <code>AudioMixer</code> that one of its
     * output <code>PushBufferDataSources</code> has been started.
     *
     * @throws IOException if anything wrong happens while starting the output <code>PushBufferStream</code> of this
     * <code>DataSource</code>
     */
    @Override
    public synchronized void start()
            throws IOException
    {
        if (!started) {
            started = true;
            if (outStream != null)
                outStream.start();
        }
    }

    /**
     * Implements {@link DataSource#stop()}. Notifies the <code>AudioMixer</code> that one of its
     * output <code>PushBufferDataSources</code> has been stopped and stops the output
     * <code>PushBufferStream</code> of this <code>DataSource</code> (if it exists).
     *
     * @throws IOException if anything wrong happens while stopping the output <code>PushBufferStream</code> of this
     * <code>DataSource</code>
     */
    @Override
    public synchronized void stop()
            throws IOException
    {
        if (started) {
            started = false;
            if (outStream != null)
                outStream.stop();
        }
    }

    /**
     * The input <code>DataSource</code> has been updated.
     *
     * @param inDataSource the <code>DataSource</code> that was updated.
     */
    public void updateInDataSource(DataSource inDataSource)
    {
        // just update the input streams
        audioMixer.getOutStream();
    }
}
