/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.audio.g729;

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
    private static final short BIT_0 = Ld8k.BIT_0;

    private static final short BIT_1 = Ld8k.BIT_1;

    private static final int L_FRAME = Ld8k.L_FRAME;

    private static final int SERIAL_SIZE = Ld8k.SERIAL_SIZE;

    private static final short SIZE_WORD = Ld8k.SIZE_WORD;

    private static final short SYNC_WORD = Ld8k.SYNC_WORD;

    private static final int INPUT_FRAME_SIZE_IN_BYTES = L_FRAME / 8;

    private static final int OUTPUT_FRAME_SIZE_IN_BYTES = 2 * L_FRAME;

    private long decoder;

    private short[] serial;

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
    }

    /**
     * Opens this <code>Codec</code> and acquires the resources that it needs to operate. A call to
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
        serial = new short[SERIAL_SIZE];
        sp16 = new short[L_FRAME];
        decoder = G729.g729_decoder_open();
    }

    private void depacketize(byte[] inFrame, int inFrameOffset, short[] serial) {
        serial[0] = SYNC_WORD;
        serial[1] = SIZE_WORD;
        for (int s = 0; s < L_FRAME; s++) {
            int in = inFrame[inFrameOffset + s / 8];

            in &= 1 << (7 - (s % 8));
            serial[2 + s] = (0 != in) ? BIT_1 : BIT_0;
        }
    }

    /* ****************************************************************************/
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
    //*****************************************************************************/
    // void bcg729Decoder(bcg729DecoderChannelContextStruct *decoderChannelContext, const uint8_t bitStream[], uint8_t bitStreamLength, uint8_t frameErasureFlag, uint8_t SIDFrameFlag, uint8_t rfc3389PayloadFlag, int16_t signal[])
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
        byte[] out = // new char[]{};
            validateByteArraySize(outBuffer, outOffset + outLength, false);

        for (int i = 0; i < frameCount; i++) {
            depacketize(in, inOffset, serial);
            inLength -= INPUT_FRAME_SIZE_IN_BYTES;
            inOffset += INPUT_FRAME_SIZE_IN_BYTES;

            // void bcg729Decoder(bcg729DecoderChannelContextStruct *decoderChannelContext,
            // const uint8_t bitStream[],
            // uint8_t bitStreamLength,
            // uint8_t frameErasureFlag,
            // uint8_t SIDFrameFlag,
            // uint8_t rfc3389PayloadFlag,
            // int16_t signal[])

            G729.g729_decoder_process(
                    decoder, in, 10, in, 0, 0, out);

            writeShorts(sp16, out, outOffset);
            outOffset += OUTPUT_FRAME_SIZE_IN_BYTES;
        }

        // outBuffer.setDuration( (outLength * 1000000L) / (16L /* kHz */ * 2L /* sampleSizeInBits / 8 */));
        // outBuffer.setFormat(getOutputFormat());

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
