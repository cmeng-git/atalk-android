/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.audio.g722;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * @author Lyubomir Marinov
 */
public class JNIDecoder extends AbstractCodec2 {
    private long decoder;

    static final Format[] SUPPORTED_INPUT_FORMATS = new Format[]{
            new AudioFormat(
                    Constants.G722_RTP,
                    8000,
                    Format.NOT_SPECIFIED /* sampleSizeInBits */,
                    1)
    };

    static final Format[] SUPPORTED_OUTPUT_FORMATS = new Format[]{
            new AudioFormat(
                    AudioFormat.LINEAR,
                    16000,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */,
                    Format.byteArray)
    };

    /**
     * Initializes a new {@code JNIDecoderImpl} instance.
     */
    public JNIDecoder() {
        super("G.722 JNI Decoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    /**
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose() {
        G722.g722_decoder_close(decoder);
    }

    /**
     * @see AbstractCodec2#doOpen()
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException {
        decoder = G722.g722_decoder_open();
        if (decoder == 0)
            throw new ResourceUnavailableException("g722_decoder_open");
    }

    /**
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer) {
        byte[] input = (byte[]) inputBuffer.getData();

        int outputOffset = outputBuffer.getOffset();
        int outputLength = inputBuffer.getLength() * 4;
        byte[] output = validateByteArraySize(
                outputBuffer,
                outputOffset + outputLength,
                true);

        G722.g722_decoder_process(
                decoder,
                input, inputBuffer.getOffset(),
                output, outputOffset, outputLength);

        outputBuffer.setDuration(
                (outputLength * 1000000L)
                        / (16L /* kHz */ * 2L /* sampleSizeInBits / 8 */));
        outputBuffer.setFormat(getOutputFormat());
        outputBuffer.setLength(outputLength);
        return BUFFER_PROCESSED_OK;
    }
}
