/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.audiolevel;

import org.atalk.impl.neomedia.control.ControlsAdapter;

import javax.media.Buffer;
import javax.media.Effect;
import javax.media.Format;
import javax.media.PlugIn;
import javax.media.format.AudioFormat;

/**
 * An {@link javax.media.Effect} implementation which calculates audio levels based on the samples
 * in the <code>Buffer</code> and includes them in the buffer's <code>headerExtension</code> field in the
 * SSRC audio level format specified in RFC6464.
 * <p>
 * The class is based on {@link AudioLevelEffect}, but an important difference is that
 * the actual calculation is performed in the same thread that calls
 * {@link #process(javax.media.Buffer, javax.media.Buffer)}.
 *
 * @author Boris Grozev
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class AudioLevelEffect2 extends ControlsAdapter implements Effect
{
    /**
     * The indicator which determines whether <code>AudioLevelEffect</code> instances are to perform
     * the copying of the data from input <code>Buffer</code>s to output <code>Buffer</code>s themselves
     * (e.g. using {@link System#arraycopy(Object, int, Object, int, int)}).
     */
    private static final boolean COPY_DATA_FROM_INPUT_TO_OUTPUT = true;

    /**
     * The supported audio formats by this effect.
     */
    private Format[] supportedAudioFormats;

    /**
     * Whether this effect is enabled or disabled. If disabled, this <code>Effect</code> will set the
     * RTP header extension of the output buffer to <code>null</code>.
     */
    private boolean enabled = false;

    /**
     * The ID of the RTP header extension for SSRC audio levels, which is to be added by this <code>Effect</code>.
     */
    private byte rtpHeaderExtensionId = -1;

    /**
     * Initializes a new <code>AudioLevelEffect2</code>.
     */
    public AudioLevelEffect2()
    {
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
     * Lists all of the input formats that this codec accepts.
     *
     * @return An array that contains the supported input <code>Formats</code>.
     */
    public Format[] getSupportedInputFormats()
    {
        return supportedAudioFormats;
    }

    /**
     * Lists the output formats that this codec can generate.
     *
     * @param input The <code>Format</code> of the data to be used as input to the plug-in.
     * @return An array that contains the supported output <code>Formats</code>.
     */
    public Format[] getSupportedOutputFormats(Format input)
    {
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
     * @return The <code>Format</code> that was set.
     */
    public Format setInputFormat(Format format)
    {
        return (format instanceof AudioFormat) ? (AudioFormat) format : null;
    }

    /**
     * Sets the format for the data this codec outputs.
     *
     * @param format The <code>Format</code> to be set.
     * @return The <code>Format</code> that was set.
     */
    public Format setOutputFormat(Format format)
    {
        return (format instanceof AudioFormat) ? (AudioFormat) format : null;
    }

    /**
     * Performs the media processing defined by this codec.
     *
     * @param inputBuffer The <code>Buffer</code> that contains the media data to be processed.
     * @param outputBuffer The <code>Buffer</code> in which to store the processed media data.
     * @return <code>BUFFER_PROCESSED_OK</code> if the processing is successful.
     * @see PlugIn
     */
    public int process(Buffer inputBuffer, Buffer outputBuffer)
    {
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

        Object data = outputBuffer.getData();
        Buffer.RTPHeaderExtension ext = outputBuffer.getHeaderExtension();

        if (enabled && rtpHeaderExtensionId != -1 && data instanceof byte[]) {
            byte level = AudioLevelCalculator.calculateAudioLevel((byte[]) data,
                    outputBuffer.getOffset(), outputBuffer.getLength());

            if (ext == null) {
                ext = new Buffer.RTPHeaderExtension(rtpHeaderExtensionId, new byte[1]);
            }

            ext.id = rtpHeaderExtensionId;
            if (ext.value == null || ext.value.length < 1)
                ext.value = new byte[1];
            ext.value[0] = level;
            outputBuffer.setHeaderExtension(ext);
        }
        else {
            // Make sure that the output buffer doesn't retain the extension from a previous payload.
            outputBuffer.setHeaderExtension(null);
        }
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Gets the name of this plug-in as a human-readable string.
     *
     * @return A <code>String</code> that contains the descriptive name of the plug-in.
     */
    public String getName()
    {
        return "Audio Level Effect2";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset()
    {
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Enables or disables this <code>AudioLevelEffect2</code> according to the value of <code>enabled</code>.
     *
     * @param enabled whether to enable or disabled this <code>Effect</code>.
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * Sets the ID of the RTP header extension which will be added.
     *
     * @param rtpHeaderExtensionId the ID to set.
     */
    public void setRtpHeaderExtensionId(byte rtpHeaderExtensionId)
    {
        this.rtpHeaderExtensionId = rtpHeaderExtensionId;
    }
}
