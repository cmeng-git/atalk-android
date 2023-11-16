/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio;

import org.atalk.impl.neomedia.control.DiagnosticsControl;
import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.device.PortAudioSystem;
import org.atalk.impl.neomedia.device.UpdateAvailableDeviceListListener;
import org.atalk.impl.neomedia.jmfext.media.protocol.portaudio.DataSource;
import org.atalk.impl.neomedia.jmfext.media.protocol.portaudio.PortAudioStream;
import org.atalk.impl.neomedia.portaudio.Pa;
import org.atalk.impl.neomedia.portaudio.PortAudioException;
import org.atalk.service.neomedia.BasicVolumeControl;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.GainControl;
import javax.media.MediaLocator;
import javax.media.PlugIn;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * Implements an audio <code>Renderer</code> which uses Pa.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class PortAudioRenderer extends AbstractAudioRenderer<PortAudioSystem>
{
    /**
     * The constant which represents an empty array with <code>Format</code> element type. Explicitly
     * defined in order to reduce unnecessary allocations.
     */
    private static final Format[] EMPTY_SUPPORTED_INPUT_FORMATS = new Format[0];

    /**
     * The flag which indicates that {@link #open()} has been called on a
     * <code>PortAudioRenderer</code> without an intervening {@link #close()}. The state it
     * represents is from the public point of view. The private point of view is represented by
     * {@link #stream}.
     */
    private static final byte FLAG_OPEN = 1;

    /**
     * The flag which indicates that {@link #start()} has been called on a
     * <code>PortAudioRenderer</code> without an intervening {@link #stop()}. The state it
     * represents is from the public point of view. The private point of view is represented by
     * {@link #started}.
     */
    private static final byte FLAG_STARTED = 2;

    /**
     * The human-readable name of the <code>PortAudioRenderer</code> JMF plug-in.
     */
    private static final String PLUGIN_NAME = "PortAudio Renderer";

    /**
     * The list of JMF <code>Format</code>s of audio data which <code>PortAudioRenderer</code> instances
     * are capable of rendering.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of the sample rates supported by <code>PortAudioRenderer</code> as input.
     */
    private static final double[] SUPPORTED_INPUT_SAMPLE_RATES = new double[]{8000, 11025, 16000,
            22050, 32000, 44100, 48000};

    static {
        int count = SUPPORTED_INPUT_SAMPLE_RATES.length;

        SUPPORTED_INPUT_FORMATS = new Format[count];
        for (int i = 0; i < count; i++) {
            SUPPORTED_INPUT_FORMATS[i] = new AudioFormat(
                    AudioFormat.LINEAR,
                    SUPPORTED_INPUT_SAMPLE_RATES[i],
                    16 /* sampleSizeInBits */,
                    Format.NOT_SPECIFIED /* channels */,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */,
                    Format.byteArray);
        }
    }

    /**
     * The audio samples left unwritten by a previous call to {@link #process(Buffer)}. As
     * {@link #bytesPerBuffer} number of bytes are always written, the number of the unwritten
     * audio samples is always less than that.
     */
    private byte[] bufferLeft;

    /**
     * The number of bytes in {@link #bufferLeft} representing unwritten audio samples.
     */
    private int bufferLeftLength = 0;

    /**
     * The number of bytes to write to the native PortAudio stream represented by this instance
     * with a single invocation. Based on {@link #framesPerBuffer}.
     */
    private int bytesPerBuffer;

    /**
     * The <code>DiagnosticsControl</code> implementation of this instance which allows the
     * diagnosis of the functional health of <code>Pa_WriteStream</code>.
     */
    private final DiagnosticsControl diagnosticsControl = new DiagnosticsControl()
    {
        /**
         * {@inheritDoc}
         *
         * <code>PortAudioRenderer</code>'s <code>DiagnosticsControl</code> implementation does not provide
         * its own user interface and always returns <code>null</code>.
         */
        public Component getControlComponent()
        {
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public long getMalfunctioningSince()
        {
            return writeIsMalfunctioningSince;
        }

        /**
         * {@inheritDoc}
         *
         * Returns the identifier of the PortAudio device written through this
         * <code>PortAudioRenderer</code>.
         */
        @Override
        public String toString()
        {
            MediaLocator locator = getLocator();
            String name = null;

            if (locator != null) {
                String id = DataSource.getDeviceID(locator);

                if (id != null) {
                    int index = Pa.getDeviceIndex(id,
                            0 /* minInputChannels */,
                            1 /* minOutputChannels */);

                    if (index != Pa.paNoDevice) {
                        long info = Pa.GetDeviceInfo(index);

                        if (info != 0)
                            name = Pa.DeviceInfo_getName(info);
                    }
                }
            }
            return name;
        }
    };

    /**
     * The flags which represent certain state of this <code>PortAudioRenderer</code>. Acceptable
     * values
     * are among the <code>FLAG_XXX</code> constants defined by the <code>PortAudioRenderer</code> class.
     * For example, {@link #FLAG_OPEN} indicates that from the public point of view {@link #open()}
     * has been invoked on this <code>Renderer</code> without an intervening {@link #close()}.
     */
    private byte flags = 0;

    /**
     * The number of frames to write to the native PortAudio stream represented by this instance
     * with a single invocation.
     */
    private int framesPerBuffer;

    private long outputParameters = 0;

    /**
     * The <code>PaUpdateAvailableDeviceListListener</code> which is to be notified before and after
     * PortAudio's native function <code>Pa_UpdateAvailableDeviceList()</code> is invoked. It will
     * close
     * {@link #stream} before the invocation in order to mitigate memory corruption afterwards and
     * it will attempt to restore the state of this <code>Renderer</code> after the invocation.
     */
    private final UpdateAvailableDeviceListListener paUpdateAvailableDeviceListListener
            = new UpdateAvailableDeviceListListener()
    {
        @Override
        public void didUpdateAvailableDeviceList()
                throws Exception
        {
            synchronized (PortAudioRenderer.this) {
                waitWhileStreamIsBusy();

                /*
                 * PortAudioRenderer's field flags represents its open and started state from the
                 * public point of view. We will automatically open and start this Renderer i.e. we
                 * will be modifying the state from the private point of view only and,
                 * consequently, we have to make sure that we will not modify it from the public
                 * point of view.
                 */
                byte flags = PortAudioRenderer.this.flags;

                try {
                    if ((FLAG_OPEN & flags) == FLAG_OPEN) {
                        open();
                        if ((FLAG_STARTED & flags) == FLAG_STARTED)
                            start();
                    }
                } finally {
                    PortAudioRenderer.this.flags = flags;
                }
            }
        }

        @Override
        public void willUpdateAvailableDeviceList()
                throws Exception
        {
            synchronized (PortAudioRenderer.this) {
                waitWhileStreamIsBusy();

                /*
                 * PortAudioRenderer's field flags represents its open and started state from the
                 * public point of view. We will automatically close this Renderer i.e. we will be
                 * modifying the state from the private point of view only and, consequently, we
                 * have to make sure that we will not modify it from the public point of view.
                 */
                byte flags = PortAudioRenderer.this.flags;
                try {
                    if (stream != 0)
                        close();
                } finally {
                    PortAudioRenderer.this.flags = flags;
                }
            }
        }
    };

    /**
     * The indicator which determines whether this <code>Renderer</code> is started.
     */
    private boolean started = false;

    /**
     * The output PortAudio stream represented by this instance.
     */
    private long stream = 0;

    /**
     * The indicator which determines whether {@link #stream} is busy and should not, for example,
     * be closed.
     */
    private boolean streamIsBusy = false;

    /**
     * Array of supported input formats.
     */
    private Format[] supportedInputFormats;

    /**
     * The time in milliseconds at which <code>Pa_WriteStream</code> has started malfunctioning. For
     * example, <code>Pa_WriteStream</code> returning <code>paTimedOut</code> and/or Windows Multimedia
     * reporting <code>MMSYSERR_NODRIVER</code> (may) indicate abnormal functioning.
     */
    private long writeIsMalfunctioningSince = DiagnosticsControl.NEVER;

    /**
     * Initializes a new <code>PortAudioRenderer</code> instance.
     */
    public PortAudioRenderer()
    {
        this(true);
    }

    /**
     * Initializes a new <code>PortAudioRenderer</code> instance which is to either perform playback or
     * sound a notification.
     *
     * @param playback <code>true</code> if the new instance is to perform playback or <code>false</code> if the new
     * instance is to sound a notification
     */
    public PortAudioRenderer(boolean enableVolumeControl)
    {
        super(AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO,
                enableVolumeControl ? AudioSystem.DataFlow.PLAYBACK : AudioSystem.DataFlow.NOTIFY);

        /*
         * XXX We will add a PaUpdateAvailableDeviceListListener and will not remove it because we
         * will rely on PortAudioSystem's use of WeakReference.
         */
        if (audioSystem != null) {
            audioSystem.addUpdateAvailableDeviceListListener(paUpdateAvailableDeviceListListener);
        }
    }

    /**
     * Closes this <code>PlugIn</code>.
     */
    @Override
    public synchronized void close()
    {
        try {
            stop();
        } finally {
            if (stream != 0) {
                try {
                    Pa.CloseStream(stream);
                    stream = 0;
                    started = false;
                    flags &= ~(FLAG_OPEN | FLAG_STARTED);

                    if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                        setWriteIsMalfunctioning(false);
                } catch (PortAudioException paex) {
                    Timber.e("paex. Failed to close PortAudio stream.");
                }
            }
            if ((stream == 0) && (outputParameters != 0)) {
                Pa.StreamParameters_free(outputParameters);
                outputParameters = 0;
            }
            super.close();
        }
    }

    /**
     * Gets the descriptive/human-readable name of this JMF plug-in.
     *
     * @return the descriptive/human-readable name of this JMF plug-in
     */
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Gets the list of JMF <code>Format</code>s of audio data which this <code>Renderer</code> is capable
     * of rendering.
     *
     * @return an array of JMF <code>Format</code>s of audio data which this <code>Renderer</code> is
     * capable of rendering
     */
    @Override
    public Format[] getSupportedInputFormats()
    {
        if (supportedInputFormats == null) {
            MediaLocator locator = getLocator();
            String deviceID;
            int deviceIndex;
            long deviceInfo;

            if ((locator == null) || ((deviceID = DataSource.getDeviceID(locator)) == null)
                    || (deviceID.length() == 0)
                    || ((deviceIndex = Pa.getDeviceIndex(deviceID,
                    0 /* minInputChannels */,
                    1 /* minOutputChannels */)) == Pa.paNoDevice)
                    || ((deviceInfo = Pa.GetDeviceInfo(deviceIndex)) == 0)) {
                supportedInputFormats = SUPPORTED_INPUT_FORMATS;
            }
            else {
                int minOutputChannels = 1;
                /*
                 * The maximum output channels may be a lot and checking all of them will take a
                 * lot of time. Besides, we currently support at most 2.
                 */
                int maxOutputChannels = Math.min(Pa.DeviceInfo_getMaxOutputChannels(deviceInfo), 2);
                List<Format> supportedInputFormats
                        = new ArrayList<>(SUPPORTED_INPUT_FORMATS.length);

                for (Format supportedInputFormat : SUPPORTED_INPUT_FORMATS) {
                    getSupportedInputFormats(supportedInputFormat, deviceIndex, minOutputChannels,
                            maxOutputChannels, supportedInputFormats);
                }
                this.supportedInputFormats = supportedInputFormats.isEmpty() ?
                        EMPTY_SUPPORTED_INPUT_FORMATS
                        : supportedInputFormats.toArray(EMPTY_SUPPORTED_INPUT_FORMATS);
            }
        }
        return (supportedInputFormats.length == 0)
                ? EMPTY_SUPPORTED_INPUT_FORMATS
                : supportedInputFormats.clone();
    }

    private void getSupportedInputFormats(Format format, int deviceIndex, int minOutputChannels,
            int maxOutputChannels, List<Format> supportedInputFormats)
    {
        AudioFormat audioFormat = (AudioFormat) format;
        int sampleSizeInBits = audioFormat.getSampleSizeInBits();
        long sampleFormat = Pa.getPaSampleFormat(sampleSizeInBits);
        double sampleRate = audioFormat.getSampleRate();

        for (int channels = minOutputChannels; channels <= maxOutputChannels; channels++) {
            long outputParameters = Pa.StreamParameters_new(deviceIndex, channels, sampleFormat,
                    Pa.LATENCY_UNSPECIFIED);

            if (outputParameters != 0) {
                try {
                    if (Pa.IsFormatSupported(0, outputParameters, sampleRate)) {
                        supportedInputFormats.add(new AudioFormat(
                                audioFormat.getEncoding(),
                                sampleRate,
                                sampleSizeInBits,
                                channels,
                                audioFormat.getEndian(),
                                audioFormat.getSigned(),
                                Format.NOT_SPECIFIED /* frameSizeInBits */,
                                Format.NOT_SPECIFIED /* frameRate */,
                                audioFormat.getDataType()));
                    }
                } finally {
                    Pa.StreamParameters_free(outputParameters);
                }
            }
        }
    }

    /**
     * Opens the PortAudio device and output stream represented by this instance which are to be
     * used to render audio.
     *
     * @throws ResourceUnavailableException if the PortAudio device or output stream cannot be created or opened
     */
    @Override
    public synchronized void open()
            throws ResourceUnavailableException
    {
        try {
            audioSystem.willOpenStream();
            try {
                doOpen();
            } finally {
                audioSystem.didOpenStream();
            }
        } catch (Throwable t) {
            /*
             * Log the problem because FMJ may swallow it and thus make debugging harder than necessary.
             */
            Timber.d(t, "Failed to open PortAudioRenderer");

            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else if (t instanceof ResourceUnavailableException)
                throw (ResourceUnavailableException) t;
            else {
                ResourceUnavailableException rue = new ResourceUnavailableException();
                rue.initCause(t);
                throw rue;
            }
        }
        super.open();
    }

    /**
     * Opens the PortAudio device and output stream represented by this instance which are to be
     * used to render audio.
     *
     * @throws ResourceUnavailableException if the PortAudio device or output stream cannot be created or opened
     */
    private void doOpen()
            throws ResourceUnavailableException
    {
        if (stream == 0) {
            MediaLocator locator = getLocator();

            if (locator == null) {
                throw new ResourceUnavailableException("No locator/MediaLocator is set.");
            }

            String deviceID = DataSource.getDeviceID(locator);
            int deviceIndex = Pa.getDeviceIndex(deviceID,
                    0 /* minInputChannels */,
                    1 /* minOutputChannels */);

            if (deviceIndex == Pa.paNoDevice) {
                throw new ResourceUnavailableException("The audio device " + deviceID
                        + " appears to be disconnected.");
            }

            AudioFormat inputFormat = this.inputFormat;

            if (inputFormat == null)
                throw new ResourceUnavailableException("inputFormat not set");

            int channels = inputFormat.getChannels();
            if (channels == Format.NOT_SPECIFIED)
                channels = 1;

            long sampleFormat = Pa.getPaSampleFormat(inputFormat.getSampleSizeInBits());
            double sampleRate = inputFormat.getSampleRate();

            framesPerBuffer = (int) ((sampleRate * Pa.DEFAULT_MILLIS_PER_BUFFER)
                    / (channels * 1000));

            try {
                outputParameters = Pa.StreamParameters_new(deviceIndex, channels, sampleFormat,
                        Pa.getSuggestedLatency());

                stream = Pa.OpenStream(0 /* inputParameters */,
                        outputParameters,
                        sampleRate, framesPerBuffer,
                        Pa.STREAM_FLAGS_CLIP_OFF | Pa.STREAM_FLAGS_DITHER_OFF,
                        null /* streamCallback */);
            } catch (PortAudioException paex) {
                Timber.e(paex, "Failed to open PortAudio stream.");
                throw new ResourceUnavailableException(paex.getMessage());
            } finally {
                started = false;
                if (stream == 0) {
                    flags &= ~(FLAG_OPEN | FLAG_STARTED);

                    if (outputParameters != 0) {
                        Pa.StreamParameters_free(outputParameters);
                        outputParameters = 0;
                    }
                }
                else {
                    flags |= (FLAG_OPEN | FLAG_STARTED);
                }
            }
            if (stream == 0)
                throw new ResourceUnavailableException("Pa_OpenStream");

            bytesPerBuffer = Pa.GetSampleSize(sampleFormat) * channels * framesPerBuffer;

            // Pa_WriteStream has not been invoked yet.
            if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                setWriteIsMalfunctioning(false);
        }
    }

    /**
     * Notifies this instance that the value of the {@link AudioSystem#PROP_PLAYBACK_DEVICE}
     * property of its associated <code>AudioSystem</code> has changed.
     *
     * @param ev a <code>PropertyChangeEvent</code> which specifies details about the change such as the
     * name of the property and its old and new values
     */
    @Override
    protected synchronized void playbackDevicePropertyChange(PropertyChangeEvent ev)
    {
        /*
         * Stop, close, re-open and re-start this Renderer (performing whichever of these in order
         * to bring it into the same state) in order to reflect the change in the selection with
         * respect to the playback device.
         */

        waitWhileStreamIsBusy();

        /*
         * From the public point of view, the state of this PortAudioRenderer remains the same.
         */
        byte flags = this.flags;

        try {
            if ((FLAG_OPEN & flags) == FLAG_OPEN) {
                close();

                try {
                    open();
                } catch (ResourceUnavailableException rue) {
                    throw new UndeclaredThrowableException(rue);
                }
                if ((FLAG_STARTED & flags) == FLAG_STARTED)
                    start();
            }
        } finally {
            this.flags = flags;
        }
    }

    /**
     * Renders the audio data contained in a specific <code>Buffer</code> onto the PortAudio device
     * represented by this <code>Renderer</code>.
     *
     * @param buffer the <code>Buffer</code> which contains the audio data to be rendered
     * @return <code>BUFFER_PROCESSED_OK</code> if the specified <code>buffer</code> has been successfully
     * processed
     */
    public int process(Buffer buffer)
    {
        synchronized (this) {
            if (!started || (stream == 0)) {
                /*
                 * The execution is somewhat abnormal but it is not because of a malfunction in
                 * Pa_WriteStream.
                 */
                if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                    setWriteIsMalfunctioning(false);

                return PlugIn.BUFFER_PROCESSED_OK;
            }
            else
                streamIsBusy = true;
        }

        long errorCode = Pa.paNoError;
        Pa.HostApiTypeId hostApiType = null;

        try {
            process((byte[]) buffer.getData(), buffer.getOffset(), buffer.getLength());
        } catch (PortAudioException pae) {
            errorCode = pae.getErrorCode();
            hostApiType = pae.getHostApiType();

            Timber.e(pae, "Failed to process Buffer.");
        } finally {
            /*
             * If a timeout has occurred in the method Pa.WriteStream, give the application a
             * little time to allow it to possibly get its act together. The same treatment sounds
             * appropriate on Windows as soon as the wmme host API starts reporting that no device
             * driver is present.
             */
            boolean yield = false;

            synchronized (this) {
                streamIsBusy = false;
                notifyAll();

                if (errorCode == Pa.paNoError) {
                    // Pa_WriteStream appears to function normally.
                    if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                        setWriteIsMalfunctioning(false);
                }
                else if ((Pa.paTimedOut == errorCode)
                        || (Pa.HostApiTypeId.paMME.equals(
                        hostApiType) && (Pa.MMSYSERR_NODRIVER == errorCode))) {
                    if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER)
                        setWriteIsMalfunctioning(true);
                    yield = true;
                }
            }

            if (yield)
                PortAudioStream.yield();
        }
        return PlugIn.BUFFER_PROCESSED_OK;
    }

    private void process(byte[] buffer, int offset, int length)
            throws PortAudioException
    {

        /*
         * If there are audio samples left unwritten from a previous write, prepend them to the
         * specified buffer. If it's possible to write them now, do it.
         */
        if ((bufferLeft != null) && (bufferLeftLength > 0)) {
            int numberOfBytesInBufferLeftToBytesPerBuffer = bytesPerBuffer - bufferLeftLength;
            int numberOfBytesToCopyToBufferLeft
                    = (numberOfBytesInBufferLeftToBytesPerBuffer < length)
                    ? numberOfBytesInBufferLeftToBytesPerBuffer
                    : length;

            System.arraycopy(buffer, offset, bufferLeft, bufferLeftLength,
                    numberOfBytesToCopyToBufferLeft);
            offset += numberOfBytesToCopyToBufferLeft;
            length -= numberOfBytesToCopyToBufferLeft;
            bufferLeftLength += numberOfBytesToCopyToBufferLeft;

            if (bufferLeftLength == bytesPerBuffer) {
                Pa.WriteStream(stream, bufferLeft, framesPerBuffer);
                bufferLeftLength = 0;
            }
        }

        // Write the audio samples from the specified buffer.
        int numberOfWrites = length / bytesPerBuffer;

        if (numberOfWrites > 0) {
            /*
             * Take into account the user's preferences with respect to the output volume.
             */
            GainControl gainControl = getGainControl();

            if (gainControl != null) {
                BasicVolumeControl.applyGain(gainControl, buffer, offset, length);
            }

            Pa.WriteStream(stream, buffer, offset, framesPerBuffer, numberOfWrites);
            int bytesWritten = numberOfWrites * bytesPerBuffer;

            offset += bytesWritten;
            length -= bytesWritten;
        }

        // If anything was left unwritten, remember it for next time.
        if (length > 0) {
            if (bufferLeft == null)
                bufferLeft = new byte[bytesPerBuffer];
            System.arraycopy(buffer, offset, bufferLeft, 0, length);
            bufferLeftLength = length;
        }
    }

    /**
     * Sets the <code>MediaLocator</code> which specifies the device index of the PortAudio device
     * to be used by this instance for rendering.
     *
     * @param locator a <code>MediaLocator</code> which specifies the device index of the PortAudio device to be
     * used by this instance for rendering
     */
    @Override
    public void setLocator(MediaLocator locator)
    {
        super.setLocator(locator);

        supportedInputFormats = null;
    }

    /**
     * Indicates whether <code>Pa_WriteStream</code> is malfunctioning.
     *
     * @param writeIsMalfunctioning <code>true</code> if <code>Pa_WriteStream</code> is malfunctioning; otherwise, <code>false</code>
     */
    private void setWriteIsMalfunctioning(boolean writeIsMalfunctioning)
    {
        if (writeIsMalfunctioning) {
            if (writeIsMalfunctioningSince == DiagnosticsControl.NEVER) {
                writeIsMalfunctioningSince = System.currentTimeMillis();
                PortAudioSystem.monitorFunctionalHealth(diagnosticsControl);
            }
        }
        else
            writeIsMalfunctioningSince = DiagnosticsControl.NEVER;
    }

    /**
     * Starts the rendering process. Any audio data available in the internal resources associated
     * with this <code>PortAudioRenderer</code> will begin being rendered.
     */
    public synchronized void start()
    {
        if (!started && (stream != 0)) {
            try {
                Pa.StartStream(stream);
                started = true;
                flags |= FLAG_STARTED;
            } catch (PortAudioException paex) {
                Timber.e(paex, "Failed to start PortAudio stream.");
            }
        }
    }

    /**
     * Stops the rendering process.
     */
    public synchronized void stop()
    {
        waitWhileStreamIsBusy();
        if (started && (stream != 0)) {
            try {
                Pa.StopStream(stream);
                started = false;
                flags &= ~FLAG_STARTED;

                bufferLeft = null;
                if (writeIsMalfunctioningSince != DiagnosticsControl.NEVER)
                    setWriteIsMalfunctioning(false);
            } catch (PortAudioException paex) {
                Timber.e(paex, "Failed to close PortAudio stream.");
            }
        }
    }

    /**
     * Waits on this instance while {@link #streamIsBusy} is equal to <code>true</code> i.e. until it
     * becomes <code>false</code>. The method should only be called by a thread that is the owner of
     * this object's monitor.
     */
    private void waitWhileStreamIsBusy()
    {
        boolean interrupted = false;

        while (streamIsBusy) {
            try {
                wait();
            } catch (InterruptedException iex) {
                interrupted = true;
            }
        }
        if (interrupted)
            Thread.currentThread().interrupt();
    }
}
