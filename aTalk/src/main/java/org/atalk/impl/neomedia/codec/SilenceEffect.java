/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec;

import java.util.Arrays;

import javax.media.Buffer;
import javax.media.Effect;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * An <code>Effect</code> which detects discontinuities in an audio stream by monitoring the input
 * <code>Buffer</code>s' timestamps and lengths, and inserts silence to account for missing data.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class SilenceEffect extends AbstractCodec2 implements Effect
{
    /**
     * The indicator which determines whether <code>SilenceEffect</code> instances are to perform the
     * copying of the data from input <code>Buffer</code>s to output <code>Buffer</code>s themselves (e.g.
     * using {@link System#arraycopy(Object, int, Object, int, int)}).
     */
    private static final boolean COPY_DATA_FROM_INPUT_TO_OUTPUT = true;

    /**
     * The name of this <code>PlugIn</code>.
     */
    private static final String NAME = "Silence Effect";

    /**
     * The maximum number of samples of silence to insert in a single <code>Buffer</code>.
     */
    private static final int MAX_SAMPLES_PER_PACKET = 48000;

    /**
     * The sample rate of the input/output format.
     */
    private static final int sampleRate = 48000;

    /**
     * The size of a single sample of input in bits.
     */
    private static final int sampleSizeInBits = 16;

    /**
     * Max samples of silence to insert between two <code>Buffer</code>s.
     */
    private static final int MAX_SAMPLES_SILENCE = sampleRate * 3; // 3sec

    /**
     * The <code>Format</code>s supported as input/output by this <code>Effect</code>.
     */
    public static final Format[] SUPPORTED_FORMATS = new Format[]{new AudioFormat(
            AudioFormat.LINEAR,
            sampleRate,
            sampleSizeInBits,
            1, //channels
            Format.NOT_SPECIFIED, //endian
            Format.NOT_SPECIFIED) //signed/unsigned
    };

    /**
     * Whether to use the input <code>Buffer</code>s' RTP timestamps (with
     * <code>Buffer.getRtpTimestamp()</code>), or their "regular" timestamps (with
     * <code>Buffer.getTimestamp()</code>).
     */
    private final boolean useRtpTimestamp;

    /**
     * The clock rate for the timestamps of input <code>Buffer</code>s (i.e. the number of units which
     * constitute one second).
     */
    private final int clockRate;

    /**
     * The total number of samples of silence inserted by this instance.
     */
    private int totalSamplesInserted = 0;

    /**
     * The timestamp (either the RTP timestamp, or the <code>Buffer</code>'s timestamp, according to the
     * value of {@link #useRtpTimestamp}) of the last sample that was output by this <code>Codec</code>.
     */
    private long lastOutputTimestamp = Buffer.TIME_UNKNOWN;

    private Listener listener = null;

    /**
     * Initializes a new <code>SilenceEffect</code>, which is to use the input <code>Buffer</code>s'
     * timestamps (as opposed to using their RTP timestamps).
     */
    public SilenceEffect()
    {
        super(NAME, AudioFormat.class, SUPPORTED_FORMATS);

        this.useRtpTimestamp = false;
        // Buffer.getTimestamp() will be used, which is in nanoseconds.
        this.clockRate = 1000 * 1000 * 1000;
    }

    /**
     * Initializes a new <code>SilenceEffect</code>, which is to use the input <code>Buffer</code>s' RTP
     * timestamps.
     *
     * @param rtpClockRate the clock rate that the RTP timestamps use.
     */
    public SilenceEffect(int rtpClockRate)
    {
        super(NAME, AudioFormat.class, SUPPORTED_FORMATS);
        this.useRtpTimestamp = true;
        this.clockRate = rtpClockRate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
        Timber.i("Closing SilenceEffect, inserted a total of %d samples of silence.", totalSamplesInserted);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException
    {
    }

    /**
     * Processes <code>inBuf</code>, and either copies its data to <code>outBuf</code> or copies silence
     *
     * @param inBuf the input <code>Buffer</code>.
     * @param outBuf the output <code>Buffer</code>.
     * @return <code>BUFFER_PROCESSED_OK</code> if <code>inBuf</code>'s date was copied to <code>outBuf</code>,
     * and <code>INPUT_BUFFER_NOT_CONSUMED</code> if silence was inserted instead.
     */
    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        boolean useInput = true;
        long timestamp = useRtpTimestamp ? inBuf.getRtpTimeStamp() : inBuf.getTimeStamp();

        if (timestamp == Buffer.TIME_UNKNOWN) {
            // if the current Buffer's timestamp is unknown, we don't know how
            // much silence to insert, so we let the Buffer pass and reset our state.
            lastOutputTimestamp = Buffer.TIME_UNKNOWN;
        }
        else if (lastOutputTimestamp == Buffer.TIME_UNKNOWN) {
            // Initialize lastOutputTimestamp. The samples from the current
            // buffer will be added below.

            lastOutputTimestamp = timestamp;

            if (listener != null)
                listener.onSilenceNotInserted(timestamp);
        }
        else // timestamp != -1 && lastOutputTimestamp != -1
        {
            long diff = timestamp - lastOutputTimestamp;
            if (useRtpTimestamp && diff < -(1L << 31)) {
                // RTP timestamps have wrapped
                diff += 1L << 32;
            }
            else if (useRtpTimestamp && diff < 0) {
                // an older packet received (possibly a retransmission)
                outBuf.setDiscard(true);
                return BUFFER_PROCESSED_OK;
            }

            long diffSamples = Math.round(((double) (diff * sampleRate)) / clockRate);
            if (diffSamples > MAX_SAMPLES_SILENCE) {
                Timber.i("More than the maximum of %d samples of silence need to be inserted.", MAX_SAMPLES_SILENCE);

                if (listener != null)
                    listener.onSilenceNotInserted(timestamp);
                lastOutputTimestamp = timestamp;
                diffSamples = 0;
            }

            if (diffSamples > 0) {
                useInput = false;
                int samplesInserted = setSilence(outBuf, (int) diffSamples);
                totalSamplesInserted += samplesInserted;

                if (useRtpTimestamp) {
                    // outBuf.setRtpTimeStamp(lastOutputTimestamp);
                }
                else {
                    outBuf.setTimeStamp(lastOutputTimestamp);
                }

                outBuf.setDuration((diffSamples * 1000L * 1000L * 1000L) / sampleRate);

                lastOutputTimestamp = calculateTimestamp(lastOutputTimestamp, samplesInserted);
            }
        }

        if (useInput) {
            int inLen = inBuf.getLength();

            if (COPY_DATA_FROM_INPUT_TO_OUTPUT) {
                // Copy the actual data from the input to the output.
                byte[] outData = validateByteArraySize(outBuf, inLen, false);

                outBuf.setLength(inLen);
                outBuf.setOffset(0);

                System.arraycopy(inBuf.getData(), inBuf.getOffset(), outData, 0, inLen);

                // Now copy the remaining attributes.
                outBuf.setFormat(inBuf.getFormat());
                outBuf.setHeader(inBuf.getHeader());
                outBuf.setSequenceNumber(inBuf.getSequenceNumber());
                outBuf.setTimeStamp(inBuf.getTimeStamp());
                outBuf.setRtpTimeStamp(inBuf.getRtpTimeStamp());
                outBuf.setFlags(inBuf.getFlags());
                outBuf.setDiscard(inBuf.isDiscard());
                outBuf.setEOM(inBuf.isEOM());
                outBuf.setDuration(inBuf.getDuration());
            }
            else {
                outBuf.copy(inBuf);
            }

            lastOutputTimestamp = calculateTimestamp(lastOutputTimestamp, (inLen * 8)
                    / sampleSizeInBits);
        }

        return useInput ? BUFFER_PROCESSED_OK : INPUT_BUFFER_NOT_CONSUMED;
    }

    /**
     * Returns the timestamp obtained by adding <code>samplesToAdd</code> samples (using a sample rate
     * of <code>this.sampleRate</code> per second) to timestamp (with a clock rate of
     * <code>this.clockRate</code> per second).
     *
     * @param oldTimestamp the timestamp to which to add.
     * @param samplesToAdd the number of samples to add.
     * @return the timestamp obtained by adding <code>samplesToAdd</code> samples (using a sample rate
     * of <code>this.sampleRate</code> per second) to timestamp (with a clock rate of
     * <code>this.clockRate</code> per second).
     */
    private long calculateTimestamp(long oldTimestamp, long samplesToAdd)
    {
        // duration of samplesToAdd (in seconds per clockRate)
        long duration = Math.round(((double) (clockRate * samplesToAdd)) / sampleRate);

        long timestamp = oldTimestamp + duration;

        // RTP timestamps come from a 32bit field and wrap.
        if (useRtpTimestamp && timestamp > 1L << 32)
            timestamp -= 1L << 32;

        return timestamp;
    }

    /**
     * Fills the data of <code>buf</code> to at most <code>samples</code> samples of silence. Returns the
     * actual number of samples used.
     *
     * @param buf the <code>Buffer</code> to fill with silence
     * @param samples the number of samples of silence to fill.
     * @return the number of samples of silence added in <code>buf</code>.
     */
    private int setSilence(Buffer buf, int samples)
    {
        int samplesToFill = Math.min(samples, MAX_SAMPLES_PER_PACKET);
        int len = samplesToFill * sampleSizeInBits / 8;
        byte[] data = validateByteArraySize(buf, len, false);
        Arrays.fill(data, (byte) 0);

        buf.setOffset(0);
        buf.setLength(len);

        return samplesToFill;
    }

    /**
     * Resets the state of this <code>SilenceEffect</code>.
     *
     * TODO: is it appropriate to override the <code>reset()</code> method?
     */
    public void resetSilence()
    {
        lastOutputTimestamp = Buffer.TIME_UNKNOWN;
    }

    public void setListener(Listener listener)
    {
        this.listener = listener;
    }

    public interface Listener
    {
        void onSilenceNotInserted(long timestamp);
    }
}
