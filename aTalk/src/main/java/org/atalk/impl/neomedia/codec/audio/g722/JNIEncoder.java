/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.g722;

/**
 *
 * @author Lyubomir Marinov
 */
public class JNIEncoder
{
    static
    {
        System.loadLibrary("jng722");
    }

    public static native void g722_encoder_close(long encoder);

    public static native long g722_encoder_open();

    public static native void g722_encoder_process(
            long encoder,
            byte[] input, int inputOffset,
            byte[] output, int outputOffset, int outputLength);
}
