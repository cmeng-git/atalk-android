/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.control.FECDecoderControl;

import java.awt.Component;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * Implements the SILK decoder as an FMJ/JMF <code>Codec</code>.
 *
 * @author Dingxin Xu
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class JavaDecoder extends AbstractCodec2
{
    /**
     * A private class, an instance of which is registered via <code>addControl</code>. This instance
     * will be used by outside classes to access decoder statistics.
     */
    private class Stats implements FECDecoderControl
    {
        /**
         * Returns the number packets for which FEC data was decoded in <code>JavaDecoder.this</code>
         *
         * @return Returns the number packets for which FEC data was decoded in <code>JavaDecoder.this</code>
         */
        public int fecPacketsDecoded()
        {
            return nbFECDecoded;
        }

        /**
         * Stub. Always return <code>null</code>, as it's not used.
         *
         * @return <code>null</code>
         */
        public Component getControlComponent()
        {
            return null;
        }
    }

    /**
     * The duration of a frame in milliseconds as defined by the SILK standard.
     */
    static final int FRAME_DURATION = 20;

    /**
     * The maximum number of frames encoded into a single payload as defined by the SILK standard.
     */
    private static final int MAX_FRAMES_PER_PAYLOAD = 5;
    /**
     * The list of <code>Format</code>s of audio data supported as input by <code>JavaDecoder</code> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS = JavaEncoder.SUPPORTED_OUTPUT_FORMATS;

    /**
     * The list of <code>Format</code>s of audio data supported as output by <code>JavaDecoder</code> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS = JavaEncoder.SUPPORTED_INPUT_FORMATS;

    /**
     * The SILK decoder control (structure).
     */
    private SKP_SILK_SDK_DecControlStruct decControl;

    /**
     * The SILK decoder state.
     */
    private SKP_Silk_decoder_state decState;

    /**
     * The length of an output frame as determined by {@link #FRAME_DURATION} and the
     * <code>inputFormat</code> of this <code>JavaDecoder</code>.
     */
    private short frameLength;

    /**
     * The number of frames decoded from the last input <code>Buffer</code> which has not been consumed yet.
     */
    private int framesPerPayload;

    /**
     * The sequence number of the last processed <code>Buffer</code>.
     */
    private long lastSeqNo = Buffer.SEQUENCE_UNKNOWN;

    /**
     * Temporary buffer used when decoding FEC. Defined here to avoid using <code>new</code> in <code>doProcess</code>.
     */
    private short[] lbrrBytes = new short[1];

    /**
     * Temporary buffer used to hold the lbrr data when decoding FEC. Defined here to avoid using
     * <code>new</code> in <code>doProcess</code>.
     */
    private byte[] lbrrData = new byte[JavaEncoder.MAX_BYTES_PER_FRAME];

    /**
     * Number of packets which: were missing, the following packet was available and it contained FEC data.
     */
    private int nbFECDecoded = 0;

    /**
     * Number of packets which: were missing, the next packet was available, but it did not contain FEC data.
     */
    private int nbFECNotDecoded = 0;

    /**
     * Number of packets which: were successfully decoded
     */
    private int nbPacketsDecoded = 0;

    /**
     * Number of packets which: were missing, and the subsequent packet was also missing.
     */
    private int nbPacketsLost = 0;

    /**
     * The length of an output frame as reported by
     * {@link DecAPI#SKP_Silk_SDK_Decode(Object, SKP_SILK_SDK_DecControlStruct, int, byte[], int, int, short[], int, short[])}
     * .
     */
    private final short[] outLength = new short[1];

    /**
     * Initializes a new <code>JavaDecoder</code> instance.
     */
    public JavaDecoder()
    {
        super("SILK Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);

        features = BUFFER_FLAG_FEC | BUFFER_FLAG_PLC;
        inputFormats = SUPPORTED_INPUT_FORMATS;

        addControl(new Stats());
    }

    @Override
    protected void doClose()
    {
        Timber.d("Packets decoded normally: %s\nPackets decoded with FEC: %s", nbPacketsDecoded, nbFECDecoded);
        Timber.d("Packets lost (subsequent missing):%s\nPackets lost (no FEC in subsequent): %s", nbPacketsLost, nbFECNotDecoded);

        decState = null;
        decControl = null;
    }

    @Override
    protected void doOpen()
            throws ResourceUnavailableException
    {
        decState = new SKP_Silk_decoder_state();
        if (DecAPI.SKP_Silk_SDK_InitDecoder(decState) != 0) {
            throw new ResourceUnavailableException("DecAPI.SKP_Silk_SDK_InitDecoder");
        }

        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        double sampleRate = inputFormat.getSampleRate();
        int channels = inputFormat.getChannels();

        decControl = new SKP_SILK_SDK_DecControlStruct();
        decControl.API_sampleRate = (int) sampleRate;

        frameLength = (short) ((FRAME_DURATION * sampleRate * channels) / 1000);
        lastSeqNo = Buffer.SEQUENCE_UNKNOWN;
    }

    @Override
    protected int doProcess(Buffer inBuf, Buffer outBuf)
    {
        long seqNo = inBuf.getSequenceNumber();

        /*
         * Buffer.FLAG_SILENCE is set only when the intention is to drop the specified input Buffer
         * but to note that it has not been lost.
         */
        if ((Buffer.FLAG_SILENCE & inBuf.getFlags()) != 0) {
            lastSeqNo = seqNo;
            return OUTPUT_BUFFER_NOT_FILLED;
        }

        /*
         * Check whether a packet has been lost. If a packet has more than one frame, we go through
         * each frame in a new call to the process method so having the same sequence number as on
         * the previous pass is fine.
         */
        int lostSeqNoCount = calculateLostSeqNoCount(lastSeqNo, seqNo);
        boolean decodeFEC = (lostSeqNoCount > 0)
                && (lostSeqNoCount <= MAX_AUDIO_SEQUENCE_NUMBERS_TO_PLC);

        if (decodeFEC && ((inBuf.getFlags() & Buffer.FLAG_SKIP_FEC) != 0)) {
            decodeFEC = false;
            Timber.log(TimberLog.FINER, "Not decoding FEC/PLC for %s because of Buffer.FLAG_SKIP_FEC.", seqNo);
        }

        byte[] in = (byte[]) inBuf.getData();
        int inOffset = inBuf.getOffset();
        int inLength = inBuf.getLength();
        short[] out = validateShortArraySize(outBuf, frameLength);
        int outOffset = 0;
        int lostFlag = 0;

        if (decodeFEC) /* Decode with FEC. */ {
            lbrrBytes[0] = 0;
            DecAPI.SKP_Silk_SDK_search_for_LBRR(in, inOffset, (short) inLength,
                    /* lost_offset */lostSeqNoCount, lbrrData, 0, lbrrBytes);
            Timber.log(TimberLog.FINER, "Packet loss detected. Last seen %s, current %s", lastSeqNo, seqNo);
            Timber.log(TimberLog.FINER, "Looking for FEC data, found %s bytes", lbrrBytes[0]);

            outLength[0] = frameLength;
            if (lbrrBytes[0] == 0) {
                // No FEC data found, process the packet as lost.
                lostFlag = 1;
            }
            else if (DecAPI.SKP_Silk_SDK_Decode(decState, decControl, 0, lbrrData, 0, lbrrBytes[0],
                    out, outOffset, outLength) == 0) {
                // Found FEC data, decode it.
                nbFECDecoded++;

                outBuf.setDuration(FRAME_DURATION * 1000000);
                outBuf.setLength(outLength[0]);
                outBuf.setOffset(outOffset);

                outBuf.setFlags(outBuf.getFlags() | BUFFER_FLAG_FEC);
                outBuf.setFlags(outBuf.getFlags() & ~BUFFER_FLAG_PLC);

                // We have decoded the expected sequence number from FEC data.
                lastSeqNo = incrementSeqNo(lastSeqNo);
                return INPUT_BUFFER_NOT_CONSUMED;
            }
            else {
                nbFECNotDecoded++;
                if (lostSeqNoCount != 0)
                    this.nbPacketsLost += lostSeqNoCount;
                lastSeqNo = seqNo;
                return BUFFER_PROCESSED_FAILED;
            }
        }
        else if (lostSeqNoCount != 0)
            this.nbPacketsLost += lostSeqNoCount;

        int processed;

        /* Decode without FEC. */
        {
            outLength[0] = frameLength;
            if (DecAPI.SKP_Silk_SDK_Decode(decState, decControl, lostFlag, in, inOffset, inLength,
                    out, outOffset, outLength) == 0) {
                outBuf.setDuration(FRAME_DURATION * 1000000);
                outBuf.setLength(outLength[0]);
                outBuf.setOffset(outOffset);

                if (lostFlag == 0) {
                    outBuf.setFlags(outBuf.getFlags() & ~(BUFFER_FLAG_FEC | BUFFER_FLAG_PLC));

                    if (decControl.moreInternalDecoderFrames == 0) {
                        nbPacketsDecoded++;
                        processed = BUFFER_PROCESSED_OK;
                    }
                    else {
                        framesPerPayload++;
                        if (framesPerPayload >= MAX_FRAMES_PER_PAYLOAD) {
                            nbPacketsDecoded++;
                            processed = BUFFER_PROCESSED_OK;
                        }
                        else
                            processed = INPUT_BUFFER_NOT_CONSUMED;
                    }
                    lastSeqNo = seqNo;
                }
                else {
                    outBuf.setFlags(outBuf.getFlags() & ~BUFFER_FLAG_FEC);
                    outBuf.setFlags(outBuf.getFlags() | BUFFER_FLAG_PLC);

                    processed = INPUT_BUFFER_NOT_CONSUMED;
                    // We have decoded the expected sequence number with PLC.
                    lastSeqNo = incrementSeqNo(lastSeqNo);
                }
            }
            else {
                processed = BUFFER_PROCESSED_FAILED;
                if (lostFlag == 1) {
                    nbFECNotDecoded++;
                    if (lostSeqNoCount != 0)
                        this.nbPacketsLost += lostSeqNoCount;
                }
                lastSeqNo = seqNo;
            }

            if ((processed & INPUT_BUFFER_NOT_CONSUMED) != INPUT_BUFFER_NOT_CONSUMED)
                framesPerPayload = 0;
        }
        return processed;
    }

    /**
     * Get the output formats matching a specific input format.
     *
     * @param inputFormat the input format to get the matching output formats of
     * @return the output formats matching the specified input format
     * @see AbstractCodec2#getMatchingOutputFormats(Format)
     */
    @Override
    protected Format[] getMatchingOutputFormats(Format inputFormat)
    {
        return JavaEncoder.getMatchingOutputFormats(inputFormat, SUPPORTED_INPUT_FORMATS, SUPPORTED_OUTPUT_FORMATS);
    }
}
