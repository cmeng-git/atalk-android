/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.opus;

import net.sf.fmj.media.AbstractCodec;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.FormatParametersAwareCodec;
import org.atalk.service.neomedia.control.PacketLossAwareEncoder;

import java.awt.Component;
import java.util.Map;

import javax.media.Buffer;
import javax.media.Control;
import javax.media.Format;
import javax.media.PlugIn;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * Implements an Opus encoder.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class JNIEncoder extends AbstractCodec2
        implements FormatParametersAwareCodec, PacketLossAwareEncoder
{
    /**
     * The list of <code>Format</code>s of audio data supported as input by <code>JNIEncoder</code> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS;

    /**
     * The list of sample rates of audio data supported as input by <code>JNIEncoder</code> instances.
     * <p>
     * The implementation does support 8, 12, 16, 24 and 48kHz but the lower sample rates are not
     * listed to prevent FMJ from defaulting to them.
     * </p>
     */
    static final double[] SUPPORTED_INPUT_SAMPLE_RATES = new double[]{48000};

    /**
     * The list of <code>Format</code>s of audio data supported as output by <code>JNIEncoder</code> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS = new Format[]{new AudioFormat(
            Constants.OPUS_RTP,
            48000,
            /* sampleSizeInBits */Format.NOT_SPECIFIED,
            2,
            /* endian */Format.NOT_SPECIFIED,
            /* signed */Format.NOT_SPECIFIED,
            /* frameSizeInBits */Format.NOT_SPECIFIED,
            /* frameRate */Format.NOT_SPECIFIED,
            Format.byteArray)
    };

    /*
     * Sets the supported input formats.
     */
    static {
        /*
         * If the Opus class or its supporting JNI library are not functional, it is too late to
         * discover the fact in #doOpen() because a JNIEncoder instance has already been initialized
         * and it has already signaled that the Opus codec is supported.
         */
        Opus.assertOpusIsFunctional();
        int supportedInputCount = SUPPORTED_INPUT_SAMPLE_RATES.length;

        SUPPORTED_INPUT_FORMATS = new Format[supportedInputCount];
        // SUPPORTED_INPUT_FORMATS = new Format[supportedInputCount*2];
        for (int i = 0; i < supportedInputCount; i++) {
            SUPPORTED_INPUT_FORMATS[i] = new AudioFormat(
                    AudioFormat.LINEAR,
                    SUPPORTED_INPUT_SAMPLE_RATES[i],
                    16,
                    1,
                    AbstractAudioRenderer.NATIVE_AUDIO_FORMAT_ENDIAN,
                    AudioFormat.SIGNED,
                    /* frameSizeInBits */Format.NOT_SPECIFIED,
                    /* frameRate */Format.NOT_SPECIFIED,
                    Format.byteArray);
        }
        /*
         * Using stereo input formats leads to problems (at least when used with pulse audio). It is
         * unclear whether they are rooted in this encoder or somewhere else in the code. So stereo
         * input formats are disabled until we make sure that they work properly.
         */
        // for (int i = 0; i < supportedInputCount; i++)
        // {
        // SUPPORTED_INPUT_FORMATS[i+supportedInputCount]
        // = new AudioFormat(
        // AudioFormat.LINEAR,
        // SUPPORTED_INPUT_SAMPLE_RATES[i],
        // 16,
        // 2,
        // AbstractAudioRenderer.NATIVE_AUDIO_FORMAT_ENDIAN,
        // AudioFormat.SIGNED,
        // /* frameSizeInBits */ Format.NOT_SPECIFIED,
        // /* frameRate */ Format.NOT_SPECIFIED,
        // Format.byteArray);
        // }
    }

    /**
     * Codec audio bandwidth, obtained from configuration.
     */
    private int bandwidth;

    /**
     * The bitrate in bits per second obtained from the configuration and set on {@link #encoder}.
     */
    private int bitrate;

    /**
     * Number of channels to use, default to 1.
     */
    private int channels = 1;

    /**
     * Complexity setting, obtained from configuration.
     */
    private int complexity;

    /**
     * The pointer to the native OpusEncoder structure
     */
    private long encoder = 0;

    /**
     * The size in bytes of an audio frame input by this instance. Automatically calculated, based
     * on {@link #frameSizeInMillis} and the <code>inputFormat</code> of this instance.
     */
    private int frameSizeInBytes;

    /**
     * The size/duration in milliseconds of an audio frame output by this instance. The possible
     * values are: 2.5, 5, 10, 20, 40 and 60. The default value is 20.
     */
    private final int frameSizeInMillis = 20;

    /**
     * The size in samples per channel of an audio frame input by this instance. Automatically
     * calculated, based on {@link #frameSizeInMillis} and the <code>inputFormat</code> of this instance.
     */
    private int frameSizeInSamplesPerChannel;

    /**
     * The minimum expected packet loss percentage to set to the encoder.
     */
    private int minPacketLoss = 0;

    /**
     * The bytes from an input <code>Buffer</code> from a previous call to
     * {@link #process(Buffer, Buffer)} that this <code>Codec</code> didn't process because the total
     * number of bytes was less than {@link #inputFrameSize()} need to be prepended to a subsequent
     * input <code>Buffer</code> in order to process a total of {@link #inputFrameSize()} bytes.
     */
    private byte[] prevIn = null;

    /**
     * The length of the audio data in {@link #prevIn}.
     */
    private int prevInLength = 0;

    /**
     * Whether to use DTX, obtained from configuration.
     */
    private boolean useDtx;

    /**
     * Whether to use FEC, obtained from configuration.
     */
    private boolean useFec;

    /**
     * Whether to use VBR, obtained from configuration.
     */
    private boolean useVbr;

    /**
     * Initializes a new <code>JNIEncoder</code> instance.
     */
    public JNIEncoder()
    {
        super("Opus JNI Encoder", AudioFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = SUPPORTED_INPUT_FORMATS;
        addControl(this);
    }

    /**
     * {@inheritDoc}
     *
     * @see AbstractCodec2#doClose()
     */
    @Override
    protected void doClose()
    {
        if (encoder != 0) {
            Opus.encoder_destroy(encoder);
            encoder = 0;
        }
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
            throws ResourceUnavailableException
    {
        AudioFormat inputFormat = (AudioFormat) getInputFormat();
        int sampleRate = (int) inputFormat.getSampleRate();

        channels = inputFormat.getChannels();
        encoder = Opus.encoder_create(sampleRate, channels);
        if (encoder == 0)
            throw new ResourceUnavailableException("opus_encoder_create()");

        // Set encoder options according to user configuration
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String bandwidthStr = cfg.getString(Constants.PROP_OPUS_BANDWIDTH, "auto");

        bandwidth = Opus.OPUS_AUTO;
        if ("fb".equals(bandwidthStr))
            bandwidth = Opus.BANDWIDTH_FULLBAND;
        else if ("swb".equals(bandwidthStr))
            bandwidth = Opus.BANDWIDTH_SUPERWIDEBAND;
        else if ("wb".equals(bandwidthStr))
            bandwidth = Opus.BANDWIDTH_WIDEBAND;
        else if ("mb".equals(bandwidthStr))
            bandwidth = Opus.BANDWIDTH_MEDIUMBAND;
        else if ("nb".equals(bandwidthStr))
            bandwidth = Opus.BANDWIDTH_NARROWBAND;
        Opus.encoder_set_bandwidth(encoder, bandwidth);

        bitrate = 1000 /* configuration is in kilobits per second */
                * cfg.getInt(Constants.PROP_OPUS_BITRATE, 32);
        if (bitrate < 500)
            bitrate = 500;
        else if (bitrate > 512000)
            bitrate = 512000;
        Opus.encoder_set_bitrate(encoder, bitrate);

        complexity = cfg.getInt(Constants.PROP_OPUS_COMPLEXITY, 0);
        if (complexity != 0)
            Opus.encoder_set_complexity(encoder, complexity);

        useFec = cfg.getBoolean(Constants.PROP_OPUS_FEC, true);
        Opus.encoder_set_inband_fec(encoder, useFec ? 1 : 0);

        minPacketLoss = cfg.getInt(Constants.PROP_OPUS_MIN_EXPECTED_PACKET_LOSS, 1);
        Opus.encoder_set_packet_loss_perc(encoder, minPacketLoss);

        useDtx = cfg.getBoolean(Constants.PROP_OPUS_DTX, false);
        Opus.encoder_set_dtx(encoder, useDtx ? 1 : 0);

        useVbr = cfg.getBoolean(Constants.PROP_OPUS_VBR, true);
        Opus.encoder_set_vbr(encoder, useVbr ? 1 : 0);

        if (TimberLog.isTraceEnable) {
            String bw;
            switch (Opus.encoder_get_bandwidth(encoder)) {
                case Opus.BANDWIDTH_FULLBAND:
                    bw = "fb";
                    break;
                case Opus.BANDWIDTH_SUPERWIDEBAND:
                    bw = "swb";
                    break;
                case Opus.BANDWIDTH_WIDEBAND:
                    bw = "wb";
                    break;
                case Opus.BANDWIDTH_MEDIUMBAND:
                    bw = "mb";
                    break;
                default:
                    bw = "nb";
                    break;
            }
            Timber.log(TimberLog.FINER, "Encoder settings: audio bandwidth %s, bitrate %s, DTX %s, FEC %s",
                    bw, Opus.encoder_get_bitrate(encoder), Opus.encoder_get_dtx(encoder), Opus.encoder_get_inband_fec(encoder));
        }
    }

    /**
     * Processes (i.e. encodes) a specific input <code>Buffer</code>.
     *
     * @param inBuffer the <code>Buffer</code> from which the media to be encoded is to be read
     * @param outBuffer the <code>Buffer</code> into which the encoded media is to be written
     * @return <code>BUFFER_PROCESSED_OK</code> if the specified <code>inBuffer</code> has been processed successfully
     * @see AbstractCodec2#doProcess(Buffer, Buffer)
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        Format inFormat = inBuffer.getFormat();

        if ((inFormat != null) && (inFormat != this.inputFormat)
                && !inFormat.equals(this.inputFormat) && (null == setInputFormat(inFormat))) {
            return BUFFER_PROCESSED_FAILED;
        }

        byte[] in = (byte[]) inBuffer.getData();
        int inLength = inBuffer.getLength();
        int inOffset = inBuffer.getOffset();

        if ((prevIn != null) && (prevInLength > 0)) {
            if (prevInLength < frameSizeInBytes) {
                if (prevIn.length < frameSizeInBytes) {
                    byte[] newPrevIn = new byte[frameSizeInBytes];

                    System.arraycopy(prevIn, 0, newPrevIn, 0, prevIn.length);
                    prevIn = newPrevIn;
                }

                int bytesToCopyFromInToPrevIn = Math.min(frameSizeInBytes - prevInLength, inLength);

                if (bytesToCopyFromInToPrevIn > 0) {
                    System.arraycopy(in, inOffset, prevIn, prevInLength, bytesToCopyFromInToPrevIn);
                    prevInLength += bytesToCopyFromInToPrevIn;
                    inLength -= bytesToCopyFromInToPrevIn;
                    inBuffer.setLength(inLength);
                    inBuffer.setOffset(inOffset + bytesToCopyFromInToPrevIn);
                }
            }

            if (prevInLength == frameSizeInBytes) {
                in = prevIn;
                inOffset = 0;
                prevInLength = 0;
            }
            else {
                outBuffer.setLength(0);
                discardOutputBuffer(outBuffer);
                if (inLength < 1)
                    return BUFFER_PROCESSED_OK;
                else
                    return BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED;
            }
        }
        else if (inLength < 1) {
            outBuffer.setLength(0);
            discardOutputBuffer(outBuffer);
            return BUFFER_PROCESSED_OK;
        }
        else if (inLength < frameSizeInBytes) {
            if ((prevIn == null) || (prevIn.length < inLength))
                prevIn = new byte[frameSizeInBytes];
            System.arraycopy(in, inOffset, prevIn, 0, inLength);
            prevInLength = inLength;
            outBuffer.setLength(0);
            discardOutputBuffer(outBuffer);
            return BUFFER_PROCESSED_OK;
        }
        else {
            inLength -= frameSizeInBytes;
            inBuffer.setLength(inLength);
            inBuffer.setOffset(inOffset + frameSizeInBytes);
        }

        // At long last, do the actual encoding.
        byte[] out = validateByteArraySize(outBuffer, Opus.MAX_PACKET, false);
        int outLength = Opus.encode(encoder, in, inOffset, frameSizeInSamplesPerChannel, out, 0, out.length);

        if (outLength < 0) // error from opus_encode
            return BUFFER_PROCESSED_FAILED;

        if (outLength > 0) {
            outBuffer.setDuration(((long) frameSizeInMillis) * 1000 * 1000);
            outBuffer.setFormat(getOutputFormat());
            outBuffer.setLength(outLength);
            outBuffer.setOffset(0);
            outBuffer.setHeaderExtension(inBuffer.getHeaderExtension());
        }

        if (inLength < 1)
            return BUFFER_PROCESSED_OK;
        else
            return BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED;
    }

    /**
     * Implements {@link Control#getControlComponent()}. <code>JNIEncoder</code> does not provide user
     * interface of its own.
     *
     * @return <code>null</code> to signify that <code>JNIEncoder</code> does not provide user interface of its own
     */
    @Override
    public Component getControlComponent()
    {
        return null;
    }

    /**
     * Gets the <code>Format</code> of the media output by this <code>Codec</code>.
     *
     * @return the <code>Format</code> of the media output by this <code>Codec</code>
     * @see net.sf.fmj.media.AbstractCodec#getOutputFormat()
     */
    @Override
    @SuppressWarnings("serial")
    public Format getOutputFormat()
    {
        Format f = super.getOutputFormat();

        if ((f != null) && (f.getClass() == AudioFormat.class)) {
            AudioFormat af = (AudioFormat) f;

            f = setOutputFormat(new AudioFormat(af.getEncoding(), af.getSampleRate(),
                    af.getSampleSizeInBits(), af.getChannels(), af.getEndian(), af.getSigned(),
                    af.getFrameSizeInBits(), af.getFrameRate(), af.getDataType())
            {
                @Override
                public long computeDuration(long length)
                {
                    return ((long) frameSizeInMillis) * 1000 * 1000;
                }
            });
        }
        return f;
    }

    /**
     * Updates the encoder's expected packet loss percentage to the bigger of <code>percentage</code>
     * and <code>this.minPacketLoss</code>.
     *
     * @param percentage the expected packet loss percentage to set
     */
    @Override
    public void setExpectedPacketLoss(int percentage)
    {
        if (opened) {
            Opus.encoder_set_packet_loss_perc(encoder, Math.max(percentage, minPacketLoss));
            Timber.log(TimberLog.FINER, "Updating expected packet loss: %s (minimum %s)", percentage, minPacketLoss);
        }
    }

    /**
     * Sets the format parameters.
     *
     * @param fmtps the format parameters to set
     */
    @Override
    public void setFormatParameters(Map<String, String> fmtps)
    {
        Timber.d("Setting format parameters: %s", fmtps);

        /*
         * TODO Use the default value for maxaveragebitrate as defined at
         * https://tools.ietf.org/html/draft-spittka-payload-rtp-opus-02#section-6.1
         */
        int maxaveragebitrate = -1;

        try {
            String s = fmtps.get("maxaveragebitrate");
            if ((s != null) && (s.length() != 0))
                maxaveragebitrate = Integer.parseInt(s);
        } catch (Exception e) {
            // Ignore and fall back to the default value.
        }
        if (maxaveragebitrate > 0) {
            Opus.encoder_set_bitrate(encoder, Math.min(maxaveragebitrate, bitrate));
        }
        // DTX is off unless specified.
        boolean useDtx = this.useDtx && "1".equals(fmtps.get("usedtx"));
        Opus.encoder_set_dtx(encoder, useDtx ? 1 : 0);

        // FEC is on unless specified.
        String s;
        boolean useFec = this.useFec
                && (((s = fmtps.get("useinbandfec")) == null) || s.equals("1"));
        Opus.encoder_set_inband_fec(encoder, useFec ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     *
     * Automatically tracks and calculates the size in bytes of an audio frame (to be) output by
     * this instance.
     */
    @Override
    public Format setInputFormat(Format format)
    {
        Format oldValue = getInputFormat();
        Format setInputFormat = super.setInputFormat(format);
        Format newValue = getInputFormat();

        if (oldValue != newValue) {
            AudioFormat af = (AudioFormat) newValue;
            int sampleRate = (int) af.getSampleRate();

            frameSizeInSamplesPerChannel = (sampleRate * frameSizeInMillis) / 1000;
            frameSizeInBytes = 2 /* sizeof(opus_int16) */
                    * channels * frameSizeInSamplesPerChannel;
        }
        return setInputFormat;
    }
}
