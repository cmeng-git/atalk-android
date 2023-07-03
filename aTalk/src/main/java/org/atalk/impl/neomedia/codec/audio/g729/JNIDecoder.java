/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g729;

import static org.atalk.impl.neomedia.codec.audio.g729.G729.L_FRAME;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.util.ArrayIOUtils;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class JNIDecoder extends AbstractCodec2 {
    private static final int INPUT_FRAME_SIZE_IN_BYTES = L_FRAME / 8;

    private static final int OUTPUT_FRAME_SIZE_IN_BYTES = 2 * L_FRAME;

    private long decoder;

    private byte[] bitStream;

    private short[] sp16;

    static final Format[] SUPPORTED_INPUT_FORMATS = new Format[]{
            new AudioFormat(
                    AudioFormat.G729_RTP,
                    8000,
                    Format.NOT_SPECIFIED /* sampleSizeInBits */,
                    1)
    };

    static final Format[] SUPPORTED_OUTPUT_FORMATS = new Format[]{
            new AudioFormat(
                    AudioFormat.LINEAR,
                    8000,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */,
                    Format.byteArray) /* frameRate */
    };

    /**
     * Initializes a new {@code JNIDecoderImpl} instance.
     */
    public JNIDecoder() {
        super("G.729 JNI Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose() {
        G729.g729_decoder_close(decoder);
        sp16 = null;
        bitStream = null;
    }

    /**
     * Open this <code>Codec</code> and acquire the resources that it needs to operate. A call to
     * {@link PlugIn#open()} on this instance will result in a call to <code>doOpen</code> only if
     * {@link AbstractCodec#opened} is <code>false</code>. All required input and/or output formats are
     * assumed to have been set on this <code>Codec</code> before <code>doOpen</code> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this <code>Codec</code> needs to operate cannot be acquired
     * @see AbstractCodecExt#doOpen()
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException {
        sp16 = new short[L_FRAME];
        bitStream = new byte[INPUT_FRAME_SIZE_IN_BYTES];
        decoder = G729.g729_decoder_open();
    }

    //****************************************************************************/
    /* bcg729Decoder :                                                           */
    /*    parameters:                                                            */
    /*      -(i) decoderChannelContext : the channel context data                */
    /*      -(i) bitStream : 15 parameters on 80 bits                            */
    /*      -(i): bitStreamLength : in bytes, length of previous buffer          */
    /*      -(i) frameErased: flag: true, frame has been erased                  */
    /*      -(i) SIDFrameFlag: flag: true, frame is a SID one                    */
    /*      -(i) rfc3389PayloadFlag: true when CN payload follow rfc3389         */
    /*      -(o) signal : a decoded frame 80 samples (16 bits PCM)               */
    /*                                                                           */
    //****************************************************************************/
    /**
     * Implements AbstractCodec2#doProcess(Buffer, Buffer).
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer) {
        int inLength = inBuffer.getLength();
        /*
         * Decode as many G.729 frames as possible in one go in order to mitigate an issue with
         * sample rate conversion which leads to audio glitches.
         */
        int frameCount = inLength / INPUT_FRAME_SIZE_IN_BYTES;

        if (frameCount < 1) {
            discardOutputBuffer(outBuffer);
            return BUFFER_PROCESSED_OK | OUTPUT_BUFFER_NOT_FILLED;
        }

        byte[] in = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();

        int outOffset = outBuffer.getOffset();
        int outLength = OUTPUT_FRAME_SIZE_IN_BYTES * frameCount;
        byte[] out = validateByteArraySize(outBuffer, outOffset + outLength, false);

        for (int i = 0; i < frameCount; i++) {
            System.arraycopy(in, inOffset, bitStream, 0, INPUT_FRAME_SIZE_IN_BYTES);

            inLength -= INPUT_FRAME_SIZE_IN_BYTES;
            inOffset += INPUT_FRAME_SIZE_IN_BYTES;

//            if ((i % 50) == 0 || frameCount < 5) {
//                Timber.w("G729 Decode a frame: %s", bytesToHex(bitStream, 10));
//            }
            G729.g729_decoder_process(decoder, bitStream, INPUT_FRAME_SIZE_IN_BYTES, 0, 0, 0, sp16);

            writeShorts(sp16, out, outOffset);
            outOffset += OUTPUT_FRAME_SIZE_IN_BYTES;
        }

        inBuffer.setLength(inLength);
        inBuffer.setOffset(inOffset);
        outBuffer.setLength(outLength);
        return BUFFER_PROCESSED_OK;
    }

    private static void writeShorts(short[] in, byte[] out, int outOffset) {
        for (int i = 0, o = outOffset; i < in.length; i++, o += 2)
            ArrayIOUtils.writeShort(in[i], out, o);
    }
}
