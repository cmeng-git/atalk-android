/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.opus;

/**
 * Defines the API of the native opus library to be utilized by the libjitsi library.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class Opus
{
    /**
     * Opus fullBand constant
     */
    public static final int BANDWIDTH_FULLBAND = 1105;

    /**
     * Opus mediumband constant
     */
    public static final int BANDWIDTH_MEDIUMBAND = 1102;

    /**
     * Opus narrowband constant
     */
    public static final int BANDWIDTH_NARROWBAND = 1101;

    /**
     * Opus superwideband constant
     */
    public static final int BANDWIDTH_SUPERWIDEBAND = 1104;

    /**
     * Opus wideband constant
     */
    public static final int BANDWIDTH_WIDEBAND = 1103;

    /**
     * Opus constant for an invalid packet
     */
    public static final int INVALID_PACKET = -4;

    /**
     * The maximum size of a packet we can create. Since we're only creating packets with a single
     * frame, that's a 1 byte TOC + the maximum frame size.
     * See http://tools.ietf.org/html/rfc6716#section-3.2
     */
    public static final int MAX_PACKET = 1 + 1275;

    /**
     * Constant used to set various settings to "automatic"
     */
    public static final int OPUS_AUTO = -1000;

    /**
     * Constant usually indicating that no error occurred
     */
    public static final int OPUS_OK = 0;

    /*
     * Loads the native JNI library.
     */
    static {
        System.loadLibrary("jnopus");
    }

    /**
     * Asserts that the <code>Opus</code> class and the JNI library which supports it are functional.
     * The method is to be invoked early (e.g. static/class initializers) by classes which require
     * it (i.e. they depend on it and they cannot function without it).
     */
    public static void assertOpusIsFunctional()
    {
        int channels = 1;

        decoder_get_size(channels);
        encoder_get_size(channels);
    }

    /**
     * Decodes an opus packet from <code>input</code> into <code>output</code>.
     *
     * @param decoder the <code>OpusDecoder</code> state to perform the decoding
     * @param input an array of <code>byte</code>s which represents the input payload to decode. If
     * <code>null</code>, indicates packet loss.
     * @param inputOffset the offset in <code>input</code> at which the payload to be decoded begins
     * @param inputLength the length in bytes in <code>input</code> beginning at <code>inputOffset</code> of the payload
     * to be decoded
     * @param output an array of <code>byte</code>s into which the decoded signal is to be output
     * @param outputOffset the offset in <code>output</code> at which the output of the decoded signal is to begin
     * @param outputFrameSize the number of samples per channel <code>output</code> beginning at <code>outputOffset</code>
     * of the maximum space available for output of the decoded signal
     * @param decodeFEC 0 to decode the packet normally, 1 to decode the FEC data in the packet
     * @return the number of decoded samples written into <code>output</code> (beginning at <code>outputOffset</code>)
     */
    public static native int decode(long decoder, byte[] input, int inputOffset, int inputLength,
            byte[] output, int outputOffset, int outputFrameSize, int decodeFEC);

    /**
     * Creates an OpusDecoder structure, returns a pointer to it or 0 on error.
     *
     * @param Fs Sample rate to decode to
     * @param channels number of channels to decode to(1/2)
     * @return A pointer to the OpusDecoder structure created, 0 on error.
     */
    public static native long decoder_create(int Fs, int channels);

    /**
     * Destroys an OpusDecoder, freeing it's resources.
     *
     * @param decoder Address of the structure (as returned from decoder_create)
     */
    public static native void decoder_destroy(long decoder);

    /**
     * Returns the number of samples in an opus packet
     *
     * @param decoder The decoder to use.
     * @param packet Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @param length Length of the packet.
     * @return the number of samples in <code>packet</code> .
     */
    public static native int decoder_get_nb_samples(long decoder, byte[] packet, int offset, int length);

    /**
     * Returns the size in bytes required for an OpusDecoder structure.
     *
     * @param channels number of channels (1/2)
     * @return the size in bytes required for an OpusDecoder structure.
     */
    public static native int decoder_get_size(int channels);

    /**
     * Encodes the input from <code>input</code> into an opus packet in <code>output</code>.
     *
     * @param encoder The encoder to use.
     * @param input Array containing PCM encoded input.
     * @param inputOffset Offset to use into the <code>input</code> array
     * @param inputFrameSize The number of samples per channel in <code>input</code>.
     * @param output Array where the encoded packet will be stored.
     * @param outputOffset output offset
     * @param outputLength The number of available bytes in <code>output</code>.
     * @return The number of bytes written in <code>output</code>, or a negative on error.
     */
    public static native int encode(long encoder, byte[] input, int inputOffset,
            int inputFrameSize, byte[] output, int outputOffset, int outputLength);

    /**
     * Creates an OpusEncoder structure, returns a pointer to it casted to long. The native
     * function's <code>application</code> parameter is always set to OPUS_APPLICATION_VOIP.
     *
     * @param Fs Sample rate of the input PCM
     * @param channels number of channels in the input (1/2)
     * @return A pointer to the OpusEncoder structure created, 0 on error
     */
    public static native long encoder_create(int Fs, int channels);

    /**
     * Destroys an OpusEncoder, freeing it's resources.
     *
     * @param encoder Address of the structure (as returned from encoder_create)
     */
    public static native void encoder_destroy(long encoder);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Returns the current encoder audio bandwidth .
     *
     * @param encoder The encoder to use
     * @return the current encoder audio bandwidth
     */
    public static native int encoder_get_bandwidth(long encoder);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Returns the current encoder bitrate.
     *
     * @param encoder The encoder to use
     * @return The current encoder bitrate.
     */
    public static native int encoder_get_bitrate(long encoder);

    public static native int encoder_get_complexity(long encoder);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Returns the current DTX setting of the encoder.
     *
     * @param encoder The encoder to use
     * @return the current DTX setting of the encoder.
     */
    public static native int encoder_get_dtx(long encoder);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Returns the current inband FEC encoder setting.
     *
     * @param encoder The encoder to use
     * @return the current inband FEC encoder setting.
     */
    public static native int encoder_get_inband_fec(long encoder);

    /**
     * Returns the size in bytes required for an OpusEncoder structure.
     *
     * @param channels number of channels (1/2)
     * @return the size in bytes required for an OpusEncoder structure.
     */
    public static native int encoder_get_size(int channels);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Returns the current encoder VBR setting
     *
     * @param encoder The encoder to use
     * @return The current encoder VBR setting.
     */
    public static native int encoder_get_vbr(long encoder);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Returns the current VBR
     * constraint encoder setting.
     *
     * @param encoder The encoder to use
     * @return the current VBR constraint encoder setting.
     */
    public static native int encoder_get_vbr_constraint(long encoder);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder audio bandwidth.
     *
     * @param encoder The encoder to use
     * @param bandwidth The bandwidth to set, should be one of <code>BANDWIDTH_FULLBAND</code>,
     * <code>BANDWIDTH_MEDIUMBAND</code>, <code>BANDWIDTH_NARROWBAND</code>,
     * <code>BANDWIDTH_SUPERWIDEBAND</code> or <code>BANDWIDTH_WIDEBAND</code>.
     * @return OPUS_OK on success
     */
    public static native int encoder_set_bandwidth(long encoder, int bandwidth);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder bitrate
     *
     * @param encoder The encoder to use
     * @param bitrate The bitrate to set
     * @return <code>OPUS_OK</code> on success
     */
    public static native int encoder_set_bitrate(long encoder, int bitrate);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder complexity setting.
     *
     * @param encoder The encoder to use
     * @param complexity The complexity level, from 1 to 10
     * @return OPUS_OK on success
     */
    public static native int encoder_set_complexity(long encoder, int complexity);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the DTX setting of the
     * encoder.
     *
     * @param encoder The encoder to use
     * @param dtx 0 to turn DTX off, non-zero to turn it on
     * @return OPUS_OK on success
     */
    public static native int encoder_set_dtx(long encoder, int dtx);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the force channels setting of the encoder.
     *
     * @param encoder The encoder to use
     * @param forcechannels Number of channels
     * @return OPUS_OK on success
     */
    public static native int encoder_set_force_channels(long encoder, int forcechannels);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder FEC setting.
     *
     * @param encoder The encoder to use
     * @param inbandFEC 0 to turn FEC off, non-zero to turn it on.
     * @return OPUS_OK on success
     */
    public static native int encoder_set_inband_fec(long encoder, int inbandFEC);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the maximum audio
     * bandwidth to be used by the encoder.
     *
     * @param encoder The encoder to use
     * @param maxBandwidth The maximum bandwidth to use, should be one of <code>BANDWIDTH_FULLBAND</code>,
     * <code>BANDWIDTH_MEDIUMBAND</code>, <code>BANDWIDTH_NARROWBAND</code>,
     * <code>BANDWIDTH_SUPERWIDEBAND</code> or <code>BANDWIDTH_WIDEBAND</code>
     * @return <code>OPUS_OK</code> on success.
     */
    public static native int encoder_set_max_bandwidth(long encoder, int maxBandwidth);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder's expected
     * packet loss percentage.
     *
     * @param encoder The encoder to use
     * @param packetLossPerc
     * @return OPUS_OK on success.
     */
    public static native int encoder_set_packet_loss_perc(long encoder, int packetLossPerc);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder VBR setting
     *
     * @param encoder The encoder to use
     * @param vbr 0 to turn VBR off, non-zero to turn it on.
     * @return OPUS_OK on success
     */
    public static native int encoder_set_vbr(long encoder, int vbr);

    /**
     * Wrapper around the native <code>opus_encoder_ctl</code> function. Sets the encoder VBR constraint setting
     *
     * @param encoder The encoder to use
     * @param use_cvbr 0 to turn VBR constraint off, non-zero to turn it on.
     * @return OPUS_OK on success
     */
    public static native int encoder_set_vbr_constraint(long encoder, int use_cvbr);

    /**
     * Returns the audio bandwidth of an Opus packet, one of <code>BANDWIDTH_FULLBAND</code>,
     * <code>BANDWIDTH_MEDIUMBAND</code>, <code>BANDWIDTH_NARROWBAND</code>,
     * <code>BANDWIDTH_SUPERWIDEBAND</code> or <code>BANDWIDTH_WIDEBAND</code>, or <code>INVALID_PACKET</code> on error.
     *
     * @param data Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @return one of <code>BANDWIDTH_FULLBAND</code>, <code>BANDWIDTH_MEDIUMBAND</code>,
     * <code>BANDWIDTH_NARROWBAND</code>, <code>BANDWIDTH_SUPERWIDEBAND</code>,
     * <code>BANDWIDTH_WIDEBAND</code>, or <code>INVALID_PACKET</code> on error.
     */
    public static native int packet_get_bandwidth(byte[] data, int offset);

    /**
     * Returns the number of channels encoded in an Opus packet.
     *
     * @param data Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @return the number of channels encoded in <code>data</code>.
     */
    public static native int packet_get_nb_channels(byte[] data, int offset);

    /**
     * Returns the number of frames in an Opus packet.
     *
     * @param packet Array holding the packet.
     * @param offset Offset into packet where the actual packet begins.
     * @param length Length of the packet.
     * @return the number of frames in <code>packet</code>.
     */
    public static native int packet_get_nb_frames(byte[] packet, int offset, int length);
}
