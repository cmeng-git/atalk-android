/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Process;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractBufferStream;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream;
import org.atalk.service.neomedia.BasicVolumeControl;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.GainControl;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;

import timber.log.Timber;

/**
 * Implements an audio <code>CaptureDevice</code> using {@link AudioRecord}.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class DataSource extends AbstractPullBufferCaptureDevice
{
    /**
     * The priority to be set to the thread executing the {@link AudioRecordStream#read(Buffer)}
     * method of a given <code>AudioRecordStream</code>.
     */
    private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_URGENT_AUDIO;

    /**
     * Initializes a new <code>DataSource</code> instance.
     */
    public DataSource()
    {
    }

    /**
     * Initializes a new <code>DataSource</code> from a specific <code>MediaLocator</code>.
     *
     * @param locator the <code>MediaLocator</code> to create the new instance from
     */
    public DataSource(MediaLocator locator)
    {
        super(locator);
    }

    /**
     * Creates a new <code>PullBufferStream</code> which is to be at a specific zero-based index in the
     * list of streams of this <code>PullBufferDataSource</code>. The <code>Format</code>-related
     * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> in the list of streams of this
     * <code>PullBufferDataSource</code>
     * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
     * information of the new instance
     * @return a new <code>PullBufferStream</code> which is to be at the specified <code>streamIndex</code>
     * in the list of streams of this <code>PullBufferDataSource</code> and which has its
     * <code>Format</code>-related information abstracted by the specified <code>formatControl</code>
     * @see AbstractPullBufferCaptureDevice#createStream(int, FormatControl)
     */
    protected AbstractPullBufferStream createStream(int streamIndex, FormatControl formatControl)
    {
        return new AudioRecordStream(this, formatControl);
    }

    /**
     * Opens a connection to the media source specified by the <code>MediaLocator</code> of this <code>DataSource</code>.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the <code>MediaLocator</code> of this <code>DataSource</code>
     * @see AbstractPullBufferCaptureDevice#doConnect()
     */
    @Override
    protected void doConnect()
            throws IOException
    {
        super.doConnect();
        /*
         * XXX The AudioRecordStream will connect upon start in order to be able to respect requests
         * to set its format.
         */
    }

    /**
     * Closes the connection to the media source specified by the <code>MediaLocator</code> of this <code>DataSource</code>.
     *
     * @see AbstractPullBufferCaptureDevice#doDisconnect()
     */
    @Override
    protected void doDisconnect()
    {
        synchronized (getStreamSyncRoot()) {
            Object[] streams = streams();
            if (streams != null)
                for (Object stream : streams)
                    ((AudioRecordStream) stream).disconnect();
        }
        aTalkApp.getAudioManager().setMode(AudioManager.MODE_NORMAL);
        super.doDisconnect();
    }

    /**
     * Sets the priority of the calling thread to {@link #THREAD_PRIORITY}.
     */
    public static void setThreadPriority()
    {
        setThreadPriority(THREAD_PRIORITY);
    }

    /**
     * Sets the priority of the calling thread to a specific value.
     *
     * @param threadPriority the priority to be set on the calling thread
     */
    public static void setThreadPriority(int threadPriority)
    {
        try {
            Process.setThreadPriority(threadPriority);
        } catch (IllegalArgumentException | SecurityException ex) {
            Timber.w("Failed to set thread priority: %s", ex.getMessage());
        }
    }

    /**
     * Attempts to set the <code>Format</code> to be reported by the <code>FormatControl</code> of a
     * <code>PullBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>PullBufferDataSource</code>. The <code>PullBufferStream</code> does not exist at the time of
     * the attempt to set its <code>Format</code>. Override the default behavior which is to not attempt
     * to set the specified <code>Format</code> so that they can enable setting the <code>Format</code>
     * prior to creating the <code>PullBufferStream</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> the <code>Format</code> of which is
     * to be set
     * @param oldValue the last-known <code>Format</code> for the <code>PullBufferStream</code> at the specified <code>streamIndex</code>
     * @param newValue the <code>Format</code> which is to be set
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PullBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PullBufferStream</code> or <code>null</code> if the attempt to set the
     * <code>Format</code> did not success and any last-known <code>Format</code> is to be left in effect
     * @see AbstractPullBufferCaptureDevice#setFormat(int, Format, Format)
     */
    @Override
    protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
    {
        /*
         * Accept format specifications prior to the initialization of AudioRecordStream.
         * Afterwards, AudioRecordStream will decide whether to accept further format specifications.
         */
        return newValue;
    }

    /**
     * Implements an audio <code>PullBufferStream</code> using {@link AudioRecord}.
     */
    private static class AudioRecordStream extends AbstractPullBufferStream<DataSource>
            implements AudioEffect.OnEnableStatusChangeListener
    {
        /**
         * The <code>android.media.AudioRecord</code> which does the actual capturing of audio.
         */
        private AudioRecord audioRecord;

        /**
         * The <code>GainControl</code> through which the volume/gain of captured media is controlled.
         */
        private final GainControl gainControl;

        /**
         * The length in bytes of the media data read into a <code>Buffer</code> via a call to {@link #read(Buffer)}.
         */
        private int length;

        /**
         * The indicator which determines whether this <code>AudioRecordStream</code> is to set the
         * priority of the thread in which its {@link #read(Buffer)} method is executed.
         */
        private boolean setThreadPriority = true;

        /**
         * Initializes a new <code>OpenSLESStream</code> instance which is to have its <code>Format</code>
         * -related information abstracted by a specific <code>FormatControl</code>.
         *
         * @param dataSource the <code>DataSource</code> which is creating the new instance so that it becomes one
         * of its <code>streams</code>
         * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
         * information of the new instance
         */
        public AudioRecordStream(DataSource dataSource, FormatControl formatControl)
        {
            super(dataSource, formatControl);
            MediaServiceImpl mediaServiceImpl = NeomediaActivator.getMediaServiceImpl();
            gainControl = (mediaServiceImpl == null) ? null : (GainControl) mediaServiceImpl.getInputVolumeControl();
        }

        /**
         * Opens a connection to the media source of the associated <code>DataSource</code>.
         *
         * @throws IOException if anything goes wrong while opening a connection to the media source of the
         * associated <code>DataSource</code>
         */
        public synchronized void connect()
                throws IOException
        {
            javax.media.format.AudioFormat af = (javax.media.format.AudioFormat) getFormat();
            int channels = af.getChannels();
            int channelConfig;

            switch (channels) {
                case Format.NOT_SPECIFIED:
                case 1:
                    channelConfig = AudioFormat.CHANNEL_IN_MONO;
                    break;
                case 2:
                    channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                    break;
                default:
                    throw new IOException("channels");
            }

            int sampleSizeInBits = af.getSampleSizeInBits();
            int audioFormat;
            switch (sampleSizeInBits) {
                case 8:
                    audioFormat = AudioFormat.ENCODING_PCM_8BIT;
                    break;
                case 16:
                    audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                    break;
                default:
                    throw new IOException("sampleSizeInBits");
            }

            double sampleRate = af.getSampleRate();
            length = (int) Math.round(20 /* milliseconds */
                    * (sampleRate / 1000) * channels * (sampleSizeInBits / 8.0));

            /*
             * Apart from the thread in which #read(Buffer) is executed, use the thread priority for
             * the thread which will create the AudioRecord.
             */
            setThreadPriority();
            try {
                int minBufferSize = AudioRecord.getMinBufferSize((int) sampleRate, channelConfig, audioFormat);

                audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, (int) sampleRate,
                        channelConfig, audioFormat, Math.max(length, minBufferSize));

                // tries to configure audio effects if available
                configureEffects();
            } catch (IllegalArgumentException iae) {
                throw new IOException(iae);
            }
            setThreadPriority = true;
        }

        /**
         * Configures echo cancellation and noise suppression effects.
         */
        private void configureEffects()
        {
            if (!AndroidUtils.hasAPI(16))
                return;

            // Must enable to improve AEC to avoid audio howling on speaker phone enabled
            aTalkApp.getAudioManager().setMode(AudioManager.MODE_IN_COMMUNICATION);
            AudioSystem audioSystem = AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_AUDIORECORD);

            // Creates echo canceler if available
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler echoCanceller = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
                if (echoCanceller != null) {
                    echoCanceller.setEnableStatusListener(this);
                    echoCanceller.setEnabled(audioSystem.isEchoCancel());
                    Timber.i("Echo cancellation: %s", echoCanceller.getEnabled());
                }
            }

            // Automatic gain control
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl agc = AutomaticGainControl.create(audioRecord.getAudioSessionId());
                if (agc != null) {
                    agc.setEnableStatusListener(this);
                    agc.setEnabled(audioSystem.isAutomaticGainControl());
                    Timber.i("Auto gain control: %s", agc.getEnabled());
                }
            }

            // Creates noise suppressor if available
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnableStatusListener(this);
                    noiseSuppressor.setEnabled(audioSystem.isDenoise());
                    Timber.i("Noise suppressor: %s", noiseSuppressor.getEnabled());
                }
            }
        }

        /**
         * Closes the connection to the media source of the associated <code>DataSource</code>.
         */
        public synchronized void disconnect()
        {
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
                setThreadPriority = true;
            }
        }

        /**
         * Attempts to set the <code>Format</code> of this <code>AbstractBufferStream</code>.
         *
         * @param format the <code>Format</code> to be set as the format of this <code>AbstractBufferStream</code>
         * @return the <code>Format</code> of this <code>AbstractBufferStream</code> or <code>null</code> if the attempt to set
         * the <code>Format</code> did not succeed and any last-known <code>Format</code> is to be left in effect
         * @see AbstractPullBufferStream#doSetFormat(Format)
         */
        @Override
        protected synchronized Format doSetFormat(Format format)
        {
            return (audioRecord == null) ? format : null;
        }

        /**
         * Reads media data from this <code>PullBufferStream</code> into a specific <code>Buffer</code> with blocking.
         *
         * @param buffer the <code>Buffer</code> in which media data is to be read from this <code>PullBufferStream</code>
         * @throws IOException if anything goes wrong while reading media data from this <code>PullBufferStream</code>
         * into the specified <code>buffer</code>  @see javax.media.protocol.PullBufferStream#read(javax.media.Buffer)
         */
        public void read(Buffer buffer)
                throws IOException
        {
            if (setThreadPriority) {
                setThreadPriority = false;
                setThreadPriority();
            }

            Object data = buffer.getData();
            int length = this.length;
            if (data instanceof byte[]) {
                if (((byte[]) data).length < length)
                    data = null;
            }
            else
                data = null;
            if (data == null) {
                data = new byte[length];
                buffer.setData(data);
            }

            int toRead = length;
            byte[] bytes = (byte[]) data;
            int offset = 0;

            buffer.setLength(0);
            while (toRead > 0) {
                int read;
                synchronized (this) {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
                        read = audioRecord.read(bytes, offset, toRead);
                    else
                        break;
                }

                if (read < 0) {
                    throw new IOException(AudioRecord.class.getName() + "#read(byte[], int, int) returned " + read);
                }
                else {
                    buffer.setLength(buffer.getLength() + read);
                    offset += read;
                    toRead -= read;
                }
            }
            buffer.setOffset(0);

            // Apply software gain.
            if (gainControl != null) {
                BasicVolumeControl.applyGain(gainControl, bytes, buffer.getOffset(), buffer.getLength());
            }
        }

        /**
         * Starts the transfer of media data from this <code>AbstractBufferStream</code>.
         * WIll not proceed if mState == STATE_UNINITIALIZED (when mic is disabled)
         *
         * @throws IOException if anything goes wrong while starting the transfer of media data from this
         * <code>AbstractBufferStream</code>
         * @see AbstractBufferStream#start()
         */
        @Override
        public void start()
                throws IOException
        {
            /*
             * Connect upon start because the connect has been delayed to allow this
             * AudioRecordStream to respect requests to set its format.
             */
            synchronized (this) {
                if (audioRecord == null)
                    connect();
            }

            super.start();
            synchronized (this) {
                if ((audioRecord != null)  && (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
                    setThreadPriority = true;
                    audioRecord.startRecording();
                }
            }
        }

        /**
         * Stops the transfer of media data from this <code>AbstractBufferStream</code>.
         *
         * @throws IOException if anything goes wrong while stopping the transfer of media data from this
         * <code>AbstractBufferStream</code>
         * @see AbstractBufferStream#stop()
         */
        @Override
        public void stop()
                throws IOException
        {
            synchronized (this) {
                if ((audioRecord != null)  && (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
                    audioRecord.stop();
                    setThreadPriority = true;
                }
            }
            super.stop();
        }

        @Override
        public void onEnableStatusChange(AudioEffect effect, boolean enabled)
        {
            Timber.i("%s: %s", effect.getDescriptor(), enabled);
        }
    }
}
