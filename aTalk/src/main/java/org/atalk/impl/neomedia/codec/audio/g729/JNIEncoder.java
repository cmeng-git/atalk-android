/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g729;

import static org.atalk.impl.neomedia.codec.audio.g729.G729.L_FRAME;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.AdvancedAttributesAwareCodec;
import org.atalk.util.ArrayIOUtils;

import java.awt.Component;
import java.util.Map;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class JNIEncoder extends AbstractCodec2 implements AdvancedAttributesAwareCodec {
    private static final int INPUT_FRAME_SIZE_IN_BYTES = 2 * L_FRAME;

    private static final int OUTPUT_FRAME_SIZE_IN_BYTES = L_FRAME / 8;

    /**
     * The count of the output frames to packetize. By default we packetize 2 audio frames in one G729 packet.
     */
    private int OUTPUT_FRAMES_COUNT = 2;

    private long encoder;

    private int outFrameCount;

    /**
     * The previous input if it was less than the input frame size and which is to be
     * prepended to the next input in order to form a complete input frame.
     */
    private byte[] prevIn;

    /**
     * The length of the previous input if it was less than the input frame size and which is to be
     * prepended to the next input in order to form a complete input frame.
     */
    private int prevInLength;

    private byte[] bitStream;

    private short[] sp16;

    private static int frameCount;
    /**
     * The duration an output <code>Buffer</code> produced by this <code>Codec</code> in nanosecond.
     * We packetize 2 audio frames in one G729 packet by default. i.e. 20mS
     */
    private int duration = OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000;

    /**
     * Initializes a new JNIEncoder instance.
     */
    public JNIEncoder() {
        super("G.729 JNI Encoder", AudioFormat.class, JNIDecoder.SUPPORTED_INPUT_FORMATS);
        inputFormats = JNIDecoder.SUPPORTED_OUTPUT_FORMATS;
    }

    /*
     * Implements AbstractCodec2#doClose().
     */
    @Override
    protected void doClose() {
        G729.g729_encoder_close(encoder);

        prevIn = null;
        prevInLength = 0;
        sp16 = null;
        bitStream = null;
    }

    /**
     * Opens this <code>Codec</code> and acquires the resources that it needs to operate. A call to
     * {@link PlugIn#open()} on this instance will result in a call to <code>doOpen</code> only if
     * {@link AbstractCodec#opened} is <code>false</code>. All required input and/or output formats are
     * assumed to have been set on this <code>Codec</code> before <code>doOpen</code> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this <code>Codec</code> needs to operate cannot be acquired
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException {
        prevIn = new byte[INPUT_FRAME_SIZE_IN_BYTES];
        prevInLength = 0;

        sp16 = new short[L_FRAME];
        bitStream = new byte[OUTPUT_FRAME_SIZE_IN_BYTES];
        outFrameCount = 0;
        frameCount = 0;

        // Set the encoder option according to user configuration; default to enable if none found.
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean g729Vad = cfg.getBoolean(Constants.PROP_G729_VAD, true);

        encoder = G729.g729_encoder_open(g729Vad ? 1 : 0);
        if (encoder == 0)
            throw new ResourceUnavailableException("g729_encoder_open");
    }

    //****************************************************************************/
    /* bcg729Encoder :                                                           */
    /*    parameters:                                                            */
    /*      -(i) encoderChannelContext : context for this encoder channel        */
    /*      -(i) inputFrame : 80 samples (16 bits PCM)                           */
    /*      -(o) bitStream : The 15 parameters for a frame on 80 bits            */
    /*           on 80 bits (10 8bits words)                                     */
    /*                                                                           */
    //****************************************************************************/
    /**
     * Implements AbstractCodec2#doProcess(Buffer, Buffer).
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer) {
        int inLength = inBuffer.getLength();
        int inOffset = inBuffer.getOffset();
        byte[] in = (byte[]) inBuffer.getData();

        // Need INPUT_FRAME_SIZE_IN_BYTES samples in input before process
        if ((prevInLength + inLength) < INPUT_FRAME_SIZE_IN_BYTES) {
            System.arraycopy(in, inOffset, prevIn, prevInLength, inLength);
            prevInLength += inLength;
            return BUFFER_PROCESSED_OK | OUTPUT_BUFFER_NOT_FILLED;
        }

        int readShorts = 0;
        if (prevInLength > 0) {
            readShorts += readShorts(prevIn, 0, sp16, 0, prevInLength / 2);
            prevInLength = 0;
        }
        readShorts = readShorts(in, inOffset, sp16, readShorts, sp16.length - readShorts);
        int readBytes = 2 * readShorts;

        inLength -= readBytes;
        inBuffer.setLength(inLength);
        inOffset += readBytes;
        inBuffer.setOffset(inOffset);

        int bsLength = G729.g729_encoder_process(encoder, sp16, bitStream);
//        if ((frameCount % 100) == 0 || frameCount < 10) {
//            Timber.w("G729 Encoded frame: %s: %s", frameCount, bytesToHex(bitStream, bsLength));
//        }

        int outLength = outBuffer.getLength();
        int outOffset = outBuffer.getOffset();
        byte[] output = validateByteArraySize(outBuffer,
                outOffset + OUTPUT_FRAMES_COUNT * OUTPUT_FRAME_SIZE_IN_BYTES, true);

        int outFrameOffset = outOffset + OUTPUT_FRAME_SIZE_IN_BYTES * outFrameCount;
        System.arraycopy(bitStream, 0, output, outFrameOffset, bsLength);

        outLength += OUTPUT_FRAME_SIZE_IN_BYTES;
        outBuffer.setLength(outLength);
        outBuffer.setFormat(outputFormat);

        int ret = BUFFER_PROCESSED_OK;
        if (outFrameCount == (OUTPUT_FRAMES_COUNT - 1)) {
            outFrameCount = 0;
        }
        else {
            outFrameCount++;
            frameCount++;
            ret |= OUTPUT_BUFFER_NOT_FILLED;
        }
        if (inLength > 0) {
            ret |= INPUT_BUFFER_NOT_CONSUMED;
        }

        if (ret == BUFFER_PROCESSED_OK) {
            updateOutput(outBuffer, getOutputFormat(), outLength, outOffset);
            outBuffer.setDuration(duration);
        }
        return ret;
    }

    /**
     * Get the output format.
     *
     * @return output format
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    public Format getOutputFormat() {
        Format outputFormat = super.getOutputFormat();

        if ((outputFormat != null) && (outputFormat.getClass() == AudioFormat.class)) {
            AudioFormat af = (AudioFormat) outputFormat;

            outputFormat = setOutputFormat(new AudioFormat(af.getEncoding(), af.getSampleRate(),
                    af.getSampleSizeInBits(), af.getChannels(), af.getEndian(), af.getSigned(),
                    af.getFrameSizeInBits(), af.getFrameRate(), af.getDataType()) {
                private static final long serialVersionUID = 0L;

                @Override
                public long computeDuration(long length) {
                    return duration;
                }
            });
        }
        return outputFormat;
    }

    private static int readShorts(byte[] in, int inOffset, short[] out, int outOffset, int outLength) {
        for (int o = outOffset, i = inOffset; o < outLength; o++, i += 2)
            out[o] = ArrayIOUtils.readShort(in, i);
        return outLength;
    }

    @Override
    protected void discardOutputBuffer(Buffer outputBuffer) {
        super.discardOutputBuffer(outputBuffer);

        outFrameCount = 0;
    }

    /**
     * Sets the additional attributes to <code>attributes</code>
     *
     * @param attributes The additional attributes to set
     */
    @Override
    public void setAdvancedAttributes(Map<String, String> attributes) {
        try {
            String s = attributes.get("ptime");

            if ((s != null) && (s.length() != 0)) {
                int ptime = Integer.parseInt(s);

                OUTPUT_FRAMES_COUNT = ptime / OUTPUT_FRAME_SIZE_IN_BYTES;
                duration = OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000;
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Not used.
     *
     * @return null as it is not used.
     */
    @Override
    public Component getControlComponent() {
        return null;
    }
}
