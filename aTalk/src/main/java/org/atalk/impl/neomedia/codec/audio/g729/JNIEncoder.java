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
import org.atalk.service.neomedia.control.AdvancedAttributesAwareCodec;

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
    private static final short BIT_1 = Ld8k.BIT_1;

    private static final int L_FRAME = Ld8k.L_FRAME;

    private static final int SERIAL_SIZE = Ld8k.SERIAL_SIZE;

    private static final int INPUT_FRAME_SIZE_IN_BYTES = 2 * L_FRAME;

    private static final int OUTPUT_FRAME_SIZE_IN_BYTES = L_FRAME / 8;

    /**
     * The count of the output frames to packetize. By default we packetize 2 audio frames in one
     * G729 packet.
     */
    private int OUTPUT_FRAMES_COUNT = 2;
    private long encoder;

    /**
     * The duration an output <code>Buffer</code> produced by this <code>Codec</code> in nanosecond. We
     * packetize 2 audio frames in one G729 packet by default.
     */
    private int duration = OUTPUT_FRAME_SIZE_IN_BYTES * OUTPUT_FRAMES_COUNT * 1000000;

    /**
     * Initializes a new {@code JNIEncoderImpl} instance.
     */
    public JNIEncoder() {
        super("G.729 JNI Encoder", AudioFormat.class, JNIDecoder.SUPPORTED_INPUT_FORMATS);
        inputFormats = JNIDecoder.SUPPORTED_OUTPUT_FORMATS;
    }

    private long computeDuration(long length) {
        return (length * 1000000L) / 8L;
    }

    /*
     * Implements AbstractCodec2#doClose().
     */
    @Override
    protected void doClose() {
        G729.g729_encoder_close(encoder);
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
        encoder = G729.g729_encoder_open();
        if (encoder == 0)
            throw new ResourceUnavailableException("g729_encoder_open");
    }

    /**
     * Implements AbstractCodec2#doProcess(Buffer, Buffer).
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer) {
        int inLength = inBuffer.getLength();
        int inOffset = inBuffer.getOffset();
        char[] inputFrame = (char[]) inBuffer.getData();

        int outOffset = outBuffer.getOffset();
        int outLength = inLength / 4;
        byte[] output = validateByteArraySize(outBuffer, outOffset + outLength,true);
        byte[] bitStream = new byte[]{};

        G729.g729_encoder_process(encoder, inputFrame, bitStream, outLength);

        outBuffer.setDuration(computeDuration(outLength));
        outBuffer.setFormat(getOutputFormat());
        outBuffer.setLength(outLength);
        return BUFFER_PROCESSED_OK;
    }

    /**
     * Get the output <tt>Format</tt>.
     *
     * @return output <tt>Format</tt> configured for this <tt>Codec</tt>
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    public Format getOutputFormat() {
        Format outputFormat = super.getOutputFormat();

        if ((outputFormat != null)
                && (outputFormat.getClass() == AudioFormat.class)) {
            AudioFormat outputAudioFormat = (AudioFormat) outputFormat;

            outputFormat = setOutputFormat(
                    new AudioFormat(
                            outputAudioFormat.getEncoding(),
                            outputAudioFormat.getSampleRate(),
                            outputAudioFormat.getSampleSizeInBits(),
                            outputAudioFormat.getChannels(),
                            outputAudioFormat.getEndian(),
                            outputAudioFormat.getSigned(),
                            outputAudioFormat.getFrameSizeInBits(),
                            outputAudioFormat.getFrameRate(),
                            outputAudioFormat.getDataType()) {
                        private static final long serialVersionUID = 0L;

                        @Override
                        public long computeDuration(long length) {
                            return JNIEncoder.this.computeDuration(length);
                        }
                    });
        }
        return outputFormat;
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
