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
public class JNIDecoder
{
    static
    {
        System.loadLibrary("jng722");
    }

    public static native void g722_decoder_close(long decoder);

    public static native long g722_decoder_open();

    public static native void g722_decoder_process(
            long decoder,
            byte[] input, int inputOffset,
            byte[] output, int outputOffset, int outputLength);
}
