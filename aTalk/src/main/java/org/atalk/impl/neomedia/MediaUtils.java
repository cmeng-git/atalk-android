/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.device.ScreenDeviceImpl;
import org.atalk.impl.neomedia.format.AudioMediaFormatImpl;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.device.ScreenDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;
import javax.sdp.SdpConstants;

import timber.log.Timber;

/**
 * Implements static utility methods used by media classes.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class MediaUtils {
    /**
     * An empty array with <code>MediaFormat</code> element type. Explicitly defined in order to reduce
     * unnecessary allocations, garbage collection.
     */
    public static final MediaFormat[] EMPTY_MEDIA_FORMATS = new MediaFormat[0];

    /**
     * The <code>Map</code> of JMF-specific encodings to well-known encodings as defined in RFC 3551.
     */
    private static final Map<String, String> jmfEncodingToEncodings = new HashMap<>();

    /**
     * The maximum number of channels for audio that is available through <code>MediaUtils</code>.
     */
    public static final int MAX_AUDIO_CHANNELS;

    /**
     * The maximum sample rate for audio that is available through <code>MediaUtils</code>.
     */
    public static final double MAX_AUDIO_SAMPLE_RATE;

    /**
     * The maximum sample size in bits for audio that is available through <code>MediaUtils</code>.
     */
    public static final int MAX_AUDIO_SAMPLE_SIZE_IN_BITS;

    /**
     * The <code>MediaFormat</code>s which do not have RTP payload types assigned by RFC 3551 and are
     * thus referred to as having dynamic RTP payload types.
     */
    private static final List<MediaFormat> rtpPayloadTypelessMediaFormats = new ArrayList<>();

    /**
     * The <code>Map</code> of RTP payload types (expressed as <code>String</code>s) to <code>MediaFormat</code>s.
     */
    private static final Map<String, MediaFormat[]> rtpPayloadTypeStrToMediaFormats = new HashMap<>();

    static {
        addMediaFormats(
                (byte) SdpConstants.PCMU,
                "PCMU",
                MediaType.AUDIO,
                AudioFormat.ULAW_RTP,
                8000);

        /*
         * Some codecs depend on JMF native libraries which are only available on 32-bit Linux and
         * 32-bit Windows.
         */
        if (OSUtils.IS_LINUX32 || OSUtils.IS_WINDOWS32) {
            Map<String, String> g723FormatParams = new HashMap<>();
            g723FormatParams.put("annexa", "no");
            g723FormatParams.put("bitrate", "6.3");
            addMediaFormats(
                    (byte) SdpConstants.G723,
                    "G723",
                    MediaType.AUDIO,
                    AudioFormat.G723_RTP,
                    g723FormatParams,
                    null,
                    8000);
        }

        addMediaFormats(
                (byte) SdpConstants.GSM,
                "GSM",
                MediaType.AUDIO,
                AudioFormat.GSM_RTP,
                8000);
        addMediaFormats(
                (byte) SdpConstants.PCMA,
                "PCMA",
                MediaType.AUDIO,
                Constants.ALAW_RTP,
                8000);
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "iLBC",
                MediaType.AUDIO,
                Constants.ILBC_RTP,
                8000);
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.SPEEX,
                MediaType.AUDIO,
                Constants.SPEEX_RTP,
                8000, 16000, 32000);
        addMediaFormats(
                (byte) SdpConstants.G722,
                "G722",
                MediaType.AUDIO,
                Constants.G722_RTP,
                8000);

        /*
         * @see https://en.wikipedia.org/wiki/G.729 #Licensing
         * As of January 1, 2017, the patent terms of most licensed patents under the G.729 Consortium
         * have expired, the remaining unexpired patents are usable on a royalty-free basis.
         */
        // Set the encoder option according to user configuration; default to enable if none found.
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean g729Vad = cfg.getBoolean(Constants.PROP_G729_VAD, true);
        Map<String, String> g729FormatParams = new HashMap<>();
        g729FormatParams.put("annexb", g729Vad ? "yes" : "no");

        addMediaFormats(
                (byte) SdpConstants.G729,
                "G729",
                MediaType.AUDIO,
                AudioFormat.G729_RTP,
                g729FormatParams,
                null,
                8000);

        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "telephone-event",
                MediaType.AUDIO,
                Constants.TELEPHONE_EVENT,
                8000);

        // Although we use "red" and "ulpfec" as jmf encodings here, FMJ should never see RTP
        // packets of these types. Such packets should be handled by transform  engines before
        // being passed to FMJ.
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.RED,
                MediaType.VIDEO,
                Constants.RED);
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.ULPFEC,
                MediaType.VIDEO,
                Constants.ULPFEC);
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.FLEXFEC_03,
                MediaType.VIDEO,
                Constants.FLEXFEC_03);

        boolean advertiseFEC = cfg.getBoolean(Constants.PROP_SILK_ADVERSISE_FEC, false);
        Map<String, String> silkFormatParams = new HashMap<>();
        if (advertiseFEC) {
            silkFormatParams.put("useinbandfec", "1");
        }
        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                "SILK",
                MediaType.AUDIO,
                Constants.SILK_RTP,
                silkFormatParams,
                null,
                8000, 12000, 16000, 24000);

        /*
         * RTP Payload Format for the Opus Speech and Audio Codec (June 2015)
         * https://tools.ietf.org/html/rfc7587
         */
        Map<String, String> opusFormatParams = new HashMap<>();
        // opusFormatParams.put("minptime", "10"); /* not in rfc7587 */

        /*
         * Decoder support for FEC SHOULD be indicated at the time a session is setup.
         */
        boolean opusFec = cfg.getBoolean(Constants.PROP_OPUS_FEC, true);
        if (opusFec) {
            opusFormatParams.put("useinbandfec", "1");
        }

        /*
         * DTX can be used with both variable and constant bitrate.  It will have a slightly lower speech
         * or audio quality than continuous transmission.  Therefore, using continuous transmission is
         * RECOMMENDED unless constraints on available network bandwidth are severe.
         * If no value is specified, the default is 0.
         */
        boolean opusDtx = cfg.getBoolean(Constants.PROP_OPUS_DTX, false);
        if (opusDtx) {
            opusFormatParams.put("usedtx", "1");
        }

        Map<String, String> opusAdvancedParams = new HashMap<>();
        /* The preferred duration of media represented by a packet that a decoder wants to receive, in milliseconds */
        opusAdvancedParams.put(Constants.PTIME, "20");

        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.OPUS,
                MediaType.AUDIO,
                Constants.OPUS_RTP,
                2,
                opusFormatParams,
                opusAdvancedParams,
                48000);


        // Adaptive Multi-Rate Wideband (AMR-WB)
        // Checks whether ffmpeg is enabled and whether AMR-WB is available in the provided binaries
        boolean enableFfmpeg = cfg.getBoolean(MediaService.ENABLE_FFMPEG_CODECS_PNAME, true);
        boolean amrwbEnabled = false;
        if (enableFfmpeg) {
            try {
                amrwbEnabled = (FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_AMR_WB) != 0);
            } catch (Throwable t) {
                Timber.d("AMR-WB codec not found %s", t.getMessage());
            }
        }

        if (amrwbEnabled) {
            addMediaFormats(
                    MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                    Constants.AMR_WB,
                    MediaType.AUDIO,
                    Constants.AMR_WB_RTP,
                    16000);
        }

        /* H264 */
        // Checks whether ffmpeg is enabled and whether h264 is available in the provided binaries
        boolean h264Enabled = false;
        if (enableFfmpeg) {
            try {
                h264Enabled = FFmpeg.avcodec_find_encoder(FFmpeg.CODEC_ID_H264) != 0;
            } catch (Throwable t) {
                Timber.d("H264 codec not found: %s", t.getMessage());
            }
        }

        // register h264 media formats if codec is present or there is
        // a property that forces enabling the formats (in case of videobridge)
        if (h264Enabled
                || cfg.getBoolean(MediaService.ENABLE_H264_FORMAT_PNAME, false)) {
            Map<String, String> h264FormatParams = new HashMap<>();
            String packetizationMode = VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP;
            Map<String, String> h264AdvancedAttributes = new HashMap<>();

            /*
             * Disable PLI because the periodic intra-refresh feature of FFmpeg/x264 is used.
             */
            // h264AdvancedAttributes.put("rtcp-fb", "nack pli");

            /*
             * XXX The initialization of MediaServiceImpl is very complex so it is wise to not
             * reference it at the early stage of its initialization.
             */
            ScreenDevice screen = ScreenDeviceImpl.getDefaultScreenDevice();
            Dimension res = (screen == null) ? null : screen.getSize();

            h264AdvancedAttributes.put("imageattr", createImageAttr(null, res));

            /* if ((cfg == null)
                    || cfg.getString("neomedia.codec.video.h264.defaultProfile",
                    JNIEncoder.MAIN_PROFILE).equals(JNIEncoder.MAIN_PROFILE)) {
                // main profile, common features, HD capable level 3.1
                h264FormatParams.put("profile-level-id", "4DE01f");
            }
            else */
            {
                // baseline profile, common features, HD capable level 3.1
                h264FormatParams.put("profile-level-id", "42E01f");
            }

            // if (cfg.getBoolean("neomedia.codec.video.h264.enabled", false)) {
            // By default, packetization-mode=1 is enabled.
            if (cfg.getBoolean("neomedia.codec.video.h264.packetization-mode-1.enabled", true)) {
                // packetization-mode=1
                h264FormatParams.put(packetizationMode, "1");
                addMediaFormats(
                        MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                        Constants.H264,
                        MediaType.VIDEO,
                        Constants.H264_RTP,
                        h264FormatParams,
                        h264AdvancedAttributes);
            }
            // packetization-mode=0

            /*
             * XXX At the time of this writing,
             * EncodingConfiguration#compareEncodingPreferences(MediaFormat, MediaFormat) is incomplete
             * and considers two MediaFormats to be equal if they have an equal number of format
             * parameters (given that the encodings and clock rates are equal, of course). Either fix
             * the method in question or don't add a format parameter for packetization-mode 0
             * equivalent to having packetization-mode explicitly defined as 0 anyway, according to the
             * respective RFC).
             */
            h264FormatParams.remove(packetizationMode);
            addMediaFormats(
                    MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                    Constants.H264,
                    MediaType.VIDEO,
                    Constants.H264_RTP,
                    h264FormatParams,
                    h264AdvancedAttributes
            );
        }

        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.VP8,
                MediaType.VIDEO,
                Constants.VP8_RTP,
                null, null);

        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.VP9,
                MediaType.VIDEO,
                Constants.VP9_RTP,
                null, null);

        addMediaFormats(
                MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN,
                Constants.RTX,
                MediaType.VIDEO,
                Constants.RTX_RTP,
                null, null);

        // Calculate the values of the MAX_AUDIO_* static fields of MediaUtils.
        List<MediaFormat> audioMediaFormats = new ArrayList<>(
                rtpPayloadTypeStrToMediaFormats.size() + rtpPayloadTypelessMediaFormats.size());

        for (MediaFormat[] mediaFormats : rtpPayloadTypeStrToMediaFormats.values())
            for (MediaFormat mediaFormat : mediaFormats)
                if (MediaType.AUDIO.equals(mediaFormat.getMediaType()))
                    audioMediaFormats.add(mediaFormat);
        for (MediaFormat mediaFormat : rtpPayloadTypelessMediaFormats)
            if (MediaType.AUDIO.equals(mediaFormat.getMediaType()))
                audioMediaFormats.add(mediaFormat);

        int maxAudioChannels = Format.NOT_SPECIFIED;
        double maxAudioSampleRate = Format.NOT_SPECIFIED;
        int maxAudioSampleSizeInBits = Format.NOT_SPECIFIED;

        for (MediaFormat mediaFormat : audioMediaFormats) {
            AudioMediaFormatImpl audioMediaFormat = (AudioMediaFormatImpl) mediaFormat;
            String encoding = audioMediaFormat.getEncoding();
            int channels = audioMediaFormat.getChannels();
            double sampleRate = audioMediaFormat.getClockRate();
            int sampleSizeInBits = audioMediaFormat.getFormat().getSampleSizeInBits();

            // The opus/rtp format has 2 channels, but we don't want it to trigger use of stereo
            // elsewhere.
            if (Constants.OPUS.equalsIgnoreCase(encoding))
                channels = 1;

            if (maxAudioChannels < channels)
                maxAudioChannels = channels;
            if (maxAudioSampleRate < sampleRate)
                maxAudioSampleRate = sampleRate;
            if (maxAudioSampleSizeInBits < sampleSizeInBits)
                maxAudioSampleSizeInBits = sampleSizeInBits;
        }

        MAX_AUDIO_CHANNELS = maxAudioChannels;
        MAX_AUDIO_SAMPLE_RATE = maxAudioSampleRate;
        MAX_AUDIO_SAMPLE_SIZE_IN_BITS = maxAudioSampleSizeInBits;
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of <code>MediaFormat</code>s
     * of a specific <code>MediaType</code>, with a specific JMF encoding and, optionally, with
     * specific clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list of <code>MediaFormat</code>s
     * @param encoding the well-known encoding (name) corresponding to <code>rtpPayloadType</code> (in
     * contrast to the JMF-specific encoding specified by <code>jmfEncoding</code>)
     * @param mediaType the <code>MediaType</code> of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     * @param jmfEncoding the JMF encoding of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     * @param clockRates the optional list of clock rates of the <code>MediaFormat</code>s to be associated
     * with <code>rtpPayloadType</code>
     */

    private static void addMediaFormats(
            byte rtpPayloadType,
            String encoding,
            MediaType mediaType,
            String jmfEncoding,
            double... clockRates) {
        addMediaFormats(
                rtpPayloadType,
                encoding,
                mediaType,
                jmfEncoding,
                null,
                null,
                clockRates);
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of <code>MediaFormat</code>s of a
     * specific <code>MediaType</code>, with a specific JMF encoding and, optionally, with specific
     * clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list of <code>MediaFormat</code>s
     * @param encoding the well-known encoding (name) corresponding to <code>rtpPayloadType</code> (in contrast
     * to the JMF-specific encoding specified by <code>jmfEncoding</code>)
     * @param mediaType the <code>MediaType</code> of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     * @param jmfEncoding the JMF encoding of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     * @param channels number of channels
     * @param formatParameters the set of format-specific parameters of the <code>MediaFormat</code>s to be associated
     * with <code>rtpPayloadType</code>
     * @param advancedAttributes the set of advanced attributes of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayload</code>
     * @param clockRates the optional list of clock rates of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     */
    private static void addMediaFormats(
            byte rtpPayloadType,
            String encoding,
            MediaType mediaType,
            String jmfEncoding,
            int channels,
            Map<String, String> formatParameters,
            Map<String, String> advancedAttributes,
            double... clockRates) {
        int clockRateCount = clockRates.length;
        List<MediaFormat> mediaFormats = new ArrayList<>(clockRateCount);

        if (clockRateCount > 0) {
            for (double clockRate : clockRates) {
                Format format;

                switch (mediaType) {
                    case AUDIO:
                        if (channels == 1)
                            format = new AudioFormat(jmfEncoding);
                        else {
                            format = new AudioFormat(
                                    jmfEncoding,
                                    Format.NOT_SPECIFIED,
                                    Format.NOT_SPECIFIED,
                                    channels);
                        }
                        break;
                    case VIDEO:
                        format = new ParameterizedVideoFormat(jmfEncoding, formatParameters);
                        break;
                    default:
                        throw new IllegalArgumentException("mediaType");
                }

                MediaFormat mediaFormat = MediaFormatImpl.createInstance(format, clockRate,
                        formatParameters, advancedAttributes);

                if (mediaFormat != null)
                    mediaFormats.add(mediaFormat);
            }
        }
        else {
            Format format;
            double clockRate;

            switch (mediaType) {
                case AUDIO:
                    AudioFormat audioFormat = new AudioFormat(jmfEncoding);
                    format = audioFormat;
                    clockRate = audioFormat.getSampleRate();
                    break;
                case VIDEO:
                    format = new ParameterizedVideoFormat(jmfEncoding, formatParameters);
                    clockRate = VideoMediaFormatImpl.DEFAULT_CLOCK_RATE;
                    break;
                default:
                    throw new IllegalArgumentException("mediaType");
            }

            MediaFormat mediaFormat
                    = MediaFormatImpl.createInstance(format, clockRate, formatParameters, advancedAttributes);

            if (mediaFormat != null)
                mediaFormats.add(mediaFormat);
        }

        if (mediaFormats.size() > 0) {
            if (MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN == rtpPayloadType)
                rtpPayloadTypelessMediaFormats.addAll(mediaFormats);
            else
                rtpPayloadTypeStrToMediaFormats.put(Byte.toString(rtpPayloadType),
                        mediaFormats.toArray(EMPTY_MEDIA_FORMATS));

            jmfEncodingToEncodings.put(
                    ((MediaFormatImpl<? extends Format>) mediaFormats.get(0)).getJMFEncoding(), encoding);
        }
    }

    /**
     * Adds a new mapping of a specific RTP payload type to a list of <code>MediaFormat</code>s of a
     * specific <code>MediaType</code>, with a specific JMF encoding and, optionally, with specific
     * clock rates.
     *
     * @param rtpPayloadType the RTP payload type to be associated with a list of <code>MediaFormat</code>s
     * @param encoding the well-known encoding (name) corresponding to <code>rtpPayloadType</code> (in contrast
     * to the JMF-specific encoding specified by <code>jmfEncoding</code>)
     * @param mediaType the <code>MediaType</code> of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     * @param jmfEncoding the JMF encoding of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     * @param formatParameters the set of format-specific parameters of the <code>MediaFormat</code>s to be associated
     * with <code>rtpPayloadType</code>
     * @param advancedAttributes the set of advanced attributes of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayload</code>
     * @param clockRates the optional list of clock rates of the <code>MediaFormat</code>s to be associated with
     * <code>rtpPayloadType</code>
     */
    private static void addMediaFormats(
            byte rtpPayloadType,
            String encoding,
            MediaType mediaType,
            String jmfEncoding,
            Map<String, String> formatParameters,
            Map<String, String> advancedAttributes,
            double... clockRates) {
        addMediaFormats(
                rtpPayloadType,
                encoding,
                mediaType,
                jmfEncoding,
                1 /* channel */,
                formatParameters,
                advancedAttributes,
                clockRates);
    }

    /**
     * Creates value of an imgattr.
     *
     * <a href="https://tools.ietf.org/html/rfc6236">...</a>
     *
     * @param sendSize maximum size peer can send
     * @param maxRecvSize maximum size peer can display
     *
     * @return string that represent imgattr that can be encoded via SIP/SDP or XMPP/Jingle
     */
    public static String createImageAttr(Dimension sendSize, Dimension maxRecvSize) {
        StringBuilder img = new StringBuilder();

        /* send width */
        if (sendSize != null) {
            /* single value => send [x=width,y=height] */
			/*img.append("send [x=");
			img.append((int)sendSize.getWidth());
            img.append(",y=");
            img.append((int)sendSize.getHeight());
            img.append("]");*/
            /* send [x=[min:max],y=[min:max]] */
            img.append("send [x=[1:");
            img.append((int) sendSize.getWidth());
            img.append("],y=[1:");
            img.append((int) sendSize.getHeight());
            img.append("]]");
			/*
			else
            {
                // range
                img.append(" send [x=[");
                img.append((int)minSendSize.getWidth());
                img.append(":");
                img.append((int)maxSendSize.getWidth());
                img.append("],y=[");
                img.append((int)minSendSize.getHeight());
                img.append(":");
                img.append((int)maxSendSize.getHeight());
                img.append("]]");
            }
            */
        }
        else {
            /* can send "all" sizes */
            img.append("send *");
        }

        /* receive size */
        if (maxRecvSize != null) {
            // basically we can receive any size up to our screen display size

            /* recv [x=[min:max],y=[min:max]] */
            img.append(" recv [x=[1:");
            img.append((int) maxRecvSize.getWidth());
            img.append("],y=[1:");
            img.append((int) maxRecvSize.getHeight());
            img.append("]]");
        }
        else {
            /* accept all sizes */
            img.append(" recv *");
        }
        return img.toString();
    }

    /**
     * Gets a <code>MediaFormat</code> predefined in <code>MediaUtils</code> which represents a specific
     * JMF <code>Format</code>. If there is no such representing <code>MediaFormat</code> in
     * <code>MediaUtils</code>, returns <code>null</code>.
     *
     * @param format the JMF <code>Format</code> to get the <code>MediaFormat</code> representation for
     *
     * @return a <code>MediaFormat</code> predefined in <code>MediaUtils</code> which represents
     * <code>format</code> if any; <code>null</code> if there is no such representing
     * <code>MediaFormat</code> in <code>MediaUtils</code>
     */
    public static MediaFormat getMediaFormat(Format format) {
        double clockRate;

        if (format instanceof AudioFormat)
            clockRate = ((AudioFormat) format).getSampleRate();
        else if (format instanceof VideoFormat)
            clockRate = VideoMediaFormatImpl.DEFAULT_CLOCK_RATE;
        else
            clockRate = Format.NOT_SPECIFIED;

        byte rtpPayloadType = getRTPPayloadType(format.getEncoding(), clockRate);
        if (MediaFormatImpl.RTP_PAYLOAD_TYPE_UNKNOWN != rtpPayloadType) {
            for (MediaFormat mediaFormat : getMediaFormats(rtpPayloadType)) {
                MediaFormatImpl<? extends Format> mediaFormatImpl = (MediaFormatImpl<? extends Format>) mediaFormat;

                if (format.matches(mediaFormatImpl.getFormat()))
                    return mediaFormat;
            }
        }
        return null;
    }

    /**
     * Gets the <code>MediaFormat</code> known to <code>MediaUtils</code> and having the specified
     * well-known <code>encoding</code> (name) and <code>clockRate</code>.
     *
     * @param encoding the well-known encoding (name) of the <code>MediaFormat</code> to get
     * @param clockRate the clock rate of the <code>MediaFormat</code> to get
     *
     * @return the <code>MediaFormat</code> known to <code>MediaUtils</code> and having the specified
     * <code>encoding</code> and <code>clockRate</code>
     */
    public static MediaFormat getMediaFormat(String encoding, double clockRate) {
        return getMediaFormat(encoding, clockRate, null);
    }

    /**
     * Gets the <code>MediaFormat</code> known to <code>MediaUtils</code> and having the specified
     * well-known <code>encoding</code> (name), <code>clockRate</code> and matching format parameters.
     *
     * @param encoding the well-known encoding (name) of the <code>MediaFormat</code> to get
     * @param clockRate the clock rate of the <code>MediaFormat</code> to get
     * @param fmtps the format parameters of the <code>MediaFormat</code> to get
     *
     * @return the <code>MediaFormat</code> known to <code>MediaUtils</code> and having the specified
     * <code>encoding</code> (name), <code>clockRate</code> and matching format parameters
     */
    public static MediaFormat getMediaFormat(String encoding, double clockRate, Map<String, String> fmtps) {
        for (MediaFormat format : getMediaFormats(encoding)) {
            if ((format.getClockRate() == clockRate) && format.formatParametersMatch(fmtps))
                return format;
        }
        return null;
    }

    /**
     * Gets the index of a specific <code>MediaFormat</code> instance within the internal storage of
     * <code>MediaUtils</code>. Since the index is in the internal storage which may or may not be one
     * and the same for the various <code>MediaFormat</code> instances and which may or may not be
     * searched for the purposes of determining the index, the index is not to be used as a way to
     * determine whether <code>MediaUtils</code> knows the specified <code>mediaFormat</code>
     *
     * @param mediaFormat the <code>MediaFormat</code> to determine the index of
     *
     * @return the index of the specified <code>mediaFormat</code> in the internal storage of
     * <code>MediaUtils</code>
     */
    public static int getMediaFormatIndex(MediaFormat mediaFormat) {
        return rtpPayloadTypelessMediaFormats.indexOf(mediaFormat);
    }

    /**
     * Gets the <code>MediaFormat</code>s (expressed as an array) corresponding to a specific RTP
     * payload type.
     *
     * @param rtpPayloadType the RTP payload type to retrieve the corresponding <code>MediaFormat</code>s for
     *
     * @return an array of <code>MediaFormat</code>s corresponding to the specified RTP payload type
     */
    public static MediaFormat[] getMediaFormats(byte rtpPayloadType) {
        MediaFormat[] mediaFormats = rtpPayloadTypeStrToMediaFormats.get(Byte.toString(rtpPayloadType));
        return (mediaFormats == null) ? EMPTY_MEDIA_FORMATS : mediaFormats.clone();
    }

    /**
     * Gets the <code>MediaFormat</code>s known to <code>MediaUtils</code> and being of the specified
     * <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaFormat</code>s to get
     *
     * @return the <code>MediaFormat</code>s known to <code>MediaUtils</code> and being of the specified
     * <code>mediaType</code>
     */
    public static MediaFormat[] getMediaFormats(MediaType mediaType) {
        List<MediaFormat> mediaFormats = new ArrayList<>();

        for (MediaFormat[] formats : rtpPayloadTypeStrToMediaFormats.values()) {
            for (MediaFormat format : formats) {
                if (format.getMediaType().equals(mediaType))
                    mediaFormats.add(format);
            }
        }
        for (MediaFormat format : rtpPayloadTypelessMediaFormats) {
            if (format.getMediaType().equals(mediaType))
                mediaFormats.add(format);
        }
        return mediaFormats.toArray(EMPTY_MEDIA_FORMATS);
    }

    /**
     * Gets the <code>MediaFormat</code>s predefined in <code>MediaUtils</code> with a specific well-known
     * encoding (name) as defined by RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control".
     *
     * @param encoding the well-known encoding (name) to get the corresponding <code>MediaFormat</code>s of
     *
     * @return a <code>List</code> of <code>MediaFormat</code>s corresponding to the specified encoding (name)
     */
    public static List<MediaFormat> getMediaFormats(String encoding) {
        String jmfEncoding = null;

        for (Map.Entry<String, String> jmfEncodingToEncoding : jmfEncodingToEncodings.entrySet()) {
            if (jmfEncodingToEncoding.getValue().equalsIgnoreCase(encoding)) {
                jmfEncoding = jmfEncodingToEncoding.getKey();
                break;
            }
        }

        List<MediaFormat> mediaFormats = new ArrayList<>();

        if (jmfEncoding != null) {
            for (MediaFormat[] rtpPayloadTypeMediaFormats : rtpPayloadTypeStrToMediaFormats.values()) {
                for (MediaFormat rtpPayloadTypeMediaFormat : rtpPayloadTypeMediaFormats) {
                    if (((MediaFormatImpl<? extends Format>) rtpPayloadTypeMediaFormat)
                            .getJMFEncoding().equals(jmfEncoding))
                        mediaFormats.add(rtpPayloadTypeMediaFormat);
                }
            }
            if (mediaFormats.size() < 1) {
                for (MediaFormat rtpPayloadTypelessMediaFormat : rtpPayloadTypelessMediaFormats) {
                    if (((MediaFormatImpl<? extends Format>) rtpPayloadTypelessMediaFormat)
                            .getJMFEncoding().equals(jmfEncoding))
                        mediaFormats.add(rtpPayloadTypelessMediaFormat);
                }
            }
        }
        return mediaFormats;
    }

    /**
     * Gets the RTP payload type corresponding to a specific JMF encoding and clock rate.
     *
     * @param jmfEncoding the JMF encoding as returned by
     * {@link Format#getEncoding()} or the respective <code>AudioFormat</code> and
     * <code>VideoFormat</code> encoding constants to get the corresponding RTP payload type of
     * @param clockRate the clock rate to be taken into account in the search for the RTP payload type if the
     * JMF encoding does not uniquely identify it
     *
     * @return the RTP payload type corresponding to the specified JMF encoding and clock rate if
     * known in RFC 3551 "RTP Profile for Audio and Video Conferences with Minimal Control";
     * otherwise,
     * {@link MediaFormat#RTP_PAYLOAD_TYPE_UNKNOWN}
     */
    public static byte getRTPPayloadType(String jmfEncoding, double clockRate) {
        if (jmfEncoding == null) {
            return MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN;
        }
        else if (jmfEncoding.equals(AudioFormat.ULAW_RTP)) {
            return SdpConstants.PCMU;
        }
        else if (jmfEncoding.equals(Constants.ALAW_RTP)) {
            return SdpConstants.PCMA;
        }
        else if (jmfEncoding.equals(AudioFormat.G723_RTP)) {
            return SdpConstants.G723;
        }
        else if (jmfEncoding.equals(AudioFormat.DVI_RTP)
                && (clockRate == 8000)) {
            return SdpConstants.DVI4_8000;
        }
        else if (jmfEncoding.equals(AudioFormat.DVI_RTP)
                && (clockRate == 16000)) {
            return SdpConstants.DVI4_16000;
        }
        else if (jmfEncoding.equals(AudioFormat.ALAW)) {
            return SdpConstants.PCMA;
        }
        else if (jmfEncoding.equals(Constants.G722)) {
            return SdpConstants.G722;
        }
        else if (jmfEncoding.equals(Constants.G722_RTP)) {
            return SdpConstants.G722;
        }
        else if (jmfEncoding.equals(AudioFormat.GSM)) {
            return SdpConstants.GSM;
        }
        else if (jmfEncoding.equals(AudioFormat.GSM_RTP)) {
            return SdpConstants.GSM;
        }
        else if (jmfEncoding.equals(AudioFormat.G728_RTP)) {
            return SdpConstants.G728;
        }
        else if (jmfEncoding.equals(AudioFormat.G729_RTP)) {
            return SdpConstants.G729;
        }
        else if (jmfEncoding.equals(VideoFormat.JPEG_RTP)) {
            return SdpConstants.JPEG;
        }
        else if (jmfEncoding.equals(VideoFormat.H261_RTP)) {
            return SdpConstants.H261;
        }
        else {
            return MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN;
        }
    }

    /**
     * Gets the well-known encoding (name) as defined in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control" corresponding to a given
     * JMF-specific encoding.
     *
     * @param jmfEncoding the JMF encoding to get the corresponding well-known encoding of
     *
     * @return the well-known encoding (name) as defined in RFC 3551
     * "RTP Profile for Audio and Video Conferences with Minimal Control" corresponding to
     * <code>jmfEncoding</code> if any; otherwise, <code>null</code>
     */
    public static String jmfEncodingToEncoding(String jmfEncoding) {
        return jmfEncodingToEncodings.get(jmfEncoding);
    }
}
