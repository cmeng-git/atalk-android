/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.codec.FFmpeg;

import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * Implements an audio <code>Codec</code> using the FFmpeg library.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractFFmpegAudioCodec extends AbstractCodec2
{
    /**
     * Returns a <code>String</code> representation of a specific <code>AVCodecID</code>.
     *
     * @param codecID the <code>AVCodecID</code> to represent as a <code>String</code>
     * @return a <code>String</code> representation of the specified <code>codecID</code>
     */
    public static String codecIDToString(int codecID)
    {
        switch (codecID) {
            case FFmpeg.CODEC_ID_MP3:
                return "CODEC_ID_MP3";
            default:
                return "0x" + Long.toHexString(codecID & 0xFFFFFFFFL);
        }
    }

    /**
     * The <code>AVCodecContext</code> which performs the actual encoding/decoding and which is the
     * native counterpart of this open <code>AbstractFFmpegAudioCodec</code>.
     */
    protected long avctx;

    /**
     * The <code>AVCodecID</code> of {@link #avctx}.
     */
    protected final int codecID;

    /**
     * The number of bytes of audio data to be encoded with a single call to
     * {@link FFmpeg#avcodec_encode_audio(long, byte[], int, int, byte[], int)} based on the
     * <code>frame_size</code> of {@link #avctx}.
     */
    protected int frameSizeInBytes;

    /**
     * Initializes a new <code>AbstractFFmpegAudioCodec</code> instance with a specific <code>PlugIn</code>
     * name, a specific <code>AVCodecID</code>, and a specific list of <code>Format</code>s supported as output.
     *
     * @param name the <code>PlugIn</code> name of the new instance
     * @param codecID the <code>AVCodecID</code> of the FFmpeg codec to be represented by the new instance
     * @param supportedOutputFormats the list of <code>Format</code>s supported by the new instance as output
     */
    protected AbstractFFmpegAudioCodec(String name, int codecID, Format[] supportedOutputFormats)
    {
        super(name, AudioFormat.class, supportedOutputFormats);
        this.codecID = codecID;
    }

    /**
     * Configures the <code>AVCodecContext</code> initialized in {@link #doOpen()} prior to invoking one
     * of the FFmpeg functions in the <code>avcodec_open</code> family. Allows extenders to override and
     * provide additional, optional configuration.
     *
     * @param avctx the <code>AVCodecContext</code> to configure
     * @param format the <code>AudioFormat</code> with which <code>avctx</code> is being configured
     */
    protected void configureAVCodecContext(long avctx, AudioFormat format)
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doClose()
    {
        if (avctx != 0) {
            FFmpeg.avcodec_close(avctx);
            FFmpeg.av_free(avctx);
            avctx = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void doOpen()
            throws ResourceUnavailableException
    {
        int codecID = this.codecID;
        long codec = findAVCodec(codecID);

        if (codec == 0) {
            throw new ResourceUnavailableException("Could not find FFmpeg codec " + codecIDToString(codecID) + "!");
        }

        avctx = FFmpeg.avcodec_alloc_context3(codec);
        if (avctx == 0) {
            throw new ResourceUnavailableException(
                    "Could not allocate AVCodecContext for FFmpeg codec " + codecIDToString(codecID) + "!");
        }

        int avcodec_open = -1;
        try {
            AudioFormat format = getAVCodecContextFormat();
            int channels = format.getChannels();
            int sampleRate = (int) format.getSampleRate();

            if (channels == Format.NOT_SPECIFIED)
                channels = 1;

            if (channels == 1) {
                // mono
                FFmpeg.avcodeccontext_set_ch_layout(avctx, FFmpeg.AV_CH_LAYOUT_MONO);
            }
            else if (channels == 2) {
                // stereo
                FFmpeg.avcodeccontext_set_ch_layout(avctx, FFmpeg.AV_CH_LAYOUT_STEREO);
            }
            // For ffmpeg 5.1, the following is not required to set with avcodeccontext_set_ch_layout()
            // FFmpeg.avcodeccontext_set_channels(avctx, channels);

            if (sampleRate != Format.NOT_SPECIFIED)
                FFmpeg.avcodeccontext_set_sample_rate(avctx, sampleRate);

            configureAVCodecContext(avctx, format);

            avcodec_open = FFmpeg.avcodec_open2(avctx, codec);

            // When encoding, set by libavcodec in avcodec_open2 and may be 0 to
            // indicate unrestricted frame size. When decoding, may be set by
            // some decoders to indicate constant frame size.
            int frameSize = FFmpeg.avcodeccontext_get_frame_size(avctx);

            frameSizeInBytes = frameSize * (format.getSampleSizeInBits() / 8) * channels;
        } finally {
            if (avcodec_open < 0) {
                FFmpeg.av_free(avctx);
                avctx = 0;
            }
        }
        if (avctx == 0) {
            throw new ResourceUnavailableException("Could not open FFmpeg codec "
                    + codecIDToString(codecID) + "!");
        }
    }

    /**
     * Finds an <code>AVCodec</code> with a specific <code>AVCodecID</code>. The method is invoked by
     * {@link #doOpen()} in order to (eventually) open a new <code>AVCodecContext</code>.
     *
     * @param codecID the <code>AVCodecID</code> of the <code>AVCodec</code> to find
     * @return an <code>AVCodec</code> with the specified <code>codecID</code> or <code>0</code>
     */
    protected abstract long findAVCodec(int codecID);

    /**
     * Gets the <code>AudioFormat</code> with which {@link #avctx} is to be configured and opened by
     * {@link #doOpen()}.
     *
     * @return the <code>AudioFormat</code> with which <code>avctx</code> is to be configured and opened by
     * <code>doOpen()</code>
     */
    protected abstract AudioFormat getAVCodecContextFormat();
}
