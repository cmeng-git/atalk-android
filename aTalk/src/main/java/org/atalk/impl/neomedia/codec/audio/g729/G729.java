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

/**
 * Provides the interface to the native g729 Encode/Decode library.
 *
 * @author Eng Chong Meng
 */
public class G729
{
    public static final int L_FRAME = 80;

    static
    {
        System.loadLibrary("jnbcg729");
    }

    public static native void g729_decoder_close(long decoder);

    public static native long g729_decoder_open();

    public static native void g729_decoder_process(
            long decoder, byte[] bitStream, int bsLength, int frameErasureFlag,
            int SIDFrameFlag, int rfc3389PayloadFlag, short[] output);

    public static native void g729_encoder_close(long encoder);

    /**
     * Open a G729Encoder structure, returns a pointer to it casted to long.
     *
     * @param enanbleVAD : flag set to 1: VAD/DTX is enabled
     * @return the encoder channel context data
     */
    public static native long g729_encoder_open(int enableVAD);

    public static native int g729_encoder_process(long encoder, short[] inputFrame, byte[] bitStream);
}
