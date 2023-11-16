/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio;

import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource;
import org.atalk.service.neomedia.BasicVolumeControl;
import org.atalk.service.neomedia.codec.Constants;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.GainControl;
import javax.media.PlugIn;
import javax.media.Renderer;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * Implements an audio <code>Renderer</code> which uses OpenSL ES.
 *
 * @author Lyubomir Marinov
 */
public class OpenSLESRenderer extends AbstractAudioRenderer<AudioSystem>
{
    /**
     * The human-readable name of the <code>OpenSLESRenderer</code> FMJ plug-in.
     */
    private static final String PLUGIN_NAME = "OpenSL ES Renderer";

    /**
     * The list of input <code>Format</code>s supported by <code>OpenSLESRenderer</code> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    static {
        System.loadLibrary("jnopensles");

        double[] supportedInputSampleRates = Constants.AUDIO_SAMPLE_RATES;
        int supportedInputSampleRateCount = supportedInputSampleRates.length;

        SUPPORTED_INPUT_FORMATS = new Format[supportedInputSampleRateCount];
        for (int i = 0; i < supportedInputSampleRateCount; i++) {
            SUPPORTED_INPUT_FORMATS[i] = new AudioFormat(
                    AudioFormat.LINEAR,
                    supportedInputSampleRates[i],
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
     * The <code>GainControl</code> through which the volume/gain of rendered media is controlled.
     */
    private final GainControl gainControl;

    private long ptr;

    /**
     * The indicator which determines whether this <code>OpenSLESRenderer</code> is to set the priority
     * of the thread in which its {@link #process(Buffer)} method is executed.
     */
    private boolean setThreadPriority = true;

    /**
     * Initializes a new <code>OpenSLESRenderer</code> instance.
     */
    public OpenSLESRenderer()
    {
        this(true);
    }

    /**
     * Initializes a new <code>OpenSLESRenderer</code> instance.
     *
     * @param enableGainControl <code>true</code> to enable controlling the volume/gain of the rendered media; otherwise, <code>false</code>
     */
    public OpenSLESRenderer(boolean enableGainControl)
    {
        super(AudioSystem.getAudioSystem(AudioSystem.LOCATOR_PROTOCOL_OPENSLES));
        if (enableGainControl) {
            MediaServiceImpl mediaServiceImpl = NeomediaActivator.getMediaServiceImpl();
            gainControl = (mediaServiceImpl == null) ? null : (GainControl) mediaServiceImpl.getOutputVolumeControl();
        }
        else
            gainControl = null;
    }

    /**
     * Implements {@link PlugIn#close()}. Closes this {@link PlugIn} and releases its resources.
     *
     * @see PlugIn#close()
     */
    public synchronized void close()
    {
        if (ptr != 0) {
            close(ptr);
            ptr = 0;
            setThreadPriority = true;
        }
    }

    private static native void close(long ptr);

    /**
     * Gets the descriptive/human-readable name of this FMJ plug-in.
     *
     * @return the descriptive/human-readable name of this FMJ plug-in
     */
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * Gets the list of input <code>Format</code>s supported by this <code>OpenSLESRenderer</code>.
     *
     * @return the list of input <code>Format</code>s supported by this <code>OpenSLESRenderer</code>
     */
    public Format[] getSupportedInputFormats()
    {
        return SUPPORTED_INPUT_FORMATS.clone();
    }

    /**
     * Implements {@link PlugIn#open()}. Opens this {@link PlugIn} and acquires the resources that
     * it needs to operate.
     *
     * @throws ResourceUnavailableException if any of the required resources cannot be acquired
     * @see PlugIn#open()
     */
    public synchronized void open()
            throws ResourceUnavailableException
    {
        if (ptr == 0) {
            AudioFormat inputFormat = this.inputFormat;
            int channels = inputFormat.getChannels();

            if (channels == Format.NOT_SPECIFIED)
                channels = 1;

            /*
             * Apart from the thread in which #process(Buffer) is executed, use the thread priority
             * for the thread which will create the OpenSL ES Audio Player.
             */
            DataSource.setThreadPriority();
            ptr = open(inputFormat.getEncoding(), inputFormat.getSampleRate(),
                    inputFormat.getSampleSizeInBits(), channels, inputFormat.getEndian(),
                    inputFormat.getSigned(), inputFormat.getDataType());
            if (ptr == 0)
                throw new ResourceUnavailableException();

            setThreadPriority = true;
        }
    }

    private static native long open(String encoding, double sampleRate, int sampleSizeInBits,
            int channels, int endian, int signed, Class<?> dataType)
            throws ResourceUnavailableException;

    /**
     * Implements {@link Renderer#process(Buffer)}. Processes the media data contained in a specific
     * {@link Buffer} and renders it to the output device represented by this <code>Renderer</code>.
     *
     * @param buffer the <code>Buffer</code> containing the media data to be processed and rendered to the
     * output device represented by this <code>Renderer</code>
     * @return one or a combination of the constants defined in {@link PlugIn}
     * @see Renderer#process(Buffer)
     */
    public int process(Buffer buffer)
    {
        if (setThreadPriority) {
            setThreadPriority = false;
            DataSource.setThreadPriority();
        }

        Format format = buffer.getFormat();
        int processed;

        if ((format == null) || ((inputFormat != null) && inputFormat.matches(format))) {
            Object data = buffer.getData();
            int length = buffer.getLength();
            int offset = buffer.getOffset();

            if ((data == null) || (length == 0)) {
                /*
                 * There is really no actual data to be processed by this OpenSLESRenderer.
                 */
                processed = BUFFER_PROCESSED_OK;
            }
            else if ((length < 0) || (offset < 0)) {
                /* The length and/or the offset of the Buffer are not valid. */
                processed = BUFFER_PROCESSED_FAILED;
            }
            else {
                synchronized (this) {
                    if (ptr == 0) {
                        /*
                         * This OpenSLESRenderer is not in a state in which it can process the data
                         * of the Buffer.
                         */
                        processed = BUFFER_PROCESSED_FAILED;
                    }
                    else {
                        // Apply software gain.
                        if (gainControl != null) {
                            BasicVolumeControl.applyGain(gainControl, (byte[]) data, offset, length);
                        }
                        processed = process(ptr, data, offset, length);
                    }
                }
            }
        }
        else {
            /*
             * This OpenSLESRenderer does not understand the format of the Buffer.
             */
            processed = BUFFER_PROCESSED_FAILED;
        }
        return processed;
    }

    private static native int process(long ptr, Object data, int offset, int length);

    /**
     * Implements {@link Renderer#start()}. Starts rendering to the output device represented by
     * this <code>Renderer</code>.
     *
     * @see Renderer#start()
     */
    public synchronized void start()
    {
        if (ptr != 0) {
            setThreadPriority = true;
            start(ptr);
        }
    }

    private static native void start(long ptr);

    /**
     * Implements {@link Renderer#stop()}. Stops rendering to the output device represented by this
     * <code>Renderer</code>.
     *
     * @see Renderer#stop()
     */
    public synchronized void stop()
    {
        if (ptr != 0) {
            stop(ptr);
            setThreadPriority = true;
        }
    }

    private static native void stop(long ptr);
}
