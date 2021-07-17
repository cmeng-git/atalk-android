/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.mp3;

import org.atalk.android.util.java.awt.Component;
import org.atalk.impl.neomedia.codec.audio.FFmpegAudioEncoder;
import org.atalk.service.neomedia.control.FlushableControl;
import org.atalk.impl.neomedia.codec.FFmpeg;

import javax.media.Format;
import javax.media.format.AudioFormat;

/**
 * Implements a MP3 encoder using the native FFmpeg library.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public class JNIEncoder extends FFmpegAudioEncoder
    implements FlushableControl
{
	/**
	 * The list of <tt>Format</tt>s of audio data supported as input by <tt>JNIEncoder</tt>
	 * instances.
	 */
	private static final AudioFormat[] SUPPORTED_INPUT_FORMATS = { new AudioFormat(
                AudioFormat.LINEAR,
                Format.NOT_SPECIFIED /* sampleRate */,
                16,
                Format.NOT_SPECIFIED /* channels */,
                AudioFormat.LITTLE_ENDIAN,
                AudioFormat.SIGNED,
                Format.NOT_SPECIFIED /* frameSizeInBits */,
                Format.NOT_SPECIFIED/* frameRate */,
                Format.byteArray)
        };

	/**
	 * The list of <tt>Format</tt>s of audio data supported as output by <tt>JNIEncoder</tt>
	 * instances.
	 */
	private static final AudioFormat[] SUPPORTED_OUTPUT_FORMATS
			= { new AudioFormat(AudioFormat.MPEGLAYER3) };

    static
    {
        assertFindAVCodec(FFmpeg.CODEC_ID_MP3);
    }

    /**
     * Initializes a new <tt>JNIEncoder</tt> instance.
     */
    public JNIEncoder()
    {
        super("MP3 JNI Encoder", FFmpeg.CODEC_ID_MP3, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = SUPPORTED_INPUT_FORMATS;
        addControl(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureAVCodecContext(long avctx, AudioFormat format)
    {
        super.configureAVCodecContext(avctx, format);
        FFmpeg.avcodeccontext_set_bit_rate(avctx, 128000);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void flush()
    {
        prevInLen = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Component getControlComponent()
    {
        return null;
    }
}
