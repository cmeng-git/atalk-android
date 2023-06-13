/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g729;

/**
 * Provides the interface to the native g729 Encode/Decode library.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class G729
{
    static
    {
        System.loadLibrary("jnbcg729");
    }

    public static native void g729_decoder_close(long decoder);

    public static native long g729_decoder_open();

    public static native void g729_decoder_process(
            long decoder, byte[] bitStream, int bsLength, byte[] input,
            int frameErasureFlag, int rfc3389PayloadFlag, byte[] output);

    public static native void g729_encoder_close(long encoder);

    public static native long g729_encoder_open();

    // G729.g729_encoder_process(encoder, in, bitStream, outLength);
    public static native void g729_encoder_process(
            long encoder, char[] inputFrame,
            byte[] bitStream, int bsLength);
}
