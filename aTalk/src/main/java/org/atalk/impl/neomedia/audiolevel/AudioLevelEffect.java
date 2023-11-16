/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel;

import org.atalk.impl.neomedia.control.ControlsAdapter;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;

import javax.media.Buffer;
import javax.media.Effect;
import javax.media.Format;
import javax.media.PlugIn;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * An effect that would pass data to the <code>AudioLevelEventDispatcher</code> so that it would
 * calculate levels and dispatch changes to interested parties.
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class AudioLevelEffect extends ControlsAdapter
        implements Effect {
    /**
     * The indicator which determines whether <code>AudioLevelEffect</code> instances are to perform
     * the copying of the data from input <code>Buffer</code>s to output <code>Buffer</code>s themselves
     * (e.g. using {@link System#arraycopy(Object, int, Object, int, int)}).
     */
    private static final boolean COPY_DATA_FROM_INPUT_TO_OUTPUT = true;

    /**
     * The <code>SimpleAudioLevelListener</code> which this instance associates with its {@link #eventDispatcher}.
     */
    private SimpleAudioLevelListener audioLevelListener = null;

    /**
     * The dispatcher of the events which handles the calculation and the event firing in different
     * thread in order to now slow down the JMF codec chain.
     */
    private final AudioLevelEventDispatcher eventDispatcher
            = new AudioLevelEventDispatcher("AudioLevelEffect Dispatcher");

    /**
     * The indicator which determines whether {@link #open()} has been called on this instance
     * without an intervening {@link #close()}.
     */
    private boolean open = false;

    /**
     * The supported audio formats by this effect.
     */
    private Format[] supportedAudioFormats;

    /**
     * The minimum and maximum values of the scale
     */
    public AudioLevelEffect() {
        supportedAudioFormats = new Format[]{new AudioFormat(
                AudioFormat.LINEAR,
                Format.NOT_SPECIFIED,
                16,
                1,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED,
                16,
                Format.NOT_SPECIFIED,
                Format.byteArray)
        };
    }

    /**
     * Sets (or unsets if <code>listener</code> is <code>null</code>), the listener that is going to be
     * notified of audio level changes detected by this effect. Given the semantics of the
     * {@link AudioLevelEventDispatcher} this effect would do no real work if no listener is set or
     * if it is set to <code>null</code>.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> that we'd like to receive level changes or
     * <code>null</code> if we'd like level measurements to stop.
     */
    public void setAudioLevelListener(SimpleAudioLevelListener listener) {
        synchronized (eventDispatcher) {
            audioLevelListener = listener;
            if (open)
                eventDispatcher.setAudioLevelListener(audioLevelListener);
        }
    }

    /**
     * Returns the audio level listener.
     *
     * @return the audio level listener or <code>null</code> if it does not exist.
     */
    public SimpleAudioLevelListener getAudioLevelListener() {
        synchronized (eventDispatcher) {
            return audioLevelListener;
        }
    }

    /**
     * Lists all of the input formats that this codec accepts.
     *
     * @return An array that contains the supported input <code>Formats</code>.
     */
    public Format[] getSupportedInputFormats() {
        return supportedAudioFormats;
    }

    /**
     * Lists the output formats that this codec can generate.
     *
     * @param input The <code>Format</code> of the data to be used as input to the plug-in.
     *
     * @return An array that contains the supported output <code>Formats</code>.
     */
    public Format[] getSupportedOutputFormats(Format input) {
        return new Format[]{new AudioFormat(
                AudioFormat.LINEAR,
                ((AudioFormat) input).getSampleRate(),
                16,
                1,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED,
                16,
                Format.NOT_SPECIFIED,
                Format.byteArray)
        };
    }

    /**
     * Sets the format of the data to be input to this codec.
     *
     * @param format The <code>Format</code> to be set.
     *
     * @return The <code>Format</code> that was set.
     */
    public Format setInputFormat(Format format) {
        return (format instanceof AudioFormat) ? (AudioFormat) format : null;
    }

    /**
     * Sets the format for the data this codec outputs.
     *
     * @param format The <code>Format</code> to be set.
     *
     * @return The <code>Format</code> that was set.
     */
    public Format setOutputFormat(Format format) {
        return (format instanceof AudioFormat) ? (AudioFormat) format : null;
    }

    /**
     * Performs the media processing defined by this codec.
     *
     * @param inputBuffer The <code>Buffer</code> that contains the media data to be processed.
     * @param outputBuffer The <code>Buffer</code> in which to store the processed media data.
     *
     * @return <code>BUFFER_PROCESSED_OK</code> if the processing is successful.
     * @see PlugIn
     */
    public int process(Buffer inputBuffer, Buffer outputBuffer) {
        /*
         * In accord with what an Effect is generally supposed to do, copy the data from the inputBuffer into outputBuffer.
         */
        if (COPY_DATA_FROM_INPUT_TO_OUTPUT) {
            // Copy the actual data from the input to the output.
            Object data = outputBuffer.getData();
            int inputBufferLength = inputBuffer.getLength();
            byte[] bufferData;

            if ((data instanceof byte[])
                    && (((byte[]) data).length >= inputBufferLength)) {
                bufferData = (byte[]) data;
            }
            else {
                bufferData = new byte[inputBufferLength];
                outputBuffer.setData(bufferData);
            }
            outputBuffer.setLength(inputBufferLength);
            outputBuffer.setOffset(0);

            System.arraycopy(inputBuffer.getData(), inputBuffer.getOffset(), bufferData, 0, inputBufferLength);

            // Now copy the remaining attributes.
            outputBuffer.setFormat(inputBuffer.getFormat());
            outputBuffer.setHeader(inputBuffer.getHeader());
            outputBuffer.setSequenceNumber(inputBuffer.getSequenceNumber());
            outputBuffer.setTimeStamp(inputBuffer.getTimeStamp());
            outputBuffer.setRtpTimeStamp(inputBuffer.getRtpTimeStamp());
            outputBuffer.setFlags(inputBuffer.getFlags());
            outputBuffer.setDiscard(inputBuffer.isDiscard());
            outputBuffer.setEOM(inputBuffer.isEOM());
            outputBuffer.setDuration(inputBuffer.getDuration());
        }
        else {
            outputBuffer.copy(inputBuffer);
        }

        /*
         * At long last, do the job which this AudioLevelEffect exists for i.e. deliver the data to
         * eventDispatcher so that its audio level gets calculated and delivered to
         * audioEventListener.
         */
        eventDispatcher.addData(outputBuffer);

        return BUFFER_PROCESSED_OK;
    }

    /**
     * Gets the name of this plug-in as a human-readable string.
     *
     * @return A <code>String</code> that contains the descriptive name of the plug-in.
     */
    public String getName() {
        return "Audio Level Effect";
    }

    /**
     * Opens this effect.
     *
     * @throws ResourceUnavailableException If all of the required resources cannot be acquired.
     */
    public void open()
            throws ResourceUnavailableException {
        synchronized (eventDispatcher) {
            if (!open) {
                open = true;
                eventDispatcher.setAudioLevelListener(audioLevelListener);
            }
        }
    }

    /**
     * Closes this effect.
     */
    public void close() {
        synchronized (eventDispatcher) {
            if (open) {
                open = false;
                eventDispatcher.setAudioLevelListener(null);
            }
        }
    }

    /**
     * Resets its state.
     */
    public void reset() {
    }
}
